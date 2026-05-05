package com.ragbot.adapter.out.storage;

import com.ragbot.application.port.out.DocumentStoragePort;
import com.ragbot.domain.entity.Document;
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
import java.util.UUID;

/**
 * Adapter de armazenamento local de documentos.
 *
 * Mudanças do #29 (Soft Delete):
 * - store()   → insere com status='ACTIVE'
 * - findAll() → retorna SOMENTE documentos com status='ACTIVE'
 * - delete()  → faz UPDATE status='DELETED' (não apaga o registro do banco)
 *               O arquivo físico também é mantido (auditoria)
 *
 * Isso garante que documentos "deletados" não aparecem para os usuários
 * mas os dados são preservados para fins de auditoria e rastreabilidade.
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

    @Override
    public String store(String fileName, String mimeType, long sizeBytes, InputStream content) {
        String documentId = UUID.randomUUID().toString();
        Path filePath = storageDir.resolve(documentId);

        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            content.transferTo(fos);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gravar arquivo: " + fileName, e);
        }

        jdbc.update(
                // status='ACTIVE' explícito — documento disponível para consulta
                "INSERT INTO documents (id, file_name, mime_type, storage_reference, file_size_bytes, ingested_at, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')",
                documentId, fileName, mimeType, filePath.toString(),
                sizeBytes, Timestamp.from(Instant.now())
        );

        log.info("Documento armazenado: id={} nome={}", documentId, fileName);
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
    public List<Document> findAll() {
        // #29: retorna APENAS documentos ACTIVE — deletados são invisíveis
        return jdbc.query(
                "SELECT id, file_name, mime_type, storage_reference, file_size_bytes, ingested_at " +
                "FROM documents WHERE status = 'ACTIVE' ORDER BY ingested_at DESC",
                (rs, rowNum) -> new Document(
                        rs.getString("id"),
                        rs.getString("file_name"),
                        rs.getString("mime_type"),
                        rs.getString("storage_reference"),
                        rs.getLong("file_size_bytes"),
                        rs.getTimestamp("ingested_at").toInstant()
                )
        );
    }

    @Override
    public void delete(String documentId) {
        // #29: soft delete — mantém o arquivo físico e o registro do banco
        // Apenas muda o status para DELETED, preservando dados para auditoria
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
}
