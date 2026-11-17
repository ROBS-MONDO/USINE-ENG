package Banbury1;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Scrive i dati del ticket (lotto, suffisso, num melange, passaggio, type, couleur, ricetta...) nella
 * tabella SQL di B1, in base a un file di configurazione (b1_label_sql.properties): tabella, colonne,
 * INSERT oppure UPDATE. Credenziali SOLO nel file di configurazione o in variabili d'ambiente, mai nel codice.
 *
 * Finche' "enabled" non e' true o "table" e' vuota resta in PROVA: mostra l'istruzione SQL ma non scrive.
 */
public final class B1SqlLabelStore implements B1LabelStore {

    /** Campi del ticket scrivibili: chiave del file (col.<chiave>) -> descrizione. */
    public static final String[][] FIELDS = {
        {"num_lot", "Lot complet AA/LOT (ex. 26/0703)"},
        {"annee", "Ann\u00e9e (ex. 26)"},
        {"lot", "Num\u00e9ro de lot (ex. 0703)"},
        {"suffixe", "Suffixe"},
        {"serie", "Num m\u00e9lange / s\u00e9rie (A, A1...)"},
        {"nombre", "Nombre de m\u00e9langes"},
        {"passage", "Passage comme texte WinCC (1er, 2eme...)"},
        {"couche", "Type (SUP, INF, STS, NET, MEL.MERE, PREMAST)"},
        {"couleur", "Couleur (---, Clair, Fonc\u00e9, Medium)"},
        {"recipe_name", "Nom recette"},
        {"recipe_uid", "RecipeUID"},
        {"date", "Date (colonne date)"},
        {"time", "Heure (colonne heure)"},
        {"timestamp", "Date et heure (colonne datetime)"},
        {"user", "Utilisateur Windows"},
    };

    private static final Pattern IDENT = Pattern.compile("[A-Za-z0-9_\\.\\[\\]]+");

    private final Properties cfg;
    private final boolean enabled;
    private final String table;
    private final boolean update;
    private final String keyColumn;
    private final String keyField;
    private final Map<String, String> columns = new LinkedHashMap<>();

    private B1SqlLabelStore(Properties cfg) {
        this.cfg = cfg;
        this.table = cfg.getProperty("table", "").trim();
        this.enabled = Boolean.parseBoolean(cfg.getProperty("enabled", "false").trim()) && !table.isEmpty();
        this.update = "update".equalsIgnoreCase(cfg.getProperty("mode", "insert").trim());
        this.keyColumn = cfg.getProperty("key.column", "").trim();
        this.keyField = cfg.getProperty("key.field", "").trim();
        for (String[] f : FIELDS) {
            String c = cfg.getProperty("col." + f[0], "").trim();
            if (!c.isEmpty()) {
                columns.put(f[0], c);
            }
        }
        if (!table.isEmpty() && !IDENT.matcher(table).matches()) {
            throw new IllegalArgumentException("Nom de table non valide : " + table);
        }
        for (String c : columns.values()) {
            if (!IDENT.matcher(c).matches()) {
                throw new IllegalArgumentException("Nom de colonne non valide : " + c);
            }
        }
        if (update && (keyColumn.isEmpty() || keyField.isEmpty())) {
            // UPDATE senza chiave aggiornerebbe tutte le righe: non lo permettiamo
            if (enabled) {
                throw new IllegalArgumentException("mode=update richiede key.column e key.field");
            }
        }
    }

    public static B1SqlLabelStore load(Path file) {
        Properties p = new Properties();
        if (file != null && Files.isReadable(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException e) {
                System.err.println("b1_label_sql: " + e.getMessage());
            }
        }
        return new B1SqlLabelStore(p);
    }

    public static B1SqlLabelStore fromProperties(Properties p) {
        return new B1SqlLabelStore(p);
    }

    @Override
    public boolean dryRun() {
        return !enabled;
    }

    /** Valore del campo per il ticket (null = non disponibile). */
    Object valueOf(String field, B1Label l, LocalDateTime now) {
        switch (field) {
            case "num_lot": return l.annee + "/" + l.lot;
            case "annee": return l.annee;
            case "lot": return l.lot;
            case "suffixe": return l.suffixe;
            case "serie": return l.serieMelange;
            case "nombre": return l.nombreMelanges;
            case "passage": return l.passageSql();
            case "couche": return l.coucheSql();
            case "couleur": return l.couleurSql();
            case "recipe_name": return l.recipeName;
            case "recipe_uid": return l.recipeUid;
            case "date": return java.sql.Date.valueOf(now.toLocalDate());
            case "time": return java.sql.Time.valueOf(now.toLocalTime().withNano(0));
            case "timestamp": return Timestamp.valueOf(now);
            case "user": return System.getProperty("user.name", "?");
            default: return null;
        }
    }

