package com.ragbot.application.port.in;

import com.ragbot.domain.entity.Namespace;

import java.io.InputStream;

public interface IngestDocumentUseCase {

    /**
     * Inicia a ingestão assíncrona de um documento no namespace especificado (#22, #33).
     *
     * O arquivo é gravado em disco imediatamente; o pipeline de chunking/embedding
     * é executado em background. O documento fica com status PROCESSING até
     * o processamento completar (READY) ou falhar (FAILED).
     *
     * @return resultado com documentId, versão e status inicial
     */
    IngestResult ingest(String fileName, String mimeType, InputStream content,
                        long sizeBytes, String namespaceId);

    /** Compatibilidade retroativa — usa namespace padrão. */
    default IngestResult ingest(String fileName, String mimeType, InputStream content, long sizeBytes) {
        return ingest(fileName, mimeType, content, sizeBytes, Namespace.DEFAULT_ID);
    }

    record IngestResult(String documentId, int version, String processingStatus) {}
}
