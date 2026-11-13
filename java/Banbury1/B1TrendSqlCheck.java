package Banbury1;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Prova la lettura dello storico del trend (sola SELECT) senza avviare il sinottico:
 * java Banbury1.B1TrendSqlCheck [file]. Senza argomento usa C:\\_tools\\Banbury1\\b1_label_sql.properties (come il sinottico), altrimenti la cartella corrente. Stampa quante righe legge o il motivo dell'errore.
 */
public final class B1TrendSqlCheck {

    private B1TrendSqlCheck() {
    }

    public static void main(String[] args) {
        Path file = args.length > 0 ? Paths.get(args[0]) : Paths.get("C:\\_tools\\Banbury1\\b1_label_sql.properties");
        if (args.length == 0 && !Files.isReadable(file)) {
            file = Paths.get("b1_label_sql.properties");
        }
        System.out.println("file       : " + file.toAbsolutePath() + (Files.isReadable(file) ? "" : "  (NON TROVATO)"));
        Properties cfg = B1TrendSqlHistory.readConfig(file);
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
