-- Fase 8 / #4 — Controle de tokens por usuário/mês
--
-- year_month: formato YYYYMM (ex: 202605 = maio de 2026)
-- tokens_used: estimativa acumulada de tokens consumidos no mês
-- ON CONFLICT DO UPDATE permite upsert atômico sem race condition

CREATE TABLE IF NOT EXISTS user_token_usage (
    username     VARCHAR(255) NOT NULL,
    year_month   INTEGER      NOT NULL,
    tokens_used  BIGINT       NOT NULL DEFAULT 0,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (username, year_month)
);

CREATE INDEX IF NOT EXISTS idx_token_usage_username
    ON user_token_usage (username);
