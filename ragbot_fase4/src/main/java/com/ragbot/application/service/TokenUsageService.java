package com.ragbot.application.service;

import com.ragbot.application.port.out.TokenUsagePort;
import com.ragbot.domain.exception.TokenLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Serviço #4 — Controle de tokens por usuário/mês.
 *
 * Tokens são estimados por heurística (palavras * 1.3), suficiente para
 * controle de quota sem custo adicional de API.
 *
 * Limite padrão: 100 000 tokens/mês por usuário (configurável via
 * ragbot.tokens.monthly-limit).
 *
 * Regras:
 * - checkLimit()  → lança TokenLimitExceededException se quota esgotada
 * - record()      → fire-and-forget, nunca propaga exceção
 */
@Service
public class TokenUsageService {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageService.class);

    private final TokenUsagePort port;
    private final long monthlyLimit;

    public TokenUsageService(
            TokenUsagePort port,
            @Value("${ragbot.tokens.monthly-limit:100000}") long monthlyLimit) {
        this.port = port;
        this.monthlyLimit = monthlyLimit;
    }

    /**
     * Verifica se o usuário tem tokens disponíveis no mês corrente.
     * Lança TokenLimitExceededException (→ HTTP 429) se o limite foi atingido.
     */
    public void checkLimit(String username) {
        long used = port.getMonthlyUsage(username, currentYearMonth());
        if (used >= monthlyLimit) {
            throw new TokenLimitExceededException(username, used, monthlyLimit);
        }
    }

    /**
     * Registra tokens consumidos na interação.
     * Fire-and-forget: falha silenciosa para não impactar o fluxo principal.
     */
    public void record(String username, String question, String answer) {
        try {
            long tokens = estimate(question) + estimate(answer);
            port.incrementUsage(username, currentYearMonth(), tokens);
            log.debug("Tokens registrados: {} (+{}) para '{}'",
                    tokens, tokens, username);
        } catch (Exception e) {
            log.error("Falha ao registrar tokens para '{}': {}", username, e.getMessage());
        }
    }

    /** Uso atual do usuário no mês corrente. */
    public long getCurrentMonthUsage(String username) {
        return port.getMonthlyUsage(username, currentYearMonth());
    }

    public long getMonthlyLimit() {
        return monthlyLimit;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Estimativa: ~1.3 tokens por palavra (heurística para português). */
    private long estimate(String text) {
        if (text == null || text.isBlank()) return 0;
        return (long) (text.split("\\s+").length * 1.3);
    }

    /** Mês no formato YYYYMM (ex: 202605). */
    private int currentYearMonth() {
        LocalDate now = LocalDate.now();
        return now.getYear() * 100 + now.getMonthValue();
    }
}
