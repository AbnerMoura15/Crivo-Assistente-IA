-- V15 — Configura limite dinâmico de mensagens no histórico do chat (#16-hist)
-- Adiciona a chave chat.max-history-messages à tabela rag_config com valor padrão 6.
INSERT INTO rag_config (key, value, updated_at)
VALUES ('chat.max-history-messages', '6', NOW())
ON CONFLICT (key) DO NOTHING;
