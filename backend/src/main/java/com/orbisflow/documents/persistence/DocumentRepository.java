package com.orbisflow.documents.persistence;

import com.orbisflow.documents.domain.Document;
import com.orbisflow.requests.domain.RequestStatus;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {
    private final JdbcTemplate jdbc;

    public DocumentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Document document) {
        jdbc.update("""
                INSERT INTO documents (
                    id, request_id, uploaded_by_user_id, s3_object_key,
                    original_filename, mime_type, file_size_bytes, is_current, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                document.id(), document.requestId(), document.uploadedByUserId(),
                document.s3ObjectKey(), document.originalFilename(), document.mimeType(),
                document.fileSizeBytes(), document.current(),
                Timestamp.from(document.createdAt()));
    }

    public int clearCurrent(UUID requestId) {
        return jdbc.update("""
                UPDATE documents SET is_current = false
                WHERE request_id = ? AND is_current = true
                """, requestId);
    }

    public Optional<ScopedDocument> findScopedById(UUID documentId) {
        return jdbc.query("""
                SELECT d.id, d.request_id, d.uploaded_by_user_id, d.s3_object_key,
                       d.original_filename, d.mime_type, d.file_size_bytes,
                       d.is_current, d.created_at,
                       r.employee_id, r.manager_id, r.status::text
                FROM documents d
                JOIN requests r ON r.id = d.request_id
                WHERE d.id = ?
                """, (rs, row) -> new ScopedDocument(
                new Document(
                        rs.getObject("id", UUID.class),
                        rs.getObject("request_id", UUID.class),
                        rs.getObject("uploaded_by_user_id", UUID.class),
                        rs.getString("s3_object_key"),
                        rs.getString("original_filename"),
                        rs.getString("mime_type"),
                        rs.getLong("file_size_bytes"),
                        rs.getBoolean("is_current"),
                        rs.getTimestamp("created_at").toInstant()),
                rs.getObject("employee_id", UUID.class),
                rs.getObject("manager_id", UUID.class),
                RequestStatus.fromDatabase(rs.getString("status"))), documentId)
                .stream().findFirst();
    }

    public Optional<Document> findCurrentByRequestId(UUID requestId) {
        return jdbc.query("""
                SELECT id, request_id, uploaded_by_user_id, s3_object_key,
                       original_filename, mime_type, file_size_bytes,
                       is_current, created_at
                FROM documents
                WHERE request_id = ? AND is_current
                """, (rs, row) -> new Document(
                rs.getObject("id", UUID.class),
                rs.getObject("request_id", UUID.class),
                rs.getObject("uploaded_by_user_id", UUID.class),
                rs.getString("s3_object_key"),
                rs.getString("original_filename"),
                rs.getString("mime_type"),
                rs.getLong("file_size_bytes"),
                rs.getBoolean("is_current"),
                rs.getTimestamp("created_at").toInstant()), requestId)
                .stream().findFirst();
    }

    public record ScopedDocument(
            Document document,
            UUID employeeId,
            UUID managerId,
            RequestStatus requestStatus
    ) {
    }
}
