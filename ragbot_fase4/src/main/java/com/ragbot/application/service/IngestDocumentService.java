package com.ragbot.application.service;

import com.ragbot.application.port.in.IngestDocumentUseCase;
import com.ragbot.application.port.out.*;
import com.ragbot.domain.entity.Chunk;
import com.ragbot.domain.entity.Document;
import com.ragbot.domain.exception.UnsupportedDocumentTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Serviço de ingestão de documentos.
 *
 * Fase 10 — novas responsabilidades:
 *
 * #33 Namespaces: o documento é indexado no namespace especificado.
 * Chunks são buscados escopados ao namespace via JOIN no PgVectorStoreAdapter.
 *
 * #26 Versionamento: se já existir um ACTIVE com o mesmo nome no namespace:
 *   1. A versão antiga é marcada como SUPERSEDED
 *   2. Os chunks antigos são soft-deletados
 *   3. O novo documento recebe version = old.version + 1
 *      e original_document_id = old.rootId()
 */
@Service
public class IngestDocumentService implements IngestDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(IngestDocumentService.class);

    private final List<DocumentParserPort> parsers;
    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final DocumentStoragePort storagePort;
    private final ChunkingStrategy chunkingStrategy;

    public IngestDocumentService(
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

    @Override
    public String ingest(String fileName, String mimeType, InputStream content,
                         long sizeBytes, String namespaceId) {
        log.info("Iniciando ingestão: {} ({}) namespace={}", fileName, mimeType, namespaceId);

        DocumentParserPort parser = parsers.stream()
                .filter(p -> p.supports(mimeType))
                .findFirst()
                .orElseThrow(() -> new UnsupportedDocumentTypeException(mimeType));

        // ── #26 Versionamento ───────────────────────────────────────────────
        Optional<Document> existing = storagePort.findActiveByFileName(fileName, namespaceId);
        int newVersion = 1;
        String originalDocumentId = null;

        if (existing.isPresent()) {
            Document prev = existing.get();
            newVersion = prev.version() + 1;
            originalDocumentId = prev.rootId();   // aponta sempre para a v1

            log.info("Versão anterior encontrada: id={} v{} → criando v{}",
                    prev.id(), prev.version(), newVersion);

            // Invalida chunks antigos e marca documento como superseded
            vectorStorePort.deleteByDocumentId(prev.id());
            storagePort.supersede(prev.id());
        }

        // ── Armazena arquivo e registra no banco ────────────────────────────
        String documentId = storagePort.store(
                fileName, mimeType, sizeBytes, content,
                namespaceId, newVersion, originalDocumentId
        );
        log.info("Documento armazenado: id={} v{}", documentId, newVersion);

        // ── Extrai texto e indexa chunks ────────────────────────────────────
        InputStream stored = storagePort.retrieve(documentId);
        String rawText = parser.extractText(stored, fileName);
        log.info("Texto extraído: {} caracteres", rawText.length());

        List<String> chunks = chunkingStrategy.split(rawText);
        log.info("Dividido em {} chunks", chunks.size());

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
                            "version",     String.valueOf(newVersion),
                            "chunkIndex",  String.valueOf(i),
                            "totalChunks", String.valueOf(chunks.size())
                    )
            ));
        }

        log.info("Ingestão concluída: {} chunks indexados para documentId={} v{}",
                chunks.size(), documentId, newVersion);
        return documentId;
    }
}
