package Banbury1;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Client S7 (ISO-on-TCP, porta 102) minimale e di SOLA LETTURA per i PLC
 * Siemens S7-300/400 (es. IM 151-8 F-CPU PN/DP di Banbury 1).
 *
 * Volutamente NON espone alcuna funzione di scrittura: la CPU e' una F-CPU
 * (di sicurezza) e in questa fase si leggono soltanto i DB di processo.
 */
public class S7ReadClient implements Closeable {

    private static final int AREA_DB = 0x84;
    /** PDU richiesta; la CPU rispondera' con il valore negoziato. */
    private static final int PDU_REQUESTED = 480;

    private final String host;
    private final int port;
    private final int rack;
    private final int slot;
    private final int timeoutMs;

    private Socket socket;
    private DataInputStream in;
    private OutputStream out;
    private int pduSize = 240;
    private int pduRef = 0;

    public S7ReadClient(String host, int rack, int slot) {
        this(host, 102, rack, slot, 3000);
    }

    public S7ReadClient(String host, int port, int rack, int slot, int timeoutMs) {
        this.host = host;
        this.port = port;
        this.rack = rack;
        this.slot = slot;
        this.timeoutMs = timeoutMs;
    }

    public synchronized void connect() throws IOException {
        close();
        socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeoutMs);
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        in = new DataInputStream(socket.getInputStream());
        out = socket.getOutputStream();

        // COTP connection request: TSAP locale 0x0100, remoto 0x01 + rack/slot
        int remote = (rack << 5) | slot;
        byte[] cr = {
                3, 0, 0, 22,
                17, (byte) 0xE0, 0, 0, 0, 1, 0,
                (byte) 0xC0, 1, 0x0A,
                (byte) 0xC1, 2, 0x01, 0x00,
                (byte) 0xC2, 2, 0x01, (byte) remote
        };
        out.write(cr);
        out.flush();
        byte[] cc = readTpkt();
        if (cc.length < 6 || (cc[5] & 0xFF) != 0xD0) {
            throw new IOException("Connessione ISO rifiutata (rack=" + rack + ", slot=" + slot + ")");
        }

        // Setup communication (negoziazione PDU)
        byte[] setup = {
                3, 0, 0, 25,
                2, (byte) 0xF0, (byte) 0x80,
                0x32, 1, 0, 0, 4, 0, 0, 8, 0, 0,
                (byte) 0xF0, 0, 0, 1, 0, 1,
                (byte) (PDU_REQUESTED >> 8), (byte) (PDU_REQUESTED & 0xFF)
        };
        out.write(setup);
        out.flush();
        byte[] r = readTpkt();
        if (r.length < 27 || r[17] != 0 || r[18] != 0) {
            throw new IOException("Negoziazione PDU rifiutata dalla CPU");
        }
        pduSize = ((r[25] & 0xFF) << 8) | (r[26] & 0xFF);
        if (pduSize < 120) {
            pduSize = 240;
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /** Legge {@code size} byte da DBn a partire da {@code start}. */
    public synchronized byte[] readDb(int db, int start, int size) throws IOException {
        if (!isConnected()) {
            throw new IOException("Non connesso");
        }
        if (db < 1 || db > 65535 || start < 0 || size < 1) {
            throw new IllegalArgumentException("Parametri DB non validi");
        }
        byte[] result = new byte[size];
        int maxChunk = Math.min(pduSize - 18, 960);
        int done = 0;
        while (done < size) {
            int chunk = Math.min(maxChunk, size - done);
            byte[] part = readChunk(db, start + done, chunk);
            System.arraycopy(part, 0, result, done, chunk);
            done += chunk;
        }
        return result;
    }

    private byte[] readChunk(int db, int start, int len) throws IOException {
        int ref = ++pduRef & 0xFFFF;
        int bitAddr = start * 8;
        byte[] req = {
                3, 0, 0, 31,
                2, (byte) 0xF0, (byte) 0x80,
                0x32, 1, 0, 0, (byte) (ref >> 8), (byte) ref, 0, 14, 0, 0,
                4, 1,
                0x12, 0x0A, 0x10, 0x02,
                (byte) (len >> 8), (byte) len,
                (byte) (db >> 8), (byte) db,
                (byte) AREA_DB,
                (byte) (bitAddr >> 16), (byte) (bitAddr >> 8), (byte) bitAddr
        };
        out.write(req);
        out.flush();
        byte[] r = readTpkt();
        if (r.length < 25) {
            throw new IOException("Risposta S7 troppo corta");
        }
        if (r[17] != 0 || r[18] != 0) {
            throw new IOException(String.format(
                    "Errore S7 (classe 0x%02X, codice 0x%02X)", r[17] & 0xFF, r[18] & 0xFF));
        }
        int rc = r[21] & 0xFF;
        if (rc != 0xFF) {
            throw new IOException(describeReturnCode(rc, db, start, len));
        }
        if (r.length < 25 + len) {
            throw new IOException("Dati S7 incompleti");
        }
        byte[] data = new byte[len];
        System.arraycopy(r, 25, data, 0, len);
        return data;
    }

    private static String describeReturnCode(int rc, int db, int start, int len) {
        String what;
        switch (rc) {
            case 0x05: what = "indirizzo non valido (DB troppo corto?)"; break;
            case 0x0A: what = "oggetto inesistente (DB assente nella CPU)"; break;
            case 0x03: what = "accesso negato (protezione/DB ottimizzato)"; break;
            default: what = "codice 0x" + Integer.toHexString(rc);
        }
        return "Lettura DB" + db + "[" + start + ".." + (start + len - 1) + "] rifiutata: " + what;
    }

    private byte[] readTpkt() throws IOException {
        byte[] head = new byte[4];
        in.readFully(head);
        if (head[0] != 3) {
            throw new IOException("Risposta non TPKT");
        }
        int total = ((head[2] & 0xFF) << 8) | (head[3] & 0xFF);
        if (total < 7 || total > 4096) {
            throw new IOException("Lunghezza TPKT non valida: " + total);
        }
        byte[] all = new byte[total];
        System.arraycopy(head, 0, all, 0, 4);
        in.readFully(all, 4, total - 4);
        return all;
    }

    @Override
    public synchronized void close() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        } finally {
            socket = null;
            in = null;
            out = null;
        }
    }

    // --- decodifica big-endian (S7) ---
    public static int int16(byte[] b, int off) {
        return (short) (((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF));
    }

    public static boolean bit(byte[] b, int byteOff, int bit) {
        return (b[byteOff] & (1 << bit)) != 0;
    }
}
