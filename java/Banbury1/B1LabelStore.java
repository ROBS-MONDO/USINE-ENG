package Banbury1;

/** Dove vengono registrati i dati letti dal ticket (database SQL di Banbury 1). */
public interface B1LabelStore {

    /** true = nessuna scrittura reale (configurazione assente o non abilitata). */
    boolean dryRun();

    /** Testo leggibile dell'istruzione SQL e dei parametri che verranno usati. */
    String describe(B1Label label);

    void store(B1Label label) throws Exception;
}
