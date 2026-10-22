#!/bin/bash
# ── RAGBot — Backup do PostgreSQL (#21) ──────────────────────────────────────
#
# Uso:
#   ./scripts/backup.sh                    # backup imediato
#   0 2 * * * /app/scripts/backup.sh       # cron: todos os dias às 2h
#
# O backup é comprimido com gzip e salvo com timestamp no nome.
# Backups com mais de RETENTION_DAYS dias são apagados automaticamente.

set -euo pipefail

# ── Configuração ──────────────────────────────────────────────────────────────
BACKUP_DIR="${BACKUP_DIR:-/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"       # mantém 7 dias de backup
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/ragbot_${TIMESTAMP}.sql.gz"

# Variáveis do banco (lidas do ambiente)
PGHOST="${PGHOST:-postgres}"
PGPORT="${PGPORT:-5432}"
PGUSER="${POSTGRES_USER:-ragbot}"
PGPASSWORD="${POSTGRES_PASSWORD}"
PGDATABASE="${POSTGRES_DB:-ragbot}"

# ── Execução ──────────────────────────────────────────────────────────────────
echo "[$(date -Iseconds)] Iniciando backup: ${BACKUP_FILE}"

# Cria o diretório de backup se não existir
mkdir -p "${BACKUP_DIR}"

# Executa pg_dump e comprime com gzip
PGPASSWORD="${PGPASSWORD}" pg_dump \
    --host="${PGHOST}" \
    --port="${PGPORT}" \
    --username="${PGUSER}" \
    --dbname="${PGDATABASE}" \
    --format=plain \
    --no-owner \
    --no-acl \
    | gzip > "${BACKUP_FILE}"

# Verifica se o arquivo foi criado com sucesso
if [ -s "${BACKUP_FILE}" ]; then
    SIZE=$(du -sh "${BACKUP_FILE}" | cut -f1)
    echo "[$(date -Iseconds)] Backup concluído: ${BACKUP_FILE} (${SIZE})"
else
    echo "[$(date -Iseconds)] ERRO: Backup falhou ou arquivo vazio"
    rm -f "${BACKUP_FILE}"
    exit 1
fi

# ── Limpeza de backups antigos ────────────────────────────────────────────────
echo "[$(date -Iseconds)] Removendo backups com mais de ${RETENTION_DAYS} dias..."
find "${BACKUP_DIR}" -name "ragbot_*.sql.gz" -mtime "+${RETENTION_DAYS}" -delete

REMAINING=$(find "${BACKUP_DIR}" -name "ragbot_*.sql.gz" | wc -l)
echo "[$(date -Iseconds)] Backups mantidos: ${REMAINING}"
