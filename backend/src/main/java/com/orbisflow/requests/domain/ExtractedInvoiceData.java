package com.orbisflow.requests.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ExtractedInvoiceData(
        UUID id,
        UUID requestId,
        String schemaVersion,
        ExtractionStatus status,
        String vendor,
        BigDecimal totalAmount,
        LocalDate invoiceDate,
        List<InvoiceLineItem> lineItems,
        List<ValidationFlag> validationFlags,
        String failureCategory,
        Instant createdAt,
        Instant updatedAt
) {
    public enum ExtractionStatus {
        PENDING,
        SUCCEEDED,
        FAILED;

        public String value() {
            return name().toLowerCase();
        }
    }

    public record InvoiceLineItem(
            int lineNumber,
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
}
