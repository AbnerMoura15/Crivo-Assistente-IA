-- Migration V7: Histórico de chat persistido (#13)
-- Cada sessão agrupa mensagens de um mesmo usuário

CREATE TABLE chat_sessions (
    id              VARCHAR(36)  PRIMARY KEY,
    username        VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_message_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE chat_messages (
    id         VARCHAR(36)  PRIMARY KEY,
    session_id VARCHAR(36)  NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role       VARCHAR(20)  NOT NULL CHECK (role IN ('user', 'assistant')),
    content    TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_sessions_username ON chat_sessions (username);
CREATE INDEX idx_chat_sessions_updated  ON chat_sessions (last_message_at DESC);
CREATE INDEX idx_chat_messages_session  ON chat_messages (session_id, created_at);
