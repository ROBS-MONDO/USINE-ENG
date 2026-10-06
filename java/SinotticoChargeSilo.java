package GOV;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Sinottico 1: CHARGE SILO.
 * Ridisegno in Java2D del sinottico GOVONI (12 silos, tramoggia, pannello ciclo, barra comandi).
 * Il disegno usa coordinate logiche 1904x1026 e viene scalato per riempire la finestra.
 */
public class SinotticoChargeSilo extends JFrame {

    private static final int W = 1904;
    private static final int H = 1026;

    private static final Color BG = new Color(192, 192, 192);
    private static final Color TITLE_BG = new Color(192, 255, 192);
    private static final Color LED_OFF = new Color(0, 100, 0);
    private static final Color LED_ON = new Color(0, 230, 0);
    private static final Color INFO_BLUE = new Color(0, 0, 200);
    private static final Color BTN_BG = new Color(224, 224, 224);

    /** Centro X, top Y, X dello ZS, ZS a sinistra, indice filtro (-1 = nessuno). */
    private static final int[][] SILOS = {
        {401, 180, 303, 1, 101}, {536, 180, 635, 0, -1}, {780, 180, 681, 1, 103},
        {913, 180, 1011, 0, -1}, {1102, 180, 1200, 0, 105}, {1285, 180, 1383, 0, -1},
        {1458, 180, 1556, 0, -1},
        {698, 467, 796, 0, -1}, {882, 467, 981, 0, 109}, {1071, 467, 1170, 0, 110},
        {1262, 467, 1360, 0, -1}, {1457, 467, 1556, 0, -1}
    };
    private static final int SILO_W = 100;
    private static final int SILO_H = 205;

    // ---- stato visualizzato -------------------------------------------------
    private final boolean[] lsh = new boolean[12];
    private final boolean[] lsl = new boolean[12];
    private final boolean[] zs = new boolean[12];
    private boolean lsh40, psl50, ev10, ev20, ev30;
    private String typeCycle = "Automatico";
    private String etatCycle = "Riposo";
    private String condCycle = "Ok";
    private String siloSel = "---";
    private String message = "6-ott    09:15  Niveau maximum LSH102 couvert";
    private String user = "ADMIN";
    private String mode = "MASTER";

    private final List<Rectangle> hitRects = new ArrayList<>();
    private final List<Runnable> hitActions = new ArrayList<>();

    private final Canvas canvas = new Canvas();

    public SinotticoChargeSilo() {
        super("SYNOPTIQUE 1: CHARGE SILO");
        for (int i = 0; i < 12; i++) lsl[i] = true;
        lsh40 = true;
        zs[0] = zs[1] = true;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(canvas);
        canvas.setPreferredSize(new Dimension(1280, 690));
        pack();
        setLocationRelativeTo(null);
        new Timer(1000, e -> canvas.repaint()).start();
    }

    // ---- API pubblica per collegare il PLC / DB -----------------------------
    public void setSilo(int n, boolean lshOn, boolean lslOn, boolean zsOn) {
        lsh[n - 1] = lshOn; lsl[n - 1] = lslOn; zs[n - 1] = zsOn; canvas.repaint();
    }
    public void setHopper(boolean lsh40On, boolean psl50On, boolean ev10On, boolean ev20On, boolean ev30On) {
        lsh40 = lsh40On; psl50 = psl50On; ev10 = ev10On; ev20 = ev20On; ev30 = ev30On; canvas.repaint();
    }
    public void setCycle(String type, String etat, String cond, String silo) {
        typeCycle = type; etatCycle = etat; condCycle = cond; siloSel = silo; canvas.repaint();
    }
    public void setMessage(String m) { message = m; canvas.repaint(); }
    public void setUser(String u, String m) { user = u; mode = m; canvas.repaint(); }

    // ---- pannello di disegno -----------------------------------------------
    private class Canvas extends JPanel {
        private double scale = 1;
        private double offX, offY;

