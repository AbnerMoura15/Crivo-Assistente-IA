package com.ragbot.application.service;

import com.ragbot.application.port.in.IngestDocumentUseCase;
import com.ragbot.application.port.out.DocumentStoragePort;
import com.ragbot.application.port.out.VectorStorePort;
import com.ragbot.domain.entity.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Optional;

/**
 * Serviço de ingestão de documentos (#22 — assíncrona).
 *
 * Responsabilidades:
 *  1. #26 Versionamento — detecta documento existente, supersede, incrementa versão
 *  2. Grava o arquivo em disco e registra no banco com processing_status=PROCESSING
 *  3. Dispara o AsyncIngestWorker em background (parse → chunk → embed → READY/FAILED)
 *  4. Retorna IngestResult imediatamente (HTTP 202 no controller)
 *
 * O pipeline pesado (embed + index) não bloqueia mais o request HTTP.
 */
@Service
public class IngestDocumentService implements IngestDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(IngestDocumentService.class);

    private final DocumentStoragePort storagePort;
    private final VectorStorePort     vectorStorePort;
    private final AsyncIngestWorker   asyncWorker;

    public IngestDocumentService(
            DocumentStoragePort storagePort,
            VectorStorePort vectorStorePort,
            AsyncIngestWorker asyncWorker) {
        this.storagePort    = storagePort;
        this.vectorStorePort = vectorStorePort;
        this.asyncWorker    = asyncWorker;
    }

    @Override
    public IngestResult ingest(String fileName, String mimeType, InputStream content,
                               long sizeBytes, String namespaceId) {

        log.info("Ingestão solicitada: {} ({}) namespace={}", fileName, mimeType, namespaceId);

        // ── #26 Versionamento ─────────────────────────────────────────────────
        Optional<Document> existing = storagePort.findActiveByFileName(fileName, namespaceId);
        int newVersion = 1;
        String originalDocumentId = null;

        if (existing.isPresent()) {
            Document prev = existing.get();
            newVersion = prev.version() + 1;
            originalDocumentId = prev.rootId();

            log.info("Versão anterior encontrada: id={} v{} → criando v{}",
                    prev.id(), prev.version(), newVersion);

            vectorStorePort.deleteByDocumentId(prev.id());
            storagePort.supersede(prev.id());
        }

        // ── Grava arquivo em disco + registra no banco como PROCESSING ────────
        String documentId = storagePort.store(
                fileName, mimeType, sizeBytes, content,
                namespaceId, newVersion, originalDocumentId
        );
        log.info("Documento registrado (PROCESSING): id={} v{}", documentId, newVersion);

        // ── Dispara processamento assíncrono ──────────────────────────────────
        asyncWorker.processAsync(documentId, fileName, mimeType, namespaceId, newVersion);

        return new IngestResult(documentId, newVersion, "PROCESSING");
    }
}
