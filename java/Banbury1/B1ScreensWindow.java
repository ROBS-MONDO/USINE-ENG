package Banbury1;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Finestra con le schermate WinCC di B1 diverse dal sinottico (Process, Display, Production,
 * Settings, Temperatures, Components...), disegnate dal layout estratto dai file .Pdl.
 * SOLA LETTURA. I valori vengono dal PLC via B1Source.raw(); lampade e campi non collegati
 * restano "--" / barrati finche' il bit/indirizzo non e' confermato.
 */
public class B1ScreensWindow extends JFrame {

    /** Schermate raggiungibili dalla barra in basso: nome file, etichetta. */
    public static final String[][] SCREENS = {
        {"002_Process", "Process"}, {"003_Trends", "Tendances"}, {"004_Alarms", "Alarmes"},
        {"005_Display", "Affichage"}, {"007_Production", "Production"}, {"008_Settings", "R\u00e9glages 1"},
        {"008_Settings_1", "R\u00e9glages 2"}, {"008_Settings_2", "R\u00e9glages 3"}, {"008_Settings_3", "R\u00e9glages 4"},
        {"009_Temperatures", "Temp\u00e9ratures"}, {"010_Components", "Composants"},
        {"010_PhaseInfo", "Info phase"}, {"203_RunningPhaseActual", "Phase en cours"},
        {"300_GovoniSynoptique1ChargeSilo", "Govoni silos"}, {"301_GovoniSynoptique2Bambury1", "Govoni B1"},
        {"304_GovoniEtatBalance", "Govoni balance"}, {"305_GovoniEtatProductionB1", "Govoni prod. B1"},
        {"309_GovoniParametreGeneral1", "Govoni param. 1"}, {"311_GovoniParametreBalance", "Govoni param. bal."},
        {"312_GovoniParametreSilo", "Govoni param. silos"}, {"316_GovoniSemiauto", "Govoni semiauto"},
    };

    private static B1ScreensWindow shared;

    private final ScreenPanel panel;

