package com.ragbot.application.port.in;

import com.ragbot.domain.entity.Namespace;

import java.io.InputStream;

public interface IngestDocumentUseCase {

    /**
     * Ingere um documento no namespace especificado (#33).
     * Se já existir um ACTIVE com o mesmo nome no namespace, cria nova versão (#26).
     *
     * @return ID do documento criado
     */
    String ingest(String fileName, String mimeType, InputStream content,
                  long sizeBytes, String namespaceId);

    /** Compatibilidade retroativa — usa namespace padrão. */
    default String ingest(String fileName, String mimeType, InputStream content, long sizeBytes) {
        return ingest(fileName, mimeType, content, sizeBytes, Namespace.DEFAULT_ID);
    }
}
