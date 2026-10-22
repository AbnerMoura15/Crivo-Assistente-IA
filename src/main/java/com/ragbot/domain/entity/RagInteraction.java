package com.ragbot.domain.entity;

import java.time.Instant;
import java.util.List;

/**
 * Entidade de domínio que representa uma interação completa com o RAG.
 * Registra pergunta, resposta, quais chunks foram usados e a classificação.
 * É a base da observabilidade de qualidade (#7, #8, #9).
 */
public record RagInteraction(
        String id,
        String username,
        String question,
        String answer,
        String classification,   // ANSWERED | NOT_FOUND
        List<String> sourceDocuments,
        int chunksRetrieved,
        Boolean feedbackPositive,  // null = sem feedback, true = 👍, false = 👎
        Instant createdAt
) {
    public RagInteraction withFeedback(boolean positive) {
        return new RagInteraction(id, username, question, answer,
                classification, sourceDocuments, chunksRetrieved, positive, createdAt);
    }
}
