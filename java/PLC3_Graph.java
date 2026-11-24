import SVC.DBBamburyReader;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;


public class PLC3_Graph extends JFrame {

    private static final String BAG_ICON_RESOURCE = "/src/icons/Exit.png";
    private static final String BAG_ICON_WINDOWS_FALLBACK =
            "C:\\Users\\public\\IdeaProjects\\Banbury2\\src\\main\\resources\\icons\\Exit.png";

    private BufferedImage bagIconImage;

    public PLC3_Graph() {
        PLC3_StepRecorder.start();

        setTitle("BANBURY 2 - TREND");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setUndecorated(true);

        loadBagIconImage();

        GraphPanel panel = new GraphPanel(this, bagIconImage);
        setContentPane(panel);

        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new PLC3_Graph().setVisible(true);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(
                        null,
                        "Errore apertura PLC3_Graph: " + ex.getMessage(),
                        "Errore",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }

    private void loadBagIconImage() {
        try (InputStream is = PLC3_Graph.class.getResourceAsStream(BAG_ICON_RESOURCE)) {
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

    static class GraphPanel extends JPanel {

        // Connessione, utente e password sono gestiti dal package SVC (DBBamburyReader.openDataConnection()).
        private static final String DB_TABLE = "DBO.DATAS_B2";

        private static final String COL_DATE = "date_record";
        private static final String COL_TIME = "time_record";
        private static final String COL_TEMP = "TEMPERATURE";
        private static final String COL_MODE = "Mode";
        private static final String COL_AMP = "Amperage";
        private static final String COL_RPM = "Vitesse";
        // Testo in DATAS_B2: "Haut", "Bas" o "Mouvement" (come in PLC3_Main)
        private static final String COL_PISTON = "Position_piston";

        private static final String MARKER_DIR_PATH = "\\\\neo\\x-service\\Banbury\\Graphique";
        private static final String MARKER_FILE_PATH = MARKER_DIR_PATH + "\\plc3_markers.csv";


        private static final int MAX_TEMP = 200;
        private static final Duration TOTAL_HISTORY = Duration.ofDays(2);
        private static final Duration[] ZOOM_WINDOWS = new Duration[] {
                Duration.ofMinutes(15),
                Duration.ofMinutes(30),
                Duration.ofHours(1),
                Duration.ofHours(2),
                Duration.ofHours(5),
                Duration.ofHours(8),
                Duration.ofHours(12),
                Duration.ofHours(24),
                Duration.ofHours(36),
                Duration.ofHours(48)
        };
        private static final int DEFAULT_ZOOM_INDEX = 4;

        private static final int BAG_BUTTON_W = 78;
        private static final int BAG_BUTTON_H = 60;

        private static final Color BG_OUTER = new Color(0xF2, 0xF3, 0xF6);
        private static final Color GRID_MAJOR = new Color(0xCF, 0xD3, 0xDC);
        private static final Color GRID_MINOR = new Color(0xE9, 0xEB, 0xF0);
        private static final Color AXIS_COLOR = new Color(0x60, 0x68, 0x70);
        private static final Color TEXT_COLOR = new Color(0x20, 0x28, 0x30);
        private static final Color LINE_COLOR = new Color(0xE0, 0x00, 0x00);
        private static final Color POINT_COLOR = new Color(0xB0, 0x50, 0x00);
        private static final Color VALUE_BOX = Color.WHITE;
        private static final Color BTN_BORDER = new Color(0x80, 0x88, 0x90);

        private final PLC3_Graph parentFrame;
        private final BufferedImage bagIconImage;

        private Timer clockTimer;
        private Timer dbRefreshTimer;
        private Timer stepDisplayRefreshTimer;

        private JLabel lblTitle;
        private JLabel lblDate;
        private JLabel lblValue;
        private JLabel lblRange;
        private JLabel lblMeasure;

        private JButton btnBackFast;
        private JButton btnBack;
        private JButton btnForward;
        private JButton btnForwardFast;
        private JButton btnZoomIn;
        private JButton btnZoomOut;
        private JButton btnLive;
        private JButton btnMeasure;
        private JButton btnBag;
        private JCheckBox chkShowSteps;
        private boolean showStepsOnCurve = true;

        private boolean measureMode = false;
        private LocalDateTime measurePoint1 = null;
        private LocalDateTime measurePoint2 = null;
        private int draggedMeasurePoint = 0;
        private boolean measurePointWasDragged = false;

        private final List<TempPoint> history = new ArrayList<>();
        // Informazioni aggiuntive come in B1: corrente e giri (da DATAS_B2), con asse destro
        private JCheckBox chkAmp;
        private int hoverX = -1; // posizione del mouse per il righello con i valori
        private JCheckBox chkRpm;
        private JCheckBox chkPiston;
        private JCheckBox chkAuto;
        private static final Color AMP_COLOR = new Color(0x1F, 0x5F, 0xD0);
        private static final Color RPM_COLOR = new Color(0x1E, 0x8C, 0x1E);
        private static final Color PISTON_COLOR = new Color(0x80, 0x30, 0xB0);
        private final List<ModePoint> modeHistory = new ArrayList<>();
        private final List<RecipePoint> recipeTimeline = new ArrayList<>();
        private volatile boolean recipeMonitorAlive = false;
        private final List<TempMarker> r2008Markers = new ArrayList<>();

        private LocalDateTime visibleEnd;
        private boolean liveMode = true;
        private int zoomIndex = DEFAULT_ZOOM_INDEX;
        private volatile String currentDbMode = "";
        private final AtomicBoolean markerLoadInProgress = new AtomicBoolean(false);

        private static final List<TempMarker> SHARED_R2008_MARKERS =
                Collections.synchronizedList(new ArrayList<TempMarker>());

        GraphPanel(PLC3_Graph parentFrame, BufferedImage bagIconImage) {
            this.parentFrame = parentFrame;
            this.bagIconImage = bagIconImage;

            setLayout(null);
            setBackground(BG_OUTER);

            visibleEnd = LocalDateTime.now();

            buildUi();
            installResizeHandling();
            startClock();
            loadHistoryFromDatabase();
            loadRecipeTimelineFromDatabase();
            loadMarkersFromFile();
            startDatabaseRefresh();
            startStepDisplayRefresh();
        }

        private void buildUi() {
            lblTitle = new JLabel("BANBURY 2 - TREND", SwingConstants.LEFT);
            lblTitle.setFont(new Font("Consolas", Font.BOLD, 26));
            lblTitle.setForeground(TEXT_COLOR);
            add(lblTitle);

            lblDate = new JLabel("", SwingConstants.RIGHT);
            lblDate.setFont(new Font("Consolas", Font.BOLD, 18));
            lblDate.setForeground(TEXT_COLOR);
            add(lblDate);

            lblValue = new JLabel("TEMP: --.- °C", SwingConstants.LEFT);
            lblValue.setFont(new Font("Consolas", Font.BOLD, 20));
            lblValue.setForeground(POINT_COLOR);
            lblValue.setOpaque(true);
            lblValue.setBackground(VALUE_BOX);
            lblValue.setBorder(BorderFactory.createLineBorder(GRID_MAJOR, 1));
            add(lblValue);

            lblRange = new JLabel("", SwingConstants.CENTER);
            lblRange.setFont(new Font("Consolas", Font.BOLD, 15));
            lblRange.setForeground(TEXT_COLOR);
            lblRange.setOpaque(true);
            lblRange.setBackground(VALUE_BOX);
            lblRange.setBorder(BorderFactory.createLineBorder(GRID_MAJOR, 1));
            add(lblRange);

            lblMeasure = new JLabel("MESURE: OFF", SwingConstants.CENTER);
            lblMeasure.setFont(new Font("Consolas", Font.BOLD, 15));
            lblMeasure.setForeground(POINT_COLOR);
            lblMeasure.setOpaque(true);
            lblMeasure.setBackground(VALUE_BOX);
            lblMeasure.setBorder(BorderFactory.createLineBorder(GRID_MAJOR, 1));
            add(lblMeasure);

            chkShowSteps = new JCheckBox("STEP");
            chkShowSteps.setFont(new Font("Consolas", Font.BOLD, 15));
            chkShowSteps.setForeground(TEXT_COLOR);
            chkShowSteps.setBackground(BG_OUTER);
            chkShowSteps.setOpaque(false);
            chkShowSteps.setFocusPainted(false);
            chkShowSteps.setSelected(true);
            chkShowSteps.addActionListener(e -> {
                showStepsOnCurve = chkShowSteps.isSelected();
                repaint();
            });
            add(chkShowSteps);

            chkAmp = createExtraCheck("CORRENTE", AMP_COLOR, false);
            chkRpm = createExtraCheck("GIRI", RPM_COLOR, false);
            chkPiston = createExtraCheck("PISTONE", PISTON_COLOR, false);
            chkAuto = createExtraCheck("SCALA AUTO", TEXT_COLOR, true);

            btnBackFast = createNavButton("", () -> moveWindowBackward(getBigStep()));
            btnBack = createNavButton("", () -> moveWindowBackward(getSmallStep()));
            btnForward = createNavButton("", () -> moveWindowForward(getSmallStep()));
            btnForwardFast = createNavButton("", () -> moveWindowForward(getBigStep()));
            btnZoomIn = createNavButton("ZOOM +", this::zoomIn);
            btnZoomOut = createNavButton("ZOOM -", this::zoomOut);
            btnLive = createNavButton("LIVE", this::goLive);
            btnMeasure = createNavButton("MESURE", this::toggleMeasureMode);
            btnBag = createBagButton();

            add(btnBackFast);
            add(btnBack);
            add(btnForward);
            add(btnForwardFast);
            add(btnZoomIn);
            add(btnZoomOut);
            add(btnLive);
            add(btnMeasure);
            add(btnBag);

            installMeasureMouseHandler();

            updateDateLabel();
            updateNavigationButtons();
            updateRangeLabel();
        }

        private void installResizeHandling() {
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    layoutComponents();
                    repaint();
                }
            });

            SwingUtilities.invokeLater(() -> {
                layoutComponents();
                repaint();
            });
        }

        private JCheckBox createExtraCheck(String text, Color fg, boolean selected) {
            JCheckBox c = new JCheckBox(text, selected);
            c.setFont(new Font("Consolas", Font.BOLD, 14));
            c.setForeground(fg);
            c.setOpaque(false);
            c.setFocusPainted(false);
            c.addActionListener(e -> repaint());
            add(c);
            return c;
        }

        private void layoutComponents() {
            int w = getWidth();
            int h = getHeight();

            lblTitle.setBounds(22, 15, 430, 35);
            lblDate.setBounds(w - 290, 15, 260, 35);
            lblValue.setBounds(22, 58, 220, 32);
            lblRange.setBounds(255, 58, 500, 32);
            lblMeasure.setBounds(Math.max(1350, w - 230), 58, 200, 32);
            chkShowSteps.setBounds(770, 58, 90, 32);
            chkAmp.setBounds(865, 58, 130, 32);
            chkRpm.setBounds(1000, 58, 80, 32);
            chkPiston.setBounds(1090, 58, 100, 32);
            chkAuto.setBounds(1195, 58, 150, 32);

            int yButtons = h - 60;
            btnBackFast.setBounds(24, yButtons, 100, 42);
            btnBack.setBounds(132, yButtons, 100, 42);
            btnForward.setBounds(240, yButtons, 100, 42);
            btnForwardFast.setBounds(348, yButtons, 100, 42);
            btnZoomIn.setBounds(468, yButtons, 100, 42);
            btnZoomOut.setBounds(576, yButtons, 100, 42);
            btnLive.setBounds(684, yButtons, 110, 42);
            btnMeasure.setBounds(806, yButtons, 130, 42);

            btnBag.setBounds(w - 25 - BAG_BUTTON_W, h - 20 - BAG_BUTTON_H, BAG_BUTTON_W, BAG_BUTTON_H);
        }


        private Duration getVisibleWindow() {
            return ZOOM_WINDOWS[zoomIndex];
        }

        private Duration getSmallStep() {
            long minutes = Math.max(1L, getVisibleWindow().toMinutes() / 10L);
            return Duration.ofMinutes(minutes);
        }

        private Duration getBigStep() {
            long minutes = Math.max(5L, getVisibleWindow().toMinutes() / 4L);
            return Duration.ofMinutes(minutes);
        }

        private void updateNavigationButtons() {
            btnBackFast.setText("<< " + formatDurationCompact(getBigStep()));
            btnBack.setText("< " + formatDurationCompact(getSmallStep()));
            btnForward.setText(formatDurationCompact(getSmallStep()) + " >");
            btnForwardFast.setText(formatDurationCompact(getBigStep()) + " >>");
        }

        private void zoomIn() {
            if (zoomIndex <= 0) {
                return;
            }
            if (liveMode) {
                visibleEnd = LocalDateTime.now();
            }
            zoomIndex--;
            updateNavigationButtons();
            updateRangeLabel();
            repaint();
        }

        private void zoomOut() {
            if (zoomIndex >= ZOOM_WINDOWS.length - 1) {
                return;
            }
            if (liveMode) {
                visibleEnd = LocalDateTime.now();
            }
            zoomIndex++;
            updateNavigationButtons();
            updateRangeLabel();
            repaint();
        }

        private String formatDurationCompact(Duration duration) {
            long minutes = duration.toMinutes();
            if (minutes % (24L * 60L) == 0) {
                return (minutes / (24L * 60L)) + "D";
            }
            if (minutes % 60L == 0) {
                return (minutes / 60L) + "H";
            }
            return minutes + "M";
        }

        private void toggleMeasureMode() {
            measureMode = !measureMode;
            measurePoint1 = null;
            measurePoint2 = null;
            draggedMeasurePoint = 0;
            measurePointWasDragged = false;
            setCursor(Cursor.getDefaultCursor());
            updateMeasureLabel();
            repaint();
        }

        private void installMeasureMouseHandler() {
            MouseAdapter measureMouseAdapter = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    hoverX = e.getX();
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverX = -1;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (!measureMode || !SwingUtilities.isLeftMouseButton(e)) {
                        return;
                    }

                    draggedMeasurePoint = findMeasurePointAt(e.getPoint());
                    measurePointWasDragged = false;

                    if (draggedMeasurePoint != 0) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (!measureMode || draggedMeasurePoint == 0) {
                        return;
                    }

                    if (moveMeasurePointToMouse(draggedMeasurePoint, e.getPoint())) {
                        measurePointWasDragged = true;
                        updateMeasureLabel();
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (draggedMeasurePoint != 0) {
                        draggedMeasurePoint = 0;
                        setCursor(Cursor.getDefaultCursor());
                    }
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!measureMode || !SwingUtilities.isLeftMouseButton(e)) {
                        return;
                    }

                    if (measurePointWasDragged) {
                        measurePointWasDragged = false;
                        return;
                    }

                    if (findMeasurePointAt(e.getPoint()) != 0) {
                        return;
                    }

                    handleMeasureClick(e.getPoint());
                }
            };

