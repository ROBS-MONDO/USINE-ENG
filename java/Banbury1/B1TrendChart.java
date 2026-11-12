package Banbury1;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Definizione delle serie del trend di B1 e funzioni di calcolo (riduzione punti, scale, griglia).
 * Il disegno e' in B1TrendPanel, i dati in B1TrendStore.
 */
public final class B1TrendChart {

    private B1TrendChart() {
    }

    /** Una serie del grafico. */
    public static final class Series {
        final String id;
        final String label;
        final String unit;
        final Color color;
        double min;
        double max;
        final double defMin;
        final double defMax;
        final String liveKey;     // chiave del sinottico (B1Source.num) per il LIVE
        final String sqlColumn;   // colonna di Datas_B1 per lo STORICO (null = non disponibile)

        Series(String id, String label, String unit, Color color, double min, double max, String liveKey,
               String sqlColumn) {
            this.id = id;
            this.label = label;
            this.unit = unit;
            this.color = color;
            this.min = min;
            this.max = max;
            this.defMin = min;
            this.defMax = max;
            this.liveKey = liveKey;
            this.sqlColumn = sqlColumn;
        }
    }

    /** Serie predefinite. Temperatura come nel WinCC (rosso, 0-200). */
    static List<Series> defaultSeries() {
        List<Series> l = new ArrayList<>();
        l.add(new Series("temp", "Temperatura", "\u00b0C", new Color(0xFF, 0x00, 0x00), 0, 200, "temp", "Temperature"));
        l.add(new Series("amp", "Corrente", "A", new Color(0x20, 0x60, 0xD0), 0, 400, "current", "Amperage"));
        l.add(new Series("rpm", "Velocit\u00e0 rotori", "rpm", new Color(0x20, 0x9A, 0x20), 0, 100, "rpm_act", "Vitesse"));
        l.add(new Series("press", "Pressione", "bar", new Color(0xE0, 0x80, 0x00), 0, 16, "press_act", null));
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

    static double niceCeil(double x) {
        return TrendScale.niceCeil(x);
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

    static double percentile(double[] values, double q) {
        return TrendScale.percentile(values, q);
    }
}
