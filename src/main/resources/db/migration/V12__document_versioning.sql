-- V12: Versionamento de documentos (#26)
--
-- Quando um documento com o mesmo nome é enviado para o mesmo namespace,
-- uma nova versão é criada automaticamente. A versão anterior recebe status
-- 'SUPERSEDED' e seus chunks são marcados como 'DELETED'.
--
-- Campos:
--   version_number        → 1 para o primeiro upload; incrementa a cada novo upload
--   original_document_id  → ID da versão 1 (null na própria v1);
--                           permite listar todo o histórico de um arquivo

ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS version_number       INTEGER     NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS original_document_id VARCHAR(36) REFERENCES documents(id);

-- Adiciona 'SUPERSEDED' ao check de status (documentos substituídos por versão mais nova)
ALTER TABLE documents DROP CONSTRAINT IF EXISTS documents_status_check;
ALTER TABLE documents
    ADD CONSTRAINT documents_status_check
        CHECK (status IN ('ACTIVE', 'DELETED', 'SUPERSEDED'));

CREATE INDEX IF NOT EXISTS idx_documents_original ON documents (original_document_id)
    WHERE original_document_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_documents_version ON documents (namespace_id, status, version_number DESC);
