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
        {"002_Process", "Process"}, {"003_Trends", "Trends"}, {"004_Alarms", "Alarms"},
        {"005_Display", "Display"}, {"007_Production", "Production"}, {"008_Settings", "Settings 1"},
        {"008_Settings_1", "Settings 2"}, {"008_Settings_2", "Settings 3"}, {"008_Settings_3", "Settings 4"},
        {"009_Temperatures", "Temperatures"}, {"010_Components", "Components"},
        {"010_PhaseInfo", "Phase info"}, {"203_RunningPhaseActual", "Running phase"},
        {"300_GovoniSynoptique1ChargeSilo", "Govoni silos"}, {"301_GovoniSynoptique2Bambury1", "Govoni B1"},
        {"304_GovoniEtatBalance", "Govoni balance"}, {"305_GovoniEtatProductionB1", "Govoni prod. B1"},
        {"309_GovoniParametreGeneral1", "Govoni param. 1"}, {"311_GovoniParametreBalance", "Govoni param. bal."},
        {"312_GovoniParametreSilo", "Govoni param. silos"}, {"316_GovoniSemiauto", "Govoni semiauto"},
    };

    private static B1ScreensWindow shared;

    private final ScreenPanel panel;

    private B1ScreensWindow(B1Source src, String first) {
        super("Banbury 1 - schermate (sola lettura)");
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

        ScreenPanel(B1Source src, String first) {
            this.src = src;
            this.top = B1Screen.load("100_TopBar");
            setPreferredSize(new Dimension(W, H));
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
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    double s = scale();
                    int x = (int) ((e.getX() - offX()) / s);
                    int y = (int) ((e.getY() - offY()) / s);
                    if (y >= 650) {
                        int idx = navIndexAt(x, y);
                        if (idx >= 0) {
                            show(SCREENS[idx][0]);
                        }
                    }
                }
            });
        }

        void show(String screen) {
            contentName = screen;
            content = B1Screen.load(screen);
            repaint();
        }

        private double scale() {
            return Math.max(0.1, Math.min(getWidth() / (double) W, getHeight() / (double) H));
        }

        private double offX() {
            return (getWidth() - W * scale()) / 2;
        }

        private double offY() {
            return (getHeight() - H * scale()) / 2;
        }

        // barra di navigazione: bottoni a due righe
        private Rectangle2D navRect(int i) {
            int perRow = 7;
            int bw = 140, bh = 32;
            int row = i / perRow, col = i % perRow;
            return new Rectangle2D.Double(10 + col * (bw + 4), 652 + row * (bh + 4), bw, bh);
        }

        private int navIndexAt(int x, int y) {
            for (int i = 0; i < SCREENS.length; i++) {
                if (navRect(i).contains(x, y)) {
                    return i;
                }
            }
            return -1;
        }

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
            g.setPaint(new GradientPaint(0, 0, new Color(0xC6C6C6), 0, H, new Color(0xE2E2E2)));
            g.fillRect(0, 0, W, H);
            if (content != null) {
                drawScreen(g, content, 0);
            } else {
                g.setColor(Color.DARK_GRAY);
                g.setFont(new Font("Arial", Font.BOLD, 18));
                g.drawString("Layout '" + contentName + "' non trovato (cartella screens/)", 40, 200);
            }
            if (top != null) {
                g.setColor(new Color(0xBEBEBE));
                g.fillRect(0, 0, W, 60);
                drawScreen(g, top, 0);
            }
            drawNav(g);
            g.dispose();
        }

        private void drawNav(Graphics2D g) {
            g.setColor(new Color(0xB4B4B4));
            g.fillRect(0, 648, W, 120);
            for (int i = 0; i < SCREENS.length; i++) {
                Rectangle2D r = navRect(i);
                boolean cur = SCREENS[i][0].equals(contentName);
                g.setPaint(new GradientPaint(0, (float) r.getY(), cur ? new Color(0xBFD7FF) : Color.WHITE,
                        0, (float) (r.getY() + r.getHeight()), cur ? new Color(0x86AEEA) : new Color(0xCFCFCF)));
                g.fill(r);
                g.setColor(new Color(0x808080));
                g.draw(r);
                g.setColor(Color.BLACK);
                g.setFont(new Font("Arial", Font.PLAIN, 12));
                FontMetrics fm = g.getFontMetrics();
                String t = SCREENS[i][1];
                g.drawString(t, (int) (r.getX() + (r.getWidth() - fm.stringWidth(t)) / 2),
                        (int) (r.getY() + (r.getHeight() + fm.getAscent()) / 2 - 3));
            }
            g.setColor(new Color(0x404040));
            g.setFont(new Font("Arial", Font.PLAIN, 10));
            String st = src.connected() ? "PLC: " + src.status() : "PLC non connesso - " + src.status();
            g.drawString(st + "   |   SOLA LETTURA   |   F2: indirizzi", 10, 765);
        }

        // ---------------- disegno oggetti ----------------
        private void drawScreen(Graphics2D g, B1Screen sc, int dy) {
            for (B1Screen.Obj o : sc.objects) {
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

        private void drawIo(Graphics2D g, B1Screen.Obj o, int dy) {
            g.setColor(GRAY);
            g.fillRect(o.l, o.t + dy, o.w(), o.h());
            bevel(g, o.l, o.t + dy, o.w(), o.h());
            String v = null;
            if (!o.tag.isEmpty() && o.tag.startsWith("DB")) {
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
            g.setColor(new Color(0xF4F4F4));
            g.fillRect(o.l, o.t + dy, o.w(), o.h());
            g.setColor(new Color(0x909090));
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[]{4f, 3f}, 0f));
            g.drawRect(o.l, o.t + dy, o.w() - 1, o.h() - 1);
            g.setStroke(new BasicStroke(1f));
            if (o.w() > 60 && o.h() > 16) {
                String label = o.name.contains("Control") || o.name.contains("Contr")
                        ? "[controllo WinCC]" : o.name;
                fit(g, label, o.l, o.t + dy, o.w(), o.h(), new Color(0x707070), 0, false);
            }
        }

        private void drawLamp(Graphics2D g, B1Screen.Obj o, int dy) {
            Color bright, dark;
            switch (o.lamp) {
                case "RED": bright = new Color(0xFF3A3A); dark = new Color(0x6E1010); break;
                case "ORANGE": bright = new Color(0xFFA030); dark = new Color(0x6B4A1A); break;
                default: bright = new Color(0x3CFF3C); dark = new Color(0x0C4A0C);
            }
            int x = o.l, y = o.t + dy, w = Math.min(o.w(), 25), h = Math.min(o.h(), 25);
            g.setColor(new Color(0x404040));
            g.fillRect(x, y, w, h);
            g.setPaint(new GradientPaint(x, y, dark.brighter(), x + w, y, dark));
            g.fillRect(x + 2, y + 2, w - 4, h - 4);
            g.setColor(new Color(255, 255, 255, 120));
            g.drawLine(x + 3, y + h - 4, x + w - 4, y + 3); // non collegata
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
