package Banbury1;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Sceglie l'archivio produzioni: SQL Server (stessa configurazione b1_recipes_sql.properties delle ricette) se abilitato e raggiungibile,
 * altrimenti il file b1_productions.csv.
 */
public final class B1ProductionRepositories {

    public static final Path DEFAULT_FILE = Paths.get("C:\\_tools\\Banbury1\\b1_productions.csv");

    private B1ProductionRepositories() {
    }

    public static B1ProductionRepository open(Path file) {
        Properties p = new Properties();
        Path[] cands = {Paths.get("C:\\_tools\\Banbury1\\b1_recipes_sql.properties"), Paths.get("b1_recipes_sql.properties")};
        for (Path f : cands) {
            if (Files.isReadable(f)) {
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    p.load(r);
                } catch (IOException e) {
                    System.err.println("Productions SQL : configuration illisible (" + e.getMessage() + ")");
                }
                break;
            }
        }
        if (!"true".equalsIgnoreCase(p.getProperty("enabled", "false").trim())) {
            return new B1FileProductionRepository(file, "");
        }
        final Properties cfg = p;
        final String shortUrl = B1Jdbc.svc(cfg) ? "SVC.DBBamburyReader" : B1Jdbc.url(cfg).replaceAll("(?i);?(user|password)=[^;]*", "");
        B1SqlProductionRepository sql = new B1SqlProductionRepository(() -> B1Jdbc.open(cfg, "B1_RECIPES_SQL_PASSWORD"), "SQL Server : " + shortUrl);
        try {
            sql.list(); // prova di connessione e di esistenza della tabella
            return sql;
        } catch (IOException e) {
            System.err.println("Productions B1 : archive SQL indisponible (" + e.getMessage() + ") : utilisation du fichier " + file);
            return new B1FileProductionRepository(file, "(ATTENTION : SQL injoignable ou table B1Productions absente)");
        }
    }
}
