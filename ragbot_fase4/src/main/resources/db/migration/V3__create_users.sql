-- Migration V3: Criação da tabela de usuários para autenticação e controle de acesso
-- Bloco 1 — #1 Spring Security + JWT + Perfis

CREATE TABLE users (
    id            VARCHAR(36)  PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,          -- BCrypt, NUNCA texto puro
    role          VARCHAR(20)  NOT NULL DEFAULT 'VIEWER' CHECK (role IN ('VIEWER','EDITOR','ADMIN')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_login    TIMESTAMPTZ
);

CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_email    ON users (email);

-- ─────────────────────────────────────────────────────────────────────────────
-- Usuário ADMIN padrão para primeiro acesso
-- Senha: Admin@123 (BCrypt custo 12)
-- !! OBRIGATÓRIO trocar a senha no primeiro login em produção !!
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO users (id, username, email, password_hash, role, active)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin',
    'admin@ragbot.local',
    '$2a$12$/WCz2rD9qXs0kh7gKiVoFe7he4jKdRxcgBBOxnLIBR1mQjXp15Ru2',
    'ADMIN',
    TRUE
);
