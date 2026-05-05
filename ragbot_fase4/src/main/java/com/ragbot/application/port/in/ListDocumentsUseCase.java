package com.ragbot.application.port.in;

import com.ragbot.domain.entity.Document;
import com.ragbot.domain.entity.Namespace;

import java.util.List;

public interface ListDocumentsUseCase {

    /** Lista documentos ACTIVE no namespace informado (#33). */
    List<Document> listByNamespace(String namespaceId);

    /** Lista todos os documentos ACTIVE (todos os namespaces). */
    List<Document> listAll();

    /** Histórico de versões de um documento (#26). */
    List<Document> listVersionHistory(String documentId);

    /** Soft-delete do documento e seus chunks. */
    void delete(String documentId);
}
