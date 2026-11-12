package Banbury1;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Valori PLC": tutte le parole dei DB di B1 in tempo reale (SOLA LETTURA), con colonna "Sinottico"
 * (chiave di b1_tags usata per quell'indirizzo) e "WinCC" (schermata + etichetta che usa quell'indirizzo).
 * E' lo strumento per completare la mappa: si guarda quale parola cambia e cosa mostra il WinCC.
 */
public class B1RawViewer extends JFrame {

    private static B1RawViewer shared;

    public static void open(B1Source src, B1Tags tags) {
        SwingUtilities.invokeLater(() -> {
            synchronized (B1RawViewer.class) {
                if (shared == null || !shared.isDisplayable()) {
                    shared = new B1RawViewer(src, tags);
                    shared.setVisible(true);
                }
                shared.toFront();
            }
        });
    }

    private static final int[] DBS = {110, 150, 201};

    private final B1Source src;
    private final B1Tags tags;
    private final B1WinccIndex index = new B1WinccIndex();
    private final JComboBox<Integer> cmbDb = new JComboBox<>(new Integer[]{110, 150, 201});
    private final JCheckBox chkNonZero = new JCheckBox("solo valori diversi da 0");
    private final JCheckBox chkUsed = new JCheckBox("solo indirizzi usati (sinottico o WinCC)");
    private final Model model = new Model();
    private final Map<String, byte[]> prev = new HashMap<>();
    private final Map<String, int[]> changes = new HashMap<>();
    private final Map<String, Long> lastChange = new HashMap<>();
    private final JLabel status = new JLabel(" ");
    private final JTable table = new JTable(model);
    private final Map<Integer, byte[]> captureA = new HashMap<>();
    private long captureATime;

    private B1RawViewer(B1Source src, B1Tags tags) {
        super("Valori PLC B1 (sola lettura)");
        this.src = src;
        this.tags = tags;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel top = new JPanel();
        top.add(new JLabel("DB:"));
        top.add(cmbDb);
        top.add(chkNonZero);
        top.add(chkUsed);
        top.add(new JLabel("   righe gialle = cambiate negli ultimi 5 s   "));
        JButton btnA = new JButton("Cattura A");
        btnA.setToolTipText("Memorizza lo stato attuale di DB110, DB150 e DB201 (es. pistone in alto, porte chiuse)");
        btnA.addActionListener(e -> capture());
        JButton btnB = new JButton("Cattura B + differenze");
        btnB.setToolTipText("Confronta lo stato attuale con la cattura A: elenca i bit cambiati");
        btnB.addActionListener(e -> compare());
        top.add(btnA);
        top.add(btnB);
        add(top, BorderLayout.NORTH);
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.setRowHeight(20);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(60);
        table.getColumnModel().getColumn(2).setPreferredWidth(60);
        table.getColumnModel().getColumn(3).setPreferredWidth(170);
        table.getColumnModel().getColumn(4).setPreferredWidth(40);
        table.getColumnModel().getColumn(5).setPreferredWidth(40);
        table.getColumnModel().getColumn(6).setPreferredWidth(210);
        table.getColumnModel().getColumn(7).setPreferredWidth(420);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                if (!sel) {
                    Long ts = lastChange.get(model.key(r));
                    boolean recent = ts != null && System.currentTimeMillis() - ts < 5000;
                    comp.setBackground(recent ? new Color(0xFFF2A0) : Color.WHITE);
                }
                return comp;
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        add(status, BorderLayout.SOUTH);
        setSize(1250, 700);
        setLocationRelativeTo(null);
        cmbDb.addActionListener(e -> refresh());
        chkNonZero.addActionListener(e -> refresh());
        chkUsed.addActionListener(e -> refresh());
        new Timer(500, e -> refresh()).start();
        refresh();
    }

    /** Memorizza lo stato attuale dei DB di B1 (cattura A). */
    private void capture() {
        captureA.clear();
        for (int db : DBS) {
            byte[] d = src.dbImage(db);
            if (d != null) {
                captureA.put(db, d.clone());
            }
        }
        captureATime = System.currentTimeMillis();
        status.setText(captureA.isEmpty() ? "Cattura A non riuscita: PLC non letto"
                : "Cattura A memorizzata alle " + java.time.LocalTime.now().withNano(0)
                + ". Ora cambiate lo stato della macchina e premete 'Cattura B + differenze'.");
    }

