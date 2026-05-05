package com.ragbot.application.port.out;

import com.ragbot.domain.entity.RagInteraction;

import java.util.List;

public interface RagObservabilityPort {
    String save(RagInteraction interaction);
    void updateFeedback(String interactionId, boolean positive);
    List<RagInteraction> findRecent(int limit);
    long countByClassification(String classification);
    long countWithNegativeFeedback();
    long countWithPositiveFeedback();
}
