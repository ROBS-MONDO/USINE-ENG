import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;


import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

public class PLC3_Main extends JFrame {

    private static final String UI_VERSION = "V45.00";

    private static final String BAG_ICON_RESOURCE = "/icons/Exit.png";
    private static final String BAG_ICON_WINDOWS_FALLBACK =
            "C:\\_Tools\\Banbury2\\Icons\\Exit.png";

    private static final String RECIPE_ICON_RESOURCE = "/icons/Recipe.png";
    private static final String RECIPE_ICON_WINDOWS_FALLBACK =
    		"C:\\_Tools\\Banbury2\\Icons\\Recipe.png";

    private static final Path LOG_FILE = Paths.get(
            "C:\\_tools\\Banbury2",
            "PLC3_Main.log"
    );
    private static final DateTimeFormatter LOG_TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String DB_URL =
            "jdbc:sqlserver://srv-bambury:1433;"
                    + "databaseName=DATA_BAMBURY;"
                    + "encrypt=true;"
                    + "trustServerCertificate=true;"
                    + "sslProtocol=TLSv1.2;"
                    + "loginTimeout=5";

    private static final String DB_USER = "Soprasteria";
    private static final String DB_PASSWORD = "*22+Sopra8ybEt*";


    private final MixerPanel panel;
    private BufferedImage bagIconImage;
    private BufferedImage recipeIconImage;

