# USINE-ENG

Classi Java per gli impianti di produzione (mescolatore Banbury, bilancia, report).

## Nota

`java/ProdMelBanb.java` referenzia la classe `ProdMelBanbBalanceSupport`
(gestione bilancia), che al momento non fa parte di questo repository e va
aggiunta separatamente per poter compilare il progetto.

## VentesNationClient (sostituisce i Crystal Report "VENTES TOTAL ... PAR NATION/CLIENT")

`java/VentesNationClient.java` riproduce i due report Crystal
*VENTES TOTAL Caou PAR NATION/CLIENT -Statpav2-* e *VENTES TOTAL Ballons PAR NATION/CLIENT*
(fatturato per nazione e cliente, totale generale, affidamento assicurativo).
Serve solo il driver JDBC Oracle (`ojdbc8.jar`) nel classpath.

```bash
javac -d out java/VentesNationClient.java
# interfaccia grafica (report, FromDate, ToDate, password -> Refresh -> Esporta CSV)
java -cp out:ojdbc8.jar Ventes.VentesNationClient
# da riga di comando
java -cp out:ojdbc8.jar Ventes.VentesNationClient --report caou --from 01-01-2018 --to 30-11-2018 --out caou.csv
```

Connessione: `--url`, `VENTES_DB_URL` o `ventes.properties` (vedi `java/ventes.properties.example`);
utente di default `poker`, password chiesta all'avvio (o `--password` / `VENTES_DB_PASSWORD`).

Regole replicate dai Crystal: Caou = somma `STAT_PAV2.RB_VALORE`, escluse parti `WSPETRA*`/`WIMPCORTRA*`
e `RB_STF = 'I'` (`--no-stf-filter` per toglierlo); Ballons = `STAT_GIO2` con `RB_TIPOR` in (DF,PK,SI),
valore = `RB_VALORE / TB_CAMBIO` in EUR (note di credito negative), pezzi x `PARTI_JOLLYN1` per `PARTI_G1='SES'`.
