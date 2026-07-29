package com.orbisflow.audit.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbisflow.requests.domain.RequestStatus;
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

    private static String status(RequestStatus status) {
        return status == null ? null : status.value();
    }
}
