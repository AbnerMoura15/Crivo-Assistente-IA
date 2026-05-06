package com.ragbot.integration;

import com.ragbot.adapter.out.storage.LocalDocumentStorageAdapter;
import com.ragbot.domain.entity.Document;
import com.ragbot.domain.entity.Namespace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes de integração para LocalDocumentStorageAdapter.
 *
 * Valida todos os métodos CRUD contra PostgreSQL real (Testcontainers),
 * incluindo store, retrieve, findById, findByNamespace, supersede, delete
 * e updateProcessingStatus.
 */
class DocumentStorageIntegrationTest extends AbstractIntegrationTest {

    private LocalDocumentStorageAdapter adapter;

    @BeforeEach
    void criarAdapter() {
        adapter = new LocalDocumentStorageAdapter(
                STORAGE_DIR.toString(), JDBC);
    }

    // ── store ────────────────────────────────────────────────────────────────

    @Test
    void store_insereDocumentoComStatusProcessing() {
        String id = storeTexto("teste.pdf", "application/pdf");

        String status = JDBC.queryForObject(
                "SELECT processing_status FROM documents WHERE id = ?",
                String.class, id);
        assertThat(status).isEqualTo("PROCESSING");
    }

    @Test
    void store_insereDocumentoStatusActive() {
        String id = storeTexto("teste.pdf", "application/pdf");

        String status = JDBC.queryForObject(
                "SELECT status FROM documents WHERE id = ?",
                String.class, id);
        assertThat(status).isEqualTo("ACTIVE");
    }

    @Test
    void store_insereComVersao1() {
        String id = storeTexto("relatorio.pdf", "application/pdf");

        Integer version = JDBC.queryForObject(
                "SELECT version_number FROM documents WHERE id = ?",
                Integer.class, id);
        assertThat(version).isEqualTo(1);
    }

    // ── findById ─────────────────────────────────────────────────────────────

    @Test
    void findById_retornaDocumentoArmazenado() {
        String id = storeTexto("relatorio.csv", "text/csv");

        Optional<Document> result = adapter.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(id);
        assertThat(result.get().fileName()).isEqualTo("relatorio.csv");
        assertThat(result.get().processingStatus()).isEqualTo("PROCESSING");
    }

    @Test
    void findById_retornaVazioParaIdInexistente() {
        Optional<Document> result = adapter.findById("id-que-nao-existe");
        assertThat(result).isEmpty();
    }

    // ── findByNamespace ──────────────────────────────────────────────────────

    @Test
    void findByNamespace_retornaApenasDocumentosDoNamespace() {
        storeTexto("a.pdf", "application/pdf");
        storeTexto("b.pdf", "application/pdf");

        List<Document> docs = adapter.findByNamespace(Namespace.DEFAULT_ID);

        assertThat(docs).hasSize(2);
        assertThat(docs).allMatch(d -> Namespace.DEFAULT_ID.equals(d.namespaceId()));
    }

    @Test
    void findByNamespace_naoRetornaDocumentosDeletados() {
        String id = storeTexto("deletado.pdf", "application/pdf");
        adapter.delete(id);

        List<Document> docs = adapter.findByNamespace(Namespace.DEFAULT_ID);
        assertThat(docs).noneMatch(d -> d.id().equals(id));
    }

    // ── findActiveByFileName ─────────────────────────────────────────────────

    @Test
    void findActiveByFileName_retornaDocumentoAtivo() {
        storeTexto("manual.pdf", "application/pdf");

        Optional<Document> result = adapter.findActiveByFileName("manual.pdf", Namespace.DEFAULT_ID);

        assertThat(result).isPresent();
        assertThat(result.get().fileName()).isEqualTo("manual.pdf");
    }

    @Test
    void findActiveByFileName_retornaVazioAposDelete() {
        String id = storeTexto("manual.pdf", "application/pdf");
        adapter.delete(id);

        Optional<Document> result = adapter.findActiveByFileName("manual.pdf", Namespace.DEFAULT_ID);
        assertThat(result).isEmpty();
    }

    // ── updateProcessingStatus ───────────────────────────────────────────────

    @Test
    void updateProcessingStatus_paraReady() {
        String id = storeTexto("doc.pdf", "application/pdf");

        adapter.updateProcessingStatus(id, "READY");

        String status = JDBC.queryForObject(
                "SELECT processing_status FROM documents WHERE id = ?",
                String.class, id);
        assertThat(status).isEqualTo("READY");
    }

    @Test
    void updateProcessingStatus_paraFailed() {
        String id = storeTexto("doc.pdf", "application/pdf");

        adapter.updateProcessingStatus(id, "FAILED");

        String status = JDBC.queryForObject(
                "SELECT processing_status FROM documents WHERE id = ?",
                String.class, id);
        assertThat(status).isEqualTo("FAILED");
    }

    // ── supersede ────────────────────────────────────────────────────────────

    @Test
    void supersede_mudaStatusParaSuperseded() {
        String id = storeTexto("v1.pdf", "application/pdf");

        adapter.supersede(id);

        String status = JDBC.queryForObject(
                "SELECT status FROM documents WHERE id = ?",
                String.class, id);
        assertThat(status).isEqualTo("SUPERSEDED");
    }

    @Test
    void supersede_documentoNaoAparececeEmFindByNamespace() {
        String id = storeTexto("v1.pdf", "application/pdf");
        adapter.supersede(id);

        List<Document> docs = adapter.findByNamespace(Namespace.DEFAULT_ID);
        assertThat(docs).noneMatch(d -> d.id().equals(id));
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_softDeleteAlteraStatusParaDeleted() {
        String id = storeTexto("remover.pdf", "application/pdf");

        adapter.delete(id);

        String status = JDBC.queryForObject(
                "SELECT status FROM documents WHERE id = ?",
                String.class, id);
        assertThat(status).isEqualTo("DELETED");
    }

    @Test
    void delete_registroAindaExisteNoBanco() {
        String id = storeTexto("remover.pdf", "application/pdf");
        adapter.delete(id);

        // Soft delete — row ainda existe
        Integer count = JDBC.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE id = ?", Integer.class, id);
        assertThat(count).isEqualTo(1);
    }

    // ── retrieve ─────────────────────────────────────────────────────────────

    @Test
    void retrieve_leBytesGravadosPorStore() throws Exception {
        String conteudo = "Conteúdo do documento para teste de retrieve";
        byte[] bytes = conteudo.getBytes(StandardCharsets.UTF_8);
        String id = adapter.store(
                "doc.pdf", "application/pdf", bytes.length,
                new ByteArrayInputStream(bytes),
                Namespace.DEFAULT_ID, 1, null);

        try (InputStream is = adapter.retrieve(id)) {
            String lido = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(lido).isEqualTo(conteudo);
        }
    }

    @Test
    void retrieve_lancaExcecaoParaIdInexistente() {
        assertThatThrownBy(() -> adapter.retrieve("nao-existe"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("não encontrado");
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private String storeTexto(String fileName, String mimeType) {
        byte[] bytes = ("conteúdo de " + fileName).getBytes(StandardCharsets.UTF_8);
        return adapter.store(
                fileName, mimeType, bytes.length,
                new ByteArrayInputStream(bytes),
                Namespace.DEFAULT_ID, 1, null);
    }
}
