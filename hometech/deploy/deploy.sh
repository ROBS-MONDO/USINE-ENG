#!/usr/bin/env bash
# Dal tuo computer, nella cartella hometech/:  bash deploy/deploy.sh utente@IP_DEL_VPS
set -euo pipefail
TARGET="${1:?uso: deploy.sh utente@host}"

rsync -az --delete web/ "$TARGET:/var/www/hometech/"
rsync -az --delete --exclude node_modules api/ "$TARGET:/opt/hometech/api/"
ssh "$TARGET" 'cd /opt/hometech/api && npm ci --omit=dev && chown -R hometech:hometech /opt/hometech && systemctl restart hometech-api && systemctl is-active hometech-api'
