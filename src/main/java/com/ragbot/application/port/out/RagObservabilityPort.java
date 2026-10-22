package com.ragbot.application.port.out;

import com.ragbot.domain.entity.RagInteraction;

import java.util.List;

public interface RagObservabilityPort {
    String save(RagInteraction interaction);
    /** Atualiza o feedback apenas se a interação pertencer ao usuário. Retorna false caso contrário. */
    boolean updateFeedback(String interactionId, String username, boolean positive);
    List<RagInteraction> findRecent(int limit);
    long countByClassification(String classification);
    long countWithNegativeFeedback();
    long countWithPositiveFeedback();
}
