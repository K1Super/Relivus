#!/usr/bin/env bash
# Relivus 元库备份脚本（df_mask_mapping / df_task / df_task_log / df_audit_log）
# 按 RELIVUS_META_URL 自动识别 PostgreSQL / MySQL，本机 127.0.0.1 默认实例。
set -euo pipefail
TS=$(date +%Y%m%d-%H%M%S)
mkdir -p backups

if [ ! -f /etc/relivus/relivus.env ]; then
  echo "Missing /etc/relivus/relivus.env" >&2
  exit 1
fi
set -a
# shellcheck disable=SC1091
source /etc/relivus/relivus.env
set +a

DB_NAME="${RELIVUS_META_URL##*/}"   # jdbc:postgresql://127.0.0.1:5432/relivus_meta -> relivus_meta
TABLES="df_mask_mapping df_task df_task_log df_audit_log"

case "$RELIVUS_META_URL" in
  jdbc:postgresql:*)
    PGPASSWORD="$RELIVUS_META_PASSWORD" pg_dump -h 127.0.0.1 -U "$RELIVUS_META_USER" \
      -t df_mask_mapping -t df_task -t df_task_log -t df_audit_log \
      "$DB_NAME" > "backups/relivus_meta_pg_$TS.sql"
    echo "Backup: backups/relivus_meta_pg_$TS.sql"
    ;;
  jdbc:mysql:*)
    mysqldump -h 127.0.0.1 -u "$RELIVUS_META_USER" -p"$RELIVUS_META_PASSWORD" \
      $TABLES > "backups/relivus_meta_mysql_$TS.sql"
    echo "Backup: backups/relivus_meta_mysql_$TS.sql"
    ;;
  *)
    echo "Unsupported RELIVUS_META_URL: $RELIVUS_META_URL" >&2
    exit 1
    ;;
esac