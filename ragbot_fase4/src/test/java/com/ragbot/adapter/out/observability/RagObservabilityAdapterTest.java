package com.ragbot.adapter.out.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RagObservabilityAdapter — feedback restrito ao dono")
class RagObservabilityAdapterTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RagObservabilityAdapter adapter = new RagObservabilityAdapter(jdbc, new ObjectMapper());

    @Test
    @DisplayName("UPDATE filtra por id E username (usuário não altera feedback de outro)")
    void feedbackUpdateIsScopedToOwner() {
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(0);

        boolean updated = adapter.updateFeedback("interacao-da-alice", "bob", true);

        assertThat(updated).isFalse();
        verify(jdbc).update(
                contains("WHERE id = ? AND username = ?"),
                eq(true), eq("interacao-da-alice"), eq("bob"));
    }

    @Test
    @DisplayName("dono atualiza o próprio feedback")
    void ownerCanUpdate() {
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(1);

        assertThat(adapter.updateFeedback("interacao-da-alice", "alice", false)).isTrue();
    }
}
