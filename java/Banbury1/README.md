# Banbury 1 – strumenti S7 (sola lettura)

PLC: Siemens **IM 151-8 F-CPU PN/DP** (S7-300/ET200S, CPU di sicurezza), progetto
STEP 7 `MONDO_Ligne_1_23062020`. Il sinottico WinCC (`001_Synoptic.Pdl`) legge/scrive
direttamente i DB **110**, **150**, **201** (e `Cmd_HMI` = DB3).

## Contenuto
| File | Funzione |
|---|---|
| `S7ReadClient.java` | client ISO-on-TCP/S7 minimale, **solo lettura** (nessun metodo di scrittura) |
| `B1DbDump.java` | stampa i DB e poi le variazioni, per associare ogni indirizzo a un valore del sinottico |
| `B1_Main.java` | **sinottico** B1 (replica di `001_Synoptic`), SOLA LETTURA |
| `B1Tags.java`, `b1_tags.properties` | mappa chiave -> indirizzo S7 (modificabile senza ricompilare) |
| `B1Source.java`, `B1PlcSource.java`, `B1DemoSource.java` | sorgente dati: PLC reale o demo |
| `DB_MAP.md` | ipotesi sulla mappa dei DB |
| `B1RecipeEditor.java` | **editor ricette** per postazione ufficio |
| `B1Recipe*.java`, `B1FileRecipeRepository.java` | modello, layout DB201, codec, archivio su file |
| `b1_recipe_layout.properties` | descrizione del formato ricetta nel DB201 (modificabile) |
| `B1RecipeBridge.java` | collegamento sinottico <-> editor: confronto ricetta in macchina / archivio |
| `B1RecipeSelfTest.java` | test automatici (codec, validazione, archivio, conflitti) |

## Uso
```bash
javac -d out S7ReadClient.java B1DbDump.java
java -cp out Banbury1.B1DbDump <ip-cpu> DB110 DB150 DB201          # rack 0, slot 2, ciclo 500 ms
java -cp out Banbury1.B1DbDump <ip-cpu> -r 0 -s 2 -i 300 DB110
java -cp out Banbury1.B1DbDump <ip-cpu> --once DB110
```
Se la connessione e' rifiutata prova `-s 3` o `-s 1`. Sulle CPU S7-300/ET200S non serve
abilitare PUT/GET. Non occorre KEPServer.

## Layout DB110 (256 byte, da export STEP 7)
| Offset | Contenuto |
|---|---|
| 0.0 – 1.7 | 16 BOOL (`STAT0..STAT15`) |
| 2 … 140 | parole `INT` `STAT16..STAT85` (DBW2, DBW4, … DBW140) |
| 142 … 171 | 3 struct da 10 byte: 16 BOOL + 4 INT ciascuna |
| 172 … 201 | altre 3 struct da 10 byte, stesso schema |
| 202 … 254 | parole `INT` `STAT214..STAT240` |

I nomi nel DB sono generici (`STATn`): il significato di ogni campo va **confermato
confrontando i valori con il Runtime** (pressione, temperatura, giri, lotto, ecc.).

## Indirizzi usati dalla schermata `001_Synoptic` (da WinCC)
`DB110`: DBW5, 6, 22, 24, 26, 44, 54, 58, 60, 62, 70, 72, 86, 100, 106, 108, 140, 234, DBX0.0
`DB150`: DBW0, DBW2, DBX40 – `DB201`: DBX41.0

Comandi presenti negli script: `SetBit_Word("DB110DBW22", 1024 / 2048)` e
`SetBit_Word("DB110DBW6", 1024 / 2048)` (bit 10 e 11 della parola). Le lampade usano
maschere su DBW6 (0x0002, 0x1000, 0x2000). **Ipotesi da verificare**, nessuna e' ancora confermata.

## Prossimi passi
1. Lanciare `B1DbDump` accanto al Runtime e annotare quali parole variano con
   temperatura (93), velocita' (33), corrente, lotto (9004), sequenza, cicli.
