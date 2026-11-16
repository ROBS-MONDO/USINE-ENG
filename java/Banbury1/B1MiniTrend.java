package Banbury1;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Trend ridotto con la sola temperatura (scala 0-200 come l'OnlineTrendControl del WinCC), disegnato dentro
 * il riquadro [controllo WinCC] delle schermate (Display). Usa gli stessi dati registrati del trend grande.
 * SOLA LETTURA.
 */
final class B1MiniTrend {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private B1MiniTrend() {
    }

    /**
     * Scala automatica: minimo e massimo visibili con un po' di margine, arrotondati a un passo "tondo"
     * (5, 10, 20, 25, 50), limitata a 0-200. Senza dati: 0-200. Ritorna {minimo, massimo, passo}.
     */
    static double[] scale(List<double[]> pts) {
        if (pts.isEmpty()) {
            return new double[]{0, 200, 40};
        }
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double[] p : pts) {
            min = Math.min(min, p[1]);
            max = Math.max(max, p[1]);
        }
        double span = Math.max(40, (max - min) * 1.2);
        double raw = span / 5;
        double[] steps = {5, 10, 20, 25, 50};
        double step = 50;
        for (double st : steps) {
            if (st >= raw) {
                step = st;
                break;
            }
        }
        double lo = Math.floor((min - (span - (max - min)) / 2) / step) * step;
        lo = Math.max(0, lo);
        double hi = Math.ceil((max + (span - (max - min)) / 2) / step) * step;
        hi = Math.min(200, Math.max(hi, lo + 2 * step));
        if (hi - lo < 2 * step) {
            lo = Math.max(0, hi - 2 * step);
        }
        return new double[]{lo, hi, step};
    }

    /** Disegna il trend nel rettangolo dato; windowMs = ampiezza della finestra (il WinCC usa 20 minuti). */
    static void paint(Graphics2D g, int x, int y, int w, int h, long windowMs) {
        g.setColor(Color.WHITE);
        g.fillRect(x, y, w, h);
        int left = 38, right = 10, top = 14, bottom = 22;
        int px = x + left, py = y + top, pw = Math.max(20, w - left - right), ph = Math.max(20, h - top - bottom);

        long to = System.currentTimeMillis();
        long from = to - windowMs;

        // dati della finestra e scala adattata ai valori visibili (cosi' la curva occupa tutto il riquadro)
        B1TrendStore store = B1TrendStore.shared(B1TrendRecorder.seriesIds());
        List<double[]> pts = store.buffer("temp").downsample(from, to, pw);
        double[] range = scale(pts);
        double lo = range[0];
        double hi = range[1];
        double step = range[2];
        int divisions = (int) Math.round((hi - lo) / step);

        // griglia e assi
        g.setFont(new Font("Arial", Font.PLAIN, 10));
        FontMetrics fm = g.getFontMetrics();
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i <= divisions; i++) {
            int yy = py + ph - ph * i / divisions;
            g.setColor(i == 0 ? new Color(0x707070) : new Color(0xD8DBE2));
            g.drawLine(px, yy, px + pw, yy);
            g.setColor(new Color(0xC00000));
            String s = String.valueOf(Math.round(lo + step * i));
            g.drawString(s, px - 6 - fm.stringWidth(s), yy + 4);
        }
        for (int i = 0; i <= 4; i++) {
            int xx = px + pw * i / 4;
            g.setColor(new Color(0xD8DBE2));
            g.drawLine(xx, py, xx, py + ph);
            g.setColor(new Color(0x303840));
            String s = HM.format(Instant.ofEpochMilli(from + (to - from) * i / 4));
            int sw = fm.stringWidth(s);
            g.drawString(s, Math.max(x + 1, Math.min(x + w - sw - 1, xx - sw / 2)), py + ph + 14);
        }
        g.setColor(new Color(0x707070));
        g.drawRect(px, py, pw, ph);
        g.setColor(new Color(0xC00000));
        g.drawString("\u00b0C", x + 4, y + 11);

        // curva della temperatura
        if (pts.isEmpty()) {
            g.setColor(new Color(0x707070));
            g.drawString("nessun dato", px + pw / 2 - 30, py + ph / 2);
            return;
        }
        g.setColor(new Color(0xE00000));
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int prevX = 0;
        int prevY = 0;
        long prevT = 0;
        boolean have = false;
        double lastV = 0;
        for (double[] p : pts) {
            int xx = px + (int) Math.round((p[0] - from) / (double) (to - from) * pw);
            int yy = py + ph - (int) Math.round((Math.max(lo, Math.min(hi, p[1])) - lo) / (hi - lo) * ph);
            if (have && p[0] - prevT <= 120_000) {
                g.drawLine(prevX, prevY, xx, yy);
            }
            prevX = xx;
            prevY = yy;
            prevT = (long) p[0];
            have = true;
            lastV = p[1];
        }
        g.setStroke(new BasicStroke(1f));
        String v = String.format(Locale.ITALY, "%.0f \u00b0C", lastV);
        g.setFont(new Font("Arial", Font.BOLD, 11));
        int vw = g.getFontMetrics().stringWidth(v);
        g.setColor(new Color(255, 255, 255, 230));
        g.fillRect(px + pw - vw - 10, py + 3, vw + 8, 16);
        g.setColor(new Color(0xC00000));
        g.drawString(v, px + pw - vw - 6, py + 15);
    }
}
