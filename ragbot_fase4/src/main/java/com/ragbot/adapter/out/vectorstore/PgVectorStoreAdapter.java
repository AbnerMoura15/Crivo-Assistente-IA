package com.ragbot.adapter.out.vectorstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragbot.application.port.out.VectorStorePort;
import com.ragbot.domain.entity.Chunk;
import com.ragbot.domain.entity.ScoredChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Adapter de busca vetorial com pgvector.
 *
 * Fase 8:
 * - findSimilar()        → retorna ScoredChunk com cosine similarity (1 - distância)
 * - findSimilarHybrid()  → score combinado: α * vetorial + (1-α) * BM25 ts_rank
 *
 * Fase anterior (#29 Soft Delete):
 * - save()               → insere com status='ACTIVE'
 * - deleteByDocumentId() → UPDATE status='DELETED' (não apaga fisicamente)
 */
@Component
public class PgVectorStoreAdapter implements VectorStorePort {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStoreAdapter.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PgVectorStoreAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(Chunk chunk) {
        jdbc.update(
                "INSERT INTO chunks (id, document_id, content, embedding, metadata, status) " +
                "VALUES (?, ?, ?, ?::vector, ?::jsonb, 'ACTIVE')",
                chunk.id(),
                chunk.documentId(),
                chunk.content(),
                toVectorString(chunk.embedding()),
                toJson(chunk.metadata())
        );
    }

    /**
     * Busca vetorial pura — score = 1 - cosine_distance.
     * Passa o vetor duas vezes: uma para ORDER BY, outra para o SELECT do score.
     */
    @Override
    public List<ScoredChunk> findSimilar(List<Float> queryEmbedding, int topK) {
        String vectorStr = toVectorString(queryEmbedding);
        return jdbc.query(
                """
                SELECT id, document_id, content, embedding::text, metadata::text,
                       1.0 - (embedding <=> ?::vector) AS score
                FROM chunks
                WHERE status = 'ACTIVE'
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """,
                this::mapScoredRow,
                vectorStr, vectorStr, topK
        );
    }

    /**
     * Busca híbrida: score = semanticWeight * similarity + (1-semanticWeight) * ts_rank.
     *
     * Usa content_tsvector (GENERATED ALWAYS, criado na V8) para evitar to_tsvector em runtime.
     * plainto_tsquery() converte a query em tsquery sem precisar de operadores especiais.
     */
    @Override
    public List<ScoredChunk> findSimilarHybrid(List<Float> queryEmbedding, String keywords,
                                               int topK, double semanticWeight) {
        String vectorStr = toVectorString(queryEmbedding);
        double bm25Weight = 1.0 - semanticWeight;

        String sql = """
                SELECT id, document_id, content, embedding::text, metadata::text,
                       (? * (1.0 - (embedding <=> ?::vector))
                        + ? * COALESCE(ts_rank(content_tsvector,
                                       plainto_tsquery('portuguese', ?)), 0.0)) AS score
                FROM chunks
                WHERE status = 'ACTIVE'
                ORDER BY (? * (1.0 - (embedding <=> ?::vector))
                          + ? * COALESCE(ts_rank(content_tsvector,
                                         plainto_tsquery('portuguese', ?)), 0.0)) DESC
                LIMIT ?
                """;

        return jdbc.query(sql, this::mapScoredRow,
                // SELECT score
                semanticWeight, vectorStr, bm25Weight, keywords,
                // ORDER BY score
                semanticWeight, vectorStr, bm25Weight, keywords,
                topK
        );
    }

    @Override
    public void deleteByDocumentId(String documentId) {
        int updated = jdbc.update(
                "UPDATE chunks SET status = 'DELETED' WHERE document_id = ? AND status = 'ACTIVE'",
                documentId
        );
        log.info("Soft delete: {} chunks marcados como DELETED para documentId={}", updated, documentId);
    }

    @Override
    public long countByDocumentId(String documentId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chunks WHERE document_id = ? AND status = 'ACTIVE'",
                Long.class, documentId);
        return count != null ? count : 0L;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ScoredChunk mapScoredRow(ResultSet rs, int rowNum) throws SQLException {
        Chunk chunk = new Chunk(
                rs.getString("id"),
                rs.getString("document_id"),
                rs.getString("content"),
                parseEmbedding(rs.getString("embedding")),
                parseMetadata(rs.getString("metadata"))
        );
        double score = rs.getDouble("score");
        return new ScoredChunk(chunk, score);
    }

    private String toVectorString(List<Float> embedding) {
        return "[" + embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";
    }

    private String toJson(Map<String, String> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Map<String, String> parseMetadata(String json) {
        try {
            if (json == null) return Map.of();
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<Float> parseEmbedding(String vectorStr) {
        if (vectorStr == null) return List.of();
        String clean = vectorStr.replaceAll("[\\[\\]]", "").trim();
        if (clean.isEmpty()) return List.of();
        return Arrays.stream(clean.split(","))
                .map(s -> Float.parseFloat(s.trim()))
                .collect(Collectors.toList());
    }
}
