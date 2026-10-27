package Banbury;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base dei quadri sinottici Bambury. Disegna tutto cio' che e' comune
 * (intestazione, silos 1-12, mescolatore, banbury, pannelli, barra inferiore);
 * le sottoclassi forniscono la configurazione e la zona inferiore destra.
 *
 * Il disegno usa uno spazio logico di 1375x1023 e viene scalato alla
 * dimensione del pannello.
 */
public abstract class SynoptiquePanel extends JPanel {

    static final int LOGICAL_X = 275, LOGICAL_W = 1375, LOGICAL_H = 1023;

    static final Color BG = new Color(160, 160, 160);
    static final Color BLUE = new Color(0, 0, 255);
    static final Color GREEN = new Color(0, 224, 0);
    static final Color RED = new Color(224, 0, 0);
    static final Color OFF = new Color(138, 138, 138);
    static final Color DARK_ORANGE = new Color(90, 48, 0);
    static final Color LABEL_BLUE = new Color(0, 0, 208);
    static final Color BTN_BLUE = new Color(106, 106, 255);
    static final Color BTN_TXT = new Color(32, 32, 144);

    static final int LEFT = 0, CENTER = 1, RIGHT = 2;

    static final String[] TOP_TITLES = {"Kaolin:", "Kaolin:", "ATH.", "ATH.", "AGER SUP", "Craie.", "Aspiration", "Aspiration"};
    static final int[] TOP_X = {420, 585, 750, 915, 1080, 1250, 1413, 1578};
    static final int[] LOW_N = {12, 11, 10, 9};
    static final String[] LOW_TITLES = {"Kaolin:", "Kaolin:", "Craie.", "Sableuse"};
    static final int[] LOW_X = {1005, 1170, 1338, 1503};

    /** Dati di un pannello Silo/Brut/Set/Net. */
    protected static class WeighPanel {
        final int x, y;
        final String button;
        String silo, brut, set, net;

        WeighPanel(int x, int y, String button, String silo, String brut, String set, String net) {
            this.x = x; this.y = y; this.button = button;
            this.silo = silo; this.brut = brut; this.set = set; this.net = net;
        }
    }

    /** Parametri che differenziano un sinottico dall'altro. */
    protected static class Config {
        String title;
        String lslFormat;          // es. "LSL1%02d" oppure "LSL%02d"
        String suffix;             // "A" oppure "B"
        int activeSilo = -1;       // silo con coclea in marcia e valvola aperta
        String mixerName, filterName, cellName, valve1, valve2;
        int mixerCx, banburyX;
        String fastValve, fan, gc, pls;
        boolean fanArrows;
        List<WeighPanel> panels = new ArrayList<>();
        int recetteX, recetteY;
        String recette = "", lot = "";
        int planningX, planningY, planningW;
        int productionX, productionY, productionW;
        String[] bottomButtons = {"SIn: SILOS", "SIn: BY"};
        String alarm = "21-ott    10:20  Niveau minimum LSL104 découvert";
    }

    protected final Config cfg = new Config();
    private final Map<String, Boolean> leds = new HashMap<>();
    private final Map<String, String> values = new HashMap<>();
    private final List<Object[]> buttons = new ArrayList<>(); // {Rectangle, label}
    private final Timer clock;
    private final Font base = new Font("Arial", Font.PLAIN, 12);

