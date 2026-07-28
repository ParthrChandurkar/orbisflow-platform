package com.orbisflow.requests.application;

import com.orbisflow.common.errors.ApiErrorCode;
import com.orbisflow.common.errors.ApiException;
import com.orbisflow.requests.domain.ExtractedInvoiceData.InvoiceLineItem;
import com.orbisflow.requests.domain.ExtractedInvoiceData.ValidationFlag;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class InvoiceValidationService {
    public ValidationResult validate(
            String vendor,
            BigDecimal totalAmount,
            LocalDate invoiceDate,
            List<InvoiceLineItem> lineItems) {
        List<ValidationFlag> flags = new ArrayList<>();
        if (vendor == null || vendor.isBlank()) {
            flags.add(new ValidationFlag(
                    "MISSING_VENDOR", "vendor", "Vendor is required."));
        }
        if (totalAmount == null) {
            flags.add(new ValidationFlag(
                    "MISSING_TOTAL_AMOUNT", "total_amount", "Total amount is required."));
        }
        if (invoiceDate == null) {
            flags.add(new ValidationFlag(
                    "MISSING_INVOICE_DATE", "invoice_date", "Invoice date is required."));
        }
        List<InvoiceLineItem> normalizedItems = normalizeItems(lineItems);
        BigDecimal normalizedTotal = normalize(totalAmount);
        if (!normalizedItems.isEmpty() && normalizedTotal != null) {
            BigDecimal sum = normalizedItems.stream()
                    .map(InvoiceLineItem::amount)
                    .reduce(BigDecimal.ZERO.setScale(4), BigDecimal::add);
            if (sum.compareTo(normalizedTotal) != 0) {
                flags.add(new ValidationFlag(
                        "LINE_ITEM_SUM_MISMATCH",
                        "line_items",
                        "Line-item amounts must sum exactly to the total amount."));
            }
        }
        return new ValidationResult(
                vendor == null ? null : vendor.trim(),
                normalizedTotal,
                invoiceDate,
                normalizedItems,
                List.copyOf(flags));
    }

    public void requireRoutable(ValidationResult result) {
        if (!result.flags().isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.VALIDATION_FAILED,
                    "Required invoice fields or line-item totals are invalid.");
        }
    }

    private List<InvoiceLineItem> normalizeItems(List<InvoiceLineItem> items) {
        List<InvoiceLineItem> normalized = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            InvoiceLineItem item = items.get(index);
            if (item.description() == null || item.description().isBlank()
                    || item.amount() == null) {
                throw invalidRequest("Every line item requires a description and amount.");
            }
            normalized.add(new InvoiceLineItem(
                    index + 1, item.description().trim(), normalizeRequired(item.amount())));
        }
        return List.copyOf(normalized);
    }

    private BigDecimal normalize(BigDecimal value) {
        return value == null ? null : normalizeRequired(value);
    }

    private BigDecimal normalizeRequired(BigDecimal value) {
        try {
            BigDecimal normalized = value.setScale(4, RoundingMode.UNNECESSARY);
            if (normalized.precision() - normalized.scale() > 15) {
                throw invalidRequest("Monetary values support at most 15 integer digits.");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw invalidRequest("Monetary values support at most four fractional digits.");
        }
    }

    private ApiException invalidRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_REQUEST, message);
    }

    public record ValidationResult(
            String vendor,
            BigDecimal totalAmount,
            LocalDate invoiceDate,
            List<InvoiceLineItem> lineItems,
            List<ValidationFlag> flags
    ) {
    }
}
