package Banbury1;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Registro degli invii di ricette al PLC (tracciabilita', come RecipePLC in B2): per ogni invio, riuscito o no, chi, quando, da quale postazione,
 * quale ricetta e quale revisione dell'archivio, quante parole scritte e dove e' la copia di sicurezza. Si scrive sempre su un file CSV locale e,
 * se l'archivio ricette e' su SQL Server, anche nella tabella dbo.B1RecipePLC. Un errore nel registro non blocca ne' annulla l'invio.
 */
public final class B1RecipeSendLog {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private B1RecipeSendLog() {
    }

    public static final class Entry {
        public LocalDateTime time = LocalDateTime.now().withNano(0);
        public String recipeUid = "";
        public String name = "";
        public int recipeNo;
        public long revision;
        public String user = "";
        public String host = "";
        public boolean ok;
        public int words;
        public String backup = "";
        public String message = "";
    }

    static String computerName() {
        String h = System.getenv("COMPUTERNAME");
        if (h == null || h.isEmpty()) {
            try {
                h = java.net.InetAddress.getLocalHost().getHostName();
            } catch (java.net.UnknownHostException e) {
                h = "";
            }
        }
        return h;
    }

    private static String csv(String s) {
        String t = s == null ? "" : s.replace('\r', ' ').replace('\n', ' ').replace(';', ',');
        return t.length() > 400 ? t.substring(0, 400) : t;
    }

    /** Aggiunge una riga al CSV (crea il file con l'intestazione se manca). */
    static void writeCsv(Path file, Entry e) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        boolean isNew = !Files.exists(file);
        StringBuilder sb = new StringBuilder();
        if (isNew) {
            sb.append("DataOra;Esito;Ricetta;RecipeUID;Revisione;NumRicetta;Utente;Postazione;ParoleScritte;Backup;Messaggio\n");
        }
        sb.append(e.time.format(TS)).append(';').append(e.ok ? "OK" : "ERRORE").append(';').append(csv(e.name)).append(';')
                .append(csv(e.recipeUid)).append(';').append(e.revision).append(';').append(e.recipeNo).append(';')
                .append(csv(e.user)).append(';').append(csv(e.host)).append(';').append(e.words).append(';')
                .append(csv(e.backup)).append(';').append(csv(e.message)).append('\n');
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** Inserisce una riga in dbo.B1RecipePLC. */
    static void insertSql(Connection c, Entry e) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO dbo.B1RecipePLC (DataOra, RecipeUID, Nome, NumRicetta, Revisione, Utente, Postazione, Esito, ParoleScritte, FileBackup, Messaggio) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setTimestamp(1, Timestamp.valueOf(e.time));
            ps.setString(2, e.recipeUid);
            ps.setString(3, e.name);
            ps.setInt(4, e.recipeNo);
            ps.setLong(5, e.revision);
            ps.setString(6, e.user);
            ps.setString(7, e.host);
            ps.setString(8, e.ok ? "OK" : "ERRORE");
            ps.setInt(9, e.words);
            ps.setString(10, e.backup);
            ps.setString(11, csv(e.message));
            ps.executeUpdate();
        }
    }

    /** Registra l'invio (CSV e, se configurato, SQL) in un thread a parte; ritorna subito. */
    public static void record(Entry e) {
        if (e.host == null || e.host.isEmpty()) {
            e.host = computerName();
        }
        Thread t = new Thread(() -> {
            Path dir = Files.isDirectory(Paths.get("C:\\_tools\\Banbury1")) ? Paths.get("C:\\_tools\\Banbury1") : Paths.get(".");
            try {
                writeCsv(dir.resolve("b1_invii_ricette.csv"), e);
            } catch (IOException ex) {
                System.err.println("Registro invii (CSV): " + ex.getMessage());
            }
            Properties p = sqlConfig();
            if (p != null) {
                try (Connection c = B1Jdbc.open(p, "B1_RECIPES_SQL_PASSWORD")) {
                    insertSql(c, e);
                } catch (SQLException ex) {
                    System.err.println("Registro invii (SQL dbo.B1RecipePLC): " + ex.getMessage());
                }
            }
        }, "b1-send-log");
        t.setDaemon(false);
        t.start();
    }

    /** Configurazione dell'archivio SQL (b1_recipes_sql.properties) se abilitato, altrimenti null. */
    private static Properties sqlConfig() {
        for (Path f : new Path[]{Paths.get("C:\\_tools\\Banbury1\\b1_recipes_sql.properties"), Paths.get("b1_recipes_sql.properties")}) {
            if (Files.isReadable(f)) {
                Properties p = new Properties();
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    p.load(r);
                } catch (IOException ex) {
                    return null;
                }
                return "true".equalsIgnoreCase(p.getProperty("enabled", "false").trim()) ? p : null;
            }
        }
        return null;
    }
}
