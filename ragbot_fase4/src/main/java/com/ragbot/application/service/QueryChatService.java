package com.ragbot.application.service;

import com.ragbot.application.port.in.QueryChatUseCase;
import com.ragbot.application.port.out.DocumentStoragePort;
import com.ragbot.application.port.out.EmbeddingPort;
import com.ragbot.application.port.out.LlmPort;
import com.ragbot.application.port.out.VectorStorePort;
import com.ragbot.domain.entity.ChatMessage;
import com.ragbot.domain.entity.ChatSession;
import com.ragbot.domain.entity.Chunk;
import com.ragbot.domain.entity.Namespace;
import com.ragbot.domain.entity.ScoredChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Serviço central do pipeline RAG.
 *
 * Fase 10 (#33 — Namespaces):
 * - query e queryWithSession aceitam namespaceId para isolar o conhecimento.
 * - fetchScored passa namespaceId para o VectorStorePort.
 * - Verificação de "nenhum documento" escopada ao namespace.
 *
 * Fase 8:
 * - Re-ranking heurístico (#28a), busca híbrida (#27), controle de tokens (#4).
 */
@Service
public class QueryChatService implements QueryChatUseCase {

    private static final Logger log = LoggerFactory.getLogger(QueryChatService.class);

    private static final String SYSTEM_PROMPT = """
            Você é um assistente inteligente de suporte interno de uma fábrica.
            Responda perguntas com base exclusivamente nos documentos internos fornecidos no contexto.

            Regras:
            - Use APENAS as informações do contexto abaixo para responder.
            - Se não houver informação suficiente, diga: "Não encontrei essa informação nos documentos indexados."
            - Seja objetivo, claro e profissional.
            - Quando relevante, mencione qual documento contém a informação.
            - Responda sempre em português do Brasil.
            - Tokens como [CPF], [EMAIL], [TELEFONE], [VALOR] são dados protegidos pela LGPD.
            """;

    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final LlmPort llmPort;
    private final DocumentStoragePort documentStoragePort;
    private final PiiMaskingService piiMaskingService;
    private final RagObservabilityService observabilityService;
    private final ChatHistoryService chatHistoryService;
    private final TokenUsageService tokenUsageService;

    // Retrieval config
    private final int topK;
    private final double minScore;
    private final int minChunkLength;

    // Hybrid search config
    private final boolean hybridEnabled;
    private final double semanticWeight;

    public QueryChatService(
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            LlmPort llmPort,
            DocumentStoragePort documentStoragePort,
            PiiMaskingService piiMaskingService,
            RagObservabilityService observabilityService,
            ChatHistoryService chatHistoryService,
            TokenUsageService tokenUsageService,
            @Value("${ragbot.retrieval.top-k:2}")            int topK,
            @Value("${ragbot.retrieval.min-score:0.30}")     double minScore,
            @Value("${ragbot.retrieval.short-chunk-length:80}") int minChunkLength,
            @Value("${ragbot.retrieval.hybrid.enabled:true}")  boolean hybridEnabled,
            @Value("${ragbot.retrieval.hybrid.semantic-weight:0.7}") double semanticWeight) {
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
        this.llmPort = llmPort;
        this.documentStoragePort = documentStoragePort;
        this.piiMaskingService = piiMaskingService;
        this.observabilityService = observabilityService;
        this.chatHistoryService = chatHistoryService;
        this.tokenUsageService = tokenUsageService;
        this.topK = topK;
        this.minScore = minScore;
        this.minChunkLength = minChunkLength;
        this.hybridEnabled = hybridEnabled;
        this.semanticWeight = semanticWeight;
    }

    @Override
    public ChatResult query(String question, List<ChatMessage> history,
                            String username, String namespaceId) {
        return queryWithSession(question, null, username, namespaceId);
    }

    @Override
    public ChatResult queryWithSession(String question, String sessionId,
                                       String username, String namespaceId) {
        log.info("Pergunta de '{}' (sessão={} namespace={}): {}", username, sessionId, namespaceId, question);

        // #4 — Verifica limite de tokens ANTES de chamar o LLM
        tokenUsageService.checkLimit(username);

        ChatSession session = chatHistoryService.getOrCreateSession(sessionId, username);
        List<ChatMessage> dbHistory = chatHistoryService.loadRecentMessages(session.id());

        // Verifica se há documentos no namespace ativo
        if (documentStoragePort.findByNamespace(namespaceId).isEmpty()) {
            String noDocsAnswer = "Nenhum documento foi encontrado neste namespace. Por favor, faça o upload de documentos primeiro.";
            chatHistoryService.saveUserMessage(session.id(), question);
            chatHistoryService.saveAssistantMessage(session.id(), noDocsAnswer);
            String interactionId = observabilityService.recordInteraction(
                    username, question, noDocsAnswer, List.of());
            return new ChatResult(noDocsAnswer, interactionId, session.id());
        }

        // Embeda e busca chunks escopados ao namespace
        List<Float> queryEmbedding = embeddingPort.embed(question);
        log.info("Embedding gerado: {} dimensões para '{}'",
                queryEmbedding.size(), question.substring(0, Math.min(50, question.length())));

        List<Chunk> relevantChunks = retrieveAndRerank(queryEmbedding, question, namespaceId);

        if (relevantChunks.isEmpty()) {
            String noChunksAnswer = "Não encontrei essa informação nos documentos indexados.";
            chatHistoryService.saveUserMessage(session.id(), question);
            chatHistoryService.saveAssistantMessage(session.id(), noChunksAnswer);
            String interactionId = observabilityService.recordInteraction(
                    username, question, noChunksAnswer, List.of());
            return new ChatResult(noChunksAnswer, interactionId, session.id());
        }

        // Monta contexto com PII mascarada
        String context = relevantChunks.stream()
                .map(c -> {
                    PiiMaskingService.MaskResult masked = piiMaskingService.maskWithStats(c.content());
                    if (masked.replacementsCount() > 0)
                        log.debug("PII mascarada: {} substituições", masked.replacementsCount());
                    return String.format("[Documento: %s]\n%s",
                            c.metadata().getOrDefault("source", "desconhecido"),
                            masked.maskedText());
                })
                .collect(Collectors.joining("\n\n---\n\n"));

        List<ChatMessage> conversation = buildConversation(dbHistory, question);
        String answer = llmPort.complete(SYSTEM_PROMPT + "\n\nCONTEXTO:\n" + context, conversation);

        // Persiste histórico + observabilidade + tokens
        chatHistoryService.saveUserMessage(session.id(), question);
        chatHistoryService.saveAssistantMessage(session.id(), answer);
        String interactionId = observabilityService.recordInteraction(
                username, question, answer, relevantChunks);
        tokenUsageService.record(username, question, answer);

        return new ChatResult(answer, interactionId, session.id());
    }

    // ── #28a + #27 — Busca e Re-ranking ──────────────────────────────────────

    private List<Chunk> retrieveAndRerank(List<Float> queryEmbedding, String question,
                                           String namespaceId) {
        int fetchK = topK * 3;
        List<ScoredChunk> scored = fetchScored(queryEmbedding, question, fetchK, namespaceId);

        log.info("Busca retornou {} candidatos (hybrid={} namespace={})",
                scored.size(), hybridEnabled, namespaceId);

        scored.forEach(sc -> log.debug(
                "  chunk '{}...' score={} adjusted={} len={}",
                sc.chunk().content().substring(0, Math.min(50, sc.chunk().content().length())).replace("\n", " "),
                String.format("%.4f", sc.score()),
                String.format("%.4f", sc.adjustedScore(minChunkLength)),
                sc.chunk().content().length()));

        List<Chunk> result = scored.stream()
                .filter(sc -> sc.adjustedScore(minChunkLength) >= minScore)
                .sorted(Comparator.comparingDouble(
                        (ScoredChunk sc) -> sc.adjustedScore(minChunkLength)).reversed())
                .limit(topK)
                .map(ScoredChunk::chunk)
                .collect(Collectors.toList());

        log.info("Re-ranking: {} candidatos → {} aprovados (minScore={})",
                scored.size(), result.size(), minScore);
        return result;
    }

    private List<ScoredChunk> fetchScored(List<Float> queryEmbedding, String question,
                                           int fetchK, String namespaceId) {
        if (hybridEnabled) {
            try {
                return vectorStorePort.findSimilarHybrid(
                        queryEmbedding, question, fetchK, semanticWeight, namespaceId);
            } catch (Exception e) {
                log.warn("Busca híbrida falhou ({}), usando vetorial pura como fallback: {}",
                        e.getClass().getSimpleName(), e.getMessage());
            }
        }
        return vectorStorePort.findSimilar(queryEmbedding, fetchK, namespaceId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<ChatMessage> buildConversation(List<ChatMessage> history, String question) {
        List<ChatMessage> normalized = history.stream()
                .filter(m -> m.content() != null && !m.content().isBlank())
                .map(m -> new ChatMessage(normalizeRole(m.role()), m.content().trim(), m.timestamp()))
                .collect(Collectors.toList());

        boolean alreadyHasQuestion = !normalized.isEmpty()
                && "user".equals(normalized.get(normalized.size() - 1).role())
                && normalized.get(normalized.size() - 1).content().trim().equals(question.trim());

        if (!alreadyHasQuestion) {
            normalized.add(ChatMessage.user(question));
        }
        return normalized;
    }

    private String normalizeRole(String role) {
        if (role == null) return "user";
        return switch (role.toLowerCase(Locale.ROOT)) {
            case "assistant", "model" -> "assistant";
            default -> "user";
        };
    }
}
