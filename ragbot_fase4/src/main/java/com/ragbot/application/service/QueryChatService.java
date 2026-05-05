package com.ragbot.application.service;

import com.ragbot.application.port.in.QueryChatUseCase;
import com.ragbot.application.port.out.DocumentStoragePort;
import com.ragbot.application.port.out.EmbeddingPort;
import com.ragbot.application.port.out.LlmPort;
import com.ragbot.application.port.out.VectorStorePort;
import com.ragbot.domain.entity.ChatMessage;
import com.ragbot.domain.entity.ChatSession;
import com.ragbot.domain.entity.Chunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

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
    private final int topK;

    public QueryChatService(
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            LlmPort llmPort,
            DocumentStoragePort documentStoragePort,
            PiiMaskingService piiMaskingService,
            RagObservabilityService observabilityService,
            ChatHistoryService chatHistoryService,
            @Value("${ragbot.retrieval.top-k:2}") int topK) {
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
        this.llmPort = llmPort;
        this.documentStoragePort = documentStoragePort;
        this.piiMaskingService = piiMaskingService;
        this.observabilityService = observabilityService;
        this.chatHistoryService = chatHistoryService;
        this.topK = topK;
    }

    @Override
    public ChatResult query(String question, List<ChatMessage> history, String username) {
        return queryWithSession(question, null, username);
    }

    @Override
    public ChatResult queryWithSession(String question, String sessionId, String username) {
        log.info("Pergunta de '{}' (sessão={}): {}", username, sessionId, question);

        ChatSession session = chatHistoryService.getOrCreateSession(sessionId, username);
        List<ChatMessage> dbHistory = chatHistoryService.loadRecentMessages(session.id());

        if (documentStoragePort.findAll().isEmpty()) {
            String noDocsAnswer = "Nenhum documento foi encontrado. Por favor, faça o upload de documentos primeiro.";
            chatHistoryService.saveUserMessage(session.id(), question);
            chatHistoryService.saveAssistantMessage(session.id(), noDocsAnswer);
            String interactionId = observabilityService.recordInteraction(username, question, noDocsAnswer, List.of());
            return new ChatResult(noDocsAnswer, interactionId, session.id());
        }

        List<Float> queryEmbedding = embeddingPort.embed(question);
        List<Chunk> relevantChunks = vectorStorePort.findSimilar(queryEmbedding, topK);
        log.info("{} chunks relevantes encontrados", relevantChunks.size());

        if (relevantChunks.isEmpty()) {
            String noChunksAnswer = "Não encontrei essa informação nos documentos indexados.";
            chatHistoryService.saveUserMessage(session.id(), question);
            chatHistoryService.saveAssistantMessage(session.id(), noChunksAnswer);
            String interactionId = observabilityService.recordInteraction(username, question, noChunksAnswer, List.of());
            return new ChatResult(noChunksAnswer, interactionId, session.id());
        }

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

        chatHistoryService.saveUserMessage(session.id(), question);
        chatHistoryService.saveAssistantMessage(session.id(), answer);

        String interactionId = observabilityService.recordInteraction(username, question, answer, relevantChunks);

        return new ChatResult(answer, interactionId, session.id());
    }

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