2. Compilare la tabella di mappa e solo dopo costruire `B1_Main`.
3. Scrittura/comandi: solo dopo la mappa, uno alla volta, mai su blocchi F.

## Sinottico
```bash
java -cp out Banbury1.B1_Main 192.168.10.100            # PLC reale (sola lettura)
java -cp out Banbury1.B1_Main --demo                    # valori dello screenshot WinCC
java -cp out Banbury1.B1_Main --tags C:\_tools\Banbury1\b1_tags.properties
java -cp out Banbury1.B1_Main --snapshot out.png --demo # salva un'immagine
```
* **F2** mostra, sopra ogni campo, la chiave e l'indirizzo (rosso = non collegato).
* Il file `b1_tags.properties` si cerca in `C:\_tools\Banbury1\` oppure nella cartella corrente.
* Campi senza indirizzo mostrano `--`; le lampade non collegate hanno una barra diagonale.
* I pulsanti di comando sono disegnati ma **non scrivono nel PLC**.

## Editor ricette
```bash
java -cp out Banbury1.B1RecipeEditor --dir \\srv-bambury\Mondo\RecipesB1 --plc 192.168.10.100
java -cp out Banbury1.B1RecipeSelfTest        # test automatici
```
* Una ricetta = un file `{uid}.b1recipe` nella cartella dell'archivio (condivisa di rete per piu' postazioni).
  Salvataggio atomico + controllo di revisione: se un'altra postazione ha salvato prima, il salvataggio e' rifiutato.
* **Importa dal PLC**: legge DB201 (sola lettura) e lo mostra nell'editor, utile per confrontare il layout con la
  schermata `Vue recette` del WinCC.
* Struttura DB201 (dal dump): testata 52 byte (n. ricetta @2, nome STRING[32] @4, parametri) + 11 fasi da 30 byte.
  I campi con `(?)` sono ipotesi: si correggono in `b1_recipe_layout.properties` senza ricompilare.
* Validazione come nel WinCC: nome valido, operazione in ogni fase, fase di scarico, limiti; le fasi dopo lo
  scarico sono residui e non vengono eseguite.
* **Invio al PLC non attivo**: il codec e' pronto (`B1RecipeCodec.encode`) ma la scrittura va abilitata solo dopo la
  conferma del layout e con un client di scrittura dedicato (la CPU e' F).

## Collegamento sinottico <-> editor ricette
```bash
java -cp out Banbury1.B1_Main 192.168.10.100 --period 1000 --recipes-dir \\srv-bambury\Mondo\RecipesB1
```
* Sotto "Ricetta: <nome>" (DB201 della macchina) il sinottico mostra: `= archivio (rev. N)` (verde),
  `DIVERSA dall'archivio` (arancio) o `non in archivio` (blu). Il confronto ignora il n. ricetta e le fasi
  dopo lo scarico (residui). Ricalcolo ogni 5 s, in un thread separato.
* Clic su quell'etichetta: apre l'editor sulla ricetta; se non e' in archivio la importa dal PLC (sola lettura).
* Pulsante **Recettes** della barra in basso: apre l'editor.
* `--period` = ciclo di lettura in ms (consigliato 1000 su macchina in produzione).
* Tutto in sola lettura: nessuna scrittura sul PLC.

## Altre schermate WinCC (Process, Display, Production, Settings, Temperatures, Components, Govoni...)
```bash
java -cp out Banbury1.B1_Main 192.168.10.100 --period 1000      # pulsanti della barra in basso -> schermate
java -cp out Banbury1.B1_Main --demo --screen 005_Display --snapshot display.png [--overlay]
```
* `screens/*.b1s` sono i layout estratti dai `.Pdl` del progetto WinCC `Mondo(210623)` (posizione, testo, campo di valore
  con il suo tag `DBxDBWn`). Vanno copiati nel classpath (Eclipse lo fa da solo) oppure in `C:\_tools\Banbury1\screens`.
