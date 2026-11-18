package Banbury1;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JOptionPane;

/**
 * Pagina "Recettes" di B1 (come la 006_Recipes di WinCC): elenco archivio, testata della ricetta, selezione della fase in modifica e griglia
 * delle 10 fasi con le spie delle operazioni. Disegna nelle coordinate base 1024x768 della schermata; la logica (archivio, validazione,
 * importazione dal PLC, invio protetto) e' quella dell'editor ricette (EditorPanel, non visibile). La scrittura nel PLC resta solo "Envoyer au PLC".
 */
public final class B1RecipePage {

    static final int[] COLS_X = {46, 416, 474, 532, 600, 632, 664, 700, 732, 788, 846, 914, 946};
    static final String[] OP_NAMES = {"Gomme", "Add.", "All Black", "Plastif.", "Cerac.", "Silano", "Half Black", "Degas", "Net.", "D\u00e9ch."};
    static final int[] OP_BITS = {0, 1, 2, 3, 4, 5, 6, 13, 14, 15};
    static final int GRID_Y = 282, ROW_H = 31, ROWS = 10;
    static final int BIT_DOOR = 0x02, BIT_UP = 0x04, BIT_DOWN = 0x10, CT_AND = 0x01, CT_OR = 0x02, CT_SKIP = 0x8000;

    private static final String[] HK = {"safety_time", "max_energy", "restart_temp", "safety_temp", "drop_door"};
    private static final String[] HL = {"TEMPS S\u00c9CURIT\u00c9", "\u00c9NERGIE MAX", "TEMP. RED\u00c9MARRAGE", "TEMP. S\u00c9CURIT\u00c9", "PORTE D\u00c9CHARG."};

    private final B1RecipeEditor.EditorPanel ed;
    private final B1Source src;
    private final Supplier<Component> parent;
    private final Runnable repaint;
    private int phase; // fase in modifica 0..9
    private volatile String message = "";
    private volatile long messageUntil;

    public B1RecipePage(B1RecipeRepository repo, B1RecipeLayout layout, String plcIp, B1Source src, Supplier<Component> parent,
                        Runnable repaint) {
        this.ed = new B1RecipeEditor.EditorPanel(null, repo, layout, plcIp);
        this.src = src;
        this.parent = parent;
        this.repaint = repaint;
        ed.refreshList();
    }

    // ---------------------------------------------------------------- ingresso da altre pagine

    /** Seleziona la ricetta (o la importa dal PLC se manca). */
    public void select(String name) {
        if (name == null || name.trim().isEmpty()) {
            boolean blank = ed.current == null || !ed.dirty && ed.current.revision == 0 && ed.current.name.isEmpty();
            if (blank) {
                String rn = src != null && src.connected() ? src.str("recipe_name") : null;
                if (rn != null && !rn.trim().isEmpty()) {
                    ed.selectByName(B1RecipeBridge.archiveName(rn));
                } else {
                    ed.autoImportIfEmpty();
                }
            }
        } else {
            ed.selectByName(name);
        }
        repaint.run();
    }

    /** Download da una produzione: seleziona e apre l'anteprima di invio. */
    public void selectAndSend(String name, Integer quantity) {
        ed.selectAndSend(name, quantity);
        repaint.run();
    }

    B1Recipe recipe() {
        return ed.current;
    }

    boolean dirty() {
        return ed.dirty;
    }

    int phase() {
        return phase;
    }

    private void say(String m) {
        message = m;
        messageUntil = System.currentTimeMillis() + 4000;
        repaint.run();
    }

    // ---------------------------------------------------------------- campi

    private B1RecipeLayout.Field hf(String key) {
        for (B1RecipeLayout.Field f : ed.layout.header) {
            if (f.key.equals(key)) {
                return f;
            }
        }
        return null;
    }

