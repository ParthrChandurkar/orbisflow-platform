package com.orbisflow.users.persistence;

import com.orbisflow.users.domain.User;
import com.orbisflow.users.domain.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<User> findByLoginIdentifier(String loginIdentifier) {
        return jdbc.query("""
                SELECT id, login_identifier, password_hash, role::text, manager_id
                FROM users WHERE login_identifier = ?
                """, (rs, row) -> mapUser(rs), loginIdentifier).stream().findFirst();
    }

    public Optional<User> findById(UUID id) {
        return jdbc.query("""
                SELECT id, login_identifier, password_hash, role::text, manager_id
                FROM users WHERE id = ?
                """, (rs, row) -> mapUser(rs), id).stream().findFirst();
    }

    private User mapUser(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new User(
                rs.getObject("id", UUID.class),
                rs.getString("login_identifier"),
                rs.getString("password_hash"),
                UserRole.fromDatabase(rs.getString("role")),
                rs.getObject("manager_id", UUID.class));
    }
}
