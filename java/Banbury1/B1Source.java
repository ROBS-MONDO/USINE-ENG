package Banbury1;

/** Sorgente dati del sinottico: PLC reale o demo. Ogni getter ritorna null se non disponibile. */
public interface B1Source extends AutoCloseable {
    Double num(String key);

    Boolean bool(String key);

    String str(String key);

    /** Indirizzo configurato per la chiave ("?" se non collegata): per l'overlay F2. */
    String spec(String key);

    /** Copia dell'ultima immagine letta del DB (null se non disponibile). */
    default byte[] dbImage(int db) {
        return null;
    }

    boolean connected();

    String status();

    @Override
    void close();
}
