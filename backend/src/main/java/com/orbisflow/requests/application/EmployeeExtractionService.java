package com.orbisflow.requests.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.orbisflow.audit.persistence.AuditLogRepository;
import com.orbisflow.auth.domain.JwtService.AuthenticatedUser;
import com.orbisflow.common.errors.ApiErrorCode;
import com.orbisflow.common.errors.ApiException;
import com.orbisflow.documents.persistence.DocumentRepository;
import com.orbisflow.requests.api.RequestDtos.CorrectionResult;
import com.orbisflow.requests.api.RequestDtos.ExtractionView;
import com.orbisflow.requests.api.RequestDtos.RequestDetail;
import com.orbisflow.requests.api.RequestDtos.RequestSummary;
import com.orbisflow.requests.domain.ExtractedInvoiceData;
import com.orbisflow.requests.domain.ExtractedInvoiceData.ExtractionStatus;
import com.orbisflow.requests.domain.ExtractedInvoiceData.InvoiceLineItem;
import com.orbisflow.requests.domain.Request;
import com.orbisflow.requests.domain.RequestStatus;
import com.orbisflow.requests.persistence.ExtractedInvoiceDataRepository;
import com.orbisflow.requests.persistence.RequestRepository;
import com.orbisflow.users.domain.UserRole;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EmployeeExtractionService {
    private static final Set<String> PATCH_FIELDS = Set.of(
            "expected_version", "vendor", "total_amount", "invoice_date", "line_items");
    private static final Set<String> EDITABLE_FIELDS = Set.of(
            "vendor", "total_amount", "invoice_date", "line_items");

    private final RequestRepository requests;
    private final ExtractedInvoiceDataRepository extractedData;
    private final DocumentRepository documents;
    private final InvoiceValidationService validation;
    private final AuditLogRepository audit;
    private final ExtractionCoordinator extraction;
    private final TransactionTemplate transactions;

    public EmployeeExtractionService(
            RequestRepository requests,
            ExtractedInvoiceDataRepository extractedData,
            DocumentRepository documents,
            InvoiceValidationService validation,
            AuditLogRepository audit,
            ExtractionCoordinator extraction,
            TransactionTemplate transactions) {
        this.requests = requests;
        this.extractedData = extractedData;
        this.documents = documents;
        this.validation = validation;
        this.audit = audit;
        this.extraction = extraction;
        this.transactions = transactions;
    }

    public ExtractionView get(AuthenticatedUser principal, UUID requestId) {
        scopedRequest(principal, requestId);
        return ExtractionView.from(requiredExtraction(requestId));
    }

    public CorrectionResult correct(
            AuthenticatedUser principal,
            UUID requestId,
            JsonNode body) {
        requireEmployee(principal);
        CorrectionPatch patch = parsePatch(body);
        Request current = ownedEditableRequest(principal.id(), requestId);
        requireVersion(current, patch.expectedVersion());
        ExtractedInvoiceData existing = requiredExtraction(requestId);

        String vendor = patch.fields().contains("vendor")
                ? patch.vendor()
                : existing.vendor();
        BigDecimal total = patch.fields().contains("total_amount")
                ? patch.totalAmount()
                : existing.totalAmount();
        LocalDate date = patch.fields().contains("invoice_date")
                ? patch.invoiceDate()
                : existing.invoiceDate();
        List<InvoiceLineItem> items = patch.fields().contains("line_items")
                ? patch.lineItems()
                : existing.lineItems();
        var checked = validation.validate(vendor, total, date, items);

        transactions.executeWithoutResult(status -> {
            if (requests.saveCorrectionVersion(
                    requestId, principal.id(), patch.expectedVersion()) != 1) {
                throw versionConflict();
            }
            extractedData.saveCorrection(
                    requestId,
                    checked.vendor(),
                    checked.totalAmount(),
                    checked.invoiceDate(),
                    checked.lineItems(),
                    checked.flags());
            audit.appendUser(
                    requestId,
                    principal.id(),
                    "field_correction",
                    current.status(),
                    current.status(),
                    Map.of("fields", patch.fields()));
        });
        Request updated = requiredOwned(requestId, principal.id());
        return new CorrectionResult(
                requestId,
                updated.version(),
                ExtractionView.from(requiredExtraction(requestId)));
    }

    public RequestDetail resubmit(
            AuthenticatedUser principal,
            UUID requestId,
            Long expectedVersion) {
        requireEmployee(principal);
        long version = requireExpectedVersion(expectedVersion);
        Request current = ownedEditableRequest(principal.id(), requestId);
        requireVersion(current, version);
        ExtractedInvoiceData data = requiredExtraction(requestId);
        var checked = validation.validate(
                data.vendor(), data.totalAmount(), data.invoiceDate(), data.lineItems());
        validation.requireRoutable(checked);

        transactions.executeWithoutResult(status -> {
            if (requests.resubmit(requestId, principal.id(), version) != 1) {
                throw versionConflict();
            }
            if (current.status() == RequestStatus.REJECTED) {
                audit.appendUser(
                        requestId,
                        principal.id(),
                        "resubmission",
                        RequestStatus.REJECTED,
                        RequestStatus.EMPLOYEE_REVIEW,
                        Map.of("manager_decision_reset", true));
            }
            audit.appendUser(
                    requestId,
                    principal.id(),
                    "routing",
                    current.status() == RequestStatus.REJECTED
                            ? RequestStatus.EMPLOYEE_REVIEW
                            : current.status(),
                    RequestStatus.MANAGER_REVIEW,
                    Map.of("route", "manager_review"));
        });
        Request updated = requiredOwned(requestId, principal.id());
        return RequestDetail.from(
                updated,
                requiredExtraction(requestId),
                documents.findCurrentByRequestId(requestId).orElse(null));
    }

    public RequestSummary retry(
            AuthenticatedUser principal,
            UUID requestId,
            Long expectedVersion,
            String correlationId) {
        requireEmployee(principal);
        long version = requireExpectedVersion(expectedVersion);
        Request current = requiredOwned(requestId, principal.id());
        ExtractedInvoiceData data = requiredExtraction(requestId);
        if (current.status() != RequestStatus.UPLOADED_EXTRACTING) {
            throw stateConflict("Extraction retry is not allowed in the current Request state.");
        }
        if (data.status() == ExtractionStatus.PENDING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXTRACTION_IN_PROGRESS,
                    "An extraction attempt is already in progress.");
        }
        if (data.status() != ExtractionStatus.FAILED) {
            throw stateConflict("Only a failed extraction can be retried.");
        }
        requireVersion(current, version);

        transactions.executeWithoutResult(status -> {
            if (requests.beginRetry(requestId, principal.id(), version) != 1) {
                throw versionConflict();
            }
            extractedData.resetPending(requestId);
            audit.appendUser(
                    requestId,
                    principal.id(),
                    "extraction",
                    RequestStatus.UPLOADED_EXTRACTING,
                    RequestStatus.UPLOADED_EXTRACTING,
                    Map.of("action", "retry_started"));
        });
        Request updated = requiredOwned(requestId, principal.id());
        extraction.start(requestId, updated.version(), correlationId);
        return RequestSummary.from(updated, requiredExtraction(requestId));
    }

    private Request scopedRequest(AuthenticatedUser principal, UUID requestId) {
        Request request = requests.findById(requestId).orElseThrow(
                EmployeeExtractionService::notFound);
        boolean allowed = switch (principal.role()) {
            case EMPLOYEE -> request.employeeId().equals(principal.id());
            case MANAGER -> request.managerId().equals(principal.id())
                    && request.status() != RequestStatus.UPLOADED_EXTRACTING
                    && request.status() != RequestStatus.EMPLOYEE_REVIEW;
            case FINANCE -> request.status() == RequestStatus.FINANCE_REVIEW
                    || request.status() == RequestStatus.PROCESSED;
        };
        if (!allowed) {
            throw notFound();
        }
        return request;
    }

    private Request ownedEditableRequest(UUID employeeId, UUID requestId) {
        Request request = requiredOwned(requestId, employeeId);
        if (request.status() != RequestStatus.EMPLOYEE_REVIEW
                && request.status() != RequestStatus.REJECTED) {
            throw stateConflict("Extracted data is not editable in the current Request state.");
        }
        return request;
    }

    private Request requiredOwned(UUID requestId, UUID employeeId) {
        return requests.findOwnedById(requestId, employeeId)
                .orElseThrow(EmployeeExtractionService::notFound);
    }

    private ExtractedInvoiceData requiredExtraction(UUID requestId) {
        return extractedData.findByRequestId(requestId)
                .orElseThrow(EmployeeExtractionService::notFound);
    }

    private CorrectionPatch parsePatch(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw invalid("The correction body must be a JSON object.");
        }
        Set<String> supplied = new HashSet<>();
        body.fieldNames().forEachRemaining(supplied::add);
        if (!PATCH_FIELDS.containsAll(supplied)) {
            throw invalid("The correction body contains an unknown field.");
        }
        Set<String> edited = new HashSet<>(supplied);
        edited.retainAll(EDITABLE_FIELDS);
        if (edited.isEmpty()) {
            throw invalid("At least one editable field is required.");
        }
        JsonNode versionNode = body.get("expected_version");
        if (versionNode == null || !versionNode.canConvertToLong()) {
            throw invalid("expected_version is required.");
        }
        long expectedVersion = requireExpectedVersion(versionNode.longValue());

        String vendor = nullableText(body, "vendor");
        BigDecimal total = nullableDecimal(body, "total_amount");
        LocalDate invoiceDate = nullableDate(body, "invoice_date");
        List<InvoiceLineItem> items = parseLineItems(body.get("line_items"));
        return new CorrectionPatch(
                expectedVersion,
                vendor,
                total,
                invoiceDate,
                items,
                Set.copyOf(edited));
    }

    private String nullableText(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw invalid(field + " must be a string or null.");
        }
        return node.textValue();
    }

    private BigDecimal nullableDecimal(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw invalid(field + " must be a decimal string or null.");
        }
        try {
            return new BigDecimal(node.textValue());
        } catch (NumberFormatException exception) {
            throw invalid(field + " is not a valid decimal.");
        }
    }

    private LocalDate nullableDate(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw invalid(field + " must be an ISO date string or null.");
        }
        try {
            return LocalDate.parse(node.textValue());
        } catch (DateTimeParseException exception) {
            throw invalid(field + " is not a valid ISO date.");
        }
    }

    private List<InvoiceLineItem> parseLineItems(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (!node.isArray()) {
            throw invalid("line_items must be an array.");
        }
        List<InvoiceLineItem> items = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode item = node.get(index);
            if (!item.isObject()
                    || item.size() != 2
                    || !item.has("description")
                    || !item.has("amount")
                    || !item.get("description").isTextual()
                    || !item.get("amount").isTextual()) {
                throw invalid("Each line item requires only description and decimal amount.");
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(item.get("amount").textValue());
            } catch (NumberFormatException exception) {
                throw invalid("A line-item amount is not a valid decimal.");
            }
            items.add(new InvoiceLineItem(
                    index + 1, item.get("description").textValue(), amount));
        }
        return List.copyOf(items);
    }

    private void requireEmployee(AuthenticatedUser principal) {
        if (principal.role() != UserRole.EMPLOYEE) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.ACCESS_DENIED,
                    "This action is not permitted.");
        }
    }

    private void requireVersion(Request request, long expectedVersion) {
        if (request.version() != expectedVersion) {
            throw versionConflict();
        }
    }

    private long requireExpectedVersion(Long value) {
        if (value == null || value < 0) {
            throw invalid("expected_version must be greater than or equal to zero.");
        }
        return value;
    }

    private static ApiException notFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.RESOURCE_NOT_FOUND,
                "The requested resource was not found.");
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_REQUEST, message);
    }

    private ApiException versionConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                ApiErrorCode.VERSION_CONFLICT,
                "The Request was modified by another operation.");
    }

    private ApiException stateConflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, ApiErrorCode.STATE_CONFLICT, message);
    }

    private record CorrectionPatch(
            long expectedVersion,
            String vendor,
            BigDecimal totalAmount,
            LocalDate invoiceDate,
            List<InvoiceLineItem> lineItems,
            Set<String> fields
    ) {
    }
}
