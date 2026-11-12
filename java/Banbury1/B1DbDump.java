package Banbury1;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Strumento di SOLA LETTURA per mappare i DB di Banbury 1.
 * Si lancia accanto al Runtime WinCC: stampa i DB una volta, poi ogni
 * variazione, cosi' si associa ogni indirizzo al valore mostrato dal sinottico.
 *
 * Uso: java Banbury1.B1DbDump <ip> [-r rack] [-s slot] [-p porta] [-i ms]
 *                              [-o file.txt] [--once] [DB...]
 * Default: rack 0, slot 2, DB110, ciclo 500 ms.
 *
 * Note: ogni riga scritta nella console (e invio) viene registrata con l'ora,
 * es. "porta carico aperta" oppure "Runtime: temp 93 rpm 33".
 */
public class B1DbDump {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int MAX_DB_SIZE = 1024;

    private static PrintWriter file;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Uso: java Banbury1.B1DbDump <ip> [-r rack] [-s slot] [-p porta] [-i ms] [-o file.txt] [--once] [DB...]");
            System.exit(2);
        }
        String host = args[0];
        int rack = 0, slot = 2, port = 102, interval = 500;
        boolean once = false;
        String outFile = null;
        List<Integer> dbs = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "-r": rack = Integer.parseInt(args[++i]); break;
                case "-s": slot = Integer.parseInt(args[++i]); break;
                case "-p": port = Integer.parseInt(args[++i]); break;
                case "-i": interval = Integer.parseInt(args[++i]); break;
                case "-o": outFile = args[++i]; break;
                case "--once": once = true; break;
                default: {
                    // tollera "DB201", "db201", "DB201." ecc.
                    String digits = args[i].replaceAll("[^0-9]", "");
                    if (digits.isEmpty()) {
                        System.err.println("Argomento non valido: " + args[i]);
                        System.exit(2);
                    }
                    dbs.add(Integer.parseInt(digits));
                }
            }
        }
        if (dbs.isEmpty()) {
            dbs.add(110);
        }
        if (outFile != null) {
            file = new PrintWriter(new FileWriter(outFile, true), true);
            println("# Log su file: " + outFile);
        }
        if (!once) {
            startNoteReader();
        }

        try (S7ReadClient plc = new S7ReadClient(host, port, rack, slot, 3000)) {
            plc.connect();
            println("# Connesso a " + host + " rack " + rack + " slot " + slot + " (SOLA LETTURA)");

            int[] sizes = new int[dbs.size()];
            for (int i = 0; i < sizes.length; i++) {
                sizes[i] = detectSize(plc, dbs.get(i));
                println("# DB" + dbs.get(i) + ": " + (sizes[i] > 0
                        ? sizes[i] + " byte leggibili" : "NON leggibile (assente o protetto)"));
            }

            byte[][] prev = new byte[dbs.size()][];
            boolean first = true;
            while (true) {
                for (int k = 0; k < dbs.size(); k++) {
                    if (sizes[k] <= 0) {
                        continue;
                    }
                    int db = dbs.get(k);
                    byte[] cur;
                    try {
                        cur = plc.readDb(db, 0, sizes[k]);
                    } catch (IOException e) {
                        String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                        println("# DB" + db + ": " + msg);
                        if (!msg.startsWith("Lettura DB")) {
                            reconnect(plc);
                            first = true;
                            break;
                        }
                        continue;
                    }
                    if (first || prev[k] == null) {
                        printAll(db, cur);
                    } else {
                        printChanges(db, prev[k], cur);
                    }
                    prev[k] = cur;
                }
                first = false;
                if (once) {
                    return;
                }
                Thread.sleep(interval);
            }
        } finally {
            if (file != null) {
                file.close();
            }
        }
    }

    private static int detectSize(S7ReadClient plc, int db) throws IOException {
        return plc.detectDbSize(db, MAX_DB_SIZE);
    }

    private static void startNoteReader() {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        println(LocalTime.now().format(TS) + "  >>> NOTA: " + line.trim());
                    }
                }
            } catch (IOException ignored) {
            }
        }, "note-reader");
        t.setDaemon(true);
        t.start();
    }

    private static void reconnect(S7ReadClient plc) throws InterruptedException {
        while (true) {
            try {
                Thread.sleep(2000);
                plc.connect();
                println("# Riconnesso");
                return;
            } catch (IOException e) {
                println("# Riconnessione fallita: " + e.getMessage());
            }
        }
    }

    private static void printAll(int db, byte[] d) {
        println("# --- DB" + db + " (" + d.length + " byte) ---");
        for (int off = 0; off + 1 < d.length; off += 2) {
            int v = S7ReadClient.int16(d, off);
            if (v != 0) {
                println(format(db, off, v));
            }
        }
        println("# (stampate solo le parole diverse da 0; poi appaiono le variazioni)");
    }

    private static void printChanges(int db, byte[] a, byte[] b) {
        for (int off = 0; off + 1 < b.length; off += 2) {
            int va = S7ReadClient.int16(a, off);
            int vb = S7ReadClient.int16(b, off);
            if (va != vb) {
                println(LocalTime.now().format(TS) + "  " + format(db, off, vb)
                        + "   (era " + va + ")");
            }
        }
    }

    private static synchronized void println(String s) {
        System.out.println(s);
        if (file != null) {
            file.println(s);
        }
    }

    private static String format(int db, int off, int v) {
        return String.format("DB%d.DBW%-4d = %7d   0x%04X   %s",
                db, off, v, v & 0xFFFF, bits(v));
    }

    private static String bits(int v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 15; i >= 0; i--) {
            sb.append(((v >> i) & 1) != 0 ? '1' : '0');
            if (i == 8) {
                sb.append('_');
            }
        }
        return sb.toString();
    }
}
