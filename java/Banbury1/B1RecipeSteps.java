package Banbury1;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

/**
 * Editeur de recettes B1 "par STEP", dans le style de MondoLuxRecipes (B2): liste des recettes a gauche, un onglet par STEP
 * (phase) avec un tableau Parametre / Valeur, listes deroulantes pour operations, porte et piston. La logique (archive, validation,
 * import du PLC, envoi protege) est celle de l'editeur de recettes (EditorPanel, invisible). Aucune ecriture directe dans le PLC.
 */
public final class B1RecipeSteps extends JFrame {

    static final String VERSION = "B1 Recettes - STEPS V0.1";
    private static B1RecipeSteps shared;

    private static final Color BG = new Color(236, 236, 236);
    private static final Font F18 = new Font("SansSerif", Font.PLAIN, 18);
    private static final Font F18B = new Font("SansSerif", Font.BOLD, 18);

    private final B1RecipeEditor.EditorPanel ed;
    private final DefaultListModel<B1RecipeRepository.Entry> listModel = new DefaultListModel<>();
    private final JList<B1RecipeRepository.Entry> list = new JList<>(listModel);
    private final JTextField txtName = new JTextField();
    private final JTextField txtNo = new JTextField();
    private final JLabel lblInfo = new JLabel(" ");
    private final JLabel lblStatus = new JLabel(" ");
    private final JTabbedPane tabs = new JTabbedPane();
    private B1Recipe shown;
    private boolean building;

    private static final class Opt {
        final int value;
        final String label;

