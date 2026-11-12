package Banbury1;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Lettura del ticket (ticket 1 = primo passaggio, ticket 2 = secondo) come in B2: il lettore QR
 * lavora da tastiera, il testo viene interpretato (B1LabelParser) e i dati vengono scritti nel
 * database SQL di B1 (B1LabelStore). Finche' lo store e' in PROVA mostra l'SQL ma non scrive.
 * Nessuna scrittura sul PLC.
 */
public final class B1LabelDialog {

    static final Path LOG_FILE = Paths.get("C:\\_tools\\Banbury1\\label_log.txt");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static B1LabelStore store = B1SqlLabelStore.fromProperties(new java.util.Properties());

    private B1LabelDialog() {
    }

    public static void setStore(B1LabelStore s) {
        store = s;
    }

    public static void open(JFrame owner, B1RecipeBridge bridge) {
        SwingUtilities.invokeLater(() -> {
            JDialog d = new JDialog(owner, "Lire ticket", true);
            LabelPanel p = new LabelPanel(bridge, store, d::dispose);
            d.setContentPane(p);
            d.pack();
            d.setLocationRelativeTo(owner);
            SwingUtilities.invokeLater(p::focusScanner);
            d.setVisible(true);
        });
    }

    /** Immagine della finestra dopo la lettura di {@code scanned} (verifica senza schermo). */
    public static void snapshot(String scanned, B1RecipeBridge bridge, B1LabelStore s, String out) throws Exception {
        LabelPanel p = new LabelPanel(bridge, s, () -> { });
        p.setSize(760, 560);
        p.addNotify();
        p.process(scanned);
        layoutAll(p);
        BufferedImage img = new BufferedImage(760, 560, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        p.paint(g);
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", new File(out));
    }

    private static void layoutAll(Component c) {
        if (c instanceof Container) {
            ((Container) c).doLayout();
            for (Component ch : ((Container) c).getComponents()) {
                layoutAll(ch);
            }
        }
    }

    // =====================================================================
    static final class LabelPanel extends JPanel {
        private static final Font F = new Font("Arial", Font.PLAIN, 15);
        private static final Font FB = new Font("Arial", Font.BOLD, 15);

        private final B1RecipeBridge bridge;
        private final B1LabelStore store;
        private final Runnable close;
        private final JTextField scan = new JTextField();
        private final JLabel info = new JLabel(" ", JLabel.CENTER);
        private final JLabel[] vals = new JLabel[11];
        private final String[] names = {"Ticket", "Ricetta", "RecipeUID", "Lotto", "Suffixe", "Num melange",
                "Miscele", "Passaggio", "Type (couche)", "Couleur", "Ricetta in archivio B1"};
        private final JTextArea sql = new JTextArea(5, 50);
        private final JButton confirm = new JButton();
        private B1Label current;

        LabelPanel(B1RecipeBridge bridge, B1LabelStore store, Runnable close) {
            super(new BorderLayout(10, 10));
            this.bridge = bridge;
            this.store = store;
            this.close = close;
            setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
            setBackground(new Color(0xECECEC));

            JLabel title = new JLabel("En attente d'un code-barres (ticket 1 ou ticket 2)", JLabel.CENTER);
            title.setFont(new Font("Arial", Font.BOLD, 20));
            scan.setFont(new Font("Arial", Font.BOLD, 20));
            scan.setHorizontalAlignment(JTextField.CENTER);
            scan.setPreferredSize(new Dimension(700, 44));
            JPanel top = new JPanel(new BorderLayout(6, 8));
            top.setOpaque(false);
            top.add(title, BorderLayout.NORTH);
            top.add(scan, BorderLayout.CENTER);
            info.setFont(F);
            top.add(info, BorderLayout.SOUTH);
            add(top, BorderLayout.NORTH);

            JPanel grid = new JPanel(new GridBagLayout());
            grid.setOpaque(false);
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(2, 4, 2, 4);
            c.anchor = GridBagConstraints.WEST;
            for (int i = 0; i < names.length; i++) {
                JLabel l = new JLabel(names[i] + ":");
                l.setFont(F);
                vals[i] = new JLabel(" ");
                vals[i].setFont(FB);
                c.gridx = 0;
                c.gridy = i;
                c.weightx = 0;
                grid.add(l, c);
                c.gridx = 1;
                c.weightx = 1;
                grid.add(vals[i], c);
            }
            JPanel mid = new JPanel(new BorderLayout(4, 6));
            mid.setOpaque(false);
            mid.add(grid, BorderLayout.NORTH);
            sql.setEditable(false);
            sql.setLineWrap(true);
            sql.setWrapStyleWord(true);
            sql.setFont(new Font("Monospaced", Font.PLAIN, 12));
            sql.setBackground(new Color(0xFFF8DC));
            JScrollPane sp = new JScrollPane(sql);
            sp.setBorder(BorderFactory.createTitledBorder(store.dryRun()
                    ? "SQL (MODALITA' PROVA: non viene scritto nulla)" : "SQL che verra' eseguito"));
            mid.add(sp, BorderLayout.CENTER);
            add(mid, BorderLayout.CENTER);

            JPanel bottom = new JPanel();
            bottom.setOpaque(false);
            confirm.setFont(FB);
            confirm.setText(store.dryRun() ? "Simula (nessuna scrittura)" : "Conferma e scrivi su SQL");
            confirm.setEnabled(false);
            confirm.addActionListener(e -> confirmWrite());
            JButton cancel = new JButton("Annulla");
            cancel.setFont(FB);
            cancel.addActionListener(e -> close.run());
            bottom.add(confirm);
            bottom.add(cancel);
            add(bottom, BorderLayout.SOUTH);

            configureScanner();
        }

        void focusScanner() {
            scan.requestFocusInWindow();
        }

        /** Il lettore invia tasti: si usa il keyCode fisico (indipendente dal layout FR) come in B2. */
        private void configureScanner() {
            scan.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    int code = e.getKeyCode();
                    if (code == KeyEvent.VK_ENTER) {
                        e.consume();
                        process(scan.getText());
                        return;
                    }
                    if (code == KeyEvent.VK_ESCAPE) {
                        e.consume();
                        close.run();
                        return;
                    }
                    if (code == KeyEvent.VK_BACK_SPACE) {
                        e.consume();
                        String t = scan.getText();
                        if (t != null && !t.isEmpty()) {
                            scan.setText(t.substring(0, t.length() - 1));
                        }
                        return;
                    }
                    if (code == KeyEvent.VK_DELETE) {
                        e.consume();
                        scan.setText("");
                        return;
                    }
                    String m = mapKey(e);
                    if (m != null) {
                        e.consume();
                        scan.setText((scan.getText() + m).toUpperCase(Locale.ROOT));
                    }
                }

                @Override
                public void keyTyped(KeyEvent e) {
                    if (!Character.isISOControl(e.getKeyChar())) {
                        e.consume();
                    }
                }
            });
        }

        static String mapKey(KeyEvent e) {
            int code = e.getKeyCode();
            char ch = e.getKeyChar();
            if (!Character.isISOControl(ch) && (ch == '+' || ch == '%' || ch == '*' || ch == ';'
                    || ch == '/' || ch == '.')) {
                return String.valueOf(ch);
            }
            if (code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z) {
                return String.valueOf((char) ('A' + (code - KeyEvent.VK_A)));
            }
            if (code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9) {
                if (e.isShiftDown()) {
                    if (code == KeyEvent.VK_5) {
                        return "%";
                    }
                    if (code == KeyEvent.VK_8) {
                        return "*";
                    }
                }
                return String.valueOf((char) ('0' + (code - KeyEvent.VK_0)));
            }
            if (code >= KeyEvent.VK_NUMPAD0 && code <= KeyEvent.VK_NUMPAD9) {
                return String.valueOf((char) ('0' + (code - KeyEvent.VK_NUMPAD0)));
            }
            switch (code) {
                case KeyEvent.VK_SEMICOLON: return ";";
                case KeyEvent.VK_COMMA: return ",";
                case KeyEvent.VK_PERIOD: return ".";
                case KeyEvent.VK_SLASH:
                case KeyEvent.VK_BACK_SLASH:
                case KeyEvent.VK_DIVIDE: return "/";
                case KeyEvent.VK_MINUS:
                case KeyEvent.VK_SUBTRACT: return "-";
                case KeyEvent.VK_EQUALS: return e.isShiftDown() ? "+" : "=";
                case KeyEvent.VK_SPACE: return " ";
                case KeyEvent.VK_MULTIPLY: return "*";
                case KeyEvent.VK_ADD: return "+";
                default: return null;
            }
        }

        void process(String text) {
            current = null;
            confirm.setEnabled(false);
            if (text == null || text.trim().isEmpty()) {
                error("Code-barres vide");
                return;
            }
            B1Label l;
            try {
                l = B1LabelParser.parse(text);
            } catch (IllegalArgumentException ex) {
                clearValues();
                error("QR illisible : " + ex.getMessage());
                return;
            }
            current = l;
            vals[0].setText(l.ticketKind());
            vals[1].setText(l.recipeName.isEmpty() ? "(dal RecipeUID)" : l.recipeName);
            vals[2].setText(l.recipeUid);
            vals[3].setText(l.annee + "/" + l.lot);
            vals[4].setText(l.suffixe);
            vals[5].setText(l.serieMelange.isEmpty() ? "-" : l.serieMelange);
            vals[6].setText(String.valueOf(l.nombreMelanges));
            vals[7].setText(l.passageSql());
            vals[8].setText(l.coucheSql());
            vals[9].setText(l.couleurSql());
            vals[10].setText(archiveStatus(l));
            String d;
            try {
                d = store.describe(l);
            } catch (RuntimeException ex) {
                d = "Errore: " + ex.getMessage();
            }
            sql.setText(d);
            info.setForeground(new Color(0, 130, 0));
            info.setText("Ticket lu. Verifier puis confirmer.");
            confirm.setEnabled(true);
        }

        private String archiveStatus(B1Label l) {
            if (bridge == null || l.recipeName.isEmpty()) {
                return "n/d";
            }
            try {
                List<B1RecipeRepository.Entry> all = bridge.repo().list();
                for (B1RecipeRepository.Entry e : all) {
                    if (e.name.trim().equalsIgnoreCase(l.recipeName)) {
                        return "si (" + e.name + ")";
                    }
                }
                return "NO: ricetta non presente nell'archivio B1";
            } catch (IOException e) {
                return "archivio non leggibile";
            }
        }

        private void clearValues() {
            for (JLabel v : vals) {
                v.setText(" ");
            }
            sql.setText("");
        }

        private void error(String m) {
            info.setForeground(Color.RED);
            info.setText(m);
        }

        private void confirmWrite() {
            final B1Label l = current;
            if (l == null) {
                return;
            }
            confirm.setEnabled(false);
            info.setForeground(Color.DARK_GRAY);
            info.setText(store.dryRun() ? "Simulazione..." : "Scrittura su SQL...");
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    if (store.dryRun()) {
                        return null;
                    }
                    store.store(l);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        log(l, store.dryRun() ? "SIMULATO" : "SCRITTO", "");
                        info.setForeground(new Color(0, 130, 0));
                        info.setText(store.dryRun() ? "Simulazione completata (nessuna scrittura)"
                                : "Ticket scritto su SQL");
                    } catch (Exception ex) {
                        Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                        log(l, "ERRORE", String.valueOf(t.getMessage()));
                        error("Erreur écriture : " + t.getMessage());
                        confirm.setEnabled(true);
                    }
                }
            }.execute();
        }

        private void log(B1Label l, String outcome, String details) {
            try {
                Files.createDirectories(LOG_FILE.getParent());
                String line = LocalDateTime.now().format(TS) + " | user=" + System.getProperty("user.name", "?")
                        + " | " + outcome + " | " + l.ticketKind() + " | recipe=" + l.recipeName
                        + " | uid=" + l.recipeUid + " | lot=" + l.annee + "/" + l.lot + " | suffixe=" + l.suffixe
                        + " | serie=" + l.serieMelange + " | n=" + l.nombreMelanges + " | passage=" + l.passageSql()
                        + " | type=" + l.coucheSql() + " | couleur=" + l.couleurSql()
                        + (details.isEmpty() ? "" : " | " + details) + System.lineSeparator();
                try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(LOG_FILE, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
                    w.print(line);
                }
            } catch (IOException ignored) {
                // il log e' accessorio: non blocca l'operatore
            }
        }
    }
}
