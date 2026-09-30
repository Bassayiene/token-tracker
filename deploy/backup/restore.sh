#!/bin/sh
# Replaces the whole database with the content of a dump. Stop the backend first (see DEPLOY.md):
#   docker compose -f docker-compose.prod.yml stop backend
#   docker compose -f docker-compose.prod.yml exec backup sh /scripts/restore.sh /backups/daily/<file>.dump --yes
#   docker compose -f docker-compose.prod.yml start backend
set -eu

dump=${1:-}
if [ -z "$dump" ] || [ ! -f "$dump" ]; then
  echo "usage: restore.sh /backups/<daily|weekly|monthly>/<file>.dump --yes"
  echo "available dumps (newest first):"
  ls -1t /backups/*/*.dump 2>/dev/null | head -20
  exit 1
fi
if [ "${2:-}" != "--yes" ]; then
  echo "This REPLACES every table of database '$PGDATABASE' with the content of $dump."
  echo "Stop the backend first, then run again with --yes as the last argument."
  exit 1
fi

pg_restore --list "$dump" > /dev/null
echo "restoring $dump into $PGDATABASE ..."
# --clean --if-exists drops each object before recreating it; a single transaction means all or nothing.
pg_restore --clean --if-exists --no-owner --single-transaction --exit-on-error --dbname="$PGDATABASE" "$dump"
echo "restore done. Start the backend again."