* Lampade nelle schermate generiche: disegnate "non collegate" (barra diagonale) finche' il bit non e' confermato.
* Campi di valore: valore grezzo x fattore (`B1Screen.SCALE`: pressione `DBW70/72` x0.01, energia `DBW34` x0.1: ipotesi).
* Non ricostruiti: controlli WinCC (trend, allarmi, tabelle di fase) -> riquadro tratteggiato `[controllo WinCC]`.

## Valori PLC (tasto F3 o pulsante "Valori PLC")
Tabella live (solo lettura) di tutte le parole di DB110/DB150/DB201: dec, hex, bit, ASCII, numero di variazioni,
chiave del sinottico che la usa e **dove la usa il WinCC** (schermata + etichetta). Righe gialle = cambiate negli ultimi 5 s.
E' lo strumento per completare `b1_tags.properties`: si cerca la parola che cambia e si legge cosa mostra il WinCC.

## Indirizzi confermati dal WinCC (progetto Mondo(210623))
| Campo | Indirizzo | Nota |
|---|---|---|
| Pressione consegna / barra alta | DB110.DBW70 | scala x0.01 (ipotesi) |
| Pressione attuale | DB110.DBW72 | scala x0.01 (ipotesi) |
| Giri consegna / attuali / corrente | DBW58 / DBW60 / DBW62 | |
| Temperatura (riquadro / barra alta) | DBW100 / DBW104 | |
| Miscele totali / fatte | DBW210 / DBW212 | "da fare" = totale - fatte |
| Passi ciclo | DBW234 | |
| % pistone | DBW86 / DBW106 / DBW108 | |
| SEQ | DB150.DBW0 / DBW2 | |
| Ricetta: temp. sicurezza / porta scarico / tempo sicurezza / energia max / temp. riavvio | DB201.DBW28 / 30 / 32 / 34 / 38 | |
| Lampade 001 | bit **ipotizzati** (vedi b1_tags.properties) | da confrontare con il WinCC |

## Lettura ticket (ticket 1 = primo passaggio, ticket 2 = secondo) -> database SQL
```bash
java -cp out Banbury1.B1LabelSelfTest          # test del parser e della costruzione SQL
```
* Sinottico: pulsante **Etichetta** o **F4**. Il lettore QR lavora da tastiera (keyCode fisico, come in B2).
* `B1LabelParser` e' il port della logica di `PLC3_Recette` (QR `MQR2` Base32, `RECIPEUID`, correzione tastiera
  francese AZERTY, ticket "compatti", lotto con lettere). Differenza voluta: l'anno non e' fisso a `26` ma e'
  l'anno corrente e il precedente.
* I dati NON vanno al PLC: vanno al database SQL tramite `B1SqlLabelStore`, configurato in `b1_label_sql.properties`
  (tabella, colonne, INSERT o UPDATE). Finche' `enabled=false` o `table` e' vuota e' in **PROVA**: mostra l'SQL, non scrive.
* Valori scritti come testo, come nei campi del WinCC e in `Datas_B2`: passaggio `1er/2ème/3ème/4ème` (come in `Datas_B1`),
  type `SUP/INF/STS/NET/MEL.MERE`, couleur `---/Clair/Foncé/Medium`; `Num_lot` = `AA/LOTTO` (es. `26/0703`).
* Password: variabile d'ambiente `B1_SQL_PASSWORD` (non nel file ne' nel codice). Ogni lettura e' registrata in
  `C:\_tools\Banbury1\label_log.txt`.

## Datas_B1 (DATA_BAMBURY) - struttura osservata
Log con una riga ogni ~2 s: `Date_record, Time_record, Num_lot (26/9004), Suffixe, Num_melange, Type_melange (INF),
Num_passage (4ème), Code_quasar, Nom_melange, Couleur, Nbr_sequence (10), Num_sequence (23), Mode (Manu/Auto),
Temps_cycle, Temperature, Amperage, Vitesse, Position_piston (Bas), Porte_charge (Ferme), Porte_decharge (Ferme),
Clair_fonce`. Nbr_sequence/Num_sequence = DB150 DBW0/DBW2 (SEQ del sinottico).

