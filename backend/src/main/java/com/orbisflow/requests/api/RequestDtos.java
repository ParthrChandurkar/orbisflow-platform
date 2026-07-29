package com.orbisflow.requests.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.orbisflow.documents.domain.Document;
import com.orbisflow.requests.domain.ExtractedInvoiceData;
import com.orbisflow.requests.domain.Request;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RequestDtos {
    private RequestDtos() {
    }

    public record RequestSummary(
            UUID id,
            String status,
            long version,
            @JsonProperty("employee_id") UUID employeeId,
            @JsonProperty("manager_id") UUID managerId,
            String vendor,
            @JsonProperty("total_amount") BigDecimal totalAmount,
            @JsonProperty("submitted_at") Instant submittedAt,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("latest_required_action") String latestRequiredAction
    ) {
        public static RequestSummary from(Request request) {
            return from(request, null);
        }

        public static RequestSummary from(
                Request request,
                ExtractedInvoiceData extractedData) {
            String requiredAction = switch (request.status()) {
                case EMPLOYEE_REVIEW, REJECTED -> "correct_or_resubmit";
                case MANAGER_REVIEW -> "manager_review";
                case FINANCE_REVIEW -> "finance_process";
                default -> null;
            };
            if (request.status() == com.orbisflow.requests.domain.RequestStatus
                    .UPLOADED_EXTRACTING
                    && extractedData != null
                    && extractedData.status() == ExtractedInvoiceData.ExtractionStatus.FAILED) {
                requiredAction = "retry_extraction";
            }
            return new RequestSummary(
                    request.id(), request.status().value(), request.version(),
                    request.employeeId(), request.managerId(),
                    extractedData == null ? null : extractedData.vendor(),
                    extractedData == null ? null : extractedData.totalAmount(),
                    request.createdAt(), request.updatedAt(), requiredAction);
        }
    }

    public record LineItemView(
            @JsonProperty("line_number") int lineNumber,
            String description,
            String amount
    ) {
    }

    public record ValidationFlagView(
            String code,
            String field,
            String message
    ) {
    }

    public record ExtractionView(
            String status,
            @JsonProperty("schema_version") String schemaVersion,
            String vendor,
            @JsonProperty("total_amount") String totalAmount,
            @JsonProperty("invoice_date") LocalDate invoiceDate,
            @JsonProperty("line_items") List<LineItemView> lineItems,
            @JsonProperty("validation_flags") List<ValidationFlagView> validationFlags,
            @JsonProperty("failure_category") String failureCategory
    ) {
        public static ExtractionView from(ExtractedInvoiceData data) {
            return new ExtractionView(
                    data.status().value(),
                    data.schemaVersion(),
                    data.vendor(),
                    decimal(data.totalAmount()),
                    data.invoiceDate(),
                    data.lineItems().stream()
                            .map(item -> new LineItemView(
                                    item.lineNumber(),
                                    item.description(),
                                    decimal(item.amount())))
                            .toList(),
                    data.validationFlags().stream()
                            .map(flag -> new ValidationFlagView(
                                    flag.code(), flag.field(), flag.message()))
                            .toList(),
                    data.failureCategory());
        }
    }

    public record CorrectionResult(
            @JsonProperty("request_id") UUID requestId,
            long version,
            @JsonProperty("extracted_data") ExtractionView extractedData
    ) {
    }

    public record ExpectedVersion(
            @JsonProperty("expected_version") Long expectedVersion
    ) {
    }

    public record RejectDecision(
            @JsonProperty("expected_version") Long expectedVersion,
            String reason
    ) {
    }

    public record ProcessRequest(
            @JsonProperty("expected_version") Long expectedVersion,
            @JsonProperty("payment_status") String paymentStatus
    ) {
    }

    public record DocumentView(
            UUID id,
            @JsonProperty("original_filename") String originalFilename,
            @JsonProperty("mime_type") String mimeType,
            @JsonProperty("file_size_bytes") long fileSizeBytes,
            @JsonProperty("created_at") Instant createdAt
    ) {
        public static DocumentView from(Document document) {
            return new DocumentView(
                    document.id(),
                    document.originalFilename(),
                    document.mimeType(),
                    document.fileSizeBytes(),
                    document.createdAt());
        }
    }

    public record RequestDetail(
            UUID id,
            String status,
            long version,
            @JsonProperty("employee_id") UUID employeeId,
            @JsonProperty("manager_id") UUID managerId,
            String vendor,
            @JsonProperty("total_amount") BigDecimal totalAmount,
            @JsonProperty("submitted_at") Instant submittedAt,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("latest_required_action") String latestRequiredAction,
            @JsonProperty("current_owner_role") String currentOwnerRole,
            @JsonProperty("manager_decision") ManagerDecisionView managerDecision,
            ProcessingView processing,
            DocumentView document,
            @JsonProperty("extracted_data") ExtractionView extractedData
    ) {
        public record ManagerDecisionView(
                String decision,
                @JsonProperty("decided_by_user_id") UUID decidedByUserId,
                @JsonProperty("decided_at") Instant decidedAt,
                @JsonProperty("rejection_reason") String rejectionReason
        ) {
        }

        public record ProcessingView(
                @JsonProperty("payment_status") String paymentStatus,
                @JsonProperty("processed_by_user_id") UUID processedByUserId,
                @JsonProperty("processed_at") Instant processedAt
        ) {
        }

        public static RequestDetail from(
                Request request,
                ExtractedInvoiceData data,
                Document document) {
            RequestSummary summary = RequestSummary.from(request, data);
            String ownerRole = switch (request.status()) {
                case EMPLOYEE_REVIEW, REJECTED -> "employee";
                case MANAGER_REVIEW -> "manager";
                case FINANCE_REVIEW -> "finance";
                default -> null;
            };
            ManagerDecisionView decision = request.managerDecision() == null
                    ? null
                    : new ManagerDecisionView(
                            request.managerDecision(),
                            request.managerDecidedByUserId(),
                            request.managerDecidedAt(),
                            request.rejectionReason());
            ProcessingView processing = request.paymentStatus() == null
                    ? null
                    : new ProcessingView(
                            request.paymentStatus(),
                            request.processedByUserId(),
                            request.processedAt());
            return new RequestDetail(
                    summary.id(), summary.status(), summary.version(),
                    summary.employeeId(), summary.managerId(), summary.vendor(),
                    summary.totalAmount(), summary.submittedAt(), summary.updatedAt(),
                    summary.latestRequiredAction(), ownerRole, decision, processing,
                    document == null ? null : DocumentView.from(document),
                    data == null ? null : ExtractionView.from(data));
        }
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
