package OT4US;

import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.PrintWriter;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

public class MondoLuxRecipesSetup extends JFrame {

    // Versione visibile per controllo file corretto
    private static final String VERSION = "V2.03";

    // =========================================================
    // CONFIG
    // =========================================================
    // Connessione DB SRV-BAMBURY centralizzata in DBBamburyReader.
    private static final String CENTRO = "S201";
    private static final String RECIPE_CHANGE_LOG_FILE =
            "\\\\neo\\x-service\\banbury\\recette\\recipe_step_change_log.txt";
    private static final String RECIPE_SETUP_LOG_FILE =
            "\\\\neo\\x-service\\banbury\\recette\\recipe_setup_change_log.txt";
    private static final String UI_STATE_FILE = "C:\\_Tools\\Banbury\\MondoLuxRecipesJava_state.properties";
    private static final DateTimeFormatter LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // =========================================================
    // CONVERSIONE CONSIGNE LIQUIDE Kg <-> L
    // =========================================================
    // Fattori di conversione (Kg per litro) secondo Produit Liquide:
    // 1 = DOA, 2 = SILANO, 3 = HUILE PASTIFIANT.
    // L'operatore inserisce il valore in 0.1 Kg; il valore inviato a DB e PLC
    // resta in 0.1 L come prima (L = Kg / fattore).
    private static final double LIQUID_FACTOR_DOA = 0.925;
    private static final double LIQUID_FACTOR_SILANO = 1.08;
    private static final double LIQUID_FACTOR_HUILE_PASTIFIANT = 0.87;

    // OPC UA
    private static final String APP_NAME = "MondoLux Recipes";
    private static final String APP_URI = "urn:mondolux:recipes";
    private static final int UA_NAMESPACE_INDEX = 2;
    private static final boolean OPC_UA_ENABLED = true;

    // =========================================================
    // ICONA BAG come PLC3_Main
    // =========================================================
    private static final String BAG_ICON_RESOURCE = "/src/icons/Exit.png";
    private static final String BAG_ICON_WINDOWS_FALLBACK =
            "C:\\_Tools\\Banbury2\\icons\\Exit.png";

    // =========================================================
    // TABELLA BATCH PLC
    // =========================================================
    private static final String PLC_R_NODE_PREFIX = "Banbury 2.PLC3.R";
    private static final String LIVE_R_NODE_PREFIX = "Banbury 2.PLC3.R";
    private static final int BATCH_TABLE_START_R = 4500;
    private static final int LIVE_TABLE_START_R = 4500;
    private static final int EXPECTED_STEP_COUNT = 20;
    private static final int PARAMS_PER_STEP = 10;
    private static final int STEP_REGISTER_STRIDE = 20;
    private static final int PARAM_REGISTER_STRIDE = 2;
    private static final int LIVE_STEP_REGISTER_STRIDE = 20;
    private static final int LIVE_PARAM_REGISTER_STRIDE = 2;

    // Nessun start da Java: download in MANUALE, avvio in AUTOMATICO
    private static final boolean WRITE_START_AFTER_DOWNLOAD = false;
    private static final boolean VERIFY_WRITE_AFTER_DOWNLOAD = true;
    private static final int VERIFY_WRITE_DELAY_MS = 80;

    // Se il PLC segnala un errore di verifica sul primo registro, ma RecipePLC
    // conferma match 100%, l'anomalia puo essere visualizzata e poi eliminata.
    private static final boolean ALLOW_CLEAR_DOWNLOAD_ANOMALIES_IF_RECIPE_MATCH_100 = true;
    private static final int DOWNLOAD_MATCH_CHECK_RETRY_COUNT = 6;
    private static final int DOWNLOAD_MATCH_CHECK_DELAY_MS = 500;
    private static final int RECIPE_TOTAL_MATCHES = EXPECTED_STEP_COUNT * PARAMS_PER_STEP;

    // =========================================================
    // MODALITE MODIFICATION
    // =========================================================
    private static final int EDIT_MODE_DURATION_SECONDS = 15 * 60; // 15 minuti
    private static final int EDIT_MODE_WARNING_SECONDS = 10;
    private static final int EDIT_MODE_POSTPONE_SECONDS = 5 * 60; // 5 minuti
    private static final Color EDIT_MODE_BACKGROUND = new Color(255, 190, 190);
    private static final Color NORMAL_BACKGROUND = new Color(236, 236, 236);

    // =========================================================
    // MODEL
    // =========================================================
    private final List<Var> variables = new ArrayList<>();
    private final S201OpcItemsRecipeEditor configEditor = new S201OpcItemsRecipeEditor();
    private final Map<Integer, List<RecipeStep>> recipeCache = new HashMap<>();
    private Integer lastDownloadedRecipeId = null;

    // =========================================================
    // UI
    // =========================================================
    private final DefaultListModel<String> recipeListModel = new DefaultListModel<>();
    private final JList<String> lstRecipes = new JList<>(recipeListModel);
    private final JTextField txtDescription = new JTextField();
    private final JTabbedPane tabSteps = new JTabbedPane();
    private final JLabel lblStatus = new JLabel("Ready");
    private final JLabel lblRecipeId = new JLabel("-");
    private final JLabel lblLastDownloaded = new JLabel("-");
    private DotPanel rootPanel;
    private ActiveDirectorySetup.AuthenticatedUser editModeUser;
    private long editModeExpiresAtMillis = 0L;
    private javax.swing.Timer editModeTimer;
    private JDialog editModeWarningDialog;
    private JLabel editModeWarningLabel;
    private boolean editModeWarningShown = false;
    private String lastAdUsername = "";

    private final List<String> lastDownloadAnomalies = new ArrayList<>();
    private Integer lastDownloadAnomalyRecipeId = null;

    private javax.swing.Timer errorBlinkTimer;
    private boolean errorBlinkOn = false;