    protected SynoptiquePanel() {
        setBackground(BG);
        setPreferredSize(new Dimension(1100, 820));
        clock = new Timer(1000, e -> repaint());
        clock.start();
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                double s = scale();
                int lx = (int) (e.getX() / s) + LOGICAL_X, ly = (int) (e.getY() / s);
                for (Object[] b : buttons) {
                    if (((Rectangle) b[0]).contains(lx, ly)) {
                        buttonPressed((String) b[1]);
                        return;
                    }
                }
            }
        });
    }

    /* ---------------- API pubblica ---------------- */

    /** Imposta lo stato di un LED/indicatore (es. "LSL104", "V103B", "C103B"). */
    public void setLed(String tag, boolean on) {
        leds.put(tag, on);
        repaint();
    }

    /** Imposta il valore di un campo (es. "W118.brut", "Recette", "Inverter"). */
    public void setValue(String key, String value) {
        values.put(key, value);
        repaint();
    }

    public void setAlarmMessage(String msg) {
        cfg.alarm = msg;
        repaint();
    }

    /** Chiamato al click su un pulsante disegnato (START, STOP, PLANNING, ...). */
    protected void buttonPressed(String label) {
    }

    public void stop() {
        clock.stop();
    }

    protected boolean led(String tag, boolean dflt) {
        Boolean b = leds.get(tag);
        return b == null ? dflt : b;
    }

    protected String value(String key, String dflt) {
        String v = values.get(key);
        return v == null ? dflt : v;
    }

    /* ---------------- Da implementare nelle sottoclassi ---------------- */

    /** Zona inferiore destra (pompe, serbatoi, cisterna, pannello, inverter). */
    protected abstract void paintLowerRight(Graphics2D g);

    /* ---------------- Disegno ---------------- */

    private double scale() {
        return Math.min(getWidth() / (double) LOGICAL_W, getHeight() / (double) LOGICAL_H);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double s = scale();
        g.scale(s, s);
        g.translate(-LOGICAL_X, 0);
        buttons.clear();

        paintHeader(g);
        paintPipes(g);
        for (int i = 0; i < 8; i++) paintTopSilo(g, i);
        for (int i = 0; i < 4; i++) paintLowSilo(g, i);
        paintMixer(g);
        paintBanbury(g);
        paintLeftSide(g);
        paintLowerRight(g);
        paintMainButtons(g);
        paintFooter(g);
        g.dispose();
    }

    /* ----- primitive ----- */

    protected Font font(int size, boolean bold) {
        return base.deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) size);
    }

    protected void text(Graphics2D g, int x, int y, String s, int size, boolean bold, int anchor, Color c) {
        g.setFont(font(size, bold));
        g.setColor(c);
        int w = g.getFontMetrics().stringWidth(s);
        g.drawString(s, anchor == CENTER ? x - w / 2 : anchor == RIGHT ? x - w : x, y);
    }

    protected void label(Graphics2D g, int x, int y, String s) {
        text(g, x, y, s, 19, true, LEFT, Color.BLACK);
    }

    protected void rect(Graphics2D g, int x, int y, int w, int h, Color fill, Color line) {
        if (fill != null) { g.setColor(fill); g.fillRect(x, y, w, h); }
        if (line != null) { g.setColor(line); g.drawRect(x, y, w, h); }
    }

    protected void circle(Graphics2D g, int cx, int cy, int r, Color fill, Color line) {
        if (fill != null) { g.setColor(fill); g.fillOval(cx - r, cy - r, 2 * r, 2 * r); }
        if (line != null) { g.setColor(line); g.drawOval(cx - r, cy - r, 2 * r, 2 * r); }
    }

    protected void ledDot(Graphics2D g, int x, int y, boolean on) {
        circle(g, x, y, 9, on ? GREEN : OFF, new Color(68, 68, 68));
    }

    protected void redLed(Graphics2D g, int x, int y) {
        circle(g, x, y, 9, RED, new Color(68, 68, 68));
    }

    private Paint orange(int x, int w) {
        return new LinearGradientPaint(x, 0, x + w, 0, new float[]{0f, .35f, 1f},
                new Color[]{new Color(138, 74, 16), new Color(232, 160, 80), new Color(163, 92, 24)});
    }

    protected void fillOrange(Graphics2D g, Shape s, int x, int w) {
        g.setPaint(orange(x, w));
        g.fill(s);
        g.setColor(DARK_ORANGE);
        g.setStroke(new BasicStroke(1f));
        g.draw(s);
    }

    /** Tubo: poligonale di punti x,y,x,y,... */
    protected void pipe(Graphics2D g, int... p) {
        Path2D path = new Path2D.Float();
        path.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
        pipe(g, path);
    }

    protected void pipe(Graphics2D g, Path2D path) {
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(11f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(106, 106, 106));
        g.draw(path);
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(225, 225, 225));
        g.draw(path);
        g.setStroke(old);
    }

    protected void dashed(Graphics2D g, int... p) {
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 3f}, 0f));
        g.setColor(new Color(85, 85, 85));
        for (int i = 0; i + 3 < p.length; i += 2) g.drawLine(p[i], p[i + 1], p[i + 2], p[i + 3]);
        g.setStroke(old);
    }

    protected void polygon(Graphics2D g, Color fill, int... p) {
        Polygon poly = new Polygon();
        for (int i = 0; i < p.length; i += 2) poly.addPoint(p[i], p[i + 1]);
        g.setColor(fill);
        g.fillPolygon(poly);
    }

    protected void button(Graphics2D g, int x, int y, int w, int h, String t, int size) {
        rect(g, x, y, w, h, BTN_BLUE, Color.WHITE);
        g.setColor(new Color(68, 68, 68));
        g.drawLine(x + 1, y + h, x + w, y + h);
        g.drawLine(x + w, y + 1, x + w, y + h);
        text(g, x + w / 2, y + h / 2 + 5, t, size, false, CENTER, BTN_TXT);
        buttons.add(new Object[]{new Rectangle(x, y, w, h), t});
    }

    protected void flatButton(Graphics2D g, int x, int y, int w, int h, String t, int size, Color fill) {
        rect(g, x, y, w, h, fill, new Color(238, 238, 238));
        g.setColor(new Color(85, 85, 85));
        g.drawLine(x + 1, y + h, x + w, y + h);
        g.drawLine(x + w, y + 1, x + w, y + h);
        text(g, x + w / 2, y + h / 2 + 5, t, size, false, CENTER, Color.BLACK);
        buttons.add(new Object[]{new Rectangle(x, y, w, h), t});
    }

    protected void flatButton(Graphics2D g, int x, int y, int w, int h, String t, int size) {
        flatButton(g, x, y, w, h, t, size, new Color(196, 196, 196));
    }

    protected void pump(Graphics2D g, int x, int y) {
        circle(g, x, y, 15, new Color(221, 221, 221), new Color(51, 51, 51));
        circle(g, x, y, 7, new Color(153, 153, 153), new Color(51, 51, 51));
    }

    protected void inlineValve(Graphics2D g, int x, int y) {
        rect(g, x - 10, y - 8, 20, 16, new Color(221, 221, 221), new Color(51, 51, 51));
        rect(g, x - 3, y - 22, 6, 14, new Color(85, 85, 85), null);
        circle(g, x, y - 28, 10, new Color(153, 153, 153), new Color(51, 51, 51));
    }

    protected void cistern(Graphics2D g, int x, int y) {
        rect(g, x - 30, y - 6, 60, 6, new Color(85, 85, 85), null);
        rect(g, x - 30, y, 60, 18, new Color(196, 122, 44), DARK_ORANGE);
        Path2D p = new Path2D.Float();
        p.moveTo(x - 26, y + 18); p.lineTo(x + 26, y + 18); p.lineTo(x + 26, y + 60);
        p.lineTo(x + 12, y + 90); p.lineTo(x - 12, y + 90); p.lineTo(x - 26, y + 60); p.closePath();
        fillOrange(g, p, x - 26, 52);
        text(g, x, y + 14, "CISTERNA", 14, true, CENTER, Color.BLACK);
        rect(g, x - 48, y + 62, 96, 28, BLUE, null);
        text(g, x, y + 82, "CCist", 20, true, CENTER, Color.WHITE);
    }

    protected void ceraclorTank(Graphics2D g, int x, int y, String name, int nx, int ny) {
        rect(g, x - 24, y, 48, 16, new Color(196, 122, 44), DARK_ORANGE);
        fillOrange(g, new java.awt.geom.RoundRectangle2D.Float(x - 46, y + 16, 92, 80, 16, 16), x - 46, 92);
        text(g, x, y + 40, "CERACLOR", 14, true, CENTER, Color.BLACK);
        text(g, x, y + 64, "SERBATOIO", 14, true, CENTER, Color.BLACK);
        rect(g, nx, ny, 100, 26, BLUE, null);
        text(g, nx + 50, ny + 20, name, 20, true, CENTER, Color.WHITE);
    }

    protected void weighPanel(Graphics2D g, int x, int y, int w, int h, String silo, String brut, String set, String net) {
        rect(g, x, y, w, h, new Color(180, 180, 180), new Color(102, 102, 102));
        String[] names = {"Silo", "Brut", "Set", "Net"};
        String[] vals = {silo, brut, set, net};
        for (int i = 0; i < 4; i++) {
            int yy = y + 22 + i * 26;
            text(g, x + 12, yy + 5, names[i], 15, false, LEFT, LABEL_BLUE);
            rect(g, x + 62, yy - 10, 86, 22, Color.WHITE, new Color(102, 102, 102));
            text(g, x + 142, yy + 6, vals[i], 15, false, RIGHT, Color.BLACK);
        }
    }

    protected void inverterBox(Graphics2D g, int x, int y, int w, String v) {
        rect(g, x, y, w, 36, new Color(192, 192, 192), new Color(102, 102, 102));
        text(g, x + 8, y + 23, "Inverter %", 15, false, LEFT, LABEL_BLUE);
        rect(g, x + w - 74, y + 5, 68, 26, Color.WHITE, new Color(102, 102, 102));
        text(g, x + w - 40, y + 24, v, 15, false, CENTER, Color.BLACK);
    }

    /* ----- componenti ----- */

    private void paintHeader(Graphics2D g) {
        rect(g, 281, 7, 205, 56, Color.WHITE, new Color(68, 68, 68));
        text(g, 383, 38, "GSBI", 34, true, CENTER, new Color(0, 34, 154));
        rect(g, 285, 41, 197, 18, new Color(0, 32, 192), null);
        text(g, 383, 55, "govoni sim bianca impianti", 12, true, CENTER, Color.WHITE);
        rect(g, 490, 7, 935, 56, new Color(200, 255, 200), new Color(68, 68, 68));
        text(g, 958, 50, cfg.title, 26, false, CENTER, Color.BLACK);
        rect(g, 1430, 7, 205, 56, Color.WHITE, new Color(68, 68, 68));
        rect(g, 1433, 22, 30, 34, new Color(0, 48, 160), null);
        text(g, 1548, 53, "MONDO", 38, true, CENTER, Color.BLACK);
    }

    private void paintPipes(Graphics2D g) {
        pipe(g, 365, 355, 1588, 355, 1588, 660, 858, 660);
        pipe(g, 318, 355, 318, 382, 850, 382, 850, 472, 776, 472);
        polygon(g, new Color(34, 34, 34), 848, 656, 864, 656, 856, 670);
        polygon(g, new Color(34, 34, 34), 300, 408, 324, 408, 318, 428);
    }

    private void silo(Graphics2D g, int cx, int y, String title, String lsl, int num, boolean ev) {
        rect(g, cx - 60, y, 120, 25, BLUE, null);
        text(g, cx, y + 20, title, 20, false, CENTER, Color.WHITE);
        int by = y + 25;
        Path2D p = new Path2D.Float();
        p.moveTo(cx - 52, by); p.lineTo(cx + 52, by); p.lineTo(cx + 52, by + 50);
        p.lineTo(cx + 18, by + 78); p.lineTo(cx - 18, by + 78); p.lineTo(cx - 52, by + 50); p.closePath();
        fillOrange(g, p, cx - 52, 104);
        g.setColor(new Color(221, 221, 221));
        g.setStroke(new BasicStroke(3f));
        g.drawLine(cx - 38, by + 62, cx - 24, by + 52);
        g.drawLine(cx + 38, by + 62, cx + 24, by + 52);
        g.setStroke(new BasicStroke(1f));
        if (ev) {
            rect(g, cx - 22, by + 55, 44, 10, new Color(196, 122, 44), new Color(51, 51, 51));
            rect(g, cx - 40, by + 36, 20, 26, new Color(85, 85, 85), new Color(34, 34, 34));
            circle(g, cx - 70, by + 32, 12, new Color(119, 119, 119), new Color(34, 34, 34));
            rect(g, cx - 82, by + 22, 22, 20, new Color(85, 85, 85), new Color(34, 34, 34));
        }
        text(g, cx - 45, y + 46, lsl, 14, true, LEFT, Color.BLACK);
        ledDot(g, cx - 10, y + 55, led(lsl, true));
        text(g, cx + 50, y + 56, String.valueOf(num), 34, false, RIGHT, Color.BLACK);
    }

    private void flowMeter(Graphics2D g, int cx, int y0, int dropY, String name) {
        int fx = cx - 85, fy = y0 - 30;
        dashed(g, fx, fy + 15, cx + 10, fy + 15, cx + 10, y0 + dropY);
        circle(g, fx + 12, fy - 18, 10, new Color(153, 153, 153), new Color(51, 51, 51));
        g.setColor(new Color(51, 51, 51));
        g.setStroke(new BasicStroke(3f));
        g.drawLine(fx + 12, fy - 8, fx + 12, fy + 8);
        g.setStroke(new BasicStroke(1f));
        rect(g, fx, fy + 4, 24, 22, new Color(221, 221, 221), new Color(51, 51, 51));
        rect(g, fx - 6, fy, 6, 30, new Color(187, 187, 187), new Color(51, 51, 51));
        rect(g, fx + 24, fy, 6, 30, new Color(187, 187, 187), new Color(51, 51, 51));
        text(g, fx + 14, fy + 54, name, 19, true, CENTER, Color.BLACK);
    }

    private void screw(Graphics2D g, int sx, int sy, String name, boolean running) {
        rect(g, sx + 40, sy, 70, 22, running ? new Color(255, 216, 0) : Color.WHITE, new Color(34, 34, 34));
        Shape oldClip = g.getClip();
        g.clipRect(sx + 40, sy, 70, 22);
        g.setColor(new Color(34, 34, 34));
        for (int x = sx + 36; x < sx + 116; x += 6) g.drawLine(x, sy + 22, x + 6, sy);
        g.setClip(oldClip);
        rect(g, sx, sy - 4, 42, 30, new Color(153, 153, 153), new Color(34, 34, 34));
        circle(g, sx + 17, sy + 11, 10, new Color(85, 85, 85), new Color(34, 34, 34));
        if (running) ledDot(g, sx + 48, sy + 11, true);
        label(g, sx - 28, sy + 48, name);
    }

    private void valve(Graphics2D g, int vx, int vy, String name, boolean open, int lblDx) {
        rect(g, vx - 9, vy - 6, 18, 12, open ? GREEN : new Color(221, 221, 221), new Color(51, 51, 51));
        rect(g, vx - 12, vy - 12, 24, 4, new Color(136, 136, 136), null);
        rect(g, vx - 12, vy + 8, 24, 4, new Color(136, 136, 136), null);
        ledDot(g, vx + 30, vy - 4, true);
        ledDot(g, vx + 45, vy - 4, false);
        label(g, vx + lblDx, vy + 22, name);
    }

    private void paintTopSilo(Graphics2D g, int i) {
        int n = i + 1, cx = TOP_X[i], y = 75, y0 = y + 25 + 78;
        boolean ev = n == 4 || n == 6;
        silo(g, cx, y, TOP_TITLES[i], String.format(cfg.lslFormat, n), n, ev);
        Path2D p = new Path2D.Float();
        int sy = y0 + 75;
        p.moveTo(cx, y0); p.lineTo(cx, y0 + 25);
        p.quadTo(cx, y0 + 40, cx + 14, y0 + 40);
        p.quadTo(cx + 22, y0 + 40, cx + 22, y0 + 55);
        p.lineTo(cx + 22, sy - 2);
        pipe(g, p);
        rect(g, cx + 12, sy - 4, 16, 6, new Color(196, 122, 44), DARK_ORANGE);
        if (ev) label(g, cx - 75, 195, "EV10" + n); else flowMeter(g, cx, y0, 35, "FL10" + n);
        String screw = "C10" + n + cfg.suffix, valve = "V10" + n + cfg.suffix;
        boolean active = cfg.activeSilo == n;
        screw(g, cx - 110, sy, screw, led(screw, active));
        int vx = cx - 58;
        pipe(g, vx, sy + 22, vx, 355);
        valve(g, vx, sy + 68, valve, led(valve, active), 25);
    }

    private void paintLowSilo(Graphics2D g, int i) {
        int n = LOW_N[i], cx = LOW_X[i], y = 375, y0 = y + 25 + 78;
        boolean ev = n == 9;
        String nn = String.format("%02d", n);
        silo(g, cx, y, LOW_TITLES[i], String.format(cfg.lslFormat, n), n, ev);
        int sy = y0 + 75;
        Path2D p = new Path2D.Float();
        p.moveTo(cx, y0); p.lineTo(cx, y0 + 22);
        p.quadTo(cx, y0 + 36, cx + 14, y0 + 36);
        p.quadTo(cx + 22, y0 + 36, cx + 22, y0 + 50);
        p.lineTo(cx + 22, sy - 2);
        pipe(g, p);
        if (ev) label(g, cx - 85, 495, "EV109"); else flowMeter(g, cx, y0, 30, "FL1" + nn);
        String screw = "C1" + nn + cfg.suffix, valve = "V1" + nn + cfg.suffix;
        screw(g, cx - 105, sy, screw, led(screw, false));
        int vx = cx - 48;
        pipe(g, vx, sy + 22, vx, 658);
        valve(g, vx, sy + 42, valve, led(valve, false), 20);
    }

    private void paintMixer(Graphics2D g) {
        int mx = cfg.mixerCx;
        pipe(g, mx, 585, mx, 790);
        // filtro
        rect(g, mx - 30, 402, 60, 40, new Color(196, 122, 44), DARK_ORANGE);
        g.setColor(new Color(90, 50, 0));
        for (int x = mx - 30; x <= mx + 30; x += 5) g.drawLine(x, 402, x, 442);
        for (int y = 402; y <= 442; y += 5) g.drawLine(mx - 30, y, mx + 30, y);
        rect(g, mx - 32, 398, 64, 8, new Color(196, 122, 44), DARK_ORANGE);
        circle(g, mx + 40, 404, 10, new Color(153, 153, 153), new Color(51, 51, 51));
        label(g, mx + 60, 430, cfg.filterName);
        // corpo
        Path2D p = new Path2D.Float();
        p.moveTo(mx - 63, 452); p.quadTo(mx - 63, 442, mx - 50, 442); p.lineTo(mx + 50, 442);
        p.quadTo(mx + 63, 442, mx + 63, 452); p.lineTo(mx + 63, 535); p.lineTo(mx + 22, 585);
        p.lineTo(mx - 22, 585); p.lineTo(mx - 63, 535); p.closePath();
        fillOrange(g, p, mx - 63, 126);
        text(g, mx, 512, cfg.mixerName, 38, false, CENTER, new Color(17, 17, 17));
        // celle di carico
        g.setColor(new Color(34, 34, 34));
        g.setStroke(new BasicStroke(3f));
        g.drawLine(mx - 120, 543, mx + 120, 543);
        g.setStroke(new BasicStroke(1f));
        polygon(g, new Color(17, 17, 17), mx - 100, 543, mx - 88, 523, mx - 76, 543);
        polygon(g, new Color(17, 17, 17), mx + 90, 543, mx + 102, 523, mx + 114, 543);
        circle(g, mx - 70, 600, 10, new Color(153, 153, 153), new Color(51, 51, 51));
        rect(g, mx - 68, 590, 26, 20, new Color(221, 221, 221), new Color(51, 51, 51));
        text(g, mx - 86, 583, cfg.cellName, 19, true, CENTER, Color.BLACK);
        // valvole di scarico
        rect(g, mx - 9, 618, 18, 12, new Color(221, 221, 221), new Color(51, 51, 51));
        ledDot(g, mx + 34, 628, led(cfg.valve1, true)); ledDot(g, mx + 49, 628, false);
        label(g, mx + 22, 654, cfg.valve1);
        rect(g, mx - 9, 676, 18, 12, new Color(221, 221, 221), new Color(51, 51, 51));
        ledDot(g, mx - 34, 689, led(cfg.valve2, true)); ledDot(g, mx - 18, 689, false);
        text(g, mx - 16, 716, cfg.valve2, 19, true, CENTER, Color.BLACK);
    }

    private void paintBanbury(Graphics2D g) {
        int bx = cfg.banburyX;
        rect(g, bx + 36, 782, 40, 40, new Color(196, 122, 44), DARK_ORANGE);
        rect(g, bx + 28, 822, 56, 8, new Color(216, 146, 74), DARK_ORANGE);
        Rectangle2D body = new Rectangle2D.Float(bx, 822, 118, 88);
        fillOrange(g, body, bx, 118);
        g.setColor(new Color(34, 34, 34));
        g.setStroke(new BasicStroke(2f));
        for (int cx : new int[]{bx + 28, bx + 88}) {
            g.drawOval(cx - 19, 843, 38, 38);
            g.drawLine(cx, 850, cx, 874);
            g.drawLine(cx - 12, 862, cx + 12, 862);
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void paintLeftSide(Graphics2D g) {
        pipe(g, 505, 565, 505, 420, 395, 420);
        pipe(g, 345, 415, 680, 415);
        g.setColor(new Color(0, 224, 192));
        g.fillRoundRect(339, 404, 54, 22, 22, 22);
        g.setColor(new Color(68, 68, 68));
        g.drawRoundRect(339, 404, 54, 22, 22, 22);
        text(g, 397, 456, cfg.fastValve, 19, true, CENTER, Color.BLACK);
        ledDot(g, 366, 440, led(cfg.fastValve, true));
        ledDot(g, 366, 458, false);
        // ventilatore
        rect(g, 296, 483, 196, 92, null, new Color(34, 34, 34));
        circle(g, 327, 507, 22, new Color(221, 221, 221), new Color(51, 51, 51));
        circle(g, 329, 506, 9, new Color(0, 192, 0), new Color(51, 51, 51));
        rect(g, 390, 520, 34, 50, new Color(85, 85, 85), new Color(34, 34, 34));
        rect(g, 405, 562, 34, 16, new Color(119, 119, 119), new Color(34, 34, 34));
        text(g, 338, 553, cfg.fan, 19, true, CENTER, Color.BLACK);
        text(g, 430, 600, cfg.gc, 19, true, CENTER, Color.BLACK);
        AffineTransform old = g.getTransform();
        g.rotate(-Math.PI / 2, 488, 505);
        text(g, 488, 505, cfg.pls, 19, true, CENTER, Color.BLACK);
        g.setTransform(old);
        circle(g, 484, 547, 12, new Color(0, 144, 0), new Color(34, 34, 34));
        text(g, 484, 551, "P", 10, false, CENTER, Color.WHITE);
        if (cfg.fanArrows) {
            polygon(g, new Color(0, 48, 255), 300, 478, 314, 456, 328, 478, 320, 478, 320, 474, 308, 474, 308, 478);
            polygon(g, new Color(0, 224, 0), 386, 545, 362, 533, 362, 541, 342, 541, 342, 549, 362, 549, 362, 557);
        }
        // Desch.
        rect(g, 526, 452, 76, 92, new Color(200, 200, 200), new Color(102, 102, 102));
        text(g, 533, 472, "Desch.", 15, false, LEFT, LABEL_BLUE);
        flatButton(g, 534, 483, 60, 24, "START", 15, new Color(216, 216, 216));
        flatButton(g, 534, 512, 60, 24, "STOP", 15, new Color(216, 216, 216));
        flatButton(g, 296, 606, 172, 30, "RESTART", 14);
        // pannelli di pesata
        for (WeighPanel p : cfg.panels) {
            String k = p.button;
            weighPanel(g, p.x, p.y, 193, 117, value(k + ".silo", p.silo), value(k + ".brut", p.brut),
                    value(k + ".set", p.set), value(k + ".net", p.net));
            button(g, p.x + 198, p.y, 62, 34, p.button, 14);
        }
        // ricetta
        int rx = cfg.recetteX, ry = cfg.recetteY;
        rect(g, rx, ry, 190, 70, new Color(192, 192, 192), new Color(102, 102, 102));
        text(g, rx + 6, ry + 32, "Recette:", 20, true, LEFT, Color.BLACK);
        rect(g, rx + 95, ry + 10, 90, 26, Color.WHITE, new Color(102, 102, 102));
        text(g, rx + 102, ry + 29, value("Recette", cfg.recette), 16, false, LEFT, Color.BLACK);
        text(g, rx + 6, ry + 62, "Lot :", 20, true, LEFT, Color.BLACK);
        rect(g, rx + 95, ry + 42, 90, 26, Color.WHITE, new Color(102, 102, 102));
        text(g, rx + 102, ry + 61, value("Lot", cfg.lot), 16, false, LEFT, Color.BLACK);
    }

    /** Pulsanti disegnati dopo le tubazioni, cosi' restano in primo piano. */
    private void paintMainButtons(Graphics2D g) {
        button(g, cfg.planningX, cfg.planningY, cfg.planningW, 38, "PLANNING", 16);
        button(g, cfg.productionX, cfg.productionY, cfg.productionW, 38, "PRODUCTION", 16);
        button(g, 914, 923, 102, 32, cfg.bottomButtons[0], 13);
        button(g, 1020, 923, 102, 32, cfg.bottomButtons[1], 13);
    }

    private void paintFooter(Graphics2D g) {
        Date now = new Date();
        rect(g, 281, 958, 155, 60, Color.WHITE, new Color(68, 68, 68));
        text(g, 358, 984, new SimpleDateFormat("HH:mm:ss").format(now), 20, false, CENTER, Color.BLACK);
        text(g, 358, 1010, new SimpleDateFormat("MM/dd/yyyy").format(now), 20, false, CENTER, Color.BLACK);
        rect(g, 440, 958, 925, 60, new Color(192, 192, 192), new Color(238, 238, 238));
        Color lt = new Color(184, 184, 184);
        flatButton(g, 446, 963, 122, 22, "Product.", 17);
        flatButton(g, 572, 963, 120, 22, "Archive", 17);
        flatButton(g, 446, 990, 122, 22, "Synoptiques", 15, lt);
        flatButton(g, 572, 990, 120, 22, "Historique", 15, lt);
        rect(g, 704, 963, 650, 20, Color.BLACK, null);
        text(g, 708, 978, cfg.alarm, 14, false, LEFT, Color.WHITE);
        flatButton(g, 1332, 963, 22, 20, "≡", 14);
        Color k = new Color(216, 216, 216);
        flatButton(g, 706, 990, 100, 24, "Recette", 18, k);
        flatButton(g, 810, 990, 124, 24, "RESET MAN", 18, k);
        rect(g, 938, 990, 232, 24, BLUE, null);
        text(g, 1054, 1008, "SELECTION PAGE", 18, false, CENTER, new Color(0, 0, 176));
        text(g, 1236, 1001, "ADMIN", 13, false, LEFT, Color.BLACK);
        text(g, 1300, 1015, "MASTER", 13, false, LEFT, Color.BLACK);
        rect(g, 1370, 958, 258, 60, new Color(192, 192, 192), new Color(238, 238, 238));
        flatButton(g, 1377, 963, 122, 22, "Alerte", 17);
        flatButton(g, 1503, 963, 122, 22, "rEset Allert", 17);
        flatButton(g, 1377, 990, 122, 22, "PASSWORD", 15, lt);
        flatButton(g, 1503, 990, 122, 22, "taCitate", 15, lt);
    }
}
