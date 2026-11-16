package Banbury1;

import java.io.IOException;

/**
 * Client S7 con scrittura. Separato apposta da S7ReadClient (che resta di SOLA LETTURA): la scrittura si usa solo
 * dal trasferimento ricetta (B1RecipeTransfer), mai dal sinottico o dal registratore. Scrive solo nei DB e negli
 * intervalli indicati dal chiamante, che deve averli verificati.
 */
public final class S7WriteClient extends S7ReadClient {

    private static final int AREA_DB = 0x84;

    public S7WriteClient(String host, int port, int rack, int slot, int timeoutMs) {
        super(host, port, rack, slot, timeoutMs);
    }

    /** Scrive {@code data} in DBn a partire da {@code start} (a blocchi che stanno nel PDU negoziato). */
    public synchronized void writeDb(int db, int start, byte[] data) throws IOException {
        if (!isConnected()) {
            throw new IOException("Non connesso");
        }
        int max = Math.max(16, pduSize - 35);
        int done = 0;
        while (done < data.length) {
            int n = Math.min(max, data.length - done);
            byte[] chunk = new byte[n];
            System.arraycopy(data, done, chunk, 0, n);
            writeChunk(db, start + done, chunk);
            done += n;
        }
    }

    private void writeChunk(int db, int start, byte[] chunk) throws IOException {
        int n = chunk.length;
        int ref = ++pduRef & 0xFFFF;
        int bitAddr = start * 8;
        int total = 35 + n;
        int dataLen = n + 4;
        byte[] req = new byte[total];
        byte[] head = {
                3, 0, (byte) (total >> 8), (byte) total,
                2, (byte) 0xF0, (byte) 0x80,
                0x32, 1, 0, 0, (byte) (ref >> 8), (byte) ref, 0, 14, (byte) (dataLen >> 8), (byte) dataLen,
                5, 1,
                0x12, 0x0A, 0x10, 0x02,
                (byte) (n >> 8), (byte) n,
                (byte) (db >> 8), (byte) db,
                (byte) AREA_DB,
                (byte) (bitAddr >> 16), (byte) (bitAddr >> 8), (byte) bitAddr,
                0, 4, (byte) ((n * 8) >> 8), (byte) (n * 8)
        };
        System.arraycopy(head, 0, req, 0, head.length);
        System.arraycopy(chunk, 0, req, head.length, n);
        out.write(req);
        out.flush();
        byte[] r = readTpkt();
        if (r.length < 22) {
            throw new IOException("Risposta S7 troppo corta");
        }
        if (r[17] != 0 || r[18] != 0) {
            throw new IOException(String.format("Errore S7 in scrittura (classe 0x%02X, codice 0x%02X)",
                    r[17] & 0xFF, r[18] & 0xFF));
        }
        int rc = r[21] & 0xFF;
        if (rc != 0xFF) {
            throw new IOException("Scrittura DB" + db + "[" + start + ".." + (start + n - 1) + "] rifiutata dalla CPU (codice 0x"
                    + Integer.toHexString(rc) + ")");
        }
    }
}
