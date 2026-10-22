package com.ragbot.application.service;

import com.ragbot.application.port.out.TokenUsagePort;
import com.ragbot.domain.exception.TokenLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenUsageService")
class TokenUsageServiceTest {

    @Mock
    TokenUsagePort tokenUsagePort;

    TokenUsageService service;

    private static final long MONTHLY_LIMIT = 1_000L;

    @BeforeEach
    void setUp() {
        service = new TokenUsageService(tokenUsagePort, MONTHLY_LIMIT);
    }

    // ── checkLimit ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("checkLimit não lança quando uso abaixo do limite")
    void checkLimit_belowLimit_doesNotThrow() {
        when(tokenUsagePort.getMonthlyUsage(eq("alice"), anyInt())).thenReturn(500L);

        // deve executar sem exceção
        service.checkLimit("alice");
    }

    @Test
    @DisplayName("checkLimit lança TokenLimitExceededException quando uso igual ao limite")
    void checkLimit_atLimit_throws() {
        when(tokenUsagePort.getMonthlyUsage(eq("alice"), anyInt())).thenReturn(MONTHLY_LIMIT);

        assertThatThrownBy(() -> service.checkLimit("alice"))
                .isInstanceOf(TokenLimitExceededException.class)
                .hasMessageContaining("alice");
    }

    @Test
    @DisplayName("checkLimit lança TokenLimitExceededException quando uso acima do limite")
    void checkLimit_aboveLimit_throws() {
        when(tokenUsagePort.getMonthlyUsage(eq("alice"), anyInt())).thenReturn(MONTHLY_LIMIT + 100);

        assertThatThrownBy(() -> service.checkLimit("alice"))
                .isInstanceOf(TokenLimitExceededException.class);
    }

    // ── record ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("record incrementa tokens para o mês corrente")
    void record_incrementsUsage() {
        int expectedYearMonth = LocalDate.now().getYear() * 100 + LocalDate.now().getMonthValue();

        service.record("bob", "Qual o prazo?", "O prazo é amanhã.");

        verify(tokenUsagePort).incrementUsage(eq("bob"), eq(expectedYearMonth), longThat(t -> t > 0));
    }

    @Test
    @DisplayName("record não propaga exceção (fire-and-forget)")
    void record_doesNotPropagateException() {
        doThrow(new RuntimeException("DB indisponível"))
                .when(tokenUsagePort).incrementUsage(anyString(), anyInt(), anyLong());

        // não deve lançar exceção
        service.record("bob", "Pergunta", "Resposta");
    }

    // ── getCurrentMonthUsage ─────────────────────────────────────────────────

    @Test
    @DisplayName("getCurrentMonthUsage retorna uso do mês corrente")
    void getCurrentMonthUsage_returnsCurrentMonth() {
        int expectedYearMonth = LocalDate.now().getYear() * 100 + LocalDate.now().getMonthValue();
        when(tokenUsagePort.getMonthlyUsage("carol", expectedYearMonth)).thenReturn(250L);

        long result = service.getCurrentMonthUsage("carol");

        assertThat(result).isEqualTo(250L);
    }

    // ── getMonthlyLimit ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getMonthlyLimit retorna o limite configurado")
    void getMonthlyLimit_returnsConfiguredLimit() {
        assertThat(service.getMonthlyLimit()).isEqualTo(MONTHLY_LIMIT);
    }
}
