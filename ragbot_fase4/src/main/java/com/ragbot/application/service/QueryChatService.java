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
            - Tokens como [CPF], [EMAIL], [TELEFONE], [VALOR] representam dados pessoais
              protegidos pela LGPD que foram anonimizados. Trate-os como informação válida.
            """;

    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final LlmPort llmPort;
    private final DocumentStoragePort documentStoragePort;
    private final PiiMaskingService piiMaskingService;  // #31
    private final int topK;

    public QueryChatService(
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            LlmPort llmPort,
            DocumentStoragePort documentStoragePort,
            PiiMaskingService piiMaskingService,
            @Value("${ragbot.retrieval.top-k:5}") int topK) {
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
        this.llmPort = llmPort;
        this.documentStoragePort = documentStoragePort;
        this.piiMaskingService = piiMaskingService;
        this.topK = topK;
    }

    @Override
    public String query(String question, List<ChatMessage> history) {
        log.info("Pergunta recebida: {}", question);

        if (documentStoragePort.findAll().isEmpty()) {
            return "Nenhum documento foi encontrado para responder sua pergunta. "
                    + "Por favor, faça o upload de documentos primeiro.";
        }

        List<ChatMessage> conversation = normalizeHistory(history, question);

        // Embedding usa texto ORIGINAL (sem mascaramento) — necessário para busca semântica precisa
        List<Float> queryEmbedding = embeddingPort.embed(question);
        List<Chunk> relevantChunks = vectorStorePort.findSimilar(queryEmbedding, topK);

        log.info("{} chunks relevantes encontrados", relevantChunks.size());

        if (relevantChunks.isEmpty()) {
            return "Nenhum documento foi encontrado para responder sua pergunta. "
                    + "Por favor, faça o upload de documentos primeiro.";
        }

        // #31: mascara PII ANTES de enviar para o LLM externo (Groq/Google)
        // O banco mantém os dados originais — apenas o que vai para fora é mascarado
        String context = relevantChunks.stream()
                .map(c -> {
                    String rawContent = c.content();
                    PiiMaskingService.MaskResult masked = piiMaskingService.maskWithStats(rawContent);
                    if (masked.replacementsCount() > 0) {
                        log.debug("PII mascarada em chunk do documento '{}': {} substituições",
                                c.metadata().getOrDefault("source", "?"),
                                masked.replacementsCount());
                    }
                    return String.format("[Documento: %s]\n%s",
                            c.metadata().getOrDefault("source", "desconhecido"),
                            masked.maskedText());
                })
                .collect(Collectors.joining("\n\n---\n\n"));

        String systemWithContext = SYSTEM_PROMPT + "\n\nCONTEXTO:\n" + context;
        return llmPort.complete(systemWithContext, conversation);
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
