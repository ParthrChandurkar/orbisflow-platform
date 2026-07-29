package com.orbisflow.requests.persistence;

import com.orbisflow.requests.domain.Request;
import com.orbisflow.requests.domain.RequestStatus;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RequestRepository {
    private final JdbcTemplate jdbc;

    public RequestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Request request) {
        jdbc.update("""
                INSERT INTO requests (
                    id, employee_id, manager_id, status, version, created_at, updated_at
                ) VALUES (?, ?, ?, 'uploaded_extracting', ?, ?, ?)
                """,
                request.id(), request.employeeId(), request.managerId(), request.version(),
                Timestamp.from(request.createdAt()), Timestamp.from(request.updatedAt()));
    }

    public Optional<Request> findOwnedById(UUID requestId, UUID employeeId) {
        return queryOne("""
                SELECT id, employee_id, manager_id, status::text, version,
                       manager_decision, manager_decided_by_user_id,
                       manager_decided_at, rejection_reason,
                       payment_status::text, processed_by_user_id, processed_at,
                       created_at, updated_at
                FROM requests
                WHERE id = ? AND employee_id = ?
                """, requestId, employeeId);
    }

    public Optional<Request> findById(UUID requestId) {
        return queryOne("""
                SELECT id, employee_id, manager_id, status::text, version,
                       manager_decision, manager_decided_by_user_id,
                       manager_decided_at, rejection_reason,
                       payment_status::text, processed_by_user_id, processed_at,
                       created_at, updated_at
                FROM requests
                WHERE id = ?
                """, requestId);
    }

    public Optional<Request> findAssignedPostRouting(UUID requestId, UUID managerId) {
        return queryOne("""
                SELECT id, employee_id, manager_id, status::text, version,
                       manager_decision, manager_decided_by_user_id,
                       manager_decided_at, rejection_reason,
                       payment_status::text, processed_by_user_id, processed_at,
                       created_at, updated_at
                FROM requests
                WHERE id = ?
                  AND manager_id = ?
                  AND status IN ('manager_review', 'rejected', 'finance_review', 'processed')
                """, requestId, managerId);
    }

    private Optional<Request> queryOne(String sql, Object... arguments) {
        return jdbc.query(sql, (rs, row) -> new Request(
                rs.getObject("id", UUID.class),
                rs.getObject("employee_id", UUID.class),
                rs.getObject("manager_id", UUID.class),
                RequestStatus.fromDatabase(rs.getString("status")),
                rs.getLong("version"),
                rs.getString("manager_decision"),
                rs.getObject("manager_decided_by_user_id", UUID.class),
                instant(rs.getTimestamp("manager_decided_at")),
                rs.getString("rejection_reason"),
                rs.getString("payment_status"),
                rs.getObject("processed_by_user_id", UUID.class),
                instant(rs.getTimestamp("processed_at")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), arguments)
                .stream().findFirst();
    }

    public int beginDocumentReplacement(
            UUID requestId, UUID employeeId, long expectedVersion) {
        return jdbc.update("""
                UPDATE requests
                SET status = 'uploaded_extracting',
                    version = version + CASE WHEN status = 'rejected' THEN 2 ELSE 1 END,
                    manager_decision = NULL,
                    manager_decided_by_user_id = NULL,
                    manager_decided_at = NULL,
                    rejection_reason = NULL,
                    updated_at = now()
                WHERE id = ?
                  AND employee_id = ?
                  AND version = ?
                  AND status IN ('employee_review', 'rejected')
                """, requestId, employeeId, expectedVersion);
    }

    public int transitionAfterExtraction(
            UUID requestId,
            long attemptVersion,
            RequestStatus resultingStatus) {
        return jdbc.update("""
                UPDATE requests
                SET status = ?::request_status,
                    version = version + 1,
                    updated_at = now()
                WHERE id = ?
                  AND version = ?
                  AND status = 'uploaded_extracting'
                """, resultingStatus.value(), requestId, attemptVersion);
    }

    public int beginRetry(UUID requestId, UUID employeeId, long expectedVersion) {
        return jdbc.update("""
                UPDATE requests r
                SET version = version + 1, updated_at = now()
                WHERE r.id = ?
                  AND r.employee_id = ?
                  AND r.version = ?
                  AND r.status = 'uploaded_extracting'
                  AND EXISTS (
                      SELECT 1 FROM extracted_invoice_data e
                      WHERE e.request_id = r.id AND e.extraction_status = 'failed'
                  )
                """, requestId, employeeId, expectedVersion);
    }

    public int saveCorrectionVersion(
            UUID requestId, UUID employeeId, long expectedVersion) {
        return jdbc.update("""
                UPDATE requests
                SET version = version + 1, updated_at = now()
                WHERE id = ?
                  AND employee_id = ?
                  AND version = ?
                  AND status IN ('employee_review', 'rejected')
                """, requestId, employeeId, expectedVersion);
    }

    public int resubmit(UUID requestId, UUID employeeId, long expectedVersion) {
        return jdbc.update("""
                UPDATE requests
                SET status = 'manager_review',
                    version = version + CASE WHEN status = 'rejected' THEN 2 ELSE 1 END,
                    manager_decision = NULL,
                    manager_decided_by_user_id = NULL,
                    manager_decided_at = NULL,
                    rejection_reason = NULL,
                    updated_at = now()
                WHERE id = ?
                  AND employee_id = ?
                  AND version = ?
                  AND status IN ('employee_review', 'rejected')
                """, requestId, employeeId, expectedVersion);
    }

    public int approve(UUID requestId, UUID managerId, long expectedVersion) {
        return jdbc.update("""
                UPDATE requests
                SET status = 'finance_review',
                    manager_decision = 'approved',
                    manager_decided_by_user_id = ?,
                    manager_decided_at = now(),
                    rejection_reason = NULL,
                    version = version + 1,
                    updated_at = now()
                WHERE id = ?
                  AND manager_id = ?
                  AND version = ?
                  AND status = 'manager_review'
                """, managerId, requestId, managerId, expectedVersion);
    }

    public int reject(
            UUID requestId,
            UUID managerId,
            long expectedVersion,
            String reason) {
        return jdbc.update("""
                UPDATE requests
                SET status = 'rejected',
                    manager_decision = 'rejected',
                    manager_decided_by_user_id = ?,
                    manager_decided_at = now(),
                    rejection_reason = ?,
                    version = version + 1,
                    updated_at = now()
                WHERE id = ?
                  AND manager_id = ?
                  AND version = ?
                  AND status = 'manager_review'
                """, managerId, reason, requestId, managerId, expectedVersion);
    }

    public int process(
            UUID requestId,
            UUID financeUserId,
            long expectedVersion,
            String paymentStatus) {
        return jdbc.update("""
                UPDATE requests
                SET status = 'processed',
                    payment_status = ?::payment_status,
                    processed_by_user_id = ?,
                    processed_at = now(),
                    version = version + 1,
                    updated_at = now()
                WHERE id = ?
                  AND version = ?
                  AND status = 'finance_review'
                """,
                paymentStatus,
                financeUserId,
                requestId,
                expectedVersion);
    }

    private static java.time.Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
