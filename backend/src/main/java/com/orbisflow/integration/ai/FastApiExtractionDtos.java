package com.orbisflow.integration.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FastApiExtractionDtos {
    private FastApiExtractionDtos() {
    }

    public record ExtractionResponse(
            @JsonProperty("request_id") UUID requestId,
            @JsonProperty("schema_version") String schemaVersion,
            String status,
            String vendor,
            @JsonProperty("total_amount") BigDecimal totalAmount,
            @JsonProperty("invoice_date") LocalDate invoiceDate,
            @JsonProperty("line_items") List<LineItem> lineItems,
            @JsonProperty("validation_flags") List<ValidationFlag> validationFlags,
            @JsonProperty("failure_category") String failureCategory
    ) {
    }

    public record LineItem(
            @JsonProperty("line_number") int lineNumber,
            String description,
            BigDecimal amount
    ) {
    }

    public record ValidationFlag(
            String code,
            String field,
            String message
    ) {
    }

    public record ClientResult(
            ExtractionResponse response,
            String failureCategory
    ) {
        public static ClientResult completed(ExtractionResponse response) {
            return new ClientResult(response, null);
        }

        public static ClientResult failed(String failureCategory) {
            return new ClientResult(null, failureCategory);
        }
    }
}
