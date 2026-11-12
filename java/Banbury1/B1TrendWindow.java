package Banbury1;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Trend di Banbury 1 (come il controllo WinCC OnlineTrendControl della schermata Display: finestra di
 * 20 minuti, serie "Temperature" in rosso, scala 0-200). Due sorgenti, SOLA LETTURA:
 *  - LIVE: valori letti dal PLC ogni secondo (memorizzati in memoria);
 *  - STORICO: tabella SQL Datas_B1 (Date_record, Time_record, Temperature, Amperage, Vitesse, Mode),
 *    come fa PLC3_Graph per Datas_B2. Connessione dalle chiavi jdbc.* di b1_label_sql.properties.
 */
public class B1TrendWindow extends JFrame {

    private static B1TrendWindow shared;

    public static void open(B1Source src, Path sqlConfig) {
        SwingUtilities.invokeLater(() -> {
            synchronized (B1TrendWindow.class) {
                if (shared == null || !shared.isDisplayable()) {
                    shared = new B1TrendWindow(src, sqlConfig);
                    shared.setVisible(true);
                }
                shared.toFront();
            }
        });
    }

    /** Query dello storico per le colonne indicate. */
    static String historySql(String table, List<String> columns) {
        StringBuilder c = new StringBuilder();
        for (String col : columns) {
            c.append(", ").append(col);
        }
        return "SELECT DATEADD(SECOND, DATEDIFF(SECOND, 0, Time_record), CAST(Date_record AS DATETIME)) AS TS, Mode"
                + c + " FROM " + table
                + " WHERE Date_record >= ? ORDER BY Date_record, Time_record";
    }

    // ------------------------------------------------------------------ finestra
    private final B1Source src;
    private final Path sqlConfig;
    private final List<B1TrendChart.Series> series = B1TrendChart.defaultSeries();
    private final B1TrendChart chart;
    private final JComboBox<String> cmbRange = new JComboBox<>(new String[]{"20 min", "1 ora", "4 ore", "8 ore", "24 ore", "2 giorni"});
    private final JComboBox<String> cmbSource = new JComboBox<>(new String[]{"LIVE (PLC)", "STORICO (SQL)"});
    private final JLabel status = new JLabel(" ");
    
