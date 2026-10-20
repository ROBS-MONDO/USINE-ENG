# Biglietti con QR: installazione su Aruba

Dopo il pagamento del biglietto da 60 euro Stripe avvisa il sito, che crea un biglietto con QR per ogni biglietto acquistato, lo salva nel database e lo invia per email. All'ingresso lo staff scansiona il QR con il telefono dalla pagina `/scan/`.

## File da caricare nella cartella principale del sito
- `stripe-webhook.php`
- cartella `ticket/` (tutta, compresi `lib`, `vendor`, `fonts`, `.htaccess`)
- cartella `scan/` (tutta, compreso `.htaccess`)
- `.htaccess` (da `htaccess.txt`), `privacy.html`, `index.html`

## 1. Database
1. Pannello Aruba > Database > **Vai a phpMyAdmin**, scegli il database (per esempio `Sql..._1`).
2. Scheda **Importa** > scegli `ticket/schema.sql` > Esegui.

## 2. Configurazione
1. In `ticket/` copia `config.sample.php` in `config.php` (stesso nome senza `.sample`).
2. Compila:
   - `db`: hostname, nome, utente e password del database (dal pannello Aruba);
   - `smtp.pass`: password della casella `info@luxembourgfestival.com`;
   - `scan.password_hash`: hash della password dello staff. Per crearlo: `php ticket/make-hash.php "LA_TUA_PASSWORD"`. Se non puoi usare il terminale, dimmelo e lo creiamo in un altro modo;
   - `stripe.webhook_secret` e `stripe.payment_link_id`: vedi il punto 3.
3. Dopo la configurazione cancella `ticket/make-hash.php`.

## 3. Stripe
1. **Payment Link del biglietto da 60 euro**: apri **Payment links**, clicca il link, attiva **Consenti ai clienti di modificare la quantità** (massimo 10) e **Raccogli il nome del cliente**. L'ID del link (`plink_...`) compare nell'indirizzo del browser o nei dettagli.
2. **Webhook**: Sviluppatori > **Webhook** > Aggiungi endpoint.
   - URL: `https://www.luxembourgfestival.com/stripe-webhook.php`
   - Eventi: `checkout.session.completed`, `checkout.session.async_payment_succeeded`, `charge.refunded`
   - Copia la **Signing secret** (`whsec_...`) in `config.php`.
3. Non usare codici sconto su questo link, oppure compila `stripe.api_key` con una chiave "restricted" in sola lettura su Checkout Sessions, per contare i biglietti con precisione.

## 4. Prova
1. Dal Payment Link fai un pagamento di prova (modalità test di Stripe) o in Stripe invia un evento di prova al webhook.
2. Controlla che arrivi l'email con il biglietto e che compaia una riga nella tabella `tickets`.
3. Apri `https://www.luxembourgfestival.com/scan/`, accedi con la password dello staff e scansiona il QR.

## All'ingresso
- **Verde**: valido, ingresso registrato. **Arancione**: già usato (con ora). **Rosso**: non valido o annullato.
- **Cerca**: ricerca per nome, email o codice, ingresso manuale e reinvio del biglietto. **Scarica elenco CSV**: elenco completo, da portare anche stampato come riserva.
- Serve la rete mobile o il wifi. Controlla la copertura nel luogo dell'evento.

## Rimborsi
Se rimborsi un pagamento in Stripe, i suoi biglietti vengono annullati da soli (rosso alla scansione).

## Log
Gli eventi del webhook sono in `ticket/logs/webhook.log` (la cartella non è accessibile dal browser).
