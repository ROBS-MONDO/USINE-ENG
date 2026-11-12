package Banbury1;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Lista allarmi di B1 con l'aspetto del controllo WinCC: colonne Date, Time, Nombre, Point d'erreur,
 * Texte du message; righe alternate bianco / grigio chiaro (F0F0F0), intestazione azzurra, pagina da 50.
 * Gli allarmi attivi sono in rosso. SOLA LETTURA.
 */
public class B1AlarmWindow extends JFrame {

    private static B1AlarmWindow shared;

    public static void open(B1Source src, B1AlarmEngine engine) {
        SwingUtilities.invokeLater(() -> {
            synchronized (B1AlarmWindow.class) {
                if (shared == null || !shared.isDisplayable()) {
                    shared = new B1AlarmWindow(src, engine);
                    shared.setVisible(true);
                }
                shared.toFront();
            }
        });
    }

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int PAGE = 50;
    // colori dal controllo WinCC (BGR -> RGB): titolo 0xEFE0DE... usiamo l'azzurro chiaro equivalente
    private static final Color TITLE = new Color(0xDE, 0xE0, 0xEF);
    private static final Color ROW2 = new Color(0xF0, 0xF0, 0xF0);

    private final B1Source src;
    private final B1AlarmEngine engine;
    private final Model model = new Model();
    private final JLabel status = new JLabel(" ");

    private B1AlarmWindow(B1Source src, B1AlarmEngine engine) {
        super("Banbury 1 - Allarmi (sola lettura)");
        this.src = src;
        this.engine = engine;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JTable table = new JTable(model);
        table.setFont(new Font("Arial", Font.PLAIN, 14));
        table.setRowHeight(24);
        table.getTableHeader().setFont(new Font("Arial", Font.BOLD, 14));
        table.getTableHeader().setBackground(TITLE);
        table.setGridColor(new Color(0xB0, 0xB0, 0xB0));
        int[] w = {110, 90, 80, 260, 520};
        for (int i = 0; i < w.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
        }
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                B1AlarmEngine.Event e = model.eventAt(r);
                comp.setForeground(e != null && e.active() ? new Color(0xC0, 0x00, 0x00) : Color.BLACK);
                if (!sel) {
                    comp.setBackground(r % 2 == 0 ? Color.WHITE : ROW2);
                }
                return comp;
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        add(status, BorderLayout.SOUTH);
        setSize(1100, 560);
        setLocationRelativeTo(null);
        new Timer(1000, e -> refresh()).start();
        refresh();
    }

    private void refresh() {
        List<B1AlarmEngine.Event> ev = engine.events();
        model.set(ev.size() > PAGE ? ev.subList(0, PAGE) : ev);
        String s;
        if (engine.definitions().isEmpty()) {
            s = "Nessun allarme definito: compilare b1_alarms.properties (servono numero, testo e bit di ogni messaggio)";
        } else if (!src.connected()) {
            s = "PLC non connesso";
        } else {
            s = "Allarmi attivi: " + engine.activeCount() + "   |   definiti: " + engine.definitions().size()
                    + "   |   mostrati gli ultimi " + PAGE + "   |   SOLA LETTURA";
        }
        status.setText(s);
    }

    private static final class Model extends AbstractTableModel {
        private List<B1AlarmEngine.Event> rows = new ArrayList<>();
        private final String[] cols = {"Date", "Time", "Nombre", "Point d'erreur", "Texte du message"};

        void set(List<B1AlarmEngine.Event> r) {
            rows = new ArrayList<>(r);
            fireTableDataChanged();
        }

        B1AlarmEngine.Event eventAt(int r) {
            return r >= 0 && r < rows.size() ? rows.get(r) : null;
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
            B1AlarmEngine.Event e = rows.get(r);
            LocalDateTime t = e.came;
            switch (c) {
                case 0: return t.format(D);
                case 1: return t.format(T);
                case 2: return String.valueOf(e.def.number);
                case 3: return e.def.point;
                default: return e.def.text + (e.active() ? "" : "   (rientrato " + e.went.format(T) + ")");
            }
        }
    }
}