        Opt(int value, String label) {
            this.value = value;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final Opt[] YESNO = {new Opt(0, "0 - Non"), new Opt(1, "1 - Oui")};
    private static final Opt[] DOOR = {new Opt(0, "0 - Ferm\u00e9e"), new Opt(1, "1 - Ouverte")};
    private static final Opt[] PISTON = {new Opt(0, "0 - Libre"), new Opt(1, "1 - Haut"), new Opt(2, "2 - Bas")};

    /** Une ligne du tableau d'un STEP. */
    private static final class Row {
        final String label;
        final String kind; // op, num, door, piston, skip, and, or
        final int bit;
        final String key;

        Row(String label, String kind, int bit, String key) {
            this.label = label;
            this.kind = kind;
            this.bit = bit;
            this.key = key;
        }
    }

    private static final String[] HEAD_KEYS = {"safety_time", "max_energy", "restart_temp", "safety_temp", "drop_door"};
    private static final String[] HEAD_LABELS = {"Temps de s\u00e9curit\u00e9 (s)", "\u00c9nergie max (kWh)", "Temp. de red\u00e9marrage (\u00b0C)",
        "Temp. de s\u00e9curit\u00e9 (\u00b0C)", "Porte de d\u00e9chargement (s)"};

    private List<Row> stepRows() {
        List<Row> r = new ArrayList<>();
        for (int i = 0; i < B1RecipePage.OP_NAMES.length; i++) {
            r.add(new Row("Op\u00e9ration - " + B1RecipePage.OP_NAMES[i], "op", B1RecipePage.OP_BITS[i], null));
        }
        r.add(new Row("Timeout (s)", "num", 0, "timeout"));
        r.add(new Row("Rotor (rpm)", "num", 0, "rpm"));
        r.add(new Row("Pression (bar)", "num", 0, "press"));
        r.add(new Row("Porte chargement", "door", 0, null));
        r.add(new Row("Piston", "piston", 0, null));
        r.add(new Row("Saut", "skip", 0, null));
        r.add(new Row("Temps (s)", "num", 0, "time"));
        r.add(new Row("Temp\u00e9rature (\u00b0C)", "num", 0, "temp"));
        r.add(new Row("\u00c9nergie (kWh)", "num", 0, "energy"));
        r.add(new Row("ET", "and", 0, null));
        r.add(new Row("OU", "or", 0, null));
        return r;
    }

    public B1RecipeSteps(B1RecipeRepository repo, B1RecipeLayout layout, String plcIp) {
        super(VERSION);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        ed = new B1RecipeEditor.EditorPanel(null, repo, layout, plcIp);
        setContentPane(build());
        setSize(1180, 800);
        setLocationRelativeTo(null);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                synchronized (B1RecipeSteps.class) {
                    shared = null;
                }
            }
        });
        refreshList();
        // l'archive, l'import du PLC (asynchrone) et l'editeur invisible changent la recette: on suit la recette courante
        new Timer(300, e -> sync()).start();
        ed.importNow();
    }

    /** Ouvre (ou met au premier plan) la fenetre; avec name, selectionne la recette en archive. */
    public static void openShared(B1RecipeRepository repo, B1RecipeLayout layout, String plcIp, String name) {
        SwingUtilities.invokeLater(() -> {
            synchronized (B1RecipeSteps.class) {
                if (shared == null || !shared.isDisplayable()) {
                    shared = new B1RecipeSteps(repo, layout, plcIp);
                    shared.setVisible(true);
                } else if (name == null || name.trim().isEmpty()) {
                    shared.ed.importNow();
                }
                shared.toFront();
                if (name != null && !name.trim().isEmpty()) {
                    shared.ed.selectByName(name);
                }
            }
        });
    }

    // ---------------------------------------------------------------- interface

    private JPanel build() {
        JPanel root = new JPanel(new BorderLayout(10, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(205, 205, 205));
                for (int y = 0; y < getHeight(); y += 10) {
                    for (int x = 0; x < getWidth(); x += 10) {
                        g2.fillRect(x, y, 1, 1);
                    }
                }
                g2.dispose();
            }
        };
        root.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        JLabel title = new JLabel("Banbury 1 - Recettes par STEP", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 30));
        root.add(title, BorderLayout.NORTH);

        // gauche: liste + boutons
        JPanel left = new JPanel(new BorderLayout(5, 5));
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(300, 600));
        JLabel lr = new JLabel("Recettes");
        lr.setFont(new Font("SansSerif", Font.BOLD, 20));
        left.add(lr, BorderLayout.NORTH);
        list.setFont(F18);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        left.add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel lb = new JPanel(new GridLayout(0, 1, 5, 5));
        lb.setOpaque(false);
        lb.add(button("Actualiser la liste", this::refreshList));
        lb.add(button("Ouvrir", this::openSelected));
        lb.add(button("Importer du PLC", ed::importFromPlc));
        lb.add(button("Nouvelle", ed::newRecipe));
        lb.add(button("Dupliquer", ed::duplicate));
        lb.add(button("Supprimer", () -> {
            ed.deleteRecipe();
            refreshList();
        }));
        lb.add(button("Enregistrer", () -> {
            ed.save();
            refreshList();
        }));
        JButton send = button("ENVOYER AU PLC", ed::sendToPlc);
        send.setBackground(new Color(255, 230, 120));
        lb.add(send);
        left.add(lb, BorderLayout.SOUTH);

        // droite: identification + onglets
        JPanel right = new JPanel(new BorderLayout(10, 10));
        right.setOpaque(false);
        JPanel info = new JPanel(new GridLayout(3, 2, 8, 8));
        info.setOpaque(false);
        info.add(label("N\u00b0 Recette :"));
        info.add(txtNo);
        info.add(label("Description :"));
        info.add(txtName);
        info.add(label("\u00c9tat :"));
        info.add(lblInfo);
        txtNo.setFont(F18);
        txtName.setFont(F18);
        lblInfo.setFont(new Font("SansSerif", Font.PLAIN, 14));
        txtNo.addActionListener(e -> headerEdited());
        txtName.addActionListener(e -> headerEdited());
        txtNo.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                headerEdited();
            }
        });
        txtName.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                headerEdited();
            }
        });
        right.add(info, BorderLayout.NORTH);
        tabs.setFont(new Font("SansSerif", Font.BOLD, 20));
        tabs.setBackground(new Color(225, 225, 225));
        tabs.setBorder(BorderFactory.createLineBorder(new Color(90, 90, 90), 3));
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.setUI(new StepTabbedPaneUI());
        right.add(tabs, BorderLayout.CENTER);
        lblStatus.setFont(new Font("SansSerif", Font.PLAIN, 14));
        right.add(lblStatus, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout(10, 10));
        center.setOpaque(false);
        center.add(left, BorderLayout.WEST);
        center.add(right, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);
        return root;
    }

    private static JLabel label(String t) {
        JLabel l = new JLabel(t);
        l.setFont(F18B);
        return l;
    }

    private JButton button(String t, Runnable r) {
        JButton b = new JButton(t);
        b.setFont(new Font("SansSerif", Font.BOLD, 16));
        b.addActionListener(e -> {
            r.run();
            sync();
        });
        return b;
    }

    private void refreshList() {
        ed.refreshList();
        listModel.clear();
        for (B1RecipeRepository.Entry e : ed.allEntries) {
            listModel.addElement(e);
        }
    }

    private void openSelected() {
        B1RecipeRepository.Entry e = list.getSelectedValue();
        if (e != null) {
            ed.openEntry(e);
        }
    }

    private void headerEdited() {
        B1Recipe r = ed.current;
        if (building || r == null) {
            return;
        }
        String n = txtName.getText().trim().replace('\u00b0', 'o');
        int no = r.recipeNo;
        try {
            no = Integer.parseInt(txtNo.getText().trim());
        } catch (NumberFormatException ignored) {
            txtNo.setText(String.valueOf(r.recipeNo));
        }
        if (!n.equals(r.name) || no != r.recipeNo) {
            r.name = n;
            r.recipeNo = no;
            ed.edited();
        }
    }

    /** Suit la recette de l'editeur invisible: reconstruit les onglets quand elle change. */
    private void sync() {
        B1Recipe r = ed.current;
        if (r != shown) {
            shown = r;
            rebuildTabs();
        }
        if (r != null) {
            lblInfo.setText(ed.lblInfo.getText() + (ed.dirty ? "  [modifications non enregistr\u00e9es]" : ""));
        }
        String p = ed.problemsText();
        lblStatus.setText(p == null ? " " : p.replace("\n", "  |  "));
        lblStatus.setForeground(p != null && p.startsWith("Recette valide") ? new Color(0, 110, 0) : new Color(160, 80, 0));
    }

    private void rebuildTabs() {
        building = true;
        int sel = tabs.getSelectedIndex();
        tabs.removeAll();
        B1Recipe r = shown;
        if (r != null) {
            txtName.setText(B1RecipeBridge.pretty(r.name));
            txtNo.setText(String.valueOf(r.recipeNo));
            tabs.addTab("ENT\u00caTE", panelFor(headerModel(r)));
            for (int i = 0; i < r.phases.size(); i++) {
                tabs.addTab("STEP " + (i + 1), panelFor(stepModel(r, r.phases.get(i), i)));
            }
            if (sel >= 0 && sel < tabs.getTabCount()) {
                tabs.setSelectedIndex(sel);
            }
        } else {
            txtName.setText("");
            txtNo.setText("");
        }
        building = false;
    }

    // ---------------------------------------------------------------- tableaux

    private B1RecipeLayout.Field hf(String key) {
        for (B1RecipeLayout.Field f : ed.layout.header) {
            if (f.key.equals(key)) {
                return f;
            }
        }
        return null;
    }

    private B1RecipeLayout.Field pf(String key) {
        for (B1RecipeLayout.Field f : ed.layout.phase) {
            if (f.key.equals(key)) {
                return f;
            }
        }
        return null;
    }

    private static int toInt(Object o) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean numeric(String s) {
        try {
            Double.parseDouble(s.trim().replace(',', '.'));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private AbstractTableModel headerModel(B1Recipe r) {
        return new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return HEAD_KEYS.length;
            }

            @Override
            public int getColumnCount() {
                return 2;
            }

            @Override
            public String getColumnName(int c) {
                return c == 0 ? "Param\u00e8tre" : "Valeur";
            }

            @Override
            public boolean isCellEditable(int row, int col) {
                return col == 1 && hf(HEAD_KEYS[row]) != null;
            }

            @Override
            public Object getValueAt(int row, int col) {
                if (col == 0) {
                    return HEAD_LABELS[row];
                }
                B1RecipeLayout.Field f = hf(HEAD_KEYS[row]);
                return f == null ? "" : B1RecipeEditor.EditorPanel.fmt(r.header.getOrDefault(f.key, 0), f);
            }

            @Override
            public void setValueAt(Object v, int row, int col) {
                B1RecipeLayout.Field f = hf(HEAD_KEYS[row]);
                if (col == 1 && f != null && numeric(String.valueOf(v))) {
                    r.header.put(f.key, B1RecipeEditor.EditorPanel.toRaw(String.valueOf(v), f));
                    ed.edited();
                    fireTableRowsUpdated(row, row);
                }
            }
        };
    }

    private AbstractTableModel stepModel(B1Recipe r, B1Recipe.Phase p, int idx) {
        final List<Row> rows = stepRows();
        return new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return rows.size();
            }

            @Override
            public int getColumnCount() {
                return 2;
            }

            @Override
            public String getColumnName(int c) {
                return c == 0 ? "Param\u00e8tre" : "Valeur";
            }

            @Override
            public boolean isCellEditable(int row, int col) {
                return col == 1;
            }

            private int word(String key) {
                Integer v = p.values.get(key);
                return v == null ? 0 : v & 0xFFFF;
            }

            @Override
            public Object getValueAt(int row, int col) {
                Row w = rows.get(row);
                if (col == 0) {
                    return w.label;
                }
                switch (w.kind) {
                    case "op": return (p.ops & (1 << w.bit)) != 0 ? 1 : 0;
                    case "door": return (word("set_bits") & B1RecipePage.BIT_DOOR) != 0 ? 1 : 0;
                    case "piston": {
                        int b = word("set_bits");
                        return (b & B1RecipePage.BIT_UP) != 0 ? 1 : (b & B1RecipePage.BIT_DOWN) != 0 ? 2 : 0;
                    }
                    case "skip": return (word("ctrl_bits") & B1RecipePage.CT_SKIP) != 0 ? 1 : 0;
                    case "and": return (word("ctrl_bits") & B1RecipePage.CT_AND) != 0 ? 1 : 0;
                    case "or": return (word("ctrl_bits") & B1RecipePage.CT_OR) != 0 ? 1 : 0;
                    default: {
                        B1RecipeLayout.Field f = pf(w.key);
                        return f == null ? "" : B1RecipeEditor.EditorPanel.fmt(p.values.getOrDefault(w.key, 0), f);
                    }
                }
            }

            private void bit(String key, int mask, boolean on) {
                int v = word(key);
                p.values.put(key, on ? v | mask : v & ~mask);
            }

            @Override
            public void setValueAt(Object val, int row, int col) {
                if (col != 1) {
                    return;
                }
                Row w = rows.get(row);
                boolean on = toInt(val) != 0;
                switch (w.kind) {
                    case "op":
                        p.ops = on ? p.ops | (1 << w.bit) : p.ops & ~(1 << w.bit);
                        break;
                    case "door": bit("set_bits", B1RecipePage.BIT_DOOR, on); break;
                    case "piston": {
                        int v = toInt(val);
                        bit("set_bits", B1RecipePage.BIT_UP, v == 1);
                        bit("set_bits", B1RecipePage.BIT_DOWN, v == 2);
                        break;
                    }
                    case "skip": bit("ctrl_bits", B1RecipePage.CT_SKIP, on); break;
                    case "and": bit("ctrl_bits", B1RecipePage.CT_AND, on); break;
                    case "or": bit("ctrl_bits", B1RecipePage.CT_OR, on); break;
                    default: {
                        B1RecipeLayout.Field f = pf(w.key);
                        if (f == null || !numeric(String.valueOf(val))) {
                            return;
                        }
                        p.values.put(w.key, B1RecipeEditor.EditorPanel.toRaw(String.valueOf(val), f));
                    }
                }
                ed.edited();
                fireTableRowsUpdated(row, row);
            }
        };
    }

    private static Opt[] optionsFor(String kind) {
        switch (kind) {
            case "op": case "skip": case "and": case "or": return YESNO;
            case "door": return DOOR;
            case "piston": return PISTON;
            default: return null;
        }
    }

    private JPanel panelFor(AbstractTableModel model) {
        JTable table = new JTable(model) {
            private String kindAt(int row) {
                if (model.getRowCount() == HEAD_KEYS.length && model.getValueAt(0, 0).equals(HEAD_LABELS[0])) {
                    return "num";
                }
                return stepRows().get(row).kind;
            }

            @Override
            public TableCellRenderer getCellRenderer(int row, int col) {
                Opt[] o = col == 1 ? optionsFor(kindAt(row)) : null;
                if (o != null) {
                    return new DefaultTableCellRenderer() {
                        @Override
                        public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                            super.getTableCellRendererComponent(t, v, s, f, r, c);
                            int n = toInt(v);
                            setText(n >= 0 && n < o.length ? o[n].label : n + " - ?");
                            setHorizontalAlignment(SwingConstants.LEFT);
                            return this;
                        }
                    };
                }
                return super.getCellRenderer(row, col);
            }

            @Override
            public TableCellEditor getCellEditor(int row, int col) {
                Opt[] o = col == 1 ? optionsFor(kindAt(row)) : null;
                return o != null ? new ComboEditor(o) : super.getCellEditor(row, col);
            }
        };
        table.setRowHeight(32);
        table.setFont(F18);
        table.getTableHeader().setFont(F18B);
        table.setGridColor(new Color(120, 120, 120));
        table.setShowGrid(true);
        table.setIntercellSpacing(new Dimension(1, 1));
        table.getColumnModel().getColumn(0).setPreferredWidth(320);
        table.getColumnModel().getColumn(1).setPreferredWidth(320);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(90, 90, 90), 3),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(new Color(120, 120, 120), 2));
        panel.add(sp, BorderLayout.CENTER);
        return panel;
    }

    private static final class ComboEditor extends AbstractCellEditor implements TableCellEditor {
        private final JComboBox<Opt> box;

        ComboEditor(Opt[] options) {
            box = new JComboBox<>(options);
            box.setFont(F18);
            box.addActionListener(e -> stopCellEditing());
        }

        @Override
        public Object getCellEditorValue() {
            Opt o = (Opt) box.getSelectedItem();
            return o == null ? 0 : o.value;
        }

        @Override
        public Component getTableCellEditorComponent(JTable t, Object v, boolean s, int r, int c) {
            int n = toInt(v);
            box.setSelectedIndex(n >= 0 && n < box.getItemCount() ? n : 0);
            return box;
        }
    }

    /** Onglets dans le style MondoLuxRecipes (B2). */
    private static final class StepTabbedPaneUI extends BasicTabbedPaneUI {
        private final Color border = new Color(70, 70, 70);

        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabAreaInsets = new Insets(8, 8, 0, 8);
            contentBorderInsets = new Insets(4, 4, 4, 4);
            selectedTabPadInsets = new Insets(2, 2, 2, 2);
        }

        @Override
        protected void paintTabBackground(Graphics g, int tp, int i, int x, int y, int w, int h, boolean sel) {
            g.setColor(sel ? Color.WHITE : new Color(210, 210, 210));
            g.fillRect(x, y, w, h);
        }

        @Override
        protected void paintTabBorder(Graphics g, int tp, int i, int x, int y, int w, int h, boolean sel) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(border);
            g2.setStroke(new BasicStroke(sel ? 3f : 2f));
            g2.drawRect(x, y, w - 1, h - 1);
            g2.dispose();
        }

        @Override
        protected void paintFocusIndicator(Graphics g, int tp, Rectangle[] rects, int i, Rectangle ir, Rectangle tr, boolean sel) {
        }

        @Override
        protected void paintText(Graphics g, int tp, Font font, FontMetrics m, int i, String title, Rectangle tr, boolean sel) {
            Graphics2D g2 = (Graphics2D) g.create();
            Font f = font.deriveFont(Font.BOLD, 18f);
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics(f);
            g2.setColor(Color.BLACK);
            int ty = tr.y + fm.getAscent();
            g2.drawString(title, tr.x, ty);
            if (sel) {
                g2.drawLine(tr.x, ty + 4, tr.x + fm.stringWidth(title), ty + 4);
            }
            g2.dispose();
        }

        @Override
        protected int calculateTabHeight(int tp, int i, int fh) {
            return Math.max(44, super.calculateTabHeight(tp, i, fh) + 10);
        }

        @Override
        protected int calculateTabWidth(int tp, int i, FontMetrics m) {
            return super.calculateTabWidth(tp, i, m) + 20;
        }
    }
}
