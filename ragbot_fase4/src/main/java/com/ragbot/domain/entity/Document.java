package com.ragbot.domain.entity;

import java.time.Instant;

/**
 * Entidade de domínio que representa um documento indexado.
 *
 * Fase 10 — novas propriedades:
 *   namespaceId          → namespace ao qual o documento pertence (#33)
 *   version              → número da versão (1 = primeira, 2 = segunda...) (#26)
 *   originalDocumentId   → ID da versão 1 do arquivo; null se este já é a v1 (#26)
 *   processingStatus     → PROCESSING | READY | FAILED (#22 — ingestão assíncrona)
 */
public record Document(
        String id,
        String fileName,
        String mimeType,
        String storageReference,
        long fileSizeBytes,
        Instant ingestedAt,
        String namespaceId,
        int version,
        String originalDocumentId,   // null na v1; aponta para o id da v1 nas versões seguintes
        String processingStatus      // PROCESSING, READY ou FAILED
) {
    /** Ctor de compatibilidade — documentos sem namespace/versão/status. */
    public Document(String id, String fileName, String mimeType,
                    String storageReference, long fileSizeBytes, Instant ingestedAt) {
        this(id, fileName, mimeType, storageReference, fileSizeBytes, ingestedAt,
                Namespace.DEFAULT_ID, 1, null, "READY");
    }

    /** Ctor de compatibilidade — documentos com namespace+versão mas sem processingStatus. */
    public Document(String id, String fileName, String mimeType,
                    String storageReference, long fileSizeBytes, Instant ingestedAt,
                    String namespaceId, int version, String originalDocumentId) {
        this(id, fileName, mimeType, storageReference, fileSizeBytes, ingestedAt,
                namespaceId, version, originalDocumentId, "READY");
    }

    /** Verdadeiro se este documento é a versão mais original (v1 sem histórico prévio). */
    public boolean isFirstVersion() {
        return originalDocumentId == null;
    }

    /** Retorna o ID da "raiz" da cadeia de versões (usado para agrupar histórico). */
    public String rootId() {
        return originalDocumentId != null ? originalDocumentId : id;
    }

    /** Verdadeiro se o embedding está concluído e o documento pode ser consultado. */
    public boolean isReady() {
        return "READY".equals(processingStatus);
    }
}
