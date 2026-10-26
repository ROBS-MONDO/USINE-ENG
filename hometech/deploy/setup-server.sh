#!/usr/bin/env bash
# Da eseguire UNA volta sul VPS Ubuntu come root:  bash setup-server.sh example.it
set -euo pipefail
DOMAIN="${1:?uso: setup-server.sh dominio.it}"

apt-get update
apt-get install -y nginx certbot python3-certbot-nginx ufw git curl
curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
apt-get install -y nodejs

id hometech >/dev/null 2>&1 || useradd --system --create-home --shell /usr/sbin/nologin hometech
mkdir -p /opt/hometech /var/www/hometech

ufw allow OpenSSH
ufw allow 'Nginx Full'
ufw --force enable

sed "s/example.it/${DOMAIN}/g" "$(dirname "$0")/nginx.conf" > /etc/nginx/sites-available/hometech
ln -sf /etc/nginx/sites-available/hometech /etc/nginx/sites-enabled/hometech
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx

cp "$(dirname "$0")/hometech-api.service" /etc/systemd/system/hometech-api.service
systemctl daemon-reload
systemctl enable hometech-api

echo "Server pronto. Ora esegui deploy.sh dal tuo computer, poi: certbot --nginx -d ${DOMAIN} -d www.${DOMAIN}"
