package Banbury1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Test delle funzioni del trend (java Banbury1.B1TrendSelfTest). */
public final class B1TrendSelfTest {

    private static int failures;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "OK    " : "FALLITO ") + what);
        if (!ok) {
            failures++;
        }
    }

    public static void main(String[] args) {
        // 1000 punti in 1000 s, ridotti a 10 colonne
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            pts.add(new double[]{i * 1000L, i % 100});
        }
        List<double[]> d = B1TrendChart.downsample(pts, 0, 1_000_000L, 10);
        check("downsample: al massimo 2 punti per colonna", d.size() <= 20 && !d.isEmpty());
        double max = 0;
        for (double[] p : d) {
            max = Math.max(max, p[1]);
        }
        check("downsample: conserva i picchi (99)", max == 99);
        boolean ordered = true;
        for (int i = 1; i < d.size(); i++) {
            ordered &= d.get(i)[0] >= d.get(i - 1)[0];
        }
        check("downsample: ordine temporale", ordered);
        check("downsample: nessun punto fuori finestra",
                B1TrendChart.downsample(pts, 2_000_000L, 3_000_000L, 10).isEmpty());
        check("downsample: lista vuota", B1TrendChart.downsample(new ArrayList<>(), 0, 10, 5).isEmpty());

        check("niceCeil: 298 -> 300, 11,55 -> 15, 0,9 -> 1, 284 -> 300",
                B1TrendChart.niceCeil(298) == 300 && B1TrendChart.niceCeil(11.55) == 15
                        && B1TrendChart.niceCeil(0.9) == 1 && B1TrendChart.niceCeil(284) == 300);
        check("niceCeil: valori limite", B1TrendChart.niceCeil(0) == 1 && B1TrendChart.niceCeil(500) == 500
                && B1TrendChart.niceCeil(501) == 600);
        check("griglia 20 min = 2 min", B1TrendChart.niceTimeStep(20 * 60_000L) == 120_000L);
        check("griglia 2 giorni = 6 ore o 12 ore", B1TrendChart.niceTimeStep(2 * 86_400_000L) >= 21_600_000L);
        check("griglia 30 s = 5 s", B1TrendChart.niceTimeStep(30_000L) == 5000L);

        check("percentile ignora il picco isolato",
                B1TrendChart.percentile(new double[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 1000}, 0.9) == 9
                        && B1TrendChart.percentile(new double[]{}, 0.5) == 0);
        check("passi come in B2 (finestra 5H): piccolo 30M, grande 75M",
                B1TrendPanel.compact(B1TrendPanel.smallStep(java.time.Duration.ofHours(5))).equals("30M")
                        && B1TrendPanel.compact(B1TrendPanel.bigStep(java.time.Duration.ofHours(5))).equals("75M"));
        check("passi con finestra 15M: 1M e 5M",
                B1TrendPanel.compact(B1TrendPanel.smallStep(java.time.Duration.ofMinutes(15))).equals("1M")
                        && B1TrendPanel.compact(B1TrendPanel.bigStep(java.time.Duration.ofMinutes(15))).equals("5M"));
        check("formato durata: 75M, 2H, 1D",
                B1TrendPanel.compact(java.time.Duration.ofMinutes(75)).equals("75M")
                        && B1TrendPanel.compact(java.time.Duration.ofHours(2)).equals("2H")
                        && B1TrendPanel.compact(java.time.Duration.ofDays(1)).equals("1D"));
        check("delta MESURE: 00H12M30S", B1TrendPanel.hms(java.time.Duration.ofSeconds(750)).equals("00H12M30S"));
        check("zoom: da 15 minuti a 48 ore, predefinito 5H",
                B1TrendPanel.ZOOM_WINDOWS.length == 10 && B1TrendPanel.ZOOM_WINDOWS[0].toMinutes() == 15
                        && B1TrendPanel.ZOOM_WINDOWS[9].toHours() == 48
                        && B1TrendPanel.ZOOM_WINDOWS[B1TrendPanel.DEFAULT_ZOOM_INDEX].toHours() == 5);
        System.exit(failures == 0 ? 0 : 1);
    }
}
