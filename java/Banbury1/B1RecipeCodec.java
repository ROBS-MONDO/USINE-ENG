package Banbury1;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Conversione ricetta <-> immagine del DB201 (big-endian, come S7). Nessuna scrittura sul PLC. */
public final class B1RecipeCodec {

    private B1RecipeCodec() {
    }

    public static B1Recipe decode(byte[] d, B1RecipeLayout l) {
        B1Recipe r = B1Recipe.blank(l);
        r.recipeNo = word(d, l.recipeNoOffset, false);
        r.name = readString(d, l.nameOffset);
        for (B1RecipeLayout.Field f : l.header) {
            r.header.put(f.key, word(d, f.offset, f.unsigned));
        }
        for (int i = 0; i < l.phaseCount; i++) {
            int base = l.phaseBase + i * l.phaseSize;
            B1Recipe.Phase p = r.phases.get(i);
            for (B1RecipeLayout.Field f : l.phase) {
                p.values.put(f.key, word(d, base + f.offset, f.unsigned));
            }
            p.ops = word(d, base + l.opsOffset, true);
        }
        return r;
    }

    /**
     * Scrive la ricetta in una COPIA di {@code template} (lasciando intatti i byte non descritti)
     * e la restituisce. Serve per il futuro invio al PLC e per i test di andata/ritorno.
     */
    public static byte[] encode(B1Recipe r, B1RecipeLayout l, byte[] template) {
        byte[] d = template.clone();
        putWord(d, l.recipeNoOffset, r.recipeNo);
        writeString(d, l.nameOffset, l.nameMax, r.name);
        for (B1RecipeLayout.Field f : l.header) {
            putWord(d, f.offset, r.header.getOrDefault(f.key, 0));
        }
        for (int i = 0; i < l.phaseCount && i < r.phases.size(); i++) {
            int base = l.phaseBase + i * l.phaseSize;
            B1Recipe.Phase p = r.phases.get(i);
            for (B1RecipeLayout.Field f : l.phase) {
                putWord(d, base + f.offset, p.values.getOrDefault(f.key, 0));
            }
            putWord(d, base + l.opsOffset, p.ops);
        }
        return d;
    }

    /** Controlli di validita' (messaggi come nel WinCC). Lista vuota = ricetta valida. */
    public static List<String> validate(B1Recipe r, B1RecipeLayout l) {
        List<String> err = new ArrayList<>();
        String n = r.name == null ? "" : r.name.trim();
        if (n.isEmpty() || n.length() > l.nameMax || !n.chars().allMatch(c -> c >= 32 && c < 127)) {
            err.add("Nome ricetta non valido (1-" + l.nameMax + " caratteri ASCII)");
        }
        boolean discharge = false;
        int used = 0;
        for (int i = 0; i < r.phases.size(); i++) {
            B1Recipe.Phase p = r.phases.get(i);
            if (p.isEmpty()) {
                continue;
            }
            used++;
            boolean isDischarge = (p.ops & (1 << l.dischargeBit)) != 0;
            if (p.ops == 0) {
                err.add("Fase " + (i + 1) + " non valida: nessuna operazione selezionata");
            }
            for (B1RecipeLayout.Field f : l.phase) {
                if (f.min == null && f.max == null) {
                    continue;
                }
                double v = p.values.getOrDefault(f.key, 0) * f.scale;
                if ((f.min != null && v < f.min) || (f.max != null && v > f.max)) {
                    err.add("Fase " + (i + 1) + ": " + f.label + " fuori limiti (" + v + ")");
                }
            }
            if (isDischarge) {
                discharge = true;
                break; // le fasi dopo lo scarico sono residui e non vengono eseguite
            }
        }
        if (used == 0) {
            err.add("Ricetta vuota: nessuna fase");
        } else if (!discharge) {
            err.add("Ricetta non valida: nessuna fase di scarico");
        }
        return err;
    }

    /**
     * Due ricette sono equivalenti per la macchina se hanno lo stesso nome (senza maiuscole),
     * la stessa testata e le stesse fasi fino alla fase di scarico compresa (le successive sono residui).
     */
    public static boolean sameExecutable(B1Recipe a, B1Recipe b, B1RecipeLayout l) {
        if (!a.name.trim().equalsIgnoreCase(b.name.trim()) || !a.header.equals(b.header)) {
            return false;
        }
        for (int i = 0; i < l.phaseCount; i++) {
            B1Recipe.Phase pa = a.phases.get(i);
            B1Recipe.Phase pb = b.phases.get(i);
            if (pa.ops != pb.ops || !pa.values.equals(pb.values)) {
                return false;
            }
            if ((pa.ops & (1 << l.dischargeBit)) != 0) {
                return true;
            }
        }
        return true;
    }

    // ---- primitive ----
    static int word(byte[] d, int off, boolean unsigned) {
        if (off < 0 || off + 2 > d.length) {
            return 0;
        }
        int v = ((d[off] & 0xFF) << 8) | (d[off + 1] & 0xFF);
        return unsigned ? v : (short) v;
    }

    static void putWord(byte[] d, int off, int v) {
        if (off < 0 || off + 2 > d.length) {
            return;
        }
        d[off] = (byte) (v >> 8);
        d[off + 1] = (byte) v;
    }

    static String readString(byte[] d, int off) {
        if (off < 0 || off + 2 > d.length) {
            return "";
        }
        int max = d[off] & 0xFF;
        int len = Math.min(d[off + 1] & 0xFF, Math.min(max, d.length - off - 2));
        return new String(d, off + 2, Math.max(0, len), StandardCharsets.ISO_8859_1).trim();
    }

    static void writeString(byte[] d, int off, int max, String s) {
        byte[] b = (s == null ? "" : s.trim()).getBytes(StandardCharsets.ISO_8859_1);
        int len = Math.min(b.length, Math.min(max, d.length - off - 2));
        d[off] = (byte) max;
        d[off + 1] = (byte) len;
        for (int i = 0; i < max && off + 2 + i < d.length; i++) {
            d[off + 2 + i] = i < len ? b[i] : 0;
        }
    }
}
