package com.ragbot.adapter.out.vectorstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragbot.application.port.out.VectorStorePort;
import com.ragbot.domain.entity.Chunk;
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
 * Mudanças do #29 (Soft Delete):
 * - save()               → insere com status='ACTIVE' explícito
 * - findSimilar()        → filtra WHERE status='ACTIVE' (ignora deletados)
 * - deleteByDocumentId() → UPDATE status='DELETED' (não apaga fisicamente)
 *
 * Isso garante que:
 * 1. Documentos deletados nunca aparecem em buscas
 * 2. Os dados são preservados para auditoria
 * 3. Soft delete de documento em cascata via status dos chunks
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
                // status='ACTIVE' explícito — chunk disponível para busca
                "INSERT INTO chunks (id, document_id, content, embedding, metadata, status) " +
                "VALUES (?, ?, ?, ?::vector, ?::jsonb, 'ACTIVE')",
                chunk.id(),
                chunk.documentId(),
                chunk.content(),
                toVectorString(chunk.embedding()),
                toJson(chunk.metadata())
        );
    }

    @Override
    public List<Chunk> findSimilar(List<Float> queryEmbedding, int topK) {
        String vectorStr = toVectorString(queryEmbedding);
        return jdbc.query(
                """
                SELECT id, document_id, content, embedding::text, metadata::text
                FROM chunks
                WHERE status = 'ACTIVE'
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """,
                this::mapRow,
                vectorStr, topK
        );
    }

    @Override
    public void deleteByDocumentId(String documentId) {
        // #29: soft delete — marca como DELETED, não apaga fisicamente
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

    private Chunk mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Chunk(
                rs.getString("id"),
                rs.getString("document_id"),
                rs.getString("content"),
                parseEmbedding(rs.getString("embedding")),
                parseMetadata(rs.getString("metadata"))
        );
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
