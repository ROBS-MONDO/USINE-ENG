#!/usr/bin/env bash
# Sul VPS come root. Esegui SOLO dopo aver verificato, da un'altra finestra,
# che puoi entrare con la chiave SSH: poi l'accesso con password viene disattivato.
set -euo pipefail

if [ ! -s /root/.ssh/authorized_keys ]; then
  echo "Nessuna chiave in /root/.ssh/authorized_keys: non disattivo la password." >&2
  exit 1
fi

# Prefisso 00- perche' in sshd vale il primo valore letto (es. 50-cloud-init.conf)
install -d /etc/ssh/sshd_config.d
cat > /etc/ssh/sshd_config.d/00-hometech.conf <<EOC
PasswordAuthentication no
PermitRootLogin prohibit-password
EOC
sshd -t
systemctl reload sshd 2>/dev/null || systemctl reload ssh
echo "Accesso con password disattivato. Non chiudere questa sessione finche' non provi a riconnetterti con la chiave."