    private B1RecipeLayout.Field pf(String key) {
        for (B1RecipeLayout.Field f : ed.layout.phase) {
            if (f.key.equals(key)) {
                return f;
            }
        }
        return null;
    }

    private B1Recipe.Phase ph() {
        B1Recipe r = ed.current;
        return r == null || phase >= r.phases.size() ? null : r.phases.get(phase);
    }

    private int pval(B1Recipe.Phase p, String key) {
        Integer v = p.values.get(key);
        return v == null ? 0 : v;
    }

    private String fmt(int raw, B1RecipeLayout.Field f) {
        return f == null ? String.valueOf(raw) : B1RecipeEditor.EditorPanel.fmt(raw, f);
    }

    private static final int[] HX = {10, 150, 290, 430, 570};

    // ---------------------------------------------------------------- disegno

    public void paint(Graphics2D g) {
        B1Recipe r = ed.current;
        // riga 1: elenco ricette, nome, N., data, ENABLED
        label(g, "LISTE DES RECETTES", 10, 70);
        box(g, 10, 86, 330, 28, r == null ? "-- aucune recette --" : B1RecipeBridge.pretty(r.name), -1, true);
        arrow(g, 316, 91, 18, 18);
        label(g, "NOM DE LA RECETTE", 350, 70);
        box(g, 350, 86, 300, 28, r == null ? "" : B1RecipeBridge.pretty(r.name), -1, true);
        label(g, "N\u00b0", 660, 70);
        box(g, 660, 86, 60, 28, r == null ? "" : String.valueOf(r.recipeNo), 0, true);
        label(g, "DATE", 730, 70);
        box(g, 730, 86, 190, 28, r == null || r.modified == null ? "" : r.modified, 0, false);
        label(g, "ENABLED", 930, 70);
        Boolean en = src != null && src.connected() ? src.bool("transfer_enabled") : null;
        lamp(g, 946, 86, 28, Boolean.TRUE.equals(en), new Color(0x3CFF3C), new Color(0x0C4A0C));
        // riga 2: parametri di testata
        for (int i = 0; i < HK.length; i++) {
            B1RecipeLayout.Field f = hf(HK[i]);
            label(g, HL[i], HX[i], 124);
            box(g, HX[i], 138, 130, 26, r == null || f == null ? "" : fmt(r.header.getOrDefault(f.key, 0), f), 1, true);
        }
        label(g, "TEMPS CYCLE", 710, 124);
        box(g, 710, 138, 130, 26, "--", 1, false);
        // riga 3: fase in modifica
        label(g, "PHASE EN \u00c9DITION", 10, 182);
        button(g, 10, 192, 30, 28, "\u25c4");
        box(g, 44, 192, 54, 28, String.valueOf(phase + 1), 0, true);
        button(g, 102, 192, 30, 28, "\u25ba");
        button(g, 150, 192, 120, 28, "Effacer phase");
        button(g, 276, 192, 150, 28, "Copier vers suivante");
        String info = r == null ? "" : ed.lblInfo.getText().trim();
        g.setFont(new Font("Arial", Font.PLAIN, 11));
        g.setColor(new Color(0x303030));
        g.drawString(info, 440, 210);
        drawGrid(g, r);
        // zona controlli / messaggio
        String txt = System.currentTimeMillis() < messageUntil ? message : ed.problemsText();
        boolean ok = txt != null && txt.startsWith("Recette valide");
        g.setColor(ok ? new Color(0xD8F5D8) : new Color(0xFFF4D0));
        g.fillRect(8, 606, 1008, 40);
        g.setColor(new Color(0x808080));
        g.drawRect(8, 606, 1008, 40);
        g.setFont(new Font("Arial", Font.PLAIN, 12));
        g.setColor(new Color(0x202020));
        String[] lines = (txt == null ? "" : txt).split("\n");
        for (int i = 0; i < Math.min(2, lines.length); i++) {
            g.drawString(lines[i], 14, 622 + i * 15);
        }
    }

