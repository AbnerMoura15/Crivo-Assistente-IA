package com.ragbot.integration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que todas as migrações Flyway (V1–V15) foram aplicadas com sucesso
 * e que o schema resultante contém as tabelas e colunas esperadas.
 */
class FlywayMigrationTest extends AbstractIntegrationTest {

    // ── Contagem e sucesso das migrations ────────────────────────────────────

    @Test
    void todasAsMigracoesForamAplicadas() {
        Integer total = JDBC.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                Integer.class);
        assertThat(total).isEqualTo(15);
    }

    @Test
    void nenhumaMigracaoFalhou() {
        Integer falhas = JDBC.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false",
                Integer.class);
        assertThat(falhas).isZero();
    }

    // ── Tabelas principais ───────────────────────────────────────────────────

    @Test
    void tabelaDocumentsExiste() {
        Integer count = JDBC.queryForObject("SELECT COUNT(*) FROM documents", Integer.class);
        assertThat(count).isNotNull();
    }

    @Test
    void tabelaChunksExiste() {
        Integer count = JDBC.queryForObject("SELECT COUNT(*) FROM chunks", Integer.class);
        assertThat(count).isNotNull();
    }

    @Test
    void tabelaChatSessionsExiste() {
        Integer count = JDBC.queryForObject("SELECT COUNT(*) FROM chat_sessions", Integer.class);
        assertThat(count).isNotNull();
    }

    @Test
    void tabelaChatMessagesExiste() {
        Integer count = JDBC.queryForObject("SELECT COUNT(*) FROM chat_messages", Integer.class);
        assertThat(count).isNotNull();
    }

    @Test
    void tabelaNamespacesExiste() {
        Integer count = JDBC.queryForObject("SELECT COUNT(*) FROM namespaces", Integer.class);
        assertThat(count).isNotNull();
    }

    @Test
    void tabelaRagConfigExiste() {
        Integer count = JDBC.queryForObject("SELECT COUNT(*) FROM rag_config", Integer.class);
        assertThat(count).isNotNull();
    }

    // ── Dados semeados pelas migrations ─────────────────────────────────────

    @Test
    void namespacesPadraoFoiCriado() {
        Integer count = JDBC.queryForObject(
                "SELECT COUNT(*) FROM namespaces WHERE id = '00000000-0000-0000-0000-000000000010'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void ragConfigTemValoresPadrao() {
        // V13 insere os padrões de config; V15 insere max-history-messages
        Integer count = JDBC.queryForObject(
                "SELECT COUNT(*) FROM rag_config", Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    void ragConfigMaxHistoryMessagesPresente() {
        Integer count = JDBC.queryForObject(
                "SELECT COUNT(*) FROM rag_config WHERE key = 'chat.max-history-messages'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // ── Colunas críticas (adicionadas em migrations posteriores) ─────────────

    @Test
    void documentsTem_processingStatus() {
        // Coluna adicionada em V14
        JDBC.queryForObject(
                "SELECT processing_status FROM documents WHERE 1=0", String.class);
        // Se a coluna não existir, lança exceção e o teste falha
    }

    @Test
    void documentsTem_namespaceId() {
        // Coluna adicionada em V11
        JDBC.queryForObject(
                "SELECT namespace_id FROM documents WHERE 1=0", String.class);
    }

    @Test
    void documentsTem_versionNumber() {
        // Coluna adicionada em V12
        JDBC.queryForObject(
                "SELECT version_number FROM documents WHERE 1=0", Integer.class);
    }

    @Test
    void extensaoPgvectorAtiva() {
        // V1 executa CREATE EXTENSION IF NOT EXISTS vector
        Integer count = JDBC.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
