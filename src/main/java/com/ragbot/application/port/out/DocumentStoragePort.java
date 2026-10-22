package com.ragbot.application.port.out;

import com.ragbot.domain.entity.Document;
import com.ragbot.domain.entity.Namespace;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public interface DocumentStoragePort {

    /**
     * Armazena um documento no namespace especificado (#33).
     * O documento é gravado com processing_status='PROCESSING'.
     * Retorna o ID gerado para o documento.
     */
    String store(String fileName, String mimeType, long sizeBytes,
                 InputStream content, String namespaceId, int version, String originalDocumentId);

    /** Compatibilidade retroativa — usa namespace padrão, versão 1. */
    default String store(String fileName, String mimeType, long sizeBytes, InputStream content) {
        return store(fileName, mimeType, sizeBytes, content, Namespace.DEFAULT_ID, 1, null);
    }

    InputStream retrieve(String documentId);

    /** Busca documento por ID, independente de status. */
    Optional<Document> findById(String documentId);

    /** Lista apenas documentos ACTIVE no namespace informado (#33). */
    List<Document> findByNamespace(String namespaceId);

    /** Lista todos os documentos ACTIVE (todos os namespaces). */
    List<Document> findAll();

    /** Retorna o documento ACTIVE com o nome exato dentro do namespace (#26). */
    Optional<Document> findActiveByFileName(String fileName, String namespaceId);

    /** Lista o histórico de versões de um arquivo (ACTIVE + SUPERSEDED), mais novo primeiro (#26). */
    List<Document> findVersionHistory(String rootDocumentId);

    /** Soft-delete: muda status para DELETED. */
    void delete(String documentId);

    /** Marca como SUPERSEDED (substituído por versão mais nova). */
    void supersede(String documentId);

    /**
     * Atualiza o status de processamento assíncrono (#22).
     * Só atualiza se o documento ainda estiver ACTIVE (evita escrita em DELETED).
     *
     * @param status "READY" ou "FAILED"
     */
    void updateProcessingStatus(String documentId, String status);

    // ── Namespaces ──────────────────────────────────────────────────────────────

    List<Namespace> findAllNamespaces();

    Optional<Namespace> findNamespaceBySlug(String slug);

    Namespace createNamespace(String slug, String displayName);

    /** Remove namespace apenas se não houver documentos ACTIVE vinculados. */
    void deleteNamespace(String namespaceId);
}