        Canvas() {
            setBackground(BG);
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    int lx = (int) ((e.getX() - offX) / scale);
                    int ly = (int) ((e.getY() - offY) / scale);
                    for (int i = 0; i < hitRects.size(); i++) {
                        if (hitRects.get(i).contains(lx, ly)) { hitActions.get(i).run(); repaint(); return; }
                    }
                }
            });
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            scale = Math.min(getWidth() / (double) W, getHeight() / (double) H);
            offX = (getWidth() - W * scale) / 2;
            offY = (getHeight() - H * scale) / 2;
            g.translate(offX, offY);
            g.scale(scale, scale);
            hitRects.clear();
            hitActions.clear();

            drawHeader(g);
            for (int i = 0; i < 12; i++) drawSilo(g, i);
            drawHopper(g);
            drawInfoPanel(g);
            drawBottom(g);
            g.dispose();
        }
    }

    // ---- primitive ----------------------------------------------------------
    private static Font f(int style, int size) { return new Font("Arial", style, size); }

    private static void text(Graphics2D g, String s, int cx, int baseY, Font font, Color c) {
        g.setFont(font);
        g.setColor(c);
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, baseY);
    }

    private static void bevel(Graphics2D g, int x, int y, int w, int h, boolean raised) {
        g.setColor(raised ? Color.WHITE : new Color(110, 110, 110));
        g.drawLine(x, y, x + w - 1, y);
        g.drawLine(x, y, x, y + h - 1);
        g.setColor(raised ? new Color(110, 110, 110) : Color.WHITE);
        g.drawLine(x, y + h - 1, x + w - 1, y + h - 1);
        g.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
    }

    private void button(Graphics2D g, String label, int x, int y, int w, int h, Color bg, Color fg, Runnable action) {
        g.setColor(bg);
        g.fillRect(x, y, w, h);
        bevel(g, x, y, w, h, true);
        text(g, label, x + w / 2, y + h / 2 + 6, f(Font.PLAIN, 17), fg);
        if (action != null) { hitRects.add(new Rectangle(x, y, w, h)); hitActions.add(action); }
    }

    private static void led(Graphics2D g, int cx, int cy, int r, boolean on, Color onC, Color offC) {
        g.setColor(on ? onC : offC);
        g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1f));
        g.drawOval(cx - r, cy - r, 2 * r, 2 * r);
    }

    /** Tubo grigio con effetto 3D. */
    private static void tube(Graphics2D g, Shape path, float w) {
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(90, 90, 90));
        g.draw(path);
        g.setStroke(new BasicStroke(w - 3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(215, 215, 215));
        g.draw(path);
        g.setStroke(new BasicStroke(Math.max(2, w / 4), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.setColor(Color.WHITE);
        g.draw(path);
    }

    private static Paint orange(double x, double w) {
        return new LinearGradientPaint((float) x, 0, (float) (x + w), 0,
            new float[]{0f, 0.35f, 1f},
            new Color[]{new Color(185, 95, 25), new Color(240, 170, 95), new Color(160, 80, 20)});
    }

    // ---- intestazione -------------------------------------------------------
    private void drawHeader(Graphics2D g) {
        // logo GSBI
        g.setColor(Color.WHITE);
        g.fillRect(283, 30, 204, 58);
        g.setColor(new Color(20, 20, 160));
        g.setFont(f(Font.BOLD | Font.ITALIC, 38));
        g.drawString("GSBI", 315, 62);
        g.setColor(new Color(20, 20, 160));
        g.fillRect(287, 66, 196, 18);
        g.setColor(Color.WHITE);
        g.setFont(f(Font.BOLD, 13));
        g.drawString("govoni sim bianca impianti", 291, 80);

        // titolo
        g.setColor(TITLE_BG);
        g.fillRect(491, 32, 919, 56);
        text(g, "SYNOPTIQUE 1: CHARGE SILO", 953, 73, f(Font.PLAIN, 26), Color.BLACK);

        // logo MONDO
        g.setColor(Color.WHITE);
        g.fillRect(1415, 30, 204, 60);
        g.setColor(new Color(0, 90, 170));
        g.fillRect(1419, 40, 28, 40);
        g.setColor(Color.BLACK);
        g.setFont(f(Font.BOLD, 40));
        g.drawString("MONDO", 1452, 76);
    }

    // ---- silos --------------------------------------------------------------
    private void drawSilo(Graphics2D g, int i) {
        int[] s = SILOS[i];
        int cx = s[0], top = s[1], zsx = s[2];
        boolean zsLeft = s[3] == 1;
        int filter = s[4];
        int left = cx - SILO_W / 2;
        boolean row2 = top > 400;
        int zsy = row2 ? 611 : 326;

        // tubo verso ZS
        Path2D p = new Path2D.Double();
        if (zsLeft) {
            p.moveTo(left + 12, top + 6);
            p.lineTo(zsx + 22, top - 6);
            p.lineTo(zsx, top + 10);
            p.lineTo(zsx, zsy - 12);
        } else {
            p.moveTo(cx + 35, top - 8);
            p.lineTo(zsx - 12, top - 8);
            p.lineTo(zsx, top + 10);
            p.lineTo(zsx, zsy - 12);
        }
        tube(g, p, 9f);

        // filtro in cima
        if (filter > 0) {
            int fx = cx - 25, fy = top - 28;
            g.setPaint(new GradientPaint(fx, 0, new Color(200, 110, 30), fx + 50, 0, new Color(235, 160, 70)));
            g.fillRect(fx, fy, 50, 30);
            g.setColor(new Color(110, 50, 10));
            for (int k = 3; k < 50; k += 6) g.drawLine(fx + k, fy + 2, fx + k, fy + 28);
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRect(fx, fy, 50, 30);
            g.setColor(new Color(140, 140, 140));
            g.fillOval(fx - 22, fy + 3, 16, 16);
            g.setColor(Color.BLACK);
            g.drawOval(fx - 22, fy + 3, 16, 16);
            text(g, "F" + filter, cx + 4, fy - 6, f(Font.BOLD, 20), Color.BLACK);
            Path2D c = new Path2D.Double();
            c.moveTo(fx + 50, fy + 15);
            c.lineTo(fx + 62, fy + 15);
            tube(g, c, 6f);
            if (zsLeft) { // collegamento verso il silo vicino
                Path2D c2 = new Path2D.Double();
                c2.moveTo(cx + 38, top - 6);
                c2.lineTo(cx + 95, top - 6);
                c2.lineTo(cx + 125, top + 12);
                tube(g, c2, 8f);
            }
        }

        // corpo silo
        Path2D body = new Path2D.Double();
        double r = left + SILO_W;
        body.moveTo(left, top + 22);
        body.curveTo(left + 5, top + 2, r - 5, top + 2, r, top + 22);
        body.lineTo(r, top + SILO_H - 10);
        int teeth = 9;
        double tw = SILO_W / (double) teeth;
        for (int k = 0; k < teeth; k++) {
            double x0 = r - k * tw;
            body.lineTo(x0 - tw / 2, top + SILO_H + ((k % 2 == 0) ? 0 : -16));
            body.lineTo(x0 - tw, top + SILO_H - 10 + ((k % 2 == 0) ? 8 : 0));
        }
        body.closePath();
        g.setPaint(orange(left, SILO_W));
        g.fill(body);
        g.setColor(new Color(110, 55, 15));
        g.setStroke(new BasicStroke(1f));
        g.draw(body);

        // LED e testi
        led(g, cx - 2, top + 40, 9, lsh[i], LED_ON, LED_OFF);
        text(g, "LSH" + (100 + i + 1), cx + 2, top + 72, f(Font.BOLD, 21), Color.BLACK);
        text(g, String.valueOf(i + 1), cx - 2, top + 110, f(Font.PLAIN, 36), Color.BLACK);
        text(g, "LSL" + (100 + i + 1), cx + 4, top + 142, f(Font.BOLD, 21), Color.BLACK);
        led(g, cx - 2, top + 159, 9, lsl[i], LED_ON, LED_OFF);

        // sensore ZS
        g.setColor(new Color(150, 150, 150));
        g.fillRect(zsx - 12, zsy - 12, 24, 24);
        g.setColor(Color.BLACK);
        g.drawRect(zsx - 12, zsy - 12, 24, 24);
        led(g, zsx, zsy, 8, zs[i], LED_ON, LED_OFF);
        text(g, "ZS", zsx, zsy + 34, f(Font.BOLD, 21), Color.BLACK);

        // click sul silo: vuoto -> LSL -> LSH+LSL
        final int idx = i;
        hitRects.add(new Rectangle(left, top, SILO_W, SILO_H));
        hitActions.add(() -> {
            if (!lsl[idx]) lsl[idx] = true;
            else if (!lsh[idx]) lsh[idx] = true;
            else { lsh[idx] = false; lsl[idx] = false; }
        });
    }

    // ---- tramoggia di carico -----------------------------------------------
    private void drawHopper(Graphics2D g) {
        // tubi
        Path2D bottom = new Path2D.Double();
        bottom.moveTo(362, 902);
        bottom.lineTo(712, 902);
        bottom.lineTo(712, 795);
        tube(g, bottom, 9f);
        Path2D left = new Path2D.Double();
        left.moveTo(404, 750);
        left.lineTo(404, 902);
        tube(g, left, 9f);
        Path2D pipeEv20 = new Path2D.Double();
        pipeEv20.moveTo(362, 736);
        pipeEv20.lineTo(445, 736);
        tube(g, pipeEv20, 9f);
        Path2D in = new Path2D.Double();
        in.moveTo(511, 640);
        in.lineTo(511, 712);
        tube(g, in, 14f);

        // tubo ribbed a destra
        g.setColor(new Color(90, 90, 90));
        g.fillRect(703, 738, 19, 58);
        g.setColor(Color.WHITE);
        for (int y = 741; y < 795; y += 5) g.drawLine(704, y, 721, y);

        // imbuto
        Path2D fun = new Path2D.Double();
        fun.moveTo(480, 607); fun.lineTo(542, 607); fun.lineTo(511, 637); fun.closePath();
        g.setPaint(orange(480, 62));
        g.fill(fun);
        g.setColor(new Color(110, 55, 15));
        g.draw(fun);

        // serbatoio
        Path2D tank = new Path2D.Double();
        tank.moveTo(437, 722);
        tank.curveTo(450, 704, 575, 704, 590, 722);
        tank.lineTo(555, 850);
        tank.lineTo(470, 850);
        tank.closePath();
        g.setPaint(orange(437, 153));
        g.fill(tank);
        g.setColor(new Color(110, 55, 15));
        g.draw(tank);
        g.setColor(new Color(150, 150, 150));
        g.fillRect(503, 850, 16, 30);
        g.setColor(Color.BLACK);
        g.drawRect(503, 850, 16, 30);

        led(g, 512, 765, 10, lsh40, LED_ON, LED_OFF);
        text(g, "LSH40", 522, 795, f(Font.BOLD, 21), Color.BLACK);
        g.setColor(psl50 ? new Color(230, 40, 40) : Color.BLACK);
        g.fillOval(585, 800, 26, 26);
        text(g, "PSL50", 610, 842, f(Font.BOLD, 21), Color.BLACK);

        // elettrovalvole
        valve(g, 511, 668, ev30, true);
        text(g, "EV30", 443, 679, f(Font.BOLD, 21), Color.BLACK);
        g.setColor(new Color(150, 150, 150));
        g.fillOval(469, 661, 18, 18);
        g.setColor(Color.BLACK);
        g.drawOval(469, 661, 18, 18);

        valve(g, 345, 735, ev20, false);
        led(g, 346, 708, 10, ev20, LED_ON, LED_ON);
        text(g, "EV20", 348, 777, f(Font.BOLD, 21), Color.BLACK);

        valve(g, 349, 900, ev10, false);
        g.setColor(ev10 ? LED_ON : new Color(120, 120, 120));
        g.fillOval(340, 866, 20, 20);
        g.setColor(Color.BLACK);
        g.drawOval(340, 866, 20, 20);
        text(g, "EV10", 352, 941, f(Font.BOLD, 21), Color.BLACK);
    }

    private void valve(Graphics2D g, int cx, int cy, boolean open, boolean vertical) {
        Color c = open ? new Color(120, 240, 120) : new Color(215, 215, 215);
        g.setColor(c);
        if (vertical) {
            g.fillRect(cx - 12, cy - 12, 24, 24);
            g.setColor(Color.BLACK);
            g.drawRect(cx - 12, cy - 12, 24, 24);
            g.drawLine(cx - 12, cy, cx + 12, cy);
        } else {
            g.fillRect(cx - 18, cy - 14, 36, 28);
            g.setColor(Color.BLACK);
            g.drawRect(cx - 18, cy - 14, 36, 28);
            g.drawLine(cx - 6, cy - 14, cx - 6, cy + 14);
            g.drawLine(cx + 6, cy - 14, cx + 6, cy + 14);
        }
    }

    // ---- pannello info ciclo -----------------------------------------------
    private void drawInfoPanel(Graphics2D g) {
        g.setColor(new Color(236, 236, 240));
        g.fillRect(801, 724, 379, 164);
        bevel(g, 801, 724, 379, 164, true);
        String[] lab = {"Type  Cycle", "Etat cycle", "Cond. Cycle", "Silo"};
        int[] ly = {755, 791, 827, 863};
        for (int i = 0; i < 4; i++) {
            g.setFont(f(Font.PLAIN, 20));
            g.setColor(INFO_BLUE);
            g.drawString(lab[i], 818, ly[i]);
        }
        field(g, 943, 734, 215, typeCycle);
        field(g, 943, 770, 215, etatCycle);
        field(g, 943, 806, 215, condCycle);
        field(g, 941, 842, 57, siloSel);

        button(g, "SIn: BY1", 906, 933, 103, 31, new Color(120, 120, 255), Color.BLACK, () -> { });
        button(g, "SIn: BY2", 1014, 933, 103, 31, new Color(120, 120, 255), Color.BLACK, () -> { });
    }

    private void field(Graphics2D g, int x, int y, int w, String v) {
        g.setColor(Color.WHITE);
        g.fillRect(x, y, w, 30);
        bevel(g, x, y, w, 30, false);
        text(g, v, x + w / 2, y + 22, f(Font.PLAIN, 20), Color.BLACK);
    }

    // ---- barra inferiore ----------------------------------------------------
    private void drawBottom(Graphics2D g) {
        Date now = new Date();
        g.setColor(Color.WHITE);
        g.fillRect(283, 966, 152, 60);
        bevel(g, 283, 966, 152, 60, true);
        text(g, new SimpleDateFormat("HH:mm:ss").format(now), 360, 993, f(Font.PLAIN, 20), Color.BLACK);
        text(g, new SimpleDateFormat("dd/MM/yyyy").format(now), 360, 1020, f(Font.PLAIN, 20), Color.BLACK);

        Color fg = Color.BLACK;
        button(g, "Product.", 447, 972, 120, 24, BTN_BG, fg, () -> { });
        button(g, "Archive", 570, 972, 120, 24, BTN_BG, fg, () -> { });
        button(g, "Synoptiques", 447, 998, 120, 24, BTN_BG, fg, () -> { });
        button(g, "Historique", 570, 998, 120, 24, BTN_BG, fg, () -> { });

        // striscia messaggi
        g.setColor(Color.BLACK);
        g.fillRect(702, 972, 641, 20);
        g.setColor(Color.WHITE);
        g.setFont(f(Font.PLAIN, 15));
        g.drawString(message, 706, 988);
        g.setColor(BTN_BG);
        g.fillRect(1320, 973, 22, 18);
        bevel(g, 1320, 973, 22, 18, true);

        button(g, "Recette", 704, 998, 97, 24, Color.WHITE, fg, () -> { });
        button(g, "RESET MAN", 808, 998, 121, 24, Color.WHITE, fg, () -> { });
        button(g, "SELECTION PAGE", 935, 998, 228, 24, new Color(0, 0, 255), new Color(120, 120, 255), () -> { });

        g.setColor(Color.BLACK);
        g.setFont(f(Font.PLAIN, 14));
        g.drawString(user, 1226, 1009);
        g.drawString(mode, 1289, 1022);

        button(g, "Alerte", 1365, 972, 120, 24, BTN_BG, fg, () -> { });
        button(g, "rEset Allert", 1488, 972, 120, 24, BTN_BG, fg, () -> { });
        button(g, "PASSWORD", 1365, 998, 120, 24, BTN_BG, fg, () -> { });
        button(g, "taCitate", 1488, 998, 120, 24, BTN_BG, fg, () -> { });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SinotticoChargeSilo().setVisible(true));
    }
}
