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
import com.ragbot.domain.entity.RagConfig;
import com.ragbot.domain.entity.ScoredChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Serviço central do pipeline RAG.
 *
 * Fase 10 (#25 — Config IA via tela):
 * - Todos os parâmetros de retrieval (topK, minScore, hybridEnabled, semanticWeight)
 *   são lidos do RagConfigService a cada request, permitindo ajustes em tempo real
 *   sem redeploy. O cache do RagConfigService garante que não há hit ao banco por msg.
 *
 * Fase 10 (#33 — Namespaces):
 * - query e queryWithSession recebem namespaceId para isolar o conhecimento.
 *
 * Fase 8:
 * - Re-ranking heurístico (#28a), busca híbrida (#27), controle de tokens (#4).
 */
@Service
public class QueryChatService implements QueryChatUseCase {

    private static final Logger log = LoggerFactory.getLogger(QueryChatService.class);

    private static final String SYSTEM_PROMPT = """
            Você é um especialista operacional sênior com conhecimento completo sobre os processos,\s
            regras e procedimentos da organização.

            Ao responder, siga rigorosamente estas diretrizes:

            1. USE EXCLUSIVAMENTE as informações fornecidas no contexto abaixo.\s
               Nunca invente, suponha ou extrapole além do que está ali.

            2. ENTREGUE a informação de forma direta, natural e autoritativa —\s
               como se você mesmo fosse o autor do conteúdo.\s
               Integre o conhecimento na resposta sem citar de onde ele veio.

            3. NUNCA mencione nomes de arquivos, fontes, seções, números de linha ou metadados,\s
               a menos que o usuário pergunte explicitamente "de onde vem essa informação".

            4. EVITE expressões como "o documento diz", "conforme o arquivo", "o registro indica",\s
               "nos documentos" ou qualquer referência à origem do conteúdo.

            5. SE a informação não estiver disponível no contexto fornecido,\s
               responda apenas: "Não tenho essa informação disponível no momento."

            6. TOKENS como [CPF], [EMAIL], [TELEFONE], [VALOR] são dados protegidos pela LGPD.\s
               Nunca tente deduzi-los, recompô-los ou comentar sobre eles.

            7. Responda sempre em português do Brasil, de forma objetiva e profissional.
            """;

    private final EmbeddingPort          embeddingPort;
    private final VectorStorePort        vectorStorePort;
    private final LlmPort                llmPort;
    private final DocumentStoragePort    documentStoragePort;
    private final PiiMaskingService      piiMaskingService;
    private final RagObservabilityService observabilityService;
    private final ChatHistoryService     chatHistoryService;
    private final TokenUsageService      tokenUsageService;
    private final RagConfigService       ragConfigService;   // #25

    public QueryChatService(
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            LlmPort llmPort,
            DocumentStoragePort documentStoragePort,
            PiiMaskingService piiMaskingService,
            RagObservabilityService observabilityService,
            ChatHistoryService chatHistoryService,
            TokenUsageService tokenUsageService,
            RagConfigService ragConfigService) {
        this.embeddingPort        = embeddingPort;
        this.vectorStorePort      = vectorStorePort;
        this.llmPort              = llmPort;
        this.documentStoragePort  = documentStoragePort;
        this.piiMaskingService    = piiMaskingService;
        this.observabilityService = observabilityService;
        this.chatHistoryService   = chatHistoryService;
        this.tokenUsageService    = tokenUsageService;
        this.ragConfigService     = ragConfigService;
    }

    @Override
    public ChatResult query(String question, List<ChatMessage> history,
                            String username, String namespaceId) {
        return queryWithSession(question, null, username, namespaceId);
    }

    @Override
    public ChatResult queryWithSession(String question, String sessionId,
                                       String username, String namespaceId) {
        // #25 — lê config atual (cache 30s); sem hit ao banco se não mudou
        RagConfig cfg = ragConfigService.getConfig();
        log.info("Pergunta de '{}' (sessão={} namespace={}): {}", username, sessionId, namespaceId, question);

        // #4 — Verifica limite de tokens ANTES de chamar o LLM
        tokenUsageService.checkLimit(username);

        ChatSession session = chatHistoryService.getOrCreateSession(sessionId, username);
        List<ChatMessage> dbHistory = chatHistoryService.loadRecentMessages(session.id());

        if (documentStoragePort.findByNamespace(namespaceId).isEmpty()) {
            String noDocsAnswer = "Nenhum documento foi encontrado neste namespace. Por favor, faça o upload de documentos primeiro.";
            chatHistoryService.saveUserMessage(session.id(), question);
            chatHistoryService.saveAssistantMessage(session.id(), noDocsAnswer);
            String interactionId = observabilityService.recordInteraction(
                    username, question, noDocsAnswer, List.of());
            return new ChatResult(noDocsAnswer, interactionId, session.id());
        }

        List<Float> queryEmbedding = embeddingPort.embed(question);
        log.info("Embedding gerado: {} dimensões", queryEmbedding.size());

        List<Chunk> relevantChunks = retrieveAndRerank(queryEmbedding, question, namespaceId, cfg);

        if (relevantChunks.isEmpty()) {
            String noChunksAnswer = "Não tenho essa informação disponível no momento.";
            chatHistoryService.saveUserMessage(session.id(), question);
            chatHistoryService.saveAssistantMessage(session.id(), noChunksAnswer);
            String interactionId = observabilityService.recordInteraction(
                    username, question, noChunksAnswer, List.of());
            return new ChatResult(noChunksAnswer, interactionId, session.id());
        }

        String context = relevantChunks.stream()
                .map(c -> {
                    PiiMaskingService.MaskResult masked = piiMaskingService.maskWithStats(c.content());
                    if (masked.replacementsCount() > 0)
                        log.debug("PII mascarada: {} substituições", masked.replacementsCount());
                    // Label neutro: LLM vê a fonte mas as regras do prompt impedem de citá-la
                    return String.format("[Fonte: %s]\n%s",
                            c.metadata().getOrDefault("source", "interno"),
                            masked.maskedText());
                })
                .collect(Collectors.joining("\n\n---\n\n"));

        // Fontes únicas para os source chips (item 3.3)
        List<String> sources = relevantChunks.stream()
                .map(c -> c.metadata().getOrDefault("source", ""))
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.toList());

        List<ChatMessage> conversation = buildConversation(dbHistory, question);
        String answer = llmPort.complete(SYSTEM_PROMPT + "\n\nCONTEXTO:\n" + context, conversation);

        chatHistoryService.saveUserMessage(session.id(), question);
        chatHistoryService.saveAssistantMessage(session.id(), answer);
        String interactionId = observabilityService.recordInteraction(
                username, question, answer, relevantChunks);
        tokenUsageService.record(username, question, answer);

        return new ChatResult(answer, interactionId, session.id(), sources);
    }

    // ── Retrieval + Re-ranking ────────────────────────────────────────────────

    private List<Chunk> retrieveAndRerank(List<Float> queryEmbedding, String question,
                                           String namespaceId, RagConfig cfg) {
        int fetchK = cfg.topK() * 3;
        List<ScoredChunk> scored = fetchScored(queryEmbedding, question, fetchK, namespaceId, cfg);

        log.info("Busca retornou {} candidatos (hybrid={} namespace={})",
                scored.size(), cfg.hybridEnabled(), namespaceId);

        scored.forEach(sc -> log.debug(
                "  chunk '{}...' score={} adjusted={} len={}",
                sc.chunk().content().substring(0, Math.min(50, sc.chunk().content().length())).replace("\n", " "),
                String.format("%.4f", sc.score()),
                String.format("%.4f", sc.adjustedScore(cfg.shortChunkLength())),
                sc.chunk().content().length()));

        List<Chunk> result = scored.stream()
                .filter(sc -> sc.adjustedScore(cfg.shortChunkLength()) >= cfg.minScore())
                .sorted(Comparator.comparingDouble(
                        (ScoredChunk sc) -> sc.adjustedScore(cfg.shortChunkLength())).reversed())
                .limit(cfg.topK())
                .map(ScoredChunk::chunk)
                .collect(Collectors.toList());

        log.info("Re-ranking: {} candidatos → {} aprovados (minScore={})",
                scored.size(), result.size(), cfg.minScore());
        return result;
    }

    private List<ScoredChunk> fetchScored(List<Float> queryEmbedding, String question,
                                           int fetchK, String namespaceId, RagConfig cfg) {
        if (cfg.hybridEnabled()) {
            try {
                return vectorStorePort.findSimilarHybrid(
                        queryEmbedding, question, fetchK, cfg.semanticWeight(), namespaceId);
            } catch (Exception e) {
                log.warn("Busca híbrida falhou ({}), usando vetorial pura: {}",
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

        if (!alreadyHasQuestion) normalized.add(ChatMessage.user(question));
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
