package com.ragbot.adapter.in.rest.dto;

import com.ragbot.domain.entity.Document;
import com.ragbot.domain.entity.ChatSession;

import java.time.Instant;
import java.util.List;

public class RestDtos {

    public record ChatRequest(String question, String sessionId, List<MessageDto> history) {}

    public record ChatResponse(String answer, String interactionId, String sessionId) {}

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

    public record DocumentResponse(
            String id, String fileName, String mimeType,
            long fileSizeBytes, Instant ingestedAt
    ) {
        public static DocumentResponse from(Document doc) {
            return new DocumentResponse(doc.id(), doc.fileName(), doc.mimeType(),
                    doc.fileSizeBytes(), doc.ingestedAt());
        }
    }

    public record IngestResponse(String documentId, String message) {}

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
}
