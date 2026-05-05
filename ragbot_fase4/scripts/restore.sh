#!/bin/bash
# ── RAGBot — Restore do PostgreSQL (#21) ──────────────────────────────────────
#
# Uso:
#   ./scripts/restore.sh /backups/ragbot_20260504_020001.sql.gz
#
# ATENÇÃO: Este script APAGA o banco atual e restaura o backup.
# Execute apenas em janela de manutenção e com a aplicação parada.

set -euo pipefail

# ── Validações ────────────────────────────────────────────────────────────────
if [ $# -eq 0 ]; then
    echo "Uso: $0 <arquivo_backup.sql.gz>"
    echo ""
    echo "Backups disponíveis:"
    ls -lh "${BACKUP_DIR:-/backups}"/ragbot_*.sql.gz 2>/dev/null || echo "  Nenhum backup encontrado"
    exit 1
fi

BACKUP_FILE="$1"

if [ ! -f "${BACKUP_FILE}" ]; then
    echo "ERRO: Arquivo não encontrado: ${BACKUP_FILE}"
    exit 1
fi

# ── Configuração ──────────────────────────────────────────────────────────────
PGHOST="${PGHOST:-postgres}"
PGPORT="${PGPORT:-5432}"
PGUSER="${POSTGRES_USER:-ragbot}"
PGPASSWORD="${POSTGRES_PASSWORD}"
PGDATABASE="${POSTGRES_DB:-ragbot}"

# ── Confirmação ───────────────────────────────────────────────────────────────
echo "========================================================"
echo "  ATENÇÃO: OPERAÇÃO DESTRUTIVA"
echo "========================================================"
echo "  Banco:   ${PGDATABASE} em ${PGHOST}:${PGPORT}"
echo "  Backup:  ${BACKUP_FILE}"
echo "  Esta operação APAGA o banco atual."
echo "========================================================"
read -r -p "  Digite CONFIRMAR para prosseguir: " CONFIRM

if [ "${CONFIRM}" != "CONFIRMAR" ]; then
    echo "Operação cancelada."
    exit 0
fi

# ── Execução ──────────────────────────────────────────────────────────────────
echo "[$(date -Iseconds)] Iniciando restore..."

# Termina conexões abertas ao banco
PGPASSWORD="${PGPASSWORD}" psql \
    --host="${PGHOST}" --port="${PGPORT}" \
    --username="${PGUSER}" --dbname=postgres \
    -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='${PGDATABASE}' AND pid <> pg_backend_pid();" \
    > /dev/null 2>&1 || true

# Apaga e recria o banco
PGPASSWORD="${PGPASSWORD}" psql \
    --host="${PGHOST}" --port="${PGPORT}" \
    --username="${PGUSER}" --dbname=postgres \
    -c "DROP DATABASE IF EXISTS ${PGDATABASE};"

PGPASSWORD="${PGPASSWORD}" psql \
    --host="${PGHOST}" --port="${PGPORT}" \
    --username="${PGUSER}" --dbname=postgres \
    -c "CREATE DATABASE ${PGDATABASE} OWNER ${PGUSER};"

# Restaura o backup
echo "[$(date -Iseconds)] Restaurando dados..."
gunzip -c "${BACKUP_FILE}" | PGPASSWORD="${PGPASSWORD}" psql \
    --host="${PGHOST}" --port="${PGPORT}" \
    --username="${PGUSER}" --dbname="${PGDATABASE}" \
    --quiet

echo "[$(date -Iseconds)] Restore concluído com sucesso!"
echo "  Reinicie a aplicação: docker compose restart app"
