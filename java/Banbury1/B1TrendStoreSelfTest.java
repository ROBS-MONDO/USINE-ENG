package Banbury1;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/** Test dell'archivio dei trend (java Banbury1.B1TrendStoreSelfTest). */
public final class B1TrendStoreSelfTest {

    private static int failures;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "OK    " : "FALLITO ") + what);
        if (!ok) {
            failures++;
        }
    }

    public static void main(String[] args) throws Exception {
        List<String> ids = Arrays.asList("a", "b");
        long now = 1_000_000_000_000L;

        B1TrendStore s = new B1TrendStore(ids);
        for (int i = 0; i < 100; i++) {
            s.add(now - 100_000L + i * 1000L, new double[]{i, 100 - i});
        }
        B1TrendStore.Buffer a = s.buffer("a");
        check("100 campioni in memoria", a.size() == 100);
        check("valore piu' vicino", a.nearest(now - 100_000L + 10_400L, 2000) == 10.0
                && a.nearest(now - 100_000L + 10_600L, 2000) == 11.0);
        check("valore troppo lontano = null", a.nearest(now + 500_000L, 2000) == null);

        List<double[]> d = a.downsample(now - 100_000L, now, 10);
        double max = 0;
        for (double[] p : d) {
            max = Math.max(max, p[1]);
        }
        check("downsample: al massimo 2 punti per colonna, picco conservato", d.size() <= 20 && max == 99);
        check("downsample fuori finestra = vuoto", a.downsample(now + 10_000L, now + 20_000L, 10).isEmpty());

        // NaN = serie non disponibile, l'altra prosegue
        s.add(now + 1000L, new double[]{Double.NaN, 5});
        check("campione mancante non entra nel buffer", a.size() == 100 && s.buffer("b").size() == 101);

        // scarto dei dati piu' vecchi di 2 giorni
        B1TrendStore old = new B1TrendStore(ids);
        old.add(now, new double[]{1, 1});
        old.add(now + B1TrendStore.KEEP_MS + 5000L, new double[]{2, 2});
        check("oltre 2 giorni: il campione vecchio viene scartato", old.buffer("a").size() == 1
                && old.buffer("a").firstTime() == now + B1TrendStore.KEEP_MS + 5000L);

        // campioni fuori ordine ignorati
        B1TrendStore ooo = new B1TrendStore(ids);
        ooo.add(now, new double[]{1, 1});
        ooo.add(now - 5000L, new double[]{9, 9});
        check("campione fuori ordine ignorato", ooo.buffer("a").size() == 1);

        // salvataggio e ricarica da file
        Path f = Files.createTempFile("b1trend", ".csv");
        Files.write(f, (String.valueOf(now - 3L * 86_400_000L) + ";1;2\n"      // 3 giorni fa: scartato
                + (now - 1000L) + ";7;8\n"
                + (now - 500L) + ";9;\n"                                         // seconda serie assente
                + "riga rovinata\n").getBytes(StandardCharsets.UTF_8));
        B1TrendStore st = new B1TrendStore(ids);
        int loaded = st.attachFile(f, now);
        check("ricarica: solo gli ultimi 2 giorni e solo righe valide", loaded == 2);
        check("ricarica: valori nelle serie", st.buffer("a").size() == 2 && st.buffer("b").size() == 1
                && st.buffer("a").nearest(now - 1000L, 100) == 7.0);
        st.add(now, new double[]{11, 12});
        st.close();
        B1TrendStore again = new B1TrendStore(ids);
        int n2 = again.attachFile(f, now + 1000L);
        check("quello che e' stato registrato resta al riavvio", n2 == 3 && again.buffer("a").size() == 3
                && again.buffer("a").nearest(now, 100) == 11.0);
        again.close();
        long lines = Files.readAllLines(f).stream().filter(x -> !x.trim().isEmpty()).count();
        check("il file non contiene piu' le righe vecchie o rovinate", lines == 3);
        Files.delete(f);

        // memoria: due giorni a un campione al secondo entrano nel buffer
        B1TrendStore big = new B1TrendStore(Arrays.asList("x"));
        for (long i = 0; i < 172_800L; i++) {
            big.add(now + i * 1000L, new double[]{i % 7});
        }
        check("2 giorni a 1 campione/s: 172.800 campioni", big.buffer("x").size() == 172_800);
        List<double[]> dd = big.buffer("x").downsample(now, now + 172_800_000L, 1000);
        check("downsample di 2 giorni in <= 2000 punti", !dd.isEmpty() && dd.size() <= 2000);
        // eventi (ricetta / modo): solo i cambiamenti, salvati e ricaricati
        B1TrendStore ev = new B1TrendStore(ids);
        ev.addEvent("mode", now, "Auto");
        ev.addEvent("mode", now + 1000L, "Auto");      // uguale: ignorato
        ev.addEvent("mode", now + 2000L, "Manu");
        ev.addEvent("recipe", now, "gomma;2");         // il ';' viene sostituito
        check("eventi: solo i cambiamenti", ev.events("mode").size() == 2 && ev.events("recipe").size() == 1
                && ev.events("recipe").get(0).text.equals("gomma,2"));
        Path fe = Files.createTempFile("b1trendev", ".csv");
        B1TrendStore e1 = new B1TrendStore(ids);
        e1.attachFile(fe, now);
        e1.addEvent("mode", now, "Auto");
        e1.addEvent("mode", now + 5000L, "Manu");
        e1.addEvent("recipe", now, "gomma2");
        e1.add(now, new double[]{3, 4});
        e1.close();
        B1TrendStore e2 = new B1TrendStore(ids);
        e2.attachFile(fe, now + 10_000L);
        check("eventi ricaricati dal file", e2.events("mode").size() == 2 && e2.events("mode").get(1).text.equals("Manu")
                && e2.events("recipe").size() == 1 && e2.buffer("a").size() == 1);
        e2.close();
        Files.delete(fe);

        // storico dal database: riempie solo i buchi, il registrato ha la precedenza
        B1TrendStore bf = new B1TrendStore(ids);
        long b0 = now - 3_600_000L;
        for (int i = 0; i < 10; i++) {
            bf.add(b0 + 1000L * i, new double[]{1, 1}); // registrato: b0 .. b0+9 s
        }
        long[] sqlT = new long[20];
        double[][] sqlV = new double[20][];
        for (int i = 0; i < 20; i++) {
            sqlT[i] = b0 - 20_000L + 2000L * i; // SQL: ogni 2 s da b0-20 s a b0+18 s
            sqlV[i] = new double[]{99, Double.NaN};
        }
        List<String[]> sqlEv = new ArrayList<>();
        sqlEv.add(new String[]{"mode", String.valueOf(b0 - 20_000L), "Manu"});
        sqlEv.add(new String[]{"mode", String.valueOf(b0 - 10_000L), "Manu"});
        sqlEv.add(new String[]{"mode", String.valueOf(b0 + 30_000L), "Auto"});
        bf.mergeBackfill(sqlT, sqlV, sqlEv, now);
        B1TrendStore.Buffer ba = bf.buffer("a");
        check("storico SQL: campioni nei buchi aggiunti, nessuno vicino al registrato",
                ba.firstTime() == b0 - 20_000L && ba.nearest(b0 + 5000L, 100) == 1.0 && ba.size() > 10 && ba.size() < 30);
        check("storico SQL: serie assente (NaN) non entra", bf.buffer("b").size() == 10);
        check("storico SQL: eventi consecutivi uguali uniti", bf.events("mode").size() == 2);
        bf.add(b0 + 500_000L, new double[]{5, 5});
        check("dopo il recupero la registrazione prosegue", bf.buffer("a").nearest(b0 + 500_000L, 100) == 5.0);
        check("nome tabella ammesso", B1TrendSqlHistory.validTable("dbo.Datas_B1")
                && !B1TrendSqlHistory.validTable("x; DROP TABLE y"));
        check("modo Auto/Manu da Mode", B1TrendSqlHistory.modeText("Auto").equals("Auto")
                && B1TrendSqlHistory.modeText("Manu").equals("Manu") && B1TrendSqlHistory.modeText(null).equals("Manu"));
        check("SELECT di sola lettura con le colonne del log", B1TrendSqlHistory.buildSql("dbo.Datas_B1", B1TrendChart.defaultSeries())
                .startsWith("SELECT ") && B1TrendSqlHistory.buildSql("dbo.Datas_B1", B1TrendChart.defaultSeries()).contains("Amperage")
                && !B1TrendSqlHistory.buildSql("dbo.Datas_B1", B1TrendChart.defaultSeries()).toUpperCase().contains("DELETE"));
        System.exit(failures == 0 ? 0 : 1);
    }
}
