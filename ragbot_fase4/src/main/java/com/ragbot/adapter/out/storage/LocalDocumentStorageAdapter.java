package com.ragbot.adapter.out.storage;

import com.ragbot.application.port.out.DocumentStoragePort;
import com.ragbot.domain.entity.Document;
import com.ragbot.domain.entity.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter de armazenamento local de documentos.
 *
 * Fase 10 — novas responsabilidades:
 * - store()               → grava namespace_id, version e original_document_id (#33, #26)
 * - findByNamespace()     → filtra por namespace (#33)
 * - findActiveByFileName() → busca versão atual de um arquivo (#26)
 * - findVersionHistory()  → lista todas as versões de um arquivo (#26)
 * - supersede()           → marca status='SUPERSEDED' (versão antiga) (#26)
 * - Namespace CRUD        → criar/listar/deletar namespaces (#33)
 */
@Component
public class LocalDocumentStorageAdapter implements DocumentStoragePort {

    private static final Logger log = LoggerFactory.getLogger(LocalDocumentStorageAdapter.class);

    private final Path storageDir;
    private final JdbcTemplate jdbc;

    public LocalDocumentStorageAdapter(
            @Value("${ragbot.storage.dir:./documents}") String storagePath,
            JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.storageDir = Paths.get(storagePath);
        try {
            Files.createDirectories(storageDir);
            log.info("Diretório de armazenamento: {}", storageDir.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível criar o diretório de armazenamento", e);
        }
    }

    // ── Documentos ────────────────────────────────────────────────────────────

    @Override
    public String store(String fileName, String mimeType, long sizeBytes,
                        InputStream content, String namespaceId, int version,
                        String originalDocumentId) {
        String documentId = UUID.randomUUID().toString();
        Path filePath = storageDir.resolve(documentId);

        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            content.transferTo(fos);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gravar arquivo: " + fileName, e);
        }

        jdbc.update(
                "INSERT INTO documents " +
                "(id, file_name, mime_type, storage_reference, file_size_bytes, ingested_at, " +
                " status, namespace_id, version_number, original_document_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)",
                documentId, fileName, mimeType, filePath.toString(),
                sizeBytes, Timestamp.from(Instant.now()),
                namespaceId, version, originalDocumentId
        );

        log.info("Documento armazenado: id={} nome={} namespace={} v{}",
                documentId, fileName, namespaceId, version);
        return documentId;
    }

    @Override
    public InputStream retrieve(String documentId) {
        Path filePath = storageDir.resolve(documentId);
        if (!Files.exists(filePath)) {
            throw new RuntimeException("Arquivo não encontrado: " + documentId);
        }
        try {
            return new FileInputStream(filePath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler arquivo: " + documentId, e);
        }
    }

    @Override
    public List<Document> findByNamespace(String namespaceId) {
        return jdbc.query(
                "SELECT id, file_name, mime_type, storage_reference, file_size_bytes, ingested_at, " +
                "       namespace_id, version_number, original_document_id " +
                "FROM documents " +
                "WHERE status = 'ACTIVE' AND namespace_id = ? " +
                "ORDER BY ingested_at DESC",
                this::mapRow, namespaceId
        );
    }

    @Override
    public List<Document> findAll() {
        return jdbc.query(
                "SELECT id, file_name, mime_type, storage_reference, file_size_bytes, ingested_at, " +
                "       namespace_id, version_number, original_document_id " +
                "FROM documents WHERE status = 'ACTIVE' ORDER BY ingested_at DESC",
                this::mapRow
        );
    }

    @Override
    public Optional<Document> findActiveByFileName(String fileName, String namespaceId) {
        List<Document> results = jdbc.query(
                "SELECT id, file_name, mime_type, storage_reference, file_size_bytes, ingested_at, " +
                "       namespace_id, version_number, original_document_id " +
                "FROM documents " +
                "WHERE file_name = ? AND namespace_id = ? AND status = 'ACTIVE' " +
                "ORDER BY version_number DESC LIMIT 1",
                this::mapRow, fileName, namespaceId
        );
        return results.stream().findFirst();
    }

    @Override
    public List<Document> findVersionHistory(String rootDocumentId) {
        return jdbc.query(
                "SELECT id, file_name, mime_type, storage_reference, file_size_bytes, ingested_at, " +
                "       namespace_id, version_number, original_document_id " +
                "FROM documents " +
                "WHERE id = ? OR original_document_id = ? " +
                "ORDER BY version_number DESC",
                this::mapRow, rootDocumentId, rootDocumentId
        );
    }

    @Override
    public void delete(String documentId) {
        int updated = jdbc.update(
                "UPDATE documents SET status = 'DELETED' WHERE id = ? AND status = 'ACTIVE'",
                documentId
        );
        if (updated == 0) {
            log.warn("Soft delete sem efeito: documento {} não encontrado ou já deletado", documentId);
        } else {
            log.info("Soft delete realizado: documentId={}", documentId);
        }
    }

    @Override
    public void supersede(String documentId) {
        int updated = jdbc.update(
                "UPDATE documents SET status = 'SUPERSEDED' WHERE id = ? AND status = 'ACTIVE'",
                documentId
        );
        if (updated > 0) {
            log.info("Documento marcado como SUPERSEDED: {}", documentId);
        }
    }

    // ── Namespaces ────────────────────────────────────────────────────────────

    @Override
    public List<Namespace> findAllNamespaces() {
        return jdbc.query(
                "SELECT id, slug, display_name, created_at FROM namespaces ORDER BY display_name",
                (rs, rowNum) -> new Namespace(
                        rs.getString("id"),
                        rs.getString("slug"),
                        rs.getString("display_name"),
                        rs.getTimestamp("created_at").toInstant()
                )
        );
    }

    @Override
    public Optional<Namespace> findNamespaceBySlug(String slug) {
        List<Namespace> results = jdbc.query(
                "SELECT id, slug, display_name, created_at FROM namespaces WHERE slug = ?",
                (rs, rowNum) -> new Namespace(
                        rs.getString("id"),
                        rs.getString("slug"),
                        rs.getString("display_name"),
                        rs.getTimestamp("created_at").toInstant()
                ), slug
        );
        return results.stream().findFirst();
    }

    @Override
    public Namespace createNamespace(String slug, String displayName) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO namespaces (id, slug, display_name, created_at) VALUES (?, ?, ?, ?)",
                id, slug, displayName, Timestamp.from(now)
        );
        log.info("Namespace criado: slug={} id={}", slug, id);
        return new Namespace(id, slug, displayName, now);
    }

    @Override
    public void deleteNamespace(String namespaceId) {
        if (namespaceId.equals(Namespace.DEFAULT_ID)) {
            throw new IllegalArgumentException("O namespace padrão não pode ser deletado.");
        }
        Long activeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE namespace_id = ? AND status = 'ACTIVE'",
                Long.class, namespaceId
        );
        if (activeCount != null && activeCount > 0) {
            throw new IllegalStateException(
                    "Namespace ainda possui " + activeCount + " documento(s) ativo(s). Exclua-os antes.");
        }
        jdbc.update("DELETE FROM namespaces WHERE id = ?", namespaceId);
        log.info("Namespace removido: {}", namespaceId);
    }

    // ── mapper ────────────────────────────────────────────────────────────────

    private Document mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Document(
                rs.getString("id"),
                rs.getString("file_name"),
                rs.getString("mime_type"),
                rs.getString("storage_reference"),
                rs.getLong("file_size_bytes"),
                rs.getTimestamp("ingested_at").toInstant(),
                rs.getString("namespace_id"),
                rs.getInt("version_number"),
                rs.getString("original_document_id")
        );
    }
}
