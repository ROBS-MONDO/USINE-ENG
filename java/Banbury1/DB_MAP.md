# Banbury 1 – mappa DB (aggiornata 12/11/2026)

Fonti: progetto WinCC `Mondo(210623)` (schermate `.Pdl`), export STEP 7 del DB110, dump del PLC (192.168.10.100) e
log SQL `Datas_B1`. **[WinCC]** = letto dal progetto, quindi affidabile; **[dump]** = dedotto dai valori; **[ipotesi]**.

## DB110 – stato macchina (256 byte)
| Indirizzo | Significato | Fonte |
|---|---|---|
| DBW58 / 60 / 62 | giri consegna / giri attuali / corrente (A) | WinCC |
| DBW70 / 72 | pressione consegna / attuale (scala x0,1, confermata) | WinCC + foto pannello |
| DBW74 | posizione pistone (%) | WinCC (Display) |
| DBW86 / 106 / 108 | % pistone (3 campi) | WinCC |
| DBW92 / 94 , DBW112 / 114 | temperature "Corps machine" / "Porte déchargement" (due coppie) | WinCC (Temperatures) |
| DBW100 | temperatura (riquadro sinottico) | WinCC |
| DBW104 | temperatura (barra alta) | WinCC |
| DBW132 | lampada "Flottant" (Display) | WinCC |
| DBW210 / 212 | miscele totali / fatte ("da fare" = totale - fatte) | WinCC |
| DBW218 | numero fase in corso (RunningPhaseActual) | WinCC |
| DBW234 | passi del ciclo | WinCC |
| DBW194/222, 202/220 | contatori in secondi | dump |
| DBW22, 24, 26, 44, 54, 140, 6 | parole di stato (lampade) | WinCC cita le parole; **bit = ipotesi** |

## DB150 – lotto/sequenza (34 byte)
DBW0 / DBW2 = SEQ (confermato: `Nbr_sequence`/`Num_sequence` in Datas_B1 = 10 / 23). STRING[4] @6 = anno ("26"),
STRING[8] @12 = lotto ("9004") [dump].

## DB201 – ricetta (384 byte)
* Testata: DBW2 n. ricetta, STRING[32] @4 nome, **DBW28** temp. sicurezza (°C), **DBW30** porta di scarico (s),
  **DBW32** tempo sicurezza (s), **DBW34** energia max (kWh, x0,1), **DBW38** temp. riavvio (°C) **[WinCC]**.
* 11 fasi da 30 byte da offset 52 (3 gruppi da 10 byte: parola a bit + 4 INT) [dump].
* **Operazioni** (parola a offset 20 della fase) **[WinCC btnBitJob]**: Gomme=0, Add.=1, All Black=2, Plastif.=3,
  Cerac.=4, Silano=5, Half Black=6, Degas=13, Net.=14, **Déch. (scarico)=15**.
* **Controlli** (btnBitControl) **[WinCC]**: et=bit 0, ou=bit 1, Skip=bit 15 (in quale parola: da identificare).
* Valori (dal dump ricetta "gomma2"): rotor = +2 (36/33/33, = DBW58) [dump+WinCC], timeout = +4 (900/900/700) [ipotesi],
  temp. = +14 (80/90/105) [ipotesi], pressione = +22 (2/2/10) [ipotesi]. Colonne WinCC della fase: Timeout, Rotor (rpm),
  Press. (bar, 1 decimale), Porte, Piston, Skip, Temps (s), Temp. (°C), Éner. (kWh, 1 decimale), et, ou.
* **Trasferimento ricetta [WinCC, Production]**: lampade "Start transfert" = DB201.DBX41.0 e "Fin transfert" =
  DB201.DBX563.0. **Attenzione**: l'offset 563 supera i 384 byte del DB201 online (verificare: tag obsoleto o DB diverso).

## SQL (DATA_BAMBURY)
`Datas_B1`/`Datas_B2`: 21 colonne, INSERT posizionale ogni ~2 s da DESKTOP-1BHBR06 (login Banbury2); pulizia a 15 giorni
con il job `DELETE-DATA`. Vedi `README.md`.

