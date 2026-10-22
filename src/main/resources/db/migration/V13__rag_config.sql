-- V13: Configuração dinâmica do RAG (#25)
--
-- Permite que admins ajustem parâmetros do pipeline sem redeploy.
-- Cada linha é um par chave/valor; valores são sempre TEXT e convertidos
-- no serviço conforme o tipo esperado.
--
-- As chaves espelham as propriedades do application.yml para consistência.

CREATE TABLE rag_config (
    key        VARCHAR(100) PRIMARY KEY,
    value      TEXT         NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Seed com os mesmos valores do application.yml
INSERT INTO rag_config (key, value) VALUES
    ('retrieval.top-k',                  '3'),
    ('retrieval.min-score',              '0.10'),
    ('retrieval.short-chunk-length',     '80'),
    ('retrieval.hybrid.enabled',         'true'),
    ('retrieval.hybrid.semantic-weight', '0.85'),
    ('tokens.monthly-limit',             '100000');
