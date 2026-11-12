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

    static final String VERSION = "B1 V0.1 (sola lettura)";
    static final String DEFAULT_IP = "192.168.10.100";
    static final Path DEFAULT_TAGS = Paths.get("C:\\_tools\\Banbury1\\b1_tags.properties");

    private final SynPanel panel;

    public B1_Main(B1Source src, B1RecipeBridge bridge, B1Tags tags) {
        super("Banbury 1 - MCC-240 T - " + VERSION);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        panel = new SynPanel(src, bridge, tags);
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
        if (!new File(tagFile.toString()).exists() && new File("b1_tags.properties").exists()) {
            tagFile = Paths.get("b1_tags.properties");
        }
        if (!new File(layoutFile.toString()).exists() && new File("b1_recipe_layout.properties").exists()) {
            layoutFile = Paths.get("b1_recipe_layout.properties");
        }
        if (!Files.isDirectory(recipeDir) && !System.getProperty("os.name", "").toLowerCase().contains("win")) {
            recipeDir = Paths.get("b1_recipes");
        }
        final B1RecipeLayout layout = B1RecipeLayout.load(layoutFile);
        final B1RecipeRepository repo = new B1FileRecipeRepository(recipeDir, layout);
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
            B1TrendRecorder.start(src, B1TrendStore.shared(B1TrendRecorder.seriesIds()), demo ? null : hist);
        }
        if (snap != null && screen != null) {
            System.setProperty("java.awt.headless", "true");
            for (int i = 0; i < 40 && !src.connected(); i++) {
                Thread.sleep(100);
            }
            Thread.sleep(700);
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
        private double rotorAngle;
        private long lastAlarmPoll;
        private double pistonDy;
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
            new Timer(60, e -> {
                long nowMs = System.currentTimeMillis();
                if (nowMs - lastAlarmPoll >= 1000) {
                    lastAlarmPoll = nowMs;
                    ENGINE.poll(src, java.time.LocalDateTime.now());
                }
                if (bridge != null) {
                    bridge.offer(src.dbImage(bridge.layout().db));
                }
                Double rpm = src.num("rpm_act");
                if (rpm != null && rpm > 0) {
                    rotorAngle += Math.min(rpm, 60) * 0.0105;
                }
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
                    double s = scale();
                    int px = (int) ((e.getX() - offX()) / s) + OX;
                    int py = (int) ((e.getY() - offY()) / s) + OY;
                    for (Hit h : hits) {
                        if (px >= h.x && px < h.x + h.w && py >= h.y && py < h.y + h.h) {
                            if (h.action != null) {
                                h.action.run();
                                return;
                            }
                            message = "'" + h.name + "': non disponibile (sinottico in SOLA LETTURA)";
                            messageUntil = System.currentTimeMillis() + 4000;
                            return;
                        }
                    }
                }
            });
            requestFocusInWindow();
        }

        private double scale() {
            return Math.min(getWidth() / (double) BASE_W, getHeight() / (double) BASE_H);
        }

        private double offX() {
            return (getWidth() - BASE_W * scale()) / 2;
        }

        private double offY() {
            return (getHeight() - BASE_H * scale()) / 2;
        }

        // ---------------------------------------------------------------
        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0xAAAAAA));
            g.fillRect(0, 0, getWidth(), getHeight());
            double s = getWidth() > 0 ? scale() : 1.0;
            g.translate(offX(), offY());
            g.scale(s, s);
            g.translate(-OX, -OY);
            hits.clear();
            drawAll(g);
            g.dispose();
        }

        private void drawAll(Graphics2D g) {
            g.setPaint(new GradientPaint(0, OY, BG1, 0, OY + BASE_H, BG2));
            g.fillRect(OX, OY, BASE_W, BASE_H);
            drawTopBar(g);
            drawPiston(g);
            drawRotors(g);
            drawLube(g);
            drawTemperature(g);
            drawLotBar(g);
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
            Boolean on = key == null ? null : src.bool(key);
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

        private void bigArrow(Graphics2D g, int x, int y, boolean up) {
            Polygon p = new Polygon();
            if (up) {
                p.addPoint(x, y + 30); p.addPoint(x + 28, y + 30); p.addPoint(x + 14, y);
            } else {
                p.addPoint(x, y); p.addPoint(x + 28, y); p.addPoint(x + 14, y + 30);
            }
            g.setPaint(new GradientPaint(x, y, new Color(0xA0A0A0), x + 28, y, new Color(0x606060)));
            g.fillPolygon(p);
            g.setColor(new Color(0x404040));
            g.drawPolygon(p);
        }

        // ---------------- sezioni ----------------
        private void drawTopBar(Graphics2D g) {
            // logo
            box(g, 12, 37, 50, 50, new Color(0xF2F2F2), new Color(0x909090));
            text(g, "ELI", 12, 40, 50, 24, new Font("Arial", Font.BOLD, 17), new Color(0x1B4F8A), 0);
            text(g, "AUTOMATION", 12, 62, 50, 14, new Font("Arial", Font.PLAIN, 7), new Color(0x1B4F8A), 0);
            label(g, "MCC-240 T", 80, 38, 102, 24);
            label(g, "Synoptique", 80, 63, 102, 24);
            // Imprimer Page
            g.setPaint(new GradientPaint(197, 38, new Color(0x5A6678), 197, 88, new Color(0x2B3340)));
            g.fillRoundRect(197, 38, 66, 50, 6, 6);
            text(g, "Imprimer", 197, 44, 66, 18, fNormal, Color.WHITE, 0);
            text(g, "Page", 197, 62, 66, 18, fNormal, Color.WHITE, 0);
            hits.add(new Hit(197, 38, 66, 50, "Imprimer Page"));

            // stato PLC + ricetta in macchina + confronto con archivio
            Boolean conn = src.connected();
            g.setColor(conn ? new Color(0x20B020) : new Color(0xD02020));
            g.fillOval(276, 42, 12, 12);
            text(g, conn ? "PLC connesso" : "PLC non connesso", 292, 38, 190, 20, fNormal, DARK, -1);
            String rn = src.str("recipe_name");
            text(g, "Ricetta: " + (rn == null || rn.isEmpty() ? "--" : rn), 276, 55, 205, 18, fBold, DARK, -1);
            keyTag(g, "recipe_name", 276, 55);
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
                g.fillOval(276, 74, 10, 10);
                text(g, line.length() > 34 ? line.substring(0, 34) + "\u2026" : line, 290, 70, 190, 18,
                        new Font("Arial", Font.PLAIN, 12), DARK, -1);
                final String openName = rn;
                hits.add(new Hit(276, 55, 205, 33, "Ricetta", () -> bridge.openEditor(openName)));
            } else {
                String st = src.status();
                text(g, st.length() > 38 ? st.substring(0, 38) + "\u2026" : st, 276, 72, 205, 14, fSmall, DARK, -1);
            }

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

        private void drawPiston(Graphics2D g) {
            box(g, 7, 108, 250, 305, new Color(0xD0D0D0), new Color(0x909090));
            title(g, "PISTON", 7, 108, 250, 26);
            lampRow(g, 13, 143, 42, 160, "piston_high", LampKind.GREEN, "Position haute");
            lampRow(g, 13, 173, 42, 160, "piston_float", LampKind.GREEN, "Flottant");
            lampRow(g, 13, 203, 42, 160, "piston_low", LampKind.GREEN, "Position basse");
            lampRow(g, 13, 233, 42, 160, "piston_blocked", LampKind.GREEN, "Bloqu\u00e9");
            bigArrow(g, 218, 143, true);
            bigArrow(g, 218, 196, false);
            arrowButton(g, 12, 263, 38, 26, false, "Pistone consegna -");
            field(g, 57, 262, 98, 26, "press_set", num("press_set", 1), true, fBig);
            unit(g, "bar", 157, 262, 38, 26);
            arrowButton(g, 196, 263, 38, 26, true, "Pistone consegna +");
            field(g, 57, 292, 98, 26, "press_act", num("press_act", 1), false, fBig);
            unit(g, "bar", 157, 292, 38, 26);
            field(g, 57, 327, 98, 26, "piston_pct1", num("piston_pct1", 0), false, fBig);
            unit(g, "%", 157, 327, 38, 26);
            field(g, 57, 357, 98, 26, "piston_pct2", num("piston_pct2", 0), true, fBig);
            unit(g, "%", 157, 357, 38, 26);
            field(g, 57, 387, 98, 26, "piston_pct3", num("piston_pct3", 0), true, fBig);
            unit(g, "%", 157, 387, 38, 26);
        }

        private void drawRotors(Graphics2D g) {
            box(g, 7, 424, 250, 150, new Color(0xD0D0D0), new Color(0x909090));
            title(g, "ROTORS", 7, 424, 250, 26);
            arrowButton(g, 12, 457, 38, 26, false, "Giri consegna -");
            field(g, 57, 457, 98, 26, "rpm_set", num("rpm_set", 0), true, fBig);
            unit(g, "rpm", 157, 457, 38, 26);
            arrowButton(g, 196, 457, 38, 26, true, "Giri consegna +");
            field(g, 57, 487, 98, 26, "rpm_act", num("rpm_act", 0), false, fBig);
            unit(g, "rpm", 157, 487, 38, 26);
            field(g, 57, 517, 98, 26, "current", num("current", 0), false, fBig);
            unit(g, "A", 157, 517, 38, 26);
            lampRow(g, 13, 548, 42, 160, "rotors_running", LampKind.GREEN, "Running");
        }

        private void drawLube(Graphics2D g) {
            box(g, 7, 583, 200, 66, new Color(0xD0D0D0), new Color(0x909090));
            title(g, "LUBRICATION", 7, 583, 200, 26);
            lampRow(g, 13, 616, 42, 155, "lube_running", LampKind.GREEN, "Running");
        }

        private void drawTemperature(Graphics2D g) {
            title(g, "TEMPERATURE", 262, 398, 140, 26);
            field(g, 265, 430, 98, 28, "temp", num("temp", 0), false, fBig);
            unit(g, "\u00b0C", 365, 430, 36, 28);
        }

        private void drawLotBar(Graphics2D g) {
            g.setPaint(new GradientPaint(262, 108, new Color(0x909090), 322, 108, new Color(0x707070)));
            g.fillRect(262, 108, 60, 282);
            g.setColor(new Color(0x404040));
            g.drawRect(262, 108, 60, 282);
            g.setFont(fSmall);
            for (int i = 0; i <= 10; i++) {
                int y = 118 + (int) Math.round(i * 26.3);
                g.setColor(Color.BLACK);
                g.drawLine(322, y, 328, y);
                String t = (i == 10 ? "+" : "+") + (100 - i * 10);
                g.drawString(t, 331, y + 4);
            }
            Double tot = src.num("mix_total"), done = src.num("mix_done");
            int y = 196;
            if (tot != null && done != null && tot > 0) {
                y = 381 - (int) Math.round(263 * Math.min(1.0, done / tot));
            }
            box(g, 270, y - 9, 44, 18, Color.WHITE, new Color(0x808080));
            text(g, "LOT:", 270, y - 9, 44, 18, fBold, Color.BLACK, 0);
            text(g, "LOT:", 270, 92, 44, 14, fSmall, Color.BLACK, 0);
        }

        // ---------------- macchina ----------------
        private void drawMachine(Graphics2D g) {
            // tramoggia
            Path2D hopper = new Path2D.Double();
            hopper.moveTo(365, 126);
            hopper.lineTo(447, 126);
            hopper.lineTo(421, 210);
            hopper.lineTo(410, 216);
            hopper.lineTo(410, 236);
            hopper.lineTo(397, 236);
            hopper.lineTo(397, 216);
            hopper.lineTo(386, 210);
            hopper.closePath();
            g.setPaint(new GradientPaint(365, 0, new Color(0x808080), 447, 0, new Color(0xE0E0E0)));
            g.fill(hopper);
            g.setColor(new Color(0x505050));
            g.draw(hopper);
            g.setPaint(new GradientPaint(365, 0, new Color(0xA0A0A0), 447, 0, new Color(0xF0F0F0)));
            g.fill(new Ellipse2D.Double(365, 118, 82, 16));
            g.setColor(new Color(0x505050));
            g.draw(new Ellipse2D.Double(365, 118, 82, 16));
            g.setColor(new Color(0x909090));
            g.setStroke(new BasicStroke(10, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            Path2D pipe = new Path2D.Double();
            pipe.moveTo(403, 236);
            pipe.lineTo(403, 256);
            pipe.quadTo(403, 268, 440, 270);
            g.draw(pipe);
            g.setStroke(new BasicStroke(1));

            // armadio comandi a destra
            box(g, 498, 240, 62, 130, new Color(0xD6D6D6), new Color(0x707070));
            box(g, 512, 262, 40, 72, new Color(0xB8B8B8), new Color(0x707070));

            // colonna
            g.setPaint(new GradientPaint(450, 0, new Color(0xB0B0B0), 460, 0, new Color(0xF0F0F0)));
            g.fillRect(450, 112, 10, 230);
            g.fillRect(508, 112, 10, 230);
            g.setColor(new Color(0x505050));
            g.drawRect(450, 112, 10, 230);
            g.drawRect(508, 112, 10, 230);
            box(g, 440, 104, 88, 12, new Color(0xA8A8A8), new Color(0x505050));
            box(g, 462, 236, 44, 32, new Color(0x505860), new Color(0x303030));

            // pistone (posizione in base ai fine corsa)
            double target;
            if (Boolean.TRUE.equals(src.bool("piston_high"))) {
                target = 0;
            } else if (Boolean.TRUE.equals(src.bool("piston_low"))) {
                target = 46;
            } else if (Boolean.TRUE.equals(src.bool("piston_float"))) {
                target = 24;
            } else {
                target = 24;
            }
            pistonDy += (target - pistonDy) * 0.12;
            int dy = (int) Math.round(pistonDy);
            g.setPaint(new GradientPaint(478, 0, new Color(0x909090), 490, 0, new Color(0xE8E8E8)));
            g.fillRect(478, 268, 12, 56 + dy);
            box(g, 456, 324 + dy, 56, 16, new Color(0x606870), new Color(0x303030));

            // camera di miscelazione
            g.setPaint(new GradientPaint(415, 338, new Color(0xF0F0F0), 565, 450, new Color(0xB0B0B0)));
            g.fill(new RoundRectangle2D.Double(415, 338, 150, 112, 40, 40));
            g.setColor(new Color(0x606060));
            g.draw(new RoundRectangle2D.Double(415, 338, 150, 112, 40, 40));
            g.setColor(new Color(0xE8E8E8));
            g.fill(new Ellipse2D.Double(423, 346, 134, 96));
            g.setColor(new Color(0x808080));
            g.draw(new Ellipse2D.Double(423, 346, 134, 96));
            drawRotor(g, 453, 396, rotorAngle);
            drawRotor(g, 507, 396, -rotorAngle + Math.PI / 2);

            // frecce verdi di rotazione
            g.setColor(new Color(0x30C030));
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Arc2D.Double(430, 363, 48, 48, 210, 120, Arc2D.OPEN));
            g.draw(new Arc2D.Double(482, 363, 48, 48, 210, 120, Arc2D.OPEN));
            g.setStroke(new BasicStroke(1));
            Polygon a1 = new Polygon(new int[]{468, 480, 474}, new int[]{372, 380, 388}, 3);
            Polygon a2 = new Polygon(new int[]{492, 480, 486}, new int[]{372, 380, 388}, 3);
            g.fillPolygon(a1);
            g.fillPolygon(a2);

            // base
            g.setPaint(new GradientPaint(392, 497, new Color(0x8A8A8A), 392, 533, new Color(0x505050)));
            g.fillRect(392, 497, 170, 36);
            g.setColor(new Color(0x303030));
            g.drawRect(392, 497, 170, 36);
            box(g, 410, 488, 134, 10, new Color(0xD0D0D0), new Color(0x606060));

            // leva rossa e porta scarico
            boolean disOpen = Boolean.TRUE.equals(src.bool("door_dis_open"));
            g.setColor(new Color(0xE00000));
            g.fillRect(428, 440, 14, 45);
            g.fillOval(430, 432, 14, 14);
            Polygon tri = disOpen
                    ? new Polygon(new int[]{440, 470, 440}, new int[]{478, 500, 500}, 3)
                    : new Polygon(new int[]{440, 462, 440}, new int[]{470, 486, 486}, 3);
            g.fillPolygon(tri);
            box(g, 512, 442, 36, 14, new Color(0x9A9A20), new Color(0x505010));
            box(g, 488, 454, 12, 12, new Color(0x909090), new Color(0x404040));
        }

        private void drawRotor(Graphics2D g, int cx, int cy, double angle) {
            for (int k = 0; k < 2; k++) {
                java.awt.geom.AffineTransform old = g.getTransform();
                g.rotate(angle + k * Math.PI / 2, cx, cy);
                g.setPaint(new GradientPaint(cx - 24, cy, new Color(0xA8A8A8), cx + 24, cy, new Color(0x606060)));
                g.fill(new Ellipse2D.Double(cx - 24, cy - 11, 48, 22));
                g.setColor(new Color(0x303030));
                g.draw(new Ellipse2D.Double(cx - 24, cy - 11, 48, 22));
                g.setTransform(old);
            }
            g.setColor(new Color(0x404040));
            g.fillOval(cx - 4, cy - 4, 8, 8);
        }

        // ---------------- colonna destra e centro ----------------
        private void drawSilosDoor(Graphics2D g) {
            title(g, "SILOS", 572, 98, 102, 24);
            box(g, 573, 124, 102, 70, new Color(0xD0D0D0), new Color(0x909090));
            lampRow(g, 578, 130, 606, 66, "silos_open", LampKind.GREEN, "Ouvert");
            lampRow(g, 578, 162, 606, 66, "silos_closed", LampKind.RED, "Ferm\u00e9");
            title(g, "PORTE DE CHARGEMENT", 678, 98, 250, 24);
            box(g, 678, 124, 152, 70, new Color(0xD0D0D0), new Color(0x909090));
            lampRow(g, 683, 130, 711, 116, "door_load_open", LampKind.GREEN, "Ouverte");
            lampRow(g, 683, 162, 711, 116, "door_load_closed", LampKind.RED, "Ferm\u00e9e");
        }

        private void drawLotInfo(Graphics2D g) {
            label(g, "SUFFIXE :", 594, 212, 76, 22);
            field(g, 672, 212, 26, 22, "suffix", num("suffix", 0), true, fBold);
            label(g, "LOT:", 594, 238, 50, 22);
            field(g, 648, 238, 34, 22, "lot_year", src.str("lot_year"), true, fBold);
            text(g, "/", 683, 238, 10, 22, fBold, Color.BLACK, 0);
            field(g, 695, 238, 70, 22, "lot_num", src.str("lot_num"), true, fBig);
            label(g, "SEQ:", 594, 264, 50, 22);
            field(g, 646, 262, 38, 22, "seq_a", num("seq_a", 0), true, fBig);
            field(g, 690, 262, 38, 22, "seq_b", num("seq_b", 0), true, fBig);
            g.setPaint(new GradientPaint(732, 263, new Color(0x6A7486), 732, 283, new Color(0x3A4252)));
            g.fillRect(732, 263, 82, 20);
            text(g, "RESET SEQ", 732, 263, 82, 20, new Font("Arial", Font.PLAIN, 11), Color.WHITE, 0);
            hits.add(new Hit(732, 263, 82, 20, "RESET SEQ"));
            label(g, "TYPE:", 594, 290, 52, 20);
            combo(g, 648, 290, 50, 20, "type", src.str("type"));
            label(g, "PASS:", 704, 290, 44, 20);
            combo(g, 750, 290, 46, 20, "pass", src.str("pass"));
            label(g, "COULEUR:", 594, 316, 74, 20);
            combo(g, 670, 316, 56, 20, "color", src.str("color"));
            label(g, "NUM MELANGE", 594, 342, 112, 24);
            combo(g, 712, 342, 48, 22, "num_melange", src.str("num_melange"));
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
            title(g, "POIDS PRESSEUR", 567, 384, 250, 26);
            lampRow(g, 572, 418, 600, 162, "weight_open", LampKind.GREEN, "Ouvert");
            valveIcon(g, 768, 424, Boolean.TRUE.equals(src.bool("weight_open")));
            lampRow(g, 572, 448, 600, 162, "weight_closed", LampKind.RED, "Ferm\u00e9");
            valveIcon(g, 768, 454, Boolean.TRUE.equals(src.bool("weight_closed")));
            title(g, "PORTE DE DECHARGEMENT", 567, 486, 250, 26);
            lampRow(g, 572, 520, 600, 162, "door_dis_open", LampKind.GREEN, "Ouverte");
            valveIcon(g, 768, 526, Boolean.TRUE.equals(src.bool("door_dis_open")));
            lampRow(g, 572, 550, 600, 162, "door_dis_closed", LampKind.RED, "Ferm\u00e9e");
            valveIcon(g, 768, 556, Boolean.TRUE.equals(src.bool("door_dis_closed")));
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
                    {"rdy_mintime", "Temps min. entre cycles"}, {"rdy_cycles", "Nr. Cycles pas effectu\u00e9s"}};
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
            box(g, 362, 553, 202, 118, new Color(0xD0D0D0), new Color(0x909090));
            lampRow(g, 366, 558, 396, 165, "pos_load", LampKind.GREEN, "Position chargement");
            lampRow(g, 366, 587, 396, 165, "decel_load", LampKind.GREEN, "d\u00e9c\u00e9l\u00e9ration charg.");
            lampRow(g, 366, 616, 396, 165, "decel_dis", LampKind.GREEN, "D\u00e9c\u00e9l\u00e9ration d\u00e9ch.");
            lampRow(g, 366, 645, 396, 165, "pos_dis", LampKind.GREEN, "Postion d\u00e9ch.");
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
        private void drawBottomBar(Graphics2D g) {
            // ordine delle schermate WinCC: 001 Synoptic, 002 Process, 003 Trends, 004 Alarms, 005 Display,
            // 006 Recipes, 007 Production, 008 Settings, 009 Temperatures, 010 Components
            String[] names = {"Synoptic", "Process", "Trends", "Alarms", "Display", "Recettes", "Production",
                    "Settings", "Temperatures", "Components"};
            int[] xs = {7, 62, 137, 192, 247, 302, 357, 412, 467, 522};
            for (int i = 0; i < names.length; i++) {
                int x = xs[i], y = 686, w = 52, h = 52;
                g.setPaint(new GradientPaint(x, y, Color.WHITE, x, y + h, new Color(0xCFCFCF)));
                g.fillRect(x, y, w, h);
                g.setColor(new Color(0x808080));
                g.drawRect(x, y, w - 1, h - 1);
                icon(g, i, x, y);
                final String[] target = {null, "002_Process", "003_Trends", "004_Alarms", "005_Display", null,
                        "007_Production", "008_Settings", "009_Temperatures", "010_Components"};
                if (i == 5 && bridge != null) {
                    hits.add(new Hit(x, y, w, h, names[i], () -> bridge.openEditor(null)));
                } else if (i == 2) {
                    hits.add(new Hit(x, y, w, h, names[i], () -> B1TrendWindow.open(src)));
                } else if (i == 3) {
                    hits.add(new Hit(x, y, w, h, names[i], () -> B1AlarmWindow.open(src, ENGINE)));
                } else if (target[i] != null) {
                    final String scr = target[i];
                    hits.add(new Hit(x, y, w, h, names[i], () -> B1ScreensWindow.open(src, scr)));
                } else {
                    hits.add(new Hit(x, y, w, h, names[i]));
                }
            }
            String[] t = {"Govoni", "B2", "B2 main", "Valori PLC", "Etichetta"};
            int[] tx = {578, 632, 688, 752, 826};
            int[] tw = {48, 48, 58, 70, 66};
            for (int i = 0; i < t.length; i++) {
                g.setPaint(new GradientPaint(tx[i], 686, Color.WHITE, tx[i], 736, new Color(0xCFCFCF)));
                g.fillRect(tx[i], 686, tw[i], 50);
                g.setColor(new Color(0x808080));
                g.drawRect(tx[i], 686, tw[i] - 1, 49);
                text(g, t[i], tx[i], 686, tw[i], 50, new Font("Arial", Font.PLAIN, 11), Color.BLACK, 0);
                if (i == 0) {
                    hits.add(new Hit(tx[i], 686, tw[i], 50, t[i], () -> B1ScreensWindow.open(src, "301_GovoniSynoptique2Bambury1")));
                } else if (i == 3) {
                    hits.add(new Hit(tx[i], 686, tw[i], 50, t[i], () -> B1RawViewer.open(src, tags)));
                } else if (i == 4 && bridge != null) {
                    hits.add(new Hit(tx[i], 686, tw[i], 50, t[i], () -> B1LabelDialog.open(
                            (JFrame) SwingUtilities.getWindowAncestor(this), bridge)));
                } else {
                    hits.add(new Hit(tx[i], 686, tw[i], 50, t[i]));
                }
            }
            g.setPaint(new GradientPaint(932, 685, Color.WHITE, 932, 737, new Color(0xCFCFCF)));
            g.fillRect(932, 685, 88, 52);
            g.setColor(new Color(0x808080));
            g.drawRect(932, 685, 87, 51);
            g.setColor(new Color(0xD01010));
            g.fillRect(956, 700, 60, 22);
            text(g, "EXIT", 956, 700, 60, 22, fBold, Color.WHITE, 0);
            hits.add(new Hit(932, 685, 88, 52, "EXIT"));
        }

        private void icon(Graphics2D g, int i, int x, int y) {
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

        private void drawAlarms(Graphics2D g) {
            box(g, 5, 742, 757, 90, new Color(0xE4ECF4), new Color(0x8098B0));
            g.setPaint(new GradientPaint(5, 744, new Color(0xC8D8EC), 757, 744, new Color(0xE4ECF4)));
            g.fillRect(6, 743, 755, 18);
            text(g, "Alarm", 8, 743, 100, 18, fNormal, new Color(0x20406C), -1);
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
                text(g, "Nessun allarme definito (b1_alarms.properties): servono numero, testo e bit dei messaggi", 38, 792, 700, 18,
                        new Font("Arial", Font.ITALIC, 11), new Color(0x606060), -1);
            } else if (ev.isEmpty()) {
                text(g, "Nessun allarme", 38, 792, 300, 18, new Font("Arial", Font.PLAIN, 12), new Color(0x206020), -1);
            }
            Font fa = new Font("Arial", Font.PLAIN, 12);
            for (int i = 0; i < Math.min(2, ev.size()); i++) {
                B1AlarmEngine.Event e = ev.get(i);
                int y = 791 + i * 18;
                g.setColor(i % 2 == 0 ? Color.WHITE : new Color(0xF0F0F0));
                g.fillRect(8, y, 747, 18);
                Color c = e.active() ? new Color(0xC00000) : Color.BLACK;
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
            sunken(g, 820, 745, 200, 28, new Color(0xC8C8C8));
            text(g, LocalDateTime.now().format(DT), 820, 745, 200, 28, fBold, new Color(0xF8F8F8), 0);
            sunken(g, 820, 776, 200, 28, new Color(0xC8C8C8));
            long now = System.currentTimeMillis();
            if (!message.isEmpty() && now < messageUntil) {
                g.setColor(new Color(0xFFF2B0));
                g.fillRect(768, 806, 255, 24);
                text(g, message, 768, 806, 255, 24, new Font("Arial", Font.PLAIN, 11), Color.BLACK, -1);
            } else {
                text(g, "SOLA LETTURA  -  F2: indirizzi", 768, 808, 255, 20, new Font("Arial", Font.PLAIN, 11), new Color(0x505050), -1);
            }
        }
    }
}
