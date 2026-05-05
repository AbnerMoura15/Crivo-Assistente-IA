-- V11: Namespaces (#33) — bases de conhecimento separadas por projeto/área
--
-- Cada namespace isola um conjunto de documentos e chunksassociados.
-- A busca vetorial é sempre escopada ao namespace ativo.
--
-- Namespace padrão: id fixo para compatibilidade retroativa com documentos
-- existentes que não tinham namespace.

CREATE TABLE namespaces (
    id           VARCHAR(36)  PRIMARY KEY,
    slug         VARCHAR(100) NOT NULL UNIQUE,   -- URL-friendly: "rh", "financeiro"
    display_name VARCHAR(255) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_namespaces_slug ON namespaces (slug);

-- Namespace padrão — todos os documentos antigos pertencem a ele
INSERT INTO namespaces (id, slug, display_name)
VALUES ('00000000-0000-0000-0000-000000000010', 'default', 'Padrão');

-- Adiciona namespace_id em documents; documentos existentes vão para 'default'
ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS namespace_id VARCHAR(36)
        NOT NULL DEFAULT '00000000-0000-0000-0000-000000000010'
        REFERENCES namespaces(id);

CREATE INDEX IF NOT EXISTS idx_documents_namespace ON documents (namespace_id);
