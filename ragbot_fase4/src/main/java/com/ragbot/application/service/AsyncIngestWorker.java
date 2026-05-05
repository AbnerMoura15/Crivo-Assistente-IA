package com.ragbot.application.service;

import com.ragbot.application.port.out.DocumentParserPort;
import com.ragbot.application.port.out.DocumentStoragePort;
import com.ragbot.application.port.out.EmbeddingPort;
import com.ragbot.application.port.out.VectorStorePort;
import com.ragbot.domain.entity.Chunk;
import com.ragbot.domain.exception.UnsupportedDocumentTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Worker assíncrono de ingestão de documentos (#22).
 *
 * Executa o pipeline pesado (parse → chunk → embed → index) em background,
 * liberando o request HTTP imediatamente com HTTP 202.
 *
 * IMPORTANTE: este bean é separado do IngestDocumentService para que
 * o proxy @Async do Spring intercepte corretamente as chamadas.
 *
 * Ao final (sucesso ou falha), atualiza processing_status do documento.
 */
@Component
public class AsyncIngestWorker {

    private static final Logger log = LoggerFactory.getLogger(AsyncIngestWorker.class);

    private final List<DocumentParserPort> parsers;
    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final DocumentStoragePort storagePort;
    private final ChunkingStrategy chunkingStrategy;

    public AsyncIngestWorker(
            List<DocumentParserPort> parsers,
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            DocumentStoragePort storagePort,
            ChunkingStrategy chunkingStrategy) {
        this.parsers = parsers;
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
        this.storagePort = storagePort;
        this.chunkingStrategy = chunkingStrategy;
    }

    /**
     * Processa o documento em background.
     *
     * @param documentId  ID já gravado no banco com status PROCESSING
     * @param fileName    nome original do arquivo
     * @param mimeType    tipo MIME para selecionar o parser
     * @param namespaceId namespace ao qual o documento pertence
     * @param version     número da versão do documento
     */
    @Async("ingestExecutor")
    public void processAsync(String documentId, String fileName, String mimeType,
                             String namespaceId, int version) {
        log.info("[async] Iniciando processamento: documentId={} fileName={} v{}", documentId, fileName, version);
        try {
            // 1. Recupera o arquivo gravado em disco pelo IngestDocumentService
            InputStream stored = storagePort.retrieve(documentId);

            // 2. Seleciona parser pelo MIME type
            DocumentParserPort parser = parsers.stream()
                    .filter(p -> p.supports(mimeType))
                    .findFirst()
                    .orElseThrow(() -> new UnsupportedDocumentTypeException(mimeType));

            // 3. Extrai texto
            String rawText = parser.extractText(stored, fileName);
            log.info("[async] Texto extraído: {} caracteres (documentId={})", rawText.length(), documentId);

            // 4. Chunkeia e embeda
            List<String> chunks = chunkingStrategy.split(rawText);
            log.info("[async] Dividido em {} chunks (documentId={})", chunks.size(), documentId);

            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                List<Float> embedding = embeddingPort.embed(chunkText);
                vectorStorePort.save(new Chunk(
                        UUID.randomUUID().toString(),
                        documentId,
                        chunkText,
                        embedding,
                        Map.of(
                                "source",      fileName,
                                "namespace",   namespaceId,
                                "version",     String.valueOf(version),
                                "chunkIndex",  String.valueOf(i),
                                "totalChunks", String.valueOf(chunks.size())
                        )
                ));
            }

            // 5. Marca como READY
            storagePort.updateProcessingStatus(documentId, "READY");
            log.info("[async] Concluído com sucesso: {} chunks indexados (documentId={})",
                    chunks.size(), documentId);

        } catch (Exception e) {
            log.error("[async] Falha no processamento do documentId={}: {}", documentId, e.getMessage(), e);
            storagePort.updateProcessingStatus(documentId, "FAILED");
        }
    }
}
