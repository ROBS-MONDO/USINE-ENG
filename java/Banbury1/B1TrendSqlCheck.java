package Banbury1;

import java.nio.file.Paths;
import java.util.Properties;

/**
 * Prova la lettura dello storico del trend (sola SELECT) senza avviare il sinottico:
 * java Banbury1.B1TrendSqlCheck [b1_label_sql.properties]. Stampa quante righe legge o il motivo dell'errore.
 */
public final class B1TrendSqlCheck {

    private B1TrendSqlCheck() {
    }

    public static void main(String[] args) {
        Properties cfg = B1TrendSqlHistory.readConfig(Paths.get(args.length > 0 ? args[0] : "b1_label_sql.properties"));
        System.out.println("jdbc.url   : " + (cfg.getProperty("jdbc.url", "").trim().isEmpty() ? "(vuoto)" : "impostato"));
        System.out.println("jdbc.user  : " + (cfg.getProperty("jdbc.user", "").trim().isEmpty() ? "(vuoto)" : cfg.getProperty("jdbc.user").trim()));
        System.out.println("password   : " + (System.getenv("B1_SQL_PASSWORD") != null ? "da B1_SQL_PASSWORD"
                : cfg.getProperty("jdbc.password", "").isEmpty() ? "(mancante)" : "da jdbc.password (meglio la variabile d'ambiente)"));
        try {
            B1TrendSqlHistory.Data d = B1TrendSqlHistory.load(cfg);
            if (d == null) {
                System.out.println("Storico SQL non configurato: compilare jdbc.url e jdbc.user (e trend.sql non deve essere false).");
                return;
            }
            System.out.println("Righe lette: " + d.times.length);
            if (d.times.length > 0) {
                System.out.println("Dal " + new java.util.Date(d.times[0]) + " al " + new java.util.Date(d.times[d.times.length - 1]));
            }
        } catch (Exception e) {
            System.out.println("ERRORE: " + e);
            if (String.valueOf(e).contains("No suitable driver")) {
                System.out.println("Manca il driver mssql-jdbc nel classpath di questo progetto.");
            }
        }
    }
}
