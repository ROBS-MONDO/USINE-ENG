package Banbury1;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Barra dei pulsanti in basso, uguale nel sinottico e in tutte le schermate: 10 pulsanti con icona
 * (Synoptic, Process, Trends, Alarms, Display, Recettes, Production, Settings, Temperatures, Components),
 * Govoni, B2, B2 main, Valori PLC, Etichetta ed EXIT. Sono solo navigazione: nessun comando al PLC.
 * Le coordinate sono relative all'angolo in alto a sinistra della barra (larghezza 1013, altezza 52).
 */
final class B1BottomBar {

    static final int WIDTH = 1013;
    static final int HEIGHT = 52;

    /** Cosa serve ai pulsanti dalla finestra che li ospita. */
    interface Env {
        B1Source src();

        B1RecipeBridge bridge();

        B1Tags tags();

        /** Finestra attuale (proprietaria di eventuali finestre di dialogo). */
        java.awt.Window window();

        /** EXIT: chiude la finestra attuale. */
        void exit();

        /** false: EXIT non fa nulla (il sinottico principale lo lascia com'era). */
        default boolean exitEnabled() {
            return true;
        }

        /** false: senza i pulsanti Govoni, B2, B2 main, Valeurs PLC e Etiquette (pagine con una barra propria, es. Production). */
        default boolean extraButtons() {
            return true;
        }

        /** Pulsanti propri della pagina al posto di Govoni/B2/... (usati se extraButtons() e' false); null = nessuno. */
        default List<Btn> customButtons() {
            return null;
        }
    }

    static final class Btn {
        final int x, y, w, h;
        final String name;
        final Runnable action;

        Btn(int x, int y, int w, int h, String name, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.name = name;
            this.action = action;
        }

