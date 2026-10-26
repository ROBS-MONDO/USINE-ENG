# Pubblicazione su Aruba

## Cosa acquistare
1. **Dominio** (Aruba Domini), es. `tuodominio.it`.
2. **VPS / Cloud Server Linux Aruba** con Ubuntu 22.04 o 24.04. Per iniziare bastano 2 vCPU, 2-4 GB RAM, 40 GB disco. Prendi l'IP pubblico fisso.
3. Per le app (a parte, non su Aruba): Apple Developer (~99 $/anno) e Google Play Console (25 $ una tantum).

## Passi
1. **DNS**: nel pannello Aruba crea i record `A` per `tuodominio.it` e `www` verso l'IP del VPS. La propagazione può richiedere alcune ore.
2. **Accesso**: dal pannello Aruba ottieni l'accesso SSH come root (meglio poi creare un utente con chiave SSH e disattivare la password).
3. **Prepara il server** (una volta): copia la cartella `deploy/` sul VPS e lancia
   `bash setup-server.sh tuodominio.it`
4. **Pubblica sito e API** (dal tuo computer, ogni aggiornamento):
   `bash deploy/deploy.sh root@IP_DEL_VPS`
5. **HTTPS** (una volta, dopo che il DNS punta al VPS):
   `certbot --nginx -d tuodominio.it -d www.tuodominio.it`
6. **Verifica**: `https://tuodominio.it` mostra il sito, `https://tuodominio.it/api/health` risponde `{"ok":true}`.

## App
- Imposta `EXPO_PUBLIC_API_URL=https://tuodominio.it/api` e crea le build con EAS (`npx eas build`), poi pubblica su App Store e Google Play.
- Le app devono parlare con l'API solo in HTTPS.

## Prima di aprire al pubblico
- L'API oggi ha dati in memoria e nessun login vero: non va esposta a utenti reali finché non ci sono database (PostgreSQL/PostGIS) e autenticazione.
- Backup automatici del VPS (opzione Aruba) e aggiornamenti di sicurezza (`unattended-upgrades`).
- Pagine privacy e termini (GDPR), obbligatorie anche per gli store.
