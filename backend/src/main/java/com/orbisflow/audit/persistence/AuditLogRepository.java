package com.orbisflow.audit.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.orbisflow.requests.domain.RequestStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditLogRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void appendSystem(
            UUID requestId,
            String eventType,
            RequestStatus previous,
            RequestStatus resulting,
            Map<String, ?> context) {
        append(requestId, eventType, "system", null, previous, resulting, context);
    }

    public void appendUser(
            UUID requestId,
            UUID actorId,
            String eventType,
            RequestStatus previous,
            RequestStatus resulting,
            Map<String, ?> context) {
        append(requestId, eventType, "user", actorId, previous, resulting, context);
    }

    public List<AuditRecord> findPage(UUID requestId, int page, int size) {
        return jdbc.query("""
                SELECT id, event_type, actor_kind, actor_user_id,
                       previous_status::text, resulting_status::text,
                       context::text, created_at
                FROM audit_log
                WHERE request_id = ?
                ORDER BY created_at ASC, id ASC
                LIMIT ? OFFSET ?
                """, (rs, row) -> new AuditRecord(
                rs.getObject("id", UUID.class),
                rs.getString("event_type"),
                rs.getString("actor_kind"),
                rs.getObject("actor_user_id", UUID.class),
                rs.getString("previous_status"),
                rs.getString("resulting_status"),
                parseJson(rs.getString("context")),
                rs.getTimestamp("created_at").toInstant()),
                requestId,
                size,
                page * size);
    }

    public long count(UUID requestId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE request_id = ?",
                Long.class,
                requestId);
        return count == null ? 0 : count;
    }

    private void append(
            UUID requestId,
            String eventType,
            String actorKind,
            UUID actorId,
            RequestStatus previous,
            RequestStatus resulting,
            Map<String, ?> context) {
        jdbc.update("""
                INSERT INTO audit_log (
                    id, request_id, event_type, actor_kind, actor_user_id,
                    previous_status, resulting_status, context
                ) VALUES (?, ?, ?, ?, ?, ?::request_status, ?::request_status, ?::jsonb)
                """,
                UUID.randomUUID(), requestId, eventType, actorKind, actorId,
                status(previous), status(resulting), json(context));
    }

    private String json(Map<String, ?> context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize audit context", exception);
        }
    }

    private JsonNode parseJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not parse audit context", exception);
        }
    }

    private static String status(RequestStatus status) {
        return status == null ? null : status.value();
    }

    public record AuditRecord(
            UUID id,
            String eventType,
            String actorKind,
            UUID actorUserId,
            String previousStatus,
            String resultingStatus,
            JsonNode context,
            Instant createdAt
    ) {
    }
}
