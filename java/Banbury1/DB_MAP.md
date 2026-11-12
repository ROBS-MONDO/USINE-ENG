# Banbury 1 – mappa DB (aggiornata 12/11/2026)

Fonti: progetto WinCC `Mondo(210623)` (schermate `.Pdl`), export STEP 7 del DB110, dump del PLC (192.168.10.100) e
log SQL `Datas_B1`. **[WinCC]** = letto dal progetto, quindi affidabile; **[dump]** = dedotto dai valori; **[ipotesi]**.

## DB110 – stato macchina (256 byte)
| Indirizzo | Significato | Fonte |
|---|---|---|
| DBW58 / 60 / 62 | giri consegna / giri attuali / corrente (A) | WinCC |
| DBW70 / 72 | pressione consegna / attuale (scala x0,01?) | WinCC (scala ipotesi) |
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
