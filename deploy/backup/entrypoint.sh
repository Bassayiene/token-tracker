#!/bin/sh
# Schedules backup.sh with busybox crond (UTC) and runs a first backup when none exists yet.
set -eu

echo "${BACKUP_CRON} sh /scripts/backup.sh > /proc/1/fd/1 2>&1" > /etc/crontabs/root
echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) [backup] scheduled: ${BACKUP_CRON} (UTC)"

if [ ! -f /backups/last-success ]; then
  # Give PostgreSQL a moment on first start; a failure here is retried by the next scheduled run.
  sleep 10
  sh /scripts/backup.sh || echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) [backup] initial backup failed"
fi

exec crond -f -l 8
