#!/usr/bin/env bash
# Da eseguire UNA volta sul VPS come root:  bash setup-server.sh tuodominio.it
# Supporta AlmaLinux/Rocky (dnf) e Ubuntu/Debian (apt).
set -euo pipefail
DOMAIN="${1:?uso: setup-server.sh dominio.it}"
HERE="$(cd "$(dirname "$0")" && pwd)"

if command -v dnf >/dev/null 2>&1; then
  dnf -y install epel-release
  dnf -y install nginx certbot python3-certbot-nginx git curl rsync tar firewalld policycoreutils-python-utils dnf-automatic
  curl -fsSL https://rpm.nodesource.com/setup_22.x | bash -
  dnf -y install nodejs

  systemctl enable --now firewalld
  firewall-cmd --permanent --add-service=ssh --add-service=http --add-service=https
  firewall-cmd --reload

  # SELinux: permette a Nginx di contattare l'API su 127.0.0.1:3000
  setsebool -P httpd_can_network_connect 1

  # Aggiornamenti di sicurezza automatici
  sed -i 's/^apply_updates.*/apply_updates = yes/' /etc/dnf/automatic.conf
  systemctl enable --now dnf-automatic.timer

  NGINX_CONF=/etc/nginx/conf.d/hometech.conf
else
  apt-get update
  apt-get install -y nginx certbot python3-certbot-nginx ufw git curl rsync unattended-upgrades
  curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
  apt-get install -y nodejs

  ufw allow OpenSSH
  ufw allow 'Nginx Full'
  ufw --force enable

  rm -f /etc/nginx/sites-enabled/default
  NGINX_CONF=/etc/nginx/sites-available/hometech
  ln -sf "$NGINX_CONF" /etc/nginx/sites-enabled/hometech
fi

id hometech >/dev/null 2>&1 || useradd --system --create-home --shell /usr/sbin/nologin hometech
mkdir -p /opt/hometech /var/www/hometech

sed "s/example.it/${DOMAIN}/g" "$HERE/nginx.conf" > "$NGINX_CONF"
nginx -t
systemctl enable --now nginx
systemctl reload nginx

cp "$HERE/hometech-api.service" /etc/systemd/system/hometech-api.service
systemctl daemon-reload
systemctl enable hometech-api

echo "Server pronto. Ora esegui deploy.sh dal tuo computer, poi: certbot --nginx -d ${DOMAIN} -d www.${DOMAIN}"
