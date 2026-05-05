package com.ragbot.application.service;

import com.ragbot.application.port.in.QueryChatUseCase;
import com.ragbot.application.port.out.DocumentStoragePort;
import com.ragbot.application.port.out.EmbeddingPort;
import com.ragbot.application.port.out.LlmPort;
import com.ragbot.application.port.out.VectorStorePort;
import com.ragbot.domain.entity.ChatMessage;
import com.ragbot.domain.entity.Chunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class QueryChatService implements QueryChatUseCase {

    private static final Logger log = LoggerFactory.getLogger(QueryChatService.class);
    private static final int MAX_HISTORY_MESSAGES = 6;

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
    private final int topK;

    public QueryChatService(
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            LlmPort llmPort,
            DocumentStoragePort documentStoragePort,
            PiiMaskingService piiMaskingService,
            RagObservabilityService observabilityService,
            @Value("${ragbot.retrieval.top-k:2}") int topK) {
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
        this.llmPort = llmPort;
        this.documentStoragePort = documentStoragePort;
        this.piiMaskingService = piiMaskingService;
        this.observabilityService = observabilityService;
        this.topK = topK;
    }

    @Override
    public ChatResult query(String question, List<ChatMessage> history, String username) {
        log.info("Pergunta de '{}': {}", username, question);

        if (documentStoragePort.findAll().isEmpty()) {
            String noDocsAnswer = "Nenhum documento foi encontrado. Por favor, faça o upload de documentos primeiro.";
            String interactionId = observabilityService.recordInteraction(username, question, noDocsAnswer, List.of());
            return new ChatResult(noDocsAnswer, interactionId);
        }

        List<Float> queryEmbedding = embeddingPort.embed(question);
        List<Chunk> relevantChunks = vectorStorePort.findSimilar(queryEmbedding, topK);
        log.info("{} chunks relevantes encontrados", relevantChunks.size());

        if (relevantChunks.isEmpty()) {
            String noChunksAnswer = "Não encontrei essa informação nos documentos indexados.";
            String interactionId = observabilityService.recordInteraction(username, question, noChunksAnswer, List.of());
            return new ChatResult(noChunksAnswer, interactionId);
        }

        // Mascara PII antes de enviar ao LLM (#31)
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

        List<ChatMessage> conversation = normalizeHistory(history, question);
        String answer = llmPort.complete(SYSTEM_PROMPT + "\n\nCONTEXTO:\n" + context, conversation);

        // Registra a interação para observabilidade (#7, #8)
        String interactionId = observabilityService.recordInteraction(username, question, answer, relevantChunks);

        return new ChatResult(answer, interactionId);
    }

    private List<ChatMessage> normalizeHistory(List<ChatMessage> history, String question) {
        List<ChatMessage> recent = history == null ? new ArrayList<>() :
                new ArrayList<>(history.stream()
                        .skip(Math.max(0, history.size() - MAX_HISTORY_MESSAGES))
                        .toList());
        boolean alreadyHas = !recent.isEmpty()
                && "user".equalsIgnoreCase(recent.get(recent.size() - 1).role())
                && recent.get(recent.size() - 1).content().trim().equals(question.trim());
        if (!alreadyHas) recent.add(ChatMessage.user(question));
        return recent.stream()
                .filter(m -> m.content() != null && !m.content().isBlank())
                .map(m -> new ChatMessage(normalizeRole(m.role()), m.content().trim(), m.timestamp()))
                .toList();
    }

    private String normalizeRole(String role) {
        if (role == null) return "user";
        return switch (role.toLowerCase(Locale.ROOT)) {
            case "assistant", "model" -> "assistant";
            default -> "user";
        };
    }
}