    private void drawGrid(Graphics2D g, B1Recipe r) {
        // intestazioni di gruppo
        int gy = 232;
        group(g, 46, gy, 360, "OP\u00c9RATION");
        group(g, 416, gy, 272, "CONSIGNE");
        group(g, 700, gy, 276, "CONTR\u00d4LE");
        label(g, "Phase", 8, 268);
        for (int i = 0; i < OP_NAMES.length; i++) {
            head(g, OP_NAMES[i], 46 + i * 36, 256, 36);
        }
        head(g, "Timeout (s)", 416, 256, 56);
        head(g, "Rotor (rpm)", 474, 256, 56);
        head(g, "Press.(bar)", 532, 256, 66);
        head(g, "Porte", 600, 256, 30);
        head(g, "Pist.", 632, 256, 30);
        head(g, "Saut", 700, 256, 30);
        head(g, "Temps (s)", 732, 256, 54);
        head(g, "Temp. (\u00b0C)", 788, 256, 56);
        head(g, "\u00c9n.(kWh)", 846, 256, 66);
        head(g, "ET", 914, 256, 30);
        head(g, "OU", 946, 256, 30);
        for (int i = 0; i < ROWS; i++) {
            int y = GRID_Y + i * ROW_H;
            boolean sel = i == phase;
            Color c0 = sel ? new Color(0xFFE066) : (i % 2 == 0 ? new Color(0xB8C8E4) : new Color(0xA4B6D8));
            Color c1 = sel ? new Color(0xE0B000) : new Color(0x8DA2CC);
            g.setPaint(new GradientPaint(4, y, c0, 4, y + ROW_H - 2, c1));
            g.fillRoundRect(4, y, 984, ROW_H - 2, 6, 6);
            B1Recipe.Phase p = r == null || i >= r.phases.size() ? null : r.phases.get(i);
            box(g, 8, y + 3, 34, 24, String.valueOf(i + 1), 0, true);
            if (p == null) {
                continue;
            }
            int ops = p.ops & 0xFFFF;
            for (int k = 0; k < 10; k++) {
                sq(g, 46 + k * 36 + 6, y + 3, 24, (ops & (1 << OP_BITS[k])) != 0, new Color(0x3CFF3C), new Color(0x0C4A0C));
            }
            box(g, 416, y + 3, 56, 24, fmt(pval(p, "timeout"), pf("timeout")), 1, false);
            box(g, 474, y + 3, 56, 24, fmt(pval(p, "rpm"), pf("rpm")), 1, false);
            box(g, 532, y + 3, 66, 24, fmt(pval(p, "press"), pf("press")), 1, false);
            int bits = pval(p, "set_bits") & 0xFFFF;
            sq(g, 603, y + 3, 24, (bits & BIT_DOOR) != 0, new Color(0xFF3A3A), new Color(0x5A1010));
            boolean up = (bits & BIT_UP) != 0, down = (bits & BIT_DOWN) != 0;
            tri(g, 636, y + 2, up, true);
            tri(g, 636, y + 16, down, false);
            int ctrl = pval(p, "ctrl_bits") & 0xFFFF;
            sq(g, 703, y + 3, 24, (ctrl & CT_SKIP) != 0, new Color(0xE0E0E0), new Color(0x404048));
            box(g, 732, y + 3, 54, 24, fmt(pval(p, "time"), pf("time")), 1, false);
            box(g, 788, y + 3, 56, 24, fmt(pval(p, "temp"), pf("temp")), 1, false);
            box(g, 846, y + 3, 66, 24, fmt(pval(p, "energy"), pf("energy")), 1, false);
            sq(g, 917, y + 3, 24, (ctrl & CT_AND) != 0, new Color(0x40A0FF), new Color(0x103060));
            sq(g, 949, y + 3, 24, (ctrl & CT_OR) != 0, new Color(0x40A0FF), new Color(0x103060));
        }
    }

