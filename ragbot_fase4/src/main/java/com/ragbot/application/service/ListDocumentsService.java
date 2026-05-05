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
    public List<Document> listAll() {
        // findAll() já filtra apenas ACTIVE — vide LocalDocumentStorageAdapter
        return storagePort.findAll();
    }

    @Override
    public void delete(String documentId) {
        // #29: valida existência antes de deletar (findAll retorna apenas ACTIVE)
        boolean exists = storagePort.findAll().stream()
                .anyMatch(doc -> doc.id().equals(documentId));

        if (!exists) {
            throw new DocumentNotFoundException(documentId);
        }

        // Ordem importa: chunks primeiro, depois o documento
        // Assim a integridade é mantida mesmo se houver falha parcial
        vectorStorePort.deleteByDocumentId(documentId);  // soft delete nos chunks
        storagePort.delete(documentId);                   // soft delete no documento

        log.info("Documento removido (soft delete): {}", documentId);
    }
}
