package com.orbisflow.audit.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public final class AuditDtos {
    private AuditDtos() {
    }

    public record AuditEventView(
            UUID id,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("actor_kind") String actorKind,
            @JsonProperty("actor_user_id") UUID actorUserId,
            @JsonProperty("previous_status") String previousStatus,
            @JsonProperty("resulting_status") String resultingStatus,
            JsonNode context,
            @JsonProperty("created_at") Instant createdAt
    ) {
    }
}