    // ---------------------------------------------------------------- clic

    /** Gestisce il clic (coordinate base); true se la pagina l'ha usato. */
    public boolean click(int x, int y) {
        B1Recipe r = ed.current;
        if (in(x, y, 10, 86, 330, 28)) {
            chooseRecipe();
            return true;
        }
        if (r != null && in(x, y, 350, 86, 300, 28)) {
            String s = ask("Nom de la recette", B1RecipeBridge.pretty(r.name));
            if (s != null) {
                r.name = s.trim().replace('\u00b0', 'o');
                ed.edited();
            }
            return true;
        }
        if (r != null && in(x, y, 660, 86, 60, 28)) {
            String s = ask("N\u00b0 de la recette", String.valueOf(r.recipeNo));
            if (s != null) {
                try {
                    r.recipeNo = Integer.parseInt(s.trim());
                    ed.edited();
                } catch (NumberFormatException e) {
                    say("N\u00b0 non valide : " + s);
                }
            }
            return true;
        }
        for (int i = 0; r != null && i < HK.length; i++) {
            B1RecipeLayout.Field f = hf(HK[i]);
            if (f != null && in(x, y, HX[i], 138, 130, 26)) {
                editHeader(r, f, HL[i]);
                return true;
            }
        }
        if (in(x, y, 10, 192, 30, 28)) {
            phase = (phase + ROWS - 1) % ROWS;
            repaint.run();
            return true;
        }
        if (in(x, y, 102, 192, 30, 28)) {
            phase = (phase + 1) % ROWS;
            repaint.run();
            return true;
        }
        if (in(x, y, 150, 192, 120, 28)) {
            ed.clearPhase(phase);
            return true;
        }
        if (in(x, y, 276, 192, 150, 28)) {
            ed.copyPhaseDown(phase);
            return true;
        }
        if (y >= GRID_Y && y < GRID_Y + ROWS * ROW_H && x >= 4 && x < 988) {
            int row = (y - GRID_Y) / ROW_H;
            phase = row;
            if (r != null && row < r.phases.size()) {
                cell(r.phases.get(row), x, y - (GRID_Y + row * ROW_H));
            }
            repaint.run();
            return true;
        }
        return false;
    }

    private void cell(B1Recipe.Phase p, int x, int dy) {
        for (int k = 0; k < 10; k++) {
            if (x >= 46 + k * 36 && x < 46 + (k + 1) * 36) {
                p.ops ^= 1 << OP_BITS[k];
                ed.edited();
                return;
            }
        }
        if (x >= 600 && x < 632) {
            toggle(p, "set_bits", BIT_DOOR, 0);
        } else if (x >= 632 && x < 700) {
            if (dy < ROW_H / 2) {
                toggle(p, "set_bits", BIT_UP, BIT_DOWN);
            } else {
                toggle(p, "set_bits", BIT_DOWN, BIT_UP);
            }
        } else if (x >= 700 && x < 732) {
            toggle(p, "ctrl_bits", CT_SKIP, 0);
        } else if (x >= 914 && x < 946) {
            toggle(p, "ctrl_bits", CT_AND, 0);
        } else if (x >= 946 && x < 978) {
            toggle(p, "ctrl_bits", CT_OR, 0);
        } else if (x >= 416 && x < 474) {
            editPhase(p, "timeout", "Timeout (s)");
        } else if (x >= 474 && x < 532) {
            editPhase(p, "rpm", "Rotor (rpm)");
        } else if (x >= 532 && x < 600) {
            editPhase(p, "press", "Pression (bar)");
        } else if (x >= 732 && x < 788) {
            editPhase(p, "time", "Temps (s)");
        } else if (x >= 788 && x < 846) {
            editPhase(p, "temp", "Temp\u00e9rature (\u00b0C)");
        } else if (x >= 846 && x < 914) {
            editPhase(p, "energy", "\u00c9nergie (kWh)");
        }
    }

