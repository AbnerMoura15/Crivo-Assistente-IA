package com.ragbot.adapter.out.audit;

import com.ragbot.application.port.out.AuditLogPort;
import com.ragbot.domain.entity.AuditAction;
import com.ragbot.domain.entity.AuditEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Component
public class PgAuditLogAdapter implements AuditLogPort {

    private final JdbcTemplate jdbc;

    public PgAuditLogAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(AuditEntry e) {
        jdbc.update(
                """
                INSERT INTO audit_log (id, actor, action, target_id, target_type, detail, ip_address, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                e.id(), e.actor(), e.action().name(),
                e.targetId(), e.targetType(), e.detail(),
                e.ipAddress(), Timestamp.from(e.createdAt())
        );
    }

    @Override
    public List<AuditEntry> findRecent(int limit) {
        return jdbc.query(
                "SELECT * FROM audit_log ORDER BY created_at DESC LIMIT ?",
                this::mapRow, limit);
    }

    @Override
    public List<AuditEntry> findByActor(String actor, int limit) {
        return jdbc.query(
                "SELECT * FROM audit_log WHERE actor = ? ORDER BY created_at DESC LIMIT ?",
                this::mapRow, actor, limit);
    }

    @Override
    public List<AuditEntry> findByAction(AuditAction action, int limit) {
        return jdbc.query(
                "SELECT * FROM audit_log WHERE action = ? ORDER BY created_at DESC LIMIT ?",
                this::mapRow, action.name(), limit);
    }

    private AuditEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AuditEntry(
                rs.getString("id"),
                rs.getString("actor"),
                AuditAction.valueOf(rs.getString("action")),
                rs.getString("target_id"),
                rs.getString("target_type"),
                rs.getString("detail"),
                rs.getString("ip_address"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
