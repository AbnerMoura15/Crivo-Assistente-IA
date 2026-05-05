package com.ragbot.application.port.out;

import com.ragbot.domain.entity.Chunk;
import com.ragbot.domain.entity.ScoredChunk;

import java.util.List;

/**
 * Port de saída para busca vetorial.
 *
 * Dois modos disponíveis:
 * - findSimilar:       busca vetorial pura (cosine similarity)
 * - findSimilarHybrid: vetorial + BM25 full-text (busca híbrida)
 *
 * Ambos retornam ScoredChunk para permitir re-ranking no serviço.
 */
public interface VectorStorePort {

    void save(Chunk chunk);

    /**
     * Busca vetorial pura.
     * Score = 1 - cosine_distance (0 = diferente, 1 = idêntico).
     */
    List<ScoredChunk> findSimilar(List<Float> queryEmbedding, int topK);

    /**
     * Busca híbrida: vetorial + BM25 full-text.
     * Score = semanticWeight * similarity + (1 - semanticWeight) * ts_rank.
     *
     * @param keywords       palavras-chave (para ts_rank)
     * @param semanticWeight peso vetorial (0-1); ex: 0.7 = 70% semântico
     */
    List<ScoredChunk> findSimilarHybrid(List<Float> queryEmbedding, String keywords,
                                        int topK, double semanticWeight);

    void deleteByDocumentId(String documentId);

    long countByDocumentId(String documentId);
}
