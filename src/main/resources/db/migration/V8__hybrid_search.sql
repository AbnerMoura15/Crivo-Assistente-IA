-- Fase 8 / #27 — Busca Híbrida: adiciona coluna tsvector para BM25 full-text search
--
-- GENERATED ALWAYS AS STORED: PostgreSQL 16 computa e mantém o tsvector automaticamente
-- a cada INSERT/UPDATE, sem necessidade de trigger manual.
-- Índice GIN permite buscas full-text em O(log n).

ALTER TABLE chunks
    ADD COLUMN IF NOT EXISTS content_tsvector tsvector
    GENERATED ALWAYS AS (to_tsvector('portuguese', content)) STORED;

CREATE INDEX IF NOT EXISTS idx_chunks_tsvector
    ON chunks USING GIN (content_tsvector);
