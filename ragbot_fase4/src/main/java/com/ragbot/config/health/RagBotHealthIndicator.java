package com.ragbot.config.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Health indicator customizado para o RAGBot.
 *
 * Exposto em: GET /actuator/health/ragbot
 * Retorna:
 *   - UP   → banco acessível
 *   - DOWN → banco inacessível (app não serve requisições)
 *
 * Detalhes incluídos:
 *   - documentsIndexed: total de documentos ACTIVE
 *   - chunksIndexed:    total de chunks ACTIVE
 *   - warning:          presente quando não há documentos (sistema vazio)
 *
 * Usado pelo docker-compose healthcheck e pelo load balancer
 * para decidir se a instância está pronta para receber tráfego.
 */
@Component("ragbot")
public class RagBotHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(RagBotHealthIndicator.class);

    private final JdbcTemplate jdbc;

    public RagBotHealthIndicator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Health health() {
        try {
            Long docs   = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM documents WHERE status = 'ACTIVE'", Long.class);
            Long chunks = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM chunks WHERE status = 'ACTIVE'", Long.class);

            long docCount   = docs   != null ? docs   : 0L;
            long chunkCount = chunks != null ? chunks : 0L;

            Health.Builder builder = Health.up()
                    .withDetail("documentsIndexed", docCount)
                    .withDetail("chunksIndexed",    chunkCount);

            if (docCount == 0) {
                builder.withDetail("warning",
                        "Nenhum documento indexado. Faça upload para começar.");
            }

            return builder.build();

        } catch (Exception e) {
            log.error("Health check falhou: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