## Confronto con le foto del pannello originale (13/11/2026 ~12:10, macchina ferma)
Valori letti sul pannello: pressione consegna 140.0 bar / attuale 5.5 bar (-> scala DBW70/72 x0.1), % 0/80/80, rotori 34 rpm consegna / 0 rpm / 0 A,
lotto 26/0729R, SEQ 15/0, totale 500 / fatti 122 / da fare 378, ciclo 5:00 mm:ss (= 300 s; tag ancora da trovare, cercare 300 in Valori PLC),
"Mixing" 4:37 (= 277 s, uguale al "Time (s)" della fase 1), tempo ciclo ricetta 2:27 (= 147 s). Pagina Process: ricetta S.TAPIS SPORT-IMPACT,
safety time 600 s, safety temp 120 C, max energy 30.0 kWh, restart temp 90 C, drop door 17 s.
Lampade (accesa = chiara): pistone = SOLO "posizione alta"; malaxeur pret = tutte tranne "moteur principal OK"; cycle malaxeur = SOLO "continuo";
produzione = SOLO "ricetta valida". Il programma mostrava invece accese Bloque, Auto, Pret pour production e (rdy) moteur OK:
corretti per scambio di bit (piston_high/blocked, cyc_auto/cyc_cont, prod_ready/recipe_ok); le 3 lampade "malaxeur pret" ancora da verificare con Cattura A/B.

## Risultati dal registro continuo B1DbDump (16/11/2026, 11:12-11:16) confrontato con le foto del pannello
Orologio del pannello = orologio del PC + circa 3 min 27 s (ricavato dalla temperatura DBW100 nelle foto e nel registro).
* **DBW202 (e copia DBW220)** = tempo ciclo in secondi (165 = 2:45 nella foto): conta +1 al secondo. DBW198/204 = tempo di mescola (secondi).
* **DBW22 pistone** (bit = bit del valore): B0 = posizione alta, B1 = posizione bassa, B2 = flottante (con LOW+FLOAT in fase 4, come nelle foto), B5 = pistone in movimento
  (si accende sia in salita sia in discesa; la direzione non e' in DB110: si ricava dall'ultimo fine corsa), B3 = impulso breve durante il movimento. "Bloque" non trovato.
* **DBW54 malaxeur pret**: B0 min temp OK (si spegne a 11:13:42 con la temperatura alta, come la lampada nelle foto), B1 scarico OK, B2 motore OK (spento a macchina ferma nella foto
  delle 12:10), B3 porta scarico chiusa, B4 tempo min tra cicli, B5 n. cicli passi non effettuati. B12 = ciclo continuo (acceso).
* DBW24 (porte): 0x80AA (chiuse) -> 0x8055 (aperte) a 11:15:05-11:15:12: coppie aperta/chiusa, coerente con la mappa attuale (push rod B3/B4, porta scarico B0/B1).
* DBW234 conta le fasi/step (45, 30, 25, 23 nelle foto).
* Ancora da trovare: Cycle malaxeur auto / cycle, produzione cycle auto ON / ricetta valida, silos chiuso, "Bloque", bit della direzione del pistone.

## Invio ricetta dal WinCC (registro del 16/11/2026 13:29, due invii della stessa ricetta con valori diversi)
* Scambio: `DB110.DBW44` 0x100A -> 0x0006 (il PLC prende in carico), `DB201.DBW40` bit 0 = Start (DBX41.0), `DB201.DBW382` bit 0 = Fine (DBX383.0), dopo ~9 s tutto torna a riposo.
* Parole scritte (solo quelle diverse): `W28, W30, W38` (testata); fase 1 `W54` rpm, `W56` timeout, `W66` temp.; fase 2 `W86` timeout, `W96` temp.; fase 3 `W114` rpm, `W116` timeout, `W122` controllo,
  `W126` temp., `W134` press.; fase 4 `W144` rpm, `W146` timeout; per ultimo `W2` (n. ricetta) e `W382` (fine). Ordine di scrittura = indirizzi crescenti a gruppi (~150 ms).
* Il WinCC indica "Fin transfert" a `DB201.DBX563.0`, ma il flag che cambia nel registro e' `DBX383.0`: l'indirizzo del WinCC non e' quello del DB reale di 384 byte.