## Pulizia dei dati vecchi (Datas_B1 / Datas_B2)
`sql/purge_datas_b1_b2.sql` crea `dbo.usp_PurgeDatasLog`: conserva gli ultimi N giorni (default 90) e cancella il resto a
blocchi, di notte. **Parte in prova** (`@Execute = 0`: solo conteggi). Retention minima 7 giorni; tocca solo `Date_record`
di `Datas_B1` e `Datas_B2`. Contiene, commentati, l'indice consigliato e il job di SQL Server Agent (creato disabilitato).
Non provato su SQL Server: prima prova a secco e backup.

## Chi scrive Datas_B1 / Datas_B2 (dalla cache SQL, 12/11/2026)
* `INSERT INTO [Datas_B1] values(@1..@21)` e `INSERT INTO [Datas_B2] values(@1..@21)`: **senza elenco di colonne**
  (21 valori posizionali, tutti passati come testo, JDBC) ogni ~2 s, login `Banbury2` da DESKTOP-1BHBR06.
  Ordine delle 21 colonne di Datas_B1: Date_record, Time_record, Num_lot, Suffixe, Num_melange, Type_melange,
  Num_passage, Code_quasar, Nom_melange, Couleur, Nbr_sequence, Num_sequence, Mode, Temps_cycle, Temperature,
  Amperage, Vitesse, Position_piston, Porte_charge, Porte_decharge, Clair_fonce.
* **Non aggiungere colonne** a Datas_B1/B2: l'INSERT posizionale smetterebbe di funzionare.
* Un lettore periodico (:00 e :30) legge 15 di quelle colonne da entrambe le tabelle (probabile trasferimento verso
  un altro sistema): la pulizia a 15 giorni (job DELETE-DATA) lo lascia indisturbato.
* Esiste anche `INSERT INTO [Consommation] values(@1..@10)` (consumi della bilancia): nessuna pulizia automatica.

## Trend e allarmi (dai controlli WinCC)
* **Trend** (pulsante Trends): `B1TrendWindow`/`B1TrendChart`. Dal WinCC (`005_Display`, OnlineTrendControl): un andamento
  "Temperature" (archivio `ProcessValueArchiveB1`, tag `B1_TEMPERATURE`, rosso), finestra 20 minuti, 120 punti, scala 0-200.
  Due sorgenti, sola lettura: **LIVE** (valori del PLC campionati ogni secondo, memoria 6 ore) e **STORICO** da
  `dbo.Datas_B1` (Temperature, Amperage, Vitesse, sfondo Auto/Manu come `PLC3_Graph`; connessione = chiavi `jdbc.*` di
  `b1_label_sql.properties`). Ruler con i valori al passaggio del mouse. `B1TrendSelfTest` prova le funzioni pure.
  La schermata `003_Trends` nel WinCC non contiene la lista dei tag (si scelgono a runtime): le serie sono quelle di `Datas_B1`.
* **Allarmi** (pulsante Alarms): `B1AlarmEngine` + `B1AlarmWindow`. Dal controllo WinCC (`004_Alarms`): colonne
  Date, Time, Nombre, Text1 = "Point d'erreur", Text2 = "Texte du message"; righe alternate bianco/grigio, colori per classe,
  pagina da 50 messaggi. Il motore legge i bit dal PLC (arrivo/rientro), tiene l'elenco e registra su
  `b1_alarm_log.csv`. **Le definizioni dei messaggi non sono nel progetto WinCC fornito** (stanno nel database di
  configurazione di Alarm Logging, file bloccato nell'archivio): vanno inserite in `b1_alarms.properties`
  (`alarm.<n> = DB110DBX22_3 ; Point d'erreur ; Testo ; classe`). Finche' e' vuoto la lista resta vuota.
  `B1AlarmSelfTest` prova il motore. Per ottenere l'elenco: WinCC Explorer -> Alarm Logging -> elenco messaggi (screenshot o export).
