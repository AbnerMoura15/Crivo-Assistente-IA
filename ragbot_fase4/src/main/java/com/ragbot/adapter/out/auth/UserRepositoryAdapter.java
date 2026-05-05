package com.ragbot.adapter.out.auth;

import com.ragbot.application.port.out.UserRepositoryPort;
import com.ragbot.domain.entity.User;
import com.ragbot.domain.entity.UserRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class UserRepositoryAdapter implements UserRepositoryPort {

    private final JdbcTemplate jdbc;

    public UserRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public User save(User user) {
        int updated = jdbc.update(
                "UPDATE users SET username=?, email=?, password_hash=?, role=?, active=? WHERE id=?",
                user.username(), user.email(), user.passwordHash(),
                user.role().name(), user.active(), user.id());

        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO users (id, username, email, password_hash, role, active, created_at) VALUES (?,?,?,?,?,?,?)",
                    user.id(), user.username(), user.email(), user.passwordHash(),
                    user.role().name(), user.active(),
                    Timestamp.from(user.createdAt() != null ? user.createdAt() : Instant.now()));
        }
        return user;
    }

    @Override
    public Optional<User> findById(String id) {
        List<User> result = jdbc.query(
                "SELECT * FROM users WHERE id = ?", this::mapRow, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        List<User> result = jdbc.query(
                "SELECT * FROM users WHERE username = ?", this::mapRow, username);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public List<User> findAll() {
        return jdbc.query("SELECT * FROM users ORDER BY created_at DESC", this::mapRow);
    }

    @Override
    public boolean existsByUsername(String username) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username);
        return count != null && count > 0;
    }

    @Override
    public boolean existsByEmail(String email) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        return count != null && count > 0;
    }

    @Override
    public void updateLastLogin(String userId) {
        jdbc.update("UPDATE users SET last_login = NOW() WHERE id = ?", userId);
    }

    private User mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new User(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("password_hash"),
                UserRole.valueOf(rs.getString("role")),
                rs.getBoolean("active"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_login") != null ? rs.getTimestamp("last_login").toInstant() : null
        );
    }
}
