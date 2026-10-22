package com.ragbot.application.port.out;

/**
 * Port de saída para persistência do uso de tokens por usuário.
 */
public interface TokenUsagePort {

    /** Retorna tokens usados no mês (yearMonth = YYYYMM). */
    long getMonthlyUsage(String username, int yearMonth);

    /** Incrementa atomicamente o uso de tokens (upsert). */
    void incrementUsage(String username, int yearMonth, long tokens);
}
