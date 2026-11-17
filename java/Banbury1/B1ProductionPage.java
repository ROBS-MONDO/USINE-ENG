package Banbury1;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Pagina "Production" di B1 (come la 007_Production di WinCC): elenco delle produzioni con la loro ricetta, dettaglio della produzione
 * selezionata, creazione / modifica / copia / eliminazione e "download" (invio della ricetta al PLC con la procedura sicura dell'editor ricette).
 * Disegna nelle coordinate base 1024x768 della schermata. Non scrive mai direttamente nel PLC.
 */
public final class B1ProductionPage {

    // geometria (coordinate base, come nel layout originale 007_Production)
    static final int LIST_X = 10, LIST_Y = 100, ROW_H = 24, ROWS = 12, ID_W = 140, LIST_W = 740;
    static final int[] F_NAME = {150, 445, 600, 25};
    static final int[] F_DESC = {150, 475, 600, 25};
    static final int[] F_DATE = {150, 505, 235, 25};
    static final int[] F_QTY = {150, 535, 95, 25};
    static final int[] F_RECIPE = {150, 565, 235, 25};

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final B1ProductionRepository repo;
    private final B1RecipeBridge bridge;
    private final Supplier<Component> parent;
    private final Runnable repaint;

    private volatile List<B1Production> list = new ArrayList<>();
    private volatile List<B1RecipeRepository.Entry> recipes = new ArrayList<>();
    private volatile String loadError = "";
    private volatile boolean loaded;
    private int selectedId;
    private int scroll;
    /** Bozza in modifica (null = sola visualizzazione). */
    private B1Production draft;
    private boolean draftNew;
    private volatile String message = "";
    private volatile long messageUntil;

    public B1ProductionPage(B1ProductionRepository repo, B1RecipeBridge bridge, Supplier<Component> parent, Runnable repaint) {
        this.repo = repo;
        this.bridge = bridge;
        this.parent = parent;
        this.repaint = repaint;
    }

    // ---------------------------------------------------------------- dati

    /** Rilegge elenco produzioni e ricette in un thread (non blocca la grafica). */
    public void refreshAsync() {
        Thread t = new Thread(this::refreshNow, "b1-production-load");
        t.setDaemon(true);
        t.start();
    }

    public void refreshNow() {
        try {
            List<B1Production> l = repo == null ? new ArrayList<>() : repo.list();
            List<B1RecipeRepository.Entry> r = bridge == null ? new ArrayList<>() : bridge.repo().list();
            list = l;
            recipes = r;
            loadError = "";
            if (selectedId == 0 && !l.isEmpty()) {
                selectedId = l.get(0).id;
            }
        } catch (IOException e) {
            loadError = e.getMessage();
        }
        loaded = true;
        repaint.run();
    }

    public boolean loaded() {
        return loaded;
    }

    private B1Production selected() {
        for (B1Production p : list) {
            if (p.id == selectedId) {
                return p;
            }
        }
        return null;
    }

    /** Il valore mostrato nei campi: la bozza se si sta modificando, altrimenti la produzione selezionata. */
    private B1Production shown() {
        return draft != null ? draft : selected();
    }

    /** Nome della ricetta in archivio (ASCII, es. "110o"), null se non c'e'. */
    String recipeName(int recipeNo) {
        for (B1RecipeRepository.Entry e : recipes) {
            if (e.recipeNo == recipeNo && recipeNo > 0) {
                return e.name;
            }
        }
        return null;
    }

    /** Nome da mostrare, con il simbolo dei gradi come in WinCC. */
    private String shownRecipe(int recipeNo) {
        String n = recipeName(recipeNo);
        return n == null ? null : B1RecipeBridge.pretty(n);
    }

    private void say(String m) {
        message = m;
        messageUntil = System.currentTimeMillis() + 6000;
        repaint.run();
    }

    // ---------------------------------------------------------------- disegno

    public void paint(Graphics2D g) {
        Font plain = new Font("Arial", Font.PLAIN, 15);
        Font bold = new Font("Arial", Font.BOLD, 15);
        // lista
        g.setColor(Color.WHITE);
        g.fillRect(LIST_X, LIST_Y, LIST_W, ROW_H * ROWS);
        g.setColor(new Color(0x808080));
        g.drawRect(LIST_X, LIST_Y, LIST_W, ROW_H * ROWS);
        g.drawLine(LIST_X + ID_W, LIST_Y, LIST_X + ID_W, LIST_Y + ROW_H * ROWS);
        List<B1Production> l = list;
        if (!loaded) {
            text(g, "Chargement...", LIST_X + 8, LIST_Y + 2, 300, ROW_H, plain, new Color(0x606060));
        } else if (!loadError.isEmpty()) {
            text(g, loadError, LIST_X + 8, LIST_Y + 2, LIST_W - 16, ROW_H, plain, new Color(0xB00000));
        } else if (l.isEmpty()) {
            text(g, "Aucune production : importer celles de WinCC ou en cr\u00e9er une (bouton Nouvelle)", LIST_X + 8, LIST_Y + 2,
                    LIST_W - 16, ROW_H, plain, new Color(0x606060));
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, l.size() - ROWS)));
        for (int i = 0; i < ROWS && scroll + i < l.size(); i++) {
            B1Production p = l.get(scroll + i);
            int y = LIST_Y + i * ROW_H;
            boolean sel = p.id == selectedId;
            if (sel) {
                g.setColor(new Color(0x0A246A));
                g.fillRect(LIST_X + 1, y + 1, LIST_W - 17, ROW_H - 1);
            }
            Color fg = sel ? Color.WHITE : Color.BLACK;
            String rn = shownRecipe(p.recipeNo);
            text(g, String.valueOf(p.id), LIST_X + 8, y, ID_W - 10, ROW_H, sel ? bold : plain, fg);
            text(g, p.name + " - " + (rn == null ? "(recette n\u00b0 " + p.recipeNo + " absente)" : rn), LIST_X + ID_W + 8, y,
                    LIST_W - ID_W - 30, ROW_H, sel ? bold : plain, fg);
        }
        // barra di scorrimento
        int sbx = LIST_X + LIST_W - 16;
        g.setColor(new Color(0xE0E0E0));
        g.fillRect(sbx, LIST_Y + 1, 15, ROW_H * ROWS - 1);
        if (l.size() > ROWS) {
            int th = Math.max(20, (ROW_H * ROWS) * ROWS / l.size());
            int ty = LIST_Y + (ROW_H * ROWS - th) * scroll / Math.max(1, l.size() - ROWS);
            g.setColor(new Color(0xA0A0A0));
            g.fillRect(sbx + 1, ty, 13, th);
        }
        // dettagli
        B1Production p = shown();
        boolean edit = draft != null;
        field(g, F_NAME, p == null ? null : p.name, edit, false);
        field(g, F_DESC, p == null ? null : p.description, edit, false);
        field(g, F_DATE, p == null ? null : (edit && draftNew ? "(nouvelle)" : p.updated.format(DT)), false, false);
        field(g, F_QTY, p == null ? null : String.valueOf(p.quantity), edit, true);
        String rn = p == null ? null : shownRecipe(p.recipeNo);
        field(g, F_RECIPE, p == null ? null : (rn != null ? rn : (p.recipeNo > 0 ? "(n\u00b0 " + p.recipeNo + " absente)" : "")), edit, false);
        // messaggi
        String m = System.currentTimeMillis() < messageUntil ? message
                : (edit ? (draftNew ? "NOUVELLE PRODUCTION" : "MODIFICATION") + " : cliquer sur un champ pour le changer, puis Enregistrer" : "");
        if (!m.isEmpty()) {
            text(g, m, 10, 603, 740, 20, new Font("Arial", Font.BOLD, 13), edit ? new Color(0xC00000) : new Color(0x204080));
        }
        text(g, "Archive : " + (repo == null ? "indisponible" : repo.location()), 10, 622, 740, 16, new Font("Arial", Font.PLAIN, 10), new Color(0x505050));
    }

    private void field(Graphics2D g, int[] r, String v, boolean editable, boolean right) {
        g.setColor(editable ? Color.WHITE : new Color(0xD4D0C8));
        g.fillRect(r[0], r[1], r[2], r[3]);
        g.setColor(editable ? new Color(0x2060C0) : new Color(0x808080));
        g.drawRect(r[0], r[1], r[2], r[3]);
        String s = v == null ? "--" : v;
        Font f = new Font("Arial", Font.PLAIN, 15);
        FontMetrics fm = g.getFontMetrics(f);
        int tx = right ? r[0] + r[2] - fm.stringWidth(s) - 6 : r[0] + 6;
        g.setFont(f);
        g.setColor(v == null ? new Color(0x707070) : Color.BLACK);
        java.awt.Shape old = g.getClip();
        g.clipRect(r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2);
        g.drawString(s, tx, r[1] + (r[3] - fm.getHeight()) / 2 + fm.getAscent());
        g.setClip(old);
    }

    private static void text(Graphics2D g, String s, int x, int y, int w, int h, Font f, Color c) {
        FontMetrics fm = g.getFontMetrics(f);
        g.setFont(f);
        g.setColor(c);
        java.awt.Shape old = g.getClip();
        g.clipRect(x, y, w, h);
        g.drawString(s, x, y + (h - fm.getHeight()) / 2 + fm.getAscent());
        g.setClip(old);
    }

    // ---------------------------------------------------------------- mouse

    /** Clic in coordinate base: lista, barra di scorrimento e campi in modifica. true = gestito. */
    public boolean click(int x, int y) {
        // lista
        if (x >= LIST_X && x < LIST_X + LIST_W && y >= LIST_Y && y < LIST_Y + ROW_H * ROWS) {
            if (x >= LIST_X + LIST_W - 16) {
                scroll += y < LIST_Y + ROW_H * ROWS / 2 ? -ROWS / 2 : ROWS / 2;
                repaint.run();
                return true;
            }
            int idx = scroll + (y - LIST_Y) / ROW_H;
            List<B1Production> l = list;
            if (idx >= 0 && idx < l.size()) {
                if (draft != null && !confirm("Abandonner la modification en cours ?")) {
                    return true;
                }
                draft = null;
                selectedId = l.get(idx).id;
                repaint.run();
            }
            return true;
        }
        if (draft != null) {
            if (in(F_NAME, x, y)) {
                String v = ask("Nom de la production (max 50 caract\u00e8res) :", draft.name);
                if (v != null) {
                    draft.name = v.trim();
                }
            } else if (in(F_DESC, x, y)) {
                String v = ask("Description (max 50 caract\u00e8res) :", draft.description);
                if (v != null) {
                    draft.description = v.trim();
                }
            } else if (in(F_QTY, x, y)) {
                String v = ask("Quantit\u00e9 de m\u00e9langes (0-999) :", String.valueOf(draft.quantity));
                if (v != null) {
                    try {
                        draft.quantity = Integer.parseInt(v.trim());
                    } catch (NumberFormatException e) {
                        say("Quantit\u00e9 non valide");
                    }
                }
            } else if (in(F_RECIPE, x, y)) {
                chooseRecipe();
            } else {
                return false;
            }
            repaint.run();
            return true;
        }
        return false;
    }

    private static boolean in(int[] r, int x, int y) {
        return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    public void wheel(int rows) {
        scroll += rows;
        repaint.run();
    }

    // ---------------------------------------------------------------- dialoghi

    private String ask(String prompt, String initial) {
        return (String) JOptionPane.showInputDialog(parent.get(), prompt, "Production", JOptionPane.PLAIN_MESSAGE, null, null, initial);
    }

    private boolean confirm(String q) {
        return JOptionPane.showConfirmDialog(parent.get(), q, "Production", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    private void error(String m) {
        JOptionPane.showMessageDialog(parent.get(), m, "Production", JOptionPane.ERROR_MESSAGE);
    }

    private void chooseRecipe() {
        List<B1RecipeRepository.Entry> usable = new ArrayList<>();
        for (B1RecipeRepository.Entry e : recipes) {
            if (e.recipeNo > 0) {
                usable.add(e);
            }
        }
        if (usable.isEmpty()) {
            error("Aucune recette avec un num\u00e9ro dans l'archive (importer les recettes de WinCC).");
            return;
        }
        JComboBox<B1RecipeRepository.Entry> box = new JComboBox<>(usable.toArray(new B1RecipeRepository.Entry[0]));
        for (int i = 0; i < usable.size(); i++) {
            if (usable.get(i).recipeNo == draft.recipeNo) {
                box.setSelectedIndex(i);
            }
        }
        if (JOptionPane.showConfirmDialog(parent.get(), box, "Recette de la production", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            B1RecipeRepository.Entry e = (B1RecipeRepository.Entry) box.getSelectedItem();
            if (e != null) {
                draft.recipeNo = e.recipeNo;
            }
        }
    }

    // ---------------------------------------------------------------- pulsanti della barra

    public boolean editing() {
        return draft != null;
    }

    public void newProduction() {
        if (draft != null && !confirm("Abandonner la modification en cours ?")) {
            return;
        }
        draft = new B1Production();
        draftNew = true;
        repaint.run();
    }

    public void editSelected() {
        B1Production p = selected();
        if (p == null) {
            say("Aucune production s\u00e9lectionn\u00e9e");
            return;
        }
        draft = p.copy();
        draftNew = false;
        repaint.run();
    }

    public void copySelected() {
        B1Production p = selected();
        if (p == null) {
            say("Aucune production s\u00e9lectionn\u00e9e");
            return;
        }
        draft = p.copy();
        draft.id = 0;
        String n = p.name + " (copie)";
        draft.name = n.length() > 50 ? n.substring(0, 50) : n;
        draftNew = true;
        repaint.run();
    }

    public void save() {
        if (draft == null) {
            say("Rien \u00e0 enregistrer : choisir Nouvelle, Modifier ou Copier");
            return;
        }
        List<String> err = draft.validate();
        if (!err.isEmpty()) {
            error(err.get(0));
            return;
        }
        try {
            if (repo.nameExists(draft.name, draft.id)) {
                error("Nom de production d\u00e9j\u00e0 utilis\u00e9");
                return;
            }
            if (!confirm("Sauvegarde production. Tu es s\u00fbr ?")) {
                return;
            }
            B1Production s = repo.save(draft);
            selectedId = s.id;
            draft = null;
            say("Production enregistr\u00e9e (n\u00b0 " + s.id + ")");
            refreshNow();
        } catch (IOException e) {
            error(e.getMessage());
        }
    }

    public void deleteSelected() {
        B1Production p = selected();
        if (p == null) {
            say("Aucune production s\u00e9lectionn\u00e9e");
            return;
        }
        if (draft != null) {
            say("Terminer d'abord la modification en cours");
            return;
        }
        if (!confirm("Effacement production. Tu es s\u00fbr ?\n\n" + p.id + " - " + p.name)) {
            return;
        }
        try {
            repo.end(p.id);
            selectedId = 0;
            say("Production effac\u00e9e");
            refreshNow();
        } catch (IOException e) {
            error(e.getMessage());
        }
    }

    /**
     * Download: envoie au PLC la recette de la production selectionnee. Ouvre l'\u00e9diteur de recettes sur la recette et l'aper\u00e7u d'envoi
     * (copie de s\u00e9curit\u00e9, confirmation \u00ab ENVOYER \u00bb, machine \u00e0 l'arr\u00eat). La quantit\u00e9 de m\u00e9langes n'est pas encore transmise au PLC.
     */
    public void download() {
        B1Production p = selected();
        if (p == null || draft != null) {
            say(draft != null ? "Enregistrer ou abandonner d'abord la modification" : "Aucune production s\u00e9lectionn\u00e9e");
            return;
        }
        String rn = recipeName(p.recipeNo);
        if (rn == null) {
            error("Recette n\u00b0 " + p.recipeNo + " introuvable dans l'archive des recettes.");
            return;
        }
        if (bridge == null) {
            error("Archive des recettes indisponible.");
            return;
        }
        say("T\u00e9l\u00e9chargement : recette \u00ab " + rn + " \u00bb (la quantit\u00e9 de m\u00e9langes n'est pas encore transmise au PLC)");
        bridge.openEditorAndSend(rn);
    }

    /** Pulsanti della barra inferiore, al posto di Govoni/B2/... Coordinate nella barra (x da 578). */
    public List<B1BottomBar.Btn> barButtons() {
        List<B1BottomBar.Btn> l = new ArrayList<>();
        l.add(new B1BottomBar.Btn(578, 0, 58, 52, "Nouvelle", this::newProduction));
        l.add(new B1BottomBar.Btn(638, 0, 58, 52, "Supprimer", this::deleteSelected));
        l.add(new B1BottomBar.Btn(698, 0, 58, 52, "Modifier", this::editSelected));
        l.add(new B1BottomBar.Btn(758, 0, 58, 52, "Copier", this::copySelected));
        l.add(new B1BottomBar.Btn(818, 0, 58, 52, "Enregistrer", this::save));
        l.add(new B1BottomBar.Btn(878, 0, 52, 52, "T\u00e9l\u00e9charger", this::download));
        return l;
    }
}
