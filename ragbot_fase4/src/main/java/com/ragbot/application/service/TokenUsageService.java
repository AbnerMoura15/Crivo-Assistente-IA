package com.ragbot.application.service;

import com.ragbot.application.port.out.TokenUsagePort;
import com.ragbot.domain.exception.TokenLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Serviço #4 — Controle de tokens por usuário/mês.
 *
 * Fase 10 (#25 — Config IA via tela):
 * - O limite mensal agora é lido dinamicamente via RagConfigService,
 *   permitindo que o admin ajuste sem redeploy.
 * - Mantém construtor legado (TokenUsagePort, long) para compatibilidade
 *   com testes que não precisam de RagConfigService.
 *
 * Tokens são estimados por heurística (palavras × 1.3).
 */
@Service
public class TokenUsageService {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageService.class);

    private final TokenUsagePort   port;
    private final RagConfigService ragConfigService;

    /** Construtor principal — usa limite dinâmico do RagConfigService. */
    public TokenUsageService(TokenUsagePort port, RagConfigService ragConfigService) {
        this.port            = port;
        this.ragConfigService = ragConfigService;
    }

    /**
     * Construtor de compatibilidade para testes que fornecem o limite diretamente.
     * Cria um RagConfigService com defaults fixos sem necessidade de DB.
     */
    public TokenUsageService(TokenUsagePort port, long fixedMonthlyLimit) {
        this.port            = port;
        this.ragConfigService = new RagConfigService(null) {
            @Override
            public com.ragbot.domain.entity.RagConfig getConfig() {
                return new com.ragbot.domain.entity.RagConfig(
                        3, 0.10, 80, true, 0.85, fixedMonthlyLimit);
            }
        };
    }

    /**
     * Verifica se o usuário tem tokens disponíveis no mês corrente.
     * Lança TokenLimitExceededException (→ HTTP 429) se o limite foi atingido.
     */
    public void checkLimit(String username) {
        long limit = ragConfigService.getConfig().monthlyTokenLimit();
        long used  = port.getMonthlyUsage(username, currentYearMonth());
        if (used >= limit) {
            throw new TokenLimitExceededException(username, used, limit);
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
            log.debug("Tokens registrados: {} para '{}'", tokens, username);
        } catch (Exception e) {
            log.error("Falha ao registrar tokens para '{}': {}", username, e.getMessage());
        }
    }

    /** Uso atual do usuário no mês corrente. */
    public long getCurrentMonthUsage(String username) {
        return port.getMonthlyUsage(username, currentYearMonth());
    }

    public long getMonthlyLimit() {
        return ragConfigService.getConfig().monthlyTokenLimit();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private long estimate(String text) {
        if (text == null || text.isBlank()) return 0;
        return (long) (text.split("\\s+").length * 1.3);
    }

    private int currentYearMonth() {
        LocalDate now = LocalDate.now();
        return now.getYear() * 100 + now.getMonthValue();
    }
}
