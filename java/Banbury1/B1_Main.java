package Banbury1;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.AbstractAction;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sinottico Banbury 1 (MCC-240 T) \u2013 replica della schermata WinCC "001_Synoptic".
 *
 * MODALITA' SOLA LETTURA: i pulsanti di comando sono disegnati ma NON scrivono nel PLC
 * (CPU di sicurezza; gli indirizzi dei comandi non sono ancora confermati).
 *
 * Uso: java Banbury1.B1_Main [ip] [--demo] [--tags file] [--snapshot out.png] [--write-tags file]
 *      F2 = mostra la chiave/indirizzo di ogni campo.
 */
public class B1_Main extends JFrame {

    static final String VERSION = "B1 V5.20";
    static final String DEFAULT_IP = "192.168.10.100";
    static final Path DEFAULT_TAGS = Paths.get("C:\\_tools\\Banbury1\\b1_tags.properties");

    private final SynPanel panel;

    public B1_Main(B1Source src, B1RecipeBridge bridge, B1Tags tags) {
        super("Banbury 1 - MCC-240 T - " + VERSION);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        panel = new SynPanel(src, bridge, tags);
        INSTANCE = this;
        BRIDGE = bridge;
        SOURCE = src;
        TAGS = tags;
        setContentPane(panel);
        pack();
        setLocationRelativeTo(null);
    }