        boolean contains(int px, int py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private B1BottomBar() {
    }

    /** Descrizione mostrata fermando il mouse su un pulsante (come i suggerimenti del WinCC). */
    static String tooltip(String name) {
        switch (name) {
            case "Synoptic": return "SYNOPTIQUE - synoptique principal";
            case "Process": return "PROCESS - proc\u00e9d\u00e9 et phases";
            case "Trends": return "TENDANCE - \u00e9volution temp\u00e9rature, courant, tours, pression";
            case "Alarms": return "ALARMES - liste des alarmes";
            case "Display": return "GRANDE VUE - vue agrandie";
            case "Recettes": return "RECETTES - \u00e9diteur de recettes";
            case "Production": return "PRODUCTION - liste de production";
            case "Settings": return "R\u00c9GLAGES - param\u00e8tres";
            case "Temperatures": return "TEMP\u00c9RATURES - temp\u00e9ratures";
            case "Components": return "COMPOSANTS - composants";
            case "Govoni": return "GOVONI - synoptique balances et silos";
            case "B2": return "B2 - Banbury 2 (pas encore connect\u00e9)";
            case "B2 main": return "B2 MAIN - Banbury 2 principal (pas encore connect\u00e9)";
            case "Valeurs PLC": return "VALEURS PLC - adresses et valeurs lues dans le PLC";
            case "\u00c9tiquette": return "\u00c9TIQUETTE - lecture de l'\u00e9tiquette QR de la recette";
            case "Nouvelle": return "NOUVELLE - cr\u00e9er une production";
            case "Supprimer": return "SUPPRIMER - effacer la production s\u00e9lectionn\u00e9e";
            case "Modifier": return "MODIFIER - modifier la production s\u00e9lectionn\u00e9e";
            case "Copier": return "COPIER - nouvelle production \u00e0 partir de la s\u00e9lectionn\u00e9e";
            case "Enregistrer": return "ENREGISTRER - sauvegarder la production";
            case "T\u00e9l\u00e9charger": return "T\u00c9L\u00c9CHARGER - envoyer la recette de la production au PLC (aper\u00e7u et confirmation)";
            case "Dupliquer": return "DUPLIQUER - copie de la recette (nom + _COPIE)";
            case "Importer PLC": return "IMPORTER DU PLC - lit la recette en machine et l'ajoute / met \u00e0 jour l'archive";
            case "CSV": return "CSV - exporter la recette";
            case "Envoyer PLC": return "ENVOYER AU PLC - aper\u00e7u et confirmation avant l'\u00e9criture";
            case "EXIT": return "EXIT - ferme cette fen\u00eatre";
            default: return name;
        }
    }

    /** Pulsante sotto il punto (px, py) della barra (coordinate relative alla barra), oppure null. */
    static Btn at(List<Btn> buttons, int px, int py) {
        for (Btn b : buttons) {
            if (b.contains(px, py)) {
                return b;
            }
        }
        return null;
    }

    /** Elenco dei pulsanti con la loro azione (null = non disponibile in sola lettura). */
    static List<Btn> buttons(Env env) {
        B1Source src = env.src();
        List<Btn> l = new ArrayList<>();
        String[] names = {"Synoptic", "Process", "Trends", "Alarms", "Display", "Recettes", "Production",
            "Settings", "Temperatures", "Components"};
        int[] xs = {7, 62, 137, 192, 247, 302, 357, 412, 467, 522};
        String[] target = {null, "002_Process", "003_Trends", "004_Alarms", "005_Display", null,
            "007_Production", "008_Settings", "009_Temperatures", "010_Components"};
        for (int i = 0; i < names.length; i++) {
            Runnable a = null;
            if (i == 0) {
                a = B1_Main::showMain;
            } else if (i == 2) {
                a = () -> B1TrendWindow.open(src);
            } else if (i == 3) {
                a = () -> B1AlarmWindow.open(src, B1_Main.ENGINE);
            } else if (i == 5) {
                if (env.bridge() != null) {
                    a = () -> env.bridge().openEditor(null);
                }
            } else if (target[i] != null) {
                final String scr = target[i];
                a = () -> B1ScreensWindow.open(src, scr);
            }
            l.add(new Btn(xs[i], 0, 52, 52, names[i], a));
        }
        String[] t = {"Govoni", "B2", "B2 main", "Valeurs PLC", "\u00c9tiquette"};
        int[] tx = {578, 632, 688, 752, 826};
        int[] tw = {48, 48, 58, 70, 66};
        for (int i = 0; i < t.length && env.extraButtons(); i++) {
            Runnable a = null;
            if (i == 0) {
                a = () -> B1ScreensWindow.open(src, "301_GovoniSynoptique2Bambury1");
            } else if (i == 3) {
                a = () -> B1RawViewer.open(src, env.tags());
            } else if (i == 4 && env.bridge() != null) {
                a = () -> B1LabelDialog.open(env.window() instanceof javax.swing.JFrame
                        ? (javax.swing.JFrame) env.window() : null, env.bridge());
            }
            l.add(new Btn(tx[i], 0, tw[i], 50, t[i], a));
        }
        if (!env.extraButtons() && env.customButtons() != null) {
            l.addAll(env.customButtons());
        }
        l.add(new Btn(932, 0, 88, 52, "EXIT", env.exitEnabled() ? env::exit : null));
        return l;
    }

    /** Disegna la barra con l'angolo in alto a sinistra in (0,0) del sistema di coordinate attuale. */
    static void paint(Graphics2D g, List<Btn> buttons) {
        for (Btn b : buttons) {
            boolean download = b.name.equals("T\u00e9l\u00e9charger"); // il pulsante piu' importante della pagina Production
            g.setPaint(download ? new GradientPaint(b.x, b.y, new Color(0xFFEFB0), b.x, b.y + b.h, new Color(0xF0B030))
                    : new GradientPaint(b.x, b.y, Color.WHITE, b.x, b.y + b.h, new Color(0xCFCFCF)));
            g.fillRect(b.x, b.y, b.w, b.h);
            g.setColor(new Color(0x808080));
            g.drawRect(b.x, b.y, b.w - 1, b.h - 1);
            int idx = iconIndex(b.name);
            if (idx >= 0) {
                icon(g, idx, b.x, b.y);
            } else if (b.name.equals("EXIT")) {
                g.setColor(new Color(0xD01010));
                g.fillRect(b.x + 24, b.y + 15, 60, 22);
                text(g, "EXIT", b.x + 24, b.y + 15, 60, 22, new Font("Arial", Font.BOLD, 14), Color.WHITE);
            } else {
                text(g, b.name, b.x, b.y, b.w, b.h, new Font("Arial", download ? Font.BOLD : Font.PLAIN, 11), Color.BLACK);
            }
        }
    }

    private static int iconIndex(String name) {
        String[] names = {"Synoptic", "Process", "Trends", "Alarms", "Display", "Recettes", "Production",
            "Settings", "Temperatures", "Components"};
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static void text(Graphics2D g, String s, int x, int y, int w, int h, Font f, Color c) {
        // il testo si rimpicciolisce fino a stare nel pulsante (es. "Enregistrer", "T\u00e9l\u00e9charger")
        Font ff = f;
        FontMetrics fm = g.getFontMetrics(ff);
        while (fm.stringWidth(s) > w - 4 && ff.getSize() > 8) {
            ff = ff.deriveFont((float) ff.getSize() - 1);
            fm = g.getFontMetrics(ff);
        }
        g.setFont(ff);
        g.setColor(c);
        g.drawString(s, x + (w - fm.stringWidth(s)) / 2, y + (h - fm.getHeight()) / 2 + fm.getAscent());
    }

    private static void icon(Graphics2D g, int i, int x, int y) {
        int cx = x + 26, cy = y + 26;
        g.setStroke(new BasicStroke(2f));
        switch (i) {
            case 0:
                g.setColor(new Color(0xE0B020));
                g.fillPolygon(new int[]{cx - 14, cx + 14, cx}, new int[]{cy, cy, cy - 14}, 3);
                g.fillRect(cx - 10, cy, 20, 12);
                break;
            case 1:
                g.setColor(new Color(0xC02020));
                g.drawOval(cx - 16, cy - 6, 18, 18);
                g.drawOval(cx - 2, cy - 14, 18, 18);
                break;
            case 2:
                g.setColor(new Color(0x2060C0));
                g.drawPolyline(new int[]{cx - 16, cx - 6, cx + 2, cx + 16}, new int[]{cy + 8, cy - 4, cy + 2, cy - 12}, 4);
                break;
            case 3:
                g.setColor(new Color(0xD02020));
                g.fillRect(cx - 12, cy - 10, 24, 20);
                break;
            case 4:
                g.setColor(new Color(0x40A040));
                g.drawOval(cx - 12, cy - 12, 16, 16);
                g.drawLine(cx + 2, cy + 2, cx + 14, cy + 14);
                break;
            case 5:
                g.setColor(new Color(0x2060C0));
                g.fillRect(cx - 14, cy - 10, 28, 18);
                g.fillRect(cx - 6, cy + 8, 12, 4);
                break;
            case 6:
                g.setColor(new Color(0xC02020));
                g.fillRect(cx - 10, cy - 12, 20, 24);
                break;
            case 7:
                g.setColor(new Color(0xC02020));
                g.drawLine(cx - 12, cy + 12, cx + 10, cy - 10);
                g.setColor(new Color(0x2060C0));
                g.fillOval(cx + 4, cy - 14, 10, 10);
                break;
            case 8:
                g.setColor(new Color(0xD02020));
                g.fillOval(cx - 14, cy - 2, 12, 12);
                g.setColor(new Color(0x2060C0));
                g.fillOval(cx + 2, cy - 2, 12, 12);
                break;
            default:
                g.setColor(new Color(0x20A020));
                g.drawArc(cx - 14, cy - 14, 28, 28, 30, 270);
                g.fillPolygon(new int[]{cx + 8, cx + 18, cx + 14}, new int[]{cy - 14, cy - 12, cy - 4}, 3);
        }
        g.setStroke(new BasicStroke(1));
    }
}
