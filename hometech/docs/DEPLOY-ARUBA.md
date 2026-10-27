# Pubblicazione su Aruba

VPS usato: AlmaLinux 9 (anche Rocky, Ubuntu e Debian sono supportati dagli script), senza pannello di controllo.

## Cosa serve
1. **Dominio** (Aruba o altro registrar; basta poter modificare i record DNS).
2. **VPS Linux Aruba**: 2 vCPU, 4 GB RAM. IP pubblico fisso, accesso SSH con chiave.
3. Per le app (a parte, non su Aruba): Apple Developer (~99 $/anno) e Google Play Console (25 $ una tantum).

## Passi
1. **Primo accesso** dal tuo computer: `ssh root@IP_DEL_VPS`
2. **DNS**: crea i record `A` per `tuodominio` e `www` verso l'IP del VPS (la propagazione può richiedere ore).
3. **Copia gli script sul server** (dalla cartella `hometech/`):
   `scp -r deploy root@IP_DEL_VPS:/root/`
4. **Prepara il server** (una volta, sul VPS):
   `bash /root/deploy/setup-server.sh tuodominio.it`
5. **Pubblica sito e API** (dal tuo computer, a ogni aggiornamento):
   `bash deploy/deploy.sh root@IP_DEL_VPS`
6. **HTTPS** (una volta, dopo che il DNS punta al VPS), sul VPS:
   `certbot --nginx -d tuodominio.it -d www.tuodominio.it`
7. **Verifica**: `https://tuodominio.it` mostra il sito, `https://tuodominio.it/api/health` risponde `{"ok":true}`.
8. **Sicurezza SSH**: da una seconda finestra verifica di entrare con la chiave, poi sul VPS lancia `bash /root/deploy/harden-ssh.sh` per disattivare la password.

## App
- Imposta `EXPO_PUBLIC_API_URL=https://tuodominio.it/api`, crea le build con EAS (`npx eas build`) e pubblica su App Store e Google Play.
- Le app devono parlare con l'API solo in HTTPS.

## Prima di aprire al pubblico
- L'API ha dati in memoria e nessun login vero: non va esposta a utenti reali finché non ci sono database (PostgreSQL/PostGIS) e autenticazione.
- Backup automatici del VPS (opzione Aruba).
- Pagine privacy e termini (GDPR), obbligatorie anche per gli store.
