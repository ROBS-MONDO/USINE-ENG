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

    static final String VERSION = "B1 Recettes V0.1";
    static final Path DEFAULT_DIR = Paths.get("C:\\_tools\\Banbury1\\Recipes");
    static final Path DEFAULT_LAYOUT = Paths.get("C:\\_tools\\Banbury1\\b1_recipe_layout.properties");
    static final String DEFAULT_PLC = "192.168.10.100";

    private static B1RecipeEditor shared;

    private final EditorPanel panel;
    private final boolean exitOnClose;

    public B1RecipeEditor(B1RecipeRepository repo, B1RecipeLayout layout, String plcIp) {
        this(repo, layout, plcIp, true);
    }

    private B1RecipeEditor(B1RecipeRepository repo, B1RecipeLayout layout, String plcIp, boolean exitOnClose) {
        super(VERSION);
        this.exitOnClose = exitOnClose;
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
                    if (B1RecipeEditor.this.exitOnClose) {
                        System.exit(0);
                    } else {
                        synchronized (B1RecipeEditor.class) {
                            shared = null;
                        }
                    }
                }
            }
        });
    }

    /**
     * Apre l'editor dal sinottico (una sola finestra). Con recipeName seleziona la ricetta in archivio;
     * se non c'e' la importa dal PLC (sola lettura).
     */
    public static void openShared(B1RecipeRepository repo, B1RecipeLayout layout, String plcIp, String recipeName) {
        openShared(repo, layout, plcIp, recipeName, false);
    }

    /** Come sopra; con send=true, dopo aver selezionato la ricetta in archivio apre l'anteprima "Envoyer au PLC". */
    public static void openShared(B1RecipeRepository repo, B1RecipeLayout layout, String plcIp, String recipeName, boolean send) {
        SwingUtilities.invokeLater(() -> {
            synchronized (B1RecipeEditor.class) {
                if (shared == null || !shared.isDisplayable()) {
                    shared = new B1RecipeEditor(repo, layout, plcIp, false);
                    shared.setVisible(true);
                }
                shared.setExtendedState(JFrame.NORMAL);
                shared.toFront();
                shared.requestFocus();
                if (send && recipeName != null && !recipeName.trim().isEmpty()) {
                    shared.panel.selectAndSend(recipeName);
                } else if (recipeName != null && !recipeName.trim().isEmpty()) {
                    shared.panel.selectByName(recipeName);
                } else {
                    shared.panel.autoImportIfEmpty();
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
        final B1RecipeRepository repo = B1RecipeRepositories.open(layout, dir);
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
        SwingUtilities.invokeLater(() -> {
            B1RecipeEditor ed = new B1RecipeEditor(repo, layout, plcIp);
            ed.setVisible(true);
            ed.panel.autoImportIfEmpty();
        });
    }

    /** Immagine dell'editor con una ricetta di esempio (verifica grafica senza schermo). */
    private static void snapshot(B1RecipeRepository repo, B1RecipeLayout layout, String out) throws Exception {
        EditorPanel p = new EditorPanel(null, repo, layout, DEFAULT_PLC);
        B1Recipe r = B1Recipe.blank(layout);
        r.name = "gomma2";
        r.recipeNo = 24;
        // fase: rotor, timeout, temp, press (+ operazioni): dati del dump 12/11/2026
        int[][] ph = {{36, 900, 80, 2}, {33, 900, 90, 2}, {33, 700, 105, 10}, {33, 0, 0, 0}};
        String[] keys = {"rpm", "timeout", "temp", "press"};
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
        p.show(r, false, "Recette d'exemple (donn\u00e9es du dump du 12/11/2026)");
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
            show(B1Recipe.blank(layout), false, "Nouvelle recette");

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
            JLabel t = new JLabel("  RECETTES B1", JLabel.LEFT);
            t.setOpaque(true);
            t.setBackground(HEAD);
            t.setForeground(Color.WHITE);
            t.setFont(FB);
            t.setPreferredSize(new Dimension(0, 28));
            left.add(t, BorderLayout.NORTH);
            JPanel mid = new JPanel(new BorderLayout(4, 4));
            mid.setOpaque(false);
            search.setFont(F);
            search.setToolTipText("Chercher par nom");
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
            btns.add(button("Nouvelle", this::newRecipe));
            btns.add(button("Dupliquer", this::duplicate));
            btns.add(button("Supprimer", this::deleteRecipe));
            btns.add(button("Actualiser", this::refreshList));
            left.add(btns, BorderLayout.SOUTH);
            add(left, BorderLayout.WEST);
        }

        private void buildCenter() {
            JPanel center = new JPanel(new BorderLayout(6, 6));
            center.setOpaque(false);

            JPanel head = new JPanel(new GridBagLayout());
            head.setBackground(new Color(0xE6E6E6));
            head.setBorder(BorderFactory.createTitledBorder("En-t\u00eate recette"));
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(3, 6, 3, 6);
            c.anchor = GridBagConstraints.WEST;
            c.gridx = 0; c.gridy = 0;
            head.add(lbl("Nom recette"), c);
            txtName.setFont(new Font("Arial", Font.BOLD, 15));
            c.gridx = 1; c.gridwidth = 2;
            head.add(txtName, c);
            c.gridwidth = 1;
            c.gridx = 3;
            head.add(lbl("N\u00b0 recette"), c);
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
                    setHorizontalAlignment(c2 == 0 || c2 == 3 ? LEFT : RIGHT);
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
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            for (int ci = 0; ci < model.getColumnCount(); ci++) {
                int w = ci == 0 ? 44 : (ci == 1 ? 64 : (ci == 2 ? 110 : (ci == 3 ? 230 : 120)));
                table.getColumnModel().getColumn(ci).setPreferredWidth(w);
                table.getColumnModel().getColumn(ci).setMinWidth(ci == 0 ? 40 : 60);
            }
            JScrollPane sp = new JScrollPane(table);
            sp.setBorder(BorderFactory.createTitledBorder("Phases (les lignes grises apr\u00e8s le d\u00e9chargement ne sont pas ex\u00e9cut\u00e9es)"));
            sp.getViewport().setBackground(Color.WHITE);
            center.add(sp, BorderLayout.CENTER);

            JPanel pb = new JPanel();
            pb.setOpaque(false);
            pb.setLayout(new BoxLayout(pb, BoxLayout.X_AXIS));
            pb.add(button("Effacer phase", this::clearPhase));
            pb.add(Box.createHorizontalStrut(6));
            pb.add(button("Copier la phase sur la suivante", this::copyPhaseDown));
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
            ps.setBorder(BorderFactory.createTitledBorder("Contr\u00f4les"));
            ps.setPreferredSize(new Dimension(0, 92));
            bottom.add(ps, BorderLayout.CENTER);
            JPanel b = new JPanel(new java.awt.GridLayout(2, 2, 6, 6));
            b.setOpaque(false);
            b.add(button("Enregistrer (Ctrl+S)", this::save));
            b.add(button("Importer du PLC", this::importFromPlc));
            b.add(button("Exporter CSV", this::exportCsv));
            JButton send = button("Envoyer au PLC", this::sendToPlc);
            send.setToolTipText("Aper\u00e7u de ce qui serait \u00e9crit dans le DB" + layout.db
                    + " (l'\u00e9criture r\u00e9elle est d\u00e9sactiv\u00e9e tant que b1_recipe_transfer.properties n'est pas activ\u00e9)");
            b.add(send);
            b.setPreferredSize(new Dimension(380, 78));
            bottom.add(b, BorderLayout.EAST);
            lblStatus.setFont(new Font("Arial", Font.PLAIN, 11));
            lblStatus.setText("Archive : " + repo.location() + "   |   Utilisateur : " + user
                    + "   |   PLC: " + plcIp + " (lecture seule)");
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
            lblInfo.setText(info + (r.revision > 0 ? "   |   r\u00e9v. " + r.revision + ", " + r.modified + " (" + r.modifiedBy + ")" : ""));
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
            problems.setText(err.isEmpty() ? "Recette valide." + (dirty ? "  (modifications non enregistr\u00e9es)" : "")
                    : String.join("\n", err));
            table.repaint();
        }

        boolean confirmDiscard() {
            if (!dirty) {
                return true;
            }
            int r = JOptionPane.showConfirmDialog(this, "Il y a des modifications non enregistr\u00e9es. Les abandonner ?",
                    "Modifications non enregistr\u00e9es", JOptionPane.YES_NO_OPTION);
            return r == JOptionPane.YES_OPTION;
        }

        private void refreshList() {
            try {
                allEntries = repo.list();
            } catch (IOException e) {
                allEntries = new ArrayList<>();
                error("Archive illisible : " + e.getMessage());
            }
            applyFilter();
        }

        private void applyFilter() {
            String q = search.getText().trim().toLowerCase(Locale.ROOT);
            loading = true;
            listModel.clear();
            for (B1RecipeRepository.Entry e : allEntries) {
                if (q.isEmpty() || e.name.toLowerCase(Locale.ROOT).contains(q) || e.toString().toLowerCase(Locale.ROOT).contains(q)) { // anche col simbolo dei gradi
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
                show(repo.load(e.uid), false, "Recette de l'archive");
            } catch (IOException ex) {
                error("\u00c9chec de lecture : " + ex.getMessage());
            }
        }

        private void newRecipe() {
            if (confirmDiscard()) {
                list.clearSelection();
                show(B1Recipe.blank(layout), false, "Nouvelle recette");
            }
        }

        private void duplicate() {
            if (current == null) {
                return;
            }
            B1Recipe c = current.copy();
            c.uid = java.util.UUID.randomUUID().toString();
            c.revision = 0;
            c.name = current.name + "_COPIE";
            list.clearSelection();
            show(c, true, "Copie pas encore enregistr\u00e9e");
        }

        private void deleteRecipe() {
            if (current == null || current.revision == 0) {
                return;
            }
            int r = JOptionPane.showConfirmDialog(this, "Supprimer d\u00e9finitivement la recette '" + current.name + "'?",
                    "Supprimer", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.YES_OPTION) {
                return;
            }
            try {
                repo.delete(current.uid);
                refreshList();
                show(B1Recipe.blank(layout), false, "Nouvelle recette");
            } catch (IOException e) {
                error("\u00c9chec de la suppression : " + e.getMessage());
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
                        "La recette n'est pas valide et ne pourra pas \u00eatre envoy\u00e9e \u00e0 la machine :\n- " + String.join("\n- ", err)
                                + "\n\nEnregistrer quand m\u00eame comme brouillon ?", "Recette non valide",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (r != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            try {
                repo.save(current, user);
                dirty = false;
                refreshList();
                lblInfo.setText("Enregistr\u00e9e : r\u00e9v. " + current.revision + ", " + current.modified + " (" + user + ")");
                revalidateRecipe();
            } catch (B1RecipeRepository.ConflictException e) {
                error(e.getMessage());
            } catch (IOException e) {
                error("\u00c9chec de l'enregistrement : " + e.getMessage());
            }
        }

        /** Seleziona in archivio la ricetta col nome dato; se manca, la importa dal PLC. */
        void selectByName(String name) {
            if (current != null && !dirty && name.trim().equalsIgnoreCase(current.name.trim())) {
                return;
            }
            if (!confirmDiscard()) {
                return;
            }
            refreshList();
            for (B1RecipeRepository.Entry e : allEntries) {
                if (e.name.trim().equalsIgnoreCase(name.trim())) {
                    try {
                        loading = true;
                        search.setText("");
                        list.setSelectedValue(e, true);
                        loading = false;
                        show(repo.load(e.uid), false, "Recette de l'archive");
                    } catch (IOException ex) {
                        loading = false;
                        error("\u00c9chec de lecture : " + ex.getMessage());
                    }
                    return;
                }
            }
            doImport(plcIp);
        }

        /** Download: seleziona la ricetta in archivio (se manca non invia nulla) e apre l'anteprima di invio al PLC. */
        void selectAndSend(String name) {
            if (!(current != null && !dirty && name.trim().equalsIgnoreCase(current.name.trim()))) {
                if (!confirmDiscard()) {
                    return;
                }
                refreshList();
                B1RecipeRepository.Entry found = null;
                for (B1RecipeRepository.Entry e : allEntries) {
                    if (e.name.trim().equalsIgnoreCase(name.trim())) {
                        found = e;
                        break;
                    }
                }
                if (found == null) {
                    error("Recette introuvable dans l'archive : " + name);
                    return;
                }
                try {
                    loading = true;
                    search.setText("");
                    list.setSelectedValue(found, true);
                    loading = false;
                    show(repo.load(found.uid), false, "Recette de l'archive");
                } catch (IOException ex) {
                    loading = false;
                    error("\u00c9chec de lecture : " + ex.getMessage());
                    return;
                }
            }
            sendToPlc();
        }

        /** Archivio ancora vuoto: parte dalla ricetta in macchina (lettura dal PLC, sola lettura) invece di mostrare un editor vuoto. */
        void autoImportIfEmpty() {
            try {
                if (!repo.list().isEmpty() || dirty || current != null && current.name != null && !current.name.isEmpty()) {
                    return;
                }
            } catch (IOException e) {
                return;
            }
            doImport(plcIp);
        }

        private void importFromPlc() {
            if (!confirmDiscard()) {
                return;
            }
            String ip = JOptionPane.showInputDialog(this, "Adresse PLC B1 (lecture DB" + layout.db + "):", plcIp);
            if (ip == null || ip.trim().isEmpty()) {
                return;
            }
            doImport(ip.trim());
        }

        /** Invio al PLC: legge DB201 e DB110, mostra l'anteprima e (solo se abilitato) scrive con copia di sicurezza e verifica. */
        private void sendToPlc() {
            if (current == null) {
                return;
            }
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
            onHeaderEdited();
            final B1Recipe recipe = current.copy();
            final B1RecipeTransfer.Config cfg = B1RecipeTransfer.Config.load();
            final String host = plcIp;
            lblStatus.setText("Lecture du PLC " + host + " pour l'aper\u00e7u...");
            new SwingWorker<B1RecipeTransfer.Plan, Void>() {
                @Override
                protected B1RecipeTransfer.Plan doInBackground() throws Exception {
                    try (S7ReadClient plc = new S7ReadClient(host, 102, 0, 2, 3000)) {
                        plc.connect();
                        int size = plc.detectDbSize(layout.db, 1024);
                        byte[] cur = size > 0 ? plc.readDb(layout.db, 0, size) : null;
                        byte[] st = null;
                        try {
                            int s110 = plc.detectDbSize(110, 512);
                            st = s110 > 0 ? plc.readDb(110, 0, s110) : null;
                        } catch (IOException ignored) {
                            // stato macchina non leggibile: l'anteprima lo segnala
                        }
                        return B1RecipeTransfer.plan(recipe, layout, cur, st);
                    }
                }

                @Override
                protected void done() {
                    try {
                        B1RecipeTransfer.Plan plan = get();
                        showTransferDialog(plan, cfg, host);
                    } catch (Exception e) {
                        Throwable t = e.getCause() != null ? e.getCause() : e;
                        error("\u00c9chec de l'aper\u00e7u d'envoi : " + (t.getMessage() != null ? t.getMessage() : t.toString()));
                    }
                }
            }.execute();
        }

        private void showTransferDialog(B1RecipeTransfer.Plan plan, B1RecipeTransfer.Config cfg, String host) {
            JTextArea area = new JTextArea(B1RecipeTransfer.describe(plan, cfg, host), 24, 86);
            area.setEditable(false);
            area.setFont(new Font("Monospaced", Font.PLAIN, 12));
            boolean canWrite = cfg.writeAllowed() && plan.blockers.isEmpty() && !plan.changes.isEmpty();
            Object[] options = canWrite ? new Object[]{"Annuler", "\u00c9crire dans le PLC"} : new Object[]{"Fermer"};
            int r = JOptionPane.showOptionDialog(this, new JScrollPane(area), "Envoyer au PLC - aper\u00e7u",
                    JOptionPane.DEFAULT_OPTION, canWrite ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE,
                    null, options, options[0]);
            lblStatus.setText("Archive : " + repo.location() + "   |   Utilisateur : " + user + "   |   PLC: " + plcIp
                    + (cfg.writeAllowed() ? " (\u00e9criture activ\u00e9e)" : " (lecture seule)"));
            if (!canWrite || r != 1) {
                return;
            }
            String typed = JOptionPane.showInputDialog(this, "Vous allez \u00e9crire la recette dans le PLC de la machine.\n"
                    + "Pour confirmer, \u00e9crivez ENVOYER :", "Confirmer", JOptionPane.WARNING_MESSAGE);
            if (typed == null || !typed.trim().equals("ENVOYER")) {
                lblStatus.setText("Envoi annul\u00e9");
                return;
            }
            lblStatus.setText("\u00c9criture dans le PLC...");
            new SwingWorker<java.nio.file.Path, Void>() {
                @Override
                protected java.nio.file.Path doInBackground() throws Exception {
                    return B1RecipeTransfer.execute(plan, cfg, host, 102, plan.current.length);
                }

                @Override
                protected void done() {
                    B1RecipeSendLog.Entry log = new B1RecipeSendLog.Entry();
                    log.recipeUid = plan.recipeUid;
                    log.name = plan.recipeName;
                    log.recipeNo = plan.recipeNo;
                    log.revision = plan.recipeRevision;
                    log.user = user;
                    log.words = plan.changes.size();
                    try {
                        java.nio.file.Path backup = get();
                        log.ok = true;
                        log.backup = String.valueOf(backup);
                        log.message = "Recette envoy\u00e9e et v\u00e9rifi\u00e9e";
                        B1RecipeSendLog.record(log);
                        lblStatus.setText("Recette envoy\u00e9e et v\u00e9rifi\u00e9e. Copie de s\u00e9curit\u00e9 : " + backup);
                        JOptionPane.showMessageDialog(EditorPanel.this, "Recette envoy\u00e9e et v\u00e9rifi\u00e9e.\nCopie de s\u00e9curit\u00e9 : " + backup);
                    } catch (Exception e) {
                        Throwable t = e.getCause() != null ? e.getCause() : e;
                        log.ok = false;
                        log.message = t.getMessage() != null ? t.getMessage() : t.toString();
                        B1RecipeSendLog.record(log);
                        error("\u00c9chec de l'envoi : " + (t.getMessage() != null ? t.getMessage() : t.toString()));
                    }
                }
            }.execute();
        }

        private void doImport(String hostSpec) {
            String h = hostSpec;
            int port = 102;
            int colon = hostSpec.lastIndexOf(':');
            if (colon > 0) {
                h = hostSpec.substring(0, colon);
                port = Integer.parseInt(hostSpec.substring(colon + 1));
            }
            final String host = h;
            final int portF = port;
            lblStatus.setText("Lecture du PLC " + host + "...");
            new SwingWorker<B1Recipe, Void>() {
                @Override
                protected B1Recipe doInBackground() throws Exception {
                    try (S7ReadClient plc = new S7ReadClient(host, portF, 0, 2, 3000)) {
                        plc.connect();
                        int size = plc.detectDbSize(layout.db, 1024);
                        if (size <= 0) {
                            throw new IOException("DB" + layout.db + " illisible");
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
                        show(r, true, "Import\u00e9e du PLC " + host + " (DB" + layout.db + ") - pas encore enregistr\u00e9e");
                        lblStatus.setText("Importation termin\u00e9e (lecture seule)");
                    } catch (Exception e) {
                        Throwable t = e.getCause() != null ? e.getCause() : e;
                        error("\u00c9chec de l'importation du PLC : " + (t.getMessage() != null ? t.getMessage() : t.toString()));
                    }
                }
            }.execute();
        }

        private void exportCsv() {
            if (current == null) {
                return;
            }
            javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
            fc.setSelectedFile(new File(current.name.isEmpty() ? "recette_b1.csv" : current.name + ".csv"));
            if (fc.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
                return;
            }
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(fc.getSelectedFile().toPath(), StandardCharsets.UTF_8))) {
                w.println("Recette;" + current.name + ";N.;" + current.recipeNo);
                for (B1RecipeLayout.Field f : layout.header) {
                    w.println(f.title() + ";" + fmt(current.header.getOrDefault(f.key, 0), f));
                }
                StringBuilder h = new StringBuilder("Phase");
                for (B1RecipeLayout.Field f : layout.phase) {
                    h.append(';').append(f.title());
                }
                w.println(h.append(";Op\u00e9rations (hex)").toString());
                for (int i = 0; i < current.phases.size(); i++) {
                    B1Recipe.Phase p = current.phases.get(i);
                    StringBuilder s = new StringBuilder().append(i + 1);
                    for (B1RecipeLayout.Field f : layout.phase) {
                        s.append(';').append(fmt(p.values.getOrDefault(f.key, 0), f));
                    }
                    w.println(s.append(String.format(";0x%04X", p.ops & 0xFFFF)).toString());
                }
            } catch (IOException e) {
                error("\u00c9chec de l'exportation : " + e.getMessage());
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
            JOptionPane.showMessageDialog(this, msg, "Erreur", JOptionPane.ERROR_MESSAGE);
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
            // colonne: 0 Fase | 1 Scarico | 2 Operazioni (hex) | 3 Operazioni (nomi) | 4.. parametri
            private static final int FIXED = 4;

            private int nParams() {
                return layout.phase.size();
            }

            @Override
            public int getRowCount() {
                return current == null ? 0 : current.phases.size();
            }

            @Override
            public int getColumnCount() {
                return FIXED + nParams();
            }

            @Override
            public String getColumnName(int c) {
                switch (c) {
                    case 0: return "Phase";
                    case 1: return "D\u00e9ch.";
                    case 2: return "Op\u00e9rations (hex)";
                    case 3: return "Op\u00e9rations";
                    default: return layout.phase.get(c - FIXED).title();
                }
            }

            @Override
            public Class<?> getColumnClass(int c) {
                return c == 1 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int r, int c) {
                return c != 0 && c != 3;
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
                switch (c) {
                    case 0:
                        return String.valueOf(r + 1);
                    case 1:
                        return (p.ops & (1 << layout.dischargeBit)) != 0;
                    case 2:
                        return String.format("0x%04X", p.ops & 0xFFFF);
                    case 3: {
                        StringBuilder sb = new StringBuilder();
                        for (int bit = 0; bit < 16; bit++) {
                            if ((p.ops & (1 << bit)) != 0) {
                                if (sb.length() > 0) {
                                    sb.append(", ");
                                }
                                sb.append(layout.opNames.getOrDefault(bit, "bit" + bit));
                            }
                        }
                        return sb.toString();
                    }
                    default: {
                        B1RecipeLayout.Field f = layout.phase.get(c - FIXED);
                        return fmt(p.values.getOrDefault(f.key, 0), f);
                    }
                }
            }

            @Override
            public void setValueAt(Object v, int r, int c) {
                B1Recipe.Phase p = current.phases.get(r);
                if (c >= FIXED) {
                    B1RecipeLayout.Field f = layout.phase.get(c - FIXED);
                    p.values.put(f.key, toRaw(String.valueOf(v), f));
                } else if (c == 1) {
                    int bit = 1 << layout.dischargeBit;
                    p.ops = Boolean.TRUE.equals(v) ? (p.ops | bit) : (p.ops & ~bit);
                } else if (c == 2) {
                    String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
                    try {
                        p.ops = (s.startsWith("0x") ? Integer.parseInt(s.substring(2), 16)
                                : Integer.parseInt(s)) & 0xFFFF;
                    } catch (NumberFormatException e) {
                        return;
                    }
                }
                dirty = true;
                fireTableRowsUpdated(r, r);
                revalidateRecipe();
            }
        }
    }
}
