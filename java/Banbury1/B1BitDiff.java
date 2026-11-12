package Banbury1;

import java.util.ArrayList;
import java.util.List;

/** Differenze tra due letture di un DB, a livello di parola e di bit (per mappare le lampade). */
public final class B1BitDiff {

    public static final class Change {
        public final int db;
        public final int off;
        public final int before;
        public final int after;
        public final List<Integer> setBits = new ArrayList<>();
        public final List<Integer> clearedBits = new ArrayList<>();

        Change(int db, int off, int before, int after) {
            this.db = db;
            this.off = off;
            this.before = before;
            this.after = after;
            int x = (before ^ after) & 0xFFFF;
            for (int b = 0; b < 16; b++) {
                if ((x & (1 << b)) != 0) {
                    if ((after & (1 << b)) != 0) {
                        setBits.add(b);
                    } else {
                        clearedBits.add(b);
                    }
                }
            }
        }

        /** Es. "DBW22 0x0C02 -> 0x0C22  acceso: bit5". */
        public String describe() {
            StringBuilder sb = new StringBuilder(String.format("DB%d.DBW%d  0x%04X -> 0x%04X", db, off,
                    before & 0xFFFF, after & 0xFFFF));
            if (!setBits.isEmpty()) {
                sb.append("   acceso: ").append(join(setBits));
            }
            if (!clearedBits.isEmpty()) {
                sb.append("   spento: ").append(join(clearedBits));
            }
            return sb.toString();
        }

        private static String join(List<Integer> bits) {
            StringBuilder sb = new StringBuilder();
            for (int b : bits) {
                sb.append(sb.length() == 0 ? "" : ", ").append("bit").append(b);
            }
            return sb.toString();
        }
    }

    private B1BitDiff() {
    }

    /** Parole (a offset pari) diverse tra a e b. Se le lunghezze differiscono si confronta la parte comune. */
    public static List<Change> diff(int db, byte[] a, byte[] b) {
        List<Change> out = new ArrayList<>();
        if (a == null || b == null) {
            return out;
        }
        int n = Math.min(a.length, b.length);
        for (int off = 0; off + 1 < n; off += 2) {
            int va = S7ReadClient.int16(a, off) & 0xFFFF;
            int vb = S7ReadClient.int16(b, off) & 0xFFFF;
            if (va != vb) {
                out.add(new Change(db, off, va, vb));
            }
        }
        return out;
    }
}
