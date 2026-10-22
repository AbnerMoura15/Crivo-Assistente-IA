package com.ragbot.adapter.in.rest.dto;

import com.ragbot.domain.entity.Document;
import com.ragbot.domain.entity.ChatSession;
import com.ragbot.domain.entity.Namespace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public class RestDtos {

    /** Limite de caracteres da pergunta — controla custo de tokens e abuso do LLM. */
    public static final int MAX_QUESTION_LENGTH = 2000;

    public record ChatRequest(
            @NotBlank(message = "Pergunta é obrigatória")
            @Size(max = MAX_QUESTION_LENGTH, message = "Pergunta deve ter no máximo " + MAX_QUESTION_LENGTH + " caracteres")
            String question,
            @Size(max = 64) String sessionId,
            @Size(max = 20) List<MessageDto> history,   // ignorado pelo backend (histórico vem do banco)
            @Size(max = 64) String namespaceId          // #33: null → usa namespace padrão
    ) {}

    public record ChatResponse(String answer, String interactionId, String sessionId, List<String> sources) {}

    public record MessageDto(String role, String content) {}

    public record ChatSessionResponse(
            String id, String username, Instant createdAt, Instant lastMessageAt
    ) {
        public static ChatSessionResponse from(ChatSession s) {
            return new ChatSessionResponse(s.id(), s.username(), s.createdAt(), s.lastMessageAt());
        }
    }

    public record ChatSessionWithMessagesResponse(
            String id, String username, Instant createdAt, Instant lastMessageAt,
            List<MessageDto> messages
    ) {
        public static ChatSessionWithMessagesResponse from(ChatSession s) {
            List<MessageDto> msgs = s.messages() == null ? List.of() :
                    s.messages().stream().map(m -> new MessageDto(m.role(), m.content())).toList();
            return new ChatSessionWithMessagesResponse(
                    s.id(), s.username(), s.createdAt(), s.lastMessageAt(), msgs);
        }
    }

    /** Resposta de documento — inclui namespace, versão e status de processamento (#33, #26, #22). */
    public record DocumentResponse(
            String id, String fileName, String mimeType,
            long fileSizeBytes, Instant ingestedAt,
            String namespaceId, int version, boolean hasHistory, String processingStatus
    ) {
        public static DocumentResponse from(Document doc) {
            return new DocumentResponse(
                    doc.id(), doc.fileName(), doc.mimeType(),
                    doc.fileSizeBytes(), doc.ingestedAt(),
                    doc.namespaceId(), doc.version(),
                    doc.version() > 1 || doc.originalDocumentId() != null,
                    doc.processingStatus()
            );
        }
    }

    /** Resposta do upload assíncrono (#22) — HTTP 202. */
    public record IngestResponse(
            String documentId,
            String message,
            int version,
            String processingStatus   // "PROCESSING" enquanto o pipeline roda em background
    ) {
        /** Ctor de compatibilidade para testes que não usam processingStatus. */
        public IngestResponse(String documentId, String message, int version) {
            this(documentId, message, version, "PROCESSING");
        }
    }

    /** Resposta do endpoint GET /api/documents/{id}/status (#22). */
    public record DocumentStatusResponse(
            String documentId,
            String fileName,
            String processingStatus,
            String message
    ) {
        public static DocumentStatusResponse from(Document doc) {
            String msg = switch (doc.processingStatus()) {
                case "PROCESSING" -> "Documento em processamento. Aguarde.";
                case "READY"      -> "Documento disponível para consulta.";
                case "FAILED"     -> "Falha no processamento. Tente fazer upload novamente.";
                default           -> doc.processingStatus();
            };
            return new DocumentStatusResponse(doc.id(), doc.fileName(), doc.processingStatus(), msg);
        }
    }

    public record ErrorResponse(String error, String message) {}

    /** Resposta do endpoint GET /api/users/me/usage */
    public record TokenUsageResponse(
            String username,
            long tokensUsed,
            long monthlyLimit,
            double percentUsed
    ) {
        public TokenUsageResponse(String username, long tokensUsed, long monthlyLimit) {
            this(username, tokensUsed, monthlyLimit,
                    monthlyLimit > 0 ? Math.round((double) tokensUsed / monthlyLimit * 1000) / 10.0 : 0.0);
        }
    }

    /** Resposta de namespace (#33). */
    public record NamespaceResponse(
            String id, String slug, String displayName, Instant createdAt
    ) {
        public static NamespaceResponse from(Namespace ns) {
            return new NamespaceResponse(ns.id(), ns.slug(), ns.displayName(), ns.createdAt());
        }
    }

    /** Histórico de versões de um documento (#26). */
    public record DocumentVersionResponse(
            String id, int version, Instant ingestedAt, String status
    ) {}
}