    /** Confronta lo stato attuale con la cattura A e mostra i bit cambiati con le etichette del WinCC. */
    private void compare() {
        if (captureA.isEmpty()) {
            status.setText("Prima premere 'Cattura A'");
            return;
        }
        List<String> stable = new ArrayList<>();
        List<String> noisy = new ArrayList<>();
        for (int db : DBS) {
            byte[] b = src.dbImage(db);
            for (B1BitDiff.Change c : B1BitDiff.diff(db, captureA.get(db), b)) {
                String line = c.describe();
                String syn = usedBySynoptic(db, c.off);
                String wc = index.usageOfWord(db, c.off);
                if (!syn.isEmpty()) {
                    line += "\n      sinottico B1: " + syn;
                }
                if (!wc.isEmpty()) {
                    line += "\n      WinCC: " + wc;
                }
                int n = changes.getOrDefault("DB" + db + "." + c.off, new int[1])[0];
                if (n >= 10) {
                    noisy.add(line + "\n      (cambia di continuo: contatore o valore analogico, probabilmente non e' una lampada)");
                } else {
                    stable.add(line);
                }
            }
        }
        StringBuilder sb = new StringBuilder("Cattura A: " + new java.text.SimpleDateFormat("HH:mm:ss").format(
                new java.util.Date(captureATime)) + "   ->   adesso: "
                + java.time.LocalTime.now().withNano(0) + "\n\n");
        sb.append(stable.isEmpty() ? "Nessun bit di stato cambiato.\n" : "BIT CAMBIATI (candidati lampade):\n\n");
        for (String l : stable) {
            sb.append("  ").append(l).append("\n\n");
        }
        if (!noisy.isEmpty()) {
            sb.append("\nVALORI CHE CAMBIANO DI CONTINUO (da ignorare per le lampade):\n\n");
            for (String l : noisy) {
                sb.append("  ").append(l).append("\n\n");
            }
        }
        sb.append("\nPer assegnare una lampada: nel sinottico originale annotate quale lampada si e' accesa/spenta "
                + "tra A e B e scrivete in b1_tags.properties, ad es.  piston_low = DB110.W22.B6");
        javax.swing.JTextArea area = new javax.swing.JTextArea(sb.toString(), 26, 100);
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        area.setCaretPosition(0);
        javax.swing.JOptionPane.showMessageDialog(this, new JScrollPane(area), "Differenze tra A e B",
                javax.swing.JOptionPane.PLAIN_MESSAGE);
    }

    private void refresh() {
        int db = (Integer) cmbDb.getSelectedItem();
        byte[] d = src.dbImage(db);
        if (d == null) {
            status.setText(src.connected() ? "DB" + db + " non letto dal PLC" : "PLC non connesso: " + src.status());
            model.set(db, null);
            return;
        }
        byte[] p = prev.get("DB" + db);
        long now = System.currentTimeMillis();
        for (int off = 0; off + 1 < d.length; off += 2) {
            String k = "DB" + db + "." + off;
            if (p != null && (p[off] != d[off] || p[off + 1] != d[off + 1])) {
                changes.computeIfAbsent(k, x -> new int[1])[0]++;
                lastChange.put(k, now);
            }
        }
        prev.put("DB" + db, d.clone());
        status.setText("DB" + db + ": " + d.length + " byte  |  " + src.status());
        model.set(db, d);
    }

    private String usedBySynoptic(int db, int off) {
        List<String> keys = new ArrayList<>();
        for (String k : B1Tags.keysOf(tags)) {
            B1Tags.Tag t = tags.get(k);
            if (t == null || t.db != db) {
                continue;
            }
            boolean hit = t.kind == B1Tags.Kind.BIT ? (t.off == off || t.off == off + 1)
                    : (t.kind == B1Tags.Kind.STRING ? (off >= t.off && off < t.off + 34) : t.off == off);
            if (hit) {
                keys.add(k + (t.kind == B1Tags.Kind.BIT ? ".b" + (t.bit + (t.off == off ? 8 : 0)) : ""));
            }
        }
        return String.join(", ", keys);
    }

    private final class Model extends AbstractTableModel {
        private int db;
        private byte[] d;
        private List<Integer> rows = new ArrayList<>();
        private final String[] cols = {"Indirizzo", "Dec", "Hex", "Bit 15..0", "Asc", "Var.", "Sinottico", "WinCC (schermata: etichetta)"};

        void set(int db, byte[] d) {
            this.db = db;
            this.d = d;
            rows = new ArrayList<>();
            if (d != null) {
                for (int off = 0; off + 1 < d.length; off += 2) {
                    int v = S7ReadClient.int16(d, off);
                    boolean used = !usedBySynoptic(db, off).isEmpty() || !index.usageOfWord(db, off).isEmpty();
                    if ((chkNonZero.isSelected() && v == 0) || (chkUsed.isSelected() && !used)) {
                        continue;
                    }
                    rows.add(off);
                }
            }
            fireTableDataChanged();
        }

        String key(int row) {
            return row < rows.size() ? "DB" + db + "." + rows.get(row) : "";
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int c) {
            return cols[c];
        }

        @Override
        public Object getValueAt(int r, int c) {
            int off = rows.get(r);
            int v = S7ReadClient.int16(d, off);
            switch (c) {
                case 0: return "DBW" + off;
                case 1: return String.valueOf(v);
                case 2: return String.format("%04X", v & 0xFFFF);
                case 3: {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 15; i >= 0; i--) {
                        sb.append(((v >> i) & 1) != 0 ? '1' : '.');
                        if (i % 4 == 0 && i > 0) {
                            sb.append(' ');
                        }
                    }
                    return sb.toString();
                }
                case 4: {
                    char a = (char) (d[off] & 0xFF), b = (char) (d[off + 1] & 0xFF);
                    return "" + (a >= 32 && a < 127 ? a : '.') + (b >= 32 && b < 127 ? b : '.');
                }
                case 5: {
                    int[] n = changes.get("DB" + db + "." + off);
                    return n == null ? "" : String.valueOf(n[0]);
                }
                case 6: return usedBySynoptic(db, off);
                default: return index.usageOfWord(db, off);
            }
        }
    }
}
