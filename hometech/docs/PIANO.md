# HomeTech - piano

Marketplace on-demand: il cliente richiede un tecnico (idraulico, elettricista, ...) per un lavoro a casa; il sistema trova il tecnico disponibile più vicino.

## Struttura
- `api/` backend Node.js/Express (archivio in memoria per ora). `npm install && npm start`, `npm test`.
- `web/` sito di presentazione (HTML statico, da portare a Next.js quando serve l'area clienti/admin).
- `app/` app Expo (React Native). Una sola app con ruolo cliente; la parte tecnico è da aggiungere.

## Stato
Fatto: registrazione, posizione e disponibilità tecnici, ricerca per vicinanza e competenza, richiesta, accettazione, avanzamento (accepted -> on_the_way -> in_progress -> done), test del flusso.

## Prossimi passi
1. PostgreSQL + PostGIS al posto dell'archivio in memoria.
2. Autenticazione vera (telefono/OTP o email) al posto dell'id utente nelle richieste.
3. Schermate tecnico nell'app: disponibilità, richieste vicine, accetta, aggiorna stato.
4. Tracking in tempo reale (WebSocket) e mappa.
5. Preventivo/prezzo, pagamenti (Stripe), recensioni.
6. Verifica documenti e abilitazioni dei tecnici (obbligatoria per la fiducia del servizio).
7. Pannello admin e notifiche push.
