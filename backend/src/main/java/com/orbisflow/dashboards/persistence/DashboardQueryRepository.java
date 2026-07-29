package com.orbisflow.dashboards.persistence;

import com.orbisflow.dashboards.api.DashboardDtos.TeamActivity;
import com.orbisflow.requests.api.RequestDtos.RequestSummary;
import com.orbisflow.requests.domain.ExtractedInvoiceData;
import com.orbisflow.requests.domain.Request;
import com.orbisflow.requests.domain.RequestStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardQueryRepository {
    private final JdbcTemplate jdbc;

    public DashboardQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RequestSummary> employeeRequests(
            UUID employeeId,
            String status,
            int page,
            int size,
            String sortColumn,
            String direction) {
        String statusPredicate = status == null
                ? ""
                : " AND r.status = ?::request_status";
        String sql = """
                SELECT r.id, r.employee_id, r.manager_id, r.status::text, r.version,
                       r.manager_decision, r.manager_decided_by_user_id,
                       r.manager_decided_at, r.rejection_reason,
                       r.payment_status::text, r.processed_by_user_id, r.processed_at,
                       r.created_at, r.updated_at,
                       e.vendor, e.total_amount
                FROM requests r
                LEFT JOIN extracted_invoice_data e ON e.request_id = r.id
                WHERE r.employee_id = ?
                %s
                ORDER BY %s %s NULLS LAST, r.id %s
                LIMIT ? OFFSET ?
                """.formatted(statusPredicate, sortColumn, direction, direction);
        Object[] parameters = status == null
                ? new Object[] {employeeId, size, page * size}
                : new Object[] {employeeId, status, size, page * size};
        return jdbc.query(
                sql,
                (rs, row) -> RequestSummary.from(mapRequest(rs), summaryExtraction(rs)),
                parameters);
    }

    public long employeeRequestCount(UUID employeeId, String status) {
        Long count = status == null
                ? jdbc.queryForObject("""
                        SELECT count(*) FROM requests WHERE employee_id = ?
                        """, Long.class, employeeId)
                : jdbc.queryForObject("""
                        SELECT count(*) FROM requests
                        WHERE employee_id = ? AND status = ?::request_status
                        """, Long.class, employeeId, status);
        return count == null ? 0 : count;
    }

    public List<RequestSummary> managerRequests(
            UUID managerId,
            String status,
            int page,
            int size,
            String sortColumn,
            String direction) {
        String sql = """
                SELECT r.id, r.employee_id, r.manager_id, r.status::text, r.version,
                       r.manager_decision, r.manager_decided_by_user_id,
                       r.manager_decided_at, r.rejection_reason,
                       r.payment_status::text, r.processed_by_user_id, r.processed_at,
                       r.created_at, r.updated_at,
                       e.vendor, e.total_amount
                FROM requests r
                LEFT JOIN extracted_invoice_data e ON e.request_id = r.id
                WHERE r.manager_id = ?
                  AND r.status = ?::request_status
                ORDER BY %s %s NULLS LAST, r.id %s
                LIMIT ? OFFSET ?
                """.formatted(sortColumn, direction, direction);
        return jdbc.query(
                sql,
                (rs, row) -> RequestSummary.from(mapRequest(rs), summaryExtraction(rs)),
                managerId,
                status,
                size,
                page * size);
    }

    public long managerRequestCount(UUID managerId, String status) {
        Long count = jdbc.queryForObject("""
                SELECT count(*)
                FROM requests
                WHERE manager_id = ?
                  AND status = ?::request_status
                """, Long.class, managerId, status);
        return count == null ? 0 : count;
    }

    public List<RequestSummary> financeRequests(
            String status,
            int page,
            int size,
            String sortColumn,
            String direction) {
        String sql = """
                SELECT r.id, r.employee_id, r.manager_id, r.status::text, r.version,
                       r.manager_decision, r.manager_decided_by_user_id,
                       r.manager_decided_at, r.rejection_reason,
                       r.payment_status::text, r.processed_by_user_id, r.processed_at,
                       r.created_at, r.updated_at,
                       e.vendor, e.total_amount
                FROM requests r
                LEFT JOIN extracted_invoice_data e ON e.request_id = r.id
                WHERE r.status = ?::request_status
                ORDER BY %s %s NULLS LAST, r.id %s
                LIMIT ? OFFSET ?
                """.formatted(sortColumn, direction, direction);
        return jdbc.query(
                sql,
                (rs, row) -> RequestSummary.from(mapRequest(rs), summaryExtraction(rs)),
                status,
                size,
                page * size);
    }

    public long financeRequestCount(String status) {
        Long count = jdbc.queryForObject("""
                SELECT count(*)
                FROM requests
                WHERE status = ?::request_status
                """, Long.class, status);
        return count == null ? 0 : count;
    }

    public TeamActivity teamActivity(UUID managerId) {
        return jdbc.queryForObject("""
                SELECT
                    count(*) FILTER (WHERE r.status = 'manager_review') AS pending,
                    count(*) FILTER (
                        WHERE r.status IN ('finance_review', 'processed')
                    ) AS approved,
                    count(*) FILTER (WHERE r.status = 'rejected') AS rejected
                FROM users employee
                LEFT JOIN requests r ON r.employee_id = employee.id
                WHERE employee.role = 'employee'
                  AND employee.manager_id = ?
                """, (rs, row) -> new TeamActivity(
                rs.getLong("pending"),
                rs.getLong("approved"),
                rs.getLong("rejected")), managerId);
    }

    private static Request mapRequest(ResultSet rs) throws SQLException {
        return new Request(
                rs.getObject("id", UUID.class),
                rs.getObject("employee_id", UUID.class),
                rs.getObject("manager_id", UUID.class),
                RequestStatus.fromDatabase(rs.getString("status")),
                rs.getLong("version"),
                rs.getString("manager_decision"),
                rs.getObject("manager_decided_by_user_id", UUID.class),
                instant(rs, "manager_decided_at"),
                rs.getString("rejection_reason"),
                rs.getString("payment_status"),
                rs.getObject("processed_by_user_id", UUID.class),
                instant(rs, "processed_at"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static ExtractedInvoiceData summaryExtraction(ResultSet rs)
            throws SQLException {
        String vendor = rs.getString("vendor");
        BigDecimal total = rs.getBigDecimal("total_amount");
        if (vendor == null && total == null) {
            return null;
        }
        return new ExtractedInvoiceData(
                null,
                null,
                "1",
                ExtractedInvoiceData.ExtractionStatus.SUCCEEDED,
                vendor,
                total,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null);
    }

    private static java.time.Instant instant(ResultSet rs, String column)
            throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
