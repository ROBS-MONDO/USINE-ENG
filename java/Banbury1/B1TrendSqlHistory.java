package Banbury1;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Storico del trend dal database SQL (come fa PLC3_Graph per B2): solo SELECT, quindi sola lettura.
 * Serve a riempire i buchi di quando il modulo B1 era chiuso: la tabella di log (Datas_B1) viene scritta
 * di continuo da un altro programma. Colonne usate: Date_record, Time_record, Temperature, Amperage, Vitesse
 * (la pressione non e' nel log: resta solo quella registrata dal PLC), Mode, Nom_melange.
 * Chiavi in b1_label_sql.properties: jdbc.url, jdbc.user, password in B1_SQL_PASSWORD (o jdbc.password),
 * trend.table (predefinita dbo.Datas_B1), trend.sql = false per disattivare.
 */
public final class B1TrendSqlHistory {

    public static final class Data {
        public final long[] times;
        public final double[][] values;
        public final List<String[]> events;

        Data(long[] times, double[][] values, List<String[]> events) {
            this.times = times;
            this.values = values;
            this.events = events;
        }
    }

    private B1TrendSqlHistory() {
    }

    /** Nome tabella ammesso: lettere, cifre, _ . e parentesi quadre (niente SQL libero nel file di configurazione). */
    static boolean validTable(String t) {
        return t != null && t.matches("[A-Za-z0-9_.\\[\\]]{1,128}");
    }

    /** Testo del modo come Auto/Manu a partire dal valore della colonna Mode. */
    static String modeText(String raw) {
        String m = raw == null ? "" : raw.trim().toLowerCase();
        return m.startsWith("a") ? "Auto" : "Manu";
    }

    static String buildSql(String table, List<B1TrendChart.Series> series) {
        StringBuilder sb = new StringBuilder("SELECT DATEADD(SECOND, DATEDIFF(SECOND, 0, Time_record), CAST(Date_record AS DATETIME)) AS TS");
        for (B1TrendChart.Series s : series) {
            if (s.sqlColumn != null) {
                sb.append(", ").append(s.sqlColumn);
            }
        }
        sb.append(", Mode, Nom_melange FROM ").append(table)
                .append(" WHERE Date_record >= DATEADD(DAY, -2, GETDATE()) ORDER BY TS");
        return sb.toString();
    }

    /** Legge lo storico; null se il database non e' configurato o disattivato. */
    public static Data load(Properties cfg) throws Exception {
        String url = cfg.getProperty("jdbc.url", "").trim();
        String user = cfg.getProperty("jdbc.user", "").trim();
        if (url.isEmpty() || user.isEmpty() || "false".equalsIgnoreCase(cfg.getProperty("trend.sql", "true").trim())) {
            return null;
        }
        String table = cfg.getProperty("trend.table", "dbo.Datas_B1").trim();
        if (!validTable(table)) {
            throw new IllegalArgumentException("trend.table non valida: " + table);
        }
        String pwd = System.getenv("B1_SQL_PASSWORD");
        if (pwd == null) {
            pwd = cfg.getProperty("jdbc.password", "");
        }
        List<B1TrendChart.Series> series = B1TrendChart.defaultSeries();
        List<long[]> times = new ArrayList<>();
        List<double[]> vals = new ArrayList<>();
        List<String[]> events = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(url, user, pwd);
             PreparedStatement ps = c.prepareStatement(buildSql(table, series));
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("TS");
                if (ts == null) {
                    continue;
                }
                long t = ts.getTime();
                double[] row = new double[series.size()];
                for (int i = 0; i < row.length; i++) {
                    row[i] = Double.NaN;
                    String col = series.get(i).sqlColumn;
                    if (col != null) {
                        double d = rs.getDouble(col);
                        if (!rs.wasNull()) {
                            row[i] = d;
                        }
                    }
                }
                times.add(new long[]{t});
                vals.add(row);
                events.add(new String[]{"mode", String.valueOf(t), modeText(rs.getString("Mode"))});
                String recipe = rs.getString("Nom_melange");
                if (recipe != null && !recipe.trim().isEmpty()) {
                    events.add(new String[]{"recipe", String.valueOf(t), recipe.trim()});
                }
            }
        }
        long[] t = new long[times.size()];
        for (int i = 0; i < t.length; i++) {
            t[i] = times.get(i)[0];
        }
        return new Data(t, vals.toArray(new double[0][]), events);
    }

    /** Carica la configurazione (stesso file dell'etichetta); proprieta' vuote se il file non c'e'. */
    public static Properties readConfig(Path file) {
        Properties p = new Properties();
        if (file != null && Files.isReadable(file)) {
            try (java.io.Reader r = Files.newBufferedReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (Exception e) {
                System.err.println("Trend SQL: configurazione illeggibile (" + e.getMessage() + ")");
            }
        }
        return p;
    }
}