    public PLC3_Main() {
        PLC3_StepRecorder.start();

        setTitle("PLC3_Main - " + UI_VERSION);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setUndecorated(true);

        loadBagIconImage();
        loadRecipeIconImage();

        panel = new MixerPanel(this, bagIconImage, recipeIconImage);
        setContentPane(panel);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                panel.shutdown();
            }
        });

        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
    }

    @Override
    public void dispose() {
        try {
            if (panel != null) {
                panel.shutdown();
            }
        } finally {
            super.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                OpcManager.getClient("PLC3 Main", "urn:plc3:main");
                new PLC3_Main().setVisible(true);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(
                        null,
                        "Erreur de connexion OPC : " + ex.getMessage(),
                        "Erreur",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }

    private void loadBagIconImage() {
        try (InputStream is = PLC3_Main.class.getResourceAsStream(BAG_ICON_RESOURCE)) {
            if (is != null) {
                bagIconImage = ImageIO.read(is);
                if (bagIconImage != null) {
                    System.out.println("Icône BAG chargée depuis la ressource : " + BAG_ICON_RESOURCE);
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
                    System.out.println("Icône BAG chargée depuis le fichier Windows : " + BAG_ICON_WINDOWS_FALLBACK);
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        bagIconImage = null;
        System.err.println("Icône BAG introuvable.");
    }

    private void loadRecipeIconImage() {
        try (InputStream is = PLC3_Main.class.getResourceAsStream(RECIPE_ICON_RESOURCE)) {
            if (is != null) {
                recipeIconImage = ImageIO.read(is);
                if (recipeIconImage != null) {
                    System.out.println("Icône RECIPE chargée depuis la ressource : " + RECIPE_ICON_RESOURCE);
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            File fallbackFile = new File(RECIPE_ICON_WINDOWS_FALLBACK);
            if (fallbackFile.exists()) {
                recipeIconImage = ImageIO.read(fallbackFile);
                if (recipeIconImage != null) {
                    System.out.println("Icône RECIPE chargée depuis le fichier Windows : " + RECIPE_ICON_WINDOWS_FALLBACK);
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        recipeIconImage = null;
        System.err.println("Icône RECIPE introuvable.");
    }

    private static synchronized void logWrite(
            NodeId nodeId,
            String insertedValue,
            String writtenValue,
            String outcome,
            String details) {

        try {
            Files.createDirectories(LOG_FILE.getParent());
            cleanupOldLogLines();

            String safeInserted = safeLogValue(insertedValue);
            String safeWritten = safeLogValue(writtenValue);
            String safeOutcome = safeLogValue(outcome);
            String safeDetails = safeLogValue(details);
            String userName = safeLogValue(System.getProperty("user.name", "unknown"));

            String line = LocalDateTime.now().format(LOG_TS_FORMAT)
                    + " | user=" + userName
                    + " | node=" + safeLogValue(nodeId != null ? nodeId.toParseableString() : "")
                    + " | inserted=" + safeInserted
                    + " | written=" + safeWritten
                    + " | outcome=" + safeOutcome
                    + (safeDetails.isEmpty() ? "" : " | details=" + safeDetails)
                    + System.lineSeparator();

            Files.write(
                    LOG_FILE,
                    line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception e) {
            System.err.println("Errore scrittura log " + LOG_FILE + ": " + e.getMessage());
        }
    }

    private static synchronized void logButtonPress(
            String buttonName,
            NodeId nodeId,
            String outcome,
            String details) {

        try {
            Files.createDirectories(LOG_FILE.getParent());
            cleanupOldLogLines();

            String safeButton = safeLogValue(buttonName);
            String safeOutcome = safeLogValue(outcome);
            String safeDetails = safeLogValue(details);
            String userName = safeLogValue(System.getProperty("user.name", "unknown"));

            String line = LocalDateTime.now().format(LOG_TS_FORMAT)
                    + " | user=" + userName
                    + " | button=" + safeButton
                    + " | node=" + safeLogValue(nodeId != null ? nodeId.toParseableString() : "")
                    + " | outcome=" + safeOutcome
                    + (safeDetails.isEmpty() ? "" : " | details=" + safeDetails)
                    + System.lineSeparator();

            Files.write(
                    LOG_FILE,
                    line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception e) {
            System.err.println("Errore scrittura log " + LOG_FILE + ": " + e.getMessage());
        }
    }

    private static void cleanupOldLogLines() {
        try {
            if (!Files.exists(LOG_FILE)) {
                return;
            }

            LocalDateTime threshold = LocalDateTime.now().minusDays(2);
            List<String> lines = Files.readAllLines(LOG_FILE, StandardCharsets.UTF_8);
            List<String> kept = new java.util.ArrayList<>();

            for (String line : lines) {
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                if (shouldKeepLogLine(line, threshold)) {
                    kept.add(line);
                }
            }

            Files.write(
                    LOG_FILE,
                    kept,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (Exception e) {
            System.err.println("Errore pulizia log " + LOG_FILE + ": " + e.getMessage());
        }
    }

    private static boolean shouldKeepLogLine(String line, LocalDateTime threshold) {
        try {
            if (line.length() < 19) {
                return false;
            }
            LocalDateTime logTime = LocalDateTime.parse(line.substring(0, 19), LOG_TS_FORMAT);
            return !logTime.isBefore(threshold);
        } catch (Exception e) {
            return false;
        }
    }

    private static String safeLogValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\n", " ")
                .replace("\r", " ")
                .trim();
    }

    static class MixerPanel extends JPanel {
        private static final int BASE_W = 1000;
        private static final int BASE_H = 720;

        private static final String APP_NAME = "PLC3 Main";
        private static final String APP_URI = "urn:plc3:main";

        private static final int BOTTOM_BUTTON_W = 78;
        private static final int BOTTOM_BUTTON_H = 60;
        private static final int BOTTOM_BUTTON_SPACING = 38;
        private static final int BOTTOM_BUTTON_Y = 638;

        private static final int EDITOR_FIELD_W = 320;
        private static final int EDITOR_FIELD_H = 52;
        private static final int EDITOR_WRITE_W = 120;
        private static final int EDITOR_WRITE_H = 52;
        private static final int KEYBOARD_KEY_W = 66;
        private static final int KEYBOARD_KEY_H = 52;
        private static final int KEYBOARD_BACK_W = 96;
        private static final int KEYBOARD_BACK_H = 52;
        private static final int KEYBOARD_CLEAR_W = 118;
        private static final int KEYBOARD_CLEAR_H = 52;
        private static final Font KEYBOARD_BUTTON_FONT = new Font("SansSerif", Font.BOLD, 18);
        private static final Font EDITOR_FIELD_FONT = new Font("SansSerif", Font.BOLD, 28);
        private static final Font EDITOR_WRITE_FONT = new Font("SansSerif", Font.BOLD, 18);

        private static final Color LIQUID_BUTTON_OFF = new Color(155, 155, 155);
        private static final Color LIQUID_BUTTON_ON = new Color(39, 255, 31);
        private static final Color LIQUID_BUTTON_FLASH = Color.YELLOW;
        private static final Color LIQUID_BUTTON_BORDER = new Color(85, 85, 85);

        private static final int MOTOR_SHIFT_X = 105;
        private static final int MOTOR_SHIFT_Y = -28;

        private static final float PIPE_FILL_STROKE = 14f;
        private static final float PIPE_BORDER_STROKE = 2.4f;
        private static final int FLOW_DOT_SIZE = 6;
        private static final int FLOW_STEP = 20;
        private static final Color LIQUID_FLOW_COLOR = new Color(185, 140, 48);
        private static final Color LIQUID_FLOW_HIGHLIGHT = new Color(224, 195, 92);

        private static final int LIQUID_VALVE_X = 152;
        private static final int LIQUID_VALVE_DIAMETER = 34;
        private static final int LIQUID_PIPE_SIDE_LEN = 18;
        private static final int LIQUID_COLLECTOR_X = LIQUID_VALVE_X + LIQUID_VALVE_DIAMETER + LIQUID_PIPE_SIDE_LEN;
        private static final int LIQUID_MIXER_ENTRY_X = 546;
        private static final int LEAK_STREAM_START_X = 611;
        private static final int LEAK_PRESSURE_X_OFFSET = 26;
        private static final int LEAK_PRESSURE_BEND_Y = 48;
        private static final int LEAK_STREAM_ENTRY_Y_OFFSET = 2;
        private static final int LEAK_STREAM_END_Y = 386;
        private static final int LEAK_OFFSET_X = 6;
        private static final int LEAK_STREAM_WIDTH = 12;
        private static final int LIQUID_Y_DOA = 96;
        private static final int LIQUID_Y_SILANO = 166;
        private static final int LIQUID_Y_OLIO = 239;

        // Layout gruppo status nel riquadro blu
        private static final int STATUS_GROUP_X = 250;

        // STEP N layout - allineato alle label (X=258, W=228 → right edge = 486)
        private static final int STATUS_LABEL_RIGHT_EDGE = 258 + 228; // = 486

        // lblStepTitle: parte da X=305, si estende fino al bordo destro delle label
        private static final int STATUS_GROUP_STEP_LABEL_X = 305;
        private static final int STATUS_GROUP_STEP_LABEL_W = STATUS_LABEL_RIGHT_EDGE - STATUS_GROUP_STEP_LABEL_X; // = 181

        // Campo lblStepDroite: W=78, il bordo destro coincide con STATUS_LABEL_RIGHT_EDGE
        private static final int STATUS_GROUP_STEP_FIELD_W = 78;
        private static final int STATUS_GROUP_STEP_FIELD_X = STATUS_LABEL_RIGHT_EDGE - STATUS_GROUP_STEP_FIELD_W; // = 408

        // Y e H condivisi tra label e campo (bordi top/bottom a contatto)
        private static final int STATUS_GROUP_STEP_Y = 120;
        private static final int STATUS_GROUP_STEP_H = 34;

        private static final int STATUS_LABEL_W = 228;
        private static final int STATUS_LABEL_H = 24;
        private static final int STATUS_LABEL_X = 258;

        // Recipe + Scheduler in alto
        private static final int STATUS_RECIPE_Y = 54;
        private static final int STATUS_LABEL_Y1 = 88;

        // blocco 4 label spostato più in basso per evitare sovrapposizioni
        private static final int STATUS_AFTER_SCHEDULER_Y1 = 215;
        private static final int STATUS_LABEL_GAP = 34;

        private static final NodeId NODE_TEMP_ACTUEL =
                NodeId.parse("ns=2;s=Banbury 2.PLC3.R00801");
        private static final NodeId NODE_TEMP_CONSIGNE =
                NodeId.parse("ns=2;s=Banbury 2.PLC3.R03502");
        private static final NodeId NODE_VITESSE_ACTUEL =
                NodeId.parse("ns=2;s=Banbury 2.PLC3.R00802");
        private static final NodeId NODE_VITESSE_CONSIGNE =
                NodeId.parse("ns=2;s=Banbury 2.PLC3.R00845");

        private static final NodeId NODE_CONSIGNE =
                NodeId.parse("ns=2;s=Banbury 2.PLC3.R2018");

        private static final NodeId NODE_STEP_DROITE =
                NodeId.parse("ns=2;s=Banbury 2.PLC3.R02008");

        private static final NodeId NODE_COURANT =
                NodeId.parse("ns=2;s=Banbury 2.PLC3.R00803");
        private static final NodeId NODE_SCHEDULER =
                NodeId.parse("ns=2;s=Banbury 2.PLC3.R32560");

        private static final NodeId NODE_TEMPO_CONSIGNE =
                NodeId.parse("ns=2;s=Banbury 2.PLC3.R03504");
        private static final NodeId NODE_TEMPO_ACTUEL =
                NodeId.parse("ns=2;s=Banbury 2.PLC3.R02002");

        private static final NodeId NODE_CHARGEMENT_SILOS =
                NodeId.parse("ns=2;s=Banbury 2.PLC3.R03500");
        private static final NodeId NODE_CHARGEMENT_SILOS_2 =
                NodeId.parse("ns=2;s=Banbury 2.PLC3.R03510");
        private static final NodeId NODE_CHARGEMENT_SILOS_3 =
                NodeId.parse("ns=2;s=Banbury 2.PLC3.R03516");
        private static final NodeId NODE_CYCLE_EN_COURS =
                NodeId.parse("ns=2;s=Banbury 2.PLC3.R02006");

        private static final NodeId NODE_MELANGE_EN_COURS =
                NodeId.parse("ns=2;s=Banbury 2.PLC3.R101");

        private static final NodeId NODE_DOSAGGIO_DOA =
                NodeId.parse("ns=2;s=Banbury 2.PLC3.M6010");
        private static final NodeId NODE_DOSAGGIO_SILANO =
                NodeId.parse("ns=2;s=Banbury 2.PLC3.M06011");
        private static final NodeId NODE_DOSAGGIO_OLIO =
                NodeId.parse("ns=2;s=Banbury 2.PLC3.M06012");

        private static final NodeId NODE_LIQUID_RESET_MONITOR =
                NodeId.parse("ns=2;s=Banbury 2.PLC3.R2018");

        private static final NodeId NODE_PRESET_LIQUIDI_SILANO =
                NodeId.parse("ns=2;s=Banbury 2.PLC3.R2018");

        private final PLC3_Main parentFrame;
        private final BufferedImage bagIconImage;
        private final BufferedImage recipeIconImage;

        private JLabel lblOra;
        private JLabel lblData;
        private JLabel lblVersion;
        private JLabel lblRecipe;
        private JLabel lblScheduler;
        private JLabel lblTempActuel;
        private JLabel lblTempConsigne;
        private JLabel lblMelangeEnCours;
        private JLabel lblVitesseActuel;
        private JLabel lblVitesseConsigne;
        private JLabel lblConsigne;
        private JLabel lblStepDroite;
        private JLabel lblCourant;
        private JLabel lblTempoConsigne;
        private JLabel lblTempoActuel;

        private JLabel lblChargementSilos;
        private JLabel lblChargementSilos2;
        private JLabel lblChargementSilos3;
        private JLabel lblCycleEnCours;
        private JLabel lblAutoMode;
        private JLabel lblManualMode;

        private DosageButton btnDoa;
        private DosageButton btnSilano;
        private DosageButton btnOlioProcesso;
        private JButton btnAck;

        private Boolean lastDoaActive = null;
        private Boolean lastSilanoActive = null;
        private Boolean lastOlioActive = null;

        private final AtomicBoolean reading = new AtomicBoolean(false);

        private final AtomicBoolean liquidSequenceInProgress = new AtomicBoolean(false);
        private final AtomicBoolean liquidStopRequested = new AtomicBoolean(false);
        private final AtomicReference<NodeId> activeLiquidNode = new AtomicReference<>(null);

        private volatile boolean doaSequenceActive = false;
        private volatile boolean doaPulseInProgress = false;
        private volatile boolean silanoSequenceActive = false;
        private volatile boolean silanoPulseInProgress = false;
        private volatile boolean huileSequenceActive = false;
        private volatile boolean huilePulseInProgress = false;

        private ScheduledExecutorService opcScheduler;
        private volatile boolean shutdown = false;
        private Timer dateTimeTimer;
        private Timer flowTimer;
        private float flowOffset = 0f;
        private double rotorMasterAngle = 0.0;
        private double rotorSpeed = 0.0;
        private final java.util.ArrayList<OilParticle> oilParticles = new java.util.ArrayList<>();
        private final java.util.Random oilRng = new java.util.Random();

        // Animazione tapis roulant porta CHARGE
        private final java.util.ArrayList<ChargePacket> chargePackets = new java.util.ArrayList<>();
        private final java.util.Random chargeRng = new java.util.Random();
        private float conveyorOffset = 0f;
        private int chargePacketCooldown = 0; // frame di attesa tra uno spawn e il successivo

        private volatile DoorState chargeDoorState = DoorState.CLOSED;
        private volatile DoorState dischargeDoorState = DoorState.CLOSED;
        private volatile String currentDbMode = "";
        private volatile long lastDoorStateDbPollTs = 0L;
        private double chargeDoorProgress = 0.0;
        private double dischargeDoorProgress = 0.0;

        private static final double ROTOR_RIGHT_PHASE_OFFSET = 90.0;

        private static final int DOOR_STROKE = 3;
        private static final int DOOR_GUIDE_TOP_LEN = 28;
        private static final int DOOR_GUIDE_BOTTOM_LEN = 28;
        private static final int DOOR_HINGE_DIAMETER = 8;

        private static final int CHARGE_DOOR_CLOSED_X = 546;
        private static final int CHARGE_DOOR_TOP_GUIDE_Y = 250;
        private static final int CHARGE_DOOR_LEAF_Y1 = 250;
        private static final int CHARGE_DOOR_LEAF_Y2 = 330;
        private static final int CHARGE_DOOR_BOTTOM_GUIDE_Y = 330;
        private static final int CHARGE_DOOR_OPEN_TOP_X = 507;
        private static final int CHARGE_DOOR_OPEN_TOP_Y = 250;
        private static final int CHARGE_DOOR_OPEN_BOTTOM_X = 553;
        private static final int CHARGE_DOOR_OPEN_BOTTOM_Y = 330;
        private static final int CHARGE_DOOR_PIVOT_X = 553;
        private static final int CHARGE_DOOR_PIVOT_Y = 330;

        private static final int DISCHARGE_DOOR_PIVOT_X = 670;
        private static final int DISCHARGE_DOOR_PIVOT_Y = 497;
        private static final int DISCHARGE_DOOR_LENGTH = 72;
        private static final double DISCHARGE_DOOR_CLOSED_ANGLE_DEG = 0.0;
        private static final double DISCHARGE_DOOR_OPEN_ANGLE_DEG = 120.0;
        private static final int DISCHARGE_DOOR_PIVOT_SIZE = 8;
        private static final int CHARGE_DOOR_PANEL_LENGTH = 84;
        private static final int CHARGE_DOOR_PANEL_WIDTH = 12;
        private static final double CHARGE_DOOR_CLOSED_ANGLE_DEG = 0.0;
        private static final double CHARGE_DOOR_OPEN_ANGLE_DEG = -60.0;

        private static final int DISCHARGE_DOOR_PANEL_LENGTH = 84;
        private static final int DISCHARGE_DOOR_PANEL_WIDTH = 12;

        private enum DoorState {
            CLOSED,
            OPEN
        }

        private static final class DoorSnapshot {
            private final DoorState chargeDoorState;
            private final DoorState dischargeDoorState;
            private final String modeValue;

            private DoorSnapshot(DoorState chargeDoorState, DoorState dischargeDoorState, String modeValue) {
                this.chargeDoorState = chargeDoorState;
                this.dischargeDoorState = dischargeDoorState;
                this.modeValue = modeValue;
            }
        }

        private static final class OilParticle {
            double x;
            double y;
            double vx;
            double vy;
            float alpha;
            int size;

            OilParticle(double x, double y, double vx, double vy, int size) {
                this.x = x;
                this.y = y;
                this.vx = vx;
                this.vy = vy;
                this.size = size;
                this.alpha = 1.0f;
            }

            void update() {
                x += vx;
                y += vy;
                vy += 0.28;
                vx *= 0.992;
                alpha -= 0.014f;
            }

            boolean isDead() {
                return alpha <= 0.0f || y > LEAK_STREAM_END_Y + 36;
            }
        }

        /**
         * Pacchetto/sacco animato sul tapis roulant della porta CHARGE.
         * Si muove da destra verso sinistra sul nastro, poi cade dentro il mixer
         * quando supera il bordo del portello.
         */
        private static final class ChargePacket {
            double x;
            double y;
            double vx;
            double vy;
            float alpha;
            int w;
            int h;
            Color color;
            boolean falling;

            ChargePacket(double x, double y, double vx, double vy, int w, int h, Color color) {
                this.x = x;
                this.y = y;
                this.vx = vx;
                this.vy = vy;
                this.w = w;
                this.h = h;
                this.color = color;
                this.alpha = 1.0f;
                this.falling = false;
            }

            void update() {
                if (!falling) {
                    x += vx;
                    y += vy;
                    // cade quando raggiunge il pivot della porta (estremo destro del nastro)
                    final double BELT_ANGLE_RAD = Math.toRadians(30.0);
                    double dirX = Math.cos(BELT_ANGLE_RAD);
                    double dirY = Math.sin(BELT_ANGLE_RAD);
                    // proiezione sulla direzione nastro: cade quando supera il pivot
                    double proj = (x - CHARGE_DOOR_PIVOT_X) * dirX + (y - CHARGE_DOOR_PIVOT_Y) * dirY;
                    if (proj >= 0) {
                        falling = true;
                        // centro mixer ≈ X 611 (tra parete sx 558 e parete dx 665)
                        final double MIXER_CENTER_X = 611.0;
                        double dx = MIXER_CENTER_X - x;
                        double dist = Math.max(1.0, Math.abs(dx));
                        // velocità orizzontale verso il centro, verticale fissa
                        vx = dx / dist * 1.8;
                        vy = 1.5;
                    }
                } else {
                    x += vx;
                    y += vy;
                    vy += 0.35;
                    alpha -= 0.02f;
                }
            }

            boolean isDead() {
                return alpha <= 0f || y > CHARGE_DOOR_PIVOT_Y + 80;
            }
        }

        private void incrementStepNByOne() {
            if (shutdown) {
                return;
            }

            if (btnAck != null) {
                btnAck.setEnabled(false);
            }

            new Thread(() -> {
                try {
                    int currentStep = 0;

                    if (lblStepDroite != null) {
                        String txt = lblStepDroite.getText();
                        if (txt != null && !txt.trim().isEmpty()) {
                            try {
                                currentStep = Integer.parseInt(txt.trim());
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    int newStep = currentStep + 1;

                    if (newStep < 0 || newStep > 65535) {
                        throw new IllegalArgumentException("Valeur hors plage pour UShort : " + newStep);
                    }

                    UShort valueToWrite = ushort(newStep);

                    StatusCode status = OpcManager.writeValue(
                            APP_NAME,
                            APP_URI,
                            NODE_STEP_DROITE,
                            DataValue.valueOnly(new Variant(valueToWrite))
                    );

                    final String insertedForLog = String.valueOf(newStep);
                    final String writtenForLog = String.valueOf(newStep);

                    if (shutdown) {
                        return;
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (shutdown || !isDisplayable()) {
                            return;
                        }

                        if (status != null && status.isGood()) {
                            PLC3_Main.logWrite(
                                    NODE_STEP_DROITE,
                                    insertedForLog,
                                    writtenForLog,
                                    "SUCCESS",
                                    "ACK STEP N"
                            );

                            updateField(lblStepDroite, String.valueOf(newStep));
                        } else {
                            PLC3_Main.logWrite(
                                    NODE_STEP_DROITE,
                                    insertedForLog,
                                    writtenForLog,
                                    "REJECTED",
                                    String.valueOf(status)
                            );

                            JOptionPane.showMessageDialog(
                                    parentFrame,
                                    "Écriture STEP N refusée : " + status,
                                    "Erreur",
                                    JOptionPane.ERROR_MESSAGE
                            );
                        }

                        if (btnAck != null) {
                            btnAck.setEnabled(true);
                        }
                    });
                } catch (Exception ex) {
                    PLC3_Main.logWrite(
                            NODE_STEP_DROITE,
                            "",
                            "",
                            "ERROR",
                            ex.getMessage()
                    );

                    if (shutdown) {
                        return;
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (shutdown || !isDisplayable()) {
                            return;
                        }

                        JOptionPane.showMessageDialog(
                                parentFrame,
                                "Erreur incrémentation STEP N : " + ex.getMessage(),
                                "Erreur",
                                JOptionPane.ERROR_MESSAGE
                        );

                        if (btnAck != null) {
                            btnAck.setEnabled(true);
                        }
                    });
                }
            }, "plc3-ack-increment-thread").start();
        }

        private void addBottomButtonsCentered() {
            int totalWidth = (BOTTOM_BUTTON_W * 5) + (BOTTOM_BUTTON_SPACING * 4);
            int startX = (BASE_W - totalWidth) / 2;

            btnAck = addUnifiedBottomButton(startX, BOTTOM_BUTTON_Y, IconType.ACK, "ACK");
            addUnifiedBottomButton(startX + (BOTTOM_BUTTON_W + BOTTOM_BUTTON_SPACING), BOTTOM_BUTTON_Y, IconType.WARNING, "WARNING");
            addUnifiedBottomButton(startX + 2 * (BOTTOM_BUTTON_W + BOTTOM_BUTTON_SPACING), BOTTOM_BUTTON_Y, IconType.DIAMOND, "DIAMOND");
            addUnifiedBottomButton(startX + 3 * (BOTTOM_BUTTON_W + BOTTOM_BUTTON_SPACING), BOTTOM_BUTTON_Y, IconType.BARS, "BARS");
            addUnifiedBottomButton(startX + 4 * (BOTTOM_BUTTON_W + BOTTOM_BUTTON_SPACING), BOTTOM_BUTTON_Y, IconType.BAG, "BAG");
        }

        MixerPanel(PLC3_Main parentFrame, BufferedImage bagIconImage, BufferedImage recipeIconImage) {
            this.parentFrame = parentFrame;
            this.bagIconImage = bagIconImage;
            this.recipeIconImage = recipeIconImage;

            setLayout(null);
            setBackground(new Color(185, 185, 185));

            buildUi();
            installResizeHandling();
            startDateTimeRefresh();
            startFlowAnimation();
            startOpcRefresh();
        }

        private void installResizeHandling() {
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    rescaleComponents();
                    repaint();
                }
            });

            SwingUtilities.invokeLater(() -> {
                rescaleComponents();
                repaint();
            });
        }

        private double scaleX() {
            return getWidth() <= 0 ? 1.0 : (double) getWidth() / BASE_W;
        }

        private double scaleY() {
            return getHeight() <= 0 ? 1.0 : (double) getHeight() / BASE_H;
        }

        private int sx(int value) {
            return (int) Math.round(value * scaleX());
        }

        private int sy(int value) {
            return (int) Math.round(value * scaleY());
        }

        private void registerBaseBounds(JComponent c, int x, int y, int w, int h) {
            c.putClientProperty("baseBounds", new Rectangle(x, y, w, h));
            if (c.getFont() != null) {
                c.putClientProperty("baseFontSize", Float.valueOf(c.getFont().getSize2D()));
            }
            add(c);
        }

        private void rescaleComponents() {
            for (Component c : getComponents()) {
                if (!(c instanceof JComponent)) {
                    continue;
                }

                JComponent jc = (JComponent) c;

                Object obj = jc.getClientProperty("baseBounds");
                if (obj instanceof Rectangle) {
                    Rectangle r = (Rectangle) obj;
                    c.setBounds(sx(r.x), sy(r.y), sx(r.width), sy(r.height));
                }

                Object fontObj = jc.getClientProperty("baseFontSize");
                if (fontObj instanceof Float && c.getFont() != null) {
                    float baseSize = (Float) fontObj;
                    float factor = (float) Math.min(scaleX(), scaleY());
                    float newSize = Math.max(9f, baseSize * factor);
                    c.setFont(c.getFont().deriveFont(newSize));
                }
            }

            revalidate();
            repaint();
        }

        private void buildUi() {
            lblOra = addField("00:00:00", 22, 18, 95, 39, 18, false, false);
            lblData = addField("18/03/26", 125, 18, 95, 39, 18, false, false);
            lblVersion = addVersionLabel(UI_VERSION, 858, 18, 120, 30);

            JLabel mixer = new JLabel("MIXER", SwingConstants.CENTER);
            mixer.setForeground(new Color(36, 62, 207));
            mixer.setFont(new Font("Serif", Font.BOLD, 28));
            registerBaseBounds(mixer, 400, 12, 150, 40);

            int liquidColumnX = 22;
            int liquidButtonW = 118;
            int liquidButtonH = 58;
            int liquidGapY = 12;
            int liquidStartY = 82;

            btnDoa = addDoaButton(
                    "DOA",
                    liquidColumnX,
                    liquidStartY,
                    liquidButtonW,
                    liquidButtonH
            );

            btnSilano = addSilanoButton(
                    "SILANO",
                    liquidColumnX,
                    liquidStartY + liquidButtonH + liquidGapY,
                    liquidButtonW,
                    liquidButtonH
            );

            btnOlioProcesso = addHuileButton(
                    "<html><center><span style='color:black;'>HUILE<br>PLASTIFIANTE</span></center></html>",
                    liquidColumnX,
                    liquidStartY + (liquidButtonH + liquidGapY) * 2,
                    liquidButtonW,
                    62
            );

            addText("CONSIGNE", liquidColumnX + 25, liquidStartY + 206, 15, true);
            lblConsigne = addField("0.0", liquidColumnX, liquidStartY + 228, 120, 42, 25, false, true);

            addText("TEMPO", 65, 405, 15, true);
            addText("CONSIGNE", 18, 432, 14, true);
            addText("ACTUEL", 100, 432, 14, true);
            lblTempoConsigne = addField("0", 16, 458, 68, 34, 18, false, false);
            lblTempoActuel = addField("0", 90, 458, 68, 34, 18, false, false);

            JPanel greenMeter = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(new Color(52, 255, 47));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(new Color(15, 138, 16));
                    g2.setStroke(new BasicStroke(2));
                    g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                    int lines = 3;
                    for (int i = 0; i < lines; i++) {
                        int yy = 6 + i * Math.max(6, getHeight() / 4);
                        g2.drawLine(6, yy, getWidth() - 6, yy);
                    }
                    g2.dispose();
                }
            };
            greenMeter.setOpaque(false);
            registerBaseBounds(greenMeter, 246 + MOTOR_SHIFT_X, 396 + MOTOR_SHIFT_Y, 63, 35);

            addButton("ME1M", 150 + MOTOR_SHIFT_X, 514 + MOTOR_SHIFT_Y, 52, 22, new Color(233, 233, 233), Color.BLACK, 14);

            addText("COURANT", 128 + MOTOR_SHIFT_X, 444 + MOTOR_SHIFT_Y, 15, true);
            lblCourant = addField("0", 120 + MOTOR_SHIFT_X, 465 + MOTOR_SHIFT_Y, 80, 40, 22, false, false);
            lblCourant.setBackground(new Color(39, 255, 31));
            
            addText("TEMPERATURE", 780, 260, 16, true);
            addText("MELANGE", 800, 280, 16, true);
            addText("ACTUEL", 765, 319, 15, true);
            addText("CONSIGNE", 865, 319, 15, true);

            lblTempActuel = addField("0", 745, 349, 95, 52, 28, false, false);
            lblTempConsigne = addField("0", 850, 349, 95, 52, 28, false, false);

            addText("MELANGES FAITS", 792, 424, 15, true);
            lblMelangeEnCours = addField("0", 800, 452, 95, 44, 24, false, false);

            addText("VITESSE", 585, 500, 15, true);
            addText("ACTUEL", 535, 521, 15, true);
            lblVitesseActuel = addField("0", 515, 554, 90, 48, 28, false, false);
            addText("CONSIGNE", 625, 521, 15, true);
            lblVitesseConsigne = addField("0", 615, 554, 90, 48, 28, false, true);

            // RECIPE sopra lo scheduler
            lblRecipe = addValueButtonLikeField(
                    "Recipe",
                    STATUS_LABEL_X,
                    STATUS_RECIPE_Y,
                    STATUS_LABEL_W,
                    STATUS_LABEL_H,
                    new Color(214, 214, 214),
                    new Color(90, 90, 90),
                    14
            );

            // SCHEDULER sopra
            lblScheduler = addValueButtonLikeField(
                    "",
                    STATUS_LABEL_X,
                    STATUS_LABEL_Y1,
                    STATUS_LABEL_W,
                    STATUS_LABEL_H,
                    new Color(214, 214, 214),
                    new Color(90, 90, 90),
                    14
            );

            // STEP N: etichetta "STEP N" con bordo senza lato destro,
            // allineata in larghezza con le altre label e con il campo adiacente.
            // Y e H uguali al campo così i bordi top/bottom toccano il campo.
      
         // STEP N sotto lo scheduler
            JLabel lblStepTitle = new JLabel("STEP N", SwingConstants.CENTER);
            lblStepTitle.setFont(new Font("Arial", Font.BOLD, 15));
            lblStepTitle.setForeground(Color.BLACK);
            lblStepTitle.setOpaque(true);
            lblStepTitle.setBackground(new Color(214, 214, 214));

            // bordo nero senza lato destro, in continuità col campo a destra
            lblStepTitle.setBorder(new StepLeftBorder(Color.BLACK, 2));

            // allineamento con le altre label:
            // sinistra = 258, destra finale del campo = 486
            registerBaseBounds(
                    lblStepTitle,
                    258,   // allineato a sinistra con le altre label
                    118,   // stessa Y del campo
                    150,   // arriva fino all'inizio del campo
                    34     // stessa altezza del campo
            );

            lblStepDroite = addField(
                    "0",
                    408,   // spostato a destra
                    117,
                    78,
                    34,
                    16,
                    false,
                    true
            );

            // 4 label sotto spostate più in basso per evitare sovrapposizioni
            lblChargementSilos = addValueButtonLikeField(
                    "CHARGEMENT SILOS",
                    STATUS_LABEL_X,
                    STATUS_AFTER_SCHEDULER_Y1,
                    STATUS_LABEL_W,
                    STATUS_LABEL_H,
                    new Color(214, 214, 214),
                    Color.BLACK,
                    14
            );

            lblChargementSilos2 = addValueButtonLikeField(
                    "",
                    STATUS_LABEL_X,
                    STATUS_AFTER_SCHEDULER_Y1 + STATUS_LABEL_GAP,
                    STATUS_LABEL_W,
                    STATUS_LABEL_H,
                    new Color(214, 214, 214),
                    Color.BLACK,
                    14
            );

            lblChargementSilos3 = addValueButtonLikeField(
                    "",
                    STATUS_LABEL_X,
                    STATUS_AFTER_SCHEDULER_Y1 + STATUS_LABEL_GAP * 2,
                    STATUS_LABEL_W,
                    STATUS_LABEL_H,
                    new Color(214, 214, 214),
                    Color.BLACK,
                    14
            );

            lblCycleEnCours = addValueButtonLikeField(
                    "CYCLE EN COURS",
                    STATUS_LABEL_X,
                    STATUS_AFTER_SCHEDULER_Y1 + STATUS_LABEL_GAP * 3,
                    STATUS_LABEL_W,
                    STATUS_LABEL_H,
                    new Color(214, 214, 214),
                    Color.BLACK,
                    14
            );

            configureUShortWritableField(
                    lblVitesseConsigne,
                    "Modifier VITESSE CONSIGNE",
                    NODE_VITESSE_CONSIGNE,
                    "VITESSE CONSIGNE"
            );

            configureDecimalWritableField(
                    lblConsigne,
                    "Modifier CONSIGNE SILANO",
                    NODE_CONSIGNE,
                    1,
                    "CONSIGNE SILANO"
            );

            configureUShortWritableField(
                    lblStepDroite,
                    "Modifier STEP N",
                    NODE_STEP_DROITE,
                    "STEP N"
            );

            lblAutoMode = addModeIndicatorField("AUTO", 24, 574, 96, 34);
            lblManualMode = addModeIndicatorField("MANUAL", 124, 574, 96, 34);
            updateModeIndicators("");

            addBottomButtonsCentered();
            updateLiquidButtonsInterlock();
        }

        private void startDateTimeRefresh() {
            updateDateTimeFields();
            dateTimeTimer = new Timer(1000, e -> updateDateTimeFields());
            dateTimeTimer.setInitialDelay(0);
            dateTimeTimer.start();
        }

        // ---------------------------------------------------------------
        // Animazione nastro trasportatore (tapis roulant) porta CHARGE
        // Attiva quando: step > 0, porta CHARGE aperta, modo AUTO
        // ---------------------------------------------------------------

        private boolean isChargeAnimationActive() {
            // step > 0
            int step = 0;
            if (lblStepDroite != null) {
                try {
                    String txt = lblStepDroite.getText();
                    if (txt != null && !txt.trim().isEmpty()) {
                        step = Integer.parseInt(txt.trim());
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (step <= 0) return false;

            // porta CHARGE completamente aperta
            if (chargeDoorProgress < 0.99) return false;

            // spia AUTO accesa
            boolean autoOn = lblAutoMode != null
                    && new Color(39, 255, 31).equals(lblAutoMode.getBackground());
            return autoOn;
        }

        private void updateChargePackets() {
            boolean active = isChargeAnimationActive();

            if (active) {
                conveyorOffset += 8.2f;
                if (conveyorOffset > 200f) conveyorOffset = 0f;

                // Cooldown fisso: spawn un pacchetto solo dopo che il precedente
                // ha percorso abbastanza nastro da non sovrapporsi (larghezza pacco + margine)
                // A 0.6 unità/frame su 80 unità di nastro, il cooldown minimo è ~(w+gap)/speed
                if (chargePacketCooldown > 0) {
                    chargePacketCooldown--;
                } else {
                    long onBelt = chargePackets.stream().filter(p -> !p.falling).count();
                    if (onBelt < 2) {
                        spawnChargePacket();
                        // attesa pari alla dimensione del pacco + 20px di margine diviso la velocità
                        chargePacketCooldown = (int) ((42 + 20) / 1.25); // ~49 frame, un po piu veloce ma ancora realistico
                    }
                }
            } else {
                chargePacketCooldown = 0;
                // porta in chiusura: rimuovi subito i pacchetti sul nastro
                chargePackets.removeIf(p -> !p.falling);
            }

            java.util.Iterator<ChargePacket> it = chargePackets.iterator();
            while (it.hasNext()) {
                ChargePacket p = it.next();
                p.update();
                if (p.isDead()) {
                    it.remove();
                }
            }
        }

        private void spawnChargePacket() {
            // Nastro a +30° rispetto all'orizzontale.
            // Estremo DESTRO (basso) = pivot della porta CHARGE (553, 330).
            final double BELT_ANGLE_RAD = Math.toRadians(30.0);
            final double BELT_LEN       = 80.0;

            // Direzione nastro: da sinistra-alto verso destra-basso (+30°)
            double dirX = Math.cos(BELT_ANGLE_RAD);
            double dirY = Math.sin(BELT_ANGLE_RAD);

            // Spawn all'estremo sinistro del nastro (pivot - direzione * lunghezza)
            double startX = CHARGE_DOOR_PIVOT_X - dirX * BELT_LEN;
            double startY = CHARGE_DOOR_PIVOT_Y - dirY * BELT_LEN;

            int w = 32 + chargeRng.nextInt(10);
            int h = 18 + chargeRng.nextInt(6);

            // Sposta il centro del pacco sul lato SUPERIORE del nastro:
            // normale superiore al nastro +30° in coord schermo (Y verso il basso):
            // la normale "verso l'alto" rispetto al nastro è (+sin30°, -cos30°) = (+0.5, -0.866)
            double normX =  Math.sin(BELT_ANGLE_RAD);
            double normY = -Math.cos(BELT_ANGLE_RAD);
            double beltHalfH = 5.0; // metà spessore nastro (BELT_H=10)
            startX += normX * (beltHalfH + h / 2.0);
            startY += normY * (beltHalfH + h / 2.0);
            double speed = 1.25 + chargeRng.nextDouble() * 0.28;
            double vx = dirX * speed;
            double vy = dirY * speed;

            Color[] bagColors = {
                new Color(90, 60, 30),
                new Color(60, 40, 20),
                new Color(200, 195, 185),
                new Color(70, 70, 70),
                new Color(180, 160, 100)
            };
            Color c = bagColors[chargeRng.nextInt(bagColors.length)];
            chargePackets.add(new ChargePacket(startX, startY, vx, vy, w, h, c));
        }

        private void drawChargeConveyorAndPackets(Graphics2D g2) {
            boolean active = isChargeAnimationActive();
            if (!active && chargePackets.isEmpty()) return;

            Graphics2D g = (Graphics2D) g2.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            final double BELT_ANGLE_RAD = Math.toRadians(30.0);
            final double BELT_LEN       = 80.0;
            final int    BELT_H         = 10;

            double dirX = Math.cos(BELT_ANGLE_RAD);
            double dirY = Math.sin(BELT_ANGLE_RAD);

            int lxPx = (int) Math.round(CHARGE_DOOR_PIVOT_X - dirX * BELT_LEN);
            int lyPx = (int) Math.round(CHARGE_DOOR_PIVOT_Y - dirY * BELT_LEN);

            // --- Nastro: visibile solo quando animazione attiva (porta completamente aperta) ---
            if (active) {
                g.translate(lxPx, lyPx);
                g.rotate(BELT_ANGLE_RAD);

                // Corpo nastro
                g.setColor(new Color(80, 80, 80));
                g.fillRoundRect(0, -BELT_H / 2, (int) BELT_LEN, BELT_H, 4, 4);
                g.setColor(new Color(40, 40, 40));
                g.setStroke(new BasicStroke(1.2f));
                g.drawRoundRect(0, -BELT_H / 2, (int) BELT_LEN, BELT_H, 4, 4);

                // Strisce animate verso destra
                Shape oldClip = g.getClip();
                g.clipRect(2, -BELT_H / 2 + 1, (int) BELT_LEN - 4, BELT_H - 2);
                g.setColor(new Color(110, 90, 55));
                int stripeW = 16;
                int offset = (int) (conveyorOffset % stripeW);
                for (int xp = -stripeW + offset; xp < (int) BELT_LEN + stripeW; xp += stripeW) {
                    int[] xs = {xp, xp + stripeW / 2, xp + stripeW / 2, xp};
                    int[] ys = {-BELT_H / 2 + 1, -BELT_H / 2 + 1, BELT_H / 2 - 1, BELT_H / 2 - 1};
                    g.fillPolygon(xs, ys, 4);
                }
                g.setClip(oldClip);

                // Rullo sinistro
                g.setColor(new Color(140, 140, 140));
                g.fillOval(-5, -BELT_H / 2 - 2, 10, BELT_H + 4);
                g.setColor(new Color(60, 60, 60));
                g.setStroke(new BasicStroke(1.2f));
                g.drawOval(-5, -BELT_H / 2 - 2, 10, BELT_H + 4);

                // Rullo destro (pivot porta)
                g.fillOval((int) BELT_LEN - 5, -BELT_H / 2 - 2, 10, BELT_H + 4);
                g.setColor(new Color(60, 60, 60));
                g.drawOval((int) BELT_LEN - 5, -BELT_H / 2 - 2, 10, BELT_H + 4);

                // Supporto verticale
                g.setColor(new Color(90, 90, 90));
                g.setStroke(new BasicStroke(2.5f));
                g.drawLine(0, BELT_H / 2, 0, BELT_H / 2 + 16);

                g.rotate(-BELT_ANGLE_RAD);
                g.translate(-lxPx, -lyPx);
            }

            // --- Pacchetti: visibili sempre (sul nastro o in caduta) ---
            for (ChargePacket p : chargePackets) {
                float a = Math.max(0f, Math.min(1f, p.alpha));
                int px = (int) Math.round(p.x);
                int py = (int) Math.round(p.y);
                int pw = p.w;
                int ph = p.h;
                Color col = p.color;

                g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), (int)(a * 230)));
                g.setStroke(new BasicStroke(1.2f));

                if (!p.falling) {
                    g.translate(px, py);
                    g.rotate(BELT_ANGLE_RAD);
                    g.fillRoundRect(-pw / 2, -ph / 2, pw, ph, 4, 4);
                    g.setColor(new Color(20, 20, 20, (int)(a * 180)));
                    g.drawRoundRect(-pw / 2, -ph / 2, pw, ph, 4, 4);
                    g.setColor(new Color(255, 255, 255, (int)(a * 35)));
                    g.drawLine(-pw / 2 + 3, -ph / 2 + 3, pw / 2 - 3, -ph / 2 + 3);
                    g.rotate(-BELT_ANGLE_RAD);
                    g.translate(-px, -py);
                } else {
                    g.fillRoundRect(px - pw / 2, py - ph / 2, pw, ph, 4, 4);
                    g.setColor(new Color(20, 20, 20, (int)(a * 180)));
                    g.drawRoundRect(px - pw / 2, py - ph / 2, pw, ph, 4, 4);
                }
            }

            g.dispose();
        }

        private void startFlowAnimation() {
            flowTimer = new Timer(40, e -> {
                flowOffset += 2f;
                if (flowOffset > 10000f) {
                    flowOffset = 0f;
                }

                rotorMasterAngle += rotorSpeed;
                if (rotorMasterAngle >= 360.0) {
                    rotorMasterAngle -= 360.0;
                }

                animateDoors();
                updateOilLeakParticles();
                updateChargePackets();
                repaint();
            });
            flowTimer.setInitialDelay(0);
            flowTimer.start();
        }

        private void updateDateTimeFields() {
            LocalDateTime now = LocalDateTime.now();

            String ora = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String data = now.format(DateTimeFormatter.ofPattern("dd/MM/yy"));

            if (lblOra != null) lblOra.setText(ora);
            if (lblData != null) lblData.setText(data);
        }

        private void startOpcRefresh() {
            if (opcScheduler != null && !opcScheduler.isShutdown()) {
                return;
            }

            shutdown = false;
            opcScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "plc3-main-opc-refresh");
                t.setDaemon(true);
                return t;
            });

            opcScheduler.scheduleWithFixedDelay(this::refreshOpcValues, 0, 1000, TimeUnit.MILLISECONDS);
        }

        private void refreshOpcValues() {
            if (shutdown) {
                return;
            }
            if (!reading.compareAndSet(false, true)) {
                return;
            }

            try {
                List<NodeId> nodes = Arrays.asList(
                        NODE_TEMP_ACTUEL,
                        NODE_TEMP_CONSIGNE,
                        NODE_VITESSE_ACTUEL,
                        NODE_VITESSE_CONSIGNE,
                        NODE_CONSIGNE,
                        NODE_STEP_DROITE,
                        NODE_COURANT,
                        NODE_SCHEDULER,
                        NODE_TEMPO_CONSIGNE,
                        NODE_TEMPO_ACTUEL,
                        NODE_CHARGEMENT_SILOS,
                        NODE_CHARGEMENT_SILOS_2,
                        NODE_CHARGEMENT_SILOS_3,
                        NODE_CYCLE_EN_COURS,
                        NODE_MELANGE_EN_COURS,
                        NODE_DOSAGGIO_DOA,
                        NODE_DOSAGGIO_SILANO,
                        NODE_DOSAGGIO_OLIO
                );

                List<DataValue> values = OpcManager.readValues(APP_NAME, APP_URI, nodes);

                String tempActuel = extractValue(values.get(0));
                String tempConsigne = extractValue(values.get(1));
                String vitesseActuel = extractValue(values.get(2));
                double parsedRotorSpeed = mapRotorSpeedFromVitesse(vitesseActuel);
                String vitesseConsigne = sanitizeDisplayedIntegerValue(extractValue(values.get(3)));

                String consigneRaw = extractValue(values.get(4));
                String consigneValue = sanitizeDisplayedDecimalValue(consigneRaw);

                String stepDroiteValue = sanitizeDisplayedIntegerValue(extractValue(values.get(5)));
                String courantValue = sanitizeDisplayedIntegerValue(extractValue(values.get(6)));
                String schedulerValue = extractValue(values.get(7));
                String tempoConsigne = sanitizeDisplayedIntegerValue(extractValue(values.get(8)));
                String tempoActuel = sanitizeDisplayedIntegerValue(extractValue(values.get(9)));

                String chargementSilosRaw = extractValue(values.get(10));
                String chargementSilos = decodeChargementSilos(chargementSilosRaw);

                String chargementSilos2Raw = extractValue(values.get(11));
                String chargementSilos2 = decodeChargementSilos2(chargementSilos2Raw);

                String chargementSilos3Raw = extractValue(values.get(12));
                String chargementSilos3 = decodeChargementSilos3(chargementSilos3Raw);

                String cycleEnCoursRaw = extractValue(values.get(13));
                String melangeEnCoursValue = sanitizeDisplayedIntegerValue(extractValue(values.get(14)));

                String dosaggioDoa = extractValue(values.get(15));
                String dosaggioSilano = extractValue(values.get(16));
                String dosaggioOlio = extractValue(values.get(17));

                DoorSnapshot doorSnapshot = readDoorStatesIfDue();
                DBBamburyReader.RecipePLCInfo recipeInfo = readLatestRecipePLCFromDb();
                final boolean recipeMonitorAlive = RecipeMonitorHealth.isAlive();

                if (shutdown) {
                    return;
                }

                final boolean finalDoaDosaggioActive = isDosaggioActive(dosaggioDoa);
                final boolean finalSilanoDosaggioActive = isDosaggioActive(dosaggioSilano);
                final boolean finalHuileDosaggioActive = isDosaggioActive(dosaggioOlio);
                final double finalParsedRotorSpeed = parsedRotorSpeed;
                final DBBamburyReader.RecipePLCInfo finalRecipeInfo = recipeInfo;
                final String finalDbMode = doorSnapshot != null ? doorSnapshot.modeValue : currentDbMode;

                SwingUtilities.invokeLater(() -> {
                    if (shutdown || !isDisplayable()) {
                        return;
                    }

                    rotorSpeed = finalParsedRotorSpeed;

                    updateSchedulerField(lblScheduler, schedulerValue);
                    updateField(lblTempActuel, tempActuel);
                    updateRecipeField(lblRecipe, recipeMonitorAlive ? finalRecipeInfo : null, finalDbMode, recipeMonitorAlive);
                    updateField(lblTempConsigne, tempConsigne);
                    updateField(lblVitesseActuel, vitesseActuel);
                    updateField(lblVitesseConsigne, vitesseConsigne);
                    updateField(lblConsigne, consigneValue);
                    updateField(lblStepDroite, stepDroiteValue);
                    updateCourantField(lblCourant, courantValue);
                    updateField(lblTempoConsigne, tempoConsigne);
                    updateField(lblTempoActuel, tempoActuel);
                    updateField(lblMelangeEnCours, melangeEnCoursValue);
                    updateField(lblChargementSilos, chargementSilos);
                    updateField(lblChargementSilos2, chargementSilos2);
                    updateField(lblChargementSilos3, chargementSilos3);
                    updateCycleField(lblCycleEnCours, cycleEnCoursRaw);
                    updateDoorTargets(doorSnapshot);
                    updateModeIndicators(finalDbMode);

                    doaSequenceActive = finalDoaDosaggioActive ||
                            (liquidSequenceInProgress.get() && NODE_DOSAGGIO_DOA.equals(activeLiquidNode.get()));
                    silanoSequenceActive = finalSilanoDosaggioActive ||
                            (liquidSequenceInProgress.get() && NODE_DOSAGGIO_SILANO.equals(activeLiquidNode.get()));
                    huileSequenceActive = finalHuileDosaggioActive ||
                            (liquidSequenceInProgress.get() && NODE_DOSAGGIO_OLIO.equals(activeLiquidNode.get()));

                    updateLiquidButtonsState(dosaggioDoa, dosaggioSilano, dosaggioOlio);
                });
            } catch (Exception ex) {
                if (!shutdown) {
                    ex.printStackTrace();
                }
            } finally {
                reading.set(false);
            }
        }

        private void animateDoors() {
            chargeDoorProgress = moveDoorProgress(chargeDoorProgress, chargeDoorState == DoorState.OPEN);
            dischargeDoorProgress = moveDoorProgress(dischargeDoorProgress, dischargeDoorState == DoorState.OPEN);
        }

        private double moveDoorProgress(double current, boolean open) {
            double target = open ? 1.0 : 0.0;
            double step = 0.10;

            if (current < target) {
                return Math.min(target, current + step);
            }
            if (current > target) {
                return Math.max(target, current - step);
            }
            return current;
        }

        private void updateDoorTargets(DoorSnapshot snapshot) {
            if (snapshot == null) {
                return;
            }
            if (snapshot.chargeDoorState != null) {
                chargeDoorState = snapshot.chargeDoorState;
            }
            if (snapshot.dischargeDoorState != null) {
                dischargeDoorState = snapshot.dischargeDoorState;
            }
        }

        private DBBamburyReader.RecipePLCInfo readLatestRecipePLCFromDb() {
            try {
                return DBBamburyReader.readLatestRecipePLC();
            } catch (Exception ex) {
                if (!shutdown) {
                    System.err.println("Erreur lecture RecipePLC: " + ex.getMessage());
                }
                return null;
            }
        }

        private void updateRecipeField(JLabel field, DBBamburyReader.RecipePLCInfo info, String rawMode, boolean monitorAlive) {
            if (field == null) {
                return;
            }

            boolean manualMode = isManualMode(rawMode);

            if (!monitorAlive) {
                field.setText(RecipeMonitorHealth.UNAVAILABLE);
                field.setOpaque(true);
                field.setBackground(new Color(214, 214, 214));
                field.setForeground(Color.BLACK);
                field.setToolTipText("RecipePLCMonitorService non actif ou sans mise a jour recente");
                field.repaint();
                return;
            }

            if (info == null) {
                field.setText(manualMode ? "Manual - Recipe" : "Recipe");
                field.setOpaque(true);
                field.setBackground(new Color(214, 214, 214));
                field.setForeground(new Color(90, 90, 90));
                field.setToolTipText(UI_VERSION + " - aucune information recette disponible");
                field.revalidate();
                field.repaint();
                return;
            }

            String displayText = info.getDisplayText();
            if (displayText == null || displayText.trim().isEmpty()) {
                displayText = "Recipe";
            }

            /*
             * VERSION: RECIPE_STATUS_FIX_V2026_06_03_02
             *
             * Correction importante:
             * Le fond du champ recette NE dépend plus du mode MANUAL/AUTO.
             * Il dépend uniquement du contrôle réel de la recette:
             *   - vert  : match 100% (matchEsatti == TOTAL_MATCHES, diff == 0, perfectMatch, ou >= 99%)
             *   - orange: presque correct
             *   - rouge : recette non conforme
             *
             * Cela corrige le cas vu en production:
             * log = match=200/200 | diff=0, mais le champ restait rouge.
             */
            Color recipeColor = getRecipeStatusColor(info);

            if (manualMode) {
                displayText = displayText + " / MANUAL";
            }

            field.setText(displayText);
            field.setOpaque(true);
            field.setForeground(Color.BLACK);
            field.setBackground(recipeColor);
            field.setToolTipText(formatRecipeStatusTooltip(info, rawMode));
            field.revalidate();
            field.repaint();

            System.out.println(UI_VERSION
                    + " | RecipeStatus"
                    + " | text=" + displayText
                    + " | mode=" + rawMode
                    + " | match=" + info.matchEsatti + "/" + DBBamburyReader.RecipePLCInfo.TOTAL_MATCHES
                    + " | diff=" + info.differenzaTotale
                    + " | percent=" + String.format(Locale.US, "%.2f", getEffectiveRecipeMatchPercent(info))
                    + " | perfect=" + info.isPerfectMatch()
                    + " | color=" + colorName(recipeColor));
        }

        private Color getRecipeStatusColor(DBBamburyReader.RecipePLCInfo info) {
            if (info == null) {
                return new Color(214, 214, 214);
            }

            int totalMatches = DBBamburyReader.RecipePLCInfo.TOTAL_MATCHES;
            int matchEsatti = info.matchEsatti;
            long diff = info.differenzaTotale;
            double percent = info.getMatchPercent();

            boolean perfectByDiff = diff == 0L;
            boolean perfectByMatch = totalMatches > 0 && matchEsatti >= totalMatches;
            boolean perfectByMethod = info.isPerfectMatch();
            boolean perfectByPercent = percent >= 99.0;

            if (perfectByDiff || perfectByMatch || perfectByMethod || perfectByPercent) {
                return new Color(39, 255, 31);
            }

            if (percent >= 95.0) {
                return new Color(255, 140, 0);
            }

            return new Color(255, 27, 27);
        }

        private double getEffectiveRecipeMatchPercent(DBBamburyReader.RecipePLCInfo info) {
            if (info == null) {
                return 0.0;
            }

            // Se DifferenzaTotale=0 o isPerfectMatch=true, il match reale e' 100%,
            // anche se MatchEsatti nell'ultima riga DB non e' ancora allineato.
            if (info.isPerfectMatch() || info.differenzaTotale == 0L) {
                return 100.0;
            }

            return info.getMatchPercent();
        }

        private String formatRecipeStatusTooltip(DBBamburyReader.RecipePLCInfo info, String rawMode) {
            if (info == null) {
                return UI_VERSION + " - aucune information recette disponible";
            }

            return String.format(
                    Locale.US,
                    "%s - Mode=%s - Match: %.2f%% (%d/%d) - Diff: %d",
                    UI_VERSION,
                    rawMode == null ? "" : rawMode,
                    info.getMatchPercent(),
                    info.matchEsatti,
                    DBBamburyReader.RecipePLCInfo.TOTAL_MATCHES,
                    info.differenzaTotale
            );
        }

        private String colorName(Color color) {
            if (new Color(39, 255, 31).equals(color)) return "GREEN";
            if (new Color(255, 140, 0).equals(color)) return "ORANGE";
            if (new Color(255, 27, 27).equals(color)) return "RED";
            return "OTHER";
        }

        private boolean isManualMode(String rawMode) {
            if (rawMode == null) {
                return false;
            }

            String mode = rawMode.trim();
            return "manu".equalsIgnoreCase(mode)
                    || "manual".equalsIgnoreCase(mode)
                    || "manuel".equalsIgnoreCase(mode);
        }

        private DoorSnapshot readDoorStatesIfDue() {
            long now = System.currentTimeMillis();
            if ((now - lastDoorStateDbPollTs) < 1500L) {
                return new DoorSnapshot(chargeDoorState, dischargeDoorState, currentDbMode);
            }

            lastDoorStateDbPollTs = now;

            try {
                return readDoorStatesFromDatabase();
            } catch (Exception ex) {
                if (!shutdown) {
                    System.err.println("Erreur lecture Datas_b2: " + ex.getMessage());
                }
                return new DoorSnapshot(chargeDoorState, dischargeDoorState, currentDbMode);
            }
        }

        private DoorSnapshot readDoorStatesFromDatabase() throws Exception {
            System.setProperty("jdk.tls.client.protocols", "TLSv1.2");
            System.setProperty("https.protocols", "TLSv1.2");

            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            DriverManager.setLoginTimeout(5);

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String[] orderCandidates = {
                        "Date_record", "DATE_RECORD", "date_record",
                        "Time_record", "TIME_RECORD", "time_record",
                        "Id", "ID", "id", "DataOra", "DATAORA", "DateHeure",
                        "Timestamp", "TIME_STAMP", "CreatedAt", "UpdatedAt"
                };

                for (String orderBy : orderCandidates) {
                    DoorSnapshot snapshot = tryReadDoorStates(conn, orderBy);
                    if (snapshot != null) {
                        return snapshot;
                    }
                }

                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT TOP 1 Porte_charge, Porte_decharge, Mode FROM Datas_b2")) {
                    if (rs.next()) {
                        return new DoorSnapshot(
                                decodeDoorState(rs.getString("Porte_charge"), chargeDoorState),
                                decodeDoorState(rs.getString("Porte_decharge"), dischargeDoorState),
                                rs.getString("Mode")
                        );
                    }
                }
            }

            return new DoorSnapshot(chargeDoorState, dischargeDoorState, currentDbMode);
        }

        private DoorSnapshot tryReadDoorStates(Connection conn, String orderByColumn) {
            String sql = "SELECT TOP 1 Porte_charge, Porte_decharge, Mode FROM Datas_b2 ORDER BY [" + orderByColumn + "] DESC";

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new DoorSnapshot(
                            decodeDoorState(rs.getString("Porte_charge"), chargeDoorState),
                            decodeDoorState(rs.getString("Porte_decharge"), dischargeDoorState),
                            rs.getString("Mode")
                    );
                }
            } catch (SQLException ignored) {
            }

            if ("Date_record".equalsIgnoreCase(orderByColumn)) {
                String combinedSql = "SELECT TOP 1 Porte_charge, Porte_decharge, Mode FROM Datas_b2 ORDER BY [Date_record] DESC, [Time_record] DESC";
                try (PreparedStatement ps = conn.prepareStatement(combinedSql);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new DoorSnapshot(
                                decodeDoorState(rs.getString("Porte_charge"), chargeDoorState),
                                decodeDoorState(rs.getString("Porte_decharge"), dischargeDoorState),
                                rs.getString("Mode")
                        );
                    }
                } catch (SQLException ignored) {
                }
            }

            return null;
        }

        private DoorState decodeDoorState(String rawValue, DoorState currentState) {
            if (rawValue == null) {
                return currentState;
            }

            String value = rawValue.trim().toLowerCase(Locale.ROOT);
            if ("ferme".equals(value)) {
                return DoorState.CLOSED;
            }
            if ("ouvert".equals(value)) {
                return DoorState.OPEN;
            }

            return currentState;
        }

        private void shutdown() {
            shutdown = true;

            try {
                if (opcScheduler != null) {
                    opcScheduler.shutdownNow();
                    opcScheduler = null;
                }
            } catch (Exception ignored) {
            }

            try {
                if (dateTimeTimer != null) {
                    dateTimeTimer.stop();
                    dateTimeTimer = null;
                }
            } catch (Exception ignored) {
            }

            try {
                if (flowTimer != null) {
                    flowTimer.stop();
                    flowTimer = null;
                }
            } catch (Exception ignored) {
            }

            reading.set(false);
            liquidSequenceInProgress.set(false);
            liquidStopRequested.set(false);
            activeLiquidNode.set(null);
            doaSequenceActive = false;
            doaPulseInProgress = false;
            silanoSequenceActive = false;
            silanoPulseInProgress = false;
            huileSequenceActive = false;
            huilePulseInProgress = false;
            chargeDoorState = DoorState.CLOSED;
            dischargeDoorState = DoorState.CLOSED;
            currentDbMode = "";
            chargeDoorProgress = 0.0;
            dischargeDoorProgress = 0.0;
            chargePackets.clear();
            conveyorOffset = 0f;
            chargePacketCooldown = 0;
        }

        private void openAlarmViewer() {
            SwingUtilities.invokeLater(() -> {
                try {
                    shutdown();

                    PLC3_AlarmViewer viewer = new PLC3_AlarmViewer();
                    viewer.setExtendedState(JFrame.MAXIMIZED_BOTH);
                    viewer.setLocationRelativeTo(null);
                    viewer.setVisible(true);

                    parentFrame.dispose();

                } catch (Throwable ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(
                            parentFrame,
                            "Erreur ouverture PLC3_AlarmViewer : " + ex.getMessage(),
                            "Erreur",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            });
        }

        private void openRecetteViewer() {
            try {
                shutdown();

                PLC3_Recette recetteFrame = new PLC3_Recette();
                recetteFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                recetteFrame.setVisible(true);
                recetteFrame.start();
                parentFrame.dispose();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(
                        parentFrame,
                        "Erreur ouverture PLC3_Recette : " + ex.getMessage(),
                        "Erreur",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }

        private void openGraphViewer() {
            try {
                shutdown();

                PLC3_Graph graph = new PLC3_Graph();
                graph.setExtendedState(JFrame.MAXIMIZED_BOTH);
                graph.setLocationRelativeTo(null);
                graph.setVisible(true);

                parentFrame.dispose();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(
                        parentFrame,
                        "Erreur ouverture PLC3_Graph : " + ex.getMessage(),
                        "Erreur",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }

        private void openMain1() {
            try {
                shutdown();

                Main0 main0 = new Main0();
                main0.setExtendedState(JFrame.MAXIMIZED_BOTH);
                main0.setVisible(true);
                parentFrame.dispose();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(
                        parentFrame,
                        "Erreur ouverture Main1 : " + ex.getMessage(),
                        "Erreur",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }

        private String decodeChargementSilos(String rawValue) {
            if (rawValue == null) return "";
            String value = rawValue.trim();

            switch (value) {
                case "0": return "----------------";
                case "1": return "CARGEMENT MANUEL";
                case "2": return "CARGEMENT SILOS";
                case "3": return "CHARG.LIQUIDE";
                case "4": return "CHARG.MANUEL+LIQUIDE";
                case "5": return "CHARG.SILOS+LIQUIDE";
                case "8": return "NETTOYAGE";
                case "9": return "MALAXAGE";
                case "10": return "DECHARGEMENT";
                default: return value;
            }
        }

        private String decodeChargementSilos2(String rawValue) {
            if (rawValue == null) return "";
            String value = rawValue.trim();

            switch (value) {
                case "0": return "----------------";
                case "1": return "CAOUTCHOUC";
                case "4": return "SILICE";
                default: return value;
            }
        }

        private String decodeChargementSilos3(String rawValue) {
            if (rawValue == null) return "";
            String value = rawValue.trim();

            switch (value) {
                case "0": return "----------------";
                case "1": return "DOA";
                case "2": return "SILANO";
                case "3": return "HUILE PLASTIFIANTE";
                default: return value;
            }
        }

        private String decodeCycle(String rawValue) {
            if (rawValue == null) return "";
            String value = rawValue.trim();

            switch (value) {
                case "0": return "----------------";
                case "1": return "ATTENTE OPERATOR";
                case "2": return "CYCLE EN COURS";
                default: return value;
            }
        }

        private String decodeScheduler(String rawValue) {
            if (rawValue == null) return "";
            String value = rawValue.trim();

            switch (value) {
                case "0": return "SCHEDULER INACTIF";
                case "1": return "ATTENTE DE LA RECETTE";
                case "4": return "SCHEDULER VIDE";
                default: return value;
            }
        }

        private String sanitizeDisplayedDecimalValue(String rawValue) {
            if (rawValue == null || rawValue.trim().isEmpty()) return "0.0";

            try {
                double v = Double.parseDouble(rawValue.trim().replace(",", "."));
                if (v > 50000) return "0.0";
                return String.format(Locale.US, "%.1f", v);
            } catch (Exception ex) {
                return rawValue.trim().replace(",", ".");
            }
        }

        private String sanitizeDisplayedIntegerValue(String rawValue) {
            if (rawValue == null || rawValue.trim().isEmpty()) return "";

            try {
                double v = Double.parseDouble(rawValue.trim().replace(",", "."));

                if (v > 50000) return "0";

                if (Math.floor(v) == v) {
                    return String.valueOf((long) v);
                }

                return String.valueOf(v);
            } catch (Exception ex) {
                return rawValue;
            }
        }

        private String formatDecimalForDisplay(String rawValue, int decimals) {
            if (rawValue == null || rawValue.trim().isEmpty()) {
                return decimals <= 0 ? "0" : "0." + "0".repeat(decimals);
            }

            try {
                double v = Double.parseDouble(rawValue.trim().replace(",", "."));
                return String.format(Locale.US, "%." + decimals + "f", v);
            } catch (Exception ex) {
                return rawValue.trim().replace(",", ".");
            }
        }

        private void updateSchedulerField(JLabel field, String rawValue) {
            String value = rawValue == null ? "" : rawValue.trim();
            String text = decodeScheduler(value);

            Color bg = new Color(233, 233, 233);
            Color fg = new Color(90, 90, 90);

            if ("1".equals(value)) {
                bg = Color.YELLOW;
                fg = Color.BLACK;
            } else if ("4".equals(value)) {
                bg = new Color(255, 27, 27);
                fg = Color.BLACK;
            }

            String oldText = field.getText();
            Color oldBg = field.getBackground();

            field.setText(text);
            field.setBackground(bg);
            field.setForeground(fg);

            if (!text.equals(oldText) || !bg.equals(oldBg)) {
                field.setBackground(Color.YELLOW);

                final Color finalBg = bg;
                final Color finalFg = fg;

                Timer timer = new Timer(800, e -> {
                    field.setBackground(finalBg);
                    field.setForeground(finalFg);
                });
                timer.setRepeats(false);
                timer.start();
            }
        }

        private void updateCycleField(JLabel field, String rawValue) {
            String value = rawValue == null ? "" : rawValue.trim();
            String text = decodeCycle(value);

            Color bg = new Color(214, 214, 214);
            Color fg = Color.BLACK;

            if ("1".equals(value)) {
                bg = new Color(255, 27, 27);
            }

            String oldText = field.getText();
            Color oldBg = field.getBackground();

            field.setText(text);
            field.setBackground(bg);
            field.setForeground(fg);

            if (!text.equals(oldText) || !bg.equals(oldBg)) {
                final Color finalBg = bg;
                final Color finalFg = fg;

                field.setBackground(Color.YELLOW);

                Timer timer = new Timer(800, e -> {
                    field.setBackground(finalBg);
                    field.setForeground(finalFg);
                });
                timer.setRepeats(false);
                timer.start();
            }
        }

        private String extractValue(DataValue dv) {
            if (dv == null || dv.getValue() == null || dv.getValue().getValue() == null) {
                return "";
            }

            Object raw = dv.getValue().getValue();

            if (raw instanceof Boolean) {
                return String.valueOf(raw);
            }

            if (raw instanceof Float || raw instanceof Double) {
                return String.format(Locale.US, "%.4f", ((Number) raw).doubleValue());
            }

            if (raw instanceof Number) {
                double d = ((Number) raw).doubleValue();
                if (Math.floor(d) == d) {
                    return String.valueOf((long) d);
                }
                return String.valueOf(d).replace(",", ".");
            }

            return String.valueOf(raw);
        }

        private void updateCourantField(JLabel field, String value) {
            if (field == null) {
                return;
            }
            if (value == null || value.trim().isEmpty()) {
                value = "0";
            }

            String oldValue = field.getText();
            field.setText(value);

            int courant = 0;
            try {
                courant = Integer.parseInt(value.trim());
            } catch (Exception ignored) {
            }

            Color targetBackground = courant >= 1000
                    ? new Color(255, 27, 27)
                    : new Color(39, 255, 31);
            field.setForeground(Color.BLACK);

            if (!value.equals(oldValue)) {
                field.setBackground(Color.YELLOW);

                Timer timer = new Timer(800, e -> field.setBackground(targetBackground));
                timer.setRepeats(false);
                timer.start();
            } else {
                field.setBackground(targetBackground);
            }
        }

        private void updateField(JLabel field, String value) {
            if (value == null) value = "";

            String oldValue = field.getText();
            field.setText(value);

            if (!value.equals(oldValue)) {
                Color oldBackground = field.getBackground();
                field.setBackground(Color.YELLOW);

                Timer timer = new Timer(800, e -> field.setBackground(oldBackground));
                timer.setRepeats(false);
                timer.start();
            }
        }

        private void configureUShortWritableField(
                JLabel field,
                String dialogTitle,
                NodeId nodeId,
                String fieldName) {

            field.setToolTipText("Cliquer pour modifier");
            field.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            field.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        showUShortEditorDialog(field, dialogTitle, nodeId, fieldName);
                    }
                }
            });
        }

        private void configureDecimalWritableField(
                JLabel field,
                String dialogTitle,
                NodeId nodeId,
                int decimals,
                String fieldName) {

            field.setToolTipText("Cliquer pour modifier");
            field.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            field.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        showDecimalEditorDialog(field, dialogTitle, nodeId, decimals, fieldName);
                    }
                }
            });
        }

        private void configureSignedIntegerWritableField(
                JLabel field,
                String dialogTitle,
                NodeId nodeId,
                String fieldName) {

            field.setToolTipText("Cliquer pour modifier");
            field.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            field.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        showSignedIntegerEditorDialog(field, dialogTitle, nodeId, fieldName);
                    }
                }
            });
        }

        private void showUShortEditorDialog(
                JLabel targetField,
                String dialogTitle,
                NodeId nodeId,
                String fieldName) {

            JDialog dialog = new JDialog(parentFrame, dialogTitle, true);
            dialog.setLayout(new BorderLayout(14, 14));
            dialog.getContentPane().setBackground(new Color(236, 236, 236));

            JPanel content = new JPanel(new BorderLayout(0, 14));
            content.setOpaque(false);
            content.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

            JTextField valueField = new JTextField(targetField.getText(), 12);
            valueField.setFont(EDITOR_FIELD_FONT);
            valueField.setHorizontalAlignment(SwingConstants.CENTER);
            valueField.setPreferredSize(new Dimension(EDITOR_FIELD_W, EDITOR_FIELD_H));
            ((AbstractDocument) valueField.getDocument()).setDocumentFilter(new NumericDocumentFilter());

            JButton writeButton = new JButton("Écrire");
            writeButton.setFont(EDITOR_WRITE_FONT);
            writeButton.setPreferredSize(new Dimension(EDITOR_WRITE_W, EDITOR_WRITE_H));
            writeButton.setFocusPainted(false);

            JLabel infoLabel = new JLabel(" ", SwingConstants.CENTER);
            infoLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));

            final boolean[] firstKeyPress = {true};

            Runnable doWrite = () -> writeUShortFieldValue(
                    targetField, nodeId, valueField.getText(), dialog, infoLabel, writeButton, fieldName
            );

            writeButton.addActionListener(e -> doWrite.run());
            valueField.addActionListener(e -> doWrite.run());

            JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
            top.setOpaque(false);
            top.add(valueField);
            top.add(writeButton);

            content.add(top, BorderLayout.NORTH);
            content.add(createNumericKeyboard(valueField, firstKeyPress), BorderLayout.CENTER);
            content.add(infoLabel, BorderLayout.SOUTH);

            dialog.setContentPane(content);
            dialog.pack();
            dialog.setLocationRelativeTo(parentFrame);
            SwingUtilities.invokeLater(valueField::requestFocusInWindow);
            dialog.setVisible(true);
        }

        private void showDecimalEditorDialog(
                JLabel targetField,
                String dialogTitle,
                NodeId nodeId,
                int decimals,
                String fieldName) {

            JDialog dialog = new JDialog(parentFrame, dialogTitle, true);
            dialog.setLayout(new BorderLayout(14, 14));
            dialog.getContentPane().setBackground(new Color(236, 236, 236));

            JPanel content = new JPanel(new BorderLayout(0, 14));
            content.setOpaque(false);
            content.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

            JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
            topPanel.setOpaque(false);

            JTextField valueField = new JTextField(
                    targetField.getText() == null ? "" : targetField.getText().replace(",", "."),
                    14
            );
            valueField.setFont(EDITOR_FIELD_FONT);
            valueField.setHorizontalAlignment(SwingConstants.RIGHT);
            valueField.setPreferredSize(new Dimension(EDITOR_FIELD_W, EDITOR_FIELD_H));
            valueField.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(170, 170, 170), 1),
                    BorderFactory.createEmptyBorder(2, 8, 2, 8)
            ));
            ((AbstractDocument) valueField.getDocument()).setDocumentFilter(new DecimalDocumentFilter());

            JButton writeButton = new JButton("Écrire");
            writeButton.setFont(EDITOR_WRITE_FONT);
            writeButton.setPreferredSize(new Dimension(EDITOR_WRITE_W, EDITOR_WRITE_H));
            writeButton.setFocusPainted(false);

            JLabel infoLabel = new JLabel(
                    "Saisissez une valeur décimale avec un point",
                    SwingConstants.CENTER
            );
            infoLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));

            final boolean[] firstKeyPress = {true};

            Runnable doWrite = () -> writeDecimalFieldValue(
                    targetField,
                    nodeId,
                    valueField.getText(),
                    dialog,
                    infoLabel,
                    writeButton,
                    fieldName,
                    decimals
            );

            writeButton.addActionListener(e -> doWrite.run());
            valueField.addActionListener(e -> doWrite.run());

            topPanel.add(valueField);
            topPanel.add(writeButton);

            content.add(topPanel, BorderLayout.NORTH);
            content.add(createDecimalKeyboard(valueField, firstKeyPress), BorderLayout.CENTER);
            content.add(infoLabel, BorderLayout.SOUTH);

            dialog.setContentPane(content);
            dialog.pack();
            dialog.setLocationRelativeTo(parentFrame);
            SwingUtilities.invokeLater(valueField::requestFocusInWindow);
            dialog.setVisible(true);
        }

        private void showSignedIntegerEditorDialog(
                JLabel targetField,
                String dialogTitle,
                NodeId nodeId,
                String fieldName) {

            JDialog dialog = new JDialog(parentFrame, dialogTitle, true);
            dialog.setLayout(new BorderLayout(14, 14));
            dialog.getContentPane().setBackground(new Color(236, 236, 236));

            JPanel content = new JPanel(new BorderLayout(0, 14));
            content.setOpaque(false);
            content.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

            JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
            topPanel.setOpaque(false);

            JTextField valueField = new JTextField(targetField.getText() == null ? "" : targetField.getText(), 14);
            valueField.setFont(EDITOR_FIELD_FONT);
            valueField.setHorizontalAlignment(SwingConstants.RIGHT);
            valueField.setPreferredSize(new Dimension(EDITOR_FIELD_W, EDITOR_FIELD_H));
            valueField.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(170, 170, 170), 1),
                    BorderFactory.createEmptyBorder(2, 8, 2, 8)
            ));
            ((AbstractDocument) valueField.getDocument()).setDocumentFilter(new SignedIntegerDocumentFilter());

            JButton writeButton = new JButton("Écrire");
            writeButton.setFont(EDITOR_WRITE_FONT);
            writeButton.setPreferredSize(new Dimension(EDITOR_WRITE_W, EDITOR_WRITE_H));
            writeButton.setFocusPainted(false);

            JLabel infoLabel = new JLabel("Saisissez une valeur entière", SwingConstants.CENTER);
            infoLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));

            final boolean[] firstKeyPress = {true};

            Runnable doWrite = () -> writeIntegerFieldValue(
                    targetField,
                    nodeId,
                    valueField.getText(),
                    dialog,
                    infoLabel,
                    writeButton,
                    fieldName
            );

            writeButton.addActionListener(e -> doWrite.run());
            valueField.addActionListener(e -> doWrite.run());

            topPanel.add(valueField);
            topPanel.add(writeButton);

            content.add(topPanel, BorderLayout.NORTH);
            content.add(createSignedIntegerKeyboard(valueField, firstKeyPress), BorderLayout.CENTER);
            content.add(infoLabel, BorderLayout.SOUTH);

            dialog.setContentPane(content);
            dialog.pack();
            dialog.setLocationRelativeTo(parentFrame);
            SwingUtilities.invokeLater(valueField::requestFocusInWindow);
            dialog.setVisible(true);
        }

        private JPanel createNumericKeyboard(JTextField targetField, boolean[] firstKeyPress) {
            JPanel keyboard = new JPanel(new BorderLayout(8, 8));
            keyboard.setOpaque(false);

            JPanel keysPanel = new JPanel(new GridLayout(4, 1, 6, 6));
            keysPanel.setOpaque(false);

            keysPanel.add(createKeyboardRow(targetField, new String[]{"1", "2", "3"}, firstKeyPress));
            keysPanel.add(createKeyboardRow(targetField, new String[]{"4", "5", "6"}, firstKeyPress));
            keysPanel.add(createKeyboardRow(targetField, new String[]{"7", "8", "9"}, firstKeyPress));
            keysPanel.add(createKeyboardRow(targetField, new String[]{"0"}, firstKeyPress));

            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            bottomPanel.setOpaque(false);

            JButton btnBack = createKeyboardButton("←");
            btnBack.setPreferredSize(new Dimension(KEYBOARD_BACK_W, KEYBOARD_BACK_H));
            btnBack.addActionListener(e -> backspaceText(targetField, firstKeyPress));

            JButton btnClear = createKeyboardButton("CLEAR");
            btnClear.setPreferredSize(new Dimension(KEYBOARD_CLEAR_W, KEYBOARD_CLEAR_H));
            btnClear.addActionListener(e -> {
                targetField.setText("");
                firstKeyPress[0] = false;
            });

            bottomPanel.add(btnBack);
            bottomPanel.add(btnClear);

            keyboard.add(keysPanel, BorderLayout.CENTER);
            keyboard.add(bottomPanel, BorderLayout.SOUTH);

            return keyboard;
        }

        private JPanel createSignedIntegerKeyboard(JTextField targetField, boolean[] firstKeyPress) {
            JPanel keyboard = new JPanel(new BorderLayout(8, 8));
            keyboard.setOpaque(false);

            JPanel keysPanel = new JPanel(new GridLayout(4, 1, 6, 6));
            keysPanel.setOpaque(false);

            keysPanel.add(createKeyboardRow(targetField, new String[]{"1", "2", "3"}, firstKeyPress));
            keysPanel.add(createKeyboardRow(targetField, new String[]{"4", "5", "6"}, firstKeyPress));
            keysPanel.add(createKeyboardRow(targetField, new String[]{"7", "8", "9"}, firstKeyPress));
            keysPanel.add(createKeyboardRow(targetField, new String[]{"-", "0"}, firstKeyPress));

            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            bottomPanel.setOpaque(false);

            JButton btnBack = createKeyboardButton("←");
            btnBack.setPreferredSize(new Dimension(KEYBOARD_BACK_W, KEYBOARD_BACK_H));
            btnBack.addActionListener(e -> backspaceText(targetField, firstKeyPress));

            JButton btnClear = createKeyboardButton("CLEAR");
            btnClear.setPreferredSize(new Dimension(KEYBOARD_CLEAR_W, KEYBOARD_CLEAR_H));
            btnClear.addActionListener(e -> {
                targetField.setText("");
                firstKeyPress[0] = false;
            });

            bottomPanel.add(btnBack);
            bottomPanel.add(btnClear);

            keyboard.add(keysPanel, BorderLayout.CENTER);
            keyboard.add(bottomPanel, BorderLayout.SOUTH);

            return keyboard;
        }

        private JPanel createDecimalKeyboard(JTextField targetField, boolean[] firstKeyPress) {
            JPanel keyboard = new JPanel(new BorderLayout(8, 8));
            keyboard.setOpaque(false);

            JPanel keysPanel = new JPanel(new GridLayout(4, 1, 6, 6));
            keysPanel.setOpaque(false);

            keysPanel.add(createDecimalKeyboardRow(targetField, new String[]{"1", "2", "3"}, firstKeyPress));
            keysPanel.add(createDecimalKeyboardRow(targetField, new String[]{"4", "5", "6"}, firstKeyPress));
            keysPanel.add(createDecimalKeyboardRow(targetField, new String[]{"7", "8", "9"}, firstKeyPress));
            keysPanel.add(createDecimalKeyboardRow(targetField, new String[]{".", "0"}, firstKeyPress));

            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            bottomPanel.setOpaque(false);

            JButton btnBack = createKeyboardButton("←");
            btnBack.setPreferredSize(new Dimension(KEYBOARD_BACK_W, KEYBOARD_BACK_H));
            btnBack.addActionListener(e -> backspaceText(targetField, firstKeyPress));

            JButton btnClear = createKeyboardButton("CLEAR");
            btnClear.setPreferredSize(new Dimension(KEYBOARD_CLEAR_W, KEYBOARD_CLEAR_H));
            btnClear.addActionListener(e -> {
                targetField.setText("");
                firstKeyPress[0] = false;
            });

            bottomPanel.add(btnBack);
            bottomPanel.add(btnClear);

            keyboard.add(keysPanel, BorderLayout.CENTER);
            keyboard.add(bottomPanel, BorderLayout.SOUTH);

            return keyboard;
        }

        private JPanel createKeyboardRow(JTextField targetField, String[] keys, boolean[] firstKeyPress) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
            row.setOpaque(false);

            for (String key : keys) {
                JButton button = createKeyboardButton(key);
                button.addActionListener(e -> appendText(targetField, key, firstKeyPress));
                row.add(button);
            }

            return row;
        }

        private JPanel createDecimalKeyboardRow(JTextField targetField, String[] keys, boolean[] firstKeyPress) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
            row.setOpaque(false);

            for (String key : keys) {
                JButton button = createKeyboardButton(key);
                button.addActionListener(e -> appendDecimalText(targetField, key, firstKeyPress));
                row.add(button);
            }

            return row;
        }

        private JButton createKeyboardButton(String text) {
            JButton button = new JButton(text);
            button.setFont(KEYBOARD_BUTTON_FONT);
            button.setPreferredSize(new Dimension(KEYBOARD_KEY_W, KEYBOARD_KEY_H));
            button.setFocusPainted(false);
            button.setBackground(new Color(245, 245, 245));
            button.setBorder(new BevelRectBorder(new Color(120, 120, 120), 1));
            return button;
        }

        private void appendText(JTextField targetField, String value, boolean[] firstKeyPress) {
            String current = targetField.getText();
            if (current == null) current = "";

            if (firstKeyPress[0]) {
                current = "";
                firstKeyPress[0] = false;
            }

            if ("-".equals(value)) {
                if (current.startsWith("-")) {
                    current = current.substring(1);
                } else {
                    current = "-" + current;
                }
                targetField.setText(current);
                targetField.requestFocusInWindow();
                return;
            }

            targetField.setText(current + value);
            targetField.requestFocusInWindow();
        }

        private void appendDecimalText(JTextField targetField, String value, boolean[] firstKeyPress) {
            String current = targetField.getText();
            if (current == null) current = "";

            if (firstKeyPress[0]) {
                current = "";
                firstKeyPress[0] = false;
            }

            current = current.replace(",", ".");

            if (".".equals(value)) {
                if (current.isEmpty()) {
                    targetField.setText("0.");
                } else if (!current.contains(".")) {
                    targetField.setText(current + ".");
                }
                targetField.requestFocusInWindow();
                return;
            }

            targetField.setText(current + value);
            targetField.requestFocusInWindow();
        }

        private void backspaceText(JTextField targetField, boolean[] firstKeyPress) {
            String current = targetField.getText();
            if (current != null && !current.isEmpty()) {
                targetField.setText(current.substring(0, current.length() - 1));
            }
            firstKeyPress[0] = false;
            targetField.requestFocusInWindow();
        }

        private void writeUShortFieldValue(
                JLabel targetField,
                NodeId nodeId,
                String newValue,
                JDialog dialog,
                JLabel infoLabel,
                JButton writeButton,
                String fieldName) {

            String value = newValue == null ? "" : newValue.trim();

            if (value.isEmpty()) {
                infoLabel.setForeground(Color.RED);
                infoLabel.setText("Saisissez une valeur numérique");
                return;
            }

            writeButton.setEnabled(false);
            infoLabel.setForeground(Color.DARK_GRAY);
            infoLabel.setText("Écriture en cours...");

            new Thread(() -> {
                try {
                    int parsed = Integer.parseInt(value);

                    if (parsed < 0 || parsed > 65535) {
                        throw new IllegalArgumentException("Valeur hors plage pour UShort : " + parsed);
                    }

                    UShort valueToWrite = ushort(parsed);

                    StatusCode status = OpcManager.writeValue(
                            APP_NAME,
                            APP_URI,
                            nodeId,
                            DataValue.valueOnly(new Variant(valueToWrite))
                    );

                    final String insertedForLog = value;
                    final String writtenForLog = String.valueOf(parsed);

                    if (shutdown) {
                        return;
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (shutdown || !isDisplayable()) {
                            return;
                        }
                        if (status != null && status.isGood()) {
                            PLC3_Main.logWrite(
                                    nodeId,
                                    insertedForLog,
                                    writtenForLog,
                                    "SUCCESS",
                                    fieldName
                            );

                            updateField(targetField, String.valueOf(parsed));
                            infoLabel.setForeground(new Color(0, 140, 0));
                            infoLabel.setText("Écriture terminée");

                            Timer timer = new Timer(700, e -> dialog.dispose());
                            timer.setRepeats(false);
                            timer.start();
                        } else {
                            PLC3_Main.logWrite(
                                    nodeId,
                                    insertedForLog,
                                    writtenForLog,
                                    "REJECTED",
                                    fieldName + " | " + status
                            );

                            infoLabel.setForeground(Color.RED);
                            infoLabel.setText("Écriture refusée : " + status);
                            writeButton.setEnabled(true);
                        }
                    });

                } catch (Exception ex) {
                    PLC3_Main.logWrite(
                            nodeId,
                            value,
                            "",
                            "ERROR",
                            fieldName + " | " + ex.getMessage()
                    );

                    if (shutdown) {
                        return;
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (shutdown || !isDisplayable()) {
                            return;
                        }
                        infoLabel.setForeground(Color.RED);
                        infoLabel.setText("Erreur : " + ex.getMessage());
                        writeButton.setEnabled(true);
                    });
                }
            }, "opc-write-ushort-thread").start();
        }

        private void writeDecimalFieldValue(
                JLabel targetField,
                NodeId nodeId,
                String newValue,
                JDialog dialog,
                JLabel infoLabel,
                JButton writeButton,
                String fieldName,
                int decimals
        ) {
            String value = newValue == null ? "" : newValue.trim().replace(",", ".");

            if (value.isEmpty() || ".".equals(value)) {
                infoLabel.setForeground(Color.RED);
                infoLabel.setText("Saisissez une valeur décimale valide");
                return;
            }

            if (value.endsWith(".")) {
                value = value + "0";
            }

            writeButton.setEnabled(false);
            infoLabel.setForeground(Color.DARK_GRAY);
            infoLabel.setText("Écriture en cours...");

            final String normalizedValue = value;

            new Thread(() -> {
                try {
                    float parsed = Float.parseFloat(normalizedValue);

                    StatusCode status = OpcManager.writeValue(
                            APP_NAME,
                            APP_URI,
                            nodeId,
                            DataValue.valueOnly(new Variant(parsed))
                    );

                    if (shutdown) {
                        return;
                    }

                    final String displayValue = formatDecimalForDisplay(normalizedValue, decimals);

                    SwingUtilities.invokeLater(() -> {
                        if (shutdown || !isDisplayable()) {
                            return;
                        }
                        if (status != null && status.isGood()) {
                            PLC3_Main.logWrite(
                                    nodeId,
                                    normalizedValue,
                                    String.valueOf(parsed),
                                    "SUCCESS",
                                    fieldName
                            );

                            updateField(targetField, displayValue);
                            infoLabel.setForeground(new Color(0, 140, 0));
                            infoLabel.setText("Écriture terminée");

                            if (NODE_CONSIGNE.equals(nodeId)) {
                                scheduleConsigneRefreshAfterWrite(300);
                            }

                            Timer timer = new Timer(700, e -> dialog.dispose());
                            timer.setRepeats(false);
                            timer.start();
                        } else {
                            PLC3_Main.logWrite(
                                    nodeId,
                                    normalizedValue,
                                    String.valueOf(parsed),
                                    "REJECTED",
                                    fieldName + " | " + status
                            );

                            infoLabel.setForeground(Color.RED);
                            infoLabel.setText("Écriture refusée : " + status);
                            writeButton.setEnabled(true);
                        }
                    });
                } catch (Exception ex) {
                    PLC3_Main.logWrite(
                            nodeId,
                            normalizedValue,
                            "",
                            "ERROR",
                            fieldName + " | " + ex.getMessage()
                    );

                    if (shutdown) {
                        return;
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (shutdown || !isDisplayable()) {
                            return;
                        }
                        infoLabel.setForeground(Color.RED);
                        infoLabel.setText("Erreur : " + ex.getMessage());
                        writeButton.setEnabled(true);
                    });
                }
            }, "opc-write-decimal-thread").start();
        }

        private void scheduleConsigneRefreshAfterWrite(int delayMs) {
            Timer timer = new Timer(delayMs, e -> {
                new Thread(() -> {
                    try {
                        readConsigneFromOpcAndRefreshLabel();
                    } catch (Exception ex) {
                        if (!shutdown) {
                            ex.printStackTrace();
                        }
                    }
                }, "plc3-consigne-delayed-refresh-thread").start();
            });
            timer.setRepeats(false);
            timer.start();
        }

        private void writeIntegerFieldValue(
                JLabel targetField,
                NodeId nodeId,
                String newValue,
                JDialog dialog,
                JLabel infoLabel,
                JButton writeButton,
                String fieldName
        ) {
            String value = newValue == null ? "" : newValue.trim();

            if (value.isEmpty() || "-".equals(value)) {
                infoLabel.setForeground(Color.RED);
                infoLabel.setText("Saisissez une valeur numérique");
                return;
            }

            writeButton.setEnabled(false);
            infoLabel.setForeground(Color.DARK_GRAY);
            infoLabel.setText("Écriture en cours...");

            new Thread(() -> {
                try {
                    int parsed = Integer.parseInt(value);

                    if (parsed < Short.MIN_VALUE || parsed > Short.MAX_VALUE) {
                        throw new IllegalArgumentException("Valeur hors plage pour Short : " + parsed);
                    }

                    short valueToWrite = (short) parsed;

                    StatusCode status = OpcManager.writeValue(
                            APP_NAME,
                            APP_URI,
                            nodeId,
                            DataValue.valueOnly(new Variant(valueToWrite))
                    );

                    if (shutdown) {
                        return;
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (shutdown || !isDisplayable()) {
                            return;
                        }
                        if (status != null && status.isGood()) {
                            updateField(targetField, String.valueOf(parsed));
                            infoLabel.setForeground(new Color(0, 140, 0));
                            infoLabel.setText("Écriture terminée");

                            Timer timer = new Timer(700, e -> dialog.dispose());
                            timer.setRepeats(false);
                            timer.start();
                        } else {
                            infoLabel.setForeground(Color.RED);
                            infoLabel.setText("Écriture refusée : " + status);
                            writeButton.setEnabled(true);
                        }
                    });
                } catch (Exception ex) {
                    if (shutdown) {
                        return;
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (shutdown || !isDisplayable()) {
                            return;
                        }
                        infoLabel.setForeground(Color.RED);
                        infoLabel.setText("Erreur : " + ex.getMessage());
                        writeButton.setEnabled(true);
                    });
                }
            }, "opc-write-thread").start();
        }

        private DosageButton addDoaButton(String text, int x, int y, int w, int h) {
            DosageButton button = new DosageButton(text);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.addActionListener(e -> handleDoaButtonClick());
            registerBaseBounds(button, x, y, w, h);
            return button;
        }

        private DosageButton addSilanoButton(String text, int x, int y, int w, int h) {
            DosageButton button = new DosageButton(text);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.addActionListener(e -> handleSilanoButtonClick());
            registerBaseBounds(button, x, y, w, h);
            return button;
        }

        private DosageButton addHuileButton(String text, int x, int y, int w, int h) {
            DosageButton button = new DosageButton(text);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.addActionListener(e -> handleHuileButtonClick());
            registerBaseBounds(button, x, y, w, h);
            return button;
        }

        private void handleSilanoButtonClick() {
            NodeId silanoNode = NODE_DOSAGGIO_SILANO;
            NodeId currentActive = activeLiquidNode.get();

            if (liquidSequenceInProgress.get()) {
                if (currentActive != null && currentActive.equals(silanoNode)) {
                    liquidStopRequested.set(true);
                    PLC3_Main.logButtonPress("SILANO", silanoNode, "STOP_REQUEST", "Bouton pressé pendant séquence active");
                }
                return;
            }

            PLC3_Main.logButtonPress("SILANO", silanoNode, "PRESSED", "Demande utilisateur");

            if (!liquidSequenceInProgress.compareAndSet(false, true)) {
                return;
            }

            liquidStopRequested.set(false);
            activeLiquidNode.set(silanoNode);
            silanoPulseInProgress = true;
            silanoSequenceActive = true;

            SwingUtilities.invokeLater(() -> {
                updateLiquidButtonsInterlock();
                repaint();
            });

            new Thread(() -> {
                boolean mustWriteFalse = false;

                try {
                    float presetValue = readConsigneFromOpcAndRefreshLabel();

                    if (presetValue <= 0f) {
                        throw new IllegalArgumentException("La CONSIGNE SILANO doit être > 0");
                    }

                    StatusCode stPreset = OpcManager.writeValue(
                            APP_NAME,
                            APP_URI,
                            NODE_PRESET_LIQUIDI_SILANO,
                            DataValue.valueOnly(new Variant(presetValue))
                    );

                    if (!isGood(stPreset)) {
                        throw new RuntimeException("Écriture du preset SILANO refusée : " + stPreset);
                    }

                    Thread.sleep(150);

                    StatusCode stOn = writeBooleanNode(NODE_DOSAGGIO_SILANO, true);
                    if (!isGood(stOn)) {
                        throw new RuntimeException("Écriture TRUE SILANO refusée : " + stOn);
                    }

                    mustWriteFalse = true;

                    waitUntilLiquidSequenceMustStop(NODE_LIQUID_RESET_MONITOR);

                    if (shutdown) {
                        return;
                    }

                    StatusCode stOff = writeBooleanNode(NODE_DOSAGGIO_SILANO, false);
                    if (!isGood(stOff)) {
                        throw new RuntimeException("Écriture FALSE SILANO refusée : " + stOff);
                    }

                    mustWriteFalse = false;

                    if (shutdown) {
                        return;
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (shutdown || !isDisplayable()) {
                            return;
                        }

                        silanoSequenceActive = false;
                        liquidSequenceInProgress.set(false);
                        liquidStopRequested.set(false);
                        activeLiquidNode.set(null);
                        silanoPulseInProgress = false;

                        updateLiquidButtonsInterlock();
                        refreshOpcValues();
                        repaint();
                    });

                } catch (Exception ex) {
                    PLC3_Main.logButtonPress("SILANO", silanoNode, "ERROR", ex.getMessage());
                    ex.printStackTrace();

                    if (shutdown) {
                        return;
                    }

                    if (mustWriteFalse) {
                        try {
                            writeBooleanNode(NODE_DOSAGGIO_SILANO, false);
                        } catch (Exception ignored) {
                        }
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (shutdown || !isDisplayable()) {
                            return;
                        }

                        silanoSequenceActive = false;
                        liquidSequenceInProgress.set(false);
                        liquidStopRequested.set(false);
                        activeLiquidNode.set(null);
                        silanoPulseInProgress = false;

                        updateLiquidButtonsInterlock();
                        refreshOpcValues();
                        repaint();

                        JOptionPane.showMessageDialog(
                                parentFrame,
                                "Erreur dosage SILANO : " + ex.getMessage(),
                                "Erreur",
                                JOptionPane.ERROR_MESSAGE
                        );
                    });
                }
            }, "plc3-silano-sequence-thread").start();
        }

        private float readConsigneFromOpcAndRefreshLabel() throws Exception {
            List<DataValue> values = OpcManager.readValues(
                    APP_NAME,
                    APP_URI,
                    Arrays.asList(NODE_CONSIGNE)
            );

            if (values == null || values.isEmpty()) {
                throw new IllegalStateException("Lecture R2018 indisponible");
            }

            String raw = extractValue(values.get(0));
            if (raw == null) {
                raw = "";
            }

            String trimmed = raw.trim().replace(",", ".");
            String displayValue = sanitizeDisplayedDecimalValue(trimmed);

            float parsedValue;
            try {
                if (trimmed.isEmpty()) {
                    parsedValue = 0f;
                } else {
                    parsedValue = Float.parseFloat(trimmed);
                }
            } catch (Exception ex) {
                String fallback = displayValue == null ? "" : displayValue.trim().replace(",", ".");
                if (fallback.isEmpty()) {
                    parsedValue = 0f;
                } else {
                    parsedValue = Float.parseFloat(fallback);
                }
            }

            final String finalDisplayValue = displayValue;

            SwingUtilities.invokeLater(() -> {
                if (shutdown || !isDisplayable() || lblConsigne == null) {
                    return;
                }
                updateField(lblConsigne, finalDisplayValue);
            });

            return parsedValue;
        }

        private void handleDoaButtonClick() {
            NodeId doaNode = NODE_DOSAGGIO_DOA;
            NodeId currentActive = activeLiquidNode.get();

            if (liquidSequenceInProgress.get()) {
                if (currentActive != null && currentActive.equals(doaNode)) {
                    liquidStopRequested.set(true);
                    PLC3_Main.logButtonPress("DOA", doaNode, "STOP_REQUEST", "Bouton pressé pendant séquence active");
                }
                return;
            }

            PLC3_Main.logButtonPress("DOA", doaNode, "PRESSED", "Demande utilisateur");

            if (!liquidSequenceInProgress.compareAndSet(false, true)) {
                return;
            }

            liquidStopRequested.set(false);
            activeLiquidNode.set(doaNode);
            doaPulseInProgress = true;
            doaSequenceActive = true;

            SwingUtilities.invokeLater(() -> {
                updateLiquidButtonsInterlock();
                repaint();
            });

            new Thread(() -> {
                boolean mustWriteFalse = false;

                try {
                    float presetValue = readConsigneFromOpcAndRefreshLabel();

                    if (presetValue <= 0f) {
                        throw new IllegalArgumentException("La CONSIGNE DOA doit être > 0");
                    }

                    StatusCode stPreset = OpcManager.writeValue(
                            APP_NAME,
                            APP_URI,
                            NODE_PRESET_LIQUIDI_SILANO,
                            DataValue.valueOnly(new Variant(presetValue))
                    );

                    if (!isGood(stPreset)) {
                        throw new RuntimeException("Écriture du preset DOA refusée : " + stPreset);
                    }

                    Thread.sleep(150);

                    StatusCode stOn = writeBooleanNode(NODE_DOSAGGIO_DOA, true);
                    if (!isGood(stOn)) {
                        throw new RuntimeException("Écriture TRUE DOA refusée : " + stOn);
                    }

                    mustWriteFalse = true;

                    waitUntilLiquidSequenceMustStop(NODE_LIQUID_RESET_MONITOR);

                    if (shutdown) {
                        return;
                    }

                    StatusCode stOff = writeBooleanNode(NODE_DOSAGGIO_DOA, false);
                    if (!isGood(stOff)) {
                        throw new RuntimeException("Écriture FALSE DOA refusée : " + stOff);
                    }

                    mustWriteFalse = false;

                    if (shutdown) {
                        return;
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (shutdown || !isDisplayable()) {
                            return;
                        }

                        doaSequenceActive = false;
                        liquidSequenceInProgress.set(false);
                        liquidStopRequested.set(false);
                        activeLiquidNode.set(null);
                        doaPulseInProgress = false;

                        updateLiquidButtonsInterlock();
                        refreshOpcValues();
                        repaint();
                    });

                } catch (Exception ex) {
                    PLC3_Main.logButtonPress("DOA", doaNode, "ERROR", ex.getMessage());
                    ex.printStackTrace();

                    if (shutdown) {
                        return;
                    }

                    if (mustWriteFalse) {
                        try {
                            writeBooleanNode(NODE_DOSAGGIO_DOA, false);
                        } catch (Exception ignored) {
                        }
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (shutdown || !isDisplayable()) {
                            return;
                        }

                        doaSequenceActive = false;
                        liquidSequenceInProgress.set(false);
                        liquidStopRequested.set(false);
                        activeLiquidNode.set(null);
                        doaPulseInProgress = false;

                        updateLiquidButtonsInterlock();
                        refreshOpcValues();
                        repaint();

                        JOptionPane.showMessageDialog(
                                parentFrame,
                                "Erreur dosage DOA : " + ex.getMessage(),
                                "Erreur",
                                JOptionPane.ERROR_MESSAGE
                        );
                    });
                }
            }, "plc3-doa-sequence-thread").start();
        }

        private void handleHuileButtonClick() {
            NodeId huileNode = NODE_DOSAGGIO_OLIO;
            NodeId currentActive = activeLiquidNode.get();

            if (liquidSequenceInProgress.get()) {
                if (currentActive != null && currentActive.equals(huileNode)) {
                    liquidStopRequested.set(true);
                    PLC3_Main.logButtonPress("HUILE PLASTIFIANTE", huileNode, "STOP_REQUEST", "Bouton pressé pendant séquence active");
                }
                return;
            }

            PLC3_Main.logButtonPress("HUILE PLASTIFIANTE", huileNode, "PRESSED", "Demande utilisateur");

            if (!liquidSequenceInProgress.compareAndSet(false, true)) {
                return;
            }

            liquidStopRequested.set(false);
            activeLiquidNode.set(huileNode);
            huilePulseInProgress = true;
            huileSequenceActive = true;

            SwingUtilities.invokeLater(() -> {
                updateLiquidButtonsInterlock();
                repaint();
            });

            new Thread(() -> {
                boolean mustWriteFalse = false;

                try {
                    float presetValue = readConsigneFromOpcAndRefreshLabel();

                    if (presetValue <= 0f) {
                        throw new IllegalArgumentException("La CONSIGNE HUILE doit être > 0");
                    }

                    StatusCode stPreset = OpcManager.writeValue(
                            APP_NAME,
                            APP_URI,
                            NODE_PRESET_LIQUIDI_SILANO,
                            DataValue.valueOnly(new Variant(presetValue))
                    );

                    if (!isGood(stPreset)) {
                        throw new RuntimeException("Écriture du preset HUILE refusée : " + stPreset);
                    }

                    Thread.sleep(150);

                    StatusCode stOn = writeBooleanNode(NODE_DOSAGGIO_OLIO, true);
                    if (!isGood(stOn)) {
                        throw new RuntimeException("Écriture TRUE HUILE refusée : " + stOn);
                    }

                    mustWriteFalse = true;

                    waitUntilLiquidSequenceMustStop(NODE_LIQUID_RESET_MONITOR);

                    if (shutdown) {
                        return;
                    }

                    StatusCode stOff = writeBooleanNode(NODE_DOSAGGIO_OLIO, false);
                    if (!isGood(stOff)) {
                        throw new RuntimeException("Écriture FALSE HUILE refusée : " + stOff);
                    }

                    mustWriteFalse = false;

                    if (shutdown) {
                        return;
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (shutdown || !isDisplayable()) {
                            return;
                        }

                        huileSequenceActive = false;
                        liquidSequenceInProgress.set(false);
                        liquidStopRequested.set(false);
                        activeLiquidNode.set(null);
                        huilePulseInProgress = false;

                        updateLiquidButtonsInterlock();
                        refreshOpcValues();
                        repaint();
                    });

                } catch (Exception ex) {
                    PLC3_Main.logButtonPress("HUILE PLASTIFIANTE", huileNode, "ERROR", ex.getMessage());
                    ex.printStackTrace();

                    if (shutdown) {
                        return;
                    }

                    if (mustWriteFalse) {
                        try {
                            writeBooleanNode(NODE_DOSAGGIO_OLIO, false);
                        } catch (Exception ignored) {
                        }
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (shutdown || !isDisplayable()) {
                            return;
                        }

                        huileSequenceActive = false;
                        liquidSequenceInProgress.set(false);
                        liquidStopRequested.set(false);
                        activeLiquidNode.set(null);
                        huilePulseInProgress = false;

                        updateLiquidButtonsInterlock();
                        refreshOpcValues();
                        repaint();

                        JOptionPane.showMessageDialog(
                                parentFrame,
                                "Erreur dosage HUILE : " + ex.getMessage(),
                                "Erreur",
                                JOptionPane.ERROR_MESSAGE
                        );
                    });
                }
            }, "plc3-huile-sequence-thread").start();
        }

        private void waitUntilLiquidSequenceMustStop(NodeId monitorNode) throws Exception {
            while (!shutdown) {
                if (liquidStopRequested.get()) {
                    return;
                }

                List<DataValue> values = OpcManager.readValues(APP_NAME, APP_URI, Arrays.asList(monitorNode));

                if (values != null && !values.isEmpty()) {
                    String raw = extractValue(values.get(0));
                    if (isZeroValue(raw)) {
                        return;
                    }
                }

                Thread.sleep(300);
            }
        }

        private boolean isZeroValue(String rawValue) {
            if (rawValue == null) {
                return false;
            }

            String v = rawValue.trim();
            if (v.isEmpty()) {
                return false;
            }

            if ("false".equalsIgnoreCase(v)) {
                return true;
            }

            try {
                double d = Double.parseDouble(v.replace(",", "."));
                return d == 0.0;
            } catch (Exception ignored) {
            }

            return false;
        }

        private StatusCode writeBooleanNode(NodeId nodeId, boolean value) throws Exception {
            return OpcManager.writeValue(
                    APP_NAME,
                    APP_URI,
                    nodeId,
                    DataValue.valueOnly(new Variant(value))
            );
        }

        private boolean isGood(StatusCode status) {
            return status != null && status.isGood();
        }

        private void setButtonVisualState(DosageButton button, boolean active) {
            if (button == null) return;
            button.setVisualState(active ? LIQUID_BUTTON_ON : LIQUID_BUTTON_OFF, Color.BLACK);
        }

        private void flashLiquidButton(DosageButton button, Color targetBg, Color targetFg) {
            if (button == null) return;

            button.setVisualState(LIQUID_BUTTON_FLASH, Color.BLACK);

            Timer timer = new Timer(500, e -> button.setVisualState(targetBg, targetFg));
            timer.setRepeats(false);
            timer.start();
        }

        private void updateLiquidButtonsInterlock() {
            NodeId active = activeLiquidNode.get();
            boolean busy = liquidSequenceInProgress.get();

            if (!busy || active == null) {
                setButtonVisualState(btnDoa, doaSequenceActive);
                setButtonVisualState(btnOlioProcesso, huileSequenceActive);
                setButtonVisualState(btnSilano, silanoSequenceActive);

                if (btnDoa != null) btnDoa.setEnabled(!doaPulseInProgress);
                if (btnSilano != null) btnSilano.setEnabled(!silanoPulseInProgress);
                if (btnOlioProcesso != null) btnOlioProcesso.setEnabled(!huilePulseInProgress);
                repaint();
                return;
            }

            boolean doaActive = NODE_DOSAGGIO_DOA.equals(active);
            boolean silanoActive = NODE_DOSAGGIO_SILANO.equals(active);
            boolean olioActive = NODE_DOSAGGIO_OLIO.equals(active);

            setButtonVisualState(btnDoa, doaActive);
            setButtonVisualState(btnSilano, silanoActive);
            setButtonVisualState(btnOlioProcesso, olioActive);

            if (btnDoa != null) btnDoa.setEnabled(doaActive);
            if (btnSilano != null) btnSilano.setEnabled(silanoActive);
            if (btnOlioProcesso != null) btnOlioProcesso.setEnabled(olioActive);

            repaint();
        }

        private void updateLiquidButtonsState(String doa, String silano, String olio) {
            boolean doaActiveOpc = isDosaggioActive(doa) || doaSequenceActive;
            boolean silanoActiveOpc = isDosaggioActive(silano) || silanoSequenceActive;
            boolean olioActiveOpc = isDosaggioActive(olio) || huileSequenceActive;

            lastDoaActive = updateSingleLiquidButton(btnDoa, doaActiveOpc, lastDoaActive);
            lastSilanoActive = updateSingleLiquidButton(btnSilano, silanoActiveOpc, lastSilanoActive);
            lastOlioActive = updateSingleLiquidButton(btnOlioProcesso, olioActiveOpc, lastOlioActive);

            NodeId active = activeLiquidNode.get();
            boolean busy = liquidSequenceInProgress.get();

            if (busy && active != null) {
                setButtonVisualState(btnDoa, NODE_DOSAGGIO_DOA.equals(active));
                setButtonVisualState(btnSilano, NODE_DOSAGGIO_SILANO.equals(active));
                setButtonVisualState(btnOlioProcesso, NODE_DOSAGGIO_OLIO.equals(active));
            }

            updateLiquidButtonsInterlock();
            repaint();
        }

        private Boolean updateSingleLiquidButton(DosageButton button, boolean active, Boolean oldState) {
            if (button == null) return oldState;

            Color targetBg = active ? LIQUID_BUTTON_ON : LIQUID_BUTTON_OFF;
            Color targetFg = Color.BLACK;

            boolean changed = (oldState == null) || (oldState.booleanValue() != active);

            if (changed) {
                flashLiquidButton(button, targetBg, targetFg);
            } else {
                button.setVisualState(targetBg, targetFg);
            }

            return active;
        }

        private boolean isDosaggioActive(String rawValue) {
            if (rawValue == null) return false;

            String v = rawValue.trim();
            if (v.isEmpty()) return false;

            if (v.equalsIgnoreCase("true")) return true;
            if (v.equalsIgnoreCase("false")) return false;

            try {
                double num = Double.parseDouble(v.replace(",", "."));
                return num != 0.0;
            } catch (Exception ignored) {
            }

            return false;
        }

        private JButton addUnifiedBottomButton(int x, int y, final IconType type, final String buttonName) {
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

                    drawBottomButtonIcon(g2, type, getWidth(), getHeight());

                    g2.dispose();
                }
            };

            button.setRolloverEnabled(true);
            button.setMargin(new Insets(0, 0, 0, 0));
            button.setFocusable(false);
            button.setFocusPainted(false);
            button.setContentAreaFilled(false);
            button.setOpaque(false);
            button.setBorderPainted(true);
            button.setBorder(new ThinIconBorder());
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.setToolTipText(buttonName);

            button.addActionListener(e -> {
                switch (type) {
                    case ACK:
                        incrementStepNByOne();
                        break;
                    case WARNING:
                        openAlarmViewer();
                        break;
                    case DIAMOND:
                        openRecetteViewer();
                        break;
                    case BARS:
                        openGraphViewer();
                        break;
                    case BAG:
                        openMain1();
                        break;
                    default:
                        break;
                }
            });

            registerBaseBounds(button, x, y, BOTTOM_BUTTON_W, BOTTOM_BUTTON_H);
            return button;
        }

        private void drawBottomButtonIcon(Graphics2D g2, IconType type, int w, int h) {
            switch (type) {
                case ACK:
                    drawAckIcon(g2, w, h);
                    break;
                case WARNING:
                    drawWarningIcon(g2, w, h);
                    break;
                case DIAMOND:
                    drawRecipeIcon(g2, w, h);
                    break;
                case BARS:
                    drawBarsChartIcon(g2, w, h);
                    break;
                case BAG:
                    drawBagIcon(g2, w, h);
                    break;
                default:
                    break;
            }
        }

        private void drawAckIcon(Graphics2D g2, int w, int h) {
            int pad = 12;
            int rw = w - (pad * 2);
            int rh = h - (pad * 2);

            g2.setColor(new Color(228, 52, 52));
            g2.fillRoundRect(pad, pad, rw, rh, 12, 12);

            g2.setColor(new Color(120, 10, 10));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(pad, pad, rw, rh, 12, 12);

            g2.setColor(new Color(150, 150, 150));
            g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(15, h / 3)));
            FontMetrics fm = g2.getFontMetrics();
            String s = "ACK";
            int tx = (w - fm.stringWidth(s)) / 2;
            int ty = (h + fm.getAscent()) / 2 - 3;
            g2.drawString(s, tx, ty);
        }

        private void drawWarningIcon(Graphics2D g2, int w, int h) {
            int cx = w / 2;
            int topY = 10;
            int bottomY = h - 10;

            Polygon p = new Polygon(
                    new int[]{cx, 12, w - 12},
                    new int[]{topY, bottomY, bottomY},
                    3
            );

            g2.setColor(new Color(228, 52, 52));
            g2.fillPolygon(p);

            g2.setColor(new Color(120, 10, 10));
            g2.setStroke(new BasicStroke(2f));
            g2.drawPolygon(p);

            g2.setColor(new Color(150, 150, 150));
            g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(18, h / 2)));
            FontMetrics fm = g2.getFontMetrics();
            String s = "!";
            int tx = (w - fm.stringWidth(s)) / 2;
            int ty = (h + fm.getAscent()) / 2 - 4;
            g2.drawString(s, tx, ty);
        }

        private void drawRecipeIcon(Graphics2D g2, int w, int h) {
            if (recipeIconImage != null) {
                int margin = Math.max(7, Math.min(w, h) / 7);
                g2.drawImage(recipeIconImage, margin, margin, w - 2 * margin, h - 2 * margin, null);
                return;
            }

            g2.setColor(new Color(70, 70, 70));
            g2.fillRoundRect(18, 12, w - 36, h - 24, 8, 8);

            g2.setColor(new Color(150, 150, 150));
            g2.fillRect(24, 16, w - 48, h - 32);

            g2.setColor(new Color(70, 70, 70));
            g2.drawRect(24, 16, w - 48, h - 32);

            g2.drawLine(30, 24, w - 30, 24);
            g2.drawLine(30, 31, w - 30, 31);
            g2.drawLine(30, 38, w - 40, 38);
            g2.drawLine(30, 45, w - 34, 45);
        }

        private void drawBarsChartIcon(Graphics2D g2, int w, int h) {
            int baseY = h - 14;
            int leftX = 16;
            int barW = 11;
            int gap = 8;

            g2.setColor(new Color(70, 70, 70));
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(12, baseY, w - 12, baseY);
            g2.drawLine(12, 12, 12, baseY);

            int[] heights = {18, 28, 38, 24};

            for (int i = 0; i < heights.length; i++) {
                int x = leftX + i * (barW + gap);
                int y = baseY - heights[i];
                GradientPaint gp = new GradientPaint(
                        x, y, new Color(68, 100, 255),
                        x, baseY, new Color(36, 62, 207)
                );
                g2.setPaint(gp);
                g2.fillRoundRect(x, y, barW, heights[i], 4, 4);
                g2.setColor(new Color(25, 35, 110));
                g2.drawRoundRect(x, y, barW, heights[i], 4, 4);
            }
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

        private void addText(String text, int x, int y, int size, boolean bold) {
            JLabel label = new JLabel(text);
            label.setForeground(Color.BLACK);
            label.setFont(new Font("Arial", bold ? Font.BOLD : Font.PLAIN, size));
            registerBaseBounds(label, x, y, 180, 24);
        }

        private JLabel addVersionLabel(String text, int x, int y, int w, int h) {
            JLabel label = new JLabel(text, SwingConstants.RIGHT);
            label.setForeground(Color.BLACK);
            label.setFont(new Font("Arial", Font.BOLD, 16));
            label.setOpaque(false);
            registerBaseBounds(label, x, y, w, h);
            return label;
        }

        private void addButton(String text, int x, int y, int w, int h, Color bg, Color fg, int fontSize) {
            addButton(text, x, y, w, h, bg, fg, fontSize, new Color(85, 85, 85), 1);
        }

        private void addButton(String text, int x, int y, int w, int h, Color bg, Color fg, int fontSize, Color borderColor, int borderWidth) {
            JLabel label = bevelLabel(text, fontSize, bg, fg);
            label.setBorder(new BevelRectBorder(borderColor, borderWidth));
            registerBaseBounds(label, x, y, w, h);
        }

        private JLabel addValueButtonLikeField(String text, int x, int y, int w, int h, Color bg, Color fg, int fontSize) {
            JLabel label = bevelLabel(text, fontSize, bg, fg);
            label.setBorder(new BevelRectBorder(new Color(85, 85, 85), 1));
            registerBaseBounds(label, x, y, w, h);
            return label;
        }

        private JLabel addValueFieldLikeEmpty(int x, int y, int w, int h) {
            JLabel label = new JLabel("", SwingConstants.CENTER);
            label.setOpaque(true);
            label.setBackground(new Color(244, 244, 244));
            label.setForeground(Color.BLACK);
            label.setFont(new Font("Arial", Font.BOLD, 12));
            label.setBorder(new BevelRectBorder(new Color(110, 110, 110), 1));
            registerBaseBounds(label, x, y, w, h);
            return label;
        }


        private JLabel addModeIndicatorField(String text, int x, int y, int w, int h) {
            JLabel label = new JLabel(text, SwingConstants.CENTER);
            label.setOpaque(true);
            label.setBackground(new Color(214, 214, 214));
            label.setForeground(Color.BLACK);
            label.setFont(new Font("Arial", Font.BOLD, 15));
            label.setBorder(new BevelRectBorder(new Color(85, 85, 85), 1));
            registerBaseBounds(label, x, y, w, h);
            return label;
        }

        private void updateModeIndicators(String rawMode) {
            currentDbMode = rawMode == null ? "" : rawMode.trim();

            boolean autoActive = "auto".equalsIgnoreCase(currentDbMode);
            boolean manualActive = isManualMode(currentDbMode);

            if (lblAutoMode != null) {
                lblAutoMode.setBackground(autoActive ? new Color(39, 255, 31) : new Color(214, 214, 214));
                lblAutoMode.setForeground(Color.BLACK);
            }

            if (lblManualMode != null) {
                lblManualMode.setBackground(manualActive ? new Color(255, 27, 27) : new Color(214, 214, 214));
                lblManualMode.setForeground(Color.BLACK);
            }
        }

        private JLabel addField(String text, int x, int y, int w, int h, int fontSize, boolean red, boolean blue) {
            JLabel label = new JLabel(text, SwingConstants.CENTER);
            label.setOpaque(true);
            label.setBackground(red ? new Color(255, 27, 27) : Color.WHITE);
            label.setForeground(Color.BLACK);
            label.setFont(new Font("Trebuchet MS", Font.PLAIN, fontSize));
            label.setBorder(new DataFieldBorder(red, blue));
            registerBaseBounds(label, x, y, w, h);
            return label;
        }

        private JLabel bevelLabel(String text, int fontSize, Color bg, Color fg) {
            JLabel label = new JLabel(text, SwingConstants.CENTER);
            label.setOpaque(true);
            label.setBackground(bg);
            label.setForeground(fg);
            label.setFont(new Font("Arial", Font.BOLD, fontSize));
            label.setBorder(new BevelRectBorder(new Color(85, 85, 85), 1));
            return label;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            double sx = scaleX();
            double sy = scaleY();
            g2.scale(sx, sy);

            paintBackground(g2);
            paintFrame(g2);
            paintMixer(g2);
            paintHopper(g2);
            paintMotor(g2);
            paintRightIndicators(g2);
            paintLiquidValves(g2);

            g2.dispose();
        }

        private void paintBackground(Graphics2D g2) {
            g2.setColor(new Color(207, 207, 207));
            g2.fillRect(0, 0, BASE_W, BASE_H);
            g2.setColor(new Color(155, 155, 155));
            for (int y = 0; y < BASE_H; y += 11) {
                for (int x = 0; x < BASE_W; x += 11) {
                    g2.fillRect(x, y, 1, 1);
                }
            }
        }

        private void paintFrame(Graphics2D g2) {
            g2.setColor(new Color(36, 62, 207));
            g2.setFont(new Font("Serif", Font.BOLD, 28));
            FontMetrics fm = g2.getFontMetrics();
            String s = "MIXER";
            int sw = fm.stringWidth(s);
            g2.drawString(s, 475 - sw / 2, 42);
            g2.drawLine(475 - sw / 2, 45, 475 + sw / 2, 45);
        }

        private void paintLiquidValves(Graphics2D g2) {
            boolean doaOn = btnDoa != null && btnDoa.isVisualOn();
            boolean silanoOn = btnSilano != null && btnSilano.isVisualOn();
            boolean olioOn = btnOlioProcesso != null && btnOlioProcesso.isVisualOn();

            int centerY1 = LIQUID_Y_DOA + LIQUID_VALVE_DIAMETER / 2;
            int centerY2 = LIQUID_Y_SILANO + LIQUID_VALVE_DIAMETER / 2;
            int centerY3 = LIQUID_Y_OLIO + LIQUID_VALVE_DIAMETER / 2;

            int valveRightX = LIQUID_VALVE_X + LIQUID_VALVE_DIAMETER;
            int mainLineY = centerY2;

            boolean upperCollectorActive = doaOn;
            boolean lowerCollectorActive = olioOn;
            boolean mainLineActive = doaOn || silanoOn || olioOn;

            drawValveWithShortLeftPipe(g2, LIQUID_VALVE_X, LIQUID_Y_DOA, doaOn, doaOn);
            drawValveWithShortLeftPipe(g2, LIQUID_VALVE_X, LIQUID_Y_SILANO, silanoOn, silanoOn);
            drawValveWithShortLeftPipe(g2, LIQUID_VALVE_X, LIQUID_Y_OLIO, olioOn, olioOn);

            drawPipeSegment(g2, valveRightX, centerY1, LIQUID_COLLECTOR_X, centerY1, doaOn);
            drawPipeSegment(g2, valveRightX, centerY2, LIQUID_COLLECTOR_X, centerY2, silanoOn);
            drawPipeSegment(g2, valveRightX, centerY3, LIQUID_COLLECTOR_X, centerY3, olioOn);

            drawPipeSegment(g2, LIQUID_COLLECTOR_X, centerY1, LIQUID_COLLECTOR_X, centerY2, upperCollectorActive);
            drawPipeSegment(g2, LIQUID_COLLECTOR_X, centerY2, LIQUID_COLLECTOR_X, centerY3, lowerCollectorActive);
            drawPipeSegment(g2, LIQUID_COLLECTOR_X, mainLineY, LIQUID_MIXER_ENTRY_X, mainLineY, mainLineActive);

            if (mainLineActive) {
                drawLeakingOilIntoMixer(g2, mainLineY);
            }
        }

        private void updateOilLeakParticles() {
            boolean mainLineActive =
                    (btnDoa != null && btnDoa.isVisualOn())
                            || (btnSilano != null && btnSilano.isVisualOn())
                            || (btnOlioProcesso != null && btnOlioProcesso.isVisualOn());

            if (mainLineActive) {
                spawnOilLeakParticles();
            }

            java.util.Iterator<OilParticle> it = oilParticles.iterator();
            while (it.hasNext()) {
                OilParticle p = it.next();
                p.update();
                if (p.isDead()) {
                    it.remove();
                }
            }
        }

        private void spawnOilLeakParticles() {
            int startX = LIQUID_MIXER_ENTRY_X + LEAK_OFFSET_X;
            int startY = (LIQUID_Y_SILANO + LIQUID_VALVE_DIAMETER / 2) + LEAK_STREAM_ENTRY_Y_OFFSET;

            int particleCount = 4;
            for (int i = 0; i < particleCount; i++) {
                double vx = 1.55 + oilRng.nextGaussian() * 0.18;
                double vy = -0.18 + oilRng.nextGaussian() * 0.10;
                int size = 4 + oilRng.nextInt(3);
                oilParticles.add(new OilParticle(startX, startY, vx, vy, size));
            }
        }

        private void drawLeakingOilIntoMixer(Graphics2D g2, int pipeY) {
            Graphics2D oilG = (Graphics2D) g2.create();
            oilG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int startX = LIQUID_MIXER_ENTRY_X + LEAK_OFFSET_X;
            int startY = pipeY + LEAK_STREAM_ENTRY_Y_OFFSET;

            for (OilParticle p : oilParticles) {
                float a = Math.max(0f, Math.min(1f, p.alpha));
                int s = Math.max(1, (int) Math.round(p.size * a));

                oilG.setColor(new Color(215 / 255f, 182 / 255f, 76 / 255f, a * 0.95f));
                oilG.fillOval((int) Math.round(p.x) - s / 2, (int) Math.round(p.y) - s / 2, s, s + 1);

                oilG.setColor(new Color(1f, 0.96f, 0.78f, a * 0.35f));
                oilG.fillOval((int) Math.round(p.x) - s / 3, (int) Math.round(p.y) - s / 3, Math.max(1, s / 2), Math.max(1, s / 2));
            }

            if (!oilParticles.isEmpty()) {
                int arcW = 30;
                int arcH = 10;
                oilG.setColor(new Color(224, 195, 92, 120));
                oilG.fillOval(startX - 7, startY - 4, arcW, arcH);
            }

            int puddleCenterX = LEAK_STREAM_START_X + LEAK_OFFSET_X;
            int puddleY = LEAK_STREAM_END_Y + 10;
            int puddleW = 40;
            int puddleH = 12;
            int puddleX = puddleCenterX - puddleW / 2 + (int) Math.round(Math.sin(flowOffset * 0.05f) * 2.0);

            GradientPaint puddlePaint = new GradientPaint(
                    puddleX, puddleY, new Color(185, 140, 48, 160),
                    puddleX, puddleY + puddleH, new Color(105, 70, 18, 110)
            );
            oilG.setPaint(puddlePaint);
            oilG.fillOval(puddleX, puddleY, puddleW, puddleH);

            oilG.setColor(new Color(255, 232, 150, 95));
            oilG.drawOval(puddleX, puddleY, puddleW, puddleH);

            oilG.dispose();
        }

        private void drawValveWithShortLeftPipe(Graphics2D g2, int x, int y, boolean active, boolean leftPipeActive) {
            int centerY = y + LIQUID_VALVE_DIAMETER / 2;
            int leftPipeX = x - LIQUID_PIPE_SIDE_LEN;

            drawPipeSegment(g2, leftPipeX, centerY, x, centerY, leftPipeActive);

            Color fillColor = active ? new Color(39, 255, 31) : new Color(170, 170, 170);
            Color borderColor = active ? new Color(10, 120, 20) : new Color(90, 90, 90);

            g2.setColor(fillColor);
            g2.fillOval(x, y, LIQUID_VALVE_DIAMETER, LIQUID_VALVE_DIAMETER);

            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(x, y, LIQUID_VALVE_DIAMETER, LIQUID_VALVE_DIAMETER);

            int cx = x + LIQUID_VALVE_DIAMETER / 2;
            int cy = y + LIQUID_VALVE_DIAMETER / 2;
            int r = 7;

            Polygon tri;
            if (active) {
                tri = new Polygon(
                        new int[]{cx + r, cx - r, cx - r},
                        new int[]{cy, cy - r, cy + r},
                        3
                );
            } else {
                tri = new Polygon(
                        new int[]{cx, cx - r, cx + r},
                        new int[]{cy - r, cy + r, cy + r},
                        3
                );
            }

            g2.setColor(Color.BLACK);
            g2.fillPolygon(tri);
        }

        private void drawPipeSegment(Graphics2D g2, int x1, int y1, int x2, int y2, boolean active) {
            boolean horizontal = y1 == y2;
            boolean vertical = x1 == x2;

            if (!horizontal && !vertical) {
                return;
            }

            g2.setStroke(new BasicStroke(PIPE_FILL_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(225, 225, 225));
            g2.drawLine(x1, y1, x2, y2);

            g2.setStroke(new BasicStroke(PIPE_BORDER_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(98, 98, 98));
            g2.drawLine(x1, y1, x2, y2);

            if (active) {
                if (horizontal) {
                    drawFlowHorizontal(g2, x1, x2, y1);
                } else {
                    drawFlowVertical(g2, x1, y1, y2);
                }
            }
        }

        private void drawFlowHorizontal(Graphics2D g2, int x1, int x2, int y) {
            int left = Math.min(x1, x2);
            int right = Math.max(x1, x2);
            int length = right - left;

            if (length <= 0) {
                return;
            }

            for (int i = -80; i < length + 80; i += FLOW_STEP) {
                int px = left + (((int) (i + flowOffset)) % (length + FLOW_STEP));
                if (px >= left && px <= right) {
                    g2.setColor(LIQUID_FLOW_COLOR);
                    g2.fillOval(px - FLOW_DOT_SIZE / 2, y - FLOW_DOT_SIZE / 2, FLOW_DOT_SIZE, FLOW_DOT_SIZE);

                    int hi = Math.max(2, FLOW_DOT_SIZE / 2);
                    g2.setColor(LIQUID_FLOW_HIGHLIGHT);
                    g2.fillOval(px - hi / 2, y - hi / 2, hi, hi);
                }
            }
        }

        private void drawFlowVertical(Graphics2D g2, int x, int y1, int y2) {
            int top = Math.min(y1, y2);
            int bottom = Math.max(y1, y2);
            int length = bottom - top;

            if (length <= 0) {
                return;
            }

            for (int i = -80; i < length + 80; i += FLOW_STEP) {
                int py = bottom - ((((int) (i + flowOffset)) % (length + FLOW_STEP)));
                if (py >= top && py <= bottom) {
                    g2.setColor(LIQUID_FLOW_COLOR);
                    g2.fillOval(x - FLOW_DOT_SIZE / 2, py - FLOW_DOT_SIZE / 2, FLOW_DOT_SIZE, FLOW_DOT_SIZE);

                    int hi = Math.max(2, FLOW_DOT_SIZE / 2);
                    g2.setColor(LIQUID_FLOW_HIGHLIGHT);
                    g2.fillOval(x - hi / 2, py - hi / 2, hi, hi);
                }
            }
        }

        private boolean isAnyLiquidActive() {
            return (btnDoa != null && btnDoa.isVisualOn())
                    || (btnSilano != null && btnSilano.isVisualOn())
                    || (btnOlioProcesso != null && btnOlioProcesso.isVisualOn());
        }

        private void paintMixer(Graphics2D g2) {
            // SINISTRA DIVISA IN DUE
            g2.setColor(new Color(141, 141, 141));
            g2.fillRect(548, 32, 10, 214);
            g2.fillRect(548, 330, 10, 167);

            g2.setColor(new Color(46, 46, 46));
            g2.drawRect(548, 32, 10, 214);
            g2.drawRect(548, 330, 10, 167);

            // DESTRA DIVISA IN DUE PER LASCIARE UNO SPAZIO LIBERO IN BASSO
            // DESTRA ALLINEATA AL BORDO SUPERIORE DEL PORTELLO DECHARGE
            g2.setColor(new Color(141, 141, 141));
            g2.fillRect(665, 32, 10, 381);

            g2.setColor(new Color(46, 46, 46));
            g2.drawRect(665, 32, 10, 381);

            g2.setColor(new Color(143, 133, 48));
            g2.fillRect(559, 48, 104, 10);
            g2.fillRect(659, 42, 12, 10);

            g2.setColor(new Color(69, 69, 69));
            g2.drawRect(559, 48, 104, 10);
            g2.drawRect(659, 42, 12, 10);

            // Contenuto interno del vano superiore rimosso su richiesta.
            // Il traverso marrone centrale e' stato rimosso; il vetro viene
            // gestito in paintRightIndicators per estendersi piu' in alto.

            drawChargeDoor(g2);
            drawChargeConveyorAndPackets(g2);
        }

        private void drawChargeDoor(Graphics2D g2) {
            Graphics2D doorG = (Graphics2D) g2.create();
            doorG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            double angleDeg = lerp(
                    CHARGE_DOOR_CLOSED_ANGLE_DEG,
                    CHARGE_DOOR_OPEN_ANGLE_DEG,
                    chargeDoorProgress
            );

            drawDoorPanel(
                    doorG,
                    CHARGE_DOOR_PIVOT_X,
                    CHARGE_DOOR_PIVOT_Y,
                    CHARGE_DOOR_PANEL_LENGTH,
                    CHARGE_DOOR_PANEL_WIDTH,
                    angleDeg,
                    new Color(160, 160, 160),
                    new Color(70, 70, 70),
                    new Color(214, 180, 0),
                    false
            );

            doorG.dispose();
        }

        private void paintHopper(Graphics2D g2) {
            GradientPaint gp = new GradientPaint(721, 26, new Color(169, 169, 169), 721, 176, new Color(140, 140, 140));
            g2.setPaint(gp);
            g2.fillRoundRect(721, 26, 66, 150, 28, 28);
            g2.setColor(new Color(79, 58, 58));
            g2.drawRoundRect(721, 26, 66, 150, 28, 28);
            g2.setColor(new Color(109, 35, 35));
            g2.fillRect(723, 70, 62, 9);
            g2.fillRect(723, 70, 62, 9);
            g2.setColor(new Color(57, 238, 255));
            g2.fillRect(741, 81, 27, 41);
            g2.setColor(new Color(25, 128, 138));
            g2.drawRect(741, 81, 27, 41);

            Polygon cone = new Polygon(new int[]{732, 776, 764, 744}, new int[]{173, 173, 202, 202}, 4);
            g2.setColor(new Color(142, 142, 142));
            g2.fillPolygon(cone);
            g2.setColor(new Color(79, 58, 58));
            g2.drawPolygon(cone);
            g2.fillRect(742, 201, 24, 16);

            AffineTransform old = g2.getTransform();
            g2.rotate(Math.toRadians(-18), 721, 190);
            g2.setColor(new Color(143, 143, 143));
            g2.fillRect(698, 176, 46, 14);
            g2.setColor(new Color(68, 68, 68));
            g2.drawRect(698, 176, 46, 14);
            g2.setTransform(old);
        }

        private void paintMotor(Graphics2D g2) {
            int dx = MOTOR_SHIFT_X;
            int dy = MOTOR_SHIFT_Y;

            g2.setColor(new Color(48, 255, 31));
            g2.fillRoundRect(96 + dx, 438 + dy, 130, 78, 8, 8);
            g2.setColor(new Color(20, 125, 22));
            g2.drawRoundRect(96 + dx, 438 + dy, 130, 78, 8, 8);
            g2.drawLine(106 + dx, 448 + dy, 216 + dx, 448 + dy);
            g2.drawLine(106 + dx, 462 + dy, 216 + dx, 462 + dy);
            g2.drawLine(106 + dx, 476 + dy, 216 + dx, 476 + dy);
            g2.drawLine(106 + dx, 490 + dy, 216 + dx, 490 + dy);
            g2.drawLine(106 + dx, 504 + dy, 216 + dx, 504 + dy);

            g2.setColor(new Color(53, 255, 40));
            g2.fillRect(226 + dx, 458 + dy, 22, 38);
            g2.setColor(new Color(20, 125, 22));
            g2.drawRect(226 + dx, 458 + dy, 22, 38);

            Polygon body = new Polygon(
                    new int[]{248 + dx, 343 + dx, 343 + dx, 265 + dx, 248 + dx, 248 + dx},
                    new int[]{428 + dy, 428 + dy, 518 + dy, 518 + dy, 487 + dy, 459 + dy},
                    6
            );
            g2.setColor(new Color(131, 169, 169));
            g2.fillPolygon(body);
            g2.setColor(new Color(75, 103, 103));
            g2.drawPolygon(body);

            g2.setColor(new Color(239, 239, 239));
            g2.fillRect(274 + dx, 452 + dy, 43, 22);
            g2.setColor(new Color(68, 68, 68));
            g2.drawRect(274 + dx, 452 + dy, 43, 22);
            g2.setFont(new Font("Arial", Font.BOLD, 14));
            g2.drawString("M4", 288 + dx, 468 + dy);

            g2.setColor(new Color(48, 255, 46));
            g2.fillRect(279 + dx, 476 + dy, 18, 34);
            g2.setColor(new Color(27, 106, 27));
            g2.drawRect(279 + dx, 476 + dy, 18, 34);
            g2.drawRect(277 + dx, 512 + dy, 20, 9);

            for (int i = 0; i < 4; i++) {
                g2.setColor(new Color(111, 143, 143));
                g2.fillRect(350 + dx + i * 20, 466 + dy, 18, 30);
                g2.setColor(new Color(63, 93, 93));
                g2.drawRect(350 + dx + i * 20, 466 + dy, 18, 30);
            }
        }

        private double mapRotorSpeedFromVitesse(String rawValue) {
            if (rawValue == null || rawValue.trim().isEmpty()) {
                return 0.0;
            }

            try {
                double vitesseNum = Double.parseDouble(rawValue.trim().replace(",", "."));
                if (vitesseNum <= 0.0) {
                    return 0.0;
                }
                return vitesseNum / 20.0;
            } catch (Exception ex) {
                return 0.0;
            }
        }

        private void drawRotorAssembly(Graphics2D g2, double cx, double cy, double angleDeg, boolean mirrorX) {
            drawRotor(g2, cx, cy, 30.0, angleDeg, mirrorX,
                    new Color(55, 70, 85),
                    new Color(145, 185, 225));
        }

        private void drawRotorShaftHousing(Graphics2D g2, double cx, double cy, double rOuter, double rFlange, double hubR) {
            Ellipse2D shadow = new Ellipse2D.Double(cx - rOuter, cy - rOuter + 2.5, rOuter * 2, rOuter * 2);
            g2.setColor(new Color(0, 0, 0, 24));
            g2.fill(shadow);

            Ellipse2D outer = new Ellipse2D.Double(cx - rOuter, cy - rOuter, rOuter * 2, rOuter * 2);
            GradientPaint gpOuter = new GradientPaint(
                    (float) cx, (float) (cy - rOuter), new Color(175, 180, 185),
                    (float) cx, (float) (cy + rOuter), new Color(105, 110, 116)
            );
            g2.setPaint(gpOuter);
            g2.fill(outer);

            g2.setColor(new Color(70, 70, 70));
            g2.setStroke(new BasicStroke(1.1f));
            g2.draw(outer);

            Ellipse2D flange = new Ellipse2D.Double(cx - rFlange, cy - rFlange, rFlange * 2, rFlange * 2);
            RadialGradientPaint flangePaint = new RadialGradientPaint(
                    new Point2D.Double(cx, cy),
                    (float) rFlange,
                    new float[]{0f, 0.7f, 1f},
                    new Color[]{new Color(220, 225, 230), new Color(145, 150, 156), new Color(80, 84, 90)}
            );
            g2.setPaint(flangePaint);
            g2.fill(flange);

            g2.setColor(new Color(45, 45, 45));
            g2.setStroke(new BasicStroke(0.9f));
            g2.draw(flange);

            Ellipse2D hub = new Ellipse2D.Double(cx - hubR, cy - hubR, hubR * 2, hubR * 2);
            RadialGradientPaint hubPaint = new RadialGradientPaint(
                    new Point2D.Double(cx - 1.2, cy - 1.2),
                    (float) (hubR * 1.5),
                    new float[]{0f, 1f},
                    new Color[]{new Color(78, 84, 90), new Color(20, 22, 26)}
            );
            g2.setPaint(hubPaint);
            g2.fill(hub);

            g2.setColor(new Color(185, 195, 205));
            g2.setStroke(new BasicStroke(0.8f));
            g2.draw(hub);
        }

        private void drawRotor(Graphics2D g2, double cx, double cy, double radius, double angleDeg,
                               boolean mirrorX, Color baseColor, Color highlightColor) {

            Graphics2D rotorG = (Graphics2D) g2.create();
            rotorG.translate(cx, cy);
            rotorG.rotate(Math.toRadians(angleDeg));

            if (mirrorX) {
                rotorG.scale(-1.0, 1.0);
            }

            for (int i = 0; i < 2; i++) {
                Shape blade = createIntermeshingBlade(radius);

                GradientPaint bladePaint = new GradientPaint(
                        (float) (-radius * 0.10), (float) (-radius * 0.90), highlightColor,
                        (float) (radius * 0.35), (float) (radius * 0.90), baseColor
                );
                rotorG.setPaint(bladePaint);
                rotorG.fill(blade);

                rotorG.setColor(new Color(255, 255, 255, 60));
                rotorG.setStroke(new BasicStroke(0.7f));
                rotorG.draw(createBladeHighlight(radius));

                rotorG.setColor(new Color(18, 28, 38));
                rotorG.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                rotorG.draw(blade);

                rotorG.rotate(Math.toRadians(180));
            }

            double coreR = radius * 0.20;
            Ellipse2D core = new Ellipse2D.Double(-coreR, -coreR, coreR * 2, coreR * 2);

            RadialGradientPaint corePaint = new RadialGradientPaint(
                    new Point2D.Double(-2, -2),
                    (float) (coreR * 1.5),
                    new float[]{0f, 0.55f, 1f},
                    new Color[]{new Color(225, 232, 240), new Color(115, 135, 152), new Color(40, 48, 58)}
            );
            rotorG.setPaint(corePaint);
            rotorG.fill(core);

            rotorG.setColor(new Color(18, 28, 38));
            rotorG.setStroke(new BasicStroke(0.9f));
            rotorG.draw(core);

            double holeR = radius * 0.075;
            Ellipse2D hole = new Ellipse2D.Double(-holeR, -holeR, holeR * 2, holeR * 2);
            rotorG.setColor(new Color(16, 16, 18));
            rotorG.fill(hole);
            rotorG.setColor(new Color(185, 195, 205));
            rotorG.setStroke(new BasicStroke(0.5f));
            rotorG.draw(hole);

            rotorG.dispose();
        }

        private Shape createIntermeshingBlade(double r) {
            Path2D.Double p = new Path2D.Double();

            p.moveTo(-0.30 * r, -0.94 * r);
            p.lineTo(-0.18 * r, -1.00 * r);
            p.lineTo( 0.18 * r, -1.00 * r);
            p.lineTo( 0.30 * r, -0.94 * r);

            p.lineTo(0.42 * r, -0.86 * r);
            p.lineTo(0.54 * r, -0.70 * r);
            p.lineTo(0.54 * r, -0.54 * r);

            p.lineTo(0.52 * r, -0.40 * r);
            p.lineTo(0.40 * r, -0.28 * r);
            p.lineTo(0.26 * r, -0.18 * r);
            p.lineTo(0.14 * r, -0.10 * r);

            p.lineTo(0.10 * r, -0.03 * r);
            p.lineTo(0.10 * r,  0.03 * r);

            p.lineTo(0.14 * r, 0.10 * r);
            p.lineTo(0.26 * r, 0.18 * r);
            p.lineTo(0.40 * r, 0.28 * r);
            p.lineTo(0.52 * r, 0.40 * r);

            p.lineTo(0.54 * r, 0.54 * r);
            p.lineTo(0.54 * r, 0.70 * r);
            p.lineTo(0.42 * r, 0.86 * r);
            p.lineTo(0.30 * r, 0.94 * r);

            p.lineTo( 0.18 * r, 1.00 * r);
            p.lineTo(-0.18 * r, 1.00 * r);
            p.lineTo(-0.30 * r, 0.94 * r);

            p.lineTo(-0.42 * r, 0.86 * r);
            p.lineTo(-0.54 * r, 0.70 * r);
            p.lineTo(-0.54 * r, 0.54 * r);

            p.lineTo(-0.52 * r, 0.40 * r);
            p.lineTo(-0.40 * r, 0.28 * r);
            p.lineTo(-0.26 * r, 0.18 * r);
            p.lineTo(-0.14 * r, 0.10 * r);

            p.lineTo(-0.10 * r, 0.03 * r);
            p.lineTo(-0.10 * r, -0.03 * r);

            p.lineTo(-0.14 * r, -0.10 * r);
            p.lineTo(-0.26 * r, -0.18 * r);
            p.lineTo(-0.40 * r, -0.28 * r);
            p.lineTo(-0.52 * r, -0.40 * r);

            p.lineTo(-0.54 * r, -0.54 * r);
            p.lineTo(-0.54 * r, -0.70 * r);
            p.lineTo(-0.42 * r, -0.86 * r);
            p.lineTo(-0.30 * r, -0.94 * r);
            p.closePath();
            return p;
        }

        private Shape createBladeHighlight(double r) {
            Path2D.Double p = new Path2D.Double();

            p.moveTo(-0.02 * r, -0.84 * r);
            p.lineTo( 0.10 * r, -0.78 * r);
            p.lineTo( 0.14 * r, -0.58 * r);
            p.lineTo( 0.08 * r, -0.20 * r);

            p.moveTo(-0.02 * r, 0.20 * r);
            p.lineTo( 0.08 * r, 0.58 * r);
            p.lineTo( 0.14 * r, 0.78 * r);
            p.lineTo( 0.02 * r, 0.86 * r);

            return p;
        }

        private void paintRightIndicators(Graphics2D g2) {
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(4f));
            g2.drawLine(546, 497, 678, 497);

            Rectangle rotorArea = new Rectangle(558, 396, 107, 101);
            Rectangle glassArea = new Rectangle(558, 162, 107, 260);

            double leftCx = 594.0;
            double rightCx = 632.0;
            double cy = 440.0;

            double leftAngle = rotorMasterAngle;
            double rightAngle = -rotorMasterAngle + ROTOR_RIGHT_PHASE_OFFSET;

            Shape oldClip = g2.getClip();
            g2.setClip(rotorArea);
            drawRotorAssembly(g2, leftCx, cy, leftAngle, false);
            drawRotorAssembly(g2, rightCx, cy, rightAngle, true);
            g2.setClip(oldClip);

            Graphics2D glassG = (Graphics2D) g2.create();
            glassG.setClip(glassArea);

            GradientPaint glassPaint = new GradientPaint(
                    glassArea.x, glassArea.y, new Color(255, 255, 255, 70),
                    glassArea.x, glassArea.y + glassArea.height, new Color(160, 205, 225, 35)
            );
            glassG.setPaint(glassPaint);
            glassG.fillRect(glassArea.x, glassArea.y, glassArea.width, glassArea.height);

            glassG.setPaint(new GradientPaint(
                    glassArea.x, glassArea.y + 8, new Color(255, 255, 255, 125),
                    glassArea.x, glassArea.y + 42, new Color(255, 255, 255, 0)
            ));
            glassG.fillRoundRect(glassArea.x + 3, glassArea.y + 4, glassArea.width - 6, 26, 12, 12);

            glassG.setPaint(new GradientPaint(
                    glassArea.x, glassArea.y, new Color(255, 255, 255, 26),
                    glassArea.x + glassArea.width, glassArea.y + glassArea.height, new Color(255, 255, 255, 0)
            ));
            glassG.fillRect(glassArea.x, glassArea.y, glassArea.width, glassArea.height);

            glassG.setPaint(new RadialGradientPaint(
                    new Point2D.Double(glassArea.getCenterX(), glassArea.y + glassArea.height - 50),
                    180f,
                    new float[]{0.0f, 0.72f, 1.0f},
                    new Color[]{new Color(255, 255, 255, 0), new Color(0, 0, 0, 0), new Color(0, 0, 0, 68)}
            ));
            glassG.fillRect(glassArea.x, glassArea.y, glassArea.width, glassArea.height);

            glassG.setColor(new Color(255, 255, 255, 72));
            glassG.setStroke(new BasicStroke(1.2f));
            glassG.drawLine(glassArea.x + 8, glassArea.y + 8, glassArea.x + glassArea.width - 16, glassArea.y + 8);
            glassG.drawLine(glassArea.x + 12, glassArea.y + 16, glassArea.x + glassArea.width - 28, glassArea.y + 16);

            glassG.dispose();

            int triangleCenterX = (int) Math.round((leftCx + rightCx) / 2.0);
            Polygon cone = new Polygon(
                    new int[]{triangleCenterX, triangleCenterX + 27, triangleCenterX - 27},
                    new int[]{471, 497, 497},
                    3
            );
            g2.setColor(new Color(211, 197, 34));
            g2.fillPolygon(cone);
            g2.setColor(new Color(130, 120, 20));
            g2.drawPolygon(cone);

            drawDischargeDoor(g2);
        }

        private void drawDischargeDoor(Graphics2D g2) {
            Graphics2D doorG = (Graphics2D) g2.create();
            doorG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            double angleDeg = lerp(
                    DISCHARGE_DOOR_CLOSED_ANGLE_DEG,
                    DISCHARGE_DOOR_OPEN_ANGLE_DEG,
                    dischargeDoorProgress
            );

            drawDoorPanel(
                    doorG,
                    DISCHARGE_DOOR_PIVOT_X,
                    DISCHARGE_DOOR_PIVOT_Y,
                    DISCHARGE_DOOR_PANEL_LENGTH,
                    DISCHARGE_DOOR_PANEL_WIDTH,
                    angleDeg,
                    new Color(160, 160, 160),
                    new Color(70, 70, 70),
                    new Color(140, 86, 41),
                    true
            );

            doorG.dispose();
        }

        private void drawDoorPanel(
                Graphics2D g2,
                int pivotX,
                int pivotY,
                int length,
                int width,
                double angleDeg,
                Color fillColor,
                Color borderColor,
                Color pivotColor,
                boolean squarePivot) {

            Graphics2D panelG = (Graphics2D) g2.create();
            panelG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            panelG.translate(pivotX, pivotY);
            panelG.rotate(Math.toRadians(angleDeg));

            int x = -width / 2;
            int y = -length;

            GradientPaint gp = new GradientPaint(
                    x, y, brighter(fillColor, 26),
                    x + width, y + length, darker(fillColor, 18)
            );
            panelG.setPaint(gp);
            panelG.fillRoundRect(x, y, width, length, 6, 6);

            panelG.setColor(borderColor);
            panelG.setStroke(new BasicStroke(1.8f));
            panelG.drawRoundRect(x, y, width, length, 6, 6);

            panelG.setColor(new Color(255, 255, 255, 65));
            panelG.setStroke(new BasicStroke(1.0f));
            panelG.drawLine(x + 2, y + 4, x + 2, y + length - 6);

            panelG.dispose();

            int diameter = 8;
            int r = diameter / 2;
            g2.setColor(new Color(155, 155, 155));
            g2.fillOval(pivotX - r, pivotY - r, diameter, diameter);
            g2.setColor(new Color(92, 92, 92));
            g2.setStroke(new BasicStroke(1.6f));
            g2.drawOval(pivotX - r, pivotY - r, diameter, diameter);
        }

        private Color brighter(Color c, int delta) {
            return new Color(
                    Math.min(255, c.getRed() + delta),
                    Math.min(255, c.getGreen() + delta),
                    Math.min(255, c.getBlue() + delta)
            );
        }

        private Color darker(Color c, int delta) {
            return new Color(
                    Math.max(0, c.getRed() - delta),
                    Math.max(0, c.getGreen() - delta),
                    Math.max(0, c.getBlue() - delta)
            );
        }

        private void drawStylizedDoor(
                Graphics2D g2,
                double progress,
                int closedX,
                int topGuideY,
                int closedLeafTopY,
                int closedLeafBottomY,
                int bottomGuideY,
                int openTopX,
                int openTopY,
                int openBottomX,
                int openBottomY) {

            Graphics2D doorG = (Graphics2D) g2.create();
            doorG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            doorG.setStroke(new BasicStroke(DOOR_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            doorG.setColor(new Color(47, 100, 214));

            doorG.drawLine(closedX, topGuideY, closedX, topGuideY + DOOR_GUIDE_TOP_LEN);
            doorG.drawLine(closedX, bottomGuideY, closedX, bottomGuideY + DOOR_GUIDE_BOTTOM_LEN);

            int leafTopX = (int) Math.round(lerp(closedX, openTopX, progress));
            int leafTopY = (int) Math.round(lerp(closedLeafTopY, openTopY, progress));
            int leafBottomX = (int) Math.round(lerp(closedX, openBottomX, progress));
            int leafBottomY = (int) Math.round(lerp(closedLeafBottomY, openBottomY, progress));

            doorG.drawLine(leafTopX, leafTopY, leafBottomX, leafBottomY);

            if (progress > 0.04) {
                drawDoorHinge(doorG, leafTopX, leafTopY);
                drawDoorHinge(doorG, leafBottomX, leafBottomY);
            }

            doorG.dispose();
        }

        private void drawDoorHinge(Graphics2D g2, int cx, int cy) {
            int r = DOOR_HINGE_DIAMETER / 2;
            g2.setColor(new Color(245, 245, 245));
            g2.fillOval(cx - r, cy - r, DOOR_HINGE_DIAMETER, DOOR_HINGE_DIAMETER);
            g2.setColor(new Color(132, 132, 132));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(cx - r, cy - r, DOOR_HINGE_DIAMETER, DOOR_HINGE_DIAMETER);
            g2.setColor(new Color(47, 100, 214));
            g2.setStroke(new BasicStroke(DOOR_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        }
        private double lerp(double start, double end, double progress) {
            return start + ((end - start) * progress);
        }

        static class StepLeftBorder implements Border {
            private final Color color;
            private final int thickness;

            StepLeftBorder(Color color, int thickness) {
                this.color = color;
                this.thickness = thickness;
            }

            @Override
            public Insets getBorderInsets(Component c) {
                return new Insets(thickness, thickness, thickness, 0);
            }

            @Override
            public boolean isBorderOpaque() {
                return true;
            }

            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(color);
                g2.setStroke(new BasicStroke(thickness));

                // lato sinistro
                g2.drawLine(x, y, x, y + h - 1);

                // lato superiore
                g2.drawLine(x, y, x + w - 1, y);

                // lato inferiore
                g2.drawLine(x, y + h - 1, x + w - 1, y + h - 1);

                g2.dispose();
            }
        }
        
        
        
        private static class NumericDocumentFilter extends DocumentFilter {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                    throws BadLocationException {
                if (string != null) {
                    string = string.replaceAll("[^0-9]", "");
                }
                super.insertString(fb, offset, string, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                if (text != null) {
                    text = text.replaceAll("[^0-9]", "");
                }
                super.replace(fb, offset, length, text, attrs);
            }
        }

        private static class DecimalDocumentFilter extends DocumentFilter {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                    throws BadLocationException {
                if (string == null) return;
                replace(fb, offset, 0, string, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                String current = fb.getDocument().getText(0, fb.getDocument().getLength());
                String safeText = text == null ? "" : text.replace(',', '.');
                String candidate = current.substring(0, offset) + safeText + current.substring(offset + length);

                if (candidate.isEmpty() || candidate.matches("\\d*(\\.\\d*)?")) {
                    super.replace(fb, offset, length, safeText, attrs);
                }
            }
        }

        private static class SignedIntegerDocumentFilter extends DocumentFilter {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                    throws BadLocationException {
                if (string == null) return;
                replace(fb, offset, 0, string, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                String current = fb.getDocument().getText(0, fb.getDocument().getLength());
                String candidate = current.substring(0, offset)
                        + (text == null ? "" : text)
                        + current.substring(offset + length);
                if (candidate.matches("-?\\d*")) {
                    super.replace(fb, offset, length, text, attrs);
                }
            }
        }

        private static class DosageButton extends JButton {
            private Color visualBackground = LIQUID_BUTTON_OFF;
            private Color visualForeground = Color.BLACK;

            DosageButton(String text) {
                super(text);
                setFocusPainted(false);
                setFont(new Font("Arial", Font.BOLD, 15));
                setForeground(Color.BLACK);
                setContentAreaFilled(false);
                setOpaque(false);
                setBorderPainted(false);
                setMargin(new Insets(0, 0, 0, 0));
                setHorizontalTextPosition(SwingConstants.CENTER);
                setVerticalTextPosition(SwingConstants.CENTER);
            }

            void setVisualState(Color bg, Color fg) {
                if (bg != null) {
                    this.visualBackground = bg;
                }
                if (fg != null) {
                    this.visualForeground = fg;
                }
                repaint();
            }

            boolean isVisualOn() {
                return LIQUID_BUTTON_ON.equals(visualBackground);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color bg = visualBackground;
                Color border = LIQUID_BUTTON_BORDER;

                if (getModel().isPressed()) {
                    bg = darken(bg, 0.10f);
                } else if (getModel().isRollover() && isEnabled()) {
                    bg = brighten(bg, 0.06f);
                }

                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

                g2.setColor(border);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

                g2.setColor(new Color(255, 255, 255, 70));
                g2.drawRoundRect(1, 1, getWidth() - 3, Math.max(8, getHeight() / 2), 9, 9);

                g2.dispose();

                setForeground(visualForeground);
                super.paintComponent(g);
            }

            @Override
            protected void paintBorder(Graphics g) {
            }

            private static Color darken(Color c, float factor) {
                factor = Math.max(0f, Math.min(1f, factor));
                int r = Math.max(0, Math.round(c.getRed() * (1f - factor)));
                int g = Math.max(0, Math.round(c.getGreen() * (1f - factor)));
                int b = Math.max(0, Math.round(c.getBlue() * (1f - factor)));
                return new Color(r, g, b);
            }

            private static Color brighten(Color c, float factor) {
                factor = Math.max(0f, Math.min(1f, factor));
                int r = Math.min(255, Math.round(c.getRed() + (255 - c.getRed()) * factor));
                int g = Math.min(255, Math.round(c.getGreen() + (255 - c.getGreen()) * factor));
                int b = Math.min(255, Math.round(c.getBlue() + (255 - c.getBlue()) * factor));
                return new Color(r, g, b);
            }
        }
    }

    enum IconType {
        ACK, WARNING, DIAMOND, BARS, BAG
    }

    static class ThinIconBorder implements Border {
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

    static class BevelRectBorder implements Border {
        private final Color borderColor;
        private final int width;

        BevelRectBorder(Color borderColor, int width) {
            this.borderColor = borderColor;
            this.width = width;
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(width, width, width, width);
        }

        @Override
        public boolean isBorderOpaque() {
            return true;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(borderColor);
            for (int i = 0; i < width; i++) {
                g2.drawRect(x + i, y + i, w - 1 - i * 2, h - 1 - i * 2);
            }
            g2.setColor(new Color(150, 150, 150));
            g2.drawLine(x + 1, y + 1, x + w - 3, y + 1);
            g2.drawLine(x + 1, y + 1, x + 1, y + h - 3);
            g2.setColor(new Color(126, 126, 126));
            g2.drawLine(x + 1, y + h - 2, x + w - 2, y + h - 2);
            g2.drawLine(x + w - 2, y + 1, x + w - 2, y + h - 2);
            g2.dispose();
        }
    }

    static class DataFieldBorder implements Border {
        private final boolean red;
        private final boolean blue;

        DataFieldBorder(boolean red, boolean blue) {
            this.red = red;
            this.blue = blue;
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(3, 3, 3, 3);
        }

        @Override
        public boolean isBorderOpaque() {
            return true;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            if (blue) {
                g2.setColor(new Color(44, 94, 255));
                g2.setStroke(new BasicStroke(3));
                g2.drawRect(x + 1, y + 1, w - 3, h - 3);
                g2.setColor(new Color(150, 150, 150));
                g2.setStroke(new BasicStroke(1));
                g2.drawRect(x + 4, y + 4, w - 9, h - 9);
            } else if (red) {
                g2.setColor(new Color(209, 0, 0));
                g2.setStroke(new BasicStroke(2));
                g2.drawRect(x + 1, y + 1, w - 3, h - 3);
            } else {
                g2.setColor(new Color(118, 118, 118));
                float[] dash = new float[]{3f, 3f};
                g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, dash, 0));
                g2.drawRect(x + 1, y + 1, w - 3, h - 3);
            }
            g2.dispose();
        }
    }

    /**
     * Bordo nero a tre lati: top, left, bottom — il lato destro è omesso.
     * Usato per l'etichetta "STEP N" che si affianca al campo valore.
     */
    static class ThreeSidedBorder implements Border {
        private final Color color;
        private final int thickness;

        ThreeSidedBorder(Color color, int thickness) {
            this.color = color;
            this.thickness = thickness;
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(thickness, thickness, thickness, 0);
        }

        @Override
        public boolean isBorderOpaque() {
            return true;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(color);
            g2.setStroke(new BasicStroke(thickness));

            int half = thickness / 2;

            // lato sinistro
            g2.drawLine(x + half, y, x + half, y + h - 1);
            // lato superiore
            g2.drawLine(x, y + half, x + w - 1, y + half);
            // lato inferiore
            g2.drawLine(x, y + h - 1 - half, x + w - 1, y + h - 1 - half);
            // lato destro: NON disegnato

            g2.dispose();
        }
    }
}