    private void toggle(B1Recipe.Phase p, String key, int bit, int clear) {
        int v = pval(p, key) & 0xFFFF;
        v = (v & bit) != 0 ? v & ~bit : (v | bit) & ~clear;
        p.values.put(key, v);
        ed.edited();
    }

    private void editPhase(B1Recipe.Phase p, String key, String title) {
        B1RecipeLayout.Field f = pf(key);
        if (f == null) {
            return;
        }
        String s = ask(title + " - phase " + (phase + 1), fmt(pval(p, key), f));
        if (s != null && parseOk(s)) {
            p.values.put(key, B1RecipeEditor.EditorPanel.toRaw(s, f));
            ed.edited();
        } else if (s != null) {
            say("Valeur non valide : " + s);
        }
    }

    private void editHeader(B1Recipe r, B1RecipeLayout.Field f, String title) {
        String s = ask(title, fmt(r.header.getOrDefault(f.key, 0), f));
        if (s != null && parseOk(s)) {
            r.header.put(f.key, B1RecipeEditor.EditorPanel.toRaw(s, f));
            ed.edited();
        } else if (s != null) {
            say("Valeur non valide : " + s);
        }
    }

    private static boolean parseOk(String s) {
        try {
            Double.parseDouble(s.trim().replace(',', '.'));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String ask(String title, String cur) {
        Object o = JOptionPane.showInputDialog(parent.get(), title, "Recettes", JOptionPane.PLAIN_MESSAGE, null, null, cur);
        return o == null ? null : o.toString();
    }

    private void chooseRecipe() {
        ed.refreshList();
        List<B1RecipeRepository.Entry> l = ed.allEntries;
        if (l.isEmpty()) {
            say("Archive vide : utiliser Importer du PLC");
            return;
        }
        Object o = JOptionPane.showInputDialog(parent.get(), "Liste des recettes", "Recettes", JOptionPane.PLAIN_MESSAGE, null,
                l.toArray(), null);
        if (o instanceof B1RecipeRepository.Entry) {
            ed.openEntry((B1RecipeRepository.Entry) o);
            phase = 0;
        }
        repaint.run();
    }

    // ---------------------------------------------------------------- barra pulsanti

    /** Pulsanti della barra in basso (al posto di Govoni/B2/...). */
    public List<B1BottomBar.Btn> barButtons() {
        List<B1BottomBar.Btn> l = new ArrayList<>();
        l.add(new B1BottomBar.Btn(578, 0, 52, 52, "Nouvelle", () -> run(ed::newRecipe)));
        l.add(new B1BottomBar.Btn(632, 0, 52, 52, "Supprimer", () -> run(ed::deleteRecipe)));
        l.add(new B1BottomBar.Btn(686, 0, 52, 52, "Dupliquer", () -> run(ed::duplicate)));
        l.add(new B1BottomBar.Btn(740, 0, 58, 52, "Enregistrer", () -> run(ed::save)));
        l.add(new B1BottomBar.Btn(800, 0, 58, 52, "Importer PLC", () -> run(ed::importFromPlc)));
        l.add(new B1BottomBar.Btn(860, 0, 34, 52, "CSV", () -> run(ed::exportCsv)));
        l.add(new B1BottomBar.Btn(896, 0, 34, 52, "Envoyer PLC", () -> run(ed::sendToPlc)));
        return l;
    }

    private void run(Runnable r) {
        r.run();
        repaint.run();
    }

    // ---------------------------------------------------------------- primitive grafiche

    private static boolean in(int x, int y, int rx, int ry, int rw, int rh) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }

    private static void bevel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(0x808080));
        g.drawLine(x, y, x + w - 1, y);
        g.drawLine(x, y, x, y + h - 1);
        g.setColor(Color.WHITE);
        g.drawLine(x, y + h - 1, x + w - 1, y + h - 1);
        g.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
    }

    private static void label(Graphics2D g, String t, int x, int y) {
        g.setFont(new Font("Arial", Font.BOLD, 11));
        g.setColor(new Color(0x202020));
        g.drawString(t, x, y + 9);
    }

    private static void fit(Graphics2D g, String text, int x, int y, int w, int h, Color c, int align, boolean bold) {
        int size = Math.max(9, Math.min(15, h - 8));
        Font f;
        FontMetrics fm;
        do {
            f = new Font("Arial", bold ? Font.BOLD : Font.PLAIN, size);
            fm = g.getFontMetrics(f);
            size--;
        } while (fm.stringWidth(text) > w - 6 && size >= 8);
        java.awt.Shape old = g.getClip();
        g.clipRect(x + 1, y + 1, Math.max(1, w - 2), Math.max(1, h - 2));
        g.setFont(f);
        g.setColor(c);
        int tx = align < 0 ? x + 4 : (align > 0 ? x + w - fm.stringWidth(text) - 4 : x + (w - fm.stringWidth(text)) / 2);
        g.drawString(text, tx, y + (h - fm.getHeight()) / 2 + fm.getAscent());
        g.setClip(old);
    }

    private static void box(Graphics2D g, int x, int y, int w, int h, String t, int align, boolean editable) {
        g.setColor(editable ? new Color(0xFFFFFF) : new Color(0xE4E4E4));
        g.fillRect(x, y, w, h);
        bevel(g, x, y, w, h);
        fit(g, t == null ? "" : t, x, y, w - 2, h, Color.BLACK, align, true);
    }

    private static void button(Graphics2D g, int x, int y, int w, int h, String t) {
        g.setColor(new Color(0xD4D0C8));
        g.fillRect(x, y, w, h);
        bevel(g, x, y, w, h);
        fit(g, t, x, y, w, h, Color.BLACK, 0, false);
    }

    private static void group(Graphics2D g, int x, int y, int w, String t) {
        g.setColor(new Color(0xE6E6E6));
        g.fillRect(x, y, w, 22);
        g.setColor(new Color(0x606060));
        g.drawRect(x, y, w, 22);
        fit(g, t, x, y, w, 22, Color.BLACK, 0, true);
    }

    private static void head(Graphics2D g, String t, int x, int y, int w) {
        g.setColor(new Color(0xD4D0C8));
        g.fillRect(x, y, w, 22);
        bevel(g, x, y, w, 22);
        fit(g, t, x, y, w, 22, Color.BLACK, 0, false);
    }

    private static void arrow(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(0xD4D0C8));
        g.fillRect(x, y, w, h);
        bevel(g, x, y, w, h);
        g.setColor(Color.BLACK);
        g.fillPolygon(new int[]{x + 4, x + w - 4, x + w / 2}, new int[]{y + 6, y + 6, y + h - 5}, 3);
    }

    private static void sq(Graphics2D g, int x, int y, int s, boolean lit, Color bright, Color dark) {
        g.setColor(new Color(0x303030));
        g.fillRect(x, y, s, s);
        g.setPaint(lit ? new GradientPaint(x, y, bright, x + s, y + s, bright.darker())
                : new GradientPaint(x, y, dark.brighter(), x + s, y + s, dark));
        g.fillRect(x + 2, y + 2, s - 4, s - 4);
    }

    private static void lamp(Graphics2D g, int x, int y, int s, boolean lit, Color bright, Color dark) {
        sq(g, x, y, s, lit, bright, dark);
    }

    private static void tri(Graphics2D g, int x, int y, boolean lit, boolean up) {
        int[] xs = {x + 12, x + 2, x + 22};
        int[] ys = up ? new int[]{y, y + 13, y + 13} : new int[]{y + 13, y, y};
        g.setColor(lit ? new Color(0x20C020) : new Color(0x50706A));
        g.fillPolygon(xs, ys, 3);
    }
}
