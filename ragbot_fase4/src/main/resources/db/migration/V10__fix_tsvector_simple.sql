-- V10: Corrige content_tsvector para usar 'simple' em vez de 'portuguese'
-- 'simple' é universalmente disponível em qualquer instalação PostgreSQL.
-- 'portuguese' requer o pacote de língua instalado no servidor, que pode não existir.

-- Remove a coluna antiga (se existir com configuração 'portuguese')
ALTER TABLE chunks DROP COLUMN IF EXISTS content_tsvector;

-- Recria com 'simple' — funciona em qualquer PostgreSQL sem dependências externas
ALTER TABLE chunks
    ADD COLUMN IF NOT EXISTS content_tsvector tsvector
    GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED;

-- Recria o índice GIN
DROP INDEX IF EXISTS idx_chunks_tsvector;
CREATE INDEX idx_chunks_tsvector ON chunks USING GIN (content_tsvector);
