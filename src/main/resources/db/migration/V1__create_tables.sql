-- Habilita a extensão pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- Tabela de metadados dos documentos
CREATE TABLE documents (
    id VARCHAR(36) PRIMARY KEY,
    file_name VARCHAR(512) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    storage_reference VARCHAR(1024) NOT NULL,
    file_size_bytes BIGINT NOT NULL DEFAULT 0,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Tabela de chunks com embeddings vetoriais
-- Dimensão 1536 = text-embedding-3-small da OpenAI/Copilot
CREATE TABLE chunks (
    id VARCHAR(36) PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    embedding vector(1536),
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Índice para busca por similaridade (IVFFLAT - bom custo/benefício)
CREATE INDEX idx_chunks_embedding ON chunks
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- Índice para deletar chunks por documento
CREATE INDEX idx_chunks_document_id ON chunks (document_id);