    public static void main(String[] args) throws Exception {
        String ip = DEFAULT_IP;
        boolean demo = false;
        String snap = null;
        int period = 300;
        String screen = null;
        boolean overlay = false;
        Path tagFile = DEFAULT_TAGS;
        Path recipeDir = B1RecipeEditor.DEFAULT_DIR;
        Path layoutFile = B1RecipeEditor.DEFAULT_LAYOUT;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--demo": demo = true; break;
                case "--snapshot": snap = args[++i]; break;
                case "--tags": tagFile = Paths.get(args[++i]); break;
                case "--recipes-dir": recipeDir = Paths.get(args[++i]); break;
                case "--layout": layoutFile = Paths.get(args[++i]); break;
                case "--period": period = Integer.parseInt(args[++i]); break;
                case "--screen": screen = args[++i]; break;
                case "--overlay": overlay = true; break;
                case "--write-tags":
                    B1Tags.writeTemplate(Paths.get(args[++i]));
                    return;
                default: ip = args[i];
            }
        }
        if (snap == null && !demo) {
            B1DefaultConfig.ensureAll(); // crea i file di configurazione mancanti (valori predefiniti, scrittura PLC spenta)
        }
        if (!new File(tagFile.toString()).exists() && new File("b1_tags.properties").exists()) {
            tagFile = Paths.get("b1_tags.properties");
        }
        if (!new File(layoutFile.toString()).exists() && new File("b1_recipe_layout.properties").exists()) {
            layoutFile = Paths.get("b1_recipe_layout.properties");
        }
        if (!Files.isDirectory(recipeDir) && !System.getProperty("os.name", "").toLowerCase().contains("win")) {
            recipeDir = Paths.get("b1_recipes");
        }
        System.out.println(demo ? "Banbury 1: modo DEMO (valori di esempio)" : "Banbury 1: PLC " + ip + " (sola lettura)");
        final B1RecipeLayout layout = B1RecipeLayout.load(layoutFile);
        final B1RecipeRepository repo = B1RecipeRepositories.open(layout, recipeDir);
        PRODUCTIONS = B1ProductionRepositories.open(B1ProductionRepositories.DEFAULT_FILE);
        final B1RecipeBridge bridge = new B1RecipeBridge(repo, layout, ip);
        Path labelCfg = Paths.get("C:\\_tools\\Banbury1\\b1_label_sql.properties");
        if (!Files.exists(labelCfg) && Files.exists(Paths.get("b1_label_sql.properties"))) {
            labelCfg = Paths.get("b1_label_sql.properties");
        }
        B1LabelDialog.setStore(B1SqlLabelStore.load(labelCfg));
        final B1Tags tagsObj = B1Tags.load(tagFile);
        Path alarmCfg = Paths.get("C:\\_tools\\Banbury1\\b1_alarms.properties");
        if (!Files.exists(alarmCfg) && Files.exists(Paths.get("b1_alarms.properties"))) {
            alarmCfg = Paths.get("b1_alarms.properties");
        }
        ENGINE = B1AlarmEngine.load(alarmCfg, Paths.get("C:\\_tools\\Banbury1\\b1_alarm_log.csv"));
        if (!demo && snap == null) {
            B1GovoniSilos.start();
        }
        SQL_CONFIG = Files.exists(labelCfg) ? labelCfg : Paths.get("b1_label_sql.properties");
        final B1Source src = demo ? new B1DemoSource()
                : newPlcSource(tagsObj, ip, layout, period, ENGINE.dbs());
        if (snap != null && "trend".equals(screen)) {
            B1TrendPanel.snapshot(snap, overlay);
            System.exit(0);
        }
        if (snap == null) {
            // registrazione in background dei valori del trend (2 giorni, anche a finestra chiusa)
            Path hist = Files.isDirectory(Paths.get("C:\\_tools\\Banbury1")) ? Paths.get("C:\\_tools\\Banbury1\\b1_trend_history.csv")
                    : Paths.get("b1_trend_history.csv");
            B1TrendRecorder.start(src, B1TrendStore.shared(B1TrendRecorder.seriesIds()), demo ? null : hist, demo ? null : SQL_CONFIG);
        }
        if (snap != null && screen != null) {
            System.setProperty("java.awt.headless", "true");
            for (int i = 0; i < 40 && !src.connected(); i++) {
                Thread.sleep(100);
            }
            Thread.sleep(700);
            BRIDGE = bridge;
            SOURCE = src;
            B1ScreensWindow.snapshot(src, screen, snap, overlay);
            src.close();
            System.exit(0);
        }
        if (snap != null) {
            System.setProperty("java.awt.headless", "true");
            for (int i = 0; i < 40 && !src.connected(); i++) {
                Thread.sleep(100);
            }
            Thread.sleep(700);
            SynPanel p = new SynPanel(src, bridge, tagsObj);
            p.setSize(SynPanel.BASE_W, SynPanel.BASE_H);
            BufferedImage img = new BufferedImage(SynPanel.BASE_W, SynPanel.BASE_H, BufferedImage.TYPE_INT_RGB);
            for (int pass = 0; pass < 2; pass++) { // 1a passata: avvia il confronto ricetta
                Graphics2D g = img.createGraphics();
                p.paint(g);
                g.dispose();
                Thread.sleep(900);
            }
            ImageIO.write(img, "png", new File(snap));
            src.close();
            System.exit(0);
        }
        SwingUtilities.invokeLater(() -> new B1_Main(src, bridge, tagsObj).setVisible(true));
    }

    /** Motore allarmi e configurazione SQL condivisi con le finestre aperte dal sinottico. */
    static volatile B1AlarmEngine ENGINE = new B1AlarmEngine(new java.util.ArrayList<>(), null);
    /** Finestra principale (per il pulsante Synoptic delle altre finestre), ricetta e tag condivisi. */
    static volatile B1_Main INSTANCE;
    static volatile B1RecipeBridge BRIDGE;
    static volatile B1Source SOURCE;
    /** Archivio delle produzioni (pagina Production). */
    static volatile B1ProductionRepository PRODUCTIONS;
    static volatile B1Tags TAGS;

    /** Porta in primo piano il sinottico. */
    static void showMain() {
        SwingUtilities.invokeLater(() -> {
            B1_Main m = INSTANCE;
            if (m != null) {
                m.setExtendedState(m.getExtendedState() & ~JFrame.ICONIFIED);
                m.toFront();
            }
        });
    }
    static volatile Path SQL_CONFIG = Paths.get("b1_label_sql.properties");

    private static B1Source newPlcSource(B1Tags tags, String ip, B1RecipeLayout layout, int period,
                                         java.util.Set<Integer> alarmDbs) {
        String host = ip;
        int port = 102;
        int c = ip.lastIndexOf(':');
        if (c > 0) {
            host = ip.substring(0, c);
            port = Integer.parseInt(ip.substring(c + 1));
        }
        java.util.Set<Integer> extra = new java.util.TreeSet<>();
        extra.add(layout.db);
        extra.addAll(alarmDbs);
        return new B1PlcSource(tags, host, port, 0, 2, period, extra);
    }

    // =====================================================================
    static final class SynPanel extends JComponent {
        static final int BASE_W = 1030;
        static final int BASE_H = 800;
        /** Le coordinate sono quelle dello screenshot; si trasla l'origine una sola volta. */
        static final int OX = 5;
        static final int OY = 33;

        private static final Locale LOC = Locale.ITALY;
        private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        private static final Color BG1 = new Color(0xC6C6C6);
        private static final Color BG2 = new Color(0xE2E2E2);
        private static final Color FIELD_GRAY = new Color(0xD4D0C8);
        private static final Color FIELD_GREEN = new Color(0xC8FFC8);
        private static final Color LABEL_BG = new Color(0xD8D4CC);
        private static final Color DARK = new Color(0x303030);
        private static final Color BORDER = new Color(0x606060);

        private final B1Source src;
        private final B1RecipeBridge bridge;
        private final B1Tags tags;
        private final Font fNormal = new Font("Arial", Font.PLAIN, 14);
        private final Font fBold = new Font("Arial", Font.BOLD, 15);
        private final Font fBig = new Font("Arial", Font.BOLD, 20);
        private final Font fSmall = new Font("Arial", Font.PLAIN, 10);
        private final Font fLabel = new Font("Arial", Font.PLAIN, 12);

        private boolean overlay;
        private final B1MixerArt art = new B1MixerArt();
        private long lastRotorNanos;
        private long lastAlarmPoll;
        private String message = "";
        private long messageUntil;
        private final List<Hit> hits = new ArrayList<>();

        private static final class Hit {
            final int x, y, w, h;
            final String name;
            final Runnable action;

            Hit(int x, int y, int w, int h, String name) {
                this(x, y, w, h, name, null);
            }

            /** action != null: navigazione (non e' un comando al PLC). */
            Hit(int x, int y, int w, int h, String name, Runnable action) {
                this.x = x;
                this.y = y;
                this.w = w;
                this.h = h;
                this.name = name;
                this.action = action;
            }
        }

        SynPanel(B1Source src, B1RecipeBridge bridge, B1Tags tags) {
            this.src = src;
            this.bridge = bridge;
            this.tags = tags;
            setPreferredSize(new Dimension(BASE_W, BASE_H));
            setDoubleBuffered(true);
            javax.swing.ToolTipManager.sharedInstance().registerComponent(this);
            javax.swing.ToolTipManager.sharedInstance().setInitialDelay(300);
            new Timer(60, e -> {
                long nowMs = System.currentTimeMillis();
                if (nowMs - lastAlarmPoll >= 1000) {
                    lastAlarmPoll = nowMs;
                    ENGINE.poll(src, java.time.LocalDateTime.now());
                }
                if (bridge != null) {
                    bridge.offer(src.dbImage(bridge.layout().db));
                }
                // animazione del mixer (PLC3_Main): pistone, porte, rotori ai giri reali del PLC
                long tNow = System.nanoTime();
                double dt = lastRotorNanos == 0 ? 0 : Math.min(0.25, (tNow - lastRotorNanos) / 1e9);
                lastRotorNanos = tNow;
                pistonArrowLit(true); // aggiorna l'ultimo fine corsa
                Double stepsNow = src.num("steps");
                art.tick(dt, src.num("rpm_act"), Boolean.TRUE.equals(src.bool("piston_high")),
                        Boolean.TRUE.equals(src.bool("piston_low")), Boolean.TRUE.equals(src.bool("piston_float")),
                        Boolean.TRUE.equals(src.bool("piston_move")), Boolean.TRUE.equals(src.bool("door_load_open")),
                        Boolean.TRUE.equals(src.bool("door_dis_open")), stepsNow != null && stepsNow > 0,
                        Boolean.TRUE.equals(src.bool("mode_auto")), Boolean.TRUE.equals(silosOpenState()));
                repaint();
            }).start();
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), "label");
            getActionMap().put("label", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    B1LabelDialog.open((JFrame) SwingUtilities.getWindowAncestor(SynPanel.this), bridge);
                }
            });
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0), "raw");
            getActionMap().put("raw", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    B1RawViewer.open(src, tags);
                }
            });
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "overlay");
            getActionMap().put("overlay", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    overlay = !overlay;
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int px = (int) (e.getX() / scaleX()) + OX;
                    int py = (int) (e.getY() / scaleY()) + OY;
                    for (Hit h : hits) {
                        if (px >= h.x && px < h.x + h.w && py >= h.y && py < h.y + h.h) {
                            if (h.action != null) {
                                h.action.run();
                                return;
                            }
                            message = "'" + h.name + "' : non disponible (synoptique en LECTURE SEULE)";
                            messageUntil = System.currentTimeMillis() + 4000;
                            return;
                        }
                    }
                }
            });
            requestFocusInWindow();
        }

        // il sinottico riempie tutta la finestra (come il WinCC a schermo intero): scala separata in larghezza e altezza
        private double scaleX() {
            return Math.max(0.1, getWidth() / (double) BASE_W);
        }

        private double scaleY() {
            return Math.max(0.1, getHeight() / (double) BASE_H);
        }

        // ---------------------------------------------------------------
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
            g.translate(-OX, -OY);
            hits.clear();
            drawAll(g);
            g.dispose();
        }

        private void drawAll(Graphics2D g) {
            g.setPaint(new GradientPaint(0, OY, BG1, 0, OY + BASE_H, BG2));
            g.fillRect(OX, OY, BASE_W, BASE_H);
            drawTopBar(g);
            drawRecipeBar(g);
            drawPiston(g);
            drawRotors(g);
            drawLube(g);
            drawMachine(g);
            drawSilosDoor(g);
            drawLotInfo(g);
            drawWeightAndDischarge(g);
            drawMalaxeurPret(g);
            drawCycleMalaxeur(g);
            drawPositions(g);
            drawProduction(g);
            drawBottomBar(g);
            drawAlarms(g);
            drawClock(g);
        }

        // ---------------- primitive ----------------
        private void box(Graphics2D g, int x, int y, int w, int h, Color fill, Color border) {
            g.setColor(fill);
            g.fillRect(x, y, w, h);
            g.setColor(border);
            g.drawRect(x, y, w - 1, h - 1);
        }

        private void sunken(Graphics2D g, int x, int y, int w, int h, Color fill) {
            g.setColor(fill);
            g.fillRect(x, y, w, h);
            g.setColor(new Color(0x808080));
            g.drawLine(x, y, x + w - 1, y);
            g.drawLine(x, y, x, y + h - 1);
            g.setColor(Color.WHITE);
            g.drawLine(x, y + h - 1, x + w - 1, y + h - 1);
            g.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
        }

        private void text(Graphics2D g, String t, int x, int y, int w, int h, Font f, Color c, int align) {
            g.setFont(f);
            g.setColor(c);
            FontMetrics fm = g.getFontMetrics();
            int tx = align < 0 ? x + 5 : (align > 0 ? x + w - fm.stringWidth(t) - 5 : x + (w - fm.stringWidth(t)) / 2);
            int ty = y + (h - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(t, tx, ty);
        }

        private void title(Graphics2D g, String t, int x, int y, int w, int h) {
            box(g, x, y, w, h, Color.WHITE, new Color(0xA0A0A0));
            text(g, t, x, y, w, h, fBold, Color.BLACK, 0);
        }

        private void label(Graphics2D g, String t, int x, int y, int w, int h) {
            sunken(g, x, y, w, h, LABEL_BG);
            java.awt.Shape oldClip = g.getClip();
            g.clipRect(x + 1, y + 1, w - 2, h - 2);
            text(g, t, x, y, w, h, w > 100 ? fLabel : fNormal, Color.BLACK, -1);
            g.setClip(oldClip);
        }

        /** Campo valore: key per l'overlay; v null -> "--". */
        private void field(Graphics2D g, int x, int y, int w, int h, String key, String v, boolean green, Font f) {
            sunken(g, x, y, w, h, green ? FIELD_GREEN : FIELD_GRAY);
            text(g, v == null ? "--" : v, x, y, w, h, f, v == null ? new Color(0x707070) : Color.BLACK, 1);
            keyTag(g, key, x, y);
        }

        private void unit(Graphics2D g, String u, int x, int y, int w, int h) {
            sunken(g, x, y, w, h, FIELD_GRAY);
            text(g, u, x, y, w, h, fBold, Color.BLACK, 0);
        }

        private void keyTag(Graphics2D g, String key, int x, int y) {
            if (!overlay || key == null) {
                return;
            }
            g.setFont(fSmall);
            String s = key + " = " + src.spec(key);
            FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(255, 255, 200, 230));
            g.fillRect(x, y - 11, fm.stringWidth(s) + 4, 11);
            g.setColor(src.spec(key).startsWith("?") ? Color.RED : new Color(0, 90, 0));
            g.drawString(s, x + 2, y - 2);
        }

        private String num(String key, int dec) {
            Double v = src.num(key);
            if (v == null) {
                return null;
            }
            return String.format(LOC, "%." + dec + "f", v);
        }

        private String mmss(String key) {
            Double v = src.num(key);
            if (v == null) {
                return null;
            }
            long s = Math.max(0, v.longValue());
            return String.format("%d:%02d", s / 60, s % 60);
        }

        private enum LampKind { GREEN, RED, ORANGE }

        private void lamp(Graphics2D g, int x, int y, String key, LampKind kind) {
            lampState(g, x, y, key, kind, key == null ? null : src.bool(key));
        }

        private void lampState(Graphics2D g, int x, int y, String key, LampKind kind, Boolean on) {
            Color bright, dark;
            switch (kind) {
                case RED: bright = new Color(0xFF3A3A); dark = new Color(0x6E1010); break;
                case ORANGE: bright = new Color(0xFFA030); dark = new Color(0x6B4A1A); break;
                default: bright = new Color(0x3CFF3C); dark = new Color(0x0C4A0C);
            }
            int w = 22, h = 24;
            Color base = Boolean.TRUE.equals(on) ? bright : dark;
            g.setColor(new Color(0x404040));
            g.fillRect(x, y, w, h);
            g.setPaint(new GradientPaint(x, y, base.brighter(), x + w, y, base));
            g.fillRect(x + 2, y + 2, w - 4, h - 4);
            if (Boolean.TRUE.equals(on)) {
                g.setColor(new Color(255, 255, 255, 150));
                g.fillRect(x + 4, y + 4, 5, h - 8);
            } else {
                g.setColor(new Color(255, 255, 255, 40));
                g.fillRect(x + 2, y + 2, 2, h - 4);
            }
            if (on == null) {
                g.setColor(new Color(255, 255, 255, 120));
                g.drawLine(x + 3, y + h - 4, x + w - 4, y + 3);
            }
            keyTag(g, key, x, y);
        }

        private void lampRow(Graphics2D g, int lx, int ly, int labelX, int labelW,
                             String key, LampKind kind, String text) {
            lamp(g, lx, ly, key, kind);
            label(g, text, labelX, ly, labelW, 24);
        }

        private void arrowButton(Graphics2D g, int x, int y, int w, int h, boolean up, String name) {
            g.setPaint(new GradientPaint(x, y, Color.WHITE, x, y + h, new Color(0xD8D8D8)));
            g.fillRect(x, y, w, h);
            g.setColor(new Color(0x808080));
            g.drawRect(x, y, w - 1, h - 1);
            Polygon p = new Polygon();
            int cx = x + w / 2, cy = y + h / 2;
            if (up) {
                p.addPoint(cx - 6, cy + 4); p.addPoint(cx + 6, cy + 4); p.addPoint(cx, cy - 6);
            } else {
                p.addPoint(cx - 6, cy - 4); p.addPoint(cx + 6, cy - 4); p.addPoint(cx, cy + 6);
            }
            g.setColor(new Color(0x303030));
            g.fillPolygon(p);
            hits.add(new Hit(x, y, w, h, name));
        }

        /**
         * Frecce del pistone: il bit osservato (piston_move) indica solo "in movimento", sia in salita sia in discesa.
         * La direzione si ricava dall'ultimo fine corsa raggiunto: partito dal basso = sale, partito dall'alto = scende.
         * (Se si trova il bit vero della direzione si sostituisce questo calcolo.)
         */
        private int pistonLastEnd; // 0 sconosciuto, 1 alto, 2 basso

        private boolean pistonArrowLit(boolean up) {
            if (Boolean.TRUE.equals(src.bool("piston_high"))) {
                pistonLastEnd = 1;
            } else if (Boolean.TRUE.equals(src.bool("piston_low"))) {
                pistonLastEnd = 2;
            }
            if (!Boolean.TRUE.equals(src.bool("piston_move"))) {
                return false;
            }
            return up ? pistonLastEnd == 2 : pistonLastEnd == 1;
        }

        private void bigArrow(Graphics2D g, int x, int y, boolean up, boolean lit) {
            Polygon p = new Polygon();
            if (up) {
                p.addPoint(x, y + 30); p.addPoint(x + 28, y + 30); p.addPoint(x + 14, y);
            } else {
                p.addPoint(x, y); p.addPoint(x + 28, y); p.addPoint(x + 14, y + 30);
            }
            g.setPaint(lit ? new GradientPaint(x, y, new Color(0x7CFF7C), x + 28, y, new Color(0x1FA01F))
                    : new GradientPaint(x, y, new Color(0xA0A0A0), x + 28, y, new Color(0x606060)));
            g.fillPolygon(p);
            g.setColor(new Color(0x404040));
            g.drawPolygon(p);
        }

        private Shape logoBlade(float x, float y, float w, float h) {
            float midX = x + w / 2f;
            float rightX = x + w;
            float bottomY = y + h;
            float midY = y + h / 2f;
            Path2D p = new Path2D.Float();
            p.moveTo(midX, y);
            p.curveTo(x + w * 0.68f, y + h * 0.12f, rightX, y + h * 0.35f, rightX, midY);
            p.curveTo(rightX, y + h * 0.65f, x + w * 0.68f, y + h * 0.88f, midX, bottomY);
            p.curveTo(x + w * 0.32f, y + h * 0.88f, x, y + h * 0.65f, x, midY);
            p.curveTo(x, y + h * 0.35f, x + w * 0.32f, y + h * 0.12f, midX, y);
            p.closePath();
            return p;
        }

        private void drawLogo(Graphics2D g, int x, int y, int w, int h) {
            box(g, x, y, w, h, new Color(0xF2F2F2), new Color(0x909090));
            for (int i = 0; i < 5; i++) {
                float bx = x + 7 + i * 8f;
                Shape blade = logoBlade(bx, y + 6, 6.5f, h - 12);
                g.setPaint(new GradientPaint(bx, y + 6, new Color(0, 110, 225), bx + 6.5f, y + h - 6, new Color(0, 180, 255)));
                g.fill(blade);
                g.setColor(new Color(80, 200, 255, 120));
                g.draw(blade);
            }
            g.setFont(new Font("Arial", Font.BOLD, 18));
            FontMetrics fm = g.getFontMetrics();
            int ty = y + (h + fm.getAscent()) / 2 - 3;
            int tx = x + 51;
            g.setColor(new Color(0, 160, 255));
            g.drawString("MONDO", tx + 2, ty - 1);
            g.setColor(Color.BLACK);
            g.drawString("MONDO", tx, ty);
        }

        // ---------------- sezioni ----------------
        private void drawTopBar(Graphics2D g) {
            // logo MONDO (pale blu + scritta con ombra azzurra, come in Main0), data/ora e nome schermata
            drawLogo(g, 12, 37, 124, 50);
            sunken(g, 140, 38, 130, 24, LABEL_BG);
            text(g, LocalDateTime.now().format(DT), 140, 38, 130, 24, new Font("Arial", Font.BOLD, 12), Color.BLACK, 0);
            sunken(g, 140, 63, 130, 24, LABEL_BG);
            text(g, "Synoptique Banb1 H", 140, 63, 130, 24, fLabel, Color.BLACK, 0);

            label(g, "Pression", 488, 40, 70, 22);
            field(g, 558, 40, 62, 22, "press_top", num("press_top", 1), false, fBig);
            unit(g, "bar", 622, 40, 38, 22);
            label(g, "Temp.", 488, 64, 70, 22);
            field(g, 558, 64, 62, 22, "temp", num("temp", 0), false, fBig);
            unit(g, "\u00b0C", 622, 64, 38, 22);
            label(g, "Vitesse", 663, 40, 70, 22);
            field(g, 733, 40, 70, 22, "speed_top", num("speed_top", 0), false, fBig);
            unit(g, "rpm", 805, 40, 48, 22);
            label(g, "Cycle", 663, 64, 70, 22);
            field(g, 733, 64, 70, 22, "cycle_time", mmss("cycle_time"), false, fBig);
            unit(g, "mm:ss", 805, 64, 48, 22);

            text(g, "M\u00e9langes", 905, 36, 110, 12, fSmall, DARK, 0);
            text(g, "total", 862, 47, 44, 12, fSmall, DARK, 0);
            text(g, "fait", 914, 47, 44, 12, fSmall, DARK, 0);
            text(g, "\u00e0 faire", 966, 47, 44, 12, fSmall, DARK, 0);
            String todo = num("mix_todo", 0);
            if (todo == null && src.num("mix_total") != null && src.num("mix_done") != null) {
                todo = String.format(LOC, "%.0f", src.num("mix_total") - src.num("mix_done"));
            }
            field(g, 862, 62, 44, 26, "mix_total", num("mix_total", 0), false, fBig);
            field(g, 914, 62, 44, 26, "mix_done", num("mix_done", 0), false, fBig);
            field(g, 966, 62, 44, 26, "mix_todo", todo, false, fBig);
        }

        /** Ricetta in macchina (numero - nome / modo), riga scheduler e numero fase, in alto a sinistra come in B2. */
        private void drawRecipeBar(Graphics2D g) {
            String rn = src.str("recipe_name");
            Double no = src.connected() ? src.raw("DB201DBW2") : null;
            Boolean auto = src.connected() ? src.bool("mode_auto") : null;
            String name = rn == null || rn.isEmpty() ? "--" : B1RecipeBridge.displayName(rn);
            String txt = (no == null || no.intValue() <= 0 ? "" : no.intValue() + " - ") + name
                    + (Boolean.FALSE.equals(auto) ? " / MAN" : ""); // automatico: solo colore verde; manuale: MAN su rosso
            boolean manual = Boolean.FALSE.equals(auto);
            box(g, 194, 104, 184, 24, manual ? new Color(0xFF0000) : new Color(0xC8FFC8), new Color(0x606060));
            g.setFont(new Font("Arial", Font.BOLD, 12));
            FontMetrics fm = g.getFontMetrics();
            String shown = txt;
            while (shown.length() > 3 && fm.stringWidth(shown) > 176) {
                shown = shown.substring(0, shown.length() - 1);
            }
            if (!shown.equals(txt)) {
                shown = shown.substring(0, shown.length() - 1) + "\u2026";
            }
            text(g, shown, 194, 104, 184, 24, new Font("Arial", Font.BOLD, 12), manual ? Color.WHITE : Color.BLACK, -1);
            keyTag(g, "recipe_name", 194, 104);
            if (bridge != null) {
                final String openName = B1RecipeBridge.archiveName(rn);
                hits.add(new Hit(194, 104, 184, 24, "Recette", () -> bridge.openEditor(openName)));
            }
            // scheduler: B1 non ha uno scheduler di ricette (riga fissa come nel disegno)
            box(g, 194, 138, 184, 22, new Color(0xE8E8E8), new Color(0x606060));
            text(g, "SCHEDULER INACTIF", 194, 138, 184, 22, new Font("Arial", Font.BOLD, 12), new Color(0x404040), 0);
            // fase in corso
            box(g, 194, 166, 92, 34, new Color(0xE8E8E8), new Color(0x303030));
            text(g, "STEP N", 194, 166, 92, 34, new Font("Arial", Font.BOLD, 14), Color.BLACK, 0);
            box(g, 288, 166, 90, 34, Color.WHITE, new Color(0x2050D0));
            Double ph = src.num("phase_no");
            text(g, ph == null ? "--" : String.valueOf(ph.intValue()), 288, 166, 90, 34, new Font("Arial", Font.PLAIN, 16), Color.BLACK, 0);
            keyTag(g, "phase_no", 288, 166);
        }

        private final Font fMed = new Font("Arial", Font.BOLD, 14);

        private void drawPiston(Graphics2D g) {
            // pannello compatto sotto il mixer (come nel disegno B1 trasformato)
            box(g, 214, 513, 143, 162, new Color(0xD0D0D0), new Color(0x909090));
            box(g, 214, 513, 143, 20, Color.WHITE, new Color(0xA0A0A0));
            text(g, "PISTON", 214, 513, 143, 20, new Font("Arial", Font.BOLD, 12), Color.BLACK, 0);
            arrowButton(g, 217, 539, 22, 22, false, "Consigne piston -");
            field(g, 241, 539, 56, 22, "press_set", num("press_set", 1), true, fMed);
            unit(g, "bar", 299, 539, 24, 22);
            arrowButton(g, 325, 539, 22, 22, true, "Consigne piston +");
            field(g, 241, 565, 56, 22, "press_act", num("press_act", 1), false, fMed);
            unit(g, "bar", 299, 565, 24, 22);
            field(g, 241, 594, 56, 22, "piston_pct1", num("piston_pct1", 0), false, fMed);
            unit(g, "%", 299, 594, 24, 22);
            field(g, 241, 619, 56, 22, "piston_pct2", num("piston_pct2", 0), true, fMed);
            unit(g, "%", 299, 619, 24, 22);
            field(g, 241, 644, 56, 22, "piston_pct3", num("piston_pct3", 0), true, fMed);
            unit(g, "%", 299, 644, 24, 22);
        }

        private void drawRotors(Graphics2D g) {
            // pannello compatto centrato sotto il mixer: consegna e giri attuali (la corrente e' sul motore)
            final int x0 = 370, y0 = 595, w = 174;
            box(g, x0, y0, w, 80, new Color(0xD0D0D0), new Color(0x909090));
            box(g, x0, y0, w, 20, Color.WHITE, new Color(0xA0A0A0));
            text(g, "ROTORS", x0, y0, w, 20, new Font("Arial", Font.BOLD, 12), Color.BLACK, 0);
            arrowButton(g, x0 + 4, y0 + 25, 26, 22, false, "Consigne tours -");
            field(g, x0 + 34, y0 + 25, 74, 22, "rpm_set", num("rpm_set", 0), true, fMed);
            unit(g, "rpm", x0 + 110, y0 + 25, 32, 22);
            arrowButton(g, x0 + 145, y0 + 25, 26, 22, true, "Consigne tours +");
            field(g, x0 + 34, y0 + 51, 74, 22, "rpm_act", num("rpm_act", 0), false, fMed);
            unit(g, "rpm", x0 + 110, y0 + 51, 32, 22);
        }

        private void drawLube(Graphics2D g) {
            box(g, 7, 609, 200, 66, new Color(0xD0D0D0), new Color(0x909090));
            title(g, "LUBRICATION", 7, 609, 200, 26);
            lampRow(g, 13, 642, 42, 155, "lube_running", LampKind.GREEN, "En marche");
        }

        // ---------------- macchina ----------------
        private void drawMachine(Graphics2D g) {
            // mixer animato preso da PLC3_Main, scalato nell'area centrale del sinottico
            java.awt.geom.AffineTransform old = g.getTransform();
            g.translate(8.2, 71.9);
            g.scale(0.734, 0.8445);
            art.setCurrent(num("current", 0));
            art.setCurrentValue(src.num("current"));
            art.setTemp(num("temp", 0));
            art.setLiquids(Boolean.TRUE.equals(src.bool("liq_doa")), Boolean.TRUE.equals(src.bool("liq_silano")),
                    Boolean.TRUE.equals(src.bool("liq_huile")), num("liq_consigne", 1));
            art.paint(g);
            g.setTransform(old);
            g.setStroke(new BasicStroke(1));
        }

        // ---------------- colonna destra e centro ----------------
        /**
         * Stato del silos (spie SILOS e polvere nel mixer, sempre la stessa fonte): se Govoni e' attivo vale solo Govoni
         * (Descharge Vidage = aperto, altrimenti chiuso; non ancora letto = sconosciuto); altrimenti i bit del PLC.
         */
        private Boolean silosOpenState() {
            if (B1GovoniSilos.configured()) {
                return B1GovoniSilos.open();
            }
            return src.bool("silos_open");
        }

        private Boolean silosClosedState() {
            if (B1GovoniSilos.configured()) {
                Boolean o = B1GovoniSilos.open();
                return o == null ? null : Boolean.valueOf(!o);
            }
            return src.bool("silos_closed");
        }

        private void drawSilosDoor(Graphics2D g) {
            title(g, "SILOS", 596, 98, 102, 24);
            box(g, 597, 124, 102, 70, new Color(0xD0D0D0), new Color(0x909090));
            // spie dal sistema Govoni (Etat execution BY1): "Descharge Vidage" = aperto, ogni altro stato = chiuso;
            // se Govoni non e' configurato o non ancora letto restano le spie del PLC
            Boolean openOn = silosOpenState();
            Boolean closedOn = silosClosedState();
            lampState(g, 602, 130, "silos_open", LampKind.GREEN, openOn);
            label(g, "Ouvert", 630, 130, 66, 24);
            lampState(g, 602, 162, "silos_closed", LampKind.RED, closedOn);
            label(g, "Ferm\u00e9", 630, 162, 66, 24);
        }

        // caratteri del blocco lotto come in B2: etichette in grassetto piccolo e centrate, valori in grassetto piu' grande e centrati
        private final Font fLotLabel = new Font("Arial", Font.BOLD, 11);
        private final Font fLotValue = new Font("Arial", Font.BOLD, 16);

        private void lotLabel(Graphics2D g, String t, int x, int y, int w, int h) {
            sunken(g, x, y, w, h, LABEL_BG);
            text(g, t, x, y, w, h, fLotLabel, Color.BLACK, 0);
        }

        /** Campo valore del blocco lotto (centrato). white = campo bianco (selezioni), altrimenti verde. */
        private void lotField(Graphics2D g, int x, int y, int w, int h, String key, String v, boolean white) {
            sunken(g, x, y, w, h, white ? Color.WHITE : FIELD_GREEN);
            java.awt.Shape oldClip = g.getClip();
            g.clipRect(x + 1, y + 1, w - 2, h - 2);
            text(g, v == null || v.isEmpty() ? "--" : v, x, y, w, h, fLotValue,
                    v == null || v.isEmpty() ? new Color(0x707070) : Color.BLACK, 0);
            g.setClip(oldClip);
            keyTag(g, key, x, y);
        }

        private void drawLotInfo(Graphics2D g) {
            g.translate(-4, 219); // blocco spostato in basso, a sinistra di CYCLE MALAXEUR
            // stessa sequenza e stesse parole del blocco di B2: LOT, SEQ x SUR y, COUCHE / PASS, COULEUR, SUFFIXE
            lotLabel(g, "LOT :", 594, 212, 72, 22);
            lotField(g, 668, 212, 34, 22, "lot_year", src.str("lot_year"), false);
            text(g, "/", 703, 212, 10, 22, fLotValue, Color.BLACK, 0);
            lotField(g, 715, 212, 70, 22, "lot_num", src.str("lot_num"), false);
            lotLabel(g, "SEQ :", 594, 238, 72, 22);
            lotField(g, 668, 238, 38, 22, "seq_b", num("seq_b", 0), false);
            text(g, "SUR", 708, 238, 30, 22, fLotValue, Color.BLACK, 0);
            lotField(g, 740, 238, 38, 22, "seq_a", num("seq_a", 0), false);
            g.setPaint(new GradientPaint(780, 239, new Color(0x6A7486), 780, 259, new Color(0x3A4252)));
            g.fillRect(780, 239, 42, 20);
            text(g, "RST SEQ", 780, 239, 42, 20, new Font("Arial", Font.BOLD, 9), Color.WHITE, 0);
            hits.add(new Hit(776, 458, 42, 20, "RESET SEQ"));
            lotLabel(g, "COUCHE :", 594, 264, 72, 22);
            lotField(g, 668, 264, 56, 22, "type", src.str("type"), true);
            lotLabel(g, "PASS :", 728, 264, 44, 22);
            lotField(g, 774, 264, 46, 22, "pass", src.str("pass"), true);
            lotLabel(g, "COULEUR :", 594, 290, 72, 22);
            lotField(g, 668, 290, 76, 22, "color", src.str("color"), true);
            lotLabel(g, "SUFFIXE :", 594, 316, 72, 22);
            lotField(g, 668, 316, 38, 22, "suffix", num("suffix", 0), false);
            g.translate(4, -219);
        }

        private void combo(Graphics2D g, int x, int y, int w, int h, String key, String v) {
            sunken(g, x, y, w, h, Color.WHITE);
            text(g, v == null || v.isEmpty() ? "--" : v, x, y, w - 14, h, fNormal, Color.BLACK, -1);
            g.setColor(new Color(0xD0D0D0));
            g.fillRect(x + w - 16, y + 2, 14, h - 4);
            g.setColor(Color.BLACK);
            g.fillPolygon(new int[]{x + w - 13, x + w - 5, x + w - 9}, new int[]{y + h / 2 - 2, y + h / 2 - 2, y + h / 2 + 3}, 3);
            keyTag(g, key, x, y);
        }

        private void drawWeightAndDischarge(Graphics2D g) {
            // in alto a destra, compatto
            box(g, 827, 89, 194, 75, new Color(0xD0D0D0), new Color(0x909090));
            title(g, "POIDS PRESSEUR", 827, 89, 194, 18);
            lampRow(g, 831, 109, 857, 112, "weight_open", LampKind.GREEN, "Ouvert");
            valveIcon(g, 975, 113, Boolean.TRUE.equals(src.bool("weight_open")));
            lampRow(g, 831, 134, 857, 112, "weight_closed", LampKind.RED, "Ferm\u00e9");
            valveIcon(g, 975, 138, Boolean.TRUE.equals(src.bool("weight_closed")));
        }

        private void valveIcon(Graphics2D g, int x, int y, boolean on) {
            g.setColor(on ? new Color(0x20D020) : new Color(0x0C4A0C));
            g.fillRect(x, y, 14, 16);
            g.setColor(Color.BLACK);
            g.drawRect(x, y, 14, 16);
            g.drawLine(x + 18, y + 1, x + 34, y + 15);
            g.drawLine(x + 18, y + 15, x + 34, y + 1);
            g.drawLine(x + 18, y + 1, x + 18, y + 15);
            g.drawLine(x + 34, y + 1, x + 34, y + 15);
        }

        private void drawMalaxeurPret(Graphics2D g) {
            title(g, "MALAXEUR PRET", 838, 173, 185, 26);
            String[][] rows = {
                    {"rdy_temp", "Temp. Min. OK d\u00e9marrage"}, {"rdy_discharge", "D\u00e9chargement OK"},
                    {"rdy_motor", "Moteur principal OK"}, {"rdy_door", "Porte d\u00e9ch. ferm\u00e9e"},
                    {"rdy_mintime", "Temps min. entre cycles"}, {"rdy_cycles", "Nb. cycles pas effectu\u00e9s"}};
            box(g, 822, 200, 201, 190, new Color(0xD0D0D0), new Color(0x909090));
            for (int i = 0; i < rows.length; i++) {
                lampRow(g, 826, 206 + i * 30, 856, 163, rows[i][0], LampKind.GREEN, rows[i][1]);
            }
        }

        private void drawCycleMalaxeur(Graphics2D g) {
            title(g, "CYCLE MALAXEUR", 822, 394, 201, 26);
            box(g, 822, 420, 201, 164, new Color(0xD0D0D0), new Color(0x909090));
            lampRow(g, 826, 426, 856, 163, "cyc_auto", LampKind.GREEN, "Auto");
            lampRow(g, 826, 456, 856, 163, "cyc_cont", LampKind.GREEN, "Continu");
            lampRow(g, 826, 486, 856, 163, "cyc_susp", LampKind.RED, "Suspension");
            lampRow(g, 826, 516, 856, 163, "cyc_cycle", LampKind.GREEN, "Cycle");
            field(g, 828, 548, 98, 28, "steps", num("steps", 0), true, fBig);
            unit(g, "pas", 928, 548, 46, 28);
        }

        private void drawPositions(Graphics2D g) {
            // spostato nella colonna di sinistra (al posto dei rotori), sopra LUBRICATION
            box(g, 7, 488, 202, 118, new Color(0xD0D0D0), new Color(0x909090));
            lampRow(g, 11, 493, 41, 165, "pos_load", LampKind.GREEN, "Position chargement");
            lampRow(g, 11, 522, 41, 165, "decel_load", LampKind.GREEN, "d\u00e9c\u00e9l\u00e9ration charg.");
            lampRow(g, 11, 551, 41, 165, "decel_dis", LampKind.GREEN, "D\u00e9c\u00e9l\u00e9ration d\u00e9ch.");
            lampRow(g, 11, 580, 41, 165, "pos_dis", LampKind.GREEN, "Position d\u00e9ch.");
        }

        private void drawProduction(Graphics2D g) {
            box(g, 617, 583, 407, 92, new Color(0xD0D0D0), new Color(0x909090));
            lampRow(g, 622, 588, 652, 168, "prod_ready", LampKind.GREEN, "Pr\u00eat pour production");
            lampRow(g, 622, 618, 652, 168, "prod_auto_on", LampKind.GREEN, "Cycle auto ON");
            lampRow(g, 622, 646, 652, 168, "new_load", LampKind.ORANGE, "Chargement nouvelle");
            lampRow(g, 828, 588, 858, 160, "prod_auto", LampKind.GREEN, "Fonct. production auto");
            lampRow(g, 828, 618, 858, 160, "cycle_stopped", LampKind.RED, "cycle arr\u00eat\u00e9");
            lampRow(g, 828, 646, 858, 160, "recipe_ok", LampKind.GREEN, "Recette valide");
        }

        // ---------------- barra pulsanti, allarmi, orologio ----------------
        private List<B1BottomBar.Btn> barButtons;

        @Override
        public String getToolTipText(MouseEvent e) {
            if (barButtons == null) {
                return null;
            }
            int px = (int) (e.getX() / scaleX()) + OX;
            int py = (int) (e.getY() / scaleY()) + OY;
            B1BottomBar.Btn b = B1BottomBar.at(barButtons, px, py - 686);
            return b == null ? null : B1BottomBar.tooltip(b.name);
        }

        private final B1BottomBar.Env barEnv = new B1BottomBar.Env() {
            @Override
            public B1Source src() {
                return src;
            }

            @Override
            public B1RecipeBridge bridge() {
                return bridge;
            }

            @Override
            public B1Tags tags() {
                return tags;
            }

            @Override
            public java.awt.Window window() {
                return SwingUtilities.getWindowAncestor(SynPanel.this);
            }

            @Override
            public void exit() {
                // chiude la finestra attuale (il sinottico principale: il programma termina)
                java.awt.Window w = SwingUtilities.getWindowAncestor(SynPanel.this);
                if (w != null) {
                    w.dispatchEvent(new java.awt.event.WindowEvent(w, java.awt.event.WindowEvent.WINDOW_CLOSING));
                }
            }
        };

        // barra pulsanti comune (B1BottomBar), uguale in tutte le schermate
        private void drawBottomBar(Graphics2D g) {
            if (barButtons == null) {
                barButtons = B1BottomBar.buttons(barEnv);
            }
            g.translate(0, 686);
            B1BottomBar.paint(g, barButtons);
            g.translate(0, -686);
            for (B1BottomBar.Btn b : barButtons) {
                hits.add(new Hit(b.x, b.y + 686, b.w, b.h, b.name, b.action));
            }
        }

        private void drawAlarms(Graphics2D g) {
            box(g, 5, 742, 757, 90, new Color(0xE4ECF4), new Color(0x8098B0));
            g.setPaint(new GradientPaint(5, 744, new Color(0xC8D8EC), 757, 744, new Color(0xE4ECF4)));
            g.fillRect(6, 743, 755, 18);
            text(g, "Alarmes", 8, 743, 100, 18, fNormal, new Color(0x20406C), -1);
            String[] cols = {"", "Date", "Time", "Nombre", "Point d'erreur", "Texte du message"};
            int[] cx = {10, 38, 107, 176, 220, 300};
            for (int i = 0; i < cols.length; i++) {
                text(g, cols[i], cx[i], 773, 120, 16, new Font("Arial", Font.PLAIN, 11), Color.BLACK, -1);
                g.setColor(new Color(0xB0B0B0));
                g.drawLine(cx[i], 773, cx[i], 789);
            }
            g.setColor(new Color(0xB0B0B0));
            g.drawLine(8, 789, 755, 789);
            List<B1AlarmEngine.Event> ev = ENGINE.events();
            if (ENGINE.definitions().isEmpty()) {
                text(g, "Aucune alarme d\u00e9finie (b1_alarms.properties) : num\u00e9ro, texte et bit des messages requis", 38, 792, 700, 18,
                        new Font("Arial", Font.ITALIC, 11), new Color(0x606060), -1);
            } else if (ev.isEmpty()) {
                text(g, "Aucune alarme", 38, 792, 300, 18, new Font("Arial", Font.PLAIN, 12), new Color(0x206020), -1);
            }
            Font fa = new Font("Arial", Font.PLAIN, 12);
            for (int i = 0; i < Math.min(2, ev.size()); i++) {
                B1AlarmEngine.Event e = ev.get(i);
                int y = 791 + i * 18;
                g.setColor(e.active() ? B1AlarmEngine.COLOR_CAME : B1AlarmEngine.COLOR_WENT); // come WinCC: rosso attivo, blu rientrato
                g.fillRect(8, y, 747, 18);
                Color c = Color.WHITE;
                text(g, e.came.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), 38, y, 70, 18, fa, c, -1);
                text(g, e.came.format(DateTimeFormatter.ofPattern("HH:mm:ss")), 107, y, 70, 18, fa, c, -1);
                text(g, String.valueOf(e.def.number), 176, y, 44, 18, fa, c, -1);
                text(g, e.def.point, 220, y, 80, 18, fa, c, -1);
                text(g, e.def.text, 300, y, 450, 18, fa, c, -1);
            }
        }

        private void drawClock(Graphics2D g) {
            box(g, 765, 742, 260, 90, new Color(0xD8D8D8), new Color(0x909090));
            g.setColor(new Color(0x707070));
            g.fillRect(776, 752, 18, 32);
            g.setColor(new Color(0xC02020));
            g.fillRect(778, 768, 14, 14);
            // stato PLC e confronto con l'archivio (spostati qui dalla barra in alto)
            Boolean conn = src.connected();
            sunken(g, 820, 746, 196, 26, new Color(0xD8D4CC));
            g.setColor(conn ? new Color(0x20B020) : new Color(0xD02020));
            g.fillOval(830, 754, 12, 12);
            text(g, conn ? "PLC connect\u00e9" : "PLC non connect\u00e9", 846, 746, 168, 26, new Font("Arial", Font.BOLD, 14), DARK, -1);
            sunken(g, 826, 775, 184, 22, new Color(0xD8D4CC));
            if (bridge != null) {
                B1RecipeBridge.Status st = bridge.status();
                Color dot;
                switch (st.state) {
                    case SAME: dot = new Color(0x20B020); break;
                    case DIFFERENT: dot = new Color(0xE08000); break;
                    case NOT_ARCHIVED: dot = new Color(0x2060C0); break;
                    default: dot = new Color(0x909090);
                }
                String line = conn ? st.text : src.status();
                g.setColor(dot);
                g.fillOval(832, 780, 10, 10);
                text(g, line.length() > 24 ? line.substring(0, 24) + "\u2026" : line, 846, 775, 162, 22,
                        new Font("Arial", Font.PLAIN, 12), DARK, -1);
            }
            long now = System.currentTimeMillis();
            if (!message.isEmpty() && now < messageUntil) {
                g.setColor(new Color(0xFFF2B0));
                g.fillRect(768, 806, 255, 24);
                text(g, message, 768, 806, 255, 24, new Font("Arial", Font.PLAIN, 11), Color.BLACK, -1);
            } else {
                text(g, B1RecipeTransfer.writeEnabled() ? "\u00c9CRITURE : recettes  -  F2 : adresses" : "LECTURE SEULE  -  F2 : adresses", 768, 808, 255, 20, new Font("Arial", Font.PLAIN, 11), new Color(0x505050), -1);
            }
        }
    }
}
