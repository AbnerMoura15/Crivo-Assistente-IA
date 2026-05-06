package com.ragbot.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragbot.adapter.out.vectorstore.PgVectorStoreAdapter;
import com.ragbot.domain.entity.Chunk;
import com.ragbot.domain.entity.Namespace;
import com.ragbot.domain.entity.ScoredChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para PgVectorStoreAdapter.
 *
 * Valida persistência de chunks, busca vetorial (findSimilar) e
 * soft delete (deleteByDocumentId) contra PostgreSQL+pgvector real.
 */
class PgVectorStoreIntegrationTest extends AbstractIntegrationTest {

    /** Dimensão dos embeddings configurada no schema (V2). */
    private static final int EMBEDDING_DIMS = 3072;

    private PgVectorStoreAdapter adapter;
    private String documentId;

    @BeforeEach
    void criarAdapter() {
        adapter = new PgVectorStoreAdapter(JDBC, new ObjectMapper());

        // Insere um documento mínimo para satisfazer a FK chunks.document_id → documents.id
        documentId = UUID.randomUUID().toString();
        JDBC.update(
                "INSERT INTO documents (id, file_name, mime_type, storage_reference, " +
                "file_size_bytes, ingested_at, status, namespace_id, version_number, processing_status) " +
                "VALUES (?, 'test.pdf', 'application/pdf', '/tmp/test', 0, ?, 'ACTIVE', ?, 1, 'READY')",
                documentId, Timestamp.from(Instant.now()), Namespace.DEFAULT_ID);
    }

    // ── save ─────────────────────────────────────────────────────────────────

    @Test
    void save_persisteChunkNoBanco() {
        Chunk chunk = novoChunk("Conteúdo do primeiro chunk.");

        adapter.save(chunk);

        Integer count = JDBC.queryForObject(
                "SELECT COUNT(*) FROM chunks WHERE id = ?", Integer.class, chunk.id());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void save_chunkIniciaComStatusActive() {
        Chunk chunk = novoChunk("Texto de teste.");
        adapter.save(chunk);

        String status = JDBC.queryForObject(
                "SELECT status FROM chunks WHERE id = ?", String.class, chunk.id());
        assertThat(status).isEqualTo("ACTIVE");
    }

    @Test
    void save_persisteMetadata() {
        Chunk chunk = new Chunk(
                UUID.randomUUID().toString(), documentId,
                "Chunk com metadata.",
                vetorAleatorio(),
                Map.of("page", "3", "source", "manual.pdf"));
        adapter.save(chunk);

        String metadata = JDBC.queryForObject(
                "SELECT metadata::text FROM chunks WHERE id = ?", String.class, chunk.id());
        assertThat(metadata).contains("page").contains("manual.pdf");
    }

    // ── countByDocumentId ────────────────────────────────────────────────────

    @Test
    void countByDocumentId_retornaQuantidadeCorreta() {
        adapter.save(novoChunk("Chunk 1"));
        adapter.save(novoChunk("Chunk 2"));
        adapter.save(novoChunk("Chunk 3"));

        long count = adapter.countByDocumentId(documentId);
        assertThat(count).isEqualTo(3);
    }

    @Test
    void countByDocumentId_retornaZeroParaDocSemChunks() {
        long count = adapter.countByDocumentId("doc-inexistente");
        assertThat(count).isZero();
    }

    // ── deleteByDocumentId ───────────────────────────────────────────────────

    @Test
    void deleteByDocumentId_softDeleteChunks() {
        adapter.save(novoChunk("A"));
        adapter.save(novoChunk("B"));

        adapter.deleteByDocumentId(documentId);

        Integer ativos = JDBC.queryForObject(
                "SELECT COUNT(*) FROM chunks WHERE document_id = ? AND status = 'ACTIVE'",
                Integer.class, documentId);
        assertThat(ativos).isZero();
    }

    @Test
    void deleteByDocumentId_registrosAindaExistemComStatusDeleted() {
        adapter.save(novoChunk("A"));
        adapter.save(novoChunk("B"));

        adapter.deleteByDocumentId(documentId);

        Integer deletados = JDBC.queryForObject(
                "SELECT COUNT(*) FROM chunks WHERE document_id = ? AND status = 'DELETED'",
                Integer.class, documentId);
        assertThat(deletados).isEqualTo(2);
    }

    @Test
    void deleteByDocumentId_countByDocumentIdRetornaZeroAposDelete() {
        adapter.save(novoChunk("X"));
        adapter.deleteByDocumentId(documentId);

        assertThat(adapter.countByDocumentId(documentId)).isZero();
    }

    // ── findSimilar ───────────────────────────────────────────────────────────

    @Test
    void findSimilar_retornaTopKResultados() {
        adapter.save(novoChunk("Dados sobre Spring Boot e microsserviços."));
        adapter.save(novoChunk("Receita de bolo de cenoura com cobertura."));
        adapter.save(novoChunk("Configuração de Kubernetes em produção."));

        List<ScoredChunk> resultados = adapter.findSimilar(
                vetorAleatorio(), 2, Namespace.DEFAULT_ID);

        assertThat(resultados).hasSize(2);
    }

    @Test
    void findSimilar_naoRetornaChunksDeletados() {
        adapter.save(novoChunk("Chunk visível."));
        Chunk deletado = novoChunk("Chunk deletado.");
        adapter.save(deletado);
        adapter.deleteByDocumentId(documentId);

        // Todos os chunks do documento foram deletados
        List<ScoredChunk> resultados = adapter.findSimilar(
                vetorAleatorio(), 10, Namespace.DEFAULT_ID);

        assertThat(resultados).isEmpty();
    }

    @Test
    void findSimilar_retornaVazioParaNamespaceInexistente() {
        adapter.save(novoChunk("Conteúdo qualquer."));

        List<ScoredChunk> resultados = adapter.findSimilar(
                vetorAleatorio(), 5, "namespace-inexistente");

        assertThat(resultados).isEmpty();
    }

    @Test
    void findSimilarHybrid_retornaResultados() {
        adapter.save(novoChunk("Spring Boot e injeção de dependência."));
        adapter.save(novoChunk("Configuração do Maven e Gradle."));

        List<ScoredChunk> resultados = adapter.findSimilarHybrid(
                vetorAleatorio(), "spring boot", 2, 0.85, Namespace.DEFAULT_ID);

        assertThat(resultados).hasSizeLessThanOrEqualTo(2);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Chunk novoChunk(String conteudo) {
        return new Chunk(
                UUID.randomUUID().toString(),
                documentId,
                conteudo,
                vetorAleatorio(),
                Map.of("source", "test"));
    }

    /**
     * Cria um vetor de floats com a dimensão correta (3072) para o pgvector.
     * Os valores são determinísticos para reprodutibilidade.
     */
    private static List<Float> vetorAleatorio() {
        List<Float> v = new ArrayList<>(EMBEDDING_DIMS);
        for (int i = 0; i < EMBEDDING_DIMS; i++) {
            // Valores distribuídos entre -1.0 e 1.0
            v.add((float) Math.sin(i * 0.01));
        }
        return v;
    }
}
