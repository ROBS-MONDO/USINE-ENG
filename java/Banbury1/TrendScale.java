package Banbury1;

import java.util.Arrays;

/**
 * Calcoli condivisi dai grafici di B1 (B1TrendPanel) e di B2 (PLC3_Graph): scale automatiche robuste ai picchi.
 * Pubblica, cosi' si puo' usare anche da classi nel pacchetto predefinito.
 */
public final class TrendScale {

    private TrendScale() {
    }

    /** Primo valore "tondo" (1, 1.5, 2, 3, 4, 5, 6, 8 x 10^k) maggiore o uguale a x. */
    public static double niceCeil(double x) {
        if (x <= 0) {
            return 1;
        }
        double[] m = {1, 1.5, 2, 3, 4, 5, 6, 8, 10};
        double p = Math.pow(10, Math.floor(Math.log10(x)));
        for (double f : m) {
            if (f * p >= x - 1e-9) {
                return f * p;
            }
        }
        return 10 * p;
    }

    /** Valore al quantile q (0..1): serve a ignorare i picchi isolati nella scala automatica. */
    public static double percentile(double[] values, double q) {
        if (values.length == 0) {
            return 0;
        }
        double[] v = values.clone();
        Arrays.sort(v);
        int i = (int) Math.ceil(q * v.length) - 1;
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }

    /** Massimo della scala automatica: 99,5 percentile + 10%, arrotondato a un valore "tondo". */
    public static double autoMax(double[] values) {
        return niceCeil(Math.max(1, percentile(values, 0.995) * 1.1));
    }
}
