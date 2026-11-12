package Banbury1;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Grafico del trend di B1 (asse tempo + serie, griglia, sfondo Auto/Manu, righello con i valori).
 * Componente autonomo: lo usano la finestra dei trend e le anteprime.
 */
public final class B1TrendChart extends JComponent {

    /** Una serie del grafico. */
    public static final class Series {
        final String id;
        final String label;
        final String unit;
        final Color color;
        final double min;
        final double max;
        final String liveKey;     // chiave del sinottico (B1Source.num) per il LIVE
        final String sqlColumn;   // colonna di Datas_B1 per lo STORICO (null = non disponibile)
        boolean visible = true;
        final List<double[]> pts = new ArrayList<>(); // {epochMillis, valore}

        Series(String id, String label, String unit, Color color, double min, double max, String liveKey,
               String sqlColumn) {
            this.id = id;
            this.label = label;
            this.unit = unit;
            this.color = color;
            this.min = min;
            this.max = max;
            this.liveKey = liveKey;
            this.sqlColumn = sqlColumn;
        }
    }

    /** Serie predefinite. Temperatura come nel WinCC (rosso, 0-200). */
    static List<Series> defaultSeries() {
        List<Series> l = new ArrayList<>();
        l.add(new Series("temp", "Temperatura", "°C", new Color(0xFF, 0x00, 0x00), 0, 200, "temp", "Temperature"));
        l.add(new Series("amp", "Corrente", "A", new Color(0x20, 0x60, 0xD0), 0, 100, "current", "Amperage"));
        l.add(new Series("rpm", "Velocità rotori", "rpm", new Color(0x20, 0x9A, 0x20), 0, 100, "rpm_act", "Vitesse"));
        l.add(new Series("press", "Pressione", "bar", new Color(0xE0, 0x80, 0x00), 0, 10, "press_act", null));
        return l;
    }

    // ------------------------------------------------------------------ funzioni pure (testabili)
    /** Riduce a al massimo un punto minimo e uno massimo per colonna di pixel. */
    static List<double[]> downsample(List<double[]> pts, long from, long to, int columns) {
        List<double[]> out = new ArrayList<>();
        if (pts.isEmpty() || columns < 1 || to <= from) {
            return out;
        }
        double span = (double) (to - from) / columns;
        int curCol = Integer.MIN_VALUE;
        double[] lo = null;
        double[] hi = null;
        for (double[] p : pts) {
            if (p[0] < from || p[0] > to) {
                continue;
            }
            int col = (int) ((p[0] - from) / span);
            if (col != curCol) {
                flush(out, lo, hi);
                curCol = col;
                lo = p;
                hi = p;
            } else {
                if (p[1] < lo[1]) {
                    lo = p;
                }
                if (p[1] > hi[1]) {
                    hi = p;
                }
            }
        }
        flush(out, lo, hi);
        return out;
    }

    private static void flush(List<double[]> out, double[] lo, double[] hi) {
        if (lo == null) {
            return;
        }
        if (lo == hi) {
            out.add(lo);
        } else if (lo[0] <= hi[0]) {
            out.add(lo);
            out.add(hi);
        } else {
            out.add(hi);
            out.add(lo);
        }
    }

    /** Passo "tondo" della griglia del tempo (ms) per avere circa 6-10 divisioni. */
    static long niceTimeStep(long spanMs) {
        long[] steps = {1000, 2000, 5000, 10000, 15000, 30000, 60000, 120000, 300000, 600000, 900000,
                1800000, 3600000, 7200000, 10800000, 21600000, 43200000, 86400000};
        for (long s : steps) {
            if (spanMs / s <= 10) {
                return s;
            }
        }
        return steps[steps.length - 1];
    }

    final List<Series> series;
    volatile List<long[]> modeSpans = new ArrayList<>(); // {inizio, fine, 1=Auto/0=Manu}
    volatile long rangeMs = 20 * 60_000L;


        private static final int ML = 64;
        private static final int MR = 16;
        private static final int MT = 12;
        private static final int MB = 34;
        private int hoverX = -1;
        private long fixedTo = -1;   // per le immagini di prova
        private long fixedFrom = -1;

