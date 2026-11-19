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
    static final int GRID_Y = 280, ROW_H = 29, ROWS = 10;
    static final int BIT_DOOR = 0x02, BIT_UP = 0x04, BIT_DOWN = 0x10, CT_AND = 0x01, CT_OR = 0x02, CT_SKIP = 0x8000;

    private static final String[] HK = {"safety_time", "max_energy", "restart_temp", "safety_temp", "drop_door"};
    private static final String[] HL = {"TEMPS S\u00c9CURIT\u00c9", "\u00c9NERGIE MAX", "TEMP. RED\u00c9MARRAGE", "TEMP. S\u00c9CURIT\u00c9", "PORTE D\u00c9CHARG."};

    private final B1RecipeEditor.EditorPanel ed;
    private final B1Source src;
    private final Supplier<Component> parent;
    private final Runnable repaint;
    private int phase; // fase in modifica 0..9
    private final B1Recipe.Phase buf = new B1Recipe.Phase();
    private boolean onBuf;
    private volatile String message = "";
    private volatile long messageUntil;

    public B1RecipePage(B1RecipeRepository repo, B1RecipeLayout layout, String plcIp, B1Source src, Supplier<Component> parent,
                        Runnable repaint) {
        this.ed = new B1RecipeEditor.EditorPanel(null, repo, layout, plcIp);
        this.src = src;
        this.parent = parent;
        this.repaint = repaint;
        ed.refreshList();
        buf.ops = 0;
    }

    // ---------------------------------------------------------------- ingresso da altre pagine

    /** Seleziona la ricetta (o la importa dal PLC se manca). */
    public void select(String name) {
        if (name == null || name.trim().isEmpty()) {
            // apertura dal pulsante Recettes: legge subito la ricetta in macchina (sola lettura), salvo modifiche in corso
            if (!ed.dirty) {
                ed.importNow();
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
        g.fillRect(8, 612, 1008, 34);
        g.setColor(new Color(0x808080));
        g.drawRect(8, 612, 1008, 34);
        g.setFont(new Font("Arial", Font.PLAIN, 12));
        g.setColor(new Color(0x202020));
        String[] lines = (txt == null ? "" : txt).split("\n");
        for (int i = 0; i < Math.min(2, lines.length); i++) {
            g.drawString(lines[i], 14, 626 + i * 15);
        }
    }

    private static int sx(int x) {
        return x >= 416 ? x + 20 : x;
    }

    private void drawGrid(Graphics2D g, B1Recipe r) {
        int gy = 232;
        group(g, 46, gy, 360, "OP\u00c9RATION");
        group(g, sx(416), gy, 272, "CONSIGNE");
        group(g, sx(700), gy, 276, "CONTR\u00d4LE");
        label(g, "Phase", 8, 268);
        for (int i = 0; i < OP_NAMES.length; i++) {
            head(g, OP_NAMES[i], 46 + i * 36, 256, 36);
        }
        head(g, "Timeout (s)", sx(416), 256, 56);
        head(g, "Rotor (rpm)", sx(474), 256, 56);
        head(g, "Press.(bar)", sx(532), 256, 66);
        head(g, "Porte", sx(600), 256, 30);
        head(g, "Pist.", sx(632), 256, 30);
        head(g, "Saut", sx(700), 256, 30);
        head(g, "Temps (s)", sx(732), 256, 54);
        head(g, "Temp. (\u00b0C)", sx(788), 256, 56);
        head(g, "\u00c9n.(kWh)", sx(846), 256, 66);
        head(g, "ET", sx(914), 256, 30);
        head(g, "OU", sx(946), 256, 30);
        // riga di modifica (fase 0): i valori si scrivono in una fase con il "+" della sua riga
        drawRow(g, GRID_Y, buf, "0", false, true);
        for (int i = 0; i < ROWS; i++) {
            B1Recipe.Phase p = r == null || i >= r.phases.size() ? null : r.phases.get(i);
            drawRow(g, GRID_Y + ROW_H + i * ROW_H, p, String.valueOf(i + 1), i == phase, false);
        }
    }

    private void drawRow(Graphics2D g, int y, B1Recipe.Phase p, String label, boolean sel, boolean editRow) {
        Color c0 = editRow ? new Color(0x9ADCC8) : sel ? new Color(0xFFE066) : new Color(0xB8C8E4);
        Color c1 = editRow ? new Color(0x6EBBA2) : sel ? new Color(0xE0B000) : new Color(0x8DA2CC);
        g.setPaint(new GradientPaint(4, y, c0, 4, y + ROW_H - 2, c1));
        g.fillRoundRect(4, y, 1012, ROW_H - 2, 6, 6);
        box(g, 8, y + 2, 34, ROW_H - 6, label, 0, true);
        if (!editRow) {
            button(g, 410, y + 2, 20, ROW_H - 6, "+");
        }
        if (p == null) {
            return;
        }
        int h = ROW_H - 6, by = y + 2, s = h;
        int ops = p.ops & 0xFFFF;
        for (int k = 0; k < 10; k++) {
            sq(g, 46 + k * 36 + 5, by, s, (ops & (1 << OP_BITS[k])) != 0, new Color(0x3CFF3C), new Color(0x0C4A0C));
        }
        box(g, sx(416), by, 56, h, fmt(pval(p, "timeout"), pf("timeout")), 1, false);
        box(g, sx(474), by, 56, h, fmt(pval(p, "rpm"), pf("rpm")), 1, false);
        box(g, sx(532), by, 66, h, fmt(pval(p, "press"), pf("press")), 1, false);
        int bits = pval(p, "set_bits") & 0xFFFF;
        sq(g, sx(603), by, s, (bits & BIT_DOOR) != 0, new Color(0xFF3A3A), new Color(0x5A1010));
        boolean up = (bits & BIT_UP) != 0, down = (bits & BIT_DOWN) != 0;
        tri(g, sx(636), y + 1, up, true);
        tri(g, sx(636), y + 15, down, false);
        int ctrl = pval(p, "ctrl_bits") & 0xFFFF;
        sq(g, sx(703), by, s, (ctrl & CT_SKIP) != 0, new Color(0xE0E0E0), new Color(0x404048));
        box(g, sx(732), by, 54, h, fmt(pval(p, "time"), pf("time")), 1, false);
        box(g, sx(788), by, 56, h, fmt(pval(p, "temp"), pf("temp")), 1, false);
        box(g, sx(846), by, 66, h, fmt(pval(p, "energy"), pf("energy")), 1, false);
        sq(g, sx(917), by, s, (ctrl & CT_AND) != 0, new Color(0x40A0FF), new Color(0x103060));
        sq(g, sx(949), by, s, (ctrl & CT_OR) != 0, new Color(0x40A0FF), new Color(0x103060));
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
        if (y >= GRID_Y && y < GRID_Y + (ROWS + 1) * ROW_H && x >= 4 && x < 1016) {
            int row = (y - GRID_Y) / ROW_H - 1; // -1 = riga di modifica
            int dy = (y - GRID_Y) % ROW_H;
            if (row < 0) {
                onBuf = true;
                if (x >= 430) {
                    cell(buf, x - 20, dy);
                } else if (x < 406) {
                    cell(buf, x, dy);
                }
                onBuf = false;
            } else if (x >= 8 && x < 42) {
                phase = row; // numero di fase: la carica nella riga di modifica
                loadBuffer(r, row);
            } else if (x >= 410 && x < 430) {
                phase = row;
                if (r != null && row < r.phases.size()) {
                    r.phases.set(row, buf.copy());
                    ed.edited();
                    say("Phase " + (row + 1) + " \u00e9crite depuis la ligne de modification (n'oubliez pas Enregistrer)");
                }
            } else if (r != null && row < r.phases.size()) {
                phase = row;
                cell(r.phases.get(row), x >= 430 ? x - 20 : x, dy);
            }
            repaint.run();
            return true;
        }
        return false;
    }

    private void loadBuffer(B1Recipe r, int row) {
        if (r != null && row < r.phases.size()) {
            B1Recipe.Phase c = r.phases.get(row).copy();
            buf.ops = c.ops;
            buf.values.clear();
            buf.values.putAll(c.values);
        }
    }

    private void cell(B1Recipe.Phase p, int x, int dy) {
        for (int k = 0; k < 10; k++) {
            if (x >= 46 + k * 36 && x < 46 + (k + 1) * 36) {
                p.ops ^= 1 << OP_BITS[k];
                touch();
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
        touch();
    }

    private void editPhase(B1Recipe.Phase p, String key, String title) {
        B1RecipeLayout.Field f = pf(key);
        if (f == null) {
            return;
        }
        String s = ask(title + " - phase " + (phase + 1), fmt(pval(p, key), f));
        if (s != null && parseOk(s)) {
            p.values.put(key, B1RecipeEditor.EditorPanel.toRaw(s, f));
            touch();
        } else if (s != null) {
            say("Valeur non valide : " + s);
        }
    }

    private void editHeader(B1Recipe r, B1RecipeLayout.Field f, String title) {
        String s = ask(title, fmt(r.header.getOrDefault(f.key, 0), f));
        if (s != null && parseOk(s)) {
            r.header.put(f.key, B1RecipeEditor.EditorPanel.toRaw(s, f));
            touch();
        } else if (s != null) {
            say("Valeur non valide : " + s);
        }
    }

    private void touch() {
        if (!onBuf) {
            ed.edited();
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
        l.add(new B1BottomBar.Btn(520, 0, 56, 52, "Steps", () -> B1RecipeSteps.openShared(ed.repo, ed.layout, ed.plcIp, null)));
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
        int[] ys = up ? new int[]{y, y + 10, y + 10} : new int[]{y + 10, y, y};
        g.setColor(lit ? new Color(0x20C020) : new Color(0x50706A));
        g.fillPolygon(xs, ys, 3);
    }
}
