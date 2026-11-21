# Traduzioni del sito

Le pagine russe (`index.html`, `sila-obedineniya.html`, `privacy.html`) sono la fonte.
Le altre lingue si generano con `python3 tools/build_i18n.py` (crea le cartelle `en/`, `fr/`, `de/`).

- `i18n/<lingua>.json`: traduzioni. "text" = testi e attributi (russo → traduzione); "script" = messaggi di JavaScript e dati strutturati.
- Dopo aver cambiato un testo nelle pagine russe, lo strumento segnala quali testi mancano nei file `.json`.
- Le recensioni (`<article class="rev">`) restano in russo.
- Per aggiungere una lingua: creare `i18n/<codice>.json`, aggiungerla a `LANGS` in `tools/build_i18n.py`, ai selettori di lingua e ai `hreflang` delle pagine russe.
