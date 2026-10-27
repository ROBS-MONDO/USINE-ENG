#!/usr/bin/env bash
# Dal tuo computer (Git Bash, WSL, Mac o Linux), nella cartella hometech/:
#   bash deploy/deploy.sh root@IP_DEL_VPS
# Usa solo ssh e tar, nessun rsync necessario in locale.
set -euo pipefail
TARGET="${1:?uso: deploy.sh utente@host}"

# Sito: sostituisce il contenuto della cartella pubblica
tar -C web -cf - . | ssh "$TARGET" 'rm -rf /var/www/hometech/* && tar -C /var/www/hometech -xf - && command -v restorecon >/dev/null && restorecon -R /var/www/hometech || true'

# API: copia il codice (senza node_modules) e riavvia il servizio
tar --exclude=node_modules -C api -cf - . | ssh "$TARGET" 'mkdir -p /opt/hometech/api && tar -C /opt/hometech/api -xf -'
ssh "$TARGET" 'cd /opt/hometech/api && npm ci --omit=dev && chown -R hometech:hometech /opt/hometech && systemctl restart hometech-api && systemctl is-active hometech-api'
