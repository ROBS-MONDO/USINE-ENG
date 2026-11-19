package CRY;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sostituisce i due Crystal Report:
 * <ul>
 *   <li>VENTES TOTAL Caou PAR NATION/CLIENT -Statpav2- (Author Destefanis)</li>
 *   <li>VENTES TOTAL Ballons PAR NATION/CLIENT (Author Destefanis)</li>
 * </ul>
 * Fatturato raggruppato per NAZIONE e poi per CLIENTE, con totale generale,
 * affidamento assicurativo (CF_FIDO) e relativo codice (CF_FIDOCA).
 *
 * <p>Avvio con interfaccia (come "Refresh" del Crystal: date + password):
 * <pre>  java -cp out:ojdbc8.jar CRY.VentesNationClient</pre>
 * Avvio da riga di comando (esporta CSV, utile per schedulare):
 * <pre>  java -cp out:ojdbc8.jar CRY.VentesNationClient --report caou \
 *       --from 01-01-2018 --to 30-11-2018 --out caou.csv</pre>
 *
 * <p>Connessione (priorità: argomenti &gt; variabili d'ambiente &gt; ventes.properties):
 * <pre>
 *   --url / VENTES_DB_URL        es. jdbc:oracle:thin:@//SERVER:1521/E   (service name "E" come nel DSN "poker")
 *   --user / VENTES_DB_USER      default "poker"
 *   --password / VENTES_DB_PASSWORD   se assente viene chiesta (come nel Crystal)
 * </pre>
 * Il driver JDBC Oracle (ojdbc) deve essere nel classpath.
 */
public final class VentesNationClient {

    // ------------------------------------------------------------------ modello

    enum ReportType {
        CAOU("Caou", "VENTES TOTAL Caou PAR NATION/CLIENT -Statpav2- (Author Destefanis)",
                "VENTES TOTAL Caou PAR NATION/CLIENT -Statpav2- (Author Destefanis)"),
        BALLONS("Ballons", "VENTES TOTAL BALLONS PAR NATION/CLIENT - EURO",
                "VENTES TOTAL Ballons PAR NATION/CLIENT (Author Destefanis)");

        final String label;
        final String title;
        final String footer;

        ReportType(String label, String title, String footer) {
            this.label = label;
            this.title = title;
            this.footer = footer;
        }

        static ReportType parse(String s) {
            String v = s.trim().toLowerCase(Locale.ROOT);
            if (v.startsWith("caou")) return CAOU;
            if (v.startsWith("ball")) return BALLONS;
            throw new IllegalArgumentException("Report sconosciuto: " + s + " (usa caou | ballons)");
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Una riga cliente come restituita dalla query (già aggregata per cliente). */
    static final class ClientLine {
        String nat, natDesc, cli, nome, fidoCa;
        String nfat, dfat, tipo; // solo con il dettaglio fatture
        BigDecimal pezzi = BigDecimal.ZERO, valore = BigDecimal.ZERO,
                addeb = BigDecimal.ZERO, accre = BigDecimal.ZERO, fido = BigDecimal.ZERO;
    }

    /** Riga del report finale: N = nazione (subtotale), C = cliente, D = fattura (dettaglio), T = totale generale. */
    static final class OutLine {
        final char kind;
        final String code, desc;
        final BigDecimal pezzi, valore, addeb, accre, fido;
        final String fidoCa;

        OutLine(char kind, String code, String desc, BigDecimal pezzi, BigDecimal valore,
                BigDecimal addeb, BigDecimal accre, BigDecimal fido, String fidoCa) {
            this.kind = kind;
            this.code = code;
            this.desc = desc;
            this.pezzi = pezzi;
            this.valore = valore;
            this.addeb = addeb;
            this.accre = accre;
            this.fido = fido;
            this.fidoCa = fidoCa;
        }
    }

    static final class Result {
        final ReportType type;
        final LocalDate from, to;
        final List<OutLine> lines;

        Result(ReportType type, LocalDate from, LocalDate to, List<OutLine> lines) {
            this.type = type;
            this.from = from;
            this.to = to;
            this.lines = lines;
        }

        /** Intestazioni di colonna (dipendono dal report). */
        List<String> headers() {
            List<String> h = new ArrayList<>(Arrays.asList("Codice", "Descrizione"));
            if (type == ReportType.BALLONS) h.add("Pezzi");
            h.add(type == ReportType.BALLONS ? "Valore EUR" : "Montant");
            if (type == ReportType.BALLONS) {
                h.add("Note debito");
                h.add("Note credito");
            }
            h.add("Ass. Credit");
            h.add("Code Ass. Cre.");
            return h;
        }

        /** Righe come testo; {@code grouping}=true usa il separatore delle migliaia (stampa/GUI). */
        List<String[]> rows(boolean grouping) {
            List<String[]> out = new ArrayList<>();
            for (OutLine l : lines) {
                List<String> r = new ArrayList<>();
                r.add(l.code);
                r.add(l.desc);
                if (type == ReportType.BALLONS) r.add(fmt(l.pezzi, grouping));
                r.add(fmt(l.valore, grouping));
                if (type == ReportType.BALLONS) {
                    r.add(fmt(l.addeb, grouping));
                    r.add(fmt(l.accre, grouping));
                }
                r.add(l.kind == 'C' ? fmt(l.fido, grouping) : "");
                r.add(l.kind == 'C' && l.fidoCa != null ? l.fidoCa : "");
                out.add(r.toArray(new String[0]));
            }
            return out;
        }
    }

    // ------------------------------------------------------------------ query

    private static final String BALLONS_INVOICE_TYPES = "'F002','G002','F003'";            // AQQ
    private static final String BALLONS_DEBIT_TYPES = "'D002','D005','D006'";              // ADD
    private static final String BALLONS_CREDIT_TYPES = "'A002','GREE','GRRE','GA01','GA02','GA03'"; // ACC

    /**
     * STAT_GIO2 (Ballons). Replica le formule del Crystal:
     * <ul>
     *   <li>selezione: TB_DFAT tra FromDate e ToDate, RB_TIPOR in (DF,PK,SI), TB_TIPO nella lista ammessa,
     *       RB_PARTE non inizia per WSPETRA;</li>
     *   <li>{@code @SELEXTIPO}: AQQ (fatture) / ADD (note di debito) / ACC (note di credito);</li>
     *   <li>{@code @TVALORE} = RB_VALORE / TB_CAMBIO (EUR), negativo per ACC;</li>
     *   <li>{@code @TPEZZI} = RB_QTA, moltiplicato per PARTI_JOLLYN1 se PARTI_G1='SES', negativo per ACC.</li>
     * </ul>
     */
    static String ballonsSql(boolean detail) {
        String qty = "s.RB_QTA * CASE WHEN p.PARTI_G1 = 'SES' THEN p.PARTI_JOLLYN1 ELSE 1 END";
        String val = "(s.RB_VALORE / s.TB_CAMBIO)";
        String plus = "s.TB_TIPO IN (" + BALLONS_INVOICE_TYPES + "," + BALLONS_DEBIT_TYPES + ")";
        String minus = "s.TB_TIPO IN (" + BALLONS_CREDIT_TYPES + ")";
        return "SELECT NVL(a.AN_CODNAZ,'--') AS NAT, MAX(n.T009_DESC) AS NAT_DESC, "
                + "NVL(s.RB_CLFO,' ') AS CLI, MAX(a.AN_RAGSOC1) AS NOME, "
                + "MAX(c.CF_FIDO) AS FIDO, MAX(c.CF_FIDOCA) AS FIDOCA, "
                + "SUM(CASE WHEN " + plus + " THEN " + qty + " WHEN " + minus + " THEN -(" + qty + ") END) AS PEZZI, "
                + "SUM(CASE WHEN " + plus + " THEN " + val + " WHEN " + minus + " THEN -" + val + " END) AS VALORE, "
                + "SUM(CASE WHEN s.TB_TIPO IN (" + BALLONS_DEBIT_TYPES + ") THEN " + val + " END) AS ADDEB, "
                + "SUM(CASE WHEN " + minus + " THEN " + val + " END) AS ACCRE "
                + (detail ? ", s.TB_NFAT AS NFAT, TO_CHAR(s.TB_DFAT,'DD-MM-YYYY') AS DFAT, s.TB_TIPO AS TIPO " : " ")
                + "FROM POKER.STAT_GIO2 s "
                + "LEFT JOIN POKER.PARTI p ON p.PARTI_COD = s.RB_PARTE "
                + "LEFT JOIN POKER.CLIFOR c ON c.CF_COD = s.RB_CLFO "
                + "LEFT JOIN POKER.ANAG a ON a.AN_COD = c.CF_ANAG "
                + "LEFT JOIN POKER.T_009 n ON n.T009_COD = a.AN_CODNAZ "
                + "WHERE s.TB_DFAT BETWEEN ? AND ? "
                + "AND s.RB_TIPOR IN ('DF','PK','SI') "
                + "AND s.TB_TIPO IN ('A002','D002','D005','D006','F002','F003','G002','GA01','GA02','GA03','GDCE','GREE','GRRE') "
                + "AND (s.RB_PARTE IS NULL OR s.RB_PARTE NOT LIKE 'WSPETRA%') "
                + "GROUP BY NVL(a.AN_CODNAZ,'--'), NVL(s.RB_CLFO,' ')"
                + (detail ? ", s.TB_DFAT, s.TB_NFAT, s.TB_TIPO" : "")
                + " ORDER BY 1, 3" + (detail ? ", s.TB_DFAT, s.TB_NFAT" : "");
    }

    /**
     * STAT_PAV2 (Caou, "-Statpav2-"). La vista contiene già i decrementi per NC
     * ("Creazione per analisi venduto (con decrementi per NC)"), quindi si somma RB_VALORE così com'è.
     * <ul>
     *   <li>selezione: TB_DFAT tra FromDate e ToDate (nel Crystal parametri FromDateInv/ToDateInv),
     *       RB_PARTE non inizia per WSPETRA / WIMPCORTRA,
     *       RB_STF diverso da 'I' (filtro aggiunto a mano dal docx "RB STF is not equal to I").</li>
     * </ul>
     */
    static String caouSql(boolean excludeStfI, boolean detail) {
        return "SELECT NVL(a.AN_CODNAZ,'--') AS NAT, MAX(n.T029_DESC) AS NAT_DESC, "
                + "NVL(s.RB_CLFO,' ') AS CLI, MAX(a.AN_RAGSOC1) AS NOME, "
                + "MAX(c.CF_FIDO) AS FIDO, MAX(c.CF_FIDOCA) AS FIDOCA, "
                + "SUM(s.RB_VALORE) AS VALORE "
                + (detail ? ", s.NFATT AS NFAT, TO_CHAR(s.TB_DFAT,'DD-MM-YYYY') AS DFAT, s.TB_TIPO AS TIPO " : " ")
                + "FROM POKER.STAT_PAV2 s "
                + "LEFT JOIN POKER.CLIFOR c ON c.CF_COD = s.RB_CLFO "
                + "LEFT JOIN POKER.ANAG a ON a.AN_COD = c.CF_ANAG "
                + "LEFT JOIN PUBMONDO.T_029 n ON n.T029_COD = a.AN_CODNAZ "
                + "WHERE s.TB_DFAT BETWEEN ? AND ? "
                + "AND (s.RB_PARTE IS NULL OR (s.RB_PARTE NOT LIKE 'WSPETRA%' AND s.RB_PARTE NOT LIKE 'WIMPCORTRA%')) "
                + (excludeStfI ? "AND (s.RB_STF IS NULL OR s.RB_STF <> 'I') " : "")
                + "GROUP BY NVL(a.AN_CODNAZ,'--'), NVL(s.RB_CLFO,' ')"
                + (detail ? ", s.TB_DFAT, s.NFATT, s.TB_TIPO" : "")
                + " ORDER BY 1, 3" + (detail ? ", s.TB_DFAT, s.NFATT" : "");
    }

    static Result run(Connection con, ReportType type, LocalDate from, LocalDate to, boolean excludeStfI,
                      boolean detail)
            throws SQLException {
        String sql = type == ReportType.BALLONS ? ballonsSql(detail) : caouSql(excludeStfI, detail);
        List<ClientLine> clients = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(from));
            ps.setDate(2, java.sql.Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ClientLine l = new ClientLine();
                    l.nat = trim(rs.getString("NAT"));
                    l.natDesc = trim(rs.getString("NAT_DESC"));
                    l.cli = trim(rs.getString("CLI"));
                    l.nome = trim(rs.getString("NOME"));
                    l.fidoCa = trim(rs.getString("FIDOCA"));
                    l.fido = nz(rs.getBigDecimal("FIDO"));
                    l.valore = nz(rs.getBigDecimal("VALORE"));
                    if (detail) {
                        l.nfat = trim(rs.getString("NFAT"));
                        l.dfat = trim(rs.getString("DFAT"));
                        l.tipo = trim(rs.getString("TIPO"));
                    }
                    if (type == ReportType.BALLONS) {
                        l.pezzi = nz(rs.getBigDecimal("PEZZI"));
                        l.addeb = nz(rs.getBigDecimal("ADDEB"));
                        l.accre = nz(rs.getBigDecimal("ACCRE"));
                    }
                    clients.add(l);
                }
            }
        }
        return new Result(type, from, to, build(clients, detail));
    }

    /** Raggruppa per nazione (subtotale sopra i clienti, come nel Crystal) e aggiunge il totale generale. */
    static List<OutLine> build(List<ClientLine> rows, boolean detail) {
        // nazione -> cliente -> righe (una per cliente, oppure una per fattura con il dettaglio)
        Map<String, Map<String, List<ClientLine>>> byNat = new LinkedHashMap<>();
        for (ClientLine c : rows) {
            byNat.computeIfAbsent(c.nat, k -> new LinkedHashMap<>())
                    .computeIfAbsent(c.cli, k -> new ArrayList<>()).add(c);
        }

        List<OutLine> out = new ArrayList<>();
        BigDecimal[] g = zeros();
        for (Map.Entry<String, Map<String, List<ClientLine>>> n : byNat.entrySet()) {
            BigDecimal[] nt = zeros();
            List<OutLine> natLines = new ArrayList<>();
            String natDesc = null;
            for (List<ClientLine> inv : n.getValue().values()) {
                BigDecimal[] ct = zeros();
                for (ClientLine l : inv) {
                    add(ct, l);
                    if (natDesc == null) natDesc = l.natDesc;
                }
                ClientLine f = inv.get(0);
                natLines.add(new OutLine('C', f.cli, f.nome == null ? "" : f.nome,
                        ct[0], ct[1], ct[2], ct[3], f.fido, f.fidoCa));
                if (detail) {
                    for (ClientLine l : inv) {
                        String d = (l.dfat == null ? "" : l.dfat) + (l.tipo == null ? "" : "  " + l.tipo);
                        natLines.add(new OutLine('D', "   " + (l.nfat == null ? "" : l.nfat), d,
                                l.pezzi, l.valore, l.addeb, l.accre, null, null));
                    }
                }
                for (int i = 0; i < 4; i++) nt[i] = nt[i].add(ct[i]);
            }
            out.add(new OutLine('N', n.getKey(), natDesc == null ? "" : natDesc, nt[0], nt[1], nt[2], nt[3], null, null));
            out.addAll(natLines);
            for (int i = 0; i < 4; i++) g[i] = g[i].add(nt[i]);
        }
        out.add(new OutLine('T', "", "TOTALE GENERALE", g[0], g[1], g[2], g[3], null, null));
        return out;
    }

    private static BigDecimal[] zeros() {
        return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    }

    private static void add(BigDecimal[] t, ClientLine l) {
        t[0] = t[0].add(l.pezzi);
        t[1] = t[1].add(l.valore);
        t[2] = t[2].add(l.addeb);
        t[3] = t[3].add(l.accre);
    }

    // ------------------------------------------------------------------ utilità

    private static final DecimalFormat GROUPED = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.ITALY));
    private static final DecimalFormat PLAIN = new DecimalFormat("0.00", new DecimalFormatSymbols(Locale.ITALY));
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    static String fmt(BigDecimal v, boolean grouping) {
        if (v == null) return "";
        return (grouping ? GROUPED : PLAIN).format(v);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    static LocalDate parseDate(String s) {
        String v = s.trim();
        for (DateTimeFormatter f : new DateTimeFormatter[]{DMY, DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/yyyy")}) {
            try {
                return LocalDate.parse(v, f);
            } catch (DateTimeParseException ignore) {
                // prova il formato successivo
            }
        }
        throw new IllegalArgumentException("Data non valida: '" + s + "' (usa gg-mm-aaaa)");
    }

    static String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(";") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    static void writeCsv(Result r, Writer w) throws IOException {
        w.write("﻿"); // BOM: Excel riconosce l'UTF-8
        w.write("MONDO LUX;" + r.type.title + ";Du " + DMY.format(r.from) + " au " + DMY.format(r.to) + "\r\n");
        w.write(String.join(";", r.headers()) + "\r\n");
        for (String[] row : r.rows(false)) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < row.length; i++) {
                if (i > 0) sb.append(';');
                sb.append(csvEscape(row[i]));
            }
            w.write(sb.append("\r\n").toString());
        }
    }

    static void printText(Result r, PrintStream out) {
        out.println("MONDO LUX - " + r.type.title + "   Du " + DMY.format(r.from) + " au " + DMY.format(r.to));
        List<String> h = r.headers();
        List<String[]> rows = r.rows(true);
        int[] w = new int[h.size()];
        for (int i = 0; i < w.length; i++) w[i] = h.get(i).length();
        for (String[] row : rows) for (int i = 0; i < row.length; i++) w[i] = Math.max(w[i], row[i].length());
        out.println(pad(h.toArray(new String[0]), w));
        for (int i = 0; i < r.lines.size(); i++) {
            if (r.lines.get(i).kind == 'N' || r.lines.get(i).kind == 'T') out.println();
            out.println(pad(rows.get(i), w));
        }
    }

    private static String spaces(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(' ');
        return sb.toString();
    }

    private static String pad(String[] cells, int[] w) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            String c = cells[i];
            boolean left = i < 2 || i == cells.length - 1;
            int n = w[i] - c.length();
            if (left) sb.append(c).append(spaces(n));
            else sb.append(spaces(n)).append(c);
            sb.append("  ");
        }
        return sb.toString().replaceAll("\\s+$", "");
    }

    // ------------------------------------------------------------------ connessione

    static final class DbConfig {
        String url, user = "poker", password;
    }

    static DbConfig loadConfig(Map<String, String> args) {
        DbConfig c = new DbConfig();
        Properties p = new Properties();
        File f = new File(args.getOrDefault("config", "ventes.properties"));
        if (f.isFile()) {
            try (InputStream in = new FileInputStream(f)) {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                System.err.println("Impossibile leggere " + f + ": " + e.getMessage());
            }
        }
        c.url = firstNonBlank(args.get("url"), System.getenv("VENTES_DB_URL"), p.getProperty("db.url"));
        c.user = firstNonBlank(args.get("user"), System.getenv("VENTES_DB_USER"), p.getProperty("db.user"), "poker");
        c.password = firstNonBlank(args.get("password"), System.getenv("VENTES_DB_PASSWORD"), p.getProperty("db.password"));
        return c;
    }

    private static String firstNonBlank(String... v) {
        for (String s : v) if (s != null && !s.trim().isEmpty()) return s.trim();
        return null;
    }

    private static final String ORACLE_MANAGER = "SVC.DbOracleManager";

    /** True se nel progetto c'è la classe SVC.DbOracleManager (connessione Oracle già configurata). */
    static boolean hasOracleManager() {
        try {
            Class.forName(ORACLE_MANAGER);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /**
     * Se è indicato un URL JDBC (argomento, variabile o properties) si usa quello con utente/password;
     * altrimenti si usa SVC.DbOracleManager.getConnection() del progetto, senza chiedere nulla.
     */
    static Connection connect(DbConfig c) throws SQLException {
        if (c.url != null) {
            return DriverManager.getConnection(c.url, c.user, c.password == null ? "" : c.password);
        }
        if (!hasOracleManager()) {
            throw new SQLException("Connessione non configurata: aggiungi SVC.DbOracleManager al progetto oppure usa "
                    + "--url, VENTES_DB_URL o db.url in ventes.properties (es. jdbc:oracle:thin:@//SERVER:1521/E).");
        }
        try {
            Object con = Class.forName(ORACLE_MANAGER).getMethod("getConnection").invoke(null);
            return (Connection) con;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable t = e.getCause() != null ? e.getCause() : e;
            if (t instanceof SQLException) throw (SQLException) t;
            throw new SQLException("DbOracleManager.getConnection() ha fallito: " + t.getMessage(), t);
        } catch (ReflectiveOperationException e) {
            throw new SQLException("Impossibile usare " + ORACLE_MANAGER + ".getConnection(): " + e, e);
        }
    }

    // ------------------------------------------------------------------ impaginazione (come il PDF del Crystal)

    /** Superficie di disegno astratta: la stessa impaginazione va a schermo, in stampa e nel PDF. */
    interface Canvas {
        void text(String s, double x, double y, boolean bold, double size, Color c);

        void line(double x1, double y1, double x2, double y2, double lw, boolean dotted);

        void fill(double x, double y, double w, double h, Color c);

        void box(double x, double y, double w, double h);
    }

    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private static final Font PLAIN100 = new Font(Font.SANS_SERIF, Font.PLAIN, 100);
    private static final Font BOLD100 = new Font(Font.SANS_SERIF, Font.BOLD, 100);
    private static final DateTimeFormatter DMY2 = DateTimeFormatter.ofPattern("dd-MM-yy");
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

    static double textWidth(String s, boolean bold, double size) {
        return (bold ? BOLD100 : PLAIN100).getStringBounds(s, FRC).getWidth() * size / 100.0;
    }

    /** Pagina in stile "VENTES TOTAL ... PAR NATION/CLIENT": intestazione, riquadro colonne, gruppi per nazione, piè di pagina. */
    static final class ReportLayout {
        static final double ROW_H = 12.5, HEADER_H = 66, FOOTER_H = 16;
        private static final Color BLACK = Color.BLACK, GREY = new Color(0x555555);

        private final Result r;
        private final List<String[]> rows;
        private final List<String> hdr;
        private final boolean caou;
        private final double[] edges;      // bordo destro (frazione della larghezza) delle colonne numeriche
        private final double natEdge = 0.50, codeX = 0.865, nameX = 0.075;
        private final LocalDateTime stamp = LocalDateTime.now();

        ReportLayout(Result r) {
            this.r = r;
            this.rows = r.rows(true);
            this.hdr = r.headers();
            this.caou = r.type == ReportType.CAOU;
            this.edges = caou ? new double[]{0.62, 0.80} : new double[]{0.38, 0.50, 0.61, 0.72, 0.83};
        }

        int rowsPerPage(double h) {
            return Math.max(1, (int) ((h - HEADER_H - FOOTER_H) / ROW_H));
        }

        int pages(double h) {
            int per = rowsPerPage(h);
            return Math.max(1, (rows.size() + per - 1) / per);
        }

        void draw(Canvas c, int page, double w, double h) {
            // intestazione di pagina
            c.text("MONDO LUX", 0, 10, true, 9, BLACK);
            c.text(r.type.title, 0, 26, false, 7.5, BLACK);
            c.text("A partir du Mois", 6, 38, false, 7.5, BLACK);
            c.text(DMY2.format(r.from), w * 0.17, 38, true, 7.5, BLACK);
            c.text("Au mois du", w * 0.30, 38, false, 7.5, BLACK);
            c.text(DMY2.format(r.to), w * 0.42, 38, true, 7.5, BLACK);

            // riquadro con i titoli di colonna
            double by = 44, bh = 14;
            c.box(0, by, w, bh);
            c.text("Nation / Client", 3, by + 10, false, 7, BLACK);
            for (int k = 0; k < edges.length; k++) {
                String t = hdr.get(2 + k);
                c.text(t, w * edges[k] - textWidth(t, false, 7), by + 10, false, 7, BLACK);
            }
            c.text("Code Ass.Cre.", w * codeX, by + 10, false, 7, BLACK);

            // righe
            int per = rowsPerPage(h);
            int first = page * per, last = Math.min(rows.size(), first + per);
            double y0 = by + bh;
            double nameLimit = (caou ? natEdge : edges[0]) * w - 62 - nameX * w;
            for (int k = first; k < last; k++) {
                char kind = r.lines.get(k).kind;
                String[] row = rows.get(k);
                double base = y0 + (k - first) * ROW_H + 10;
                boolean bold = kind == 'N' || kind == 'T';
                double sz = kind == 'D' ? 6.5 : 7.5;
                Color col = kind == 'D' ? GREY : BLACK;

                if (kind == 'N' && k > first) c.line(0, base - 10.5, w, base - 10.5, 0.6, true);
                if (kind == 'T') c.line(0, base - 10.5, w, base - 10.5, 0.8, false);

                String code = row[0].trim();
                c.text(code, kind == 'D' ? 14 : 3, base, bold, sz, col);

                String name = row[1];
                if (kind == 'T') {
                    c.text(caou ? "Total generale" : name, (caou ? 0.30 : nameX) * w, base, true, 7.5, BLACK);
                } else {
                    while (name.length() > 1 && textWidth(name, bold, sz) > nameLimit) name = name.substring(0, name.length() - 1);
                    c.text(name, nameX * w, base, bold, sz, col);
                }

                for (int i = 0; i < edges.length; i++) {
                    String t = row[2 + i];
                    if (t.isEmpty()) continue;
                    double edge = (caou && i == 0 && (kind == 'N' || kind == 'T')) ? natEdge : edges[i];
                    boolean b = bold || (caou && kind == 'C');
                    c.text(t, w * edge - textWidth(t, b, sz), base, b, sz, col);
                }
                String ca = row[row.length - 1];
                if (!ca.isEmpty()) c.text(ca, w * codeX, base, false, sz, col);
            }

            // piè di pagina
            double fy = h - 3;
            c.text("** " + r.type.footer, 0, fy, false, 6.5, BLACK);
            c.text(DMY2.format(stamp) + "   " + HMS.format(stamp), w * 0.42, fy, false, 6.5, BLACK);
            String pg = "Page " + (page + 1) + " of " + pages(h);
            c.text(pg, w - textWidth(pg, false, 6.5), fy, false, 6.5, BLACK);
        }
    }

    /** Canvas su Graphics2D (schermo e stampante). */
    static final class GfxCanvas implements Canvas {
        private final Graphics2D g;

        GfxCanvas(Graphics2D g) {
            this.g = g;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        }

        @Override
        public void text(String s, double x, double y, boolean bold, double size, Color c) {
            g.setColor(c);
            g.setFont((bold ? BOLD100 : PLAIN100).deriveFont((float) size));
            g.drawString(s, (float) x, (float) y);
        }

        @Override
        public void line(double x1, double y1, double x2, double y2, double lw, boolean dotted) {
            g.setColor(Color.BLACK);
            g.setStroke(dotted ? new BasicStroke((float) lw, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[]{1.2f, 1.8f}, 0f) : new BasicStroke((float) lw));
            g.draw(new java.awt.geom.Line2D.Double(x1, y1, x2, y2));
        }

        @Override
        public void fill(double x, double y, double w, double h, Color c) {
            g.setColor(c);
            g.fill(new Rectangle2D.Double(x, y, w, h));
        }

        @Override
        public void box(double x, double y, double w, double h) {
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(0.7f));
            g.draw(new Rectangle2D.Double(x, y, w, h));
        }
    }

    /** Canvas che produce il contenuto di una pagina PDF (Helvetica standard, nessuna libreria). */
    static final class PdfCanvas implements Canvas {
        final StringBuilder sb = new StringBuilder();

        private static String n(double v) {
            return String.format(Locale.ROOT, "%.2f", v);
        }

        private static String esc(String s) {
            StringBuilder o = new StringBuilder();
            for (char ch : s.toCharArray()) {
                int c = ch == '€' ? 0x80 : ch;
                if (c > 255) c = '?';
                if (c == '(' || c == ')' || c == '\\') o.append('\\').append((char) c);
                else if (c < 32 || c > 126) o.append(String.format("\\%03o", c));
                else o.append((char) c);
            }
            return o.toString();
        }

        private static String rgb(Color c) {
            return n(c.getRed() / 255.0) + " " + n(c.getGreen() / 255.0) + " " + n(c.getBlue() / 255.0);
        }

        @Override
        public void text(String s, double x, double y, boolean bold, double size, Color c) {
            sb.append(rgb(c)).append(" rg BT /F").append(bold ? 2 : 1).append(' ').append(n(size)).append(" Tf ")
                    .append(n(x)).append(' ').append(n(-y)).append(" Td (").append(esc(s)).append(") Tj ET\n");
        }

        @Override
        public void line(double x1, double y1, double x2, double y2, double lw, boolean dotted) {
            sb.append("0 G ").append(n(lw)).append(" w ").append(dotted ? "[1.2 1.8] 0 d " : "[] 0 d ")
                    .append(n(x1)).append(' ').append(n(-y1)).append(" m ")
                    .append(n(x2)).append(' ').append(n(-y2)).append(" l S [] 0 d\n");
        }

        @Override
        public void fill(double x, double y, double w, double h, Color c) {
            sb.append(rgb(c)).append(" rg ").append(n(x)).append(' ').append(n(-(y + h))).append(' ')
                    .append(n(w)).append(' ').append(n(h)).append(" re f\n");
        }

        @Override
        public void box(double x, double y, double w, double h) {
            sb.append("0 G 0.7 w [] 0 d ").append(n(x)).append(' ').append(n(-(y + h))).append(' ')
                    .append(n(w)).append(' ').append(n(h)).append(" re S\n");
        }
    }

    /** Scrive il report come PDF (una pagina per foglio, stessa impaginazione della stampa). */
    static void writePdf(Result r, PageFormat pf, OutputStream os) throws IOException {
        ReportLayout lay = new ReportLayout(r);
        double w = pf.getImageableWidth(), h = pf.getImageableHeight();
        int pages = lay.pages(h);
        List<byte[]> objs = new ArrayList<>();
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pages; i++) kids.append(5 + 2 * i).append(" 0 R ");
        objs.add(latin1("<< /Type /Catalog /Pages 2 0 R >>"));
        objs.add(latin1("<< /Type /Pages /Kids [" + kids + "] /Count " + pages + " >>"));
        objs.add(latin1("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
        objs.add(latin1("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"));
        for (int i = 0; i < pages; i++) {
            PdfCanvas pc = new PdfCanvas();
            lay.draw(pc, i, w, h);
            String content = "1 0 0 1 " + PdfCanvas.n(pf.getImageableX()) + " "
                    + PdfCanvas.n(pf.getHeight() - pf.getImageableY()) + " cm\n" + pc.sb;
            byte[] cb = latin1(content);
            objs.add(latin1("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + PdfCanvas.n(pf.getWidth()) + " "
                    + PdfCanvas.n(pf.getHeight()) + "] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents "
                    + (6 + 2 * i) + " 0 R >>"));
            ByteArrayOutputStream st = new ByteArrayOutputStream();
            st.write(latin1("<< /Length " + cb.length + " >>\nstream\n"));
            st.write(cb);
            st.write(latin1("\nendstream"));
            objs.add(st.toByteArray());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(latin1("%PDF-1.4\n"));
        int[] off = new int[objs.size()];
        for (int i = 0; i < objs.size(); i++) {
            off[i] = out.size();
            out.write(latin1((i + 1) + " 0 obj\n"));
            out.write(objs.get(i));
            out.write(latin1("\nendobj\n"));
        }
        int xref = out.size();
        StringBuilder x = new StringBuilder("xref\n0 " + (objs.size() + 1) + "\n0000000000 65535 f \n");
        for (int o : off) x.append(String.format(Locale.ROOT, "%010d 00000 n \n", o));
        x.append("trailer\n<< /Size ").append(objs.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        out.write(latin1(x.toString()));
        os.write(out.toByteArray());
    }

    private static byte[] latin1(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    /** Stampa su stampante di sistema. */
    static final class ReportPrintable implements Printable {
        private final ReportLayout lay;

        ReportPrintable(Result r) {
            this.lay = new ReportLayout(r);
        }

        @Override
        public int print(Graphics gr, PageFormat pf, int idx) {
            if (idx < 0 || idx >= lay.pages(pf.getImageableHeight())) return NO_SUCH_PAGE;
            Graphics2D g = (Graphics2D) gr.create();
            g.translate(pf.getImageableX(), pf.getImageableY());
            lay.draw(new GfxCanvas(g), idx, pf.getImageableWidth(), pf.getImageableHeight());
            g.dispose();
            return PAGE_EXISTS;
        }
    }

    private static PageFormat a4(boolean landscape) {
        Paper paper = new Paper();
        paper.setSize(595, 842);
        paper.setImageableArea(24, 24, 547, 794);
        PageFormat pf = new PageFormat();
        pf.setPaper(paper);
        pf.setOrientation(landscape ? PageFormat.LANDSCAPE : PageFormat.PORTRAIT);
        return pf;
    }

    static String defaultFileName(Result r, String ext) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("ddMMyyyy");
        return "fatturato_" + (r.type == ReportType.CAOU ? "flooring" : "ballons") + "_" + f.format(r.from)
                + "_-_" + f.format(r.to) + "." + ext;
    }

    // ------------------------------------------------------------------ GUI

    /** Pannello pagina (anteprima), con scorrimento pagine, zoom, orientamento, stampa ed esportazione. */
    static final class ReportPane extends JPanel {
        private Result result;
        private ReportLayout layout;
        private PageFormat pf = a4(false);
        private int page = 0;
        private double zoom = 1.25;
        private final JLabel lbPage = new JLabel(" ");
        private final JComboBox<String> orient = new JComboBox<>(new String[]{"Verticale", "Orizzontale"});
        private final JButton prev = new JButton("◀"), next = new JButton("▶"),
                zin = new JButton("Zoom +"), zout = new JButton("Zoom −"),
                print = new JButton("Stampa..."), pdf = new JButton("Esporta PDF..."), csv = new JButton("Esporta CSV...");
        private final JPanel canvas;
        private final JLabel status;

        ReportPane(JLabel status) {
            super(new BorderLayout());
            this.status = status;
            canvas = new JPanel() {
                @Override
                protected void paintComponent(Graphics g0) {
                    super.paintComponent(g0);
                    if (layout == null) return;
                    Graphics2D g = (Graphics2D) g0.create();
                    double pw = pf.getWidth() * zoom, ph = pf.getHeight() * zoom;
                    g.setColor(new Color(0x777777));
                    g.fillRect(24, 24, (int) pw, (int) ph);
                    g.setColor(Color.WHITE);
                    g.fillRect(20, 20, (int) pw, (int) ph);
                    g.translate(20, 20);
                    g.setClip(0, 0, (int) pw, (int) ph);
                    g.scale(zoom, zoom);
                    g.translate(pf.getImageableX(), pf.getImageableY());
                    layout.draw(new GfxCanvas(g), page, pf.getImageableWidth(), pf.getImageableHeight());
                    g.dispose();
                }

                @Override
                public Dimension getPreferredSize() {
                    return new Dimension((int) (pf.getWidth() * zoom) + 44, (int) (pf.getHeight() * zoom) + 44);
                }
            };
            canvas.setBackground(new Color(0xA0A0A0));

            JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
            bar.add(prev);
            bar.add(lbPage);
            bar.add(next);
            bar.add(zout);
            bar.add(zin);
            bar.add(new JLabel("Orientamento:"));
            bar.add(orient);
            bar.add(print);
            bar.add(pdf);
            bar.add(csv);
            add(bar, BorderLayout.NORTH);
            add(new JScrollPane(canvas), BorderLayout.CENTER);

            prev.addActionListener(e -> go(-1));
            next.addActionListener(e -> go(1));
            zin.addActionListener(e -> {
                zoom = Math.min(3.0, zoom + 0.25);
                refresh();
            });
            zout.addActionListener(e -> {
                zoom = Math.max(0.5, zoom - 0.25);
                refresh();
            });
            orient.addActionListener(e -> {
                pf = a4(orient.getSelectedIndex() == 1);
                page = 0;
                refresh();
            });
            print.addActionListener(e -> doPrint());
            pdf.addActionListener(e -> doExport(true));
            csv.addActionListener(e -> doExport(false));
            setEnabledAll(false);
        }

        private void setEnabledAll(boolean on) {
            for (JComponent c : new JComponent[]{prev, next, zin, zout, orient, print, pdf, csv}) c.setEnabled(on);
        }

        void setResult(Result r) {
            this.result = r;
            this.layout = new ReportLayout(r);
            boolean land = r.type == ReportType.BALLONS;
            pf = a4(land);
            orient.setSelectedIndex(land ? 1 : 0);
            page = 0;
            setEnabledAll(true);
            refresh();
        }

        private void go(int d) {
            int n = layout.pages(pf.getImageableHeight());
            page = Math.max(0, Math.min(n - 1, page + d));
            refresh();
        }

        private void refresh() {
            if (layout == null) return;
            int n = layout.pages(pf.getImageableHeight());
            if (page >= n) page = n - 1;
            lbPage.setText("Pagina " + (page + 1) + " / " + n);
            canvas.revalidate();
            canvas.repaint();
        }

        private void doPrint() {
            try {
                PrinterJob job = PrinterJob.getPrinterJob();
                job.setJobName(result.type.title);
                job.setPrintable(new ReportPrintable(result), pf);
                if (job.printDialog()) job.print();
            } catch (PrinterException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Errore di stampa", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void doExport(boolean asPdf) {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File(defaultFileName(result, asPdf ? "pdf" : "csv")));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File f = fc.getSelectedFile();
            try {
                if (asPdf) {
                    try (OutputStream os = new FileOutputStream(f)) {
                        writePdf(result, pf, os);
                    }
                } else {
                    try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
                        writeCsv(result, w);
                    }
                }
                status.setText("Salvato: " + f);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Errore salvataggio", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void showGui(DbConfig cfg) {
        JFrame f = new JFrame("Ventes par nation / client - Mondo Lux");
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JComboBox<ReportType> cbReport = new JComboBox<>(ReportType.values());
        JTextField tfFrom = new JTextField(LocalDate.now().withDayOfYear(1).format(DMY), 10);
        JTextField tfTo = new JTextField(LocalDate.now().format(DMY), 10);
        JCheckBox cbStf = new JCheckBox("Escludi RB_STF = 'I' (Caou)", true);
        JCheckBox cbDet = new JCheckBox("Dettaglio fatture", false);
        JButton btRun = new JButton("Refresh");
        JLabel status = new JLabel(" ");

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        top.add(new JLabel("Report:"));
        top.add(cbReport);
        top.add(new JLabel("FromDate:"));
        top.add(tfFrom);
        top.add(new JLabel("ToDate:"));
        top.add(tfTo);
        top.add(cbStf);
        top.add(cbDet);
        top.add(btRun);

        ReportPane pane = new ReportPane(status);

        btRun.addActionListener(ev -> {
            final ReportType type = (ReportType) cbReport.getSelectedItem();
            final LocalDate from, to;
            try {
                from = parseDate(tfFrom.getText());
                to = parseDate(tfTo.getText());
                if (to.isBefore(from)) throw new IllegalArgumentException("ToDate precedente a FromDate");
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(f, ex.getMessage(), "Parametri", JOptionPane.WARNING_MESSAGE);
                return;
            }
            DbConfig c = new DbConfig();
            c.url = cfg.url;
            c.user = cfg.user;
            c.password = cfg.password; // usati solo senza DbOracleManager
            if (c.url == null && !hasOracleManager()) {
                c.url = JOptionPane.showInputDialog(f, "URL JDBC (es. jdbc:oracle:thin:@//SERVER:1521/E):");
                if (c.url == null) return;
                cfg.url = c.url;
            }
            final boolean stf = cbStf.isSelected();
            final boolean det = cbDet.isSelected();
            btRun.setEnabled(false);
            status.setText("Estrazione in corso...");
            new SwingWorker<Result, Void>() {
                @Override
                protected Result doInBackground() throws Exception {
                    try (Connection con = connect(c)) {
                        return VentesNationClient.run(con, type, from, to, stf, det);
                    }
                }

                @Override
                protected void done() {
                    btRun.setEnabled(true);
                    try {
                        Result r = get();
                        pane.setResult(r);
                        BigDecimal tot = r.lines.get(r.lines.size() - 1).valore;
                        status.setText((r.lines.size() - 1) + " righe - totale generale: " + fmt(tot, true));
                    } catch (Exception ex) {
                        Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                        status.setText("Errore");
                        JOptionPane.showMessageDialog(f, t.getMessage(), "Errore estrazione", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        f.add(top, BorderLayout.NORTH);
        f.add(pane, BorderLayout.CENTER);
        f.add(status, BorderLayout.SOUTH);
        f.setSize(1100, 800);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    // ------------------------------------------------------------------ main

    private static Map<String, String> parseArgs(String[] a) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < a.length; i++) {
            if (!a[i].startsWith("--")) throw new IllegalArgumentException("Argomento inatteso: " + a[i]);
            String k = a[i].substring(2);
            if (k.equals("help") || k.equals("no-stf-filter") || k.equals("detail")) {
                m.put(k, "true");
            } else {
                if (i + 1 >= a.length) throw new IllegalArgumentException("Manca il valore di --" + k);
                m.put(k, a[++i]);
            }
        }
        return m;
    }

    private static void usage() {
        System.out.println("Uso:\n"
                + "  (senza argomenti)  apre l'interfaccia grafica\n"
                + "  --report caou|ballons --from gg-mm-aaaa --to gg-mm-aaaa [--out file.pdf|file.csv]\n"
                + "  [--detail]  aggiunge il dettaglio per fattura sotto ogni cliente\n"
                + "  [--no-stf-filter]  (Caou: non applica RB_STF <> 'I')\n"
                + "  [--url jdbc:oracle:thin:@//SERVER:1521/E] [--user poker] [--password ...] [--config file]\n"
                + "Senza --out il risultato viene stampato a video.");
    }

    public static void main(String[] args) {
        Map<String, String> a;
        try {
            a = parseArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            usage();
            System.exit(2);
            return;
        }
        if (a.containsKey("help")) {
            usage();
            return;
        }
        DbConfig cfg = loadConfig(a);

        if (!a.containsKey("report")) {
            SwingUtilities.invokeLater(() -> showGui(cfg));
            return;
        }

        try {
            ReportType type = ReportType.parse(a.get("report"));
            if (!a.containsKey("from") || !a.containsKey("to"))
                throw new IllegalArgumentException("Servono --from e --to");
            LocalDate from = parseDate(a.get("from")), to = parseDate(a.get("to"));
            if (to.isBefore(from)) throw new IllegalArgumentException("--to precedente a --from");
            if (cfg.password == null && (cfg.url != null || !hasOracleManager())) {
                Console console = System.console();
                if (console == null) throw new IllegalArgumentException("Password mancante (--password o VENTES_DB_PASSWORD)");
                cfg.password = new String(console.readPassword("Password per %s: ", cfg.user));
            }
            Result r;
            try (Connection con = connect(cfg)) {
                r = run(con, type, from, to, !a.containsKey("no-stf-filter"), a.containsKey("detail"));
            }
            if (a.containsKey("out") && a.get("out").toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                try (OutputStream os = new FileOutputStream(a.get("out"))) {
                    writePdf(r, a4(r.type == ReportType.BALLONS), os);
                }
                System.out.println("Scritto " + a.get("out"));
            } else if (a.containsKey("out")) {
                try (Writer w = new OutputStreamWriter(new FileOutputStream(a.get("out")), StandardCharsets.UTF_8)) {
                    writeCsv(r, w);
                }
                System.out.println("Scritto " + a.get("out") + " (" + (r.lines.size() - 1) + " righe)");
            } else {
                printText(r, System.out);
            }
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            usage();
            System.exit(2);
        } catch (SQLException | IOException e) {
            System.err.println("Errore: " + e.getMessage());
            System.exit(1);
        }
    }
}
