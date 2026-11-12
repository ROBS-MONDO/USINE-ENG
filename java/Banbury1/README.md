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
