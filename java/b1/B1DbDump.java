import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Strumento di SOLA LETTURA per mappare i DB di Banbury 1.
 * Si lancia accanto al Runtime WinCC: stampa i DB una volta, poi ogni
 * variazione, cosi' si associa ogni indirizzo al valore mostrato dal sinottico.
 *
 * Uso: java B1DbDump <ip> [-r rack] [-s slot] [-p porta] [-i ms] [--once] [DB...]
 * Default: rack 0, slot 2, DB110, ciclo 500 ms.
 */
public class B1DbDump {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Uso: java B1DbDump <ip> [-r rack] [-s slot] [-p porta] [-i ms] [--once] [DB...]");
            System.exit(2);
        }
        String host = args[0];
        int rack = 0, slot = 2, port = 102, interval = 500;
        boolean once = false;
        List<Integer> dbs = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "-r": rack = Integer.parseInt(args[++i]); break;
                case "-s": slot = Integer.parseInt(args[++i]); break;
                case "-p": port = Integer.parseInt(args[++i]); break;
                case "-i": interval = Integer.parseInt(args[++i]); break;
                case "--once": once = true; break;
                default: dbs.add(Integer.parseInt(args[i].replaceFirst("(?i)^db", "")));
            }
        }
        if (dbs.isEmpty()) {
            dbs.add(110);
        }

        // Dimensioni note dal DB110 esportato (256 byte); gli altri DB: 256 di default.
        int[] sizes = new int[dbs.size()];
        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = dbs.get(i) == 110 ? 256 : 256;
        }

        try (S7ReadClient plc = new S7ReadClient(host, port, rack, slot, 3000)) {
            plc.connect();
            System.out.println("# Connesso a " + host + " rack " + rack + " slot " + slot + " (SOLA LETTURA)");

            byte[][] prev = new byte[dbs.size()][];
            boolean first = true;
            while (true) {
                for (int k = 0; k < dbs.size(); k++) {
                    int db = dbs.get(k);
                    byte[] cur;
                    try {
                        cur = plc.readDb(db, 0, sizes[k]);
                    } catch (java.io.IOException e) {
                        String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                        System.out.println("# DB" + db + ": " + msg);
                        if (!msg.startsWith("Lettura DB")) {
                            // connessione persa: riconnette e rilegge da zero
                            reconnect(plc);
                            first = true;
                            break;
                        }
                        continue;
                    }
                    if (first) {
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
        }
    }

    private static void reconnect(S7ReadClient plc) throws InterruptedException {
        while (true) {
            try {
                Thread.sleep(2000);
                plc.connect();
                System.out.println("# Riconnesso");
                return;
            } catch (java.io.IOException e) {
                System.out.println("# Riconnessione fallita: " + e.getMessage());
            }
        }
    }

    private static void printAll(int db, byte[] d) {
        System.out.println("# --- DB" + db + " (" + d.length + " byte) ---");
        for (int off = 0; off + 1 < d.length; off += 2) {
            int v = S7ReadClient.int16(d, off);
            if (v != 0) {
                System.out.println(format(db, off, v));
            }
        }
        System.out.println("# (stampate solo le parole diverse da 0; poi appaiono le variazioni)");
    }

    private static void printChanges(int db, byte[] a, byte[] b) {
        for (int off = 0; off + 1 < b.length; off += 2) {
            int va = S7ReadClient.int16(a, off);
            int vb = S7ReadClient.int16(b, off);
            if (va != vb) {
                System.out.println(LocalTime.now().format(TS) + "  " + format(db, off, vb)
                        + "   (era " + va + ")");
            }
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
            if (i == 8) sb.append('_');
        }
        return sb.toString();
    }
}
