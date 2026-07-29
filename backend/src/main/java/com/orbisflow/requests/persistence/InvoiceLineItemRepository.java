package com.orbisflow.requests.persistence;

import com.orbisflow.requests.domain.ExtractedInvoiceData.InvoiceLineItem;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InvoiceLineItemRepository {
    private final JdbcTemplate jdbc;

    public InvoiceLineItemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<InvoiceLineItem> findByExtractionId(UUID extractionId) {
        return jdbc.query("""
                SELECT line_number, description, amount
                FROM invoice_line_items
                WHERE extracted_invoice_data_id = ?
                ORDER BY line_number
                """, (rs, row) -> new InvoiceLineItem(
                rs.getInt("line_number"),
                rs.getString("description"),
                rs.getBigDecimal("amount")), extractionId);
    }

    public void replace(UUID extractionId, List<InvoiceLineItem> items) {
        jdbc.queryForObject(
                "SELECT clear_invoice_line_items(?)",
                Void.class,
                extractionId);
        for (InvoiceLineItem item : items) {
            jdbc.update("""
                    INSERT INTO invoice_line_items (
                        id, extracted_invoice_data_id, line_number, description, amount
                    ) VALUES (?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(), extractionId, item.lineNumber(),
                    item.description(), item.amount());
        }
    }
}