    /** SQL parametrico; i parametri (nell'ordine dei segnaposto) vengono aggiunti a outParams. */
    String buildSql(B1Label l, LocalDateTime now, List<Object> outParams) {
        if (columns.isEmpty()) {
            throw new IllegalStateException("Aucune colonne configur\u00e9e (col.*)");
        }
        StringBuilder sql = new StringBuilder();
        if (update) {
            sql.append("UPDATE ").append(table).append(" SET ");
            boolean first = true;
            for (Map.Entry<String, String> e : columns.entrySet()) {
                sql.append(first ? "" : ", ").append(e.getValue()).append(" = ?");
                outParams.add(valueOf(e.getKey(), l, now));
                first = false;
            }
            sql.append(" WHERE ").append(keyColumn).append(" = ?");
            outParams.add(valueOf(keyField, l, now));
        } else {
            sql.append("INSERT INTO ").append(table).append(" (");
            StringBuilder q = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, String> e : columns.entrySet()) {
                sql.append(first ? "" : ", ").append(e.getValue());
                q.append(first ? "?" : ", ?");
                outParams.add(valueOf(e.getKey(), l, now));
                first = false;
            }
            sql.append(") VALUES (").append(q).append(")");
        }
        return sql.toString();
    }

    @Override
    public String describe(B1Label l) {
        if (table.isEmpty() || columns.isEmpty()) {
            return "(table/colonnes pas encore configur\u00e9es dans b1_label_sql.properties)";
        }
        List<Object> p = new ArrayList<>();
        String sql = buildSql(l, LocalDateTime.now(), p);
        StringBuilder sb = new StringBuilder(sql).append("\n  parametri: ");
        for (int i = 0; i < p.size(); i++) {
            sb.append(i == 0 ? "" : " | ").append(i + 1).append("=").append(p.get(i));
        }
        return sb.toString();
    }

    @Override
    public void store(B1Label l) throws Exception {
        if (!enabled) {
            throw new IllegalStateException("\u00c9criture SQL non activ\u00e9e (enabled=false) : mode test");
        }
        List<Object> p = new ArrayList<>();
        String sql = buildSql(l, LocalDateTime.now(), p);
        if (B1Jdbc.url(cfg).isEmpty()) {
            throw new IllegalStateException("jdbc.url mancante");
        }
        try (Connection c = B1Jdbc.open(cfg, "B1_SQL_PASSWORD");
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < p.size(); i++) {
                ps.setObject(i + 1, p.get(i));
            }
            int n = ps.executeUpdate();
            if (update && n != 1) {
                throw new IllegalStateException("UPDATE a modifi\u00e9 " + n + " righe (atteso 1)");
            }
        }
    }

    public static String template() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Scrittura dei dati del ticket nel database SQL di Banbury 1.\n");
        sb.append("# Finche' enabled=false (o table vuota) il programma mostra l'SQL ma NON scrive.\n");
        sb.append("# Password: meglio nella variabile d'ambiente B1_SQL_PASSWORD (non nel file).\n");
        sb.append("enabled = false\n");
        sb.append("jdbc.url = jdbc:sqlserver://srv-bambury:1433;databaseName=DATA_BAMBURY;encrypt=true;trustServerCertificate=true;loginTimeout=5\n");
        sb.append("# Consigliato: auth = windows (autenticazione Windows, nessuna password, permessi al gruppo di dominio sul server)\n");
        sb.append("# auth = windows\n");
        sb.append("jdbc.user =\n");
        sb.append("jdbc.password =\n");
        sb.append("# Tabella di B1 (da confermare: es. dbo.Datas_B1) e modalita' insert|update\n");
        sb.append("table =\n");
        sb.append("mode = insert\n");
        sb.append("# per mode=update: colonna e campo che identificano la riga da aggiornare\n");
        sb.append("key.column =\n");
        sb.append("key.field =\n");
        sb.append("# Colonne: lasciare vuoto cio' che non va scritto. Esempio modellato su Datas_B2:\n");
        sb.append("#   col.num_lot = Num_lot        col.suffixe = Suffixe       col.serie = Num_melange\n");
        sb.append("#   col.couche = Type_melange    col.passage = Num_passage   col.couleur = Couleur\n");
        sb.append("#   col.recipe_name = Nom_melange   col.nombre = Nbr_sequence\n");
        for (String[] f : FIELDS) {
            sb.append("# ").append(f[1]).append("\ncol.").append(f[0]).append(" =\n");
        }
        return sb.toString();
    }
}
