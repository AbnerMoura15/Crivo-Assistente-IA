-- Ajusta o banco para embeddings do Google Gemini (3072 dimensoes).
-- Limpa chunks antigos para evitar conflito de dimensao entre modelos.

DROP INDEX IF EXISTS idx_chunks_embedding;

TRUNCATE TABLE chunks;

ALTER TABLE chunks
    ALTER COLUMN embedding TYPE vector(3072);

-- Observacao:
-- ivfflat nao suporta 3072 dimensoes no pgvector atual.
-- Mantemos sem indice vetorial por enquanto para garantir compatibilidade.
