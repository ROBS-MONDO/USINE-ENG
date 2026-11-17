package Banbury1;

import java.io.IOException;
import java.util.List;

/** Archivio delle produzioni B1 (SQL Server come le ricette, oppure file). Non scrive mai nel PLC. */
public interface B1ProductionRepository {

    /** Produzioni attive (stato 1..3), ordinate per priorita', nome, ID (come sp_GetProductionListWinCC). */
    List<B1Production> list() throws IOException;

    /** Una produzione per ID (qualsiasi stato), null se non esiste. */
    B1Production load(int id) throws IOException;

    /** Salva; id 0 = nuova produzione (assegna il prossimo ID). Restituisce la produzione salvata. */
    B1Production save(B1Production p) throws IOException;

    /** Importazione (da WinCC): inserisce con ID, date e stato originali; false se l'ID esiste gia' (non tocca nulla). */
    boolean importRow(B1Production p) throws IOException;

    /** "Elimina" come WinCC: stato 4 (la produzione resta nell'archivio ma esce dall'elenco). */
    void end(int id) throws IOException;

    /** Segna la produzione in uso (stato 3). */
    void setRunning(int id) throws IOException;

    /** Esiste una produzione attiva con questo nome (escludendo exceptId)? */
    boolean nameExists(String name, int exceptId) throws IOException;

    String location();
}
