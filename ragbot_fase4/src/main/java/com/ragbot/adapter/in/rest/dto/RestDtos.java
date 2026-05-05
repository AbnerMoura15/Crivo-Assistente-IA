package com.ragbot.adapter.in.rest.dto;

import com.ragbot.domain.entity.Document;

import java.time.Instant;
import java.util.List;

public class RestDtos {

    public record ChatRequest(String question, List<MessageDto> history) {}

    public record ChatResponse(String answer) {}

    public record MessageDto(String role, String content) {}

    public record DocumentResponse(
            String id,
            String fileName,
            String mimeType,
            long fileSizeBytes,
            Instant ingestedAt
    ) {
        public static DocumentResponse from(Document doc) {
            return new DocumentResponse(
                    doc.id(), doc.fileName(), doc.mimeType(),
                    doc.fileSizeBytes(), doc.ingestedAt()
            );
        }
    }

    public record IngestResponse(String documentId, String message) {}

    public record ErrorResponse(String error, String message) {}
}
