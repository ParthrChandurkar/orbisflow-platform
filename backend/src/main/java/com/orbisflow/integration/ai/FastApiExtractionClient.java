package com.orbisflow.integration.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbisflow.integration.ai.FastApiExtractionDtos.ClientResult;
import com.orbisflow.integration.ai.FastApiExtractionDtos.ExtractionResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FastApiExtractionClient {
    private static final Set<String> INTERNAL_FAILURES =
            Set.of("unreadable_document", "unsupported_content", "ocr_error");
    private static final Set<String> FLAG_CODES =
            Set.of("MISSING_VENDOR", "MISSING_TOTAL_AMOUNT", "MISSING_INVOICE_DATE");
    private static final Set<String> FLAG_FIELDS =
            Set.of("vendor", "total_amount", "invoice_date");

    private final HttpClient http;
    private final ObjectMapper objectMapper;
    private final FastApiProperties properties;

    public FastApiExtractionClient(
            HttpClient extractionHttpClient,
            ObjectMapper objectMapper,
            FastApiProperties properties) {
        this.http = extractionHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ClientResult extract(
            UUID requestId,
            String mimeType,
            byte[] content,
            UUID correlationId) {
        String boundary = "orbisflow-" + UUID.randomUUID();
        byte[] body = multipart(boundary, requestId, mimeType, content);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(properties.baseUrl().resolve("/internal/v1/extractions"))
                .timeout(properties.readTimeout())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-Correlation-ID", correlationId.toString())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<byte[]> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException exception) {
            return ClientResult.failed("timeout");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ClientResult.failed("service_unavailable");
        } catch (IOException exception) {
            return ClientResult.failed("service_unavailable");
        }
        if (response.statusCode() != 200) {
            return ClientResult.failed(
                    response.statusCode() >= 500
                            ? "service_unavailable"
                            : "invalid_response");
        }
        if (!correlationId.toString().equalsIgnoreCase(
                response.headers().firstValue("X-Correlation-ID").orElse(""))) {
            return ClientResult.failed("invalid_response");
        }
        try {
            ExtractionResponse parsed =
                    objectMapper.readValue(response.body(), ExtractionResponse.class);
            return validate(parsed, requestId);
        } catch (IOException | RuntimeException exception) {
            return ClientResult.failed("invalid_response");
        }
    }

    private ClientResult validate(ExtractionResponse response, UUID requestId) {
        if (!requestId.equals(response.requestId())
                || !"1".equals(response.schemaVersion())
                || response.lineItems() == null
                || response.validationFlags() == null) {
            return ClientResult.failed("invalid_response");
        }
        if ("failed".equals(response.status())) {
            boolean valid = INTERNAL_FAILURES.contains(response.failureCategory())
                    && response.vendor() == null
                    && response.totalAmount() == null
                    && response.invoiceDate() == null
                    && response.lineItems().isEmpty();
            return valid
                    ? ClientResult.failed(response.failureCategory())
                    : ClientResult.failed("invalid_response");
        }
        if (!"succeeded".equals(response.status()) || response.failureCategory() != null) {
            return ClientResult.failed("invalid_response");
        }
        for (int index = 0; index < response.lineItems().size(); index++) {
            var item = response.lineItems().get(index);
            if (item.lineNumber() != index + 1
                    || item.description() == null
                    || item.description().isBlank()
                    || item.amount() == null) {
                return ClientResult.failed("invalid_response");
            }
        }
        for (var flag : response.validationFlags()) {
            if (flag == null
                    || !FLAG_CODES.contains(flag.code())
                    || !FLAG_FIELDS.contains(flag.field())
                    || flag.message() == null
                    || flag.message().isBlank()) {
                return ClientResult.failed("invalid_response");
            }
        }
        return ClientResult.completed(response);
    }

    private byte[] multipart(
            String boundary,
            UUID requestId,
            String mimeType,
            byte[] content) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            field(output, boundary, "request_id", requestId.toString());
            field(output, boundary, "schema_version", "1");
            field(output, boundary, "mime_type", mimeType);
            output.write(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"invoice\"\r\n"
                    + "Content-Type: " + mimeType + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            output.write(content);
            output.write(("\r\n--" + boundary + "--\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not build extraction request", exception);
        }
    }

    private void field(
            ByteArrayOutputStream output,
            String boundary,
            String name,
            String value) throws IOException {
        output.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
}
