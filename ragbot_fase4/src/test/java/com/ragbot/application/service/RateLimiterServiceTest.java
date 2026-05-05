package com.ragbot.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD #5 — RateLimiterService.
 *
 * Contrato:
 * - Usuário autenticado: até MAX_PER_MINUTE requisições/min e MAX_PER_DAY/dia
 * - IP anônimo: limite menor (proteção extra contra bots)
 * - Após atingir o limite, tryAcquire retorna false
 * - Após o janela de tempo, o limite é resetado
 */
@DisplayName("RateLimiterService")
class RateLimiterServiceTest {

    // Configuração de teste: 3 req/min, 10 req/dia para facilitar asserções
    private RateLimiterService rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiterService(3, 10, 2, 5);
        // args: userPerMin, userPerDay, anonPerMin, anonPerDay
    }

    // ── Usuário autenticado ───────────────────────────────────────────────────

    @Test
    @DisplayName("primeiras requisições dentro do limite devem ser permitidas")
    void firstRequestsWithinLimitShouldBeAllowed() {
        assertThat(rateLimiter.tryAcquire("user:joao")).isTrue();
        assertThat(rateLimiter.tryAcquire("user:joao")).isTrue();
        assertThat(rateLimiter.tryAcquire("user:joao")).isTrue();
    }

    @Test
    @DisplayName("requisição acima do limite por minuto deve ser bloqueada")
    void requestAbovePerMinuteLimitShouldBeBlocked() {
        rateLimiter.tryAcquire("user:joao"); // 1
        rateLimiter.tryAcquire("user:joao"); // 2
        rateLimiter.tryAcquire("user:joao"); // 3 - limite

        assertThat(rateLimiter.tryAcquire("user:joao")).isFalse(); // 4 - bloqueado
    }

    @Test
    @DisplayName("usuários diferentes não devem compartilhar limite")
    void differentUsersShouldHaveSeparateLimits() {
        rateLimiter.tryAcquire("user:joao");
        rateLimiter.tryAcquire("user:joao");
        rateLimiter.tryAcquire("user:joao");

        // Maria ainda não usou nada — deve passar
        assertThat(rateLimiter.tryAcquire("user:maria")).isTrue();
    }

    // ── IP anônimo ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("IP anônimo tem limite menor que usuário autenticado")
    void anonymousIpHasSmallerLimit() {
        assertThat(rateLimiter.tryAcquire("ip:192.168.1.1")).isTrue();
        assertThat(rateLimiter.tryAcquire("ip:192.168.1.1")).isTrue();
        // Limite anon é 2/min — terceira deve bloquear
        assertThat(rateLimiter.tryAcquire("ip:192.168.1.1")).isFalse();
    }

    // ── Informações de status ─────────────────────────────────────────────────

    @Test
    @DisplayName("getMinuteCount deve retornar contagem atual")
    void getMinuteCountShouldReturnCurrentCount() {
        rateLimiter.tryAcquire("user:joao");
        rateLimiter.tryAcquire("user:joao");

        assertThat(rateLimiter.getMinuteCount("user:joao")).isEqualTo(2);
    }

    @Test
    @DisplayName("getDayCount deve acumular contagem do dia")
    void getDayCountShouldAccumulateDailyCount() {
        rateLimiter.tryAcquire("user:joao");
        rateLimiter.tryAcquire("user:joao");

        assertThat(rateLimiter.getDayCount("user:joao")).isEqualTo(2);
    }

    @Test
    @DisplayName("chave desconhecida deve retornar zero")
    void unknownKeyShouldReturnZeroCount() {
        assertThat(rateLimiter.getMinuteCount("user:ninguem")).isEqualTo(0);
        assertThat(rateLimiter.getDayCount("user:ninguem")).isEqualTo(0);
    }
}