    private BufferedImage bagIconImage;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                System.setProperty("jdk.tls.client.protocols", "TLSv1.2");
                System.setProperty("https.protocols", "TLSv1.2");
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new MondoLuxRecipesSetup().setVisible(true);
        });
    }

    public MondoLuxRecipesSetup() {
        super("MondoLux Recipes - " + VERSION);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setUndecorated(false);
        setResizable(true);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                saveUiState();
            }
        });

        loadBagIconImage();

        setContentPane(buildContent());

        tabSteps.setFont(new Font("SansSerif", Font.BOLD, 20));
        tabSteps.setBackground(new Color(225, 225, 225));
        tabSteps.setForeground(Color.BLACK);
        tabSteps.setOpaque(true);
        tabSteps.setBorder(BorderFactory.createLineBorder(new Color(90, 90, 90), 3));
        tabSteps.setUI(new StepTabbedPaneUI());
        tabSteps.addChangeListener(e -> saveUiState());

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int width = Math.min(1280, Math.max(1100, screen.width - 120));
        int height = Math.min(820, Math.max(720, screen.height - 120));
        setSize(width, height);
        setPreferredSize(new Dimension(width, height));
        setMinimumSize(new Dimension(1000, 650));
        setLocationRelativeTo(null);

        try {
            ensureRecipeUidSchema();
            buildVars();
            loadRecipeList();
            restoreUiState();
            setOkStatus("Configuration OPC chargée: " + variables.size() + " items - " + VERSION);
        } catch (Exception ex) {
            showError("Erreur initiale", ex);
        }
    }

    private void loadBagIconImage() {
        try (InputStream is = MondoLuxRecipesSetup.class.getResourceAsStream(BAG_ICON_RESOURCE)) {
            if (is != null) {
                bagIconImage = ImageIO.read(is);
                if (bagIconImage != null) {
                    System.out.println("Icona BAG caricata da resource: " + BAG_ICON_RESOURCE);
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            File fallbackFile = new File(BAG_ICON_WINDOWS_FALLBACK);
            if (fallbackFile.exists()) {
                bagIconImage = ImageIO.read(fallbackFile);
                if (bagIconImage != null) {
                    System.out.println("Icona BAG caricata da file Windows: " + BAG_ICON_WINDOWS_FALLBACK);
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        bagIconImage = null;
        System.err.println("Icona BAG NON trovata.");
    }

    // =========================================================
    // UI ROOT
    // =========================================================
    private JPanel buildContent() {
        rootPanel = new DotPanel();
        JPanel root = rootPanel;
        root.setLayout(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(18, 22, 18, 22));

        JLabel title = new JLabel("MondoLux Recipes - " + VERSION, SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 34));
        title.setForeground(Color.BLACK);
        root.add(title, BorderLayout.NORTH);

        root.add(buildCenterArea(), BorderLayout.CENTER);
        root.add(buildBottomArea(), BorderLayout.SOUTH);

        return root;
    }

    private JPanel buildCenterArea() {
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setOpaque(false);

        JPanel left = new JPanel(new BorderLayout(5, 5));
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(320, 600));

        JLabel lblRecipes = new JLabel("Recettes");
        lblRecipes.setFont(new Font("SansSerif", Font.BOLD, 20));
        left.add(lblRecipes, BorderLayout.NORTH);

        lstRecipes.setFont(new Font("SansSerif", Font.PLAIN, 18));
        left.add(new JScrollPane(lstRecipes), BorderLayout.CENTER);

        JPanel leftButtons = new JPanel(new GridLayout(0, 1, 5, 5));
        leftButtons.setOpaque(false);

        JButton btnRefresh = new JButton("Actualiser la liste");
        JButton btnOpen = new JButton("Ouvrir");
        JButton btnDownloadAll = new JButton("DOWNLOAD");
        JButton btnSetup = new JButton("SETUP");

        Font buttonFont = new Font("SansSerif", Font.BOLD, 16);
        btnRefresh.setFont(buttonFont);
        btnOpen.setFont(buttonFont);
        btnDownloadAll.setFont(buttonFont);
        btnSetup.setFont(buttonFont);

        leftButtons.add(btnRefresh);
        leftButtons.add(btnOpen);
        leftButtons.add(btnDownloadAll);
        leftButtons.add(btnSetup);
        left.add(leftButtons, BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout(10, 10));
        right.setOpaque(false);

        JPanel topInfo = new JPanel(new BorderLayout(10, 10));
        topInfo.setOpaque(false);

        JPanel infoLeft = new JPanel(new GridLayout(3, 2, 8, 8));
        infoLeft.setOpaque(false);

        JLabel lblIdTitle = new JLabel("ID Recette:");
        JLabel lblDescTitle = new JLabel("Description:");
        JLabel lblLastTitle = new JLabel("Dernière recette envoyée:");

        Font labelFont = new Font("SansSerif", Font.BOLD, 18);
        Font valueFont = new Font("SansSerif", Font.PLAIN, 18);

        lblIdTitle.setFont(labelFont);
        lblDescTitle.setFont(labelFont);
        lblLastTitle.setFont(labelFont);

        lblRecipeId.setFont(valueFont);
        lblLastDownloaded.setFont(valueFont);
        txtDescription.setFont(valueFont);

        infoLeft.add(lblIdTitle);
        infoLeft.add(lblRecipeId);
        infoLeft.add(lblDescTitle);
        infoLeft.add(txtDescription);
        infoLeft.add(lblLastTitle);
        infoLeft.add(lblLastDownloaded);

        txtDescription.setEditable(false);

        topInfo.add(infoLeft, BorderLayout.CENTER);

        right.add(topInfo, BorderLayout.NORTH);
        right.add(tabSteps, BorderLayout.CENTER);

        main.add(left, BorderLayout.WEST);
        main.add(right, BorderLayout.CENTER);

        btnRefresh.addActionListener(e -> {
            try {
                loadRecipeList();
            } catch (Exception ex) {
                showError("Erreur chargement liste", ex);
            }
        });

        btnOpen.addActionListener(e -> openSelectedRecipe());



        btnDownloadAll.addActionListener(e -> {
            Integer id = getSelectedRecipeId();
            if (id == null) {
                setErrorStatus("Sélectionnez une recette.");
                return;
            }

            String recipeName = getSelectedRecipeName();
            if (showDownloadConfirmationDialog(id, recipeName)) {
                startRecipeDownloadWithDialog(id);
            } else {
                setInfoStatus("Download annulé: recette " + id);
            }
        });

        btnSetup.addActionListener(e -> openRecipeSetupWithAuthentication());

        lstRecipes.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(evt)) {
                    int index = lstRecipes.locationToIndex(evt.getPoint());
                    if (index >= 0) {
                        Rectangle bounds = lstRecipes.getCellBounds(index, index);
                        if (bounds != null && bounds.contains(evt.getPoint())) {
                            lstRecipes.setSelectedIndex(index);

                            String value = recipeListModel.getElementAt(index);
                            if (value != null && value.contains(" - ")) {
                                try {
                                    int id = Integer.parseInt(value.substring(0, value.indexOf(" - ")).trim());
                                    openRecipeById(id);
                                } catch (Exception ex) {
                                    setErrorStatus("ID recette non valide.");
                                }
                            }
                        }
                    }
                }
            }
        });

        return main;
    }

    private JPanel buildBottomArea() {
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(22, 0, 0, 0));

        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        centerPanel.setOpaque(false);
        lblStatus.setFont(new Font("SansSerif", Font.BOLD, 18));
        lblStatus.setForeground(new Color(0, 120, 0));
        centerPanel.add(lblStatus);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightPanel.setOpaque(false);

        JButton btnBackToMain0 = createBagIconButton("BAG", this::openMain0);
        rightPanel.add(btnBackToMain0);

        bottom.add(centerPanel, BorderLayout.CENTER);
        bottom.add(rightPanel, BorderLayout.EAST);

        return bottom;
    }

    private JButton createBagIconButton(String toolTip, final Runnable action) {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color top = new Color(238, 238, 238);
                Color bottom = new Color(198, 198, 198);

                if (getModel().isRollover()) {
                    top = new Color(248, 248, 248);
                    bottom = new Color(215, 215, 215);
                }

                if (getModel().isPressed()) {
                    top = new Color(188, 188, 188);
                    bottom = new Color(165, 165, 165);
                }

                GradientPaint gp = new GradientPaint(0, 0, top, 0, getHeight(), bottom);
                g2.setPaint(gp);
                g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 14, 14);

                g2.setColor(new Color(140, 140, 140));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 14, 14);

                g2.setColor(new Color(255, 255, 255, 120));
                g2.drawRoundRect(2, 2, getWidth() - 5, (getHeight() / 2), 12, 12);

                drawBagIcon(g2, getWidth(), getHeight());

                g2.dispose();
            }
        };

        button.setPreferredSize(new Dimension(78, 60));
        button.setMinimumSize(new Dimension(78, 60));
        button.setMaximumSize(new Dimension(78, 60));
        button.setRolloverEnabled(true);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFocusable(false);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBorderPainted(true);
        button.setBorder(new ThinIconBorder());
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setToolTipText(toolTip);
        button.addActionListener(e -> action.run());

        return button;
    }

    private void drawBagIcon(Graphics2D g2, int w, int h) {
        if (bagIconImage != null) {
            int margin = Math.max(7, Math.min(w, h) / 7);
            g2.drawImage(bagIconImage, margin, margin, w - 2 * margin, h - 2 * margin, null);
        } else {
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(10, h / 3)));
            FontMetrics fm = g2.getFontMetrics();
            String txt = "EXIT";
            int tx = (w - fm.stringWidth(txt)) / 2;
            int ty = (h + fm.getAscent()) / 2 - 3;
            g2.drawString(txt, tx, ty);
        }
    }

    private void openSelectedRecipe() {
        Integer id = getSelectedRecipeId();
        if (id == null) {
            setErrorStatus("Sélectionnez une recette.");
            return;
        }

        try {
            recipeOpen(id);
        } catch (Exception ex) {
            showError("Erreur ouverture recette", ex);
        }
    }

    private void openRecipeById(int id) {
        try {
            recipeOpen(id);
        } catch (Exception ex) {
            showError("Erreur ouverture recette", ex);
        }
    }

    private Integer getSelectedRecipeId() {
        String selected = lstRecipes.getSelectedValue();
        if (selected == null || !selected.contains(" - ")) {
            return null;
        }

        try {
            return Integer.parseInt(selected.substring(0, selected.indexOf(" - ")).trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String getSelectedRecipeName() {
        String selected = lstRecipes.getSelectedValue();
        if (selected != null && selected.contains(" - ")) {
            String name = selected.substring(selected.indexOf(" - ") + 3).trim();
            if (!name.isBlank()) {
                return name;
            }
        }

        String description = txtDescription.getText();
        if (description != null && !description.trim().isBlank()) {
            return description.trim();
        }

        return "-";
    }

    private boolean showDownloadConfirmationDialog(int recipeId, String recipeName) {
        final JDialog dialog = new JDialog(this, "Confirmation DOWNLOAD", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        final boolean[] confirmed = new boolean[]{false};

        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setBorder(new EmptyBorder(22, 26, 22, 26));

        JLabel title = new JLabel("CONFIRMATION DOWNLOAD", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        title.setForeground(Color.BLACK);

        JPanel summary = new JPanel(new GridLayout(2, 2, 10, 10));
        summary.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(120, 120, 120), 2),
                new EmptyBorder(14, 14, 14, 14)
        ));

        JLabel lblId = new JLabel("ID Recette:");
        JLabel lblName = new JLabel("Nom recette:");
        JLabel valId = new JLabel(String.valueOf(recipeId));
        JLabel valName = new JLabel(recipeName == null || recipeName.isBlank() ? "-" : recipeName);

        Font labelFont = new Font("SansSerif", Font.BOLD, 18);
        Font valueFont = new Font("SansSerif", Font.PLAIN, 18);

        lblId.setFont(labelFont);
        lblName.setFont(labelFont);
        valId.setFont(valueFont);
        valName.setFont(valueFont);

        summary.add(lblId);
        summary.add(valId);
        summary.add(lblName);
        summary.add(valName);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        JButton cancelButton = new JButton("Annuler");
        JButton confirmButton = new JButton("CONFIRMER DOWNLOAD");

        cancelButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        confirmButton.setFont(new Font("SansSerif", Font.BOLD, 16));

        cancelButton.addActionListener(e -> dialog.dispose());
        confirmButton.addActionListener(e -> {
            confirmed[0] = true;
            dialog.dispose();
        });

        buttons.add(cancelButton);
        buttons.add(confirmButton);

        panel.add(title, BorderLayout.NORTH);
        panel.add(summary, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(panel);
        dialog.setSize(560, 270);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        return confirmed[0];
    }


    // =========================================================
    // UI STATE PERSISTENCE
    // =========================================================
    private void saveUiState() {
        try {
            File stateFile = new File(UI_STATE_FILE);
            File parent = stateFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }

            Properties props = new Properties();

            Integer selectedId = getSelectedRecipeId();
            String recipeIdText = lblRecipeId.getText() == null ? "" : lblRecipeId.getText().trim();

            if (selectedId != null) {
                props.setProperty("selectedRecipeId", String.valueOf(selectedId));
            } else if (recipeIdText.matches("\\d+")) {
                props.setProperty("selectedRecipeId", recipeIdText);
            }

            props.setProperty("recipeIdLabel", recipeIdText);
            props.setProperty("description", txtDescription.getText() == null ? "" : txtDescription.getText());
            props.setProperty("lastDownloaded", lblLastDownloaded.getText() == null ? "" : lblLastDownloaded.getText());
            props.setProperty("selectedTabIndex", String.valueOf(tabSteps.getSelectedIndex()));
            props.setProperty("status", lblStatus.getText() == null ? "" : lblStatus.getText());
            props.setProperty("adUsername", lastAdUsername == null ? "" : lastAdUsername);
            props.setProperty("version", VERSION);
            props.setProperty("savedAt", LocalDateTime.now().toString());

            try (FileWriter fw = new FileWriter(stateFile)) {
                props.store(fw, "MondoLuxRecipesJava UI state");
            }

            System.out.println("UI state saved: " + stateFile.getAbsolutePath());
        } catch (Exception ex) {
            System.out.println("Erreur sauvegarde UI state: " + ex.getMessage());
        }
    }

    private void restoreUiState() {
        File stateFile = new File(UI_STATE_FILE);
        if (!stateFile.exists()) {
            return;
        }

        try (FileReader fr = new FileReader(stateFile)) {
            Properties props = new Properties();
            props.load(fr);

            lastAdUsername = props.getProperty("adUsername", "").trim();

            String lastDownloaded = props.getProperty("lastDownloaded", "").trim();
            if (!lastDownloaded.isEmpty()) {
                lblLastDownloaded.setText(lastDownloaded);
                try {
                    lastDownloadedRecipeId = Integer.parseInt(lastDownloaded);
                } catch (Exception ignored) {
                }
            }

            String selectedRecipeIdText = props.getProperty("selectedRecipeId", "").trim();
            if (!selectedRecipeIdText.isEmpty()) {
                try {
                    int recipeId = Integer.parseInt(selectedRecipeIdText);
                    selectRecipeInList(recipeId);
                    recipeOpen(recipeId);

                    String tabText = props.getProperty("selectedTabIndex", "-1").trim();
                    try {
                        int tabIndex = Integer.parseInt(tabText);
                        if (tabIndex >= 0 && tabIndex < tabSteps.getTabCount()) {
                            tabSteps.setSelectedIndex(tabIndex);
                        }
                    } catch (Exception ignored) {
                    }

                    setInfoStatus("Dernier état restauré: recette " + recipeId + " - " + VERSION);
                    return;
                } catch (Exception ex) {
                    System.out.println("Erreur restauration recette UI: " + ex.getMessage());
                }
            }

            String recipeIdLabel = props.getProperty("recipeIdLabel", "").trim();
            String description = props.getProperty("description", "");
            if (!recipeIdLabel.isEmpty()) {
                lblRecipeId.setText(recipeIdLabel);
            }
            if (!description.isEmpty()) {
                txtDescription.setText(description);
            }

            setInfoStatus("Dernier état restauré - " + VERSION);
        } catch (Exception ex) {
            System.out.println("Erreur lecture UI state: " + ex.getMessage());
        }
    }

    private void selectRecipeInList(int recipeId) {
        String prefix = recipeId + " - ";
        for (int i = 0; i < recipeListModel.getSize(); i++) {
            String item = recipeListModel.getElementAt(i);
            if (item != null && item.startsWith(prefix)) {
                lstRecipes.setSelectedIndex(i);
                lstRecipes.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    // =========================================================
    // SETUP RECETTES
    // =========================================================
    private void openRecipeSetupWithAuthentication() {
        ActiveDirectorySetup.AuthenticatedUser user = getAuthenticatedUserForEditMode();

        if (user == null) {
            setInfoStatus("SETUP annulé: authentification Active Directory non confirmée.");
            return;
        }

        try {
            logRecipeSetupModification("OUVERTURE_SETUP", null, null, null, null, user);
            showRecipeSetupDialog(user);
        } catch (Exception ex) {
            showError("Erreur SETUP recettes", ex);
        }
    }

    private void showRecipeSetupDialog(ActiveDirectorySetup.AuthenticatedUser setupUser) {
        final JDialog dialog = new JDialog(this, "SETUP Recettes", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        DefaultTableModel model = new DefaultTableModel(new Object[]{"ID", "Nom / Description"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Integer.class : String.class;
            }
        };

        JTable table = new JTable(model);
        table.setRowHeight(32);
        table.setFont(new Font("SansSerif", Font.PLAIN, 18));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 18));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setMinWidth(50);
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        table.getColumnModel().getColumn(0).setMaxWidth(90);

        JTextField txtFilter = new JTextField();
        txtFilter.setFont(new Font("SansSerif", Font.PLAIN, 18));

        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        txtFilter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                String filter = txtFilter.getText() == null ? "" : txtFilter.getText().trim();
                if (filter.isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(filter)));
                }
            }

            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });

        JButton btnRefresh = new JButton("Actualiser");
        JButton btnNew = new JButton("Nouvelle recette");
        JButton btnCopy = new JButton("COPIA");
        JButton btnEdit = new JButton("Modifier ID / Nom");
        JButton btnMoveUp = new JButton("Monter");
        JButton btnMoveDown = new JButton("Descendre");
        JButton btnDelete = new JButton("Supprimer");
        JButton btnClose = new JButton("FERMER");

        Font buttonFont = new Font("SansSerif", Font.BOLD, 16);
        btnRefresh.setFont(buttonFont);
        btnNew.setFont(buttonFont);
        btnCopy.setFont(buttonFont);
        btnEdit.setFont(buttonFont);
        btnMoveUp.setFont(buttonFont);
        btnMoveDown.setFont(buttonFont);
        btnDelete.setFont(buttonFont);
        btnClose.setFont(buttonFont);

        Runnable reload = () -> {
            try {
                loadRecipeSetupModel(model);
            } catch (Exception ex) {
                showError("Erreur chargement SETUP recettes", ex);
            }
        };

        btnRefresh.addActionListener(e -> reload.run());

        btnNew.addActionListener(e -> {
            if (showCreateRecipeDialog(dialog, setupUser)) {
                reload.run();
                try {
                    loadRecipeList();
                } catch (Exception ex) {
                    showError("Erreur actualisation liste recettes", ex);
                }
            }
        });

        btnCopy.addActionListener(e -> {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) {
                JOptionPane.showMessageDialog(dialog, "Sélectionnez une recette à copier.", "SETUP", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int modelRow = table.convertRowIndexToModel(viewRow);
            int sourceId = ((Number) model.getValueAt(modelRow, 0)).intValue();
            String sourceDesc = String.valueOf(model.getValueAt(modelRow, 1));

            Integer newId = showCopyRecipeDialog(dialog, sourceId, sourceDesc, setupUser);
            if (newId != null) {
                reload.run();
                try {
                    loadRecipeList();
                    recipeCache.clear();
                    selectRecipeInSetupTable(table, model, newId);
                    selectRecipeInList(newId);
                } catch (Exception ex) {
                    showError("Erreur actualisation liste recettes", ex);
                }
            }
        });

        btnEdit.addActionListener(e -> {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) {
                JOptionPane.showMessageDialog(dialog, "Sélectionnez une recette.", "SETUP", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int modelRow = table.convertRowIndexToModel(viewRow);
            int oldId = ((Number) model.getValueAt(modelRow, 0)).intValue();
            String oldDesc = String.valueOf(model.getValueAt(modelRow, 1));

            if (showEditRecipeDialog(dialog, oldId, oldDesc, setupUser)) {
                reload.run();
                try {
                    loadRecipeList();
                    recipeCache.clear();
                } catch (Exception ex) {
                    showError("Erreur actualisation liste recettes", ex);
                }
            }
        });

        btnMoveUp.addActionListener(e -> {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) {
                JOptionPane.showMessageDialog(dialog, "Sélectionnez une recette.", "SETUP", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int modelRow = table.convertRowIndexToModel(viewRow);
            int id = ((Number) model.getValueAt(modelRow, 0)).intValue();

            try {
                int newId = moveRecipeInSetup(id, -1);
                logRecipeSetupModification("DEPLACEMENT_RECETTE_MONTER", id, String.valueOf(model.getValueAt(modelRow, 1)), newId, null, setupUser);
                reload.run();
                selectRecipeInSetupTable(table, model, newId);
                loadRecipeList();
                recipeCache.clear();
                setOkStatus("Recette déplacée vers le haut: " + id + " -> " + newId);
            } catch (Exception ex) {
                showError("Erreur déplacement recette", ex);
            }
        });

        btnMoveDown.addActionListener(e -> {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) {
                JOptionPane.showMessageDialog(dialog, "Sélectionnez une recette.", "SETUP", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int modelRow = table.convertRowIndexToModel(viewRow);
            int id = ((Number) model.getValueAt(modelRow, 0)).intValue();

            try {
                int newId = moveRecipeInSetup(id, 1);
                logRecipeSetupModification("DEPLACEMENT_RECETTE_DESCENDRE", id, String.valueOf(model.getValueAt(modelRow, 1)), newId, null, setupUser);
                reload.run();
                selectRecipeInSetupTable(table, model, newId);
                loadRecipeList();
                recipeCache.clear();
                setOkStatus("Recette déplacée vers le bas: " + id + " -> " + newId);
            } catch (Exception ex) {
                showError("Erreur déplacement recette", ex);
            }
        });

        btnDelete.addActionListener(e -> {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) {
                JOptionPane.showMessageDialog(dialog, "Sélectionnez une recette.", "SETUP", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int modelRow = table.convertRowIndexToModel(viewRow);
            int id = ((Number) model.getValueAt(modelRow, 0)).intValue();
            String desc = String.valueOf(model.getValueAt(modelRow, 1));

            int confirm = JOptionPane.showConfirmDialog(
                    dialog,
                    "Voulez-vous vraiment supprimer la recette " + id + " - " + desc + " ?\n\n"
                            + "Les lignes RecipesSteps liées à cette recette seront également supprimées.",
                    "Confirmation suppression",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    deleteRecipeFromDb(id);
                    logRecipeSetupModification("SUPPRESSION_RECETTE_RENUMEROTATION", id, desc, null, "Renumbering automatique après suppression", setupUser);
                    reload.run();
                    loadRecipeList();
                    recipeCache.clear();
                    if (lblRecipeId.getText() != null && lblRecipeId.getText().trim().equals(String.valueOf(id))) {
                        lblRecipeId.setText("-");
                        txtDescription.setText("");
                        tabSteps.removeAll();
                    }
                    setOkStatus("Recette supprimée et numéros recalculés à partir de " + id);
                } catch (Exception ex) {
                    showError("Erreur suppression recette", ex);
                }
            }
        });

        btnClose.addActionListener(e -> dialog.dispose());
        // Chiusura del SETUP (FERMER o X): uscita dalla modalita' modification
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                deactivateEditMode();
                setInfoStatus("SETUP fermé - mode modification terminé.");
            }
        });

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.setBorder(new EmptyBorder(8, 8, 8, 8));
        JLabel lblFilter = new JLabel("Filtre:");
        lblFilter.setFont(new Font("SansSerif", Font.BOLD, 18));
        top.add(lblFilter, BorderLayout.WEST);
        top.add(txtFilter, BorderLayout.CENTER);

        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        actionButtons.add(btnRefresh);
        actionButtons.add(btnNew);
        actionButtons.add(btnCopy);
        actionButtons.add(btnEdit);
        actionButtons.add(btnMoveUp);
        actionButtons.add(btnMoveDown);
        actionButtons.add(btnDelete);

        JPanel closeButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btnClose.setPreferredSize(new Dimension(140, 42));
        btnClose.setFocusPainted(false);
        btnClose.setBackground(new Color(225, 225, 225));
        btnClose.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(120, 120, 120), 1),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)
        ));
        closeButtons.add(btnClose);

        JPanel bottomButtons = new JPanel(new BorderLayout(0, 0));
        bottomButtons.add(actionButtons, BorderLayout.CENTER);
        bottomButtons.add(closeButtons, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.add(top, BorderLayout.NORTH);
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        root.add(bottomButtons, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.setSize(940, 720);
        dialog.setLocationRelativeTo(this);

        reload.run();
        dialog.setVisible(true);
    }

    // V2.02 - L'identificatore di processo NON e' dbo.Recipes.Id (rinumerabile).
    // RecipeUID viene generato da SQL Server, non dall'operatore; il DB ne
    // protegge anche l'immutabilita' mediante trigger.
    private void ensureRecipeUidSchema() throws Exception {
        try (Connection c = openConnection();
             PreparedStatement p = c.prepareStatement(
                 "SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('dbo.Recipes') AND name='RecipeUID'");
             ResultSet r = p.executeQuery()) {
            if (!r.next() || r.getInt(1) != 1) {
                throw new IllegalStateException("Migration RecipeUID non installata: eseguire SQL_RecipeUID_V1.sql prima di utilizzare SETUP.");
            }
        }
    }

    private void loadRecipeSetupModel(DefaultTableModel model) throws Exception {
        model.setRowCount(0);

        try (Connection conn = openConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT Id, Descrizione FROM dbo.Recipes ORDER BY Id")) {

            while (rs.next()) {
                int id = rs.getInt("Id");
                String desc = "";
                try {
                    desc = rs.getString("Descrizione");
                } catch (Exception ignored) {
                }
                model.addRow(new Object[]{id, desc == null ? "" : desc});
            }
        }
    }


    private Integer showCopyRecipeDialog(Component owner, int sourceId, String sourceDesc, ActiveDirectorySetup.AuthenticatedUser setupUser) {
        int proposedId;
        try {
            proposedId = getNextRecipeId();
        } catch (Exception ex) {
            showError("Erreur calcul nouvel ID recette", ex);
            return null;
        }

        String baseDesc = sourceDesc == null ? "" : sourceDesc.trim();
        String proposedDesc = baseDesc.isEmpty() ? "COPIE DE " + sourceId : baseDesc + " - COPIE";

        JTextField txtSource = new JTextField(sourceId + " - " + baseDesc, 28);
        JTextField txtId = new JTextField(String.valueOf(proposedId), 12);
        JTextField txtDesc = new JTextField(proposedDesc, 28);
        txtSource.setEditable(false);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createSetupGbc();

        addSetupFormRow(panel, gbc, 0, "Recette source:", txtSource);
        addSetupFormRow(panel, gbc, 1, "Nouvel ID recette:", txtId);
        addSetupFormRow(panel, gbc, 2, "Nouveau nom:", txtDesc);

        int result = JOptionPane.showConfirmDialog(
                owner,
                panel,
                "COPIA recette " + sourceId,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        try {
            int newId = parsePositiveRecipeId(txtId.getText());
            String newDesc = txtDesc.getText() == null ? "" : txtDesc.getText().trim();
            copyRecipeInDb(sourceId, newId, newDesc);
            logRecipeSetupModification("COPIE_RECETTE", sourceId, sourceDesc, newId, newDesc, setupUser);
            setOkStatus("Recette copiée: " + sourceId + " -> " + newId + " - " + newDesc);
            return newId;
        } catch (Exception ex) {
            showError("Erreur copie recette", ex instanceof Exception ? (Exception) ex : new Exception(ex));
            return null;
        }
    }

    private boolean showCreateRecipeDialog(Component owner, ActiveDirectorySetup.AuthenticatedUser setupUser) {
        JTextField txtId = new JTextField(12);
        JTextField txtDesc = new JTextField(28);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createSetupGbc();

        addSetupFormRow(panel, gbc, 0, "Nouvel ID recette:", txtId);
        addSetupFormRow(panel, gbc, 1, "Nom / Description:", txtDesc);

        int result = JOptionPane.showConfirmDialog(
                owner,
                panel,
                "Nouvelle recette",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return false;
        }

        try {
            int id = parsePositiveRecipeId(txtId.getText());
            String desc = txtDesc.getText() == null ? "" : txtDesc.getText().trim();
            createRecipeInDb(id, desc);
            logRecipeSetupModification("CREATION_RECETTE", null, null, id, desc, setupUser);
            setOkStatus("Nouvelle recette créée: " + id + " - " + desc);
            return true;
        } catch (Exception ex) {
            showError("Erreur création recette", ex instanceof Exception ? (Exception) ex : new Exception(ex));
            return false;
        }
    }

    private boolean showEditRecipeDialog(Component owner, int oldId, String oldDesc, ActiveDirectorySetup.AuthenticatedUser setupUser) {
        JTextField txtId = new JTextField(String.valueOf(oldId), 12);
        JTextField txtDesc = new JTextField(oldDesc == null ? "" : oldDesc, 28);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createSetupGbc();

        addSetupFormRow(panel, gbc, 0, "ID recette:", txtId);
        addSetupFormRow(panel, gbc, 1, "Nom / Description:", txtDesc);

        int result = JOptionPane.showConfirmDialog(
                owner,
                panel,
                "Modifier recette " + oldId,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return false;
        }

        try {
            int newId = parsePositiveRecipeId(txtId.getText());
            String newDesc = txtDesc.getText() == null ? "" : txtDesc.getText().trim();
            updateRecipeHeaderInDb(oldId, newId, newDesc);
            logRecipeSetupModification("MODIFICATION_RECETTE", oldId, oldDesc, newId, newDesc, setupUser);
            setOkStatus("Recette modifiée: " + oldId + " -> " + newId + " - " + newDesc);
            return true;
        } catch (Exception ex) {
            showError("Erreur modification recette", ex instanceof Exception ? (Exception) ex : new Exception(ex));
            return false;
        }
    }

    private GridBagConstraints createSetupGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        return gbc;
    }

    private void addSetupFormRow(JPanel panel, GridBagConstraints gbc, int row, String label, JTextField field) {
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 16));
        field.setFont(new Font("SansSerif", Font.PLAIN, 16));

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        panel.add(lbl, gbc);

        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1;
        panel.add(field, gbc);
    }

    private int parsePositiveRecipeId(String text) {
        try {
            int id = Integer.parseInt(text == null ? "" : text.trim());
            if (id <= 0) {
                throw new IllegalArgumentException("L'ID recette doit être supérieur à 0.");
            }
            return id;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("ID recette non valide.");
        }
    }


    private int getNextRecipeId() throws Exception {
        try (Connection conn = openConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT ISNULL(MAX(Id), 0) + 1 FROM dbo.Recipes")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 1;
    }

    private void copyRecipeInDb(int sourceId, int newId, String newDescription) throws Exception {
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);

            try {
                if (!recipeExists(conn, sourceId)) {
                    throw new SQLException("La recette source " + sourceId + " est introuvable.");
                }
                if (recipeExists(conn, newId)) {
                    throw new SQLException("La recette " + newId + " existe déjà.");
                }

                insertRecipeHeader(conn, newId, newDescription);
                copyRecipeSteps(conn, sourceId, newId);

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void copyRecipeSteps(Connection conn, int sourceId, int newId) throws SQLException {
        List<String> columns = new ArrayList<>();

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT TOP 0 * FROM dbo.RecipesSteps")) {

            ResultSetMetaData md = rs.getMetaData();

            for (int i = 1; i <= md.getColumnCount(); i++) {
                String col = md.getColumnName(i);
                if ("Id".equalsIgnoreCase(col)) {
                    continue;
                }
                columns.add(col);
            }
        }

        if (columns.isEmpty()) {
            throw new SQLException("Structure dbo.RecipesSteps non valide: aucune colonne à copier.");
        }

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO dbo.RecipesSteps (Id");
        for (String col : columns) {
            sql.append(", [").append(col.replace("]", "]]")) .append("]");
        }
        sql.append(") SELECT ?");
        for (String col : columns) {
            sql.append(", [").append(col.replace("]", "]]")) .append("]");
        }
        sql.append(" FROM dbo.RecipesSteps WHERE Id=? ORDER BY Passo");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, newId);
            ps.setInt(2, sourceId);
            int inserted = ps.executeUpdate();
            if (inserted <= 0) {
                throw new SQLException("Aucune ligne RecipesSteps copiée depuis la recette " + sourceId + ".");
            }
        }
    }

    private void createRecipeInDb(int id, String description) throws Exception {
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);

            try {
                if (recipeExists(conn, id)) {
                    throw new SQLException("La recette " + id + " existe déjà.");
                }

                insertRecipeHeader(conn, id, description);
                insertEmptyRecipeSteps(conn, id);

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void updateRecipeHeaderInDb(int oldId, int newId, String description) throws Exception {
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);

            try {
                if (oldId != newId && recipeExists(conn, newId)) {
                    throw new SQLException("La recette " + newId + " existe déjà.");
                }

                if (oldId == newId) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE dbo.Recipes SET Descrizione=?, Modifica=? WHERE Id=?")) {
                        ps.setString(1, description);
                        ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                        ps.setInt(3, oldId);
                        int updated = ps.executeUpdate();
                        if (updated != 1) {
                            throw new SQLException("Modification non confirmée. Righe modificate=" + updated);
                        }
                    }
                } else {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE dbo.Recipes SET Id=?, Descrizione=?, Modifica=? WHERE Id=?")) {
                        ps.setInt(1, newId);
                        ps.setString(2, description);
                        ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                        ps.setInt(4, oldId);
                        int updated = ps.executeUpdate();
                        if (updated != 1) {
                            throw new SQLException("Modification ID non confirmée. Righe modificate=" + updated);
                        }
                    }

                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE dbo.RecipesSteps SET Id=? WHERE Id=?")) {
                        ps.setInt(1, newId);
                        ps.setInt(2, oldId);
                        ps.executeUpdate();
                    }

                    if (lastDownloadedRecipeId != null && lastDownloadedRecipeId == oldId) {
                        lastDownloadedRecipeId = newId;
                    }
                }

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void deleteRecipeFromDb(int id) throws Exception {
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);

            try {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM dbo.RecipesSteps WHERE Id=?")) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM dbo.Recipes WHERE Id=?")) {
                    ps.setInt(1, id);
                    int deleted = ps.executeUpdate();
                    if (deleted != 1) {
                        throw new SQLException("Suppression non confirmée. Righe eliminate=" + deleted);
                    }
                }

                List<RecipeSetupEntry> entries = loadRecipeSetupEntries(conn);
                applySequentialRecipeOrder(conn, entries);

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private int moveRecipeInSetup(int recipeId, int direction) throws Exception {
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);

            try {
                List<RecipeSetupEntry> entries = loadRecipeSetupEntries(conn);
                int index = findRecipeSetupIndex(entries, recipeId);
                if (index < 0) {
                    throw new SQLException("Recette introuvable: " + recipeId);
                }

                int newIndex = index + direction;
                if (newIndex < 0 || newIndex >= entries.size()) {
                    return recipeId;
                }

                java.util.Collections.swap(entries, index, newIndex);
                applySequentialRecipeOrder(conn, entries);

                conn.commit();
                return newIndex + 1;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private int moveRecipeAfterInSetup(int recipeId, int targetAfterId) throws Exception {
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);

            try {
                List<RecipeSetupEntry> entries = loadRecipeSetupEntries(conn);
                int oldIndex = findRecipeSetupIndex(entries, recipeId);
                if (oldIndex < 0) {
                    throw new SQLException("Recette introuvable: " + recipeId);
                }

                RecipeSetupEntry moved = entries.remove(oldIndex);

                int insertIndex;
                if (targetAfterId <= 0) {
                    insertIndex = 0;
                } else {
                    int targetIndex = findRecipeSetupIndex(entries, targetAfterId);
                    if (targetIndex < 0) {
                        throw new SQLException("ID cible introuvable: " + targetAfterId);
                    }
                    insertIndex = targetIndex + 1;
                }

                entries.add(insertIndex, moved);
                applySequentialRecipeOrder(conn, entries);

                conn.commit();
                return insertIndex + 1;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private List<RecipeSetupEntry> loadRecipeSetupEntries(Connection conn) throws SQLException {
        List<RecipeSetupEntry> entries = new ArrayList<>();

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT Id, Descrizione FROM dbo.Recipes ORDER BY Id")) {
            while (rs.next()) {
                entries.add(new RecipeSetupEntry(
                        rs.getInt("Id"),
                        rs.getString("Descrizione")
                ));
            }
        }

        return entries;
    }

    private int findRecipeSetupIndex(List<RecipeSetupEntry> entries, int id) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id == id) {
                return i;
            }
        }
        return -1;
    }

    private void applySequentialRecipeOrder(Connection conn, List<RecipeSetupEntry> entries) throws SQLException {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        for (int i = 0; i < entries.size(); i++) {
            RecipeSetupEntry entry = entries.get(i);
            int tempId = -1000000 - i;

            try (PreparedStatement ps = conn.prepareStatement("UPDATE dbo.Recipes SET Id=?, Modifica=? WHERE Id=?")) {
                ps.setInt(1, tempId);
                ps.setTimestamp(2, now);
                ps.setInt(3, entry.id);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement("UPDATE dbo.RecipesSteps SET Id=? WHERE Id=?")) {
                ps.setInt(1, tempId);
                ps.setInt(2, entry.id);
                ps.executeUpdate();
            }

            entry.id = tempId;
        }

        for (int i = 0; i < entries.size(); i++) {
            RecipeSetupEntry entry = entries.get(i);
            int finalId = i + 1;

            try (PreparedStatement ps = conn.prepareStatement("UPDATE dbo.Recipes SET Id=?, Modifica=? WHERE Id=?")) {
                ps.setInt(1, finalId);
                ps.setTimestamp(2, now);
                ps.setInt(3, entry.id);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement("UPDATE dbo.RecipesSteps SET Id=? WHERE Id=?")) {
                ps.setInt(1, finalId);
                ps.setInt(2, entry.id);
                ps.executeUpdate();
            }

            entry.id = finalId;
        }
    }

    private void selectRecipeInSetupTable(JTable table, DefaultTableModel model, int id) {
        for (int i = 0; i < model.getRowCount(); i++) {
            Object value = model.getValueAt(i, 0);
            if (value instanceof Number && ((Number) value).intValue() == id) {
                int viewRow = table.convertRowIndexToView(i);
                if (viewRow >= 0) {
                    table.setRowSelectionInterval(viewRow, viewRow);
                    table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
                }
                return;
            }
        }
    }

    private static class RecipeSetupEntry {
        int id;
        String description;

        RecipeSetupEntry(int id, String description) {
            this.id = id;
            this.description = description;
        }
    }

    private boolean recipeExists(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM dbo.Recipes WHERE Id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private void insertRecipeHeader(Connection conn, int id, String description) throws SQLException {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO dbo.Recipes (Id, Descrizione, Creazione, Modifica) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, id);
                ps.setString(2, description);
                ps.setTimestamp(3, now);
                ps.setTimestamp(4, now);
                ps.executeUpdate();
            }
        } catch (SQLException firstEx) {
            if (isIdentityInsertError(firstEx)) {
                try (Statement st = conn.createStatement()) {
                    st.execute("SET IDENTITY_INSERT dbo.Recipes ON");
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO dbo.Recipes (Id, Descrizione, Creazione, Modifica) VALUES (?, ?, ?, ?)")) {
                    ps.setInt(1, id);
                    ps.setString(2, description);
                    ps.setTimestamp(3, now);
                    ps.setTimestamp(4, now);
                    ps.executeUpdate();
                } finally {
                    try (Statement st = conn.createStatement()) {
                        st.execute("SET IDENTITY_INSERT dbo.Recipes OFF");
                    }
                }
            } else {
                throw firstEx;
            }
        }
    }

    private boolean isIdentityInsertError(SQLException ex) {
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        return msg.contains("identity_insert") || msg.contains("identity column");
    }

    private void insertEmptyRecipeSteps(Connection conn, int recipeId) throws SQLException {
        List<String> columns = new ArrayList<>();

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT TOP 0 * FROM dbo.RecipesSteps")) {

            ResultSetMetaData md = rs.getMetaData();

            for (int i = 1; i <= md.getColumnCount(); i++) {
                String col = md.getColumnName(i);
                if ("Id".equalsIgnoreCase(col) || "Passo".equalsIgnoreCase(col)) {
                    continue;
                }
                columns.add(col);
            }
        }

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO dbo.RecipesSteps (Id, Passo");
        for (String col : columns) {
            sql.append(", [").append(col.replace("]", "]]")).append("]");
        }
        sql.append(") VALUES (?, ?");
        for (int i = 0; i < columns.size(); i++) {
            sql.append(", ?");
        }
        sql.append(")");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int passo = 1; passo <= EXPECTED_STEP_COUNT; passo++) {
                int idx = 1;
                ps.setInt(idx++, recipeId);
                ps.setInt(idx++, passo);

                for (int i = 0; i < columns.size(); i++) {
                    ps.setInt(idx++, 0);
                }

                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // =========================================================
    // DATA STRUCTURES
    // =========================================================
    static class Var {
        int handle;
        String step;
        String name;
        String path;
        String nodeId;

        @Override
        public String toString() {
            return step + " / " + name + " -> " + path + " / " + nodeId;
        }
    }

    static class RecipeStep {
        int id;
        int passo;
        Map<String, Integer> values = new LinkedHashMap<>();
    }


    private static class RecipePLCMatchInfo {
        int recipeId;
        int matchEsatti;
        long differenzaTotale;
        String descrizione;

        boolean isMatch100() {
            return matchEsatti >= RECIPE_TOTAL_MATCHES || differenzaTotale == 0L;
        }

        double getPercent() {
            if (RECIPE_TOTAL_MATCHES <= 0) {
                return 0.0;
            }
            double p = (matchEsatti * 100.0) / RECIPE_TOTAL_MATCHES;
            if (p < 0.0) return 0.0;
            if (p > 100.0) return 100.0;
            return p;
        }

        String toDisplayLine() {
            return "Ricetta=" + recipeId
                    + " | match=" + matchEsatti + "/" + RECIPE_TOTAL_MATCHES
                    + " | diff=" + differenzaTotale
                    + " | percent=" + String.format(java.util.Locale.US, "%.2f", getPercent()) + "%";
        }
    }

    // =========================================================
    // STATUS
    // =========================================================
    private void setOkStatus(String message) {
        stopErrorBlink();
        lblStatus.setForeground(new Color(0, 120, 0));
        lblStatus.setText(message);
    }

    private void setInfoStatus(String message) {
        stopErrorBlink();
        lblStatus.setForeground(Color.BLACK);
        lblStatus.setText(message);
    }

    private void setErrorStatus(String message) {
        lblStatus.setText("ERREUR: " + message);
        startErrorBlink();
    }

    private void startErrorBlink() {
        stopErrorBlink();
        errorBlinkOn = false;
        errorBlinkTimer = new javax.swing.Timer(400, e -> {
            errorBlinkOn = !errorBlinkOn;
            lblStatus.setForeground(errorBlinkOn ? Color.RED : new Color(120, 0, 0));
        });
        errorBlinkTimer.start();
    }

    private void stopErrorBlink() {
        if (errorBlinkTimer != null) {
            errorBlinkTimer.stop();
            errorBlinkTimer = null;
        }
    }

    private void startRecipeDownloadWithDialog(int recipeId) {
        final JDialog dialog = new JDialog(this, "Téléchargement recette", false);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(20, 24, 20, 24));

        JLabel message = new JLabel("TÉLÉCHARGEMENT EN COURS", SwingConstants.CENTER);
        message.setFont(new Font("SansSerif", Font.BOLD, 24));

        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);

        JButton closeButton = new JButton("Fermer");
        closeButton.setEnabled(false);
        closeButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        closeButton.addActionListener(e -> dialog.dispose());

        panel.add(message, BorderLayout.NORTH);
        panel.add(progress, BorderLayout.CENTER);
        panel.add(closeButton, BorderLayout.SOUTH);

        dialog.setContentPane(panel);
        dialog.setSize(520, 210);
        dialog.setLocationRelativeTo(this);

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                recipeDownload(recipeId);
                return null;
            }

            @Override
            protected void done() {
                progress.setIndeterminate(false);
                closeButton.setEnabled(true);
                dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

                try {
                    get();
                    message.setForeground(new Color(0, 120, 0));
                    message.setText("TÉLÉCHARGEMENT TERMINÉ");
                    setOkStatus("Téléchargement terminé et vérifié: recette " + recipeId + " - " + VERSION);
                } catch (Exception ex) {
                    Throwable cause = ex instanceof java.util.concurrent.ExecutionException && ex.getCause() != null
                            ? ex.getCause()
                            : ex;
                    Exception realException = cause instanceof Exception
                            ? (Exception) cause
                            : new Exception(cause);
                    String friendlyCause = buildFriendlyErrorMessage(realException);

                    message.setForeground(Color.RED);
                    message.setText("<html><div style='text-align:center;'>ERREUR DE TÉLÉCHARGEMENT<br>"
                            + escapeHtml(friendlyCause)
                            + "</div></html>");
                    setErrorStatus("Erreur de téléchargement recette " + recipeId + ": " + friendlyCause);
                }
            }
        };

        dialog.setVisible(true);
        worker.execute();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }


    // =========================================================
    // FRIENDLY ERRORS
    // =========================================================
    private String buildFriendlyErrorMessage(Exception ex) {
        if (ex == null) {
            return "Erreur inconnue.";
        }

        String msg = ex.getMessage();
        String lower = msg == null ? "" : msg.toLowerCase();

        if (ex instanceof ClassNotFoundException) return "Driver SQL Server introuvable.";
        if (ex instanceof SQLTimeoutException) return "Timeout de connexion à la base SRV-BAMBURY.";
        if (ex instanceof SQLNonTransientConnectionException) return "Connexion à la base SRV-BAMBURY échouée.";

        if (ex instanceof SQLException) {
            if (lower.contains("login failed")) return "Accès à la base SRV-BAMBURY échoué.";
            if (lower.contains("cannot open database")) return "Base Mondo non disponible sur le serveur SRV-BAMBURY.";
            if (lower.contains("network-related") || lower.contains("instance-specific")) return "Serveur SRV-BAMBURY inaccessible.";
            if (lower.contains("socket") || lower.contains("connection reset") || lower.contains("connection refused")) {
                return "Connexion au serveur SRV-BAMBURY non disponible.";
            }
            if (lower.contains("timeout")) return "Timeout de connexion à la base SRV-BAMBURY.";
            return "Erreur base SRV-BAMBURY: " + msg;
        }

        return msg == null || msg.isBlank() ? ex.getClass().getSimpleName() : msg;
    }

    // =========================================================
    // OPC CONFIG REPOSITORY (sostituisce il CSV fisso)
    // =========================================================
    private void buildVars() throws Exception {
        variables.clear();
        for (MondoLuxRecipesJava.Var src : configEditor.loadConfiguration()) {
            Var v = new Var();
            v.handle = src.handle;
            v.step = src.step;
            v.name = src.name;
            v.path = src.path;
            v.nodeId = src.nodeId;
            variables.add(v);
        }
        if (variables.isEmpty()) {
            throw new IllegalStateException("Configuration OPC vide.");
        }
    }

    private Var findVar(String step, String columnName) {
        String normalizedStep = normalizeFieldName(step);
        String normalizedName = normalizeFieldName(columnName);
        for (Var v : variables) {
            if (normalizeFieldName(v.step).equals(normalizedStep)
                    && normalizeFieldName(v.name).equals(normalizedName)) {
                return v;
            }
        }
        return null;
    }

    // =========================================================
    // DB
    // =========================================================
    private Connection openConnection() throws Exception {
        return DBBamburyReader.openConnection();
    }

    private void loadRecipeList() throws Exception {
        recipeListModel.clear();

        try (Connection conn = openConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT Id, Descrizione FROM Recipes ORDER BY Id")) {

            while (rs.next()) {
                int id = rs.getInt("Id");
                String desc;
                try {
                    desc = rs.getString("Descrizione");
                } catch (SQLException ex) {
                    desc = "";
                }

                if (desc == null) desc = "";
                recipeListModel.addElement(id + " - " + desc);
            }
        }

        setOkStatus("Recettes chargées: " + recipeListModel.size());
    }

    private List<RecipeStep> loadRecipeSteps(int recipeId) throws Exception {
        List<RecipeStep> result = new ArrayList<>();

        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM RecipesSteps WHERE Id=? ORDER BY Passo")) {

            ps.setInt(1, recipeId);

            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();

                while (rs.next()) {
                    RecipeStep step = new RecipeStep();
                    step.id = rs.getInt("Id");
                    step.passo = rs.getInt("Passo");

                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        String col = md.getColumnName(i);

                        if ("Id".equalsIgnoreCase(col) || "Passo".equalsIgnoreCase(col)) continue;

                        Object value = rs.getObject(col);
                        int intValue = 0;

                        if (value instanceof Number) {
                            intValue = ((Number) value).intValue();
                        } else if (value != null) {
                            try {
                                intValue = Integer.parseInt(value.toString().trim());
                            } catch (Exception ignored) {
                            }
                        }

                        step.values.put(col, intValue);
                    }

                    result.add(step);
                }
            }
        }

        return result;
    }

    private String loadRecipeDescription(int recipeId) throws Exception {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT Descrizione FROM Recipes WHERE Id=?")) {

            ps.setInt(1, recipeId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    try {
                        return Optional.ofNullable(rs.getString("Descrizione")).orElse("");
                    } catch (SQLException ex) {
                        return "";
                    }
                }
            }
        }

        return "";
    }

    // =========================================================
    // OPC UA
    // =========================================================
    private StatusCode writeUaRegisterValue(NodeId nodeId, int value) throws Exception {
        Object[] candidates = new Object[]{
                (short) value,
                value,
                (long) value,
                (float) value,
                (double) value,
                ushort(value)
        };

        Exception lastException = null;
        StatusCode lastStatus = null;

        for (Object candidate : candidates) {
            try {
                DataValue dv = DataValue.valueOnly(new Variant(candidate));

                StatusCode status = OpcManager.writeValue(
                        APP_NAME,
                        APP_URI,
                        nodeId,
                        dv
                );

                System.out.println("TEST TYPE -> "
                        + nodeId.toParseableString()
                        + " type=" + candidate.getClass().getSimpleName()
                        + " value=" + candidate
                        + " status=" + status);

                if (status != null && status.isGood()) {
                    System.out.println("TEST TYPE OK -> "
                            + nodeId.toParseableString()
                            + " type=" + candidate.getClass().getSimpleName());
                    return status;
                }

                lastStatus = status;

            } catch (Exception ex) {
                lastException = ex;
                System.out.println("TEST TYPE FAIL -> "
                        + nodeId.toParseableString()
                        + " type=" + candidate.getClass().getSimpleName()
                        + " errore=" + ex.getMessage());
            }
        }

        if (lastException != null) {
            throw new Exception("Nessun tipo OPC accettato da "
                    + nodeId.toParseableString(), lastException);
        }

        throw new Exception("Nessun tipo OPC accettato da "
                + nodeId.toParseableString()
                + ". Ultimo status: " + lastStatus);
    }

    private int readUaRegisterValue(NodeId nodeId) throws Exception {
        /*
         * Uso reflection per non legare questa classe a una firma specifica di OpcManager:
         * se OpcManager espone readValue(String appName, String appUri, NodeId nodeId),
         * la verifica legge il valore realmente presente nel PLC dopo la scrittura.
         */
        try {
            java.lang.reflect.Method readMethod = OpcManager.class.getMethod(
                    "readValue", String.class, String.class, NodeId.class
            );

            Object result = readMethod.invoke(null, APP_NAME, APP_URI, nodeId);
            if (!(result instanceof DataValue)) {
                throw new IllegalStateException("OpcManager.readValue non ha restituito DataValue ma "
                        + (result == null ? "null" : result.getClass().getName()));
            }

            DataValue dv = (DataValue) result;
            if (dv.getStatusCode() != null && !dv.getStatusCode().isGood()) {
                throw new IllegalStateException("Lettura OPC UA rifiutata su "
                        + nodeId.toParseableString() + ": " + dv.getStatusCode());
            }

            Object raw = dv.getValue() != null ? dv.getValue().getValue() : null;
            if (raw instanceof UShort) return ((UShort) raw).intValue();
            if (raw instanceof Number) return ((Number) raw).intValue();

            throw new IllegalStateException("Tipo letto non numerico su "
                    + nodeId.toParseableString() + ": "
                    + (raw == null ? "null" : raw.getClass().getName()));
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(
                    "Verifica read-back non possibile: OpcManager non contiene il metodo "
                            + "readValue(String, String, NodeId). Aggiungere questo metodo oppure "
                            + "impostare VERIFY_WRITE_AFTER_DOWNLOAD=false.",
                    ex
            );
        } catch (java.lang.reflect.InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new RuntimeException(cause);
        }
    }

    private void writeAndVerifyUaRegisterValue(NodeId nodeId, int value, String label) throws Exception {
        StatusCode status = writeUaRegisterValue(nodeId, value);
        if (status == null || !status.isGood()) {
            throw new Exception("Écriture OPC UA refusée sur "
                    + nodeId.toParseableString() + ": " + status);
        }

        if (!VERIFY_WRITE_AFTER_DOWNLOAD) {
            System.out.println("WRITE UA -> " + nodeId.toParseableString()
                    + " = " + value + " [" + label + "]");
            return;
        }

        if (VERIFY_WRITE_DELAY_MS > 0) {
            Thread.sleep(VERIFY_WRITE_DELAY_MS);
        }

        int readBack = readUaRegisterValue(nodeId);
        System.out.println("WRITE+VERIFY UA -> " + nodeId.toParseableString()
                + " scritto=" + value
                + " letto=" + readBack
                + " [" + label + "]");

        if (readBack != value) {
            throw new IllegalStateException("Vérification PLC échouée sur "
                    + nodeId.toParseableString()
                    + " [" + label + "] scritto=" + value + " letto=" + readBack);
        }
    }

    private NodeId buildBatchTableNodeId(int registerNumber) {
        return new NodeId(UA_NAMESPACE_INDEX, PLC_R_NODE_PREFIX + registerNumber);
    }

    // =========================================================
    // OPEN RECIPE
    // =========================================================
    private void recipeOpen(int recipeId) throws Exception {
        List<RecipeStep> steps = loadRecipeSteps(recipeId);
        recipeCache.put(recipeId, steps);

        String desc = loadRecipeDescription(recipeId);
        lblRecipeId.setText(String.valueOf(recipeId));
        txtDescription.setText(desc);

        buildStepTabs(steps);

        setInfoStatus("Recette " + recipeId + " ouverte. Steps chargés: " + steps.size());
        saveUiState();
    }

    private void buildStepTabs(List<RecipeStep> steps) {
        tabSteps.removeAll();

        for (RecipeStep step : steps) {
            String[] cols = {"Parametro", "Valore", "Tag OPC", "OriginalName"};
            DefaultTableModel model = new DefaultTableModel(cols, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return column == 1;
                }

                @Override
                public void setValueAt(Object aValue, int row, int column) {
                    if (column != 1) {
                        super.setValueAt(aValue, row, column);
                        return;
                    }

                    String displayName = String.valueOf(getValueAt(row, 0));
                    String originalName = String.valueOf(getValueAt(row, 3));
                    boolean consigneLiquide = isConsigneLiquideField(originalName);

                    Object oldValue = getValueAt(row, column);
                    int oldNumericValue = toIntValue(oldValue);
                    int newNumericValue;

                    if (consigneLiquide) {
                        double factor = getLiquidConversionFactor(getCodiceLiquidoFromStep(step));
                        if (factor <= 0.0) {
                            setErrorStatus("Produit Liquide non défini pour le STEP " + step.passo
                                    + ": conversion Kg -> L impossible.");
                            return;
                        }
                        // Valore inserito in 0.1 Kg -> litri (0.1 L) per DB e PLC,
                        // arrotondati per eccesso al decimo di litro
                        newNumericValue = (int) Math.ceil(toIntValue(aValue) / factor);
                    } else {
                        newNumericValue = toIntValue(aValue);
                    }

                    if (oldNumericValue == newNumericValue) {
                        super.setValueAt(newNumericValue, row, column);
                        return;
                    }

                    String oldDisplayValue = consigneLiquide
                            ? formatConsigneLiquideValue(oldNumericValue, step)
                            : String.valueOf(oldNumericValue);
                    String newDisplayValue = consigneLiquide
                            ? formatConsigneLiquideValue(newNumericValue, step)
                            : String.valueOf(newNumericValue);

                    ActiveDirectorySetup.AuthenticatedUser user = getAuthenticatedUserForEditMode();

                    if (user == null) {
                        setInfoStatus("Modification annulée: authentification Active Directory non confirmée.");
                        return;
                    }

                    if (!confirmRecipeParameterModification(
                            step.id,
                            txtDescription.getText(),
                            step.passo,
                            displayName,
                            oldDisplayValue,
                            newDisplayValue)) {
                        setInfoStatus("Modification annulée par l'opérateur: " + displayName
                                + " reste à " + oldDisplayValue + ".");
                        return;
                    }

                    try {
                        updateRecipeStepParameterInDb(step.id, step.passo, originalName, newNumericValue);

                        logRecipeStepModification(
                                step.id,
                                step.passo,
                                originalName,
                                displayName,
                                oldNumericValue,
                                newNumericValue,
                                user
                        );

                        step.values.put(originalName, newNumericValue);
                        super.setValueAt(newNumericValue, row, column);
                        // Refresh anche della riga Consigne Liquide quando cambia Produit Liquide
                        fireTableRowsUpdated(0, getRowCount() - 1);
                        setOkStatus("MODE MODIFICATION ACTIF - PARAMÈTRE ENREGISTRÉ SUR SRV-BAMBURY: RECETTE " + step.id
                                + ", STEP " + step.passo
                                + ", " + displayName
                                + " DE " + oldDisplayValue
                                + " À " + newDisplayValue
                                + " - UTILISATEUR " + user.getUsername());
                    } catch (Exception ex) {
                        showError("Erreur sauvegarde modification sur SRV-BAMBURY", ex);
                    }
                }
            };

            List<Map.Entry<String, Integer>> orderedEntries = getOrderedEntries(step);

            for (Map.Entry<String, Integer> entry : orderedEntries) {
                String originalName = entry.getKey();
                String displayName = getDisplayName(originalName);

                Var v = findVar("Step" + step.passo, originalName);
                String generatedPath = buildLiveTagPath(step.passo, originalName);

                String path = !generatedPath.isBlank() ? generatedPath : (v != null ? v.path : "");

                model.addRow(new Object[]{displayName, entry.getValue(), path, originalName});
            }

            JTable table = new JTable(model) {
                @Override
                public TableCellRenderer getCellRenderer(int row, int column) {
                    if (column == 1) {
                        String originalName = String.valueOf(getModel().getValueAt(row, 3));
                        if (isConsigneLiquideField(originalName)) {
                            return new ConsigneLiquideRenderer(step);
                        }
                        Object param = getModel().getValueAt(row, 0);
                        ComboOption[] options = getOptionsForParameter(String.valueOf(param));
                        if (options != null) return new ComboLabelRenderer();
                    }
                    return super.getCellRenderer(row, column);
                }

                @Override
                public TableCellEditor getCellEditor(int row, int column) {
                    if (column == 1) {
                        String originalName = String.valueOf(getModel().getValueAt(row, 3));
                        if (isConsigneLiquideField(originalName)) {
                            return new ConsigneLiquideCellEditor(step);
                        }
                        Object param = getModel().getValueAt(row, 0);
                        ComboOption[] options = getOptionsForParameter(String.valueOf(param));
                        if (options != null) return new ComboBoxCellEditor(options);
                    }
                    return super.getCellEditor(row, column);
                }
            };
            table.setRowHeight(32);
            table.setFont(new Font("SansSerif", Font.PLAIN, 18));
            table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 18));
            table.setGridColor(new Color(120, 120, 120));
            table.setShowGrid(true);
            table.setIntercellSpacing(new Dimension(1, 1));
            hideTechnicalColumn(table, 3);

            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(Color.WHITE);
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(90, 90, 90), 3),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));

            JScrollPane scrollPane = new JScrollPane(table);
            scrollPane.setBorder(BorderFactory.createLineBorder(new Color(120, 120, 120), 2));
            panel.add(scrollPane, BorderLayout.CENTER);

            tabSteps.addTab("STEP " + step.passo, panel);
        }

        tabSteps.revalidate();
        tabSteps.repaint();
    }


    private int toIntValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private void hideTechnicalColumn(JTable table, int modelColumnIndex) {
        try {
            table.getColumnModel().removeColumn(table.getColumnModel().getColumn(modelColumnIndex));
        } catch (Exception ignored) {
        }
    }

    // AUTHENTIFICATION ACTIVE DIRECTORY CENTRALISEE
    // Utilise obligatoirement la classe ActiveDirectorySetup pour valider l'utilisateur.
    private ActiveDirectorySetup.AuthenticatedUser getAuthenticatedUserForEditMode() {
        if (isEditModeActive() && editModeUser != null) {
            return editModeUser;
        }

        // Precompila l'utente AD memorizzato (mai la password) nella finestra di login
        final java.awt.event.AWTEventListener prefill = ev -> {
            if (ev.getID() == java.awt.event.WindowEvent.WINDOW_OPENED
                    && ev.getSource() instanceof JDialog
                    && lastAdUsername != null && !lastAdUsername.isEmpty()) {
                JDialog dlg = (JDialog) ev.getSource();
                List<JTextField> fields = new ArrayList<>();
                collectTextFields(dlg.getContentPane(), fields);
                if (!fields.isEmpty()) {
                    fields.get(0).setText(lastAdUsername);
                    if (fields.size() > 1) {
                        fields.get(1).requestFocusInWindow();
                    }
                }
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(prefill, java.awt.AWTEvent.WINDOW_EVENT_MASK);

        ActiveDirectorySetup.AuthenticatedUser user;
        try {
            user = ActiveDirectorySetup.requestAuthentication(MondoLuxRecipesSetup.this);
        } finally {
            Toolkit.getDefaultToolkit().removeAWTEventListener(prefill);
        }

        if (user != null) {
            lastAdUsername = user.getUsername() == null ? "" : user.getUsername();
            saveUiState();
            activateEditMode(user, EDIT_MODE_DURATION_SECONDS);
        }

        return user;
    }

    private void collectTextFields(Container c, List<JTextField> out) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JTextField) {
                out.add((JTextField) comp);
            } else if (comp instanceof Container) {
                collectTextFields((Container) comp, out);
            }
        }
    }

    private boolean isEditModeActive() {
        return editModeUser != null && System.currentTimeMillis() < editModeExpiresAtMillis;
    }

    private boolean confirmRecipeParameterModification(int recipeId,
                                                       String recipeDescription,
                                                       int passo,
                                                       String parameterName,
                                                       String oldValue,
                                                       String newValue) {
        String safeParameterName = parameterName == null || parameterName.trim().isEmpty()
                ? "-"
                : parameterName.trim();

        String safeRecipeDescription = recipeDescription == null ? "" : recipeDescription.trim();
        String recipeDisplay = String.valueOf(recipeId);
        if (!safeRecipeDescription.isEmpty()) {
            recipeDisplay += " - " + safeRecipeDescription;
        }

        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel title = new JLabel("CONFIRMATION DE MODIFICATION", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(new Color(120, 0, 0));

        JPanel details = new JPanel(new GridLayout(5, 2, 10, 10));
        details.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(140, 140, 140), 2),
                new EmptyBorder(14, 14, 14, 14)
        ));

        Font labelFont = new Font("SansSerif", Font.BOLD, 17);
        Font valueFont = new Font("SansSerif", Font.PLAIN, 17);

        addConfirmRow(details, "Recette:", recipeDisplay, labelFont, valueFont);
        addConfirmRow(details, "STEP:", String.valueOf(passo), labelFont, valueFont);
        addConfirmRow(details, "Paramètre:", safeParameterName, labelFont, valueFont);
        addConfirmRow(details, "Ancienne valeur:", oldValue, labelFont, valueFont);
        addConfirmRow(details, "Nouvelle valeur:", newValue, labelFont, valueFont);

        JLabel question = new JLabel("Voulez-vous confirmer cette modification ?", SwingConstants.CENTER);
        question.setFont(new Font("SansSerif", Font.BOLD, 18));

        JPanel center = new JPanel(new BorderLayout(10, 10));
        center.add(details, BorderLayout.CENTER);
        center.add(question, BorderLayout.SOUTH);

        panel.add(title, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);

        Object[] options = {"CONFIRMER", "ANNULER"};
        int result = JOptionPane.showOptionDialog(
                this,
                panel,
                "CONFIRMATION DE MODIFICATION",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[1]
        );

        return result == 0;
    }

    private void addConfirmRow(JPanel panel,
                               String label,
                               String value,
                               Font labelFont,
                               Font valueFont) {
        JLabel lbl = new JLabel(label);
        lbl.setFont(labelFont);

        JLabel val = new JLabel(value == null ? "" : value);
        val.setFont(valueFont);

        panel.add(lbl);
        panel.add(val);
    }

    private void activateEditMode(ActiveDirectorySetup.AuthenticatedUser user, int seconds) {
        editModeUser = user;
        editModeExpiresAtMillis = System.currentTimeMillis() + (seconds * 1000L);
        editModeWarningShown = false;

        if (rootPanel != null) {
            rootPanel.setEditModeActive(true);
            rootPanel.repaint();
        }

        setOkStatus("MODE MODIFICATION ACTIF - AUTHENTIFICATION ACTIVE DIRECTORY VALIDÉE - VOUS POUVEZ MODIFIER LA RECETTE SANS RÉAUTHENTIFICATION PENDANT 15 MINUTES");
        startEditModeTimer();
    }

    private void startEditModeTimer() {
        if (editModeTimer != null) {
            editModeTimer.stop();
        }

        editModeTimer = new javax.swing.Timer(250, e -> updateEditModeTimer());
        editModeTimer.start();
    }

    private void updateEditModeTimer() {
        long remainingMillis = editModeExpiresAtMillis - System.currentTimeMillis();
        long remainingSeconds = Math.max(0L, (remainingMillis + 999L) / 1000L);

        if (remainingSeconds <= 0L) {
            deactivateEditMode();
            return;
        }

        if (remainingSeconds <= EDIT_MODE_WARNING_SECONDS && !editModeWarningShown) {
            editModeWarningShown = true;
            showEditModeWarningDialog();
        }

        if (editModeWarningDialog != null && editModeWarningDialog.isDisplayable() && editModeWarningLabel != null) {
            editModeWarningLabel.setText("<html><div style='text-align:center;'>LE MODE MODIFICATION SE TERMINE DANS<br><b>"
                    + remainingSeconds
                    + " SECONDES</b></div></html>");
        }
    }

    private void showEditModeWarningDialog() {
        if (editModeWarningDialog != null && editModeWarningDialog.isDisplayable()) {
            return;
        }

        editModeWarningDialog = new JDialog(this, "Fin du mode modification", false);
        editModeWarningDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setBorder(new EmptyBorder(22, 26, 22, 26));

        editModeWarningLabel = new JLabel("", SwingConstants.CENTER);
        editModeWarningLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        editModeWarningLabel.setForeground(Color.RED);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        JButton okButton = new JButton("OK");
        JButton postponeButton = new JButton("PROLONGER DE 5 MINUTES");

        okButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        postponeButton.setFont(new Font("SansSerif", Font.BOLD, 16));

        okButton.addActionListener(e -> editModeWarningDialog.dispose());
        postponeButton.addActionListener(e -> {
            editModeExpiresAtMillis += EDIT_MODE_POSTPONE_SECONDS * 1000L;
            editModeWarningShown = false;
            setOkStatus("MODE MODIFICATION ACTIF - PROLONGÉ DE 5 MINUTES");
            editModeWarningDialog.dispose();
        });

        buttons.add(okButton);
        buttons.add(postponeButton);

        panel.add(editModeWarningLabel, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);

        editModeWarningDialog.setContentPane(panel);
        editModeWarningDialog.setSize(520, 230);
        editModeWarningDialog.setLocationRelativeTo(this);
        updateEditModeTimer();
        editModeWarningDialog.setVisible(true);
    }

    private void deactivateEditMode() {
        if (editModeTimer != null) {
            editModeTimer.stop();
            editModeTimer = null;
        }

        if (editModeWarningDialog != null && editModeWarningDialog.isDisplayable()) {
            editModeWarningDialog.dispose();
        }

        editModeUser = null;
        editModeExpiresAtMillis = 0L;
        editModeWarningShown = false;

        if (rootPanel != null) {
            rootPanel.setEditModeActive(false);
            rootPanel.repaint();
        }

        setInfoStatus("Mode modification terminé.");
    }

    private void updateRecipeStepParameterInDb(int recipeId, int passo, String originalName, int newValue) throws Exception {
        DBBamburyReader.updateRecipeStepParameter(recipeId, passo, originalName, newValue);
    }

    private void logRecipeStepModification(int recipeId,
                                           int passo,
                                           String originalName,
                                           String displayName,
                                           int oldValue,
                                           int newValue,
                                           ActiveDirectorySetup.AuthenticatedUser user) throws Exception {
        File logFile = new File(RECIPE_CHANGE_LOG_FILE);
        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {
            pw.println(LOG_DATE_FORMAT.format(LocalDateTime.now())
                    + " | RecipeId=" + recipeId
                    + " | Step=" + passo
                    + " | User=" + user.getUsername()
                    + " | Principal=" + user.getPrincipal()
                    + " | Campo=" + originalName
                    + " | Display=" + displayName
                    + " | OldValue=" + oldValue
                    + " | NewValue=" + newValue);
        }
    }

    private void logRecipeSetupModification(String action,
                                            Integer oldId,
                                            String oldDescription,
                                            Integer newId,
                                            String newDescription,
                                            ActiveDirectorySetup.AuthenticatedUser user) throws Exception {
        File logFile = new File(RECIPE_SETUP_LOG_FILE);
        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {
            pw.println(LOG_DATE_FORMAT.format(LocalDateTime.now())
                    + " | Action=" + safeLogValue(action)
                    + " | User=" + (user == null ? "-" : safeLogValue(user.getUsername()))
                    + " | Principal=" + (user == null ? "-" : safeLogValue(user.getPrincipal()))
                    + " | OldId=" + (oldId == null ? "-" : oldId)
                    + " | OldDescription=" + safeLogValue(oldDescription)
                    + " | NewId=" + (newId == null ? "-" : newId)
                    + " | NewDescription=" + safeLogValue(newDescription));
        }
    }

    private String safeLogValue(String value) {
        if (value == null) {
            return "-";
        }
        return value.replace("\r", " ").replace("\n", " ").replace("|", "/").trim();
    }

    private int getDisplayOrder(String originalName) {
        if (originalName == null) return 9999;
        String key = normalizeFieldName(originalName);

        if (key.equals("FASE")) return 1;
        if (key.equals("TEMPERATURA")) return 2;
        if (key.equals("TEMPO")) return 3;
        if (key.equals("STATOPORTELLA")) return 4;
        if (key.equals("PRESSORE")) return 5;
        if (key.equals("CODICESOLIDO")) return 6;
        if (key.equals("VELMIXER")) return 7;
        if (key.equals("QTALIQUIDO")) return 8;
        if (key.equals("CODICELIQUIDO")) return 9;
        if (key.equals("RITARDOLIQUIDI")) return 10;

        return 1000;
    }

    private String getDisplayName(String originalName) {
        if (originalName == null) return "";

        String key = normalizeFieldName(originalName);

        if (key.equals("FASE")) return "Operation";
        if (key.equals("TEMPERATURA")) return "Temperature Matiere";
        if (key.equals("TEMPO")) return "Tempo d'attente";
        if (key.equals("STATOPORTELLA")) return "Porte Chargement";
        if (key.equals("PRESSORE")) return "Piston";
        if (key.equals("CODICESOLIDO")) return "Produit Solid";
        if (key.equals("VELMIXER")) return "Vitesse";
        if (key.equals("QTALIQUIDO")) return "Consigne Liquide (0.1 Kg.)";
        if (key.equals("CODICELIQUIDO")) return "Produit Liquide";
        if (key.equals("RITARDOLIQUIDI")) return "Retard Liquide";

        return originalName;
    }

    private String normalizeFieldName(String s) {
        return s == null ? "" : s.replace(" ", "").replace("_", "").replace("'", "").trim().toUpperCase();
    }

    // =========================================================
    // CONSIGNE LIQUIDE: conversione Kg <-> L
    // =========================================================
    private static double getLiquidConversionFactor(int codiceLiquido) {
        switch (codiceLiquido) {
            case 1: return LIQUID_FACTOR_DOA;
            case 2: return LIQUID_FACTOR_SILANO;
            case 3: return LIQUID_FACTOR_HUILE_PASTIFIANT;
            default: return 0.0;
        }
    }

    private boolean isConsigneLiquideField(String originalName) {
        return normalizeFieldName(originalName).equals("QTALIQUIDO");
    }

    private int getCodiceLiquidoFromStep(RecipeStep step) {
        for (Map.Entry<String, Integer> entry : step.values.entrySet()) {
            if (normalizeFieldName(entry.getKey()).equals("CODICELIQUIDO")) {
                return entry.getValue() == null ? 0 : entry.getValue();
            }
        }
        return 0;
    }

    // Il valore in tabella/DB/PLC resta in 0.1 L: la visualizzazione mostra Kg e L.
    private String formatConsigneLiquideValue(int litersTenths, RecipeStep step) {
        double liters = litersTenths / 10.0;
        double factor = getLiquidConversionFactor(getCodiceLiquidoFromStep(step));
        if (factor <= 0.0) {
            return String.format(java.util.Locale.US, "%.1f L", liters);
        }
        double kg = liters * factor;
        return String.format(java.util.Locale.US, "%.1f Kg (%.1f L)", kg, liters);
    }

    private Integer getLiveParameterIndex(String originalName) {
        String key = normalizeFieldName(originalName);

        if (key.equals("FASE")) return 0;
        if (key.equals("TEMPERATURA")) return 1;
        if (key.equals("TEMPO")) return 2;
        if (key.equals("STATOPORTELLA")) return 3;
        if (key.equals("PRESSORE")) return 4;
        if (key.equals("CODICESOLIDO")) return 5;
        if (key.equals("VELMIXER")) return 6;
        if (key.equals("QTALIQUIDO")) return 7;
        if (key.equals("CODICELIQUIDO")) return 8;
        if (key.equals("RITARDOLIQUIDI")) return 9;

        return null;
    }

    private int getLiveRegisterNumber(int passo, String originalName) {
        Integer paramIndex = getLiveParameterIndex(originalName);
        if (paramIndex == null || passo < 1) return -1;

        return LIVE_TABLE_START_R
                + ((passo - 1) * LIVE_STEP_REGISTER_STRIDE)
                + (paramIndex * LIVE_PARAM_REGISTER_STRIDE);
    }

    private String buildLiveTagPath(int passo, String originalName) {
        int reg = getLiveRegisterNumber(passo, originalName);
        if (reg < 0) return "";
        return LIVE_R_NODE_PREFIX + reg;
    }

    private RecipeStep findStepByPasso(List<RecipeStep> steps, int passo) {
        for (RecipeStep step : steps) {
            if (step.passo == passo) return step;
        }
        return null;
    }

    private List<Map.Entry<String, Integer>> getOrderedEntries(RecipeStep step) {
        List<Map.Entry<String, Integer>> orderedEntries = new ArrayList<>(step.values.entrySet());
        orderedEntries.sort((e1, e2) -> {
            int o1 = getDisplayOrder(e1.getKey());
            int o2 = getDisplayOrder(e2.getKey());
            if (o1 != o2) return Integer.compare(o1, o2);
            return e1.getKey().compareToIgnoreCase(e2.getKey());
        });
        return orderedEntries;
    }


    // =========================================================
    // CONTROLLO ANOMALIE DOWNLOAD
    // =========================================================
    private void updateDownloadAnomalyControl(final Integer recipeId, final List<String> anomalies, final boolean cleared) {
        SwingUtilities.invokeLater(() -> {
            lastDownloadAnomalies.clear();

            if (anomalies != null) {
                lastDownloadAnomalies.addAll(anomalies);
            }

            lastDownloadAnomalyRecipeId = recipeId;

            if (lastDownloadAnomalies.isEmpty()) {
                return;
            }

            if (cleared) {
                setOkStatus("Anomalies download éliminées après match 100% - recette " + recipeId);
            } else {
                setErrorStatus(lastDownloadAnomalies.size() + " anomalie(s) download - recette " + recipeId);
            }
        });
    }

    private void showAndOptionallyClearLastDownloadAnomalies() {
        List<String> copy = new ArrayList<>(lastDownloadAnomalies);

        if (copy.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Aucune anomalie download enregistrée.",
                    "Contrôle anomalies",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        StringBuilder body = new StringBuilder();
        body.append("Recette: ").append(lastDownloadAnomalyRecipeId == null ? "-" : lastDownloadAnomalyRecipeId).append("\n\n");
        body.append("Anomalies enregistrées:\n");

        int max = Math.min(copy.size(), 30);
        for (int i = 0; i < max; i++) {
            body.append("- ").append(copy.get(i)).append("\n");
        }

        if (copy.size() > max) {
            body.append("\n... ").append(copy.size() - max).append(" autre(s) anomalie(s).");
        }

        body.append("\n\nVoulez-vous éliminer ces anomalies de l'écran?");

        int choice = JOptionPane.showConfirmDialog(
                this,
                body.toString(),
                "Contrôle anomalies download",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (choice == JOptionPane.YES_OPTION) {
            lastDownloadAnomalies.clear();
            setOkStatus("Anomalies download éliminées de l'écran.");
            saveUiState();
        }
    }

    private boolean confirmClearDownloadAnomaliesAfterMatch100(
            int recipeId,
            List<String> anomalies,
            RecipePLCMatchInfo matchInfo) throws Exception {

        final boolean[] result = new boolean[]{false};
        final Exception[] dialogException = new Exception[]{null};

        Runnable dialogTask = () -> {
            try {
                StringBuilder body = new StringBuilder();
                body.append("Des anomalies OPC ont été détectées pendant le download.\n\n");

                int max = Math.min(anomalies.size(), 12);
                for (int i = 0; i < max; i++) {
                    body.append("- ").append(anomalies.get(i)).append("\n");
                }

                if (anomalies.size() > max) {
                    body.append("\n... ").append(anomalies.size() - max).append(" autre(s) anomalie(s).\n");
                }

                body.append("\nContrôle RecipePLC:\n");
                body.append(matchInfo == null ? "Non disponible" : matchInfo.toDisplayLine()).append("\n\n");

                body.append("Le match est à 100%. Voulez-vous éliminer ces anomalies et considérer le download comme terminé?");

                int choice = JOptionPane.showConfirmDialog(
                        MondoLuxRecipesSetup.this,
                        body.toString(),
                        "Contrôle anomalies download",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );

                result[0] = choice == JOptionPane.YES_OPTION;
            } catch (Exception ex) {
                dialogException[0] = ex;
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            dialogTask.run();
        } else {
            SwingUtilities.invokeAndWait(dialogTask);
        }

        if (dialogException[0] != null) {
            throw dialogException[0];
        }

        return result[0];
    }

    private RecipePLCMatchInfo readLatestRecipePLCMatchInfo(int recipeId) {
        try {
            DBBamburyReader.RecipePLCInfo dbInfo = DBBamburyReader.readLatestRecipePLC(recipeId);
            if (dbInfo == null) {
                return null;
            }

            RecipePLCMatchInfo info = new RecipePLCMatchInfo();
            info.recipeId = dbInfo.ricetta;
            info.descrizione = dbInfo.descrizione;
            info.matchEsatti = dbInfo.matchEsatti;
            info.differenzaTotale = dbInfo.differenzaTotale;
            return info;
        } catch (Exception ex) {
            System.out.println("Erreur lecture RecipePLC pour contrôle anomalies: " + ex.getMessage());
            return null;
        }
    }

    private RecipePLCMatchInfo waitForRecipePLCMatchInfo(int recipeId) {
        RecipePLCMatchInfo lastInfo = null;

        for (int i = 0; i < DOWNLOAD_MATCH_CHECK_RETRY_COUNT; i++) {
            lastInfo = readLatestRecipePLCMatchInfo(recipeId);

            if (lastInfo != null && lastInfo.isMatch100()) {
                return lastInfo;
            }

            try {
                Thread.sleep(DOWNLOAD_MATCH_CHECK_DELAY_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return lastInfo;
            }
        }

        return lastInfo;
    }

    // =========================================================
    // DOWNLOAD RICETTA
    // =========================================================
    public void recipeDownload(int recipeId) throws Exception {

        // Registra subito l'ID scelto dall'operatore. Il monitor usera questo ID
        // per distinguere ricette con parametri perfettamente identici.
        RecipePLCMonitorService.registerRequestedRecipe(recipeId);

    	final String REPORT_VERSION = "PLC3_DOWNLOAD_REPORT_V005_ANOMALY_CONTROL";
    	final String REPORT_FILE = "C:\\_Tools\\PLC3_Download_Report.csv";

    	File reportFile = new File(REPORT_FILE);
    	reportFile.getParentFile().mkdirs();

    	System.out.println("REPORT FILE = " + reportFile.getAbsolutePath());

        // Non fidarsi mai della cache per il DOWNLOAD fisico: se la ricetta e' stata
        // modificata nel DB dopo l'ultima apertura in questa sessione, la cache
        // conterrebbe valori obsoleti e verrebbero scritti sul PLC dati sbagliati.
        List<RecipeStep> steps = loadRecipeSteps(recipeId);
        recipeCache.put(recipeId, steps);

        if (steps.size() != EXPECTED_STEP_COUNT) {
            throw new IllegalStateException(
                    "La ricetta non ha " + EXPECTED_STEP_COUNT + " step ma " + steps.size()
            );
        }

        int writtenOk = 0;
        int writtenKo = 0;

        List<String> failedWrites = new ArrayList<>();

        System.out.println("========================================");
        System.out.println("VERSIONE: " + REPORT_VERSION);
        System.out.println("TÉLÉCHARGEMENT RECETTE OPC UA SUR TABLE PLC3");
        System.out.println("Recipe ID: " + recipeId);
        System.out.println("Prefisso OPC download: " + PLC_R_NODE_PREFIX);
        System.out.println("Vérification read-back: " + (VERIFY_WRITE_AFTER_DOWNLOAD ? "ACTIVE" : "DISACTIVE"));
        System.out.println("========================================");

        for (int passo = 1; passo <= EXPECTED_STEP_COUNT; passo++) {

            RecipeStep step = findStepByPasso(steps, passo);
            if (step == null) {
                failedWrites.add("STEP " + passo + ";STEP_MISSING;-;-;Step introuvable");
                writtenKo++;
                continue;
            }

            List<Map.Entry<String, Integer>> orderedEntries = getOrderedEntries(step);

            if (orderedEntries.size() < PARAMS_PER_STEP) {
                failedWrites.add("STEP " + passo + ";PARAMS_MISSING;-;-;Paramètres présents="
                        + orderedEntries.size());
                writtenKo++;
                continue;
            }

            int stepBaseRegister = BATCH_TABLE_START_R + ((passo - 1) * STEP_REGISTER_STRIDE);

            for (int i = 0; i < PARAMS_PER_STEP; i++) {

                Map.Entry<String, Integer> entry = orderedEntries.get(i);
                int registerNumber = stepBaseRegister + (i * PARAM_REGISTER_STRIDE);
                NodeId nodeId = buildBatchTableNodeId(registerNumber);
                int value = entry.getValue();

                if (!OPC_UA_ENABLED) {
                    System.out.println("SIM WRITE UA -> "
                            + nodeId.toParseableString()
                            + " = "
                            + value
                            + " ["
                            + entry.getKey()
                            + "]");
                    writtenOk++;
                    continue;
                }

                try {
                    writeAndVerifyUaRegisterValue(nodeId, value, entry.getKey());
                    writtenOk++;
                } catch (Exception ex) {
                    writtenKo++;

                    String errorLine =
                            "STEP " + passo
                                    + ";R" + registerNumber
                                    + ";" + entry.getKey()
                                    + ";" + value
                                    + ";" + ex.getMessage();

                    failedWrites.add(errorLine);

                    System.out.println("DOWNLOAD WARNING -> " + errorLine);
                }
            }
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(reportFile, true))) {
            pw.println("DATA;VERSIONE;RICETTA;STEP;REGISTRO;PARAMETRO;VALORE;ERRORE");
            String now = LocalDateTime.now().toString();

            if (failedWrites.isEmpty()) {
                pw.println(now + ";" + REPORT_VERSION + ";" + recipeId
                        + ";OK;OK;OK;OK;DOWNLOAD_COMPLETO_SENZA_ERRORI");
            } else {
                for (String line : failedWrites) {
                    pw.println(now + ";" + REPORT_VERSION + ";" + recipeId + ";" + line);
                }
            }
        } catch (Exception ex) {
            System.out.println("ERRORE SCRITTURA REPORT CSV: " + ex.getMessage());
        }

        StringBuilder msg = new StringBuilder();
        msg.append("Versione: ").append(REPORT_VERSION).append("\n");
        msg.append("Recette: ").append(recipeId).append("\n\n");
        msg.append("Écritures OK: ").append(writtenOk).append("\n");
        msg.append("Écritures KO: ").append(writtenKo).append("\n\n");

        if (failedWrites.isEmpty()) {
            msg.append("TÉLÉCHARGEMENT TERMINÉ SANS ANOMALIE.");
            setOkStatus("Téléchargement OK - " + REPORT_VERSION + " - scritture OK: " + writtenOk);
        } else {
            msg.append("TÉLÉCHARGEMENT TERMINÉ AVEC ANOMALIES:\n\n");

            int maxRowsToShow = Math.min(failedWrites.size(), 20);

            for (int i = 0; i < maxRowsToShow; i++) {
                msg.append(failedWrites.get(i)).append("\n");
            }

            if (failedWrites.size() > maxRowsToShow) {
                msg.append("\n... altre ")
                   .append(failedWrites.size() - maxRowsToShow)
                   .append(" anomalies enregistrées dans le CSV.");
            }

            msg.append("\nRapport enregistré dans: ").append(REPORT_FILE);

            setErrorStatus("Téléchargement terminé avec anomalies - "
                    + REPORT_VERSION
                    + " - OK="
                    + writtenOk
                    + " KO="
                    + writtenKo);
        }

        System.out.println(msg.toString());

        if (!failedWrites.isEmpty()) {
            updateDownloadAnomalyControl(recipeId, failedWrites, false);

            RecipePLCMatchInfo matchInfo = waitForRecipePLCMatchInfo(recipeId);
            boolean match100 = matchInfo != null && matchInfo.isMatch100();

            System.out.println("CONTROLLO ANOMALIE DOWNLOAD -> "
                    + (matchInfo == null ? "RecipePLC non disponibile" : matchInfo.toDisplayLine()));

            if (ALLOW_CLEAR_DOWNLOAD_ANOMALIES_IF_RECIPE_MATCH_100 && match100) {
                boolean clearAnomalies = confirmClearDownloadAnomaliesAfterMatch100(recipeId, failedWrites, matchInfo);

                if (clearAnomalies) {
                    updateDownloadAnomalyControl(recipeId, failedWrites, true);
                    setOkStatus("Download terminé: anomalies OPC éliminées car RecipePLC confirme match 100% - recette " + recipeId);
                } else {
                    throw new IllegalStateException("Téléchargement terminé avec anomalies non éliminées. "
                            + "RecipePLC confirme match 100%, mais l'opérateur n'a pas éliminé les anomalies. "
                            + "Écritures KO=" + writtenKo
                            + ". Première anomalie: " + failedWrites.get(0)
                            + ". Rapport enregistré dans: " + REPORT_FILE);
                }
            } else {
                throw new IllegalStateException("Téléchargement non terminé correctement. "
                        + "Écritures KO=" + writtenKo
                        + ". Première anomalie: " + failedWrites.get(0)
                        + ". Contrôle RecipePLC: "
                        + (matchInfo == null ? "non disponible" : matchInfo.toDisplayLine())
                        + ". Rapport enregistré dans: " + REPORT_FILE);
            }
        } else {
            updateDownloadAnomalyControl(recipeId, new ArrayList<>(), false);
        }

        lastDownloadedRecipeId = recipeId;
        SwingUtilities.invokeLater(() -> {
            lblLastDownloaded.setText(String.valueOf(recipeId));
            saveUiState();
        });
        logDownloadedRecipeId(recipeId);

        System.out.println("WRITE OK: " + writtenOk);
        System.out.println("WRITE KO: " + writtenKo);
        System.out.println("REPORT: " + REPORT_FILE);
    }

    private void logDownloadedRecipeId(int recipeId) {
        try (PrintWriter pw = new PrintWriter(new FileWriter("download_recipe_log.txt", true))) {
            pw.println(LocalDateTime.now() + " - Recipe ID envoyé: " + recipeId);
        } catch (Exception ex) {
            ex.printStackTrace();
            setErrorStatus("Impossible d’écrire le log download_recipe_log.txt");
        }
    }

    // =========================================================
    // NAVIGATION
    // =========================================================
    private void openMain0() {
        try {
            deactivateEditMode();
            saveUiState();
            dispose();
            System.exit(0);
        } catch (Exception ex) {
            showError("Erreur sortie application", ex);
        }
    }

    // =========================================================
    // ERROR
    // =========================================================
    private void showError(String title, Exception ex) {
        ex.printStackTrace();
        String msg = buildFriendlyErrorMessage(ex);
        setErrorStatus(msg);
        JOptionPane.showMessageDialog(this, msg, title, JOptionPane.ERROR_MESSAGE);
    }

    private ComboOption[] getOptionsForParameter(String parameterName) {
        String key = normalizeFieldName(parameterName);

        if (key.equals("OPERATION")) {
            return new ComboOption[]{
                    new ComboOption(0, "0 - ----------"),
                    new ComboOption(1, "1 - Charg.Manuel"),
                    new ComboOption(2, "2 - Charg.Silos"),
                    new ComboOption(3, "3 - Charge.Liquide"),
                    new ComboOption(4, "4 - Charg.Manuel+Liquide"),
                    new ComboOption(5, "5 - Charg.Silos+Liquide"),
                    new ComboOption(8, "8 - Nettoyage"),
                    new ComboOption(9, "9 - Malaxage"),
                    new ComboOption(10, "10 - Dechargement")
            };
        }

        if (key.equals("PORTECHARGEMENT")) {
            return new ComboOption[]{
                    new ComboOption(0, "0 - Ferme"),
                    new ComboOption(1, "1 - Ouverte")
            };
        }

        if (key.equals("PISTON")) {
            return new ComboOption[]{
                    new ComboOption(0, "0 - Haut"),
                    new ComboOption(1, "1 - Nettoyage"),
                    new ComboOption(2, "2 - Bas")
            };
        }

        if (key.equals("PRODUITSOLID")) {
            return new ComboOption[]{
                    new ComboOption(0, "0 - ---------"),
                    new ComboOption(1, "1 - Caoutchouc"),
                    new ComboOption(4, "4 - Silice")
            };
        }

        if (key.equals("PRODUITLIQUIDE")) {
            return new ComboOption[]{
                    new ComboOption(0, "0 - ----------"),
                    new ComboOption(1, "1 - DOA"),
                    new ComboOption(2, "2 - SILANO"),
                    new ComboOption(3, "3 - HUILE PASTIFIANT")
            };
        }

        return null;
    }

    private String formatComboValue(String parameterName, Object value) {
        ComboOption[] options = getOptionsForParameter(parameterName);
        if (options == null) return value == null ? "" : String.valueOf(value);

        int numericValue = 0;
        if (value instanceof Number) {
            numericValue = ((Number) value).intValue();
        } else if (value != null) {
            try {
                numericValue = Integer.parseInt(String.valueOf(value).trim());
            } catch (Exception ignored) {
                return String.valueOf(value);
            }
        }

        for (ComboOption option : options) {
            if (option.value == numericValue) return option.label;
        }
        return numericValue + " - ?";
    }

    private static class ComboOption {
        final int value;
        final String label;

        ComboOption(int value, String label) {
            this.value = value;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    // Riga Consigne Liquide: il modello contiene 0.1 L, la cella mostra Kg e L.
    private class ConsigneLiquideRenderer extends DefaultTableCellRenderer {
        private final RecipeStep step;

        ConsigneLiquideRenderer(RecipeStep step) {
            this.step = step;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setText(formatConsigneLiquideValue(toIntValue(value), step));
            setHorizontalAlignment(SwingConstants.LEFT);
            return this;
        }
    }

    // Editor della riga Consigne Liquide: presenta e accetta il valore in 0.1 Kg;
    // la conversione in 0.1 L avviene in setValueAt del modello.
    private class ConsigneLiquideCellEditor extends DefaultCellEditor {
        private final RecipeStep step;

        ConsigneLiquideCellEditor(RecipeStep step) {
            super(new JTextField());
            this.step = step;
            getComponent().setFont(new Font("SansSerif", Font.PLAIN, 18));
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
                                                     int row, int column) {
            Object kgValue = value;
            double factor = getLiquidConversionFactor(getCodiceLiquidoFromStep(step));
            if (factor > 0.0) {
                // Presenta in 0.1 Kg (decimali) partendo dai 0.1 L memorizzati
                kgValue = (int) Math.round(toIntValue(value) * factor);
            }
            return super.getTableCellEditorComponent(table, kgValue, isSelected, row, column);
        }
    }

    private class ComboLabelRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            Object parameterName = table.getModel().getValueAt(row, 0);
            setText(formatComboValue(String.valueOf(parameterName), value));
            setHorizontalAlignment(SwingConstants.LEFT);
            return this;
        }
    }

    private class ComboBoxCellEditor extends AbstractCellEditor implements TableCellEditor {
        private final JComboBox<ComboOption> comboBox;

        ComboBoxCellEditor(ComboOption[] options) {
            this.comboBox = new JComboBox<>(options);
            this.comboBox.setFont(new Font("SansSerif", Font.PLAIN, 18));
            this.comboBox.addActionListener(e -> stopCellEditing());
        }

        @Override
        public Object getCellEditorValue() {
            ComboOption selected = (ComboOption) comboBox.getSelectedItem();
            return selected != null ? selected.value : 0;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
                                                     int row, int column) {
            int numericValue = 0;
            if (value instanceof Number) {
                numericValue = ((Number) value).intValue();
            } else if (value != null) {
                try {
                    numericValue = Integer.parseInt(String.valueOf(value).trim());
                } catch (Exception ignored) {
                    numericValue = 0;
                }
            }

            ComboBoxModel<ComboOption> model = comboBox.getModel();
            for (int i = 0; i < model.getSize(); i++) {
                ComboOption option = model.getElementAt(i);
                if (option.value == numericValue) {
                    comboBox.setSelectedIndex(i);
                    return comboBox;
                }
            }

            if (model.getSize() > 0) comboBox.setSelectedIndex(0);
            return comboBox;
        }
    }

    // =========================================================
    // SUPPORT CLASSES
    // =========================================================
    private static class DotPanel extends JPanel {
        private boolean editModeActive = false;

        void setEditModeActive(boolean editModeActive) {
            this.editModeActive = editModeActive;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(editModeActive ? EDIT_MODE_BACKGROUND : NORMAL_BACKGROUND);
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setColor(new Color(205, 205, 205));
            for (int y = 0; y < getHeight(); y += 10) {
                for (int x = 0; x < getWidth(); x += 10) {
                    g2.fillRect(x, y, 1, 1);
                }
            }
            g2.dispose();
        }
    }

    private static class ThinIconBorder implements Border {
        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(1, 1, 1, 1);
        }

        @Override
        public boolean isBorderOpaque() {
            return true;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(new Color(135, 135, 135));
            g2.drawRoundRect(x, y, w - 1, h - 1, 14, 14);
            g2.dispose();
        }
    }

    private static class StepTabbedPaneUI extends BasicTabbedPaneUI {
        private final Color selectedColor = Color.WHITE;
        private final Color unselectedColor = new Color(210, 210, 210);
        private final Color borderColor = new Color(70, 70, 70);
        private final Color textColor = Color.BLACK;

        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabAreaInsets = new Insets(8, 8, 0, 8);
            contentBorderInsets = new Insets(4, 4, 4, 4);
            selectedTabPadInsets = new Insets(2, 2, 2, 2);
        }

        @Override
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                          int x, int y, int w, int h, boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(isSelected ? selectedColor : unselectedColor);
            g2.fillRect(x, y, w, h);
            g2.dispose();
        }

        @Override
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                      int x, int y, int w, int h, boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(isSelected ? 3f : 2f));
            g2.drawRect(x, y, w - 1, h - 1);
            g2.dispose();
        }

        @Override
        protected void paintFocusIndicator(Graphics g, int tabPlacement,
                                           Rectangle[] rects, int tabIndex,
                                           Rectangle iconRect, Rectangle textRect,
                                           boolean isSelected) {
        }

        @Override
        protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics,
                                 int tabIndex, String title, Rectangle textRect, boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            Font drawFont = font.deriveFont(Font.BOLD, 20f);
            g2.setFont(drawFont);
            FontMetrics fm = g2.getFontMetrics(drawFont);
            g2.setColor(textColor);

            int textY = textRect.y + fm.getAscent();
            g2.drawString(title, textRect.x, textY);

            if (isSelected) {
                g2.setStroke(new BasicStroke(2f));
                int underlineY = textY + 4;
                g2.drawLine(textRect.x, underlineY, textRect.x + fm.stringWidth(title), underlineY);
            }

            g2.dispose();
        }

        @Override
        protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
            return Math.max(46, super.calculateTabHeight(tabPlacement, tabIndex, fontHeight) + 12);
        }

        @Override
        protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
            return super.calculateTabWidth(tabPlacement, tabIndex, metrics) + 28;
        }

        @Override
        protected void paintContentBorderTopEdge(Graphics g, int tabPlacement, int selectedIndex,
                                                 int x, int y, int w, int h) {
            g.setColor(borderColor);
            g.drawLine(x, y, x + w - 1, y);
        }

        @Override
        protected void paintContentBorderLeftEdge(Graphics g, int tabPlacement, int selectedIndex,
                                                  int x, int y, int w, int h) {
            g.setColor(borderColor);
            g.drawLine(x, y, x, y + h - 1);
        }

        @Override
        protected void paintContentBorderRightEdge(Graphics g, int tabPlacement, int selectedIndex,
                                                   int x, int y, int w, int h) {
            g.setColor(borderColor);
            g.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
        }

        @Override
        protected void paintContentBorderBottomEdge(Graphics g, int tabPlacement, int selectedIndex,
                                                    int x, int y, int w, int h) {
            g.setColor(borderColor);
            g.drawLine(x, y + h - 1, x + w - 1, y + h - 1);
        }
    }
}