            addMouseListener(measureMouseAdapter);
            addMouseMotionListener(measureMouseAdapter);
        }

        private void handleMeasureClick(Point clickPoint) {
            Rectangle chart = getChartBounds();
            if (!chart.contains(clickPoint)) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }

            LocalDateTime clickedTime = timeFromChartX(clickPoint.x, chart);
            LocalDateTime curveTime = findNearestCurveTime(clickedTime);

            if (curveTime == null) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }

            if (measurePoint1 == null || measurePoint2 != null) {
                measurePoint1 = curveTime;
                measurePoint2 = null;
            } else {
                measurePoint2 = curveTime;
            }

            updateMeasureLabel();
            repaint();
        }

        private int findMeasurePointAt(Point mousePoint) {
            if (mousePoint == null) {
                return 0;
            }

            Rectangle chart = getChartBounds();
            Point point1 = measurePoint1 != null ? findNearestCurvePoint(measurePoint1, chart) : null;
            Point point2 = measurePoint2 != null ? findNearestCurvePoint(measurePoint2, chart) : null;

            int hitRadius = 14;
            double bestDistance = Double.MAX_VALUE;
            int hitPoint = 0;

            if (point1 != null) {
                double d = point1.distanceSq(mousePoint);
                if (d <= hitRadius * hitRadius && d < bestDistance) {
                    bestDistance = d;
                    hitPoint = 1;
                }
            }

            if (point2 != null) {
                double d = point2.distanceSq(mousePoint);
                if (d <= hitRadius * hitRadius && d < bestDistance) {
                    hitPoint = 2;
                }
            }

            return hitPoint;
        }

        private boolean moveMeasurePointToMouse(int pointNumber, Point mousePoint) {
            Rectangle chart = getChartBounds();
            if (mousePoint == null || history.isEmpty()) {
                return false;
            }

            int clampedX = Math.max(chart.x, Math.min(chart.x + chart.width, mousePoint.x));
            LocalDateTime targetTime = timeFromChartX(clampedX, chart);
            LocalDateTime curveTime = findNearestCurveTime(targetTime);

            if (curveTime == null) {
                return false;
            }

            if (pointNumber == 1) {
                if (curveTime.equals(measurePoint1)) {
                    return false;
                }
                measurePoint1 = curveTime;
                return true;
            }

            if (pointNumber == 2) {
                if (curveTime.equals(measurePoint2)) {
                    return false;
                }
                measurePoint2 = curveTime;
                return true;
            }

            return false;
        }

        /** Righello al passaggio del mouse (come in B1): riga verticale con i valori in quel punto. */
        private void paintRuler(Graphics2D g2, int x, int y, int w, int h) {
            if (measureMode || hoverX < x || hoverX > x + w || history.isEmpty()) {
                return;
            }
            Rectangle chart = new Rectangle(x, y, w, h);
            LocalDateTime t = timeFromChartX(hoverX, chart);
            TempPoint p = findNearestVisibleTempPoint(t);
            if (p == null || Math.abs(Duration.between(p.time, t).toMinutes()) > 2) {
                return; // nessun dato vicino (buco di registrazione)
            }
            List<String> rows = new ArrayList<>();
            rows.add(p.time.format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")));
            rows.add(String.format(Locale.ITALY, "Temperatura: %.1f °C", p.temperature));
            if (chkAmp.isSelected()) {
                rows.add(String.format(Locale.ITALY, "Corrente: %.0f A", p.amp));
            }
            if (chkRpm.isSelected()) {
                rows.add(String.format(Locale.ITALY, "Giri: %.0f rpm", p.rpm));
            }
            if (chkPiston.isSelected()) {
                rows.add("Pistone: " + pistonText(p.piston));
            }
            String mode = null;
            for (ModePoint m : modeHistory) {
                if (m.time.isAfter(p.time)) {
                    break;
                }
                mode = m.mode;
            }
            if (mode != null && isManualMode(mode)) {
                rows.add("MANUALE");
            } else {
                String recipe = null;
                for (RecipePoint r : recipeTimeline) {
                    if (r.time.isAfter(p.time)) {
                        break;
                    }
                    recipe = r.description;
                }
                if (recipe != null && !recipe.isEmpty()) {
                    rows.add("Ricetta: " + recipe);
                }
            }

            g2.setColor(new Color(0x40, 0x48, 0x50));
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(hoverX, y, hoverX, y + h);

            g2.setFont(new Font("Consolas", Font.PLAIN, 12));
            FontMetrics fm = g2.getFontMetrics();
            int bw = 0;
            for (String r : rows) {
                bw = Math.max(bw, fm.stringWidth(r));
            }
            bw += 14;
            int bh = 16 * rows.size() + 6;
            int bx = hoverX + 12 + bw > x + w ? hoverX - 12 - bw : hoverX + 12;
            g2.setColor(new Color(255, 255, 225, 240));
            g2.fillRect(bx, y + 6, bw, bh);
            g2.setColor(Color.GRAY);
            g2.drawRect(bx, y + 6, bw, bh);
            g2.setColor(Color.BLACK);
            for (int i = 0; i < rows.size(); i++) {
                g2.drawString(rows.get(i), bx + 6, y + 20 + i * 16);
            }
        }

        private Rectangle getChartBounds() {
            int left = 95;
            int right = 115;
            int top = 110;
            int bottom = 120;

            int chartX = left;
            int chartY = top;
            int chartW = Math.max(200, getWidth() - left - right);
            int chartH = Math.max(200, getHeight() - top - bottom);
            return new Rectangle(chartX, chartY, chartW, chartH);
        }

        private LocalDateTime timeFromChartX(int mouseX, Rectangle chart) {
            double ratio = (mouseX - chart.x) / (double) Math.max(1, chart.width);
            ratio = Math.max(0.0, Math.min(1.0, ratio));
            LocalDateTime start = visibleEnd.minus(getVisibleWindow());
            long nanos = (long) (getVisibleWindow().toNanos() * ratio);
            return start.plusNanos(nanos);
        }

        private LocalDateTime findNearestCurveTime(LocalDateTime targetTime) {
            TempPoint closest = findNearestVisibleTempPoint(targetTime);
            return closest != null ? closest.time : null;
        }

        private TempPoint findNearestVisibleTempPoint(LocalDateTime targetTime) {
            if (history.isEmpty() || targetTime == null) {
                return null;
            }

            LocalDateTime start = visibleEnd.minus(getVisibleWindow());
            TempPoint closest = null;
            long bestDiff = Long.MAX_VALUE;

            for (TempPoint p : history) {
                if (p.time.isBefore(start) || p.time.isAfter(visibleEnd)) {
                    continue;
                }

                long diff = Math.abs(Duration.between(p.time, targetTime).toMillis());
                if (diff < bestDiff) {
                    bestDiff = diff;
                    closest = p;
                }
            }

            return closest;
        }

        private Point findNearestCurvePoint(LocalDateTime targetTime, Rectangle chart) {
            TempPoint closest = findNearestVisibleTempPoint(targetTime);
            if (closest == null || chart == null) {
                return null;
            }

            LocalDateTime start = visibleEnd.minus(getVisibleWindow());
            long totalMillis = Math.max(1L, getVisibleWindow().toMillis());
            long elapsedMillis = Duration.between(start, closest.time).toMillis();
            elapsedMillis = Math.max(0L, Math.min(totalMillis, elapsedMillis));

            int px = chart.x + (int) Math.round((elapsedMillis / (double) totalMillis) * chart.width);

            double ratioY = clampTemperature(closest.temperature) / MAX_TEMP;
            int py = chart.y + chart.height - (int) Math.round(ratioY * chart.height);

            return new Point(px, py);
        }

        private void updateMeasureLabel() {
            if (lblMeasure == null) {
                return;
            }

            if (!measureMode) {
                lblMeasure.setText("MESURE: OFF");
                if (btnMeasure != null) btnMeasure.setText("MESURE");
                return;
            }

            if (btnMeasure != null) btnMeasure.setText("MESURE ON");

            if (measurePoint1 == null) {
                lblMeasure.setText("MESURE: cliquez point 1");
            } else if (measurePoint2 == null) {
                lblMeasure.setText("MESURE: cliquez point 2");
            } else {
                Duration d = Duration.between(measurePoint1, measurePoint2).abs();
                lblMeasure.setText("DELTA: " + formatDurationHms(d));
            }
        }

        private String formatDurationHms(Duration duration) {
            long seconds = Math.abs(duration.getSeconds());
            long hours = seconds / 3600L;
            long minutes = (seconds % 3600L) / 60L;
            long sec = seconds % 60L;
            return String.format(Locale.US, "%02dH%02dM%02dS", hours, minutes, sec);
        }

        private int getXAxisTickCount() {
            long minutes = getVisibleWindow().toMinutes();
            if (minutes <= 60) return 6;
            if (minutes <= 300) return 5;
            if (minutes <= 720) return 6;
            return 8;
        }

        private DateTimeFormatter getXAxisFormatter() {
            return getVisibleWindow().toHours() >= 12
                    ? DateTimeFormatter.ofPattern("dd/MM HH:mm")
                    : DateTimeFormatter.ofPattern("HH:mm");
        }

        private JButton createNavButton(String text, Runnable action) {
            JButton b = new JButton(text) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setPaint(new GradientPaint(0, 0, Color.WHITE, 0, getHeight(),
                            getModel().isPressed() ? new Color(0xB8, 0xBE, 0xC8) : new Color(0xD4, 0xD8, 0xE0)));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            b.setFont(new Font("Consolas", Font.BOLD, 15));
            b.setFocusPainted(false);
            b.setContentAreaFilled(false);
            b.setForeground(TEXT_COLOR);
            b.setBorder(BorderFactory.createLineBorder(BTN_BORDER, 1));
            b.addActionListener(e -> action.run());
            return b;
        }

        private void startClock() {
            clockTimer = new Timer(1000, e -> updateDateLabel());
            clockTimer.setInitialDelay(0);
            clockTimer.start();
        }

        private void updateDateLabel() {
            lblDate.setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        }

        private void startDatabaseRefresh() {
            dbRefreshTimer = new Timer(5000, e -> {
                loadHistoryFromDatabase();
                    loadRecipeTimelineFromDatabase();
                if (liveMode) {
                    visibleEnd = LocalDateTime.now();
                    updateRangeLabel();
                }
                repaint();
            });
            dbRefreshTimer.setInitialDelay(5000);
            dbRefreshTimer.start();
        }

        private void startStepDisplayRefresh() {
            stepDisplayRefreshTimer = new Timer(1000, e -> {
                // Mantiene il grafico LIVE davvero agganciato all'orario corrente.
                // Dopo uno zoom, se visibleEnd restava fermo, i nuovi step venivano registrati
                // ma non disegnati perche' cadevano oltre il bordo destro della finestra visibile.
                if (liveMode) {
                    visibleEnd = LocalDateTime.now();
                    updateRangeLabel();
                } else if (visibleEnd != null && visibleEnd.isAfter(LocalDateTime.now())) {
                    visibleEnd = LocalDateTime.now();
                    updateRangeLabel();
                }

                loadMarkersFromFile();
                syncMarkersFromRecorder();
                repaint();
            });
            stepDisplayRefreshTimer.setInitialDelay(0);
            stepDisplayRefreshTimer.start();
        }

        private void syncMarkersFromRecorder() {
            synchronized (SHARED_R2008_MARKERS) {
                r2008Markers.clear();
                r2008Markers.addAll(SHARED_R2008_MARKERS);
            }
            trimOldMarkers();
        }

        private void trimOldMarkers() {
            LocalDateTime threshold = LocalDateTime.now().minus(TOTAL_HISTORY);
            Iterator<TempMarker> it = r2008Markers.iterator();
            while (it.hasNext()) {
                if (it.next().time.isBefore(threshold)) {
                    it.remove();
                }
            }
        }


        private void loadMarkersFromFile() {
            if (!markerLoadInProgress.compareAndSet(false, true)) {
                return;
            }

            new Thread(() -> {
                List<TempMarker> loaded = new ArrayList<>();
                try {
                    File file = new File(MARKER_FILE_PATH);
                    if (!file.exists()) {
                        return;
                    }

                    LocalDateTime threshold = LocalDateTime.now().minus(TOTAL_HISTORY);
                    List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

                    for (String line : lines) {
                        if (line == null || line.trim().isEmpty()) {
                            continue;
                        }

                        String[] parts = line.split(";", 3);
                        if (parts.length != 3) {
                            continue;
                        }

                        try {
                            LocalDateTime time = LocalDateTime.parse(parts[0].trim());
                            double temp = clampTemperature(Double.parseDouble(parts[1].trim()));
                            String step = parts[2].trim();

                            if (!time.isBefore(threshold) && !step.isEmpty()) {
                                loaded.add(new TempMarker(time, temp, step));
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    loaded.sort((a, b) -> a.time.compareTo(b.time));

                    synchronized (SHARED_R2008_MARKERS) {
                        SHARED_R2008_MARKERS.clear();
                        SHARED_R2008_MARKERS.addAll(loaded);
                    }

                    SwingUtilities.invokeLater(() -> {
                        syncMarkersFromRecorder();
                        repaint();
                    });

                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    markerLoadInProgress.set(false);
                }
            }, "plc3-graph-marker-load").start();
        }

        private void goLive() {
            liveMode = true;
            visibleEnd = LocalDateTime.now();
            updateRangeLabel();
            repaint();
        }

        private void moveWindowBackward(Duration step) {
            liveMode = false;

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime minEnd = now.minus(TOTAL_HISTORY).plus(getVisibleWindow());

            visibleEnd = visibleEnd.minus(step);
            if (visibleEnd.isBefore(minEnd)) {
                visibleEnd = minEnd;
            }

            updateRangeLabel();
            repaint();
        }

        private void moveWindowForward(Duration step) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime maxEnd = now;

            visibleEnd = visibleEnd.plus(step);
            if (visibleEnd.isAfter(maxEnd)) {
                visibleEnd = maxEnd;
                liveMode = true;
            } else {
                liveMode = false;
            }

            updateRangeLabel();
            repaint();
        }

        private void updateRangeLabel() {
            LocalDateTime now = LocalDateTime.now();
            if (visibleEnd == null || visibleEnd.isAfter(now)) {
                visibleEnd = now;
            }

            LocalDateTime start = visibleEnd.minus(getVisibleWindow());
            String txt = start.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
                    + "  ->  "
                    + visibleEnd.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));
            if (liveMode) {
                txt += "   [LIVE]";
            }
            lblRange.setText(txt);
        }

        private void loadRecipeTimelineFromDatabase() {
            new Thread(() -> {
                List<RecipePoint> newRecipeData = new ArrayList<>();

                try {
                    List<DBBamburyReader.RecipePLCInfo> rows = DBBamburyReader.readRecipePLCTimelineWithBaseline(2);

                    for (DBBamburyReader.RecipePLCInfo row : rows) {
                        if (row == null || row.dataOra == null) {
                            continue;
                        }

                        String description = row.descrizione == null ? "" : row.descrizione.trim();

                        if (!newRecipeData.isEmpty()) {
                            RecipePoint last = newRecipeData.get(newRecipeData.size() - 1);
                            if (last.recipeNumber == row.ricetta && last.description.equals(description)) {
                                continue;
                            }
                        }

                        newRecipeData.add(new RecipePoint(row.dataOra, row.ricetta, description));
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                final boolean monitorAliveNow = RecipeMonitorHealth.isAlive();
                SwingUtilities.invokeLater(() -> {
                    recipeMonitorAlive = monitorAliveNow;
                    recipeTimeline.clear();
                    recipeTimeline.addAll(newRecipeData);
                    repaint();
                });
            }, "plc3-graph-recipe-db-load").start();
        }

        private String normalizeMode(String rawMode) {
            if (rawMode == null) {
                return "";
            }

            String mode = rawMode.trim();
            if (mode.equalsIgnoreCase("Auto")) {
                return "Auto";
            }
            if (isManualMode(mode)) {
                return "Manu";
            }
            return mode;
        }

        // Caricamento unico e incrementale di temperatura, corrente, giri e modo da DATAS_B2:
        // la prima volta gli ultimi 2 giorni, poi (ogni 5 s) solo le righe nuove. Prima si rileggevano
        // due volte tutti i 2 giorni ogni 5 secondi, anche con la query precedente ancora in corso:
        // il server chiudeva la connessione ("Connection reset").
        private final AtomicBoolean historyLoadInProgress = new AtomicBoolean(false);
        private volatile LocalDateTime lastHistoryTs = null;
        private volatile String lastModeSeen = null;
        private volatile boolean historyErrorShown = false;

        private void loadHistoryFromDatabase() {
            if (!historyLoadInProgress.compareAndSet(false, true)) {
                return; // il caricamento precedente non e' ancora finito
            }
            final LocalDateTime since = lastHistoryTs;
            new Thread(() -> {
                List<TempPoint> newData = new ArrayList<>();
                List<ModePoint> newModes = new ArrayList<>();
                boolean ok = false;
                String lastMode = since == null ? null : lastModeSeen;

                String sql =
                        "SELECT " +
                        "DATEADD(SECOND, DATEDIFF(SECOND, 0, " + COL_TIME + "), CAST(" + COL_DATE + " AS DATETIME)) AS TS, " +
                        COL_TEMP + ", " + COL_AMP + ", " + COL_RPM + ", " + COL_PISTON + ", " + COL_MODE +
                        " FROM " + DB_TABLE +
                        " WHERE " + COL_DATE + " >= ?" +
                        " ORDER BY TS";

                try (
                        Connection conn = DBBamburyReader.openDataConnection();
                        PreparedStatement ps = conn.prepareStatement(sql)
                ) {
                    java.time.LocalDate from = since == null
                            ? java.time.LocalDate.now().minusDays(2)
                            : since.toLocalDate();
                    ps.setDate(1, java.sql.Date.valueOf(from));
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Timestamp ts = rs.getTimestamp("TS");
                            if (ts == null) {
                                continue;
                            }
                            LocalDateTime time = ts.toLocalDateTime();
                            if (since != null && !time.isAfter(since)) {
                                continue;
                            }
                            newData.add(new TempPoint(time, clampTemperature(rs.getDouble(COL_TEMP)),
                                    rs.getDouble(COL_AMP), rs.getDouble(COL_RPM), pistonValue(rs.getString(COL_PISTON))));

                            String mode = normalizeMode(rs.getString(COL_MODE));
                            if (!mode.isEmpty() && !mode.equals(lastMode)) {
                                newModes.add(new ModePoint(time, mode));
                                lastMode = mode;
                            }
                        }
                    }
                    ok = true;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    if (!historyErrorShown && history.isEmpty()) {
                        historyErrorShown = true; // una sola finestra, non una ogni 5 secondi
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                                parentFrame,
                                "Errore lettura database:\n" + ex.getMessage(),
                                "Errore DB",
                                JOptionPane.ERROR_MESSAGE
                        ));
                    }
                }

                final boolean loaded = ok;
                final String modeNow = lastMode;
                SwingUtilities.invokeLater(() -> {
                    try {
                        if (loaded) {
                            if (since == null) {
                                history.clear();
                                modeHistory.clear();
                            }
                            history.addAll(newData);
                            modeHistory.addAll(newModes);
                            if (!newData.isEmpty()) {
                                lastHistoryTs = newData.get(newData.size() - 1).time;
                            } else if (since == null) {
                                lastHistoryTs = LocalDateTime.now().minusDays(2);
                            }
                            lastModeSeen = modeNow;
                            // si tengono solo gli ultimi 2 giorni
                            LocalDateTime limit = LocalDateTime.now().minus(TOTAL_HISTORY);
                            int cut = 0;
                            while (cut < history.size() && history.get(cut).time.isBefore(limit)) {
                                cut++;
                            }
                            if (cut > 0) {
                                history.subList(0, cut).clear();
                            }
                            if (!modeHistory.isEmpty()) {
                                currentDbMode = modeHistory.get(modeHistory.size() - 1).mode;
                            }
                        }
                        updateCurrentValueLabel();
                        if (liveMode) {
                            visibleEnd = LocalDateTime.now();
                            updateRangeLabel();
                        }
                        repaint();
                    } finally {
                        historyLoadInProgress.set(false);
                    }
                });

            }, "plc3-graph-db-load").start();
        }

        /** Haut = 1.0 (in alto nel grafico), Mouvement = 0.5 (in transito), Bas = 0.0 (in basso). */
        private static double pistonValue(String raw) {
            if (raw == null) {
                return 0.5;
            }
            String v = raw.trim().toLowerCase(Locale.ROOT);
            if (v.startsWith("haut")) return 1.0;
            if (v.startsWith("bas")) return 0.0;
            return 0.5;
        }

        private static String pistonText(double v) {
            return v >= 0.99 ? "HAUT" : v <= 0.01 ? "BAS" : "MOUVEMENT";
        }

        private double clampTemperature(double value) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return 0.0;
            }
            if (value < 0) return 0.0;
            if (value > MAX_TEMP) return MAX_TEMP;
            return value;
        }

        private void updateCurrentValueLabel() {
            if (history.isEmpty()) {
                lblValue.setText("TEMP: --.- °C");
                return;
            }

            TempPoint last = history.get(history.size() - 1);
            lblValue.setText(String.format(java.util.Locale.US, "TEMP: %.1f °C", last.temperature));
        }

        private JButton createBagButton() {
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

            button.setRolloverEnabled(true);
            button.setMargin(new Insets(0, 0, 0, 0));
            button.setFocusable(false);
            button.setFocusPainted(false);
            button.setContentAreaFilled(false);
            button.setOpaque(false);
            button.setBorderPainted(true);
            button.setBorder(new ThinIconBorder());
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.setToolTipText("BAG");

            button.addActionListener(e -> {
                try {
                    stopTimers();

                    PLC3_Main main = new PLC3_Main();
                    main.setExtendedState(JFrame.MAXIMIZED_BOTH);
                    main.setVisible(true);

                    parentFrame.dispose();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(
                            parentFrame,
                            "Errore apertura PLC3_Main: " + ex.getMessage(),
                            "Errore",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            });

            return button;
        }

        private void stopTimers() {
            if (clockTimer != null) clockTimer.stop();
            if (dbRefreshTimer != null) dbRefreshTimer.stop();
            if (stepDisplayRefreshTimer != null) stepDisplayRefreshTimer.stop();
            // Il registratore STEP è statico e indipendente dalla finestra:
            // non viene fermato qui, così continua a salvare R02008 anche quando esci dal grafico.
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

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            paintBackground(g2);
            paintChart(g2);

            g2.dispose();
        }

        private void paintBackground(Graphics2D g2) {
            g2.setColor(BG_OUTER);
            g2.fillRect(0, 0, getWidth(), getHeight());
        }

        private void paintChart(Graphics2D g2) {
            int left = 95;
            int right = 115;
            int top = 110;
            int bottom = 120;

            int chartX = left;
            int chartY = top;
            int chartW = Math.max(200, getWidth() - left - right);
            int chartH = Math.max(200, getHeight() - top - bottom);

            GradientPaint gp = new GradientPaint(
                    chartX, chartY, Color.WHITE,
                    chartX, chartY + chartH, new Color(0xF8, 0xF9, 0xFB)
            );
            g2.setPaint(gp);
            g2.fillRect(chartX, chartY, chartW, chartH);

            g2.setColor(AXIS_COLOR);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRect(chartX, chartY, chartW, chartH);

            paintGrid(g2, chartX, chartY, chartW, chartH);
            paintModeTimeline(g2, chartX, chartY, chartW, chartH);
            paintAxesLabels(g2, chartX, chartY, chartW, chartH);
            paintExtraLine(g2, chartX, chartY, chartW, chartH, 0);
            paintExtraLine(g2, chartX, chartY, chartW, chartH, 1);
            paintExtraLine(g2, chartX, chartY, chartW, chartH, 2);
            paintTemperatureLine(g2, chartX, chartY, chartW, chartH);
            if (showStepsOnCurve) {
                paintR2008Markers(g2, chartX, chartY, chartW, chartH);
            }
            paintMeasureSelection(g2, chartX, chartY, chartW, chartH);
            paintRuler(g2, chartX, chartY, chartW, chartH);
        }

        private void paintGrid(Graphics2D g2, int x, int y, int w, int h) {
            g2.setFont(new Font("Consolas", Font.PLAIN, 12));

            for (int i = 0; i <= 20; i++) {
                int yy = y + (int) Math.round((double) i / 20.0 * h);
                g2.setColor(i % 2 == 0 ? GRID_MAJOR : GRID_MINOR);
                g2.drawLine(x, yy, x + w, yy);
            }

            for (int i = 0; i <= 20; i++) {
                int xx = x + (int) Math.round((double) i / 20.0 * w);
                g2.setColor(i % 4 == 0 ? GRID_MAJOR : GRID_MINOR);
                g2.drawLine(xx, y, xx, y + h);
            }
        }

        private void paintAxesLabels(Graphics2D g2, int x, int y, int w, int h) {
            g2.setColor(LINE_COLOR);
            g2.setFont(new Font("Consolas", Font.BOLD, 13));

            for (int i = 0; i <= 10; i++) {
                int temp = MAX_TEMP - (i * 20);
                int yy = y + (int) Math.round((double) i / 10.0 * h);
                String s = String.valueOf(temp);
                int sw = g2.getFontMetrics().stringWidth(s);
                g2.drawString(s, x - 18 - sw, yy + 5);
            }

            g2.setColor(LINE_COLOR);
            g2.drawString("°C", x - 42, y - 10);

            LocalDateTime start = visibleEnd.minus(getVisibleWindow());

            g2.setColor(TEXT_COLOR);
            g2.setFont(new Font("Consolas", Font.PLAIN, 12));

            int tickCount = Math.max(2, getXAxisTickCount());
            long totalMillis = Math.max(1L, getVisibleWindow().toMillis());
            DateTimeFormatter xFormatter = getXAxisFormatter();

            for (int i = 0; i <= tickCount; i++) {
                double ratio = (double) i / (double) tickCount;
                int xx = x + (int) Math.round(ratio * w);
                LocalDateTime tickTime = start.plusNanos((long) (totalMillis * 1_000_000.0 * ratio));
                String label = tickTime.format(xFormatter);
                int sw = g2.getFontMetrics().stringWidth(label);
                g2.drawString(label, xx - sw / 2, y + h + 22);
            }

            g2.setFont(new Font("Consolas", Font.BOLD, 13));
            g2.setColor(POINT_COLOR);
            String windowLabel = "TEMPS FENETRE (" + formatDurationCompact(getVisibleWindow()) + ")";
            int windowSw = g2.getFontMetrics().stringWidth(windowLabel);
            g2.drawString(windowLabel, x + (w - windowSw) / 2, y + h + 48);
        }

        private void paintModeTimeline(Graphics2D g2, int x, int y, int w, int h) {
            int barMarginX = 2;
            int barH = 22;
            int barY = y + h - barH - 6;
            int barX = x + barMarginX;
            int barW = Math.max(50, w - (barMarginX * 2));

            LocalDateTime start = visibleEnd.minus(getVisibleWindow());
            long totalMillis = Math.max(1L, getVisibleWindow().toMillis());

            g2.setColor(new Color(0xDD, 0xE6, 0xDD));
            g2.fillRect(barX, barY, barW, barH);

            if (recipeTimeline.isEmpty()) {
                g2.setColor(new Color(0x90, 0x98, 0x90));
                g2.drawRect(barX, barY, barW, barH);
                return;
            }

            // Barra verde: ricetta corrente. La prima riga caricata puo' essere precedente
            // ai due giorni visibili e serve da baseline fino al cambio successivo.
            // Qui disegniamo solo fondo e bordi: le scritte delle ricette vengono aggiunte
            // dopo il manuale, spezzando ogni ricetta nei soli tratti non manuali.
            for (int i = 0; i < recipeTimeline.size(); i++) {
                RecipePoint current = recipeTimeline.get(i);
                LocalDateTime segStart = current.time;
                LocalDateTime segEnd = (i + 1 < recipeTimeline.size()) ? recipeTimeline.get(i + 1).time : visibleEnd;
                paintTimelineSegment(g2, barX, barY, barW, barH, start, visibleEnd, totalMillis,
                        segStart, segEnd,
                        new Color(0, 115, 60), new Color(80, 210, 120),
                        "", "", false);
            }

            // Sovrascrittura rossa: quando il modo manuale e' attivo, copre il verde.
            for (int i = 0; i < modeHistory.size(); i++) {
                ModePoint current = modeHistory.get(i);
                if (!isManualMode(current.mode)) {
                    continue;
                }

                LocalDateTime segStart = current.time;
                LocalDateTime segEnd = (i + 1 < modeHistory.size()) ? modeHistory.get(i + 1).time : visibleEnd;
                paintTimelineSegment(g2, barX, barY, barW, barH, start, visibleEnd, totalMillis,
                        segStart, segEnd,
                        new Color(190, 25, 25), new Color(255, 95, 95),
                        "MANUALE", "M", true);
            }

            // Scritte verdi delle ricette: restano visibili tra due manuali consecutivi.
            // Se il tratto e' corto viene usato solo il numero della ricetta.
            paintRecipeLabelsBetweenManualSegments(g2, barX, barY, barW, barH, start, visibleEnd, totalMillis);

            // Solo porzione LIVE: lo storico resta intatto, ma non suggeriamo
            // che l'ultima ricetta sia ancora valida se il monitor e' fermo.
            if (liveMode && !recipeMonitorAlive) {
                long windowMs = Math.max(1L, totalMillis);
                long unavailableMs = Math.min(windowMs, 60000L);
                int unavailableX = barX + (int) Math.round((1.0 - unavailableMs / (double) windowMs) * barW);
                g2.setColor(new Color(95, 95, 95));
                g2.fillRect(unavailableX, barY, Math.max(1, barX + barW - unavailableX), barH);
                g2.setFont(new Font("Consolas", Font.BOLD, 11));
                g2.setColor(Color.WHITE);
                if (barX + barW - unavailableX >= 68) {
                    g2.drawString("-----------", unavailableX + 2, barY + 15);
                }
            }

            g2.setColor(new Color(0, 160, 120));
            g2.drawRect(barX, barY, barW, barH);
        }

        private void paintRecipeLabelsBetweenManualSegments(Graphics2D g2,
                                                            int barX, int barY, int barW, int barH,
                                                            LocalDateTime visibleStart, LocalDateTime visibleEndTime,
                                                            long totalMillis) {
            for (int i = 0; i < recipeTimeline.size(); i++) {
                RecipePoint recipe = recipeTimeline.get(i);
                LocalDateTime recipeStart = recipe.time;
                LocalDateTime recipeEnd = (i + 1 < recipeTimeline.size()) ? recipeTimeline.get(i + 1).time : visibleEndTime;

                if (recipeStart == null || recipeEnd == null) {
                    continue;
                }
                if (!recipeEnd.isAfter(visibleStart) || !recipeStart.isBefore(visibleEndTime)) {
                    continue;
                }

                LocalDateTime freeStart = recipeStart.isBefore(visibleStart) ? visibleStart : recipeStart;
                LocalDateTime limitEnd = recipeEnd.isAfter(visibleEndTime) ? visibleEndTime : recipeEnd;

                for (int m = 0; m < modeHistory.size(); m++) {
                    ModePoint mode = modeHistory.get(m);
                    if (!isManualMode(mode.mode)) {
                        continue;
                    }

                    LocalDateTime manualStart = mode.time;
                    LocalDateTime manualEnd = (m + 1 < modeHistory.size()) ? modeHistory.get(m + 1).time : visibleEndTime;
                    if (manualStart == null || manualEnd == null) {
                        continue;
                    }
                    if (!manualEnd.isAfter(freeStart) || !manualStart.isBefore(limitEnd)) {
                        continue;
                    }

                    LocalDateTime labelEnd = manualStart.isAfter(limitEnd) ? limitEnd : manualStart;
                    drawTimelineLabelForTimeRange(g2, barX, barY, barW, barH,
                            visibleStart, totalMillis, freeStart, labelEnd,
                            recipe.getFullLabel(), String.valueOf(recipe.recipeNumber), false);

                    if (manualEnd.isAfter(freeStart)) {
                        freeStart = manualEnd.isAfter(limitEnd) ? limitEnd : manualEnd;
                    }
                    if (!freeStart.isBefore(limitEnd)) {
                        break;
                    }
                }

                drawTimelineLabelForTimeRange(g2, barX, barY, barW, barH,
                        visibleStart, totalMillis, freeStart, limitEnd,
                        recipe.getFullLabel(), String.valueOf(recipe.recipeNumber), false);
            }
        }

        private void drawTimelineLabelForTimeRange(Graphics2D g2,
                                                   int barX, int barY, int barW, int barH,
                                                   LocalDateTime visibleStart,
                                                   long totalMillis,
                                                   LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                                   String fullLabel, String shortLabel,
                                                   boolean forceLightText) {
            if (rangeStart == null || rangeEnd == null || !rangeEnd.isAfter(rangeStart)) {
                return;
            }

            long startMillis = Math.max(0L, Duration.between(visibleStart, rangeStart).toMillis());
            long endMillis = Math.min(totalMillis, Duration.between(visibleStart, rangeEnd).toMillis());
            if (endMillis <= startMillis) {
                return;
            }

            int segX = barX + (int) Math.round((startMillis / (double) totalMillis) * barW);
            int segX2 = barX + (int) Math.round((endMillis / (double) totalMillis) * barW);
            int segW = Math.max(0, segX2 - segX);

            drawTimelineLabel(g2, segX, barY, segW, barH, fullLabel, shortLabel, forceLightText);
        }

        private void paintTimelineSegment(Graphics2D g2,
                                          int barX, int barY, int barW, int barH,
                                          LocalDateTime visibleStart, LocalDateTime visibleEndTime,
                                          long totalMillis,
                                          LocalDateTime segStart, LocalDateTime segEnd,
                                          Color fillColor, Color borderColor,
                                          String fullLabel, String shortLabel,
                                          boolean forceLightText) {
            if (segStart == null || segEnd == null) {
                return;
            }

            if (!segEnd.isAfter(visibleStart) || !segStart.isBefore(visibleEndTime)) {
                return;
            }

            LocalDateTime drawStart = segStart.isBefore(visibleStart) ? visibleStart : segStart;
            LocalDateTime drawEnd = segEnd.isAfter(visibleEndTime) ? visibleEndTime : segEnd;

            long startMillis = Math.max(0L, Duration.between(visibleStart, drawStart).toMillis());
            long endMillis = Math.min(totalMillis, Duration.between(visibleStart, drawEnd).toMillis());
            int segX = barX + (int) Math.round((startMillis / (double) totalMillis) * barW);
            int segX2 = barX + (int) Math.round((endMillis / (double) totalMillis) * barW);
            int segW = Math.max(2, segX2 - segX);

            g2.setColor(fillColor);
            g2.fillRect(segX, barY, segW, barH);

            g2.setColor(borderColor);
            g2.drawRect(segX, barY, segW, barH);

            drawTimelineLabel(g2, segX, barY, segW, barH, fullLabel, shortLabel, forceLightText);
        }

        private void drawTimelineLabel(Graphics2D g2, int segX, int barY, int segW, int barH,
                                       String fullLabel, String shortLabel, boolean forceLightText) {
            if (segW < 14) {
                return;
            }

            g2.setFont(new Font("Consolas", Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();

            String label = fullLabel == null ? "" : fullLabel.trim();
            String compact = shortLabel == null ? "" : shortLabel.trim();

            if (label.isEmpty()) {
                label = compact;
            }

            if (fm.stringWidth(label) + 10 > segW) {
                label = compact;
            }

            if (label.isEmpty() || fm.stringWidth(label) + 8 > segW) {
                return;
            }

            int tx = segX + (segW - fm.stringWidth(label)) / 2;
            int ty = barY + ((barH - fm.getHeight()) / 2) + fm.getAscent();
            g2.setColor(forceLightText ? new Color(255, 245, 245) : new Color(215, 255, 215));
            g2.drawString(label, tx, ty);
        }

        private boolean isManualMode(String mode) {
            if (mode == null) {
                return false;
            }

            String value = mode.trim();
            return "manu".equalsIgnoreCase(value)
                    || "manual".equalsIgnoreCase(value)
                    || "manuel".equalsIgnoreCase(value);
        }

        private Color getModeColor(String mode) {
            if ("Auto".equalsIgnoreCase(mode)) {
                return new Color(0, 110, 60);
            }
            if (isManualMode(mode)) {
                return new Color(190, 25, 25);
            }
            return new Color(85, 85, 85);
        }

        private String getModeLabel(String mode) {
            if ("Auto".equalsIgnoreCase(mode)) {
                return "AUTO";
            }
            if (isManualMode(mode)) {
                return "MANUAL";
            }
            return mode == null || mode.trim().isEmpty() ? "N/A" : mode.trim().toUpperCase(Locale.ROOT);
        }

        /**
         * Il DB da solo uno stato (Haut/Mouvement/Bas): durante "Mouvement" la posizione reale e' ignota.
         * Qui si ricostruisce una curva di transito: da Haut a Bas (o viceversa) il valore scorre
         * con un andamento dolce (smoothstep) lungo tutta la durata di "Mouvement".
         */
        private double[] smoothPiston(List<TempPoint> vis, double[] v) {
            double[] o = v.clone();
            int n = v.length;
            int i = 0;
            while (i < n) {
                if (v[i] > 0.01 && v[i] < 0.99) {
                    int j = i;
                    while (j < n && v[j] > 0.01 && v[j] < 0.99) {
                        j++;
                    }
                    Double from = i > 0 ? v[i - 1] : null;
                    Double to = j < n ? v[j] : null;
                    if (from == null && to == null) {
                        i = j;
                        continue;
                    }
                    if (from == null) from = 1.0 - to;
                    boolean open = to == null;
                    if (open) to = 1.0 - from;
                    LocalDateTime t0 = vis.get(Math.max(0, i - 1)).time;
                    long span = open ? 8000L : Math.max(1L, Duration.between(t0, vis.get(j).time).toMillis());
                    for (int k = i; k < j; k++) {
                        double f = Math.min(open ? 0.95 : 1.0,
                                Duration.between(t0, vis.get(k).time).toMillis() / (double) span);
                        f = Math.max(0.0, f);
                        double sm = f * f * (3 - 2 * f);
                        o[k] = from + (to - from) * sm;
                    }
                    i = j;
                } else {
                    i++;
                }
            }
            return o;
        }

        /** Serie aggiuntive (0 = corrente, 1 = giri, 2 = posizione pistone) con asse destro, scala automatica opzionale. */
        private void paintExtraLine(Graphics2D g2, int x, int y, int w, int h, int series) {
            JCheckBox chk = series == 0 ? chkAmp : series == 1 ? chkRpm : chkPiston;
            if (!chk.isSelected() || history.isEmpty()) {
                return;
            }
            Color col = series == 0 ? AMP_COLOR : series == 1 ? RPM_COLOR : PISTON_COLOR;
            LocalDateTime start = visibleEnd.minus(getVisibleWindow());
            long total = getVisibleWindow().toMillis();
            List<TempPoint> vis = new ArrayList<>();
            for (TempPoint p : history) {
                if (!p.time.isBefore(start) && !p.time.isAfter(visibleEnd)) {
                    vis.add(p);
                }
            }
            if (vis.isEmpty()) {
                return;
            }
            double[] vals = new double[vis.size()];
            for (int i = 0; i < vals.length; i++) {
                vals[i] = series == 0 ? vis.get(i).amp : series == 1 ? vis.get(i).rpm : vis.get(i).piston;
            }
            if (series == 2) {
                vals = smoothPiston(vis, vals);
            }
            double max = series == 2 ? 1.0 : chkAuto.isSelected() ? PLC3_TrendScale.autoMax(vals) : (series == 0 ? 400 : 100);
            g2.setStroke(new BasicStroke(1.6f));
            g2.setColor(col);
            Point prev = null;
            for (int i = 0; i < vals.length; i++) {
                long el = Math.max(0, Math.min(total, Duration.between(start, vis.get(i).time).toMillis()));
                int px = x + (int) Math.round((double) el / total * w);
                int py = y + h - (int) Math.round(Math.max(0, Math.min(max, vals[i])) / max * h);
                if (prev != null) {
                    g2.drawLine(prev.x, prev.y, px, py);
                }
                prev = new Point(px, py);
            }
            // asse destro: corrente subito a destra del grafico, giri piu' a destra
            g2.setFont(new Font("Consolas", Font.BOLD, 12));
            int ax = x + w + (series == 0 ? 6 : series == 1 ? 40 : 78);
            g2.drawString(series == 0 ? "A" : series == 1 ? "rpm" : "pist", ax, y - 6);
            if (series == 2) {
                g2.drawString("HAUT", ax, y + 4);
                g2.drawString("MOUV", ax, y + h / 2 + 4);
                g2.drawString("BAS", ax, y + h + 4);
                return;
            }
            for (int i = 0; i <= 4; i++) {
                double v = max * i / 4.0;
                int ty = y + h - (int) Math.round(h * i / 4.0);
                g2.drawString(v >= 10 ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.US, "%.1f", v),
                        ax, ty + 4);
            }
        }

        private void paintTemperatureLine(Graphics2D g2, int x, int y, int w, int h) {
            if (history.isEmpty()) {
                return;
            }

            LocalDateTime start = visibleEnd.minus(getVisibleWindow());
            List<TempPoint> visiblePoints = new ArrayList<>();

            for (TempPoint p : history) {
                if ((p.time.isEqual(start) || p.time.isAfter(start)) &&
                        (p.time.isEqual(visibleEnd) || p.time.isBefore(visibleEnd))) {
                    visiblePoints.add(p);
                }
            }

            if (visiblePoints.isEmpty()) {
                return;
            }

            g2.setColor(LINE_COLOR);
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            Point prev = null;
            Point last = null;

            long totalMillis = getVisibleWindow().toMillis();

            for (TempPoint p : visiblePoints) {
                long elapsedMillis = Duration.between(start, p.time).toMillis();
                if (elapsedMillis < 0) elapsedMillis = 0;
                if (elapsedMillis > totalMillis) elapsedMillis = totalMillis;

                double ratioX = (double) elapsedMillis / (double) totalMillis;
                int px = x + (int) Math.round(ratioX * w);

                double temp = clampTemperature(p.temperature);
                double ratioY = temp / MAX_TEMP;
                int py = y + h - (int) Math.round(ratioY * h);

                Point cur = new Point(px, py);

                if (prev != null) {
                    g2.drawLine(prev.x, prev.y, cur.x, cur.y);
                }

                prev = cur;
                last = cur;
            }

            if (last != null) {
                g2.setColor(POINT_COLOR);
                g2.fillOval(last.x - 5, last.y - 5, 10, 10);
                g2.setColor(LINE_COLOR);
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(last.x - 7, last.y - 7, 14, 14);

                TempPoint lastPoint = visiblePoints.get(visiblePoints.size() - 1);
                String valueText = String.format(java.util.Locale.US, "%.1f °C", lastPoint.temperature);
                g2.setFont(new Font("Consolas", Font.BOLD, 12));
                int tw = g2.getFontMetrics().stringWidth(valueText);

                int boxX = Math.min(last.x + 12, x + w - tw - 16);
                int boxY = Math.max(last.y - 24, y + 16);

                g2.setColor(new Color(255, 255, 255, 235));
                g2.fillRoundRect(boxX - 4, boxY - 13, tw + 8, 18, 6, 6);
                g2.setColor(GRID_MAJOR);
                g2.drawRoundRect(boxX - 4, boxY - 13, tw + 8, 18, 6, 6);
                g2.setColor(POINT_COLOR);
                g2.drawString(valueText, boxX, boxY);
            }
        }



        private double getTemperatureAt(LocalDateTime time) {
            if (history.isEmpty() || time == null) {
                return 0.0;
            }

            TempPoint closest = null;
            long bestDiff = Long.MAX_VALUE;

            for (TempPoint p : history) {
                long diff = Math.abs(Duration.between(p.time, time).toMillis());
                if (diff < bestDiff) {
                    bestDiff = diff;
                    closest = p;
                }
            }

            return closest != null ? clampTemperature(closest.temperature) : 0.0;
        }

        private void paintMeasureSelection(Graphics2D g2, int x, int y, int w, int h) {
            if (!measureMode) {
                return;
            }

            Rectangle chart = new Rectangle(x, y, w, h);
            Point point1 = measurePoint1 != null ? findNearestCurvePoint(measurePoint1, chart) : null;
            Point point2 = measurePoint2 != null ? findNearestCurvePoint(measurePoint2, chart) : null;

            g2.setFont(new Font("Consolas", Font.BOLD, 13));
            g2.setStroke(new BasicStroke(1.6f));

            drawMeasurePoint(g2, point1, x, y, h, "1");
            drawMeasurePoint(g2, point2, x, y, h, "2");

            if (point1 != null && point2 != null && measurePoint1 != null && measurePoint2 != null) {
                g2.setColor(new Color(0x60, 0x60, 0x00));
                g2.drawLine(point1.x, point1.y, point2.x, point2.y);

                String text = formatDurationHms(Duration.between(measurePoint1, measurePoint2).abs());
                int textW = g2.getFontMetrics().stringWidth(text);
                int midX = (point1.x + point2.x) / 2;
                int midY = Math.min(point1.y, point2.y) - 18;
                midX = Math.max(x + 4 + textW / 2, Math.min(x + w - 4 - textW / 2, midX));
                midY = Math.max(y + 18, midY);

                g2.setColor(new Color(255, 255, 200, 240));
                g2.fillRoundRect(midX - textW / 2 - 6, midY - 14, textW + 12, 19, 6, 6);
                g2.setColor(new Color(0x60, 0x40, 0x00));
                g2.drawRoundRect(midX - textW / 2 - 6, midY - 14, textW + 12, 19, 6, 6);
                g2.drawString(text, midX - textW / 2, midY);
            }
        }

        private void drawMeasurePoint(Graphics2D g2, Point p, int x, int y, int h, String label) {
            if (p == null) {
                return;
            }

            g2.setColor(new Color(0x60, 0x60, 0x00));
            g2.drawLine(p.x, y, p.x, y + h);
            g2.fillOval(p.x - 6, p.y - 6, 12, 12);
            g2.setColor(Color.WHITE);
            g2.fillOval(p.x - 3, p.y - 3, 6, 6);
            g2.setColor(new Color(0x60, 0x40, 0x00));
            g2.drawString(label, p.x + 8, Math.max(y + 14, p.y - 8));
        }

        private void paintR2008Markers(Graphics2D g2, int x, int y, int w, int h) {
            if (r2008Markers.isEmpty()) {
                return;
            }

            LocalDateTime start = visibleEnd.minus(getVisibleWindow());
            long totalMillis = getVisibleWindow().toMillis();

            g2.setFont(new Font("Consolas", Font.BOLD, 12));

            for (TempMarker marker : r2008Markers) {
                if (marker.time.isBefore(start) || marker.time.isAfter(visibleEnd)) {
                    continue;
                }

                long elapsedMillis = Duration.between(start, marker.time).toMillis();
                if (elapsedMillis < 0) elapsedMillis = 0;
                if (elapsedMillis > totalMillis) elapsedMillis = totalMillis;

                double ratioX = (double) elapsedMillis / (double) totalMillis;
                int px = x + (int) Math.round(ratioX * w);

                double markerTemp = getTemperatureAt(marker.time);
                double ratioY = clampTemperature(markerTemp) / MAX_TEMP;
                int py = y + h - (int) Math.round(ratioY * h);

                g2.setColor(new Color(0xFF, 0xB4, 0x3C));
                g2.fillOval(px - 4, py - 4, 8, 8);
                g2.setColor(new Color(0xC0, 0x70, 0x00));
                g2.drawOval(px - 6, py - 6, 12, 12);

                String label = marker.stepValue;
                int textX = Math.min(px + 10, x + w - g2.getFontMetrics().stringWidth(label) - 6);
                int textY = Math.max(py - 8, y + 14);

                g2.setColor(new Color(0xFF, 0xF2, 0xD0, 235));
                int tw = g2.getFontMetrics().stringWidth(label);
                g2.fillRoundRect(textX - 3, textY - 11, tw + 6, 16, 6, 6);
                g2.setColor(new Color(0x80, 0x45, 0x00));
                g2.drawString(label, textX, textY);
            }
        }

        private static class TempMarker {
            final LocalDateTime time;
            final double temperature;
            final String stepValue;

            TempMarker(LocalDateTime time, double temperature, String stepValue) {
                this.time = time;
                this.temperature = temperature;
                this.stepValue = stepValue;
            }
        }

        private static class RecipePoint {
            final LocalDateTime time;
            final int recipeNumber;
            final String description;

            RecipePoint(LocalDateTime time, int recipeNumber, String description) {
                this.time = time;
                this.recipeNumber = recipeNumber;
                this.description = description == null ? "" : description.trim();
            }

            String getFullLabel() {
                if (description.isEmpty()) {
                    return String.valueOf(recipeNumber);
                }
                return recipeNumber + " - " + description;
            }
        }

        private static class ModePoint {
            final LocalDateTime time;
            final String mode;

            ModePoint(LocalDateTime time, String mode) {
                this.time = time;
                this.mode = mode;
            }
        }

        private static class TempPoint {
            final LocalDateTime time;
            final double temperature;
            final double amp;
            final double rpm;
            final double piston;

            TempPoint(LocalDateTime time, double temperature) {
                this(time, temperature, 0, 0, 0);
            }

            TempPoint(LocalDateTime time, double temperature, double amp, double rpm, double piston) {
                this.piston = piston;
                this.time = time;
                this.temperature = temperature;
                this.amp = amp;
                this.rpm = rpm;
            }
        }
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

    static class ThinGreenBorder implements Border {
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
            g2.setColor(new Color(0, 120, 0));
            g2.drawRect(x, y, w - 1, h - 1);
            g2.setColor(new Color(90, 180, 90));
            g2.drawLine(x + 1, y + 1, x + w - 2, y + 1);
            g2.drawLine(x + 1, y + 1, x + 1, y + h - 2);
            g2.dispose();
        }
    }
}