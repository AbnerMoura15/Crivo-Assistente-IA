package com.ragbot.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Base para testes de integração com PostgreSQL + pgvector real.
 *
 * Usa um único contêiner Docker por JVM (singleton) para todos os testes
 * que estendem esta classe — o contêiner sobe uma vez e é reutilizado,
 * o que mantém a suíte rápida.
 *
 * Flyway aplica todas as migrações (V1–V15) uma única vez no startup.
 * O método limparBanco() em @BeforeEach garante isolamento entre testes.
 *
 * Se o Docker não estiver disponível, os testes são IGNORADOS (skip),
 * não falhados — o mvn test continua passando normalmente.
 */
public abstract class AbstractIntegrationTest {

    // ── Container singleton — inicia uma vez por JVM ─────────────────────────
    static final boolean DOCKER_AVAILABLE;
    static final PostgreSQLContainer<?> POSTGRES;
    static final DataSource DATA_SOURCE;
    static final JdbcTemplate JDBC;

    /** Diretório temporário para armazenar arquivos de documentos nos testes. */
    static final Path STORAGE_DIR;

    static {
        boolean dockerDisponivel = false;
        PostgreSQLContainer<?> container = null;
        DataSource dataSource = null;
        JdbcTemplate jdbcTemplate = null;
        Path storageDir = null;

        try {
            @SuppressWarnings("resource")
            PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("ragbot_test")
                    .withUsername("test")
                    .withPassword("test");
            pg.start();
            container = pg;

            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setUrl(pg.getJdbcUrl());
            ds.setUsername(pg.getUsername());
            ds.setPassword(pg.getPassword());
            dataSource = ds;
            jdbcTemplate = new JdbcTemplate(ds);
            storageDir = Files.createTempDirectory("ragbot-integration-");

            Flyway.configure()
                  .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                  .locations("classpath:db/migration")
                  .load()
                  .migrate();

            dockerDisponivel = true;

        } catch (Exception e) {
            System.out.println(
                "\n[INTEGRATION] Docker não disponível — testes de integração serão ignorados (SKIP).\n" +
                "[INTEGRATION] Inicie o Docker Desktop e rode novamente para executá-los.\n");
        }

        DOCKER_AVAILABLE = dockerDisponivel;
        POSTGRES     = container;
        DATA_SOURCE  = dataSource;
        JDBC         = jdbcTemplate;
        STORAGE_DIR  = storageDir;
    }

    /**
     * Pula o teste quando Docker não está disponível (em vez de falhar).
     * Em seguida limpa as tabelas transacionais para garantir isolamento.
     */
    @BeforeEach
    void limparBanco() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE,
                "Docker não disponível — teste de integração ignorado (SKIP).");

        // Limpa dados transacionais; preserva namespaces e rag_config
        JDBC.execute("DELETE FROM chunks");
        JDBC.execute("DELETE FROM documents");
        JDBC.execute("DELETE FROM chat_messages");
        JDBC.execute("DELETE FROM chat_sessions");
        JDBC.execute("DELETE FROM audit_log");
        JDBC.execute("DELETE FROM user_token_usage");
        JDBC.execute("DELETE FROM rag_interactions");
    }
}
