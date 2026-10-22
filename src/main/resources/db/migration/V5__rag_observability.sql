-- Migration V5: Tabela de observabilidade RAG (#7, #8, #9)
-- Registra cada interação: pergunta, resposta, classificação e feedback

CREATE TABLE rag_interactions (
    id               VARCHAR(36)  PRIMARY KEY,
    username         VARCHAR(100) NOT NULL,
    question         TEXT         NOT NULL,
    answer           TEXT         NOT NULL,
    classification   VARCHAR(20)  NOT NULL CHECK (classification IN ('ANSWERED', 'NOT_FOUND')),
    source_documents JSONB        NOT NULL DEFAULT '[]',
    chunks_retrieved INTEGER      NOT NULL DEFAULT 0,
    feedback_positive BOOLEAN,       -- NULL = sem feedback, TRUE = 👍, FALSE = 👎
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Índices para o dashboard de qualidade
CREATE INDEX idx_rag_classification ON rag_interactions (classification);
CREATE INDEX idx_rag_feedback       ON rag_interactions (feedback_positive) WHERE feedback_positive IS NOT NULL;
CREATE INDEX idx_rag_username       ON rag_interactions (username);
CREATE INDEX idx_rag_created_at     ON rag_interactions (created_at DESC);
