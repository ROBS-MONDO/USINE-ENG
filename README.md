# USINE-ENG

Classi Java per gli impianti di produzione (mescolatore Banbury, bilancia, report).

## Nota

`java/ProdMelBanb.java` referenzia la classe `ProdMelBanbBalanceSupport`
(gestione bilancia), che al momento non fa parte di questo repository e va
aggiunta separatamente per poter compilare il progetto.

## SinotticoChargeSilo

`java/SinotticoChargeSilo.java` (package `GOV`) ridisegna il sinottico
"SYNOPTIQUE 1: CHARGE SILO" (12 silos, tramoggia EV10/EV20/EV30, pannello ciclo,
barra comandi). Va copiato in `src/main/java/GOV` del progetto GOVONI_DEV e
collegato in `GovoniActionRouter.openChargeSilo()`:

```java
public void openChargeSilo() { new SinotticoChargeSilo().setVisible(true); }
```

Stato aggiornabile via `setSilo`, `setHopper`, `setCycle`, `setMessage`, `setUser`
(da collegare ai word sinottici `AddressBook.synopticInputWord/OutputWord`).
Cliccando un silo si simula il cambio livello (vuoto -> LSL -> LSH).
