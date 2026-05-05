package com.ragbot.application.service;

import com.ragbot.application.port.out.RagObservabilityPort;
import com.ragbot.domain.entity.Chunk;
import com.ragbot.domain.entity.RagInteraction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serviço de Observabilidade RAG (#7, #9).
 *
 * Responsabilidades:
 * - Registrar cada interação com pergunta, resposta, chunks e classificação
 * - Registrar feedback do usuário (👍/👎) por interação
 * - Fornecer métricas agregadas para o dashboard
 */
@Service
public class RagObservabilityService {

    private static final Logger log = LoggerFactory.getLogger(RagObservabilityService.class);

    private final RagObservabilityPort observabilityPort;
    private final ResponseClassifier classifier;

    public RagObservabilityService(RagObservabilityPort observabilityPort,
                                   ResponseClassifier classifier) {
        this.observabilityPort = observabilityPort;
        this.classifier = classifier;
    }

    /**
     * Registra uma interação RAG completa.
     * Chamado pelo QueryChatService após cada resposta.
     *
     * @return ID da interação (usado para associar feedback depois)
     */
    public String recordInteraction(String username, String question,
                                   String answer, List<Chunk> chunks) {
        String classification = classifier.classify(answer).name();

        List<String> sources = chunks.stream()
                .map(c -> c.metadata().getOrDefault("source", "desconhecido"))
                .distinct()
                .collect(Collectors.toList());

        RagInteraction interaction = new RagInteraction(
                UUID.randomUUID().toString(),
                username,
                question,
                answer,
                classification,
                sources,
                chunks.size(),
                null,         // sem feedback ainda
                Instant.now()
        );

        String id = observabilityPort.save(interaction);
        log.info("Interação registrada: id={} classification={} chunks={}",
                id, classification, chunks.size());
        return id;
    }

    /**
     * Registra feedback do usuário para uma interação.
     * Chamado pelo FeedbackController (#9).
     */
    public void recordFeedback(String interactionId, boolean positive, String username) {
        observabilityPort.updateFeedback(interactionId, positive);
        log.info("Feedback registrado: interactionId={} positive={} user={}",
                interactionId, positive, username);
    }

    /** Retorna métricas agregadas para o dashboard */
    public Map<String, Object> getMetrics() {
        long answered    = observabilityPort.countByClassification("ANSWERED");
        long notFound    = observabilityPort.countByClassification("NOT_FOUND");
        long total       = answered + notFound;
        long posFeedback = observabilityPort.countWithPositiveFeedback();
        long negFeedback = observabilityPort.countWithNegativeFeedback();

        double answeredRate = total > 0 ? (double) answered / total * 100 : 0;

        return Map.of(
                "totalInteractions",  total,
                "answered",           answered,
                "notFound",           notFound,
                "answeredRate",       Math.round(answeredRate * 10) / 10.0,
                "positiveFeedback",   posFeedback,
                "negativeFeedback",   negFeedback,
                "recentInteractions", observabilityPort.findRecent(10)
        );
    }
}
