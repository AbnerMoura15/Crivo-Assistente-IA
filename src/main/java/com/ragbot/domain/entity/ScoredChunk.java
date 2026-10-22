package com.ragbot.domain.entity;

/**
 * Chunk recuperado da busca com score de relevância.
 *
 * Score: 0.0 = sem relevância, 1.0 = idêntico à query.
 *
 * Na busca vetorial pura:  score = 1 - cosine_distance
 * Na busca híbrida:        score = α * similarity + (1-α) * ts_rank
 */
public record ScoredChunk(Chunk chunk, double score) {

    /**
     * Score ajustado com penalidade para chunks muito curtos.
     * Chunks curtos (< minLength chars) tendem a ser fragmentos sem contexto,
     * por isso recebem uma penalidade de 15%.
     */
    public double adjustedScore(int minLength) {
        if (chunk.content().length() < minLength) {
            return score * 0.85;
        }
        return score;
    }
}
