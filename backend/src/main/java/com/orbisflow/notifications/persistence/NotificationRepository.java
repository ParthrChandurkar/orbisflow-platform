package com.orbisflow.notifications.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {
    private final JdbcTemplate jdbc;

    public NotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID userId, UUID requestId, String type) {
        jdbc.update("""
                INSERT INTO notifications (id, user_id, request_id, type)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), userId, requestId, type);
    }
}