        B1TrendChart(List<Series> series) {
            this.series = series;
            setPreferredSize(new Dimension(1000, 520));
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    hoverX = e.getX();
                    repaint();
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    hoverX = -1;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            int pw = w - ML - MR;
            int ph = h - MT - MB;
            if (pw < 50 || ph < 50) {
                g.dispose();
                return;
            }
            long to = fixedTo > 0 ? fixedTo : System.currentTimeMillis();
            long from = fixedFrom > 0 ? fixedFrom : to - rangeMs;
            // sfondo Auto / Manu (come PLC3_Graph)
            for (long[] m : modeSpans) {
                if (m[1] < from || m[0] > to) {
                    continue;
                }
                int x1 = ML + (int) ((Math.max(m[0], from) - from) * (double) pw / (to - from));
                int x2 = ML + (int) ((Math.min(m[1], to) - from) * (double) pw / (to - from));
                g.setColor(m[2] == 1 ? new Color(0xEA, 0xF6, 0xEA) : new Color(0xFB, 0xF1, 0xE0));
                g.fillRect(x1, MT, Math.max(1, x2 - x1), ph);
            }
            g.setFont(new Font("Arial", Font.PLAIN, 11));
            FontMetrics fm = g.getFontMetrics();
            // griglia verticale (tempo)
            long step = niceTimeStep(to - from);
            DateTimeFormatter tf = DateTimeFormatter.ofPattern(step >= 3_600_000L && to - from > 86_400_000L
                    ? "dd/MM HH:mm" : (step < 60_000L ? "HH:mm:ss" : "HH:mm"));
            ZoneId zone = ZoneId.systemDefault();
            long first = ((from / step) + 1) * step;
            for (long t = first; t < to; t += step) {
                int x = ML + (int) ((t - from) * (double) pw / (to - from));
                g.setColor(new Color(0xE4, 0xE4, 0xEA));
                g.drawLine(x, MT, x, MT + ph);
                String s = LocalDateTime.ofInstant(Instant.ofEpochMilli(t), zone).format(tf);
                g.setColor(Color.DARK_GRAY);
                g.drawString(s, x - fm.stringWidth(s) / 2, MT + ph + 16);
            }
            // asse valori: scala della prima serie visibile
            Series axis = null;
            for (Series s : series) {
                if (s.visible) {
                    axis = s;
                    break;
                }
            }
            for (int i = 0; i <= 4; i++) {
                int y = MT + ph - ph * i / 4;
                g.setColor(new Color(0xE4, 0xE4, 0xEA));
                g.drawLine(ML, y, ML + pw, y);
                if (axis != null) {
                    double v = axis.min + (axis.max - axis.min) * i / 4.0;
                    String s = String.format(Locale.ITALY, "%.0f", v);
                    g.setColor(axis.color.darker());
                    g.drawString(s, ML - 6 - fm.stringWidth(s), y + 4);
                }
            }
            if (axis != null) {
                g.setColor(axis.color.darker());
                g.drawString(axis.unit, 6, MT + 10);
            }
            g.setColor(new Color(0x70, 0x70, 0x70));
            g.drawRect(ML, MT, pw, ph);
            // serie
            g.setClip(ML, MT, pw + 1, ph + 1);
            for (Series s : series) {
                if (!s.visible) {
                    continue;
                }
                List<double[]> data;
                synchronized (s.pts) {
                    data = downsample(new ArrayList<>(s.pts), from, to, pw);
                }
                if (data.isEmpty()) {
                    continue;
                }
                Path2D path = new Path2D.Double();
                boolean first1 = true;
                double prevT = -1;
                for (double[] p : data) {
                    double x = ML + (p[0] - from) * pw / (double) (to - from);
                    double v = Math.max(s.min, Math.min(s.max, p[1]));
                    double y = MT + ph - (v - s.min) / (s.max - s.min) * ph;
                    if (first1 || p[0] - prevT > 120_000) { // buco di dati: interrompe la linea
                        path.moveTo(x, y);
                        first1 = false;
                    } else {
                        path.lineTo(x, y);
                    }
                    prevT = p[0];
                }
                g.setColor(s.color);
                g.setStroke(new BasicStroke(1.6f));
                g.draw(path);
            }
            g.setClip(null);
            g.setStroke(new BasicStroke(1f));
            // righello con i valori
            if (hoverX >= ML && hoverX <= ML + pw) {
                long t = from + (long) ((hoverX - ML) * (double) (to - from) / pw);
                g.setColor(new Color(0x40, 0x40, 0x40));
                g.drawLine(hoverX, MT, hoverX, MT + ph);
                StringBuilder sb = new StringBuilder(LocalDateTime.ofInstant(Instant.ofEpochMilli(t), zone)
                        .format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")));
                int lines = 1;
                List<String> rows = new ArrayList<>();
                rows.add(sb.toString());
                for (Series s : series) {
                    Double v = s.visible ? valueAt(s, t) : null;
                    if (v != null) {
                        rows.add(String.format(Locale.ITALY, "%s: %.1f %s", s.label, v, s.unit));
                        lines++;
                    }
                }
                int bw = 190;
                int bx = hoverX + 10 + bw > ML + pw ? hoverX - 10 - bw : hoverX + 10;
                g.setColor(new Color(255, 255, 225, 240));
                g.fillRect(bx, MT + 6, bw, 16 * lines + 6);
                g.setColor(Color.GRAY);
                g.drawRect(bx, MT + 6, bw, 16 * lines + 6);
                g.setColor(Color.BLACK);
                for (int i = 0; i < rows.size(); i++) {
                    g.drawString(rows.get(i), bx + 6, MT + 20 + i * 16);
                }
            }
            if (axis == null) {
                g.setColor(Color.GRAY);
                g.drawString("Nessuna serie selezionata", ML + 20, MT + 30);
            }
            g.dispose();
        }
    
    private static Double valueAt(Series s, long t) {
        double[] best = null;
        synchronized (s.pts) {
            for (double[] p : s.pts) {
                if (best == null || Math.abs(p[0] - t) < Math.abs(best[0] - t)) {
                    best = p;
                }
            }
        }
        return best != null && Math.abs(best[0] - t) < 120_000 ? best[1] : null;
    }


    /** Immagine di prova (senza schermo). */
    public static void snapshot(String out, boolean synthetic) throws Exception {
        System.setProperty("java.awt.headless", "true");
        List<Series> ser = defaultSeries();
        long to = System.currentTimeMillis();
        if (synthetic) {
            for (Series s : ser) {
                for (long t = to - 20 * 60_000L; t <= to; t += 4000) {
                    double x = (t - (to - 20 * 60_000L)) / (20.0 * 60_000.0);
                    double v;
                    switch (s.id) {
                        case "temp": v = 40 + 100 * x + 6 * Math.sin(x * 25); break;
                        case "amp": v = 35 + 20 * Math.sin(x * 9); break;
                        case "rpm": v = x < 0.1 ? 330 * x : 33; break;
                        default: v = 5 + 2 * Math.sin(x * 6);
                    }
                    s.pts.add(new double[]{t, v});
                }
            }
        }
        B1TrendChart c = new B1TrendChart(ser);
        c.setSize(1000, 520);
        BufferedImage img = new BufferedImage(1000, 520, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        c.paint(g);
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", new File(out));
    }
}
