package Banbury1;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Properties;

/**
 * Sceglie l'archivio ricette: SQL Server (come B2) se b1_recipes_sql.properties ha enabled=true, altrimenti la cartella di file.
 * Se l'SQL e' abilitato ma non raggiungibile si usa la cartella e lo si dice (nome dell'archivio e console).
 * La password non sta nel file: variabile d'ambiente B1_RECIPES_SQL_PASSWORD (o jdbc.password, meno sicuro).
 */
public final class B1RecipeRepositories {

    private B1RecipeRepositories() {
    }

    public static B1RecipeRepository open(B1RecipeLayout layout, Path dir) throws IOException {
        Properties p = new Properties();
        Path[] cands = {Paths.get("C:\\_tools\\Banbury1\\b1_recipes_sql.properties"), Paths.get("b1_recipes_sql.properties")};
        Path used = null;
        for (Path f : cands) {
            if (Files.isReadable(f)) {
                used = f.toAbsolutePath();
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    p.load(r);
                } catch (IOException e) {
                    System.err.println("Ricette SQL: configurazione illeggibile (" + e.getMessage() + ")");
                }
                break;
            }
        }
        System.out.println("Ricette B1: configurazione " + (used == null ? "NON TROVATA (cercata C:\\_tools\\Banbury1\\b1_recipes_sql.properties e nella cartella corrente)" : used)
                + ", enabled=" + p.getProperty("enabled", "false").trim());
        if (!"true".equalsIgnoreCase(p.getProperty("enabled", "false").trim())) {
            return new B1FileRecipeRepository(dir, layout);
        }
        final String shortUrl = B1Jdbc.url(p).replaceAll("(?i);?(user|password)=[^;]*", "");
        final Properties cfg = p;
        B1SqlRecipeRepository sql = new B1SqlRecipeRepository(
                () -> B1Jdbc.open(cfg, "B1_RECIPES_SQL_PASSWORD"), layout, "SQL Server: " + shortUrl);
        try {
            sql.list(); // prova di connessione e di esistenza delle tabelle
            System.out.println("Ricette B1: archivio SQL Server (" + shortUrl + ")");
            return sql;
        } catch (IOException e) {
            System.err.println("Ricette B1: archivio SQL non disponibile (" + e.getMessage() + "): uso la cartella " + dir);
            final B1FileRecipeRepository files = new B1FileRecipeRepository(dir, layout);
            final String note = dir + "  (ATTENZIONE: SQL non raggiungibile)";
            return new B1RecipeRepository() {
                @Override
                public List<Entry> list() throws IOException {
                    return files.list();
                }

                @Override
                public B1Recipe load(String uid) throws IOException {
                    return files.load(uid);
                }

                @Override
                public void save(B1Recipe r, String u) throws IOException {
                    files.save(r, u);
                }

                @Override
                public void delete(String uid) throws IOException {
                    files.delete(uid);
                }

                @Override
                public String location() {
                    return note;
                }
            };
        }
    }
}
