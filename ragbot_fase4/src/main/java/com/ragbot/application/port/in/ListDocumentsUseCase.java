package com.ragbot.application.port.in;

import com.ragbot.domain.entity.Document;
import com.ragbot.domain.entity.Namespace;

import java.util.List;
import java.util.Optional;

public interface ListDocumentsUseCase {

    /** Lista documentos ACTIVE no namespace informado (#33). */
    List<Document> listByNamespace(String namespaceId);

    /** Lista todos os documentos ACTIVE (todos os namespaces). */
    List<Document> listAll();

    /** Busca documento por ID (qualquer status). */
    Optional<Document> findById(String documentId);

    /** Histórico de versões de um documento (#26). */
    List<Document> listVersionHistory(String documentId);

    /** Soft-delete do documento e seus chunks. */
    void delete(String documentId);
}
