package com.orbisflow.notifications.persistence;

import com.orbisflow.notifications.domain.Notification;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
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

    public List<Notification> findOwned(
            UUID userId,
            boolean unread,
            int page,
            int size,
            int datasetLimit) {
        if (unread) {
            return jdbc.query("""
                    SELECT id, user_id, request_id, type, read_at, created_at
                    FROM notifications
                    WHERE user_id = ? AND read_at IS NULL
                    ORDER BY created_at DESC, id DESC
                    LIMIT ? OFFSET ?
                    """, NotificationRepository::map, userId, size, page * size);
        }
        return jdbc.query("""
                SELECT id, user_id, request_id, type, read_at, created_at
                FROM (
                    SELECT id, user_id, request_id, type, read_at, created_at
                    FROM notifications
                    WHERE user_id = ?
                    ORDER BY created_at DESC, id DESC
                    LIMIT ?
                ) recent
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """, NotificationRepository::map,
                userId, datasetLimit, size, page * size);
    }

    public long countOwned(UUID userId, boolean unread, int datasetLimit) {
        Long count = unread
                ? jdbc.queryForObject("""
                        SELECT count(*) FROM notifications
                        WHERE user_id = ? AND read_at IS NULL
                        """, Long.class, userId)
                : jdbc.queryForObject("""
                        SELECT least(count(*), ?)
                        FROM notifications WHERE user_id = ?
                        """, Long.class, datasetLimit, userId);
        return count == null ? 0 : count;
    }

    public Optional<Notification> findOwnedById(UUID userId, UUID id) {
        return jdbc.query("""
                SELECT id, user_id, request_id, type, read_at, created_at
                FROM notifications
                WHERE id = ? AND user_id = ?
                """, NotificationRepository::map, id, userId).stream().findFirst();
    }

    public void markRead(UUID userId, UUID id) {
        jdbc.update("""
                UPDATE notifications
                SET read_at = COALESCE(read_at, now())
                WHERE id = ? AND user_id = ?
                """, id, userId);
    }

    private static Notification map(ResultSet rs, int row) throws SQLException {
        var readAt = rs.getTimestamp("read_at");
        return new Notification(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("request_id", UUID.class),
                rs.getString("type"),
                readAt == null ? null : readAt.toInstant(),
                rs.getTimestamp("created_at").toInstant());
    }
}
