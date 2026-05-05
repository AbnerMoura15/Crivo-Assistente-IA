package com.ragbot.application.port.out;

import com.ragbot.domain.entity.Chunk;
import com.ragbot.domain.entity.Namespace;
import com.ragbot.domain.entity.ScoredChunk;

import java.util.List;

/**
 * Port de saída para busca vetorial.
 *
 * Fase 10 (#33 — Namespaces):
 * - Todos os métodos de busca recebem namespaceId para isolar o conhecimento.
 * - Métodos sem namespace usam o namespace padrão (compatibilidade retroativa).
 *
 * Dois modos de busca:
 * - findSimilar:       vetorial pura (cosine similarity)
 * - findSimilarHybrid: vetorial + BM25 full-text
 */
public interface VectorStorePort {

    void save(Chunk chunk);

    /**
     * Busca vetorial pura escopada ao namespace.
     * Score = 1 - cosine_distance (0 = diferente, 1 = idêntico).
     */
    List<ScoredChunk> findSimilar(List<Float> queryEmbedding, int topK, String namespaceId);

    /** Compatibilidade retroativa — usa namespace padrão. */
    default List<ScoredChunk> findSimilar(List<Float> queryEmbedding, int topK) {
        return findSimilar(queryEmbedding, topK, Namespace.DEFAULT_ID);
    }

    /**
     * Busca híbrida escopada ao namespace: vetorial + BM25 full-text.
     * Score = semanticWeight * similarity + (1 - semanticWeight) * ts_rank.
     *
     * @param keywords       palavras-chave (para ts_rank)
     * @param semanticWeight peso vetorial (0-1); ex: 0.85 = 85% semântico
     */
    List<ScoredChunk> findSimilarHybrid(List<Float> queryEmbedding, String keywords,
                                        int topK, double semanticWeight, String namespaceId);

    /** Compatibilidade retroativa — usa namespace padrão. */
    default List<ScoredChunk> findSimilarHybrid(List<Float> queryEmbedding, String keywords,
                                                int topK, double semanticWeight) {
        return findSimilarHybrid(queryEmbedding, keywords, topK, semanticWeight, Namespace.DEFAULT_ID);
    }

    void deleteByDocumentId(String documentId);

    long countByDocumentId(String documentId);
}
