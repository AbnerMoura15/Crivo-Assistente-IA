package com.ragbot.config.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * TDD #20 — RagBotHealthIndicator.
 *
 * Verifica que o health check customizado:
 * - Reporta UP quando banco responde e contagem de docs é acessível
 * - Reporta DOWN quando banco falha
 * - Inclui métricas relevantes no detalhe (docs indexados, chunks)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RagBotHealthIndicator")
class RagBotHealthIndicatorTest {

    @Mock
    private JdbcTemplate jdbc;

    private RagBotHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new RagBotHealthIndicator(jdbc);
    }

    @Test
    @DisplayName("deve retornar UP quando banco está acessível")
    void shouldReturnUpWhenDatabaseIsAccessible() {
        when(jdbc.queryForObject("SELECT COUNT(*) FROM documents WHERE status = 'ACTIVE'",
                Long.class)).thenReturn(5L);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM chunks WHERE status = 'ACTIVE'",
                Long.class)).thenReturn(42L);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("deve incluir contagem de documentos e chunks nos detalhes")
    void shouldIncludeDocumentAndChunkCountInDetails() {
        when(jdbc.queryForObject("SELECT COUNT(*) FROM documents WHERE status = 'ACTIVE'",
                Long.class)).thenReturn(3L);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM chunks WHERE status = 'ACTIVE'",
                Long.class)).thenReturn(27L);

        Health health = indicator.health();

        assertThat(health.getDetails())
                .containsKey("documentsIndexed")
                .containsKey("chunksIndexed");
        assertThat(health.getDetails().get("documentsIndexed")).isEqualTo(3L);
        assertThat(health.getDetails().get("chunksIndexed")).isEqualTo(27L);
    }

    @Test
    @DisplayName("deve retornar DOWN quando banco não responde")
    void shouldReturnDownWhenDatabaseFails() {
        when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }

    @Test
    @DisplayName("deve indicar sistema sem documentos como UP com aviso")
    void shouldReturnUpWithWarningWhenNoDocuments() {
        when(jdbc.queryForObject("SELECT COUNT(*) FROM documents WHERE status = 'ACTIVE'",
                Long.class)).thenReturn(0L);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM chunks WHERE status = 'ACTIVE'",
                Long.class)).thenReturn(0L);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("warning");
    }
}
