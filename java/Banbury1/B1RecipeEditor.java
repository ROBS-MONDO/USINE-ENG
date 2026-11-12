package Banbury1;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.imageio.ImageIO;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Editor ricette Banbury 1 per postazione ufficio (come mondoluxrecipes per B2).
 * Crea/modifica ricette nell'archivio condiviso; puo' IMPORTARE la ricetta in macchina
 * dal PLC (DB201, sola lettura). L'invio al PLC NON e' ancora abilitato.
 *
 * Uso: java Banbury1.B1RecipeEditor [--dir cartella] [--layout file] [--plc ip] [--snapshot out.png]
 */
public class B1RecipeEditor extends JFrame {

    static final String VERSION = "B1 Ricette V0.1";
    static final Path DEFAULT_DIR = Paths.get("C:\\_tools\\Banbury1\\Recipes");
    static final Path DEFAULT_LAYOUT = Paths.get("C:\\_tools\\Banbury1\\b1_recipe_layout.properties");
    static final String DEFAULT_PLC = "192.168.10.100";

    private final EditorPanel panel;

    public B1RecipeEditor(B1RecipeRepository repo, B1RecipeLayout layout, String plcIp) {
        super(VERSION);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        panel = new EditorPanel(this, repo, layout, plcIp);
        setContentPane(panel);
        setSize(1180, 760);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (panel.confirmDiscard()) {
                    dispose();
                    System.exit(0);
                }
            }
        });
    }

    public static void main(String[] args) throws Exception {
        Path dir = DEFAULT_DIR;
        Path layoutFile = DEFAULT_LAYOUT;
        String plc = DEFAULT_PLC;
        String snap = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--dir": dir = Paths.get(args[++i]); break;
                case "--layout": layoutFile = Paths.get(args[++i]); break;
                case "--plc": plc = args[++i]; break;
                case "--snapshot": snap = args[++i]; break;
                case "--write-layout":
                    B1RecipeLayout.defaults().writeTemplate(Paths.get(args[++i]));
                    return;
                default:
            }
        }
        if (!Files.exists(layoutFile) && Files.exists(Paths.get("b1_recipe_layout.properties"))) {
            layoutFile = Paths.get("b1_recipe_layout.properties");
        }
        if (!Files.isDirectory(dir) && !System.getProperty("os.name", "").toLowerCase().contains("win")) {
            dir = Paths.get("b1_recipes");
        }
        final B1RecipeLayout layout = B1RecipeLayout.load(layoutFile);
        final B1RecipeRepository repo = new B1FileRecipeRepository(dir, layout);
        if (snap != null) {
            System.setProperty("java.awt.headless", "true");
            snapshot(repo, layout, snap);
            System.exit(0);
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        final String plcIp = plc;
        SwingUtilities.invokeLater(() -> new B1RecipeEditor(repo, layout, plcIp).setVisible(true));
    }

    /** Immagine dell'editor con una ricetta di esempio (verifica grafica senza schermo). */
    private static void snapshot(B1RecipeRepository repo, B1RecipeLayout layout, String out) throws Exception {
        EditorPanel p = new EditorPanel(null, repo, layout, DEFAULT_PLC);
        B1Recipe r = B1Recipe.blank(layout);
        r.name = "gomma2";
        r.recipeNo = 24;
        int[][] ph = {{18, 36, 900, 1, 80, 2}, {18, 33, 900, 1, 90, 2}, {18, 33, 700, 1, 105, 10}, {0, 33, 0, 8, 0, 0}};
        String[] keys = {"p0", "rpm", "p4", "p10", "temp", "p22"};
        int[] ops = {0x3000, 0x3000, 0x8000, 0};
        for (int i = 0; i < ph.length; i++) {
            for (int k = 0; k < keys.length; k++) {
                if (r.phases.get(i).values.containsKey(keys[k])) {
                    r.phases.get(i).values.put(keys[k], ph[i][k]);
                }
            }
            r.phases.get(i).ops = ops[i];
        }
        r.header.put("safety_temp", 115);
        r.header.put("restart_temp", 80);
        r.header.put("safety_time", 600);
        r.header.put("max_energy", 300);
        p.show(r, false, "Ricetta di esempio (dati del dump del 12/11/2026)");
        p.setSize(1180, 760);
        p.addNotify();
        layoutAll(p);
        BufferedImage img = new BufferedImage(1180, 760, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        p.paint(g);
        g.dispose();
        ImageIO.write(img, "png", new File(out));
    }

    private static void layoutAll(Component c) {
        if (c instanceof java.awt.Container) {
            java.awt.Container k = (java.awt.Container) c;
            k.doLayout();
            for (Component ch : k.getComponents()) {
                layoutAll(ch);
            }
        }
    }

    // =====================================================================
    static final class EditorPanel extends JPanel {
        private static final Color BG = new Color(0xD8D8D8);
        private static final Color HEAD = new Color(0x3A4252);
        private static final Font F = new Font("Arial", Font.PLAIN, 13);
        private static final Font FB = new Font("Arial", Font.BOLD, 13);

        private final JFrame owner;
        private final B1RecipeRepository repo;
        private final B1RecipeLayout layout;
        private final String plcIp;
        private final String user = System.getProperty("user.name", "?");

        private final DefaultListModel<B1RecipeRepository.Entry> listModel = new DefaultListModel<>();
        private final JList<B1RecipeRepository.Entry> list = new JList<>(listModel);
        private final JTextField search = new JTextField();
        private final JTextField txtName = new JTextField(24);
        private final JTextField txtNo = new JTextField(5);
        private final Map<String, JTextField> headerFields = new java.util.LinkedHashMap<>();
        private final JLabel lblInfo = new JLabel(" ");
        private final JTextArea problems = new JTextArea(4, 40);
        private final JLabel lblStatus = new JLabel(" ");
        private final PhaseModel model;
        private final JTable table;

        private B1Recipe current;
        private boolean dirty;
        private boolean loading;
        private List<B1RecipeRepository.Entry> allEntries = new ArrayList<>();

        EditorPanel(JFrame owner, B1RecipeRepository repo, B1RecipeLayout layout, String plcIp) {
            super(new BorderLayout(8, 8));
            this.owner = owner;
            this.repo = repo;
            this.layout = layout;
            this.plcIp = plcIp;
            setBackground(BG);
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

            model = new PhaseModel();
            table = new JTable(model);
            buildLeft();
            buildCenter();
            buildBottom();
            refreshList();
            show(B1Recipe.blank(layout), false, "Nuova ricetta");

            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK), "save");
            getActionMap().put("save", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    save();
                }
            });
        }

        // ---------------- costruzione UI ----------------
        private JButton button(String text, Runnable r) {
            JButton b = new JButton(text);
            b.setFont(F);
            b.addActionListener(e -> r.run());
            return b;
        }

        private void buildLeft() {
            JPanel left = new JPanel(new BorderLayout(4, 4));
            left.setOpaque(false);
            left.setPreferredSize(new Dimension(270, 0));
            JLabel t = new JLabel("  RICETTE B1", JLabel.LEFT);
            t.setOpaque(true);
            t.setBackground(HEAD);
            t.setForeground(Color.WHITE);
            t.setFont(FB);
            t.setPreferredSize(new Dimension(0, 28));
            left.add(t, BorderLayout.NORTH);
            JPanel mid = new JPanel(new BorderLayout(4, 4));
            mid.setOpaque(false);
            search.setFont(F);
            search.setToolTipText("Cerca per nome");
            search.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { applyFilter(); }
                public void removeUpdate(DocumentEvent e) { applyFilter(); }
                public void changedUpdate(DocumentEvent e) { applyFilter(); }
            });
            mid.add(search, BorderLayout.NORTH);
            list.setFont(F);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting() && !loading && list.getSelectedValue() != null) {
                    openEntry(list.getSelectedValue());
                }
            });
            mid.add(new JScrollPane(list), BorderLayout.CENTER);
            left.add(mid, BorderLayout.CENTER);
            JPanel btns = new JPanel(new java.awt.GridLayout(2, 2, 4, 4));
            btns.setOpaque(false);
            btns.add(button("Nuova", this::newRecipe));
            btns.add(button("Duplica", this::duplicate));
            btns.add(button("Elimina", this::deleteRecipe));
            btns.add(button("Aggiorna", this::refreshList));
            left.add(btns, BorderLayout.SOUTH);
            add(left, BorderLayout.WEST);
        }

        private void buildCenter() {
            JPanel center = new JPanel(new BorderLayout(6, 6));
            center.setOpaque(false);

            JPanel head = new JPanel(new GridBagLayout());
            head.setBackground(new Color(0xE6E6E6));
            head.setBorder(BorderFactory.createTitledBorder("Testata ricetta"));
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(3, 6, 3, 6);
            c.anchor = GridBagConstraints.WEST;
            c.gridx = 0; c.gridy = 0;
            head.add(lbl("Nome ricetta"), c);
            txtName.setFont(new Font("Arial", Font.BOLD, 15));
            c.gridx = 1; c.gridwidth = 2;
            head.add(txtName, c);
            c.gridwidth = 1;
            c.gridx = 3;
            head.add(lbl("N. ricetta"), c);
            txtNo.setFont(F);
            c.gridx = 4;
            head.add(txtNo, c);
            int col = 0, row = 1;
            for (B1RecipeLayout.Field f : layout.header) {
                JTextField tf = new JTextField(6);
                tf.setFont(F);
                tf.setHorizontalAlignment(JTextField.RIGHT);
                headerFields.put(f.key, tf);
                c.gridx = col * 2; c.gridy = row; c.gridwidth = 1;
                head.add(lbl(f.title()), c);
                c.gridx = col * 2 + 1;
                head.add(tf, c);
                tf.getDocument().addDocumentListener(simple(() -> onHeaderEdited()));
                if (++col == 3) {
                    col = 0;
                    row++;
                }
            }
            txtName.getDocument().addDocumentListener(simple(() -> onHeaderEdited()));
            txtNo.getDocument().addDocumentListener(simple(() -> onHeaderEdited()));
            lblInfo.setFont(new Font("Arial", Font.ITALIC, 12));
            c.gridx = 0; c.gridy = row + 1; c.gridwidth = 6;
            head.add(lblInfo, c);
            center.add(head, BorderLayout.NORTH);

            table.setFont(F);
            table.setRowHeight(26);
            table.getTableHeader().setFont(FB);
            table.setGridColor(new Color(0xB0B0B0));
            table.setSurrendersFocusOnKeystroke(true);
            table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
            DefaultTableCellRenderer r = new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int c2) {
                    Component comp = super.getTableCellRendererComponent(t, v, sel, foc, row, c2);
                    setHorizontalAlignment(c2 == 0 || c2 == model.getColumnCount() - 1 ? LEFT : RIGHT);
                    boolean residue = current != null && model.isResidue(row);
                    boolean empty = current != null && current.phases.get(row).isEmpty();
                    comp.setForeground(residue || empty ? new Color(0x909090) : Color.BLACK);
                    if (!sel) {
                        comp.setBackground(c2 == 0 ? new Color(0xC8D0DC) : (residue ? new Color(0xEEEEEE) : Color.WHITE));
                    }
                    return comp;
                }
            };
            table.setDefaultRenderer(Object.class, r);
            table.setDefaultRenderer(String.class, r);
            table.setDefaultRenderer(Integer.class, r);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
            for (int ci = 0; ci < model.getColumnCount(); ci++) {
                int w = ci == 0 ? 44 : (ci <= layout.phase.size() ? 128 : (ci == layout.phase.size() + 1 ? 70
                        : (ci == layout.phase.size() + 2 ? 130 : 220)));
                table.getColumnModel().getColumn(ci).setPreferredWidth(w);
                table.getColumnModel().getColumn(ci).setMinWidth(ci == 0 ? 40 : 60);
            }
            JScrollPane sp = new JScrollPane(table);
            sp.setBorder(BorderFactory.createTitledBorder("Fasi (le righe grigie dopo lo scarico non vengono eseguite)"));
            sp.getViewport().setBackground(Color.WHITE);
            center.add(sp, BorderLayout.CENTER);

            JPanel pb = new JPanel();
            pb.setOpaque(false);
            pb.setLayout(new BoxLayout(pb, BoxLayout.X_AXIS));
            pb.add(button("Azzera fase", this::clearPhase));
            pb.add(Box.createHorizontalStrut(6));
            pb.add(button("Copia fase sulla successiva", this::copyPhaseDown));
            center.add(pb, BorderLayout.SOUTH);
            add(center, BorderLayout.CENTER);
        }

        private JLabel lbl(String s) {
            JLabel l = new JLabel(s);
            l.setFont(F);
            return l;
        }

        private void buildBottom() {
            JPanel bottom = new JPanel(new BorderLayout(6, 6));
            bottom.setOpaque(false);
            problems.setEditable(false);
            problems.setFont(new Font("Arial", Font.PLAIN, 12));
            problems.setBackground(new Color(0xFFF4D0));
            JScrollPane ps = new JScrollPane(problems);
            ps.setBorder(BorderFactory.createTitledBorder("Controlli"));
            ps.setPreferredSize(new Dimension(0, 92));
            bottom.add(ps, BorderLayout.CENTER);
            JPanel b = new JPanel(new java.awt.GridLayout(2, 2, 6, 6));
            b.setOpaque(false);
            b.add(button("Salva (Ctrl+S)", this::save));
            b.add(button("Importa dal PLC", this::importFromPlc));
            b.add(button("Esporta CSV", this::exportCsv));
            JButton send = button("Invia al PLC", () -> { });
            send.setEnabled(false);
            send.setToolTipText("Non ancora attivo: da abilitare dopo la conferma del layout DB201");
            b.add(send);
            b.setPreferredSize(new Dimension(380, 78));
            bottom.add(b, BorderLayout.EAST);
            lblStatus.setFont(new Font("Arial", Font.PLAIN, 11));
            lblStatus.setText("Archivio: " + repo.location() + "   |   Utente: " + user
                    + "   |   PLC: " + plcIp + " (sola lettura)");
            bottom.add(lblStatus, BorderLayout.SOUTH);
            add(bottom, BorderLayout.SOUTH);
        }

        // ---------------- logica ----------------
        void show(B1Recipe r, boolean isDirty, String info) {
            loading = true;
            current = r;
            txtName.setText(r.name);
            txtNo.setText(String.valueOf(r.recipeNo));
            for (B1RecipeLayout.Field f : layout.header) {
                headerFields.get(f.key).setText(fmt(r.header.getOrDefault(f.key, 0), f));
            }
            lblInfo.setText(info + (r.revision > 0 ? "   |   rev. " + r.revision + ", " + r.modified + " (" + r.modifiedBy + ")" : ""));
            model.fireTableDataChanged();
            loading = false;
            dirty = isDirty;
            revalidateRecipe();
        }

        private void onHeaderEdited() {
            if (loading || current == null) {
                return;
            }
            current.name = txtName.getText();
            current.recipeNo = parseInt(txtNo.getText());
            for (B1RecipeLayout.Field f : layout.header) {
                current.header.put(f.key, toRaw(headerFields.get(f.key).getText(), f));
            }
            dirty = true;
            revalidateRecipe();
        }

        void revalidateRecipe() {
            List<String> err = B1RecipeCodec.validate(current, layout);
            problems.setBackground(err.isEmpty() ? new Color(0xD8F5D8) : new Color(0xFFF4D0));
            problems.setText(err.isEmpty() ? "Ricetta valida." + (dirty ? "  (modifiche non salvate)" : "")
                    : String.join("\n", err));
            table.repaint();
        }

        boolean confirmDiscard() {
            if (!dirty) {
                return true;
            }
            int r = JOptionPane.showConfirmDialog(this, "Ci sono modifiche non salvate. Scartarle?",
                    "Modifiche non salvate", JOptionPane.YES_NO_OPTION);
            return r == JOptionPane.YES_OPTION;
        }

        private void refreshList() {
            try {
                allEntries = repo.list();
            } catch (IOException e) {
                allEntries = new ArrayList<>();
                error("Archivio non leggibile: " + e.getMessage());
            }
            applyFilter();
        }

        private void applyFilter() {
            String q = search.getText().trim().toLowerCase(Locale.ROOT);
            loading = true;
            listModel.clear();
            for (B1RecipeRepository.Entry e : allEntries) {
                if (q.isEmpty() || e.name.toLowerCase(Locale.ROOT).contains(q)) {
                    listModel.addElement(e);
                }
            }
            loading = false;
        }

        private void openEntry(B1RecipeRepository.Entry e) {
            if (current != null && e.uid.equals(current.uid)) {
                return;
            }
            if (!confirmDiscard()) {
                return;
            }
            try {
                show(repo.load(e.uid), false, "Ricetta dall'archivio");
            } catch (IOException ex) {
                error("Lettura fallita: " + ex.getMessage());
            }
        }

        private void newRecipe() {
            if (confirmDiscard()) {
                list.clearSelection();
                show(B1Recipe.blank(layout), false, "Nuova ricetta");
            }
        }

        private void duplicate() {
            if (current == null) {
                return;
            }
            B1Recipe c = current.copy();
            c.uid = java.util.UUID.randomUUID().toString();
            c.revision = 0;
            c.name = current.name + "_COPIA";
            list.clearSelection();
            show(c, true, "Copia non ancora salvata");
        }

        private void deleteRecipe() {
            if (current == null || current.revision == 0) {
                return;
            }
            int r = JOptionPane.showConfirmDialog(this, "Eliminare definitivamente la ricetta '" + current.name + "'?",
                    "Elimina", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.YES_OPTION) {
                return;
            }
            try {
                repo.delete(current.uid);
                refreshList();
                show(B1Recipe.blank(layout), false, "Nuova ricetta");
            } catch (IOException e) {
                error("Eliminazione fallita: " + e.getMessage());
            }
        }

        private void save() {
            if (current == null) {
                return;
            }
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
            onHeaderEdited();
            List<String> err = B1RecipeCodec.validate(current, layout);
            if (!err.isEmpty()) {
                int r = JOptionPane.showConfirmDialog(this,
                        "La ricetta non e' valida e non potra' essere scaricata:\n- " + String.join("\n- ", err)
                                + "\n\nSalvare comunque come bozza?", "Ricetta non valida",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (r != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            try {
                repo.save(current, user);
                dirty = false;
                refreshList();
                lblInfo.setText("Salvata: rev. " + current.revision + ", " + current.modified + " (" + user + ")");
                revalidateRecipe();
            } catch (B1RecipeRepository.ConflictException e) {
                error(e.getMessage());
            } catch (IOException e) {
                error("Salvataggio fallito: " + e.getMessage());
            }
        }

        private void importFromPlc() {
            if (!confirmDiscard()) {
                return;
            }
            String ip = JOptionPane.showInputDialog(this, "Indirizzo PLC B1 (lettura DB" + layout.db + "):", plcIp);
            if (ip == null || ip.trim().isEmpty()) {
                return;
            }
            final String host = ip.trim();
            lblStatus.setText("Lettura dal PLC " + host + "...");
            new SwingWorker<B1Recipe, Void>() {
                @Override
                protected B1Recipe doInBackground() throws Exception {
                    try (S7ReadClient plc = new S7ReadClient(host, 0, 2)) {
                        plc.connect();
                        int size = plc.detectDbSize(layout.db, 1024);
                        if (size <= 0) {
                            throw new IOException("DB" + layout.db + " non leggibile");
                        }
                        byte[] d = plc.readDb(layout.db, 0, size);
                        B1Recipe r = B1RecipeCodec.decode(d, layout);
                        r.uid = java.util.UUID.randomUUID().toString();
                        r.revision = 0;
                        return r;
                    }
                }

                @Override
                protected void done() {
                    try {
                        B1Recipe r = get();
                        list.clearSelection();
                        show(r, true, "Importata dal PLC " + host + " (DB" + layout.db + ") - non ancora salvata");
                        lblStatus.setText("Importazione completata (sola lettura)");
                    } catch (Exception e) {
                        Throwable t = e.getCause() != null ? e.getCause() : e;
                        error("Importazione dal PLC fallita: " + (t.getMessage() != null ? t.getMessage() : t.toString()));
                    }
                }
            }.execute();
        }

        private void exportCsv() {
            if (current == null) {
                return;
            }
            javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
            fc.setSelectedFile(new File(current.name.isEmpty() ? "ricetta_b1.csv" : current.name + ".csv"));
            if (fc.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
                return;
            }
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(fc.getSelectedFile().toPath(), StandardCharsets.UTF_8))) {
                w.println("Ricetta;" + current.name + ";N.;" + current.recipeNo);
                for (B1RecipeLayout.Field f : layout.header) {
                    w.println(f.title() + ";" + fmt(current.header.getOrDefault(f.key, 0), f));
                }
                StringBuilder h = new StringBuilder("Fase");
                for (B1RecipeLayout.Field f : layout.phase) {
                    h.append(';').append(f.title());
                }
                w.println(h.append(";Operazioni (hex)").toString());
                for (int i = 0; i < current.phases.size(); i++) {
                    B1Recipe.Phase p = current.phases.get(i);
                    StringBuilder s = new StringBuilder().append(i + 1);
                    for (B1RecipeLayout.Field f : layout.phase) {
                        s.append(';').append(fmt(p.values.getOrDefault(f.key, 0), f));
                    }
                    w.println(s.append(String.format(";0x%04X", p.ops & 0xFFFF)).toString());
                }
            } catch (IOException e) {
                error("Esportazione fallita: " + e.getMessage());
            }
        }

        private void clearPhase() {
            int r = table.getSelectedRow();
            if (r < 0 || current == null) {
                return;
            }
            B1Recipe.Phase p = current.phases.get(r);
            p.values.replaceAll((k, v) -> 0);
            p.ops = 0;
            dirty = true;
            model.fireTableRowsUpdated(r, r);
            revalidateRecipe();
        }

        private void copyPhaseDown() {
            int r = table.getSelectedRow();
            if (r < 0 || current == null || r + 1 >= current.phases.size()) {
                return;
            }
            current.phases.set(r + 1, current.phases.get(r).copy());
            dirty = true;
            model.fireTableRowsUpdated(r + 1, r + 1);
            table.setRowSelectionInterval(r + 1, r + 1);
            revalidateRecipe();
        }

        private void error(String msg) {
            JOptionPane.showMessageDialog(this, msg, "Errore", JOptionPane.ERROR_MESSAGE);
        }

        // ---------------- conversioni ----------------
        private static String fmt(int raw, B1RecipeLayout.Field f) {
            double v = raw * f.scale;
            return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ITALY, "%.2f", v);
        }

        private static int toRaw(String s, B1RecipeLayout.Field f) {
            try {
                double v = Double.parseDouble(s.trim().replace(',', '.'));
                return (int) Math.round(v / f.scale);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private static int parseInt(String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private static DocumentListener simple(Runnable r) {
            return new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { r.run(); }
                public void removeUpdate(DocumentEvent e) { r.run(); }
                public void changedUpdate(DocumentEvent e) { r.run(); }
            };
        }

        // ---------------- modello tabella fasi ----------------
        private final class PhaseModel extends AbstractTableModel {
            private int nParams() {
                return layout.phase.size();
            }

            @Override
            public int getRowCount() {
                return current == null ? 0 : current.phases.size();
            }

            @Override
            public int getColumnCount() {
                return 1 + nParams() + 3;
            }

            @Override
            public String getColumnName(int c) {
                if (c == 0) {
                    return "Fase";
                }
                if (c <= nParams()) {
                    return layout.phase.get(c - 1).title();
                }
                int k = c - nParams() - 1;
                return k == 0 ? "Scarico" : (k == 1 ? "Operazioni (hex)" : "Operazioni");
            }

            @Override
            public Class<?> getColumnClass(int c) {
                return c == nParams() + 1 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int r, int c) {
                return c != 0 && c != getColumnCount() - 1;
            }

            boolean isResidue(int row) {
                for (int i = 0; i < row; i++) {
                    B1Recipe.Phase p = current.phases.get(i);
                    if (!p.isEmpty() && (p.ops & (1 << layout.dischargeBit)) != 0) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public Object getValueAt(int r, int c) {
                B1Recipe.Phase p = current.phases.get(r);
                if (c == 0) {
                    return String.valueOf(r + 1);
                }
                if (c <= nParams()) {
                    B1RecipeLayout.Field f = layout.phase.get(c - 1);
                    return fmt(p.values.getOrDefault(f.key, 0), f);
                }
                int k = c - nParams() - 1;
                if (k == 0) {
                    return (p.ops & (1 << layout.dischargeBit)) != 0;
                }
                if (k == 1) {
                    return String.format("0x%04X", p.ops & 0xFFFF);
                }
                StringBuilder s = new StringBuilder();
                for (int b = 0; b < 16; b++) {
                    if ((p.ops & (1 << b)) != 0) {
                        if (s.length() > 0) {
                            s.append(", ");
                        }
                        s.append(layout.opNames.getOrDefault(b, "bit" + b));
                    }
                }
                return s.toString();
            }

            @Override
            public void setValueAt(Object v, int r, int c) {
                B1Recipe.Phase p = current.phases.get(r);
                if (c <= nParams()) {
                    B1RecipeLayout.Field f = layout.phase.get(c - 1);
                    p.values.put(f.key, toRaw(String.valueOf(v), f));
                } else {
                    int k = c - nParams() - 1;
                    if (k == 0) {
                        int bit = 1 << layout.dischargeBit;
                        p.ops = Boolean.TRUE.equals(v) ? (p.ops | bit) : (p.ops & ~bit);
                    } else if (k == 1) {
                        String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
                        try {
                            p.ops = (s.startsWith("0x") ? Integer.parseInt(s.substring(2), 16)
                                    : Integer.parseInt(s)) & 0xFFFF;
                        } catch (NumberFormatException e) {
                            return;
                        }
                    }
                }
                dirty = true;
                fireTableRowsUpdated(r, r);
                revalidateRecipe();
            }
        }
    }
}
