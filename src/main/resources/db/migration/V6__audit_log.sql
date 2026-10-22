-- Migration V6: Audit Log (#11)
-- Registra ações críticas para rastreabilidade e segurança

CREATE TABLE audit_log (
    id          VARCHAR(36)  PRIMARY KEY,
    actor       VARCHAR(100) NOT NULL,           -- username de quem executou a ação
    action      VARCHAR(50)  NOT NULL,           -- LOGIN_SUCCESS, DOCUMENT_UPLOAD, etc.
    target_id   VARCHAR(255),                    -- id do recurso afetado (se houver)
    target_type VARCHAR(50),                     -- 'USER', 'DOCUMENT', etc.
    detail      TEXT,                            -- informação adicional livre
    ip_address  VARCHAR(45),                     -- IPv4 ou IPv6
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_actor      ON audit_log (actor);
CREATE INDEX idx_audit_action     ON audit_log (action);
CREATE INDEX idx_audit_created_at ON audit_log (created_at DESC);
