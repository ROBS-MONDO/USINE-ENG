package CRY;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
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
        CAOU("Caou", "VENTES TOTAL Caou PAR NATION/CLIENT -Statpav2-"),
        BALLONS("Ballons", "VENTES TOTAL BALLONS PAR NATION/CLIENT - EURO");

        final String label;
        final String title;

        ReportType(String label, String title) {
            this.label = label;
            this.title = title;
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
                + "LEFT JOIN POKER.ANAG a ON a.AN_COD = s.RB_CLFO "
                + "LEFT JOIN POKER.CLIFOR c ON c.CF_COD = s.RB_CLFO "
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
                + "LEFT JOIN POKER.ANAG a ON a.AN_COD = s.RB_CLFO "
                + "LEFT JOIN POKER.CLIFOR c ON c.CF_COD = s.RB_CLFO "
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

    static Connection connect(DbConfig c) throws SQLException {
        if (c.url == null) {
            throw new SQLException("URL JDBC non configurato. Usa --url, VENTES_DB_URL oppure db.url in "
                    + "ventes.properties (es. jdbc:oracle:thin:@//SERVER:1521/E).");
        }
        return DriverManager.getConnection(c.url, c.user, c.password == null ? "" : c.password);
    }

    // ------------------------------------------------------------------ GUI

    private static void showGui(DbConfig cfg) {
        JFrame f = new JFrame("Ventes par nation / client - Mondo Lux");
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JComboBox<ReportType> cbReport = new JComboBox<>(ReportType.values());
        JTextField tfFrom = new JTextField(LocalDate.now().withDayOfYear(1).format(DMY), 10);
        JTextField tfTo = new JTextField(LocalDate.now().format(DMY), 10);
        JPasswordField pf = new JPasswordField(cfg.password == null ? "" : cfg.password, 10);
        JCheckBox cbStf = new JCheckBox("Escludi RB_STF = 'I' (Caou)", true);
        JCheckBox cbDet = new JCheckBox("Dettaglio fatture", true);
        JButton btRun = new JButton("Refresh");
        JButton btCsv = new JButton("Esporta CSV...");
        btCsv.setEnabled(false);
        JButton btPrint = new JButton("Anteprima / Stampa...");
        btPrint.setEnabled(false);
        JLabel status = new JLabel(" ");

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        top.add(new JLabel("Report:"));
        top.add(cbReport);
        top.add(new JLabel("FromDate:"));
        top.add(tfFrom);
        top.add(new JLabel("ToDate:"));
        top.add(tfTo);
        top.add(new JLabel("Password:"));
        top.add(pf);
        top.add(cbStf);
        top.add(cbDet);
        top.add(btRun);
        top.add(btCsv);
        top.add(btPrint);

        DefaultTableModel model = new DefaultTableModel();
        final AtomicReference<List<OutLine>> shown = new AtomicReference<>(new ArrayList<>());
        JTable table = new JTable(model) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                char kind = r < shown.get().size() ? shown.get().get(r).kind : 'C';
                comp.setFont(t.getFont().deriveFont(kind == 'N' || kind == 'T' ? Font.BOLD : Font.PLAIN));
                int cols = t.getColumnCount();
                setHorizontalAlignment(c < 2 || c == cols - 1 ? SwingConstants.LEFT : SwingConstants.RIGHT);
                if (!sel) comp.setBackground(kind == 'N' ? new Color(0xE8EEF7) : kind == 'T' ? new Color(0xD5DFEF)
                        : kind == 'D' ? new Color(0xF7F7F7) : Color.WHITE);
                return comp;
            }
        };
        table.setDefaultRenderer(Object.class, renderer);

        final Result[] last = new Result[1];

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
            c.password = new String(pf.getPassword());
            if (c.url == null) {
                c.url = JOptionPane.showInputDialog(f, "URL JDBC (es. jdbc:oracle:thin:@//SERVER:1521/E):");
                if (c.url == null) return;
                cfg.url = c.url;
            }
            final boolean stf = cbStf.isSelected();
            final boolean det = cbDet.isSelected();
            btRun.setEnabled(false);
            btCsv.setEnabled(false);
            btPrint.setEnabled(false);
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
                        last[0] = r;
                        shown.set(r.lines);
                        model.setDataVector(r.rows(true).toArray(new Object[0][]), r.headers().toArray());
                        btCsv.setEnabled(true);
                        btPrint.setEnabled(true);
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

        btPrint.addActionListener(ev -> {
            if (last[0] != null) showPreview(f, last[0]);
        });

        btCsv.addActionListener(ev -> {
            if (last[0] == null) return;
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("ventes_" + last[0].type.label.toLowerCase(Locale.ROOT) + ".csv"));
            if (fc.showSaveDialog(f) != JFileChooser.APPROVE_OPTION) return;
            try (Writer w = new OutputStreamWriter(new FileOutputStream(fc.getSelectedFile()), StandardCharsets.UTF_8)) {
                writeCsv(last[0], w);
                status.setText("Salvato: " + fc.getSelectedFile());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(f, ex.getMessage(), "Errore salvataggio", JOptionPane.ERROR_MESSAGE);
            }
        });

        f.add(top, BorderLayout.NORTH);
        f.add(new JScrollPane(table), BorderLayout.CENTER);
        f.add(status, BorderLayout.SOUTH);
        f.setSize(1500, 700);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    // ------------------------------------------------------------------ stampa e anteprima

    /** Impaginazione A4 del report: intestazione e piè di pagina su ogni foglio, colonne adattate alla larghezza. */
    static final class ReportPrintable implements Printable {
        private static final int LINE_H = 12, HEADER_H = 64, FOOTER_H = 22, GAP = 14;
        private final Result r;
        private final List<String> hdr;
        private final List<String[]> rows;
        private final Font base = new Font(Font.SANS_SERIF, Font.PLAIN, 8);
        private final Font bold = base.deriveFont(Font.BOLD);
        private double[] colX, colW;
        private double scale = 1;
        private int rowsPerPage = 1, pages = 1;
        private double laidW = -1, laidH = -1;

        ReportPrintable(Result r) {
            this.r = r;
            this.hdr = r.headers();
            this.rows = r.rows(true);
        }

        int pageCount(PageFormat pf) {
            layout(pf);
            return pages;
        }

        private void layout(PageFormat pf) {
            double w = pf.getImageableWidth(), h = pf.getImageableHeight();
            if (w == laidW && h == laidH) return;
            laidW = w;
            laidH = h;
            Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
            FontMetrics fm = g.getFontMetrics(bold);
            int n = hdr.size();
            colW = new double[n];
            colX = new double[n];
            for (int i = 0; i < n; i++) {
                int m = fm.stringWidth(hdr.get(i));
                for (String[] row : rows) m = Math.max(m, fm.stringWidth(row[i]));
                colW[i] = m + GAP;
            }
            double total = 0;
            for (int i = 0; i < n; i++) {
                colX[i] = total;
                total += colW[i];
            }
            g.dispose();
            scale = Math.min(1.0, w / total);
            double usableH = h / scale;
            rowsPerPage = Math.max(1, (int) ((usableH - HEADER_H - FOOTER_H) / LINE_H));
            pages = Math.max(1, (rows.size() + rowsPerPage - 1) / rowsPerPage);
        }

        @Override
        public int print(Graphics gr, PageFormat pf, int idx) {
            layout(pf);
            if (idx < 0 || idx >= pages) return NO_SUCH_PAGE;
            Graphics2D g = (Graphics2D) gr.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.translate(pf.getImageableX(), pf.getImageableY());
            g.scale(scale, scale);
            double w = pf.getImageableWidth() / scale, h = pf.getImageableHeight() / scale;
            g.setColor(Color.BLACK);

            g.setFont(bold.deriveFont(11f));
            g.drawString("MONDO LUX", 0, 11);
            g.setFont(bold.deriveFont(10f));
            g.drawString(r.type.title, 0, 25);
            g.setFont(base);
            g.drawString("Du " + DMY.format(r.from) + " au " + DMY.format(r.to), 0, 38);

            int y = HEADER_H - 6;
            g.setFont(bold);
            for (int i = 0; i < hdr.size(); i++) drawCell(g, hdr.get(i), i, y);
            g.drawLine(0, y + 3, (int) w, y + 3);

            int first = idx * rowsPerPage, last = Math.min(rows.size(), first + rowsPerPage);
            y = HEADER_H + LINE_H - 2;
            for (int k = first; k < last; k++, y += LINE_H) {
                char kind = r.lines.get(k).kind;
                if (kind == 'N' || kind == 'T') {
                    g.setColor(kind == 'T' ? new Color(0xD5DFEF) : new Color(0xE8EEF7));
                    g.fill(new Rectangle2D.Double(0, y - LINE_H + 3, w, LINE_H));
                    g.setColor(Color.BLACK);
                    g.setFont(bold);
                } else {
                    g.setFont(base);
                    g.setColor(kind == 'D' ? new Color(0x444444) : Color.BLACK);
                }
                String[] row = rows.get(k);
                for (int i = 0; i < row.length; i++) drawCell(g, row[i], i, y);
                g.setColor(Color.BLACK);
            }

            g.setFont(base);
            double fy = h - 4;
            g.drawLine(0, (int) (fy - 10), (int) w, (int) (fy - 10));
            g.drawString("Stampato il " + java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
                    .format(LocalDateTime.now()), 0, (int) fy);
            String pg = "Pagina " + (idx + 1) + " / " + pages;
            g.drawString(pg, (int) (w - g.getFontMetrics().stringWidth(pg)), (int) fy);
            g.dispose();
            return PAGE_EXISTS;
        }

        private void drawCell(Graphics2D g, String text, int col, int y) {
            boolean left = col < 2 || col == hdr.size() - 1;
            if (left) {
                g.drawString(text, (float) colX[col], y);
            } else {
                int tw = g.getFontMetrics().stringWidth(text);
                g.drawString(text, (float) (colX[col] + colW[col] - GAP / 2.0 - tw), y);
            }
        }
    }

    private static PageFormat a4(boolean landscape) {
        Paper paper = new Paper();
        paper.setSize(595, 842);
        paper.setImageableArea(28, 28, 539, 786);
        PageFormat pf = new PageFormat();
        pf.setPaper(paper);
        pf.setOrientation(landscape ? PageFormat.LANDSCAPE : PageFormat.PORTRAIT);
        return pf;
    }

    /** Finestra di anteprima: pagine A4 con zoom, orientamento e pulsante Stampa. */
    static void showPreview(Frame owner, Result r) {
        final ReportPrintable doc = new ReportPrintable(r);
        final PageFormat[] pf = {a4(true)};
        final int[] page = {0};
        final double[] zoom = {1.0};

        final JLabel lbPage = new JLabel();
        final JPanel canvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                Graphics2D g = (Graphics2D) g0.create();
                double pw = pf[0].getWidth() * zoom[0], ph = pf[0].getHeight() * zoom[0];
                g.setColor(new Color(0x777777));
                g.fillRect(24, 24, (int) pw, (int) ph);
                g.setColor(Color.WHITE);
                g.fillRect(20, 20, (int) pw, (int) ph);
                g.translate(20, 20);
                g.setClip(0, 0, (int) pw, (int) ph);
                g.scale(zoom[0], zoom[0]);
                doc.print(g, pf[0], page[0]);
                g.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension((int) (pf[0].getWidth() * zoom[0]) + 44, (int) (pf[0].getHeight() * zoom[0]) + 44);
            }
        };
        canvas.setBackground(new Color(0xA0A0A0));

        final JDialog d = new JDialog(owner, "Anteprima di stampa", true);
        final Runnable refresh = () -> {
            int n = doc.pageCount(pf[0]);
            if (page[0] >= n) page[0] = n - 1;
            lbPage.setText("Pagina " + (page[0] + 1) + " / " + n);
            canvas.revalidate();
            canvas.repaint();
        };

        JButton prev = new JButton("◀"), next = new JButton("▶");
        JButton zin = new JButton("Zoom +"), zout = new JButton("Zoom −");
        JComboBox<String> orient = new JComboBox<>(new String[]{"Orizzontale", "Verticale"});
        JButton print = new JButton("Stampa...");
        JButton close = new JButton("Chiudi");

        prev.addActionListener(e -> {
            if (page[0] > 0) page[0]--;
            refresh.run();
        });
        next.addActionListener(e -> {
            if (page[0] < doc.pageCount(pf[0]) - 1) page[0]++;
            refresh.run();
        });
        zin.addActionListener(e -> {
            zoom[0] = Math.min(3.0, zoom[0] + 0.25);
            refresh.run();
        });
        zout.addActionListener(e -> {
            zoom[0] = Math.max(0.5, zoom[0] - 0.25);
            refresh.run();
        });
        orient.addActionListener(e -> {
            pf[0] = a4(orient.getSelectedIndex() == 0);
            page[0] = 0;
            refresh.run();
        });
        print.addActionListener(e -> {
            try {
                PrinterJob job = PrinterJob.getPrinterJob();
                job.setJobName(r.type.title);
                job.setPrintable(doc, pf[0]);
                if (job.printDialog()) job.print();
            } catch (PrinterException ex) {
                JOptionPane.showMessageDialog(d, ex.getMessage(), "Errore di stampa", JOptionPane.ERROR_MESSAGE);
            }
        });
        close.addActionListener(e -> d.dispose());

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        bar.add(print);
        bar.add(prev);
        bar.add(lbPage);
        bar.add(next);
        bar.add(zout);
        bar.add(zin);
        bar.add(new JLabel("Orientamento:"));
        bar.add(orient);
        bar.add(close);

        d.add(bar, BorderLayout.NORTH);
        d.add(new JScrollPane(canvas), BorderLayout.CENTER);
        refresh.run();
        d.setSize(960, 700);
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
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
                + "  --report caou|ballons --from gg-mm-aaaa --to gg-mm-aaaa [--out file.csv]\n"
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
            if (cfg.password == null) {
                Console console = System.console();
                if (console == null) throw new IllegalArgumentException("Password mancante (--password o VENTES_DB_PASSWORD)");
                cfg.password = new String(console.readPassword("Password per %s: ", cfg.user));
            }
            Result r;
            try (Connection con = connect(cfg)) {
                r = run(con, type, from, to, !a.containsKey("no-stf-filter"), a.containsKey("detail"));
            }
            if (a.containsKey("out")) {
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
