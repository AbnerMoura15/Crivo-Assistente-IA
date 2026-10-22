package com.ragbot.adapter.out.chat;

import com.ragbot.application.port.out.ChatHistoryPort;
import com.ragbot.domain.entity.ChatMessage;
import com.ragbot.domain.entity.ChatSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PgChatHistoryAdapter implements ChatHistoryPort {

    private final JdbcTemplate jdbc;

    public PgChatHistoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ChatSession createSession(String username) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO chat_sessions (id, username, created_at, last_message_at) VALUES (?, ?, ?, ?)",
                id, username, Timestamp.from(now), Timestamp.from(now)
        );
        return new ChatSession(id, username, now, now, List.of());
    }

    @Override
    public Optional<ChatSession> findSession(String sessionId) {
        List<ChatSession> rows = jdbc.query(
                "SELECT id, username, created_at, last_message_at FROM chat_sessions WHERE id = ?",
                this::mapSession, sessionId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<ChatSession> findSessionsByUser(String username, int limit) {
        return jdbc.query(
                """
                SELECT id, username, created_at, last_message_at
                FROM chat_sessions WHERE username = ?
                ORDER BY last_message_at DESC LIMIT ?
                """,
                this::mapSession, username, limit);
    }

    @Override
    public void addMessage(String sessionId, ChatMessage message) {
        jdbc.update(
                "INSERT INTO chat_messages (id, session_id, role, content, created_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), sessionId,
                message.role(), message.content(),
                Timestamp.from(message.timestamp() != null ? message.timestamp() : Instant.now())
        );
        jdbc.update(
                "UPDATE chat_sessions SET last_message_at = NOW() WHERE id = ?",
                sessionId
        );
    }

    @Override
    public List<ChatMessage> loadMessages(String sessionId) {
        return jdbc.query(
                "SELECT role, content, created_at FROM chat_messages WHERE session_id = ? ORDER BY created_at",
                this::mapMessage, sessionId);
    }

    @Override
    public void deleteSession(String sessionId) {
        jdbc.update("DELETE FROM chat_messages WHERE session_id = ?", sessionId);
        jdbc.update("DELETE FROM chat_sessions WHERE id = ?", sessionId);
    }

    private ChatSession mapSession(ResultSet rs, int rowNum) throws SQLException {
        return new ChatSession(
                rs.getString("id"),
                rs.getString("username"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_message_at").toInstant(),
                List.of()
        );
    }

    private ChatMessage mapMessage(ResultSet rs, int rowNum) throws SQLException {
        return new ChatMessage(
                rs.getString("role"),
                rs.getString("content"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
