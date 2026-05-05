package com.ragbot.adapter.out.usage;

import com.ragbot.application.port.out.TokenUsagePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Adapter JDBC para persistência de uso de tokens.
 * Usa INSERT ... ON CONFLICT DO UPDATE para upsert atômico.
 */
@Repository
public class PgTokenUsageAdapter implements TokenUsagePort {

    private final JdbcTemplate jdbc;

    public PgTokenUsageAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long getMonthlyUsage(String username, int yearMonth) {
        Long result = jdbc.queryForObject(
                "SELECT COALESCE(tokens_used, 0) FROM user_token_usage " +
                "WHERE username = ? AND year_month = ?",
                Long.class, username, yearMonth);
        return result != null ? result : 0L;
    }

    @Override
    public void incrementUsage(String username, int yearMonth, long tokens) {
        jdbc.update("""
                INSERT INTO user_token_usage (username, year_month, tokens_used, updated_at)
                VALUES (?, ?, ?, NOW())
                ON CONFLICT (username, year_month) DO UPDATE
                SET tokens_used = user_token_usage.tokens_used + EXCLUDED.tokens_used,
                    updated_at  = NOW()
                """,
                username, yearMonth, tokens);
    }
}