    private B1TrendWindow(B1Source src, Path sqlConfig) {
        super("Banbury 1 - Trend (sola lettura)");
        this.src = src;
        this.sqlConfig = sqlConfig;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        chart = new B1TrendChart(series);
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        top.add(new JLabel("Sorgente:"));
        top.add(cmbSource);
        top.add(new JLabel("Intervallo:"));
        top.add(cmbRange);
        JCheckBox auto = new JCheckBox("Scala automatica", true);
        auto.setToolTipText("Corrente, giri e pressione si adattano ai valori; la temperatura resta 0-200 come nel WinCC");
        auto.addActionListener(e -> {
            chart.autoScale = auto.isSelected();
            chart.repaint();
        });
        top.add(auto);
        for (B1TrendChart.Series s : series) {
            JCheckBox cb = new JCheckBox(s.label + " [" + s.unit + "]", true);
            cb.setForeground(s.color.darker());
            cb.addActionListener(e -> {
                s.visible = cb.isSelected();
                chart.repaint();
            });
            top.add(cb);
        }
        add(top, BorderLayout.NORTH);
        add(chart, BorderLayout.CENTER);
        status.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 8, 3, 8));
        add(status, BorderLayout.SOUTH);
        setSize(1100, 620);
        setLocationRelativeTo(null);
        cmbRange.setSelectedIndex(0);
        cmbSource.addActionListener(e -> reload());
        cmbRange.addActionListener(e -> reload());
        new Timer(1000, e -> tick()).start();
    }

    private long rangeMs() {
        long[] ms = {20 * 60_000L, 3_600_000L, 4 * 3_600_000L, 8 * 3_600_000L, 24 * 3_600_000L, 48 * 3_600_000L};
        return ms[cmbRange.getSelectedIndex()];
    }

    private boolean live() {
        return cmbSource.getSelectedIndex() == 0;
    }

    /** Ogni secondo: campiona il PLC (sempre, per avere memoria) e aggiorna il grafico. */
    private void tick() {
        long now = System.currentTimeMillis();
        if (src.connected()) {
            for (B1TrendChart.Series s : series) {
                Double v = src.num(s.liveKey);
                if (v != null) {
                    synchronized (s.pts) {
                        s.pts.add(new double[]{now, v});
                    }
                }
            }
        }
        long keepFrom = now - 6 * 3_600_000L;
        for (B1TrendChart.Series s : series) {
            synchronized (s.pts) {
                // la memoria LIVE tiene al massimo 6 ore
                if (!s.pts.isEmpty() && s.pts.get(0)[0] < keepFrom && live()) {
                    int i = 0;
                    while (i < s.pts.size() && s.pts.get(i)[0] < keepFrom) {
                        i++;
                    }
                    s.pts.subList(0, i).clear();
                }
            }
        }
        chart.repaint();
    }

    /** Cambio di sorgente/intervallo: nello STORICO carica da SQL, nel LIVE usa la memoria. */
    private void reload() {
        chart.rangeMs = rangeMs();
        if (live()) {
            status.setText("LIVE: valori letti dal PLC ogni secondo (memoria: ultime 6 ore da quando e' aperta la finestra)");
            chart.repaint();
            return;
        }
        status.setText("Caricamento storico da SQL...");
        final long span = rangeMs();
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                return loadHistory(span);
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void done() {
                try {
                    Object[] r = get();
                    List<List<double[]>> data = (List<List<double[]>>) r[0];
                    chart.modeSpans = (List<long[]>) r[1];
                    int i = 0;
                    for (B1TrendChart.Series s : series) {
                        if (s.sqlColumn != null) {
                            synchronized (s.pts) {
                                s.pts.clear();
                                s.pts.addAll(data.get(i++));
                            }
                        }
                    }
                    status.setText("STORICO SQL (Datas_B1): " + dataCount(data) + " punti");
                } catch (Exception e) {
                    Throwable t = e.getCause() != null ? e.getCause() : e;
                    status.setText("Storico non disponibile: " + t.getMessage());
                }
                chart.repaint();
            }
        }.execute();
    }

    private static int dataCount(List<List<double[]>> d) {
        int n = 0;
        for (List<double[]> l : d) {
            n += l.size();
        }
        return n;
    }

    /** Legge lo storico da Datas_B1 (solo SELECT). */
    private Object[] loadHistory(long spanMs) throws Exception {
        Properties p = new Properties();
        if (sqlConfig != null && Files.isReadable(sqlConfig)) {
            try (InputStream in = Files.newInputStream(sqlConfig)) {
                p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        String url = p.getProperty("jdbc.url", "").trim();
        if (url.isEmpty()) {
            throw new IOException("jdbc.url mancante in " + sqlConfig);
        }
        String pwd = System.getenv("B1_SQL_PASSWORD");
        if (pwd == null) {
            pwd = p.getProperty("jdbc.password", "");
        }
        List<String> cols = new ArrayList<>();
        for (B1TrendChart.Series s : series) {
            if (s.sqlColumn != null) {
                cols.add(s.sqlColumn);
            }
        }
        String sql = historySql("dbo.Datas_B1", cols);
        long fromMs = System.currentTimeMillis() - spanMs;
        List<List<double[]>> out = new ArrayList<>();
        for (int i = 0; i < cols.size(); i++) {
            out.add(new ArrayList<>());
        }
        List<long[]> modes = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(url, p.getProperty("jdbc.user", "").trim(), pwd);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, new java.sql.Date(fromMs - 86_400_000L)); // giorno precedente: il filtro fine e' sull'orario
            try (ResultSet rs = ps.executeQuery()) {
                long lastStart = -1;
                int lastMode = -1;
                long lastT = -1;
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp(1);
                    if (ts == null) {
                        continue;
                    }
                    long t = ts.getTime();
                    if (t < fromMs) {
                        continue;
                    }
                    int mode = isAuto(rs.getString(2)) ? 1 : 0;
                    if (mode != lastMode) {
                        if (lastMode >= 0) {
                            modes.add(new long[]{lastStart, t, lastMode});
                        }
                        lastStart = t;
                        lastMode = mode;
                    }
                    lastT = t;
                    for (int i = 0; i < cols.size(); i++) {
                        Double v = parse(rs.getString(3 + i));
                        if (v != null) {
                            out.get(i).add(new double[]{t, v});
                        }
                    }
                }
                if (lastMode >= 0) {
                    modes.add(new long[]{lastStart, lastT, lastMode});
                }
            }
        }
        return new Object[]{out, modes};
    }

    static boolean isAuto(String m) {
        return m != null && m.trim().toLowerCase(Locale.ROOT).startsWith("auto");
    }

    static Double parse(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
