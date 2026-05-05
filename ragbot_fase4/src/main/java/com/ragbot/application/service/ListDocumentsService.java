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

@Service
public class ListDocumentsService implements ListDocumentsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListDocumentsService.class);

    private final DocumentStoragePort storagePort;
    private final VectorStorePort vectorStorePort;

    public ListDocumentsService(DocumentStoragePort storagePort, VectorStorePort vectorStorePort) {
        this.storagePort = storagePort;
        this.vectorStorePort = vectorStorePort;
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
    public List<Document> listVersionHistory(String documentId) {
        // Busca raiz: pode ser o próprio doc (v1) ou o original_document_id
        List<Document> allActive = storagePort.findAll();
        Document doc = allActive.stream()
                .filter(d -> d.id().equals(documentId))
                .findFirst()
                .orElseGet(() -> {
                    // Pode ser uma versão antiga (SUPERSEDED) — busca pelo rootId
                    List<Document> history = storagePort.findVersionHistory(documentId);
                    return history.stream()
                            .filter(d -> d.id().equals(documentId))
                            .findFirst()
                            .orElseThrow(() -> new DocumentNotFoundException(documentId));
                });

        return storagePort.findVersionHistory(doc.rootId());
    }

    @Override
    public void delete(String documentId) {
        boolean exists = storagePort.findAll().stream()
                .anyMatch(d -> d.id().equals(documentId));
        if (!exists) {
            throw new DocumentNotFoundException(documentId);
        }

        vectorStorePort.deleteByDocumentId(documentId);
        storagePort.delete(documentId);

        log.info("Documento removido (soft delete): {}", documentId);
    }
}
