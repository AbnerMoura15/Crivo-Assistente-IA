package com.ragbot.application.service;

import com.ragbot.application.port.in.ListDocumentsUseCase;
import com.ragbot.application.port.out.DocumentStoragePort;
import com.ragbot.application.port.out.VectorStorePort;
import com.ragbot.domain.entity.Document;
import com.ragbot.domain.exception.DocumentNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ListDocumentsService implements ListDocumentsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListDocumentsService.class);

    private final DocumentStoragePort storagePort;
    private final VectorStorePort vectorStorePort;
    private final AsyncIngestWorker asyncIngestWorker;

    public ListDocumentsService(DocumentStoragePort storagePort,
                                VectorStorePort vectorStorePort,
                                AsyncIngestWorker asyncIngestWorker) {
        this.storagePort        = storagePort;
        this.vectorStorePort    = vectorStorePort;
        this.asyncIngestWorker  = asyncIngestWorker;
    }

    @Override
    public List<Document> listByNamespace(String namespaceId) {
        return storagePort.findByNamespace(namespaceId);
    }

    @Override
    public List<Document> listAll() {
        return storagePort.findAll();
    }

    @Override
    public Optional<Document> findById(String documentId) {
        return storagePort.findById(documentId);
    }

    @Override
    public List<Document> listVersionHistory(String documentId) {
        // Tenta via findById (inclui SUPERSEDED)
        Document doc = storagePort.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        return storagePort.findVersionHistory(doc.rootId());
    }

    @Override
    public void delete(String documentId) {
        // findAll() retorna apenas ACTIVE — se não encontrado, lança exceção
        boolean exists = storagePort.findAll().stream()
                .anyMatch(d -> d.id().equals(documentId));
        if (!exists) {
            throw new DocumentNotFoundException(documentId);
        }

        vectorStorePort.deleteByDocumentId(documentId);
        storagePort.delete(documentId);

        log.info("Documento removido (soft delete): {}", documentId);
    }

    @Override
    public boolean retryProcessing(String documentId) {
        Optional<Document> docOpt = storagePort.findById(documentId);
        if (docOpt.isEmpty()) return false;

        Document doc = docOpt.get();
        if (!"FAILED".equals(doc.processingStatus())) {
            log.warn("Retry ignorado — documento {} está em status '{}' (esperado FAILED)",
                    documentId, doc.processingStatus());
            return false;
        }

        storagePort.updateProcessingStatus(documentId, "PROCESSING");
        asyncIngestWorker.processAsync(documentId, doc.fileName(), doc.mimeType(),
                doc.namespaceId(), doc.version());
        log.info("Reprocessamento iniciado para documento FAILED: {} ({})", documentId, doc.fileName());
        return true;
    }
}
