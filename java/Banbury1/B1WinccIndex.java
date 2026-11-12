package Banbury1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Indice "dove usa questo indirizzo il WinCC": per ogni tag DBxDBWn elenca schermata + etichetta
 * vicina (dai layout screens/*.b1s). Serve a riconoscere le parole del DB confrontandole col WinCC.
 */
public final class B1WinccIndex {

    private final Map<String, List<String>> usage = new HashMap<>();

    public B1WinccIndex() {
        List<String> names = new ArrayList<>();
        names.add("001_Synoptic");
        names.add("100_TopBar");
        for (String[] s : B1ScreensWindow.SCREENS) {
            names.add(s[0]);
        }
        for (String n : names) {
            B1Screen sc = B1Screen.load(n);
            if (sc == null) {
                continue;
            }
            for (B1Screen.Obj o : sc.objects) {
                if (!o.tag.startsWith("DB") || !(o.kind.equals("IO") || o.kind.equals("LAMP")
                        || o.kind.equals("COMBO"))) {
                    continue;
                }
                String label = nearestLabel(sc, o);
                String entry = n.replaceFirst("^\\d+_", "") + ": " + (label.isEmpty() ? o.name : label)
                        + (o.kind.equals("LAMP") ? " (lampada)" : "");
                List<String> l = usage.computeIfAbsent(o.tag, k -> new ArrayList<>());
                if (!l.contains(entry)) {
                    l.add(entry);
                }
            }
        }
    }

    /** Per i campi di valore: "SEZIONE \u00b7 etichetta [unita']"; per le lampade l'etichetta vicina. */
    private static String nearestLabel(B1Screen sc, B1Screen.Obj o) {
        if (!o.kind.equals("IO")) {
            return lampLabel(sc, o);
        }
        String left = "";
        String section = "";
        String unit = "";
        int dl = Integer.MAX_VALUE, ds = Integer.MAX_VALUE, du = Integer.MAX_VALUE;
        for (B1Screen.Obj t : sc.objects) {
            String txt = t.text.trim();
            if (!t.kind.equals("TEXT") || txt.isEmpty() || txt.equals("Line speed") || txt.equals("??????")
                    || txt.matches("DB\\d+DB.*") || t.name.startsWith("ST_msg") || txt.length() > 40) {
                continue;
            }
            boolean rowOverlap = t.t < o.b && t.b > o.t;
            if (rowOverlap && t.r <= o.l + 4 && o.l - t.r < 220 && o.l - t.r < dl) {
                dl = o.l - t.r;
                left = txt;
            }
            if (rowOverlap && t.l >= o.r - 4 && t.l - o.r < 50 && t.l - o.r < du && txt.length() <= 8) {
                du = t.l - o.r;
                unit = txt;
            }
            if ("FFFFFF".equals(t.bg) && t.b <= o.t + 2 && t.l < o.r && t.r > o.l && o.t - t.b < ds) {
                ds = o.t - t.b;
                section = txt;
            }
        }
        StringBuilder sb = new StringBuilder();
        if (!section.isEmpty()) {
            sb.append(section).append(" > ");
        }
        sb.append(left.isEmpty() ? o.name : left);
        if (!unit.isEmpty()) {
            sb.append(" [").append(unit).append("]");
        }
        return sb.toString();
    }

    private static String lampLabel(B1Screen sc, B1Screen.Obj o) {
        String best = "";
        int bestD = Integer.MAX_VALUE;
        for (B1Screen.Obj t : sc.objects) {
            if (!t.kind.equals("TEXT")) {
                continue;
            }
            String txt = t.text.trim();
            if (txt.isEmpty() || txt.equals("Line speed") || txt.matches("DB\\d+DB.*") || txt.equals("??????")
                    || t.name.startsWith("ST_msg") || txt.length() > 40) {
                continue;
            }
            boolean sameRow = t.t < o.b && t.b > o.t && t.r <= o.l + 4 && o.l - t.r < 320;
            boolean above = t.b <= o.t + 4 && o.t - t.b < 30 && t.l < o.r && t.r > o.l;
            boolean right = t.t < o.b && t.b > o.t && t.l >= o.r - 4 && t.l - o.r < 260;
            int d = Integer.MAX_VALUE;
            if (sameRow) {
                d = o.l - t.r;
            } else if (above) {
                d = (o.t - t.b) + 40;
            } else if (right && o.kind.equals("LAMP")) {
                d = t.l - o.r;
            }
            if (d < bestD) {
                bestD = d;
                best = txt;
            }
        }
        return best;
    }

    /** Usi WinCC della parola DBWn (o del doppio DBD) e dei suoi bit. */
    public String usageOfWord(int db, int off) {
        List<String> out = new ArrayList<>();
        add(out, "DB" + db + "DBW" + off);
        add(out, "DB" + db + "DBD" + off);
        for (int b = 0; b < 8; b++) {
            add(out, "DB" + db + "DBX" + off + "_" + b);
            add(out, "DB" + db + "DBX" + (off + 1) + "_" + b);
        }
        return String.join("; ", out);
    }

    private void add(List<String> out, String tag) {
        List<String> l = usage.get(tag);
        if (l != null) {
            out.addAll(l);
        }
    }
}
