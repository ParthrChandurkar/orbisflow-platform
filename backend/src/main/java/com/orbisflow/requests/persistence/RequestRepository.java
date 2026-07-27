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
        return jdbc.query("""
                SELECT id, employee_id, manager_id, status::text, version,
                       created_at, updated_at
                FROM requests
                WHERE id = ? AND employee_id = ?
                """, (rs, row) -> new Request(
                rs.getObject("id", UUID.class),
                rs.getObject("employee_id", UUID.class),
                rs.getObject("manager_id", UUID.class),
                RequestStatus.fromDatabase(rs.getString("status")),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), requestId, employeeId)
                .stream().findFirst();
    }

    public int incrementVersionForDocumentReplacement(
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
}
