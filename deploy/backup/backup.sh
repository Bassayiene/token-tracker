#!/bin/sh
# Dumps the database into /backups/daily, verifies the dump, keeps weekly/monthly copies and rotates old files.
# Connection comes from the PG* environment variables. Run manually with:
#   docker compose -f docker-compose.prod.yml exec backup sh /scripts/backup.sh
set -eu

BACKUP_DIR=/backups
KEEP_DAILY=${BACKUP_KEEP_DAILY:-14}
KEEP_WEEKLY=${BACKUP_KEEP_WEEKLY:-8}
KEEP_MONTHLY=${BACKUP_KEEP_MONTHLY:-12}

log() { echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) [backup] $*"; }

# Keeps the $2 newest dumps of directory $1. Count-based, so old backups survive if new ones stop being made.
rotate() {
  ls -1t "$1"/*.dump 2>/dev/null | tail -n +"$(($2 + 1))" | while read -r old; do
    rm -f "$old"
    log "removed $old"
  done
}

mkdir -p "$BACKUP_DIR/daily" "$BACKUP_DIR/weekly" "$BACKUP_DIR/monthly"
name="${PGDATABASE}-$(date -u +%Y-%m-%dT%H%MZ).dump"
tmp="$BACKUP_DIR/daily/.$name.partial"
trap 'rm -f "$tmp"' EXIT

log "dumping $PGDATABASE from $PGHOST"
# Custom format: compressed, and restorable table by table with pg_restore.
pg_dump --format=custom --compress=6 --no-owner --file="$tmp"
# A dump that pg_restore cannot read is worse than no dump: fail loudly instead of keeping it.
pg_restore --list "$tmp" > /dev/null
mv "$tmp" "$BACKUP_DIR/daily/$name"
log "written daily/$name ($(du -h "$BACKUP_DIR/daily/$name" | cut -f1))"

# Hard links: the weekly/monthly copies take no extra space while the daily file exists.
# Falls back to a plain copy on filesystems without hard links.
keep_copy() {
  ln -f "$BACKUP_DIR/daily/$name" "$BACKUP_DIR/$1/$name" 2>/dev/null || cp "$BACKUP_DIR/daily/$name" "$BACKUP_DIR/$1/$name"
  log "kept as $1"
}
if [ "${BACKUP_FORCE_WEEKLY:-}" = 1 ] || [ "$(date -u +%u)" = 7 ]; then
  keep_copy weekly
fi
if [ "${BACKUP_FORCE_MONTHLY:-}" = 1 ] || [ "$(date -u +%d)" = 01 ]; then
  keep_copy monthly
fi

rotate "$BACKUP_DIR/daily" "$KEEP_DAILY"
rotate "$BACKUP_DIR/weekly" "$KEEP_WEEKLY"
rotate "$BACKUP_DIR/monthly" "$KEEP_MONTHLY"

date -u +%Y-%m-%dT%H:%M:%SZ > "$BACKUP_DIR/last-success"
log "done"
