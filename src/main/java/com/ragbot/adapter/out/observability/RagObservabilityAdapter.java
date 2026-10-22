package com.ragbot.adapter.out.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragbot.application.port.out.RagObservabilityPort;
import com.ragbot.domain.entity.RagInteraction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Component
public class RagObservabilityAdapter implements RagObservabilityPort {

    private static final Logger log = LoggerFactory.getLogger(RagObservabilityAdapter.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RagObservabilityAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public String save(RagInteraction i) {
        jdbc.update(
                """
                INSERT INTO rag_interactions
                  (id, username, question, answer, classification,
                   source_documents, chunks_retrieved, created_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """,
                i.id(), i.username(), i.question(), i.answer(),
                i.classification(), toJson(i.sourceDocuments()),
                i.chunksRetrieved(), Timestamp.from(i.createdAt())
        );
        return i.id();
    }

    @Override
    public boolean updateFeedback(String interactionId, String username, boolean positive) {
        int rows = jdbc.update(
                "UPDATE rag_interactions SET feedback_positive = ? WHERE id = ? AND username = ?",
                positive, interactionId, username
        );
        if (rows == 0) log.warn("Interação não encontrada para feedback do usuário: {}", interactionId);
        return rows > 0;
    }

    @Override
    public List<RagInteraction> findRecent(int limit) {
        return jdbc.query(
                """
                SELECT id, username, question, answer, classification,
                       source_documents::text, chunks_retrieved,
                       feedback_positive, created_at
                FROM rag_interactions
                ORDER BY created_at DESC
                LIMIT ?
                """,
                this::mapRow, limit
        );
    }

    @Override
    public long countByClassification(String classification) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rag_interactions WHERE classification = ?",
                Long.class, classification);
        return count != null ? count : 0L;
    }

    @Override
    public long countWithNegativeFeedback() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rag_interactions WHERE feedback_positive = FALSE",
                Long.class);
        return count != null ? count : 0L;
    }

    @Override
    public long countWithPositiveFeedback() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rag_interactions WHERE feedback_positive = TRUE",
                Long.class);
        return count != null ? count : 0L;
    }

    private RagInteraction mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new RagInteraction(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("question"),
                rs.getString("answer"),
                rs.getString("classification"),
                parseList(rs.getString("source_documents")),
                rs.getInt("chunks_retrieved"),
                rs.getObject("feedback_positive") != null
                        ? rs.getBoolean("feedback_positive") : null,
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private String toJson(List<String> list) {
        try { return objectMapper.writeValueAsString(list); }
        catch (JsonProcessingException e) { return "[]"; }
    }

    private List<String> parseList(String json) {
        try {
            if (json == null) return List.of();
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) { return List.of(); }
    }
}
