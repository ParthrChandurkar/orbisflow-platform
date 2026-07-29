package com.orbisflow.notifications.domain;

import java.time.Instant;
import java.util.UUID;

public record Notification(
        UUID id,
        UUID userId,
        UUID requestId,
        String type,
        Instant readAt,
        Instant createdAt
) {
}
