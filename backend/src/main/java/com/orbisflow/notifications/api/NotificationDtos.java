package com.orbisflow.notifications.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.orbisflow.notifications.domain.Notification;
import java.time.Instant;
import java.util.UUID;

public final class NotificationDtos {
    private NotificationDtos() {
    }

    public record NotificationView(
            UUID id,
            @JsonProperty("request_id") UUID requestId,
            String type,
            @JsonProperty("read_at") Instant readAt,
            @JsonProperty("created_at") Instant createdAt
    ) {
        public static NotificationView from(Notification notification) {
            return new NotificationView(
                    notification.id(),
                    notification.requestId(),
                    notification.type(),
                    notification.readAt(),
                    notification.createdAt());
        }
    }
}