    private B1ScreensWindow(B1Source src, String first) {
        super("Banbury 1 - \u00e9crans");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        panel = new ScreenPanel(src, first);
        setContentPane(panel);
        pack();
        setLocationRelativeTo(null);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                synchronized (B1ScreensWindow.class) {
                    shared = null;
                }
            }
        });
    }

    /** Apre (o porta in primo piano) la finestra sulla schermata indicata (es. "005_Display"). */
    public static void open(B1Source src, String screen) {
        SwingUtilities.invokeLater(() -> {
            synchronized (B1ScreensWindow.class) {
                if (shared == null || !shared.isDisplayable()) {
                    shared = new B1ScreensWindow(src, screen);
                    shared.setVisible(true);
                } else {
                    shared.panel.show(screen);
                }
                shared.toFront();
            }
        });
    }

    /** Disegna una schermata in un PNG (verifica senza schermo). */
    public static void snapshot(B1Source src, String screen, String out, boolean overlay) throws Exception {
        ScreenPanel p = new ScreenPanel(src, screen);
        if (p.prodPage != null) {
            p.prodPage.refreshNow(); // la cattura non aspetta il caricamento in background
        }
        p.overlay = overlay;
        p.setSize(ScreenPanel.W, ScreenPanel.H);
        BufferedImage img = new BufferedImage(ScreenPanel.W, ScreenPanel.H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        p.paint(g);
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", new File(out));
    }

    // =====================================================================
    static final class ScreenPanel extends JComponent {
        static final int W = 1024;
        static final int H = 768;
        private static final Locale LOC = Locale.ITALY;
        private static final Color GRAY = new Color(0xD4D0C8);

        private final B1Source src;
        private B1Screen top;
        private B1Screen content;
        private String contentName;
        private int navScroll;
        boolean overlay;
        private String message = "";
        private long messageUntil;
        /** Pagina Production (solo quando e' la schermata attiva). */
        private B1ProductionPage prodPage;

        ScreenPanel(B1Source src, String first) {
            this.src = src;
            this.top = B1Screen.load("100_TopBar");
            setPreferredSize(new Dimension(W, H));
            javax.swing.ToolTipManager.sharedInstance().registerComponent(this);
            javax.swing.ToolTipManager.sharedInstance().setInitialDelay(300);
            show(first);
            new Timer(500, e -> repaint()).start();
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "ov");
            getActionMap().put("ov", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    overlay = !overlay;
                    repaint();
                }
            });
            addMouseWheelListener(e -> {
                if (prodPage != null) {
                    prodPage.wheel(e.getWheelRotation() * 2);
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int x = (int) (e.getX() / scaleX());
                    int y = (int) (e.getY() / scaleY());
                    if (prodPage != null && prodPage.click(x, y)) {
                        return;
                    }
                    for (B1BottomBar.Btn b : barButtons()) {
                        if (b.contains(x - BAR_X, y - BAR_Y)) {
                            if (b.action != null) {
                                b.action.run();
                            } else {
                                message = "'" + b.name + "' : non disponible (lecture seule)";
                                messageUntil = System.currentTimeMillis() + 4000;
                                repaint();
                            }
                            return;
                        }
                    }
                }
            });
        }

        void show(String screen) {
            contentName = screen;
            content = B1Screen.load(screen);
            if ("007_Production".equals(screen)) {
                prodPage = new B1ProductionPage(B1_Main.PRODUCTIONS, B1_Main.BRIDGE,
                        () -> SwingUtilities.getWindowAncestor(ScreenPanel.this), this::repaint);
                prodPage.refreshAsync();
            } else {
                prodPage = null;
            }
            barButtons = null; // la barra cambia secondo la pagina (es. Production senza i pulsanti Govoni/B2/...)
            repaint();
        }

        // le schermate riempiono tutta la finestra (come il WinCC a schermo intero): scala separata in larghezza e altezza
        private double scaleX() {
            return Math.max(0.1, getWidth() / (double) W);
        }

        private double scaleY() {
            return Math.max(0.1, getHeight() / (double) H);
        }

        // barra pulsanti comune (B1BottomBar), uguale al sinottico: in basso, sopra la riga di stato
        private static final int BAR_X = 7;
        private static final int BAR_Y = 700;
        private java.util.List<B1BottomBar.Btn> barButtons;

        private final B1BottomBar.Env barEnv = new B1BottomBar.Env() {
            @Override
            public B1Source src() {
                return src;
            }

            @Override
            public B1RecipeBridge bridge() {
                return B1_Main.BRIDGE;
            }

            @Override
            public boolean extraButtons() {
                return !"007_Production".equals(contentName);
            }

            @Override
            public java.util.List<B1BottomBar.Btn> customButtons() {
                return prodPage == null ? null : prodPage.barButtons();
            }

            @Override
            public B1Tags tags() {
                return B1_Main.TAGS;
            }

            @Override
            public java.awt.Window window() {
                return SwingUtilities.getWindowAncestor(ScreenPanel.this);
            }

            @Override
            public void exit() {
                java.awt.Window w = SwingUtilities.getWindowAncestor(ScreenPanel.this);
                if (w != null) {
                    w.dispatchEvent(new java.awt.event.WindowEvent(w, java.awt.event.WindowEvent.WINDOW_CLOSING));
                }
            }
        };

        @Override
        public String getToolTipText(MouseEvent e) {
            B1BottomBar.Btn b = B1BottomBar.at(barButtons(), (int) (e.getX() / scaleX()) - BAR_X,
                    (int) (e.getY() / scaleY()) - BAR_Y);
            return b == null ? null : B1BottomBar.tooltip(b.name);
        }

        private java.util.List<B1BottomBar.Btn> barButtons() {
            if (barButtons == null) {
                barButtons = B1BottomBar.buttons(barEnv);
            }
            return barButtons;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0xAAAAAA));
            g.fillRect(0, 0, getWidth(), getHeight());
            if (getWidth() > 0) {
                g.scale(scaleX(), scaleY());
            }
            g.setPaint(new GradientPaint(0, 0, new Color(0xC6C6C6), 0, H, new Color(0xE2E2E2)));
            g.fillRect(0, 0, W, H);
            if (content != null) {
                drawScreen(g, content, 0);
                if ("002_Process".equals(contentName)) {
                    drawProcessTable(g);
                }
                if (prodPage != null && "007_Production".equals(contentName)) {
                    prodPage.paint(g);
                }
            } else {
                g.setColor(Color.DARK_GRAY);
                g.setFont(new Font("Arial", Font.BOLD, 18));
                g.drawString("Disposition '" + contentName + "' introuvable (dossier screens/)", 40, 200);
            }
            if (top != null) {
                g.setColor(new Color(0xBEBEBE));
                g.fillRect(0, 0, W, 60);
                drawScreen(g, top, 0);
                drawClockBox(g);
            }
            drawNav(g);
            g.dispose();
        }

        /** Data e ora nello spazio libero della barra in alto (tra la sigla macchina e la pressione). */
        private void drawClockBox(Graphics2D g) {
            int x = 196, y = 5, w = 270, h = 50;
            g.setColor(new Color(0xD4D0C8));
            g.fillRect(x, y, w, h);
            bevel(g, x, y, w, h);
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            g.setFont(new Font("Arial", Font.BOLD, 17));
            java.awt.FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(0x202020));
            String d = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String t = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            g.drawString(d, x + (w - fm.stringWidth(d)) / 2, y + 20);
            g.drawString(t, x + (w - fm.stringWidth(t)) / 2, y + 42);
        }


        // ---------------- pagina Process: tabella delle tre fasi ----------------
        private static final String[] OP_NAMES = {"Rubb.", "Add.", "All Black", "PLAST.", "CERAC.", "SILANO", "Half Black", "Degas", "Clean", "Disch."};
        private static final int[] OP_BITS = {0, 1, 2, 3, 4, 5, 6, 13, 14, 15};
        private static final Color[][] BANDS = {
            {new Color(0x3C86E0), new Color(0x1458B8)},   // fase attuale, valori attuali
            {new Color(0x5CC05C), new Color(0x2E8E2E)},   // fase attuale, valori impostati
            {new Color(0xEBAA48), new Color(0xC27818)}};  // fase successiva, valori impostati

        private static final int[] COLS_X = {46, 416, 474, 532, 600, 632, 664, 700, 732, 788, 846, 914, 946};

        private void drawProcessTable(Graphics2D g) {
            Font fh = new Font("Arial", Font.PLAIN, 11);
            // intestazioni
            g.setColor(new Color(0xE6E6E6));
            g.fillRect(46, 154, 360, 22);
            g.fillRect(416, 154, 272, 22);
            g.fillRect(700, 154, 276, 22);
            g.setColor(new Color(0x606060));
            g.drawRect(46, 154, 360, 22);
            g.drawRect(416, 154, 272, 22);
            g.drawRect(700, 154, 276, 22);
            g.setFont(new Font("Arial", Font.PLAIN, 14));
            g.setColor(Color.BLACK);
            g.drawString("OP\u00c9RATION", 46 + 150, 170);
            g.drawString("CONSIGNE", 416 + 120, 170);
            g.drawString("CONTR\u00d4LE", 700 + 100, 170);
            g.setFont(fh);
            g.drawString("Phase", 8, 196);
            for (int i = 0; i < OP_NAMES.length; i++) {
                fitHeader(g, OP_NAMES[i], 46 + i * 36, 180, 36);
            }
            fitHeader(g, "Timeout (s)", 416, 180, 56);
            fitHeader(g, "Rotor (rpm)", 474, 180, 56);
            fitHeader(g, "Press.(bar)", 532, 180, 66);
            fitHeader(g, "Porte", 600, 180, 30);
            fitHeader(g, "Pist.", 632, 180, 30);
            fitHeader(g, "Saut", 700, 180, 30);
            fitHeader(g, "Temps (s)", 732, 180, 54);
            fitHeader(g, "Temp. (\u00B0C)", 788, 180, 56);
            fitHeader(g, "\u00c9n.(kWh)", 846, 180, 66);
            fitHeader(g, "ET", 914, 180, 30);
            fitHeader(g, "OU", 946, 180, 30);

            Double ph = src.connected() ? src.num("phase_no") : null;
            int phase = ph == null ? 0 : ph.intValue();
            // riga 1: attuale/valori attuali; riga 2: attuale/impostati; riga 3: successiva (dal DB201)
            String[] act = {"ph_act_ops", "ph_act_tmo", "ph_act_bits", "ph_act_rpm", "ph_act_press", "ph_act_ctrl", "ph_act_time", "ph_act_temp", "ph_act_energy"};
            String[] set = {"ph_set_ops", "ph_set_tmo", "ph_set_bits", "ph_set_rpm", "ph_set_press", "ph_set_ctrl", "ph_set_time", "ph_set_temp", "ph_set_energy"};
            double[][] rows = new double[3][];
            rows[0] = readPhase(act);
            rows[1] = readPhase(set);
            rows[2] = nextPhase(phase);
            int[] y = {212, 266, 320};
            String[] labels = {phase > 0 ? String.valueOf(phase) : "--", phase > 0 ? String.valueOf(phase) : "--",
                rows[2] == null ? "--" : String.valueOf(phase + 1)};
            for (int r = 0; r < 3; r++) {
                drawPhaseRow(g, y[r], BANDS[r], labels[r], rows[r], r == 0);
            }
            // etichetta della spia "ciclo sospeso" (nel layout originale e' un campo senza testo)
            g.setColor(new Color(0xD4D0C8));
            g.fillRect(855, 565, 160, 25);
            bevel(g, 855, 565, 160, 25);
            fit(g, "Cycle suspendu", 855, 565, 160, 25, Color.BLACK, -1, false);
            // legenda colori (le scritte sono nel layout)
            int[] ly = {533, 563, 593};
            for (int i = 0; i < 3; i++) {
                g.setPaint(new GradientPaint(5, ly[i], BANDS[i][0], 5, ly[i] + 21, BANDS[i][1]));
                g.fillRect(5, ly[i], 50, 21);
            }
        }

        private void fitHeader(Graphics2D g, String text, int x, int y, int w) {
            g.setColor(new Color(0xD4D0C8));
            g.fillRect(x, y, w, 22);
            bevel(g, x, y, w, 22);
            fit(g, text, x, y, w, 22, Color.BLACK, 0, false);
        }

        private double[] readPhase(String[] keys) {
            if (!src.connected()) {
                return null;
            }
            double[] v = new double[keys.length];
            for (int i = 0; i < keys.length; i++) {
                Double d = src.num(keys[i]);
                if (d == null) {
                    return null;
                }
                v[i] = d;
            }
            return v;
        }

        /** Fase successiva dal DB201 (ricetta in macchina): 10 fasi da 30 byte dal DBW42. */
        private double[] nextPhase(int phase) {
            if (!src.connected() || phase < 1 || phase >= 10) {
                return null;
            }
            int base = 42 + 30 * phase;
            int[] offs = {0, 2, 10, 12, 14, 20, 22, 24, 26};
            double[] v = new double[9];
            for (int i = 0; i < offs.length; i++) {
                Double d = src.raw("DB201DBW" + (base + offs[i]));
                if (d == null) {
                    return null;
                }
                v[i] = d;
            }
            v[0] = ((int) v[0]) & 0xFFFF;
            v[2] = ((int) v[2]) & 0xFFFF;
            v[5] = ((int) v[5]) & 0xFFFF;
            v[4] = v[4] * 0.1;
            v[8] = v[8] * 0.1;
            return v;
        }

        private void drawPhaseRow(Graphics2D g, int y, Color[] band, String label, double[] v, boolean actual) {
            int h = 46;
            g.setPaint(new GradientPaint(4, y, band[0], 4, y + h, band[1]));
            g.fillRoundRect(4, y, 984, h, 8, 8);
            fieldBox(g, 8, y + 8, 34, 28, label, true);
            int ops = v == null ? 0 : (int) v[0] & 0xFFFF;
            for (int i = 0; i < 10; i++) {
                drawSq(g, 46 + i * 36 + 5, y + 10, 26, v != null && (ops & (1 << OP_BITS[i])) != 0,
                        new Color(0x3CFF3C), new Color(0x0C4A0C));
            }
            fieldBox(g, 416, y + 8, 56, 28, v == null ? null : fmt(v[1], 0), false);
            fieldBox(g, 474, y + 8, 56, 28, v == null ? null : fmt(v[3], 0), false);
            fieldBox(g, 532, y + 8, 66, 28, v == null ? null : fmt(v[4], 1), false);
            int bits = v == null ? 0 : (int) v[2] & 0xFFFF;
            drawSq(g, 602, y + 10, 26, v != null && (bits & 0x02) != 0,
                    actual ? new Color(0x3CFF3C) : new Color(0xFF3A3A), actual ? new Color(0x0C4A0C) : new Color(0x5A1010));
            boolean up = v != null && (bits & 0x04) != 0;
            boolean down = v != null && (bits & 0x10) != 0;
            drawSq(g, 634, y + 10, 26, up || down, new Color(0x3CFF3C), new Color(0x0C4A0C));
            drawArrow(g, 664, y + 8, up, true);
            drawArrow(g, 664, y + 25, down, false);
            int ctrl = v == null ? 0 : (int) v[5] & 0xFFFF;
            drawSq(g, 702, y + 10, 26, v != null && (ctrl & 0x04) != 0, new Color(0xE0E0E0), new Color(0x404048));
            fieldBox(g, 732, y + 8, 54, 28, v == null ? null : fmt(v[6], 0), false);
            fieldBox(g, 788, y + 8, 56, 28, v == null ? null : fmt(v[7], 0), false);
            fieldBox(g, 846, y + 8, 66, 28, v == null ? null : fmt(v[8], 1), false);
            drawSq(g, 916, y + 10, 26, v != null && (ctrl & 0x01) != 0, new Color(0x40A0FF), new Color(0x103060));
            drawSq(g, 948, y + 10, 26, v != null && (ctrl & 0x02) != 0, new Color(0x40A0FF), new Color(0x103060));
        }

        private static String fmt(double d, int dec) {
            return String.format(LOC, "%." + dec + "f", d);
        }

        private void fieldBox(Graphics2D g, int x, int y, int w, int h, String text, boolean center) {
            g.setColor(new Color(0xEEF2FA));
            g.fillRect(x, y, w, h);
            bevel(g, x, y, w, h);
            fit(g, text == null ? "--" : text, x, y, w - 2, h, text == null ? new Color(0x808080) : Color.BLACK, center ? 0 : 1, true);
        }

        private void drawSq(Graphics2D g, int x, int y, int s, boolean lit, Color bright, Color dark) {
            g.setColor(new Color(0x303030));
            g.fillRect(x, y, s, s);
            g.setPaint(lit ? new GradientPaint(x, y, bright, x + s, y + s, bright.darker())
                    : new GradientPaint(x, y, dark.brighter(), x + s, y + s, dark));
            g.fillRect(x + 2, y + 2, s - 4, s - 4);
        }

        private void drawArrow(Graphics2D g, int x, int y, boolean lit, boolean up) {
            int[] xs = {x + 12, x + 2, x + 22};
            int[] ys = up ? new int[]{y, y + 14, y + 14} : new int[]{y + 14, y, y};
            g.setColor(lit ? new Color(0x20C020) : new Color(0x50706A));
            g.fillPolygon(xs, ys, 3);
        }

        private void drawNav(Graphics2D g) {
            g.setColor(new Color(0xB4B4B4));
            g.fillRect(0, 648, W, 120);
            if ("005_Display".equals(contentName)) {
                drawDisplayStatus(g);
            }
            g.translate(BAR_X, BAR_Y);
            B1BottomBar.paint(g, barButtons());
            g.translate(-BAR_X, -BAR_Y);
            g.setColor(new Color(0x404040));
            g.setFont(new Font("Arial", Font.PLAIN, 10));
            String st = src.connected() ? "PLC: " + src.status() : "PLC non connect\u00e9 - " + src.status();
            if (System.currentTimeMillis() < messageUntil) {
                st = message;
            }
            g.drawString(st + (B1RecipeTransfer.writeEnabled()
                    ? "   |   \u00c9CRITURE ACTIV\u00c9E : recettes uniquement (T\u00e9l\u00e9charger / Envoyer au PLC)   |   F2 : adresses"
                    : "   |   LECTURE SEULE   |   F2 : adresses"), 10, 765);
        }

        /** Schermata Display, fascia sopra la barra: spie AUTO / MANUAL a sinistra e ricetta attuale al centro. */
        private void drawDisplayStatus(Graphics2D g) {
            Boolean auto = src.connected() ? src.bool("mode_auto") : null;
            drawModeLamp(g, 8, 654, 178, 42, "AUTO", Boolean.TRUE.equals(auto), new Color(0x2BFF1F));
            drawModeLamp(g, 190, 654, 178, 42, "MANUEL", Boolean.FALSE.equals(auto), new Color(0xFF2828));
            // ricetta attuale (nome riconosciuto anche dai valori, come nel sinottico)
            int x = 376, y = 654, w = 356, h = 42;
            g.setColor(new Color(0xD4D0C8));
            g.fillRect(x, y, w, h);
            bevel(g, x, y, w, h);
            String rn = src.connected() ? src.str("recipe_name") : null;
            String name = rn == null || rn.isEmpty() ? "--" : B1RecipeBridge.displayName(rn);
            g.setFont(new Font("Arial", Font.PLAIN, 10));
            g.setColor(new Color(0x505050));
            g.drawString("Recette actuelle", x + 6, y + 12);
            fit(g, name, x + 2, y + 13, w - 4, 28, new Color(0x202020), -1, true);
            B1RecipeBridge br = B1_Main.BRIDGE;
            if (br != null && src.connected()) {
                B1RecipeBridge.Status st = br.status();
                Color dot;
                switch (st.state) {
                    case SAME: dot = new Color(0x20B020); break;
                    case DIFFERENT: dot = new Color(0xE08000); break;
                    case NOT_ARCHIVED: dot = new Color(0x2060C0); break;
                    default: dot = new Color(0x909090);
                }
                g.setColor(dot);
                g.fillOval(x + w - 150, y + 4, 8, 8);
                g.setFont(new Font("Arial", Font.PLAIN, 10));
                g.setColor(new Color(0x404040));
                String t = st.text.length() > 30 ? st.text.substring(0, 30) + "\u2026" : st.text;
                g.drawString(t, x + w - 138, y + 12);
            }
        }

        private void drawModeLamp(Graphics2D g, int x, int y, int w, int h, String text, boolean on, Color lit) {
            g.setColor(on ? lit : new Color(0xD8D8D8));
            g.fillRect(x, y, w, h);
            g.setColor(new Color(0x606060));
            g.drawRect(x, y, w - 1, h - 1);
            g.setColor(on ? lit.darker() : Color.WHITE);
            g.drawRect(x + 1, y + 1, w - 3, h - 3);
            g.setFont(new Font("Arial", Font.BOLD, 20));
            java.awt.FontMetrics fm = g.getFontMetrics();
            g.setColor(Color.BLACK);
            g.drawString(text, x + (w - fm.stringWidth(text)) / 2, y + (h - fm.getHeight()) / 2 + fm.getAscent());
        }

        // ---------------- disegno oggetti ----------------
        /**
         * Adattamenti al layout WinCC della schermata Display (nel codice, cosi' valgono con qualsiasi copia del file .b1s):
         * si toglie il campo velocita' duplicato (DBW58 e la sua unita') e i campi dinamici del rotore (velocita' e corrente)
         * si avvicinano alle loro descrizioni. Se il file e' gia' stato modificato (oggetti gia' a sinistra) non si sposta di nuovo.
         */
        private static final int DISPLAY_SHIFT = -200;

        private boolean skipObject(B1Screen.Obj o) {
            if ("002_Process".equals(drawingScreen) && o.name.equals("IO_RecipeID")) {
                return true;
            }
            return "005_Display".equals(drawingScreen) && (o.name.equals("IO_value4") || o.name.equals("ST_unit4"));
        }

        private int shiftX(B1Screen.Obj o) {
            if ("005_Display".equals(drawingScreen) && o.l >= 820
                    && (o.name.equals("IO_value5") || o.name.equals("ST_unit5")
                    || o.name.equals("IO_value9") || o.name.equals("ST_unit9"))) {
                return DISPLAY_SHIFT;
            }
            return 0;
        }

        private void drawScreen(Graphics2D g, B1Screen sc, int dy) {
            drawingScreen = sc.name;
            for (B1Screen.Obj o : sc.objects) {
                if (skipObject(o)) {
                    continue;
                }
                int sx = shiftX(o);
                if (sx != 0) {
                    g.translate(sx, 0);
                }
                drawObject(g, o, dy);
                if (sx != 0) {
                    g.translate(-sx, 0);
                }
            }
        }

        private void drawObject(Graphics2D g, B1Screen.Obj o, int dy) {
            {
                switch (o.kind) {
                    case "RECT": strokeShape(g, new Rectangle2D.Double(o.l, o.t + dy, o.w(), o.h())); break;
                    case "ELLIPSE": strokeShape(g, new Ellipse2D.Double(o.l, o.t + dy, o.w(), o.h())); break;
                    case "LINE":
                        if (o.w() <= 3 || o.h() <= 3) {
                            g.setColor(new Color(0x606060));
                            g.drawLine(o.l, o.t + dy, o.r, o.b + dy);
                        }
                        break;
                    case "TEXT": drawText(g, o, dy); break;
                    case "IO": drawIo(g, o, dy); break;
                    case "COMBO": drawCombo(g, o, dy); break;
                    case "BUTTON": drawButton(g, o, dy); break;
                    case "CONTROL": drawControl(g, o, dy); break;
                    case "LAMP": drawLamp(g, o, dy); break;
                    case "PIC": drawPic(g, o, dy); break;
                    default:
                }
            }
        }

        private void strokeShape(Graphics2D g, java.awt.Shape s) {
            if (s.getBounds().width > 900 && s.getBounds().height > 500) {
                return; // sfondo dell'intero picture
            }
            g.setColor(new Color(0x707070));
            g.setStroke(new BasicStroke(1f));
            g.draw(s);
        }

        private Color color(String hex, Color def) {
            if (hex == null || hex.length() != 6) {
                return def;
            }
            try {
                return new Color(Integer.parseInt(hex, 16));
            } catch (NumberFormatException e) {
                return def;
            }
        }

        private boolean hidden(B1Screen.Obj o) {
            String t = o.text.trim();
            return t.isEmpty() || t.equals("Line speed") || o.name.startsWith("ST_msg")
                    || t.equals("RecipeRowSelected") || t.matches("DB\\d+DB[XWDB][0-9_]+") || t.equals("??????");
        }

        private void drawText(Graphics2D g, B1Screen.Obj o, int dy) {
            if (hidden(o)) {
                return;
            }
            Color bg = color(o.bg, null);
            if (bg != null) {
                g.setColor(bg);
                g.fillRect(o.l, o.t + dy, o.w(), o.h());
                if (bg.equals(GRAY)) {
                    bevel(g, o.l, o.t + dy, o.w(), o.h());
                } else {
                    g.setColor(new Color(0xA0A0A0));
                    g.drawRect(o.l, o.t + dy, o.w() - 1, o.h() - 1);
                }
            }
            boolean center = bg != null && bg.equals(Color.WHITE);
            fit(g, o.text.trim(), o.l, o.t + dy, o.w(), o.h(), Color.BLACK, center ? 0 : -1, false);
        }

        /** Campi senza tag nel WinCC (valori dinamici) collegati per nome: schermata/oggetto -> chiave B1Tags (mm:ss). */
        private final java.util.Map<String, String> timeFields = java.util.Map.of(
                "005_Display/IO_value7", "cycle_time",
                "005_Display/IO_value8", "mix_time",
                "100_TopBar/IO_value1", "cycle_time");

        /**
         * Pannello in alto (uguale a quello del sinottico): i campi sono collegati alle stesse chiavi di B1Tags
         * del sinottico, cosi' i due pannelli mostrano sempre gli stessi valori. Oggetto -> chiave.
         */
        private final java.util.Map<String, String> topBarKeys = java.util.Map.of(
                "100_TopBar/IO_value2", "press_top",
                "100_TopBar/IO_value5", "speed_top",
                "100_TopBar/IO_value7", "temp",
                "100_TopBar/IO_value4", "mix_total",
                "100_TopBar/IO_value3", "mix_done",
                "100_TopBar/IO_value6", "mix_todo");

        private String drawingScreen = "";

        /** Valore da mostrare per i campi del pannello in alto (come B1_Main); null = non collegato. */
        private String topBarValue(B1Screen.Obj o) {
            String key = topBarKeys.get(drawingScreen + "/" + o.name);
            if (key == null) {
                return null;
            }
            Double v = src.num(key);
            if (v == null && key.equals("mix_todo") && src.num("mix_total") != null && src.num("mix_done") != null) {
                v = src.num("mix_total") - src.num("mix_done");
            }
            if (v == null) {
                return null;
            }
            int dec = o.fmt.contains(".") ? o.fmt.length() - o.fmt.indexOf('.') - 1 : 0;
            return String.format(LOC, "%." + dec + "f", v);
        }

        /** Lampade collegate per nome: schermata/oggetto -> chiave B1Tags (bit confermati con il registro continuo). */
        private final java.util.Map<String, String> lampKeys = java.util.Map.ofEntries(
                java.util.Map.entry("005_Display/GO_logo9", "piston_high"),
                java.util.Map.entry("005_Display/GO_logo5", "piston_float"),
                java.util.Map.entry("005_Display/GO_logo4", "piston_low"),
                java.util.Map.entry("002_Process/GO_logo6", "prod_ready"),
                java.util.Map.entry("002_Process/GO_logo5", "prod_auto"),
                java.util.Map.entry("002_Process/GO_logo1", "prod_auto_on"),
                java.util.Map.entry("002_Process/GO_logo7", "cycle_stopped"),
                java.util.Map.entry("002_Process/GO_logo3", "new_load"),
                java.util.Map.entry("002_Process/GO_logo4", "recipe_ok"),
                java.util.Map.entry("007_Production/GO_logo12", "transfer_enabled"),
                java.util.Map.entry("007_Production/GO_logo7", "new_load"),
                java.util.Map.entry("007_Production/GO_logo13", "transfer_end"),
                java.util.Map.entry("007_Production/GO_logo14", "recipe_ok"));

        private void drawIo(Graphics2D g, B1Screen.Obj o, int dy) {
            g.setColor(GRAY);
            g.fillRect(o.l, o.t + dy, o.w(), o.h());
            bevel(g, o.l, o.t + dy, o.w(), o.h());
            String v = null;
            String timeKey = o.tag.isEmpty() ? timeFields.get(drawingScreen + "/" + o.name) : null;
            String topVal = topBarValue(o);
            if (topVal != null) {
                v = topVal;
            }
            if ("002_Process".equals(drawingScreen) && o.name.equals("IO_RecipeName")) {
                String rn = src.connected() ? src.str("recipe_name") : null;
                fit(g, rn == null || rn.isEmpty() ? "--" : B1RecipeBridge.displayName(rn), o.l, o.t + dy, o.w(), o.h(),
                        Color.BLACK, -1, true);
                return;
            }
            if (timeKey != null) {
                Double sec = src.num(timeKey);
                if (sec != null) {
                    long s = Math.max(0, sec.longValue());
                    v = String.format("%d:%02d", s / 60, s % 60);
                }
            }
            if (topVal == null && !o.tag.isEmpty() && o.tag.startsWith("DB")) {
                Double raw = src.raw(o.tag);
                if (raw != null) {
                    double sc = B1Screen.SCALE.getOrDefault(o.tag, 1.0);
                    int dec = o.fmt.contains(".") ? o.fmt.length() - o.fmt.indexOf('.') - 1 : 0;
                    v = String.format(LOC, "%." + dec + "f", raw * sc);
                }
            }
            fit(g, v == null ? "--" : v, o.l, o.t + dy, o.w(), o.h(),
                    v == null ? new Color(0x707070) : Color.BLACK, 1, true);
            tagOverlay(g, o, dy);
        }

        private void drawCombo(Graphics2D g, B1Screen.Obj o, int dy) {
            g.setColor(Color.WHITE);
            g.fillRect(o.l, o.t + dy, o.w(), o.h());
            bevel(g, o.l, o.t + dy, o.w(), o.h());
            fit(g, "--", o.l, o.t + dy, o.w() - 14, o.h(), new Color(0x707070), -1, false);
            g.setColor(new Color(0xD0D0D0));
            g.fillRect(o.l + o.w() - 16, o.t + dy + 2, 14, o.h() - 4);
            g.setColor(Color.BLACK);
            int cy = o.t + dy + o.h() / 2;
            g.fillPolygon(new int[]{o.l + o.w() - 13, o.l + o.w() - 5, o.l + o.w() - 9},
                    new int[]{cy - 2, cy - 2, cy + 3}, 3);
        }

        private void drawButton(Graphics2D g, B1Screen.Obj o, int dy) {
            g.setPaint(new GradientPaint(0, o.t + dy, Color.WHITE, 0, o.b + dy, new Color(0xCFCFCF)));
            g.fillRect(o.l, o.t + dy, o.w(), o.h());
            g.setColor(new Color(0x808080));
            g.drawRect(o.l, o.t + dy, o.w() - 1, o.h() - 1);
            if (!hidden(o)) {
                fit(g, o.text.trim(), o.l, o.t + dy, o.w(), o.h(), Color.BLACK, 0, false);
            }
        }

        private void drawControl(Graphics2D g, B1Screen.Obj o, int dy) {
            if (o.w() > 900 && o.h() > 500) {
                return;
            }
            if ("005_Display".equals(contentName) && o.w() > 200 && o.h() > 150) {
                // il controllo della schermata Display e' il trend con la sola temperatura (WinCC: finestra 20 minuti)
                B1MiniTrend.paint(g, o.l, o.t + dy, o.w(), o.h(), 20L * 60_000L);
                g.setColor(new Color(0x909090));
                g.drawRect(o.l, o.t + dy, o.w() - 1, o.h() - 1);
                return;
            }
            g.setColor(new Color(0xF4F4F4));
            g.fillRect(o.l, o.t + dy, o.w(), o.h());
            g.setColor(new Color(0x909090));
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[]{4f, 3f}, 0f));
            g.drawRect(o.l, o.t + dy, o.w() - 1, o.h() - 1);
            g.setStroke(new BasicStroke(1f));
            if (o.w() > 60 && o.h() > 16) {
                String label = o.name.contains("Control") || o.name.contains("Contr")
                        ? "[contr\u00f4le WinCC]" : o.name;
                fit(g, label, o.l, o.t + dy, o.w(), o.h(), new Color(0x707070), 0, false);
            }
        }

        private void drawLamp(Graphics2D g, B1Screen.Obj o, int dy) {
            Color bright, dark;
            String lampKind = o.lamp;
            if ("002_Process".equals(contentName) && (o.name.equals("GO_logo1") || o.name.equals("GO_logo5"))) {
                lampKind = "GREEN"; // nella schermata reale Auto cycle ON e Production auto sono verdi da accese
            }
            switch (lampKind) {
                case "RED": bright = new Color(0xFF3A3A); dark = new Color(0x6E1010); break;
                case "ORANGE": bright = new Color(0xFFA030); dark = new Color(0x6B4A1A); break;
                default: bright = new Color(0x3CFF3C); dark = new Color(0x0C4A0C);
            }
            int x = o.l, y = o.t + dy, w = Math.min(o.w(), 25), h = Math.min(o.h(), 25);
            String key = lampKeys.get(contentName + "/" + o.name);
            Boolean state = key == null ? null : src.bool(key);
            g.setColor(new Color(0x404040));
            g.fillRect(x, y, w, h);
            g.setPaint(Boolean.TRUE.equals(state) ? new GradientPaint(x, y, bright, x + w, y, bright.darker())
                    : new GradientPaint(x, y, dark.brighter(), x + w, y, dark));
            g.fillRect(x + 2, y + 2, w - 4, h - 4);
            if (key == null) {
                g.setColor(new Color(255, 255, 255, 120));
                g.drawLine(x + 3, y + h - 4, x + w - 4, y + 3); // non collegata
            }
            tagOverlay(g, o, dy);
        }

        private void drawPic(Graphics2D g, B1Screen.Obj o, int dy) {
            if (o.img.toLowerCase(Locale.ROOT).contains("gate valve")) {
                g.setColor(Color.BLACK);
                g.drawLine(o.l, o.t + dy, o.r, o.b + dy);
                g.drawLine(o.l, o.b + dy, o.r, o.t + dy);
                g.drawLine(o.l, o.t + dy, o.l, o.b + dy);
                g.drawLine(o.r, o.t + dy, o.r, o.b + dy);
            }
        }

        private void bevel(Graphics2D g, int x, int y, int w, int h) {
            g.setColor(new Color(0x808080));
            g.drawLine(x, y, x + w - 1, y);
            g.drawLine(x, y, x, y + h - 1);
            g.setColor(Color.WHITE);
            g.drawLine(x, y + h - 1, x + w - 1, y + h - 1);
            g.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
        }

        /** Testo ridotto fino a stare nel riquadro. align: -1 sinistra, 0 centro, 1 destra. */
        private void fit(Graphics2D g, String text, int x, int y, int w, int h, Color c, int align, boolean bold) {
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

        private void tagOverlay(Graphics2D g, B1Screen.Obj o, int dy) {
            if (!overlay || o.tag.isEmpty()) {
                return;
            }
            g.setFont(new Font("Arial", Font.PLAIN, 9));
            String s = o.tag + (o.bit >= 0 && o.kind.equals("LAMP") ? " (bit ?)" : "");
            FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(255, 255, 200, 230));
            g.fillRect(o.l, o.t + dy - 10, fm.stringWidth(s) + 4, 10);
            g.setColor(new Color(0, 90, 0));
            g.drawString(s, o.l + 2, o.t + dy - 2);
        }
    }
}
