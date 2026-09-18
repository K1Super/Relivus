#!/usr/bin/env bash
# Relivus 元库恢复脚本：./restore.sh backups/relivus_meta_xxx_TIMESTAMP.sql
set -euo pipefail
FILE=${1:?Usage: restore.sh <backup-file>}

if [ ! -f /etc/relivus/relivus.env ]; then
  echo "Missing /etc/relivus/relivus.env" >&2
  exit 1
fi
set -a
# shellcheck disable=SC1091
source /etc/relivus/relivus.env
set +a

case "$FILE" in
  *mysql*)
    mysql -h 127.0.0.1 -u "$RELIVUS_META_USER" -p"$RELIVUS_META_PASSWORD" \
      relivus_meta < "$FILE"
    ;;
  *pg*)
    PGPASSWORD="$RELIVUS_META_PASSWORD" psql -h 127.0.0.1 -U "$RELIVUS_META_USER" \
      relivus_meta < "$FILE"
    ;;
  *)
    echo "Unknown backup type: $FILE" >&2
    exit 1
    ;;
esac
echo "Restored: $FILE"