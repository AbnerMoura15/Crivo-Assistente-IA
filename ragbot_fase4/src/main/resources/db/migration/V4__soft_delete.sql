-- Migration V4: Soft Delete para documentos e chunks (#29)
-- Adiciona coluna status para deletar logicamente sem perder dados
--
-- Status possíveis:
--   documents: ACTIVE | DELETED
--   chunks:    ACTIVE | DELETED
--
-- !! IMPORTANTE: ao buscar chunks no pgvector, SEMPRE filtrar WHERE status = 'ACTIVE' !!

-- Adiciona status na tabela de documentos
ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'DELETED'));

-- Todos os documentos existentes continuam ativos
UPDATE documents SET status = 'ACTIVE' WHERE status IS NULL;

-- Adiciona status na tabela de chunks
ALTER TABLE chunks
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'DELETED'));

-- Todos os chunks existentes continuam ativos
UPDATE chunks SET status = 'ACTIVE' WHERE status IS NULL;

-- Índices para filtrar por status eficientemente
CREATE INDEX IF NOT EXISTS idx_documents_status ON documents (status);
CREATE INDEX IF NOT EXISTS idx_chunks_status     ON chunks (status);

-- Índice composto: busca vetorial filtrando por status (caso mais comum)
CREATE INDEX IF NOT EXISTS idx_chunks_document_status
    ON chunks (document_id, status);
