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

        check("griglia 20 min = 2 min", B1TrendChart.niceTimeStep(20 * 60_000L) == 120_000L);
        check("griglia 2 giorni = 6 ore o 12 ore", B1TrendChart.niceTimeStep(2 * 86_400_000L) >= 21_600_000L);
        check("griglia 30 s = 5 s", B1TrendChart.niceTimeStep(30_000L) == 5000L);

        String sql = B1TrendWindow.historySql("dbo.Datas_B1", Arrays.asList("Temperature", "Amperage", "Vitesse"));
        check("query storico: solo SELECT con parametro data",
                sql.startsWith("SELECT ") && sql.contains("FROM dbo.Datas_B1")
                        && sql.contains("WHERE Date_record >= ?") && sql.contains(", Temperature, Amperage, Vitesse")
                        && !sql.toUpperCase().contains("DELETE") && !sql.toUpperCase().contains("UPDATE"));
        check("Mode Auto/Manu", B1TrendWindow.isAuto("Auto") && B1TrendWindow.isAuto(" AUTO ") && !B1TrendWindow.isAuto("Manu")
                && !B1TrendWindow.isAuto(null));
        check("parse numeri con virgola/punto/spazi",
                B1TrendWindow.parse("93,5") == 93.5 && B1TrendWindow.parse(" 18 ") == 18.0 && B1TrendWindow.parse("x") == null
                        && B1TrendWindow.parse(null) == null);
        System.exit(failures == 0 ? 0 : 1);
    }
}
