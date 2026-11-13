package Banbury1;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Registra in background (anche a finestra del trend chiusa) un campione al secondo delle serie del trend,
 * in memoria e su file, per tenere gli ultimi 2 giorni. SOLA LETTURA dal PLC.
 */
public final class B1TrendRecorder {

    private final Thread thread;
    private volatile boolean stop;

    private B1TrendRecorder(B1Source src, B1TrendStore store, List<B1TrendChart.Series> series) {
        thread = new Thread(() -> {
            long lastFlush = System.currentTimeMillis();
            while (!stop) {
                long now = System.currentTimeMillis();
                if (src.connected()) {
                    double[] v = new double[series.size()];
                    for (int i = 0; i < v.length; i++) {
                        Double d = src.num(series.get(i).liveKey);
                        v[i] = d == null ? Double.NaN : d;
                    }
                    store.add(now, v);
                    String recipe = src.str("recipe_name");
                    if (recipe != null && !recipe.isEmpty()) {
                        store.addEvent("recipe", now, recipe);
                    }
                    Double phase = src.num("phase_no");
                    if (phase != null) {
                        store.addEvent("step", now, String.valueOf(phase.intValue()));
                    }
                    Boolean auto = src.bool("mode_auto");
                    if (auto != null) {
                        store.addEvent("mode", now, auto ? "Auto" : "Manu");
                    }
                }
                if (now - lastFlush >= 10_000) {
                    store.flush();
                    lastFlush = now;
                }
                try {
                    Thread.sleep(Math.max(50, 1000 - (System.currentTimeMillis() - now)));
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "b1-trend-recorder");
        thread.setDaemon(true);
    }

    /** Avvia la registrazione. file = null: solo memoria (es. demo). */
    public static B1TrendRecorder start(B1Source src, B1TrendStore store, Path file) {
        return start(src, store, file, null);
    }

    /**
     * Come sopra; con sqlConfig riempie prima i buchi dello storico dal database SQL (solo SELECT), poi parte
     * la registrazione dal PLC. Se il database non risponde si prosegue con i soli dati registrati.
     */
    public static B1TrendRecorder start(B1Source src, B1TrendStore store, Path file, Path sqlConfig) {
        List<B1TrendChart.Series> series = B1TrendChart.defaultSeries();
        if (file != null) {
            try {
                int n = store.attachFile(file, System.currentTimeMillis());
                System.out.println("Trend: " + n + " campioni caricati da " + file);
            } catch (IOException e) {
                System.err.println("Trend: file non utilizzabile (" + e.getMessage() + "): solo memoria");
            }
        }
        B1TrendRecorder r = new B1TrendRecorder(src, store, series);
        if (sqlConfig == null) {
            r.thread.start();
        } else {
            Thread t = new Thread(() -> {
                try {
                    B1TrendSqlHistory.Data d = B1TrendSqlHistory.load(B1TrendSqlHistory.readConfig(sqlConfig));
                    if (d == null) {
                        System.out.println("Trend: storico SQL non configurato (jdbc.url/jdbc.user): solo dati registrati");
                    } else {
                        store.mergeBackfill(d.times, d.values, d.events, System.currentTimeMillis());
                        System.out.println("Trend: " + d.times.length + " righe di storico lette da SQL");
                    }
                } catch (Exception e) {
                    System.err.println("Trend: storico SQL non disponibile (" + e + "): solo dati registrati");
                }
                r.thread.start();
            }, "b1-trend-sql");
            t.setDaemon(true);
            t.start();
        }
        Runtime.getRuntime().addShutdownHook(new Thread(store::close));
        return r;
    }

    public void stop() {
        stop = true;
        thread.interrupt();
    }

    /** Identificatori delle serie, nell'ordine usato dall'archivio. */
    public static List<String> seriesIds() {
        List<String> ids = new ArrayList<>();
        for (B1TrendChart.Series s : B1TrendChart.defaultSeries()) {
            ids.add(s.id);
        }
        return ids;
    }
}
