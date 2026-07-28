package com.orbisflow.requests.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbisflow.requests.domain.ExtractedInvoiceData;
import com.orbisflow.requests.domain.ExtractedInvoiceData.ExtractionStatus;
import com.orbisflow.requests.domain.ExtractedInvoiceData.InvoiceLineItem;
import com.orbisflow.requests.domain.ExtractedInvoiceData.ValidationFlag;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ExtractedInvoiceDataRepository {
    private static final TypeReference<List<ValidationFlag>> FLAGS_TYPE =
            new TypeReference<>() {
            };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final InvoiceLineItemRepository lineItems;

    public ExtractedInvoiceDataRepository(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            InvoiceLineItemRepository lineItems) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.lineItems = lineItems;
    }

    public UUID createPending(UUID requestId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO extracted_invoice_data (
                    id, request_id, schema_version, extraction_status
                ) VALUES (?, ?, '1', 'pending')
                """, id, requestId);
        return id;
    }

    public void resetPending(UUID requestId) {
        UUID extractionId = findId(requestId).orElseGet(() -> createPending(requestId));
        lineItems.replace(extractionId, List.of());
        jdbc.update("""
                UPDATE extracted_invoice_data
                SET schema_version = '1',
                    extraction_status = 'pending',
                    vendor = NULL,
                    total_amount = NULL,
                    invoice_date = NULL,
                    validation_flags = '[]'::jsonb,
                    failure_category = NULL,
                    updated_at = now()
                WHERE id = ?
                """, extractionId);
    }

    public void saveSucceeded(
            UUID requestId,
            String vendor,
            BigDecimal totalAmount,
            LocalDate invoiceDate,
            List<InvoiceLineItem> items,
            List<ValidationFlag> flags) {
        UUID extractionId = requiredId(requestId);
        lineItems.replace(extractionId, items);
        jdbc.update("""
                UPDATE extracted_invoice_data
                SET extraction_status = 'succeeded',
                    vendor = ?,
                    total_amount = ?,
                    invoice_date = ?,
                    validation_flags = ?::jsonb,
                    failure_category = NULL,
                    updated_at = now()
                WHERE id = ?
                """, vendor, totalAmount, sqlDate(invoiceDate), flagsJson(flags), extractionId);
    }

    public void saveFailed(UUID requestId, String failureCategory) {
        UUID extractionId = requiredId(requestId);
        lineItems.replace(extractionId, List.of());
        jdbc.update("""
                UPDATE extracted_invoice_data
                SET extraction_status = 'failed',
                    vendor = NULL,
                    total_amount = NULL,
                    invoice_date = NULL,
                    validation_flags = '[]'::jsonb,
                    failure_category = ?,
                    updated_at = now()
                WHERE id = ?
                """, failureCategory, extractionId);
    }

    public void saveCorrection(
            UUID requestId,
            String vendor,
            BigDecimal totalAmount,
            LocalDate invoiceDate,
            List<InvoiceLineItem> items,
            List<ValidationFlag> flags) {
        saveSucceeded(requestId, vendor, totalAmount, invoiceDate, items, flags);
    }

    public Optional<ExtractedInvoiceData> findByRequestId(UUID requestId) {
        return jdbc.query("""
                SELECT id, request_id, schema_version, extraction_status,
                       vendor, total_amount, invoice_date, validation_flags::text,
                       failure_category, created_at, updated_at
                FROM extracted_invoice_data
                WHERE request_id = ?
                """, (rs, row) -> {
            UUID id = rs.getObject("id", UUID.class);
            return new ExtractedInvoiceData(
                    id,
                    rs.getObject("request_id", UUID.class),
                    rs.getString("schema_version"),
                    ExtractionStatus.valueOf(rs.getString("extraction_status").toUpperCase()),
                    rs.getString("vendor"),
                    rs.getBigDecimal("total_amount"),
                    rs.getObject("invoice_date", LocalDate.class),
                    lineItems.findByExtractionId(id),
                    parseFlags(rs.getString("validation_flags")),
                    rs.getString("failure_category"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant());
        }, requestId).stream().findFirst();
    }

    public Optional<UUID> findId(UUID requestId) {
        return jdbc.query(
                "SELECT id FROM extracted_invoice_data WHERE request_id = ?",
                (rs, row) -> rs.getObject("id", UUID.class),
                requestId).stream().findFirst();
    }

    private UUID requiredId(UUID requestId) {
        return findId(requestId).orElseThrow(
                () -> new IllegalStateException("Extraction row is missing"));
    }

    private String flagsJson(List<ValidationFlag> flags) {
        try {
            return objectMapper.writeValueAsString(flags);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize validation flags", exception);
        }
    }

    private List<ValidationFlag> parseFlags(String json) {
        try {
            return objectMapper.readValue(json, FLAGS_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored validation flags are invalid", exception);
        }
    }

    private static Date sqlDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }
}
