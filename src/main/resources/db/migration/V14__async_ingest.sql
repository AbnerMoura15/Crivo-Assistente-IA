-- #22 Ingestão assíncrona: coluna de status de processamento em documents
ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS processing_status VARCHAR(20) NOT NULL DEFAULT 'READY';

-- Documentos existentes já estão totalmente processados
UPDATE documents SET processing_status = 'READY' WHERE processing_status IS NULL;

ALTER TABLE documents
    ADD CONSTRAINT chk_processing_status
        CHECK (processing_status IN ('PROCESSING', 'READY', 'FAILED'));
