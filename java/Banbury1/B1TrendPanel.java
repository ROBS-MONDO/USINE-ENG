package Banbury1;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.Border;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Trend di Banbury 1 con lo stesso aspetto e gli stessi pulsanti del grafico di B2 (PLC3_Graph):
 * << 75M, < 30M, 30M >, 75M >>, ZOOM +, ZOOM -, LIVE, MESURE, EXIT, "TEMPS FENETRE", fascia ricetta/modo in basso.
 * I dati vengono dalla memoria di 2 giorni (B1TrendStore). SOLA LETTURA.
 */
public final class B1TrendPanel extends JPanel {

    static final Duration[] ZOOM_WINDOWS = {
        Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofHours(2),
        Duration.ofHours(5), Duration.ofHours(8), Duration.ofHours(12), Duration.ofHours(24),
        Duration.ofHours(36), Duration.ofHours(48)
    };
    static final int DEFAULT_ZOOM_INDEX = 4;
    static final Duration TOTAL_HISTORY = Duration.ofDays(2);
    static final int MAX_TEMP = 200;

    // grafica chiara di B1; i pulsanti e le informazioni sono quelli di B2 (PLC3_Graph)
    private static final Color BG_OUTER = new Color(0xF2, 0xF3, 0xF6);
    private static final Color GRID_MAJOR = new Color(0xCF, 0xD3, 0xDC);
    private static final Color GRID_MINOR = new Color(0xE9, 0xEB, 0xF0);
    private static final Color AXIS_COLOR = new Color(0x60, 0x68, 0x70);
    private static final Color TEXT_COLOR = new Color(0x20, 0x28, 0x30);
    private static final Color LINE_COLOR = new Color(0xE0, 0x00, 0x00);
    private static final Color POINT_COLOR = new Color(0xB0, 0x50, 0x00);
    private static final Color VALUE_BOX = Color.WHITE;
    private static final Color BTN_BORDER = new Color(0x80, 0x88, 0x90);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final B1TrendStore store;
    private final List<B1TrendChart.Series> extra = new ArrayList<>();
    private final Runnable onExit;

    private final JLabel lblTitle = new JLabel("BANBURY 1 - TREND", SwingConstants.LEFT);
    private final JLabel lblDate = new JLabel("", SwingConstants.RIGHT);
    private final JLabel lblValue = new JLabel("TEMP: --.- \u00b0C", SwingConstants.LEFT);
    private final JLabel lblRange = new JLabel("", SwingConstants.CENTER);
    private final JLabel lblMeasure = new JLabel("MESURE: OFF", SwingConstants.CENTER);
    private final JButton btnBackFast = nav("", () -> moveBackward(bigStep(window())));
    private final JButton btnBack = nav("", () -> moveBackward(smallStep(window())));
    private final JButton btnForward = nav("", () -> moveForward(smallStep(window())));
    private final JButton btnForwardFast = nav("", () -> moveForward(bigStep(window())));
    private final JButton btnZoomIn = nav("ZOOM +", this::zoomIn);
    private final JButton btnZoomOut = nav("ZOOM -", this::zoomOut);
    private final JButton btnLive = nav("LIVE", this::goLive);
    private final JButton btnMeasure = nav("MESURE", this::toggleMeasure);
    private final JButton btnExit = new JButton("EXIT");
    private final List<JCheckBox> chkExtra = new ArrayList<>();
    private final JCheckBox chkTemp = new JCheckBox("TEMPERATURA", true);
    private final JCheckBox chkAuto = new JCheckBox("SCALA AUTO", true);
    private final JCheckBox chkStep = new JCheckBox("STEP", true);
    private int hoverX = -1;

    private long visibleEnd = System.currentTimeMillis();
    private boolean live = true;
    private int zoomIndex = DEFAULT_ZOOM_INDEX;
    private boolean measureMode;
    private long measure1 = -1;
    private long measure2 = -1;

    public B1TrendPanel(B1TrendStore store, Runnable onExit) {
        this.store = store;
        this.onExit = onExit;
        for (B1TrendChart.Series s : B1TrendChart.defaultSeries()) {
            if (!"temp".equals(s.id)) {
                extra.add(s);
            }
        }
        setLayout(null);
        setBackground(BG_OUTER);
        style(lblTitle, 26, TEXT_COLOR, false);
        style(lblDate, 18, TEXT_COLOR, false);
        style(lblValue, 20, POINT_COLOR, true);
        style(lblRange, 15, TEXT_COLOR, true);
        style(lblMeasure, 15, POINT_COLOR, true);
        add(lblTitle);
        add(lblDate);
        add(lblValue);
        add(lblRange);
        add(lblMeasure);
        chkTemp.setForeground(LINE_COLOR);
        decorate(chkTemp);
        add(chkTemp);
        for (B1TrendChart.Series s : extra) {
            JCheckBox c = new JCheckBox(s.label.toUpperCase(Locale.ROOT).replace("VELOCIT\u00c0 ROTORI", "GIRI"), true);
            c.setForeground(s.color.darker());
            decorate(c);
            chkExtra.add(c);
            add(c);
        }
        decorate(chkAuto);
        decorate(chkStep);
        chkAuto.setForeground(TEXT_COLOR);
        chkStep.setForeground(POINT_COLOR);
        add(chkAuto);
        add(chkStep);
        for (JButton b : new JButton[]{btnBackFast, btnBack, btnForward, btnForwardFast, btnZoomIn, btnZoomOut,
                btnLive, btnMeasure}) {
            add(b);
        }
        btnExit.setFont(new Font("Arial", Font.BOLD, 20));
        btnExit.setFocusPainted(false);
        btnExit.setForeground(TEXT_COLOR);
        btnExit.addActionListener(e -> {
            if (onExit != null) {
                onExit.run();
            }
        });
        add(btnExit);
        installMeasureMouse();
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                layoutComponents();
            }
        });
        updateButtons();
        updateRange();
        new Timer(1000, e -> {
            if (live) {
                visibleEnd = System.currentTimeMillis();
            }
            lblDate.setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            updateValue();
            updateRange();
            repaint();
        }).start();
    }

    // ------------------------------------------------------------------ funzioni pure (testabili)
    static Duration smallStep(Duration window) {
        return Duration.ofMinutes(Math.max(1L, window.toMinutes() / 10L));
    }

    static Duration bigStep(Duration window) {
        return Duration.ofMinutes(Math.max(5L, window.toMinutes() / 4L));
    }

    static String compact(Duration d) {
        long m = d.toMinutes();
        if (m % (24L * 60L) == 0) {
            return (m / (24L * 60L)) + "D";
        }
        if (m % 60L == 0) {
            return (m / 60L) + "H";
        }
        return m + "M";
    }

    static String hms(Duration d) {
        long s = Math.abs(d.getSeconds());
        return String.format(Locale.US, "%02dH%02dM%02dS", s / 3600L, (s % 3600L) / 60L, s % 60L);
    }

    private static int tickCount(Duration window) {
        long m = window.toMinutes();
        if (m <= 60) {
            return 6;
        }
        if (m <= 300) {
            return 5;
        }
        if (m <= 720) {
            return 6;
        }
        return 8;
    }

    // ------------------------------------------------------------------ interfaccia
    private void decorate(JCheckBox c) {
        c.setFont(new Font("Consolas", Font.BOLD, 14));
        c.setOpaque(false);
        c.setFocusPainted(false);
        c.addActionListener(e -> repaint());
    }

    private static void style(JLabel l, int size, Color fg, boolean box) {
        l.setFont(new Font("Consolas", Font.BOLD, size));
        l.setForeground(fg);
        if (box) {
            l.setOpaque(true);
            l.setBackground(VALUE_BOX);
            l.setBorder(BorderFactory.createLineBorder(BTN_BORDER, 1));
        }
    }

    private static JButton nav(String text, Runnable action) {
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

    private void layoutComponents() {
        int w = getWidth();
        int h = getHeight();
        lblTitle.setBounds(22, 15, 520, 35);
        lblDate.setBounds(w - 290, 15, 260, 35);
        lblValue.setBounds(22, 58, 220, 32);
        lblRange.setBounds(255, 58, 440, 32);
        lblMeasure.setBounds(Math.max(710, w - 330), 58, 300, 32);
        int cx = 22;
        chkTemp.setBounds(cx, 92, 150, 24);
        cx += 160;
        for (JCheckBox c : chkExtra) {
            c.setBounds(cx, 92, 150, 24);
            cx += 160;
        }
        chkAuto.setBounds(cx, 92, 140, 24);
        chkStep.setBounds(cx + 150, 92, 90, 24);
        int y = h - 60;
        btnBackFast.setBounds(24, y, 100, 42);
        btnBack.setBounds(132, y, 100, 42);
        btnForward.setBounds(240, y, 100, 42);
        btnForwardFast.setBounds(348, y, 100, 42);
        btnZoomIn.setBounds(468, y, 100, 42);
        btnZoomOut.setBounds(576, y, 100, 42);
        btnLive.setBounds(684, y, 110, 42);
        btnMeasure.setBounds(806, y, 130, 42);
        btnExit.setBounds(w - 25 - 110, h - 20 - 60, 110, 60);
    }

    private Duration window() {
        return ZOOM_WINDOWS[zoomIndex];
    }

    private void updateButtons() {
        btnBackFast.setText("<< " + compact(bigStep(window())));
        btnBack.setText("< " + compact(smallStep(window())));
        btnForward.setText(compact(smallStep(window())) + " >");
        btnForwardFast.setText(compact(bigStep(window())) + " >>");
    }

    private void zoomIn() {
        if (zoomIndex <= 0) {
            return;
        }
        if (live) {
            visibleEnd = System.currentTimeMillis();
        }
        zoomIndex--;
        updateButtons();
        updateRange();
        repaint();
    }

    private void zoomOut() {
        if (zoomIndex >= ZOOM_WINDOWS.length - 1) {
            return;
        }
        if (live) {
            visibleEnd = System.currentTimeMillis();
        }
        zoomIndex++;
        updateButtons();
        updateRange();
        repaint();
    }

    private void goLive() {
        live = true;
        visibleEnd = System.currentTimeMillis();
        updateRange();
        repaint();
    }

    private void moveBackward(Duration step) {
        live = false;
        long now = System.currentTimeMillis();
        long minEnd = now - TOTAL_HISTORY.toMillis() + window().toMillis();
        visibleEnd = Math.max(minEnd, visibleEnd - step.toMillis());
        updateRange();
        repaint();
    }

    private void moveForward(Duration step) {
        long now = System.currentTimeMillis();
        visibleEnd += step.toMillis();
        if (visibleEnd >= now) {
            visibleEnd = now;
            live = true;
        } else {
            live = false;
        }
        updateRange();
        repaint();
    }

    private void updateRange() {
        long now = System.currentTimeMillis();
        if (visibleEnd > now) {
            visibleEnd = now;
        }
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM HH:mm");
        String txt = fmt(visibleEnd - window().toMillis(), f) + "  ->  " + fmt(visibleEnd, f);
        lblRange.setText(live ? txt + "   [LIVE]" : txt);
    }

    private static String fmt(long ms, DateTimeFormatter f) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZONE).format(f);
    }

    private void updateValue() {
        Double v = store.buffer("temp").nearest(System.currentTimeMillis(), 5000);
        lblValue.setText(v == null ? "TEMP: --.- \u00b0C" : String.format(Locale.US, "TEMP: %.1f \u00b0C", v));
    }

    // ------------------------------------------------------------------ MESURE
    private void toggleMeasure() {
        measureMode = !measureMode;
        measure1 = -1;
        measure2 = -1;
        setCursor(Cursor.getDefaultCursor());
        updateMeasureLabel();
        repaint();
    }

    private void updateMeasureLabel() {
        if (!measureMode) {
            lblMeasure.setText("MESURE: OFF");
            btnMeasure.setText("MESURE");
            return;
        }
        btnMeasure.setText("MESURE ON");
        if (measure1 < 0) {
            lblMeasure.setText("MESURE: cliquez point 1");
        } else if (measure2 < 0) {
            lblMeasure.setText("MESURE: cliquez point 2");
        } else {
            lblMeasure.setText("DELTA: " + hms(Duration.ofMillis(Math.abs(measure2 - measure1))));
        }
    }

    private Rectangle chartBounds() {
        int left = 95;
        int right = 70;
        int top = 128;
        int bottom = 120;
        return new Rectangle(left, top, Math.max(200, getWidth() - left - right), Math.max(200, getHeight() - top - bottom));
    }

    private long timeAt(int x, Rectangle c) {
        double r = Math.max(0, Math.min(1, (x - c.x) / (double) c.width));
        return (long) (visibleEnd - window().toMillis() + r * window().toMillis());
    }

    private void installMeasureMouse() {
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                hoverX = e.getX();
                repaint();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hoverX = -1;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (!measureMode || e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                Rectangle c = chartBounds();
                if (!c.contains(e.getPoint())) {
                    Toolkit.getDefaultToolkit().beep();
                    return;
                }
                long t = timeAt(e.getX(), c);
                Double v = store.buffer("temp").nearest(t, 120_000);
                if (v == null) {
                    Toolkit.getDefaultToolkit().beep(); // nessun dato vicino
                    return;
                }
                if (measure1 < 0 || measure2 >= 0) {
                    measure1 = t;
                    measure2 = -1;
                } else {
                    measure2 = t;
                }
                updateMeasureLabel();
                repaint();
            }
        });
    }

    // ------------------------------------------------------------------ disegno
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(BG_OUTER);
        g2.fillRect(0, 0, getWidth(), getHeight());
        paintChart(g2);
        g2.dispose();
    }

    private void paintChart(Graphics2D g2) {
        Rectangle c = chartBounds();
        g2.setPaint(new GradientPaint(c.x, c.y, Color.WHITE, c.x, c.y + c.height, new Color(0xF8, 0xF9, 0xFB)));
        g2.fillRect(c.x, c.y, c.width, c.height);
        paintGrid(g2, c);
        g2.setColor(AXIS_COLOR);
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRect(c.x, c.y, c.width, c.height);
        paintTimeline(g2, c);
        paintAxes(g2, c);
        long to = visibleEnd;
        long from = to - window().toMillis();
        paintExtra(g2, c, from, to);
        if (chkTemp.isSelected()) {
            paintTemperature(g2, c, from, to);
            if (chkStep.isSelected()) {
                paintSteps(g2, c, from, to);
            }
        }
        paintMeasure(g2, c, from, to);
        paintRuler(g2, c, from, to);
    }

    /** Marcatori STEP (come in B2): cambio di fase della ricetta, sul punto della curva di temperatura. */
    private void paintSteps(Graphics2D g2, Rectangle c, long from, long to) {
        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        for (B1TrendStore.Event e : store.events("step")) {
            if (e.time < from || e.time > to || e.text.isEmpty() || "0".equals(e.text)) {
                continue;
            }
            Double t = store.buffer("temp").nearest(e.time, 120_000);
            if (t == null) {
                continue;
            }
            int px = c.x + (int) Math.round((e.time - from) / (double) (to - from) * c.width);
            int py = c.y + c.height - (int) Math.round(Math.max(0, Math.min(MAX_TEMP, t)) / MAX_TEMP * c.height);
            g2.setColor(new Color(0xFF, 0xB4, 0x3C));
            g2.fillOval(px - 4, py - 4, 8, 8);
            g2.setColor(new Color(0xC0, 0x70, 0x00));
            g2.drawOval(px - 6, py - 6, 12, 12);
            String label = e.text;
            int tw = g2.getFontMetrics().stringWidth(label);
            int tx = Math.min(px + 10, c.x + c.width - tw - 6);
            int ty = Math.max(py - 8, c.y + 14);
            g2.setColor(new Color(0xFF, 0xF2, 0xD0, 235));
            g2.fillRoundRect(tx - 3, ty - 11, tw + 6, 16, 6, 6);
            g2.setColor(new Color(0x80, 0x45, 0x00));
            g2.drawString(label, tx, ty);
        }
    }

    /** Righello al passaggio del mouse (fuori dalla modalita' MESURE): valori di tutte le serie visibili. */
    private void paintRuler(Graphics2D g2, Rectangle c, long from, long to) {
        if (measureMode || hoverX < c.x || hoverX > c.x + c.width) {
            return;
        }
        long t = timeAt(hoverX, c);
        g2.setColor(new Color(0x40, 0x48, 0x50));
        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(hoverX, c.y, hoverX, c.y + c.height);
        List<String> rows = new ArrayList<>();
        rows.add(fmt(t, DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")));
        if (chkTemp.isSelected()) {
            Double v = store.buffer("temp").nearest(t, 120_000);
            if (v != null) {
                rows.add(String.format(Locale.ITALY, "Temperatura: %.1f \u00b0C", v));
            }
        }
        for (int k = 0; k < extra.size(); k++) {
            if (!chkExtra.get(k).isSelected()) {
                continue;
            }
            Double v = store.buffer(extra.get(k).id).nearest(t, 120_000);
            if (v != null) {
                rows.add(String.format(Locale.ITALY, "%s: %.1f %s", extra.get(k).label, v, extra.get(k).unit));
            }
        }
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        int bw = 230;
        int bx = hoverX + 12 + bw > c.x + c.width ? hoverX - 12 - bw : hoverX + 12;
        g2.setColor(new Color(255, 255, 225, 240));
        g2.fillRect(bx, c.y + 6, bw, 16 * rows.size() + 6);
        g2.setColor(Color.GRAY);
        g2.drawRect(bx, c.y + 6, bw, 16 * rows.size() + 6);
        g2.setColor(Color.BLACK);
        for (int i = 0; i < rows.size(); i++) {
            g2.drawString(rows.get(i), bx + 6, c.y + 20 + i * 16);
        }
    }

    private void paintGrid(Graphics2D g2, Rectangle c) {
        for (int i = 0; i <= 20; i++) {
            int y = c.y + (int) Math.round(i / 20.0 * c.height);
            g2.setColor(i % 2 == 0 ? GRID_MAJOR : GRID_MINOR);
            g2.drawLine(c.x, y, c.x + c.width, y);
            int x = c.x + (int) Math.round(i / 20.0 * c.width);
            g2.setColor(i % 4 == 0 ? GRID_MAJOR : GRID_MINOR);
            g2.drawLine(x, c.y, x, c.y + c.height);
        }
    }

    private void paintAxes(Graphics2D g2, Rectangle c) {
        g2.setColor(LINE_COLOR);
        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 0; i <= 10; i++) {
            String s = String.valueOf(MAX_TEMP - i * 20);
            int y = c.y + (int) Math.round(i / 10.0 * c.height);
            g2.drawString(s, c.x - 18 - fm.stringWidth(s), y + 5);
        }
        g2.setColor(LINE_COLOR);
        g2.drawString("\u00b0C", c.x - 42, c.y - 10);
        long from = visibleEnd - window().toMillis();
        g2.setColor(TEXT_COLOR);
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        fm = g2.getFontMetrics();
        int ticks = Math.max(2, tickCount(window()));
        DateTimeFormatter f = DateTimeFormatter.ofPattern(window().toHours() >= 12 ? "dd/MM HH:mm" : "HH:mm");
        for (int i = 0; i <= ticks; i++) {
            double r = (double) i / ticks;
            int x = c.x + (int) Math.round(r * c.width);
            String s = fmt(from + (long) (r * window().toMillis()), f);
            g2.drawString(s, x - fm.stringWidth(s) / 2, c.y + c.height + 22);
        }
        g2.setFont(new Font("Consolas", Font.BOLD, 13));
        g2.setColor(POINT_COLOR);
        String label = "TEMPS FENETRE (" + compact(window()) + ")";
        g2.drawString(label, c.x + (c.width - g2.getFontMetrics().stringWidth(label)) / 2, c.y + c.height + 48);
    }

    /** Fascia in basso: ricetta (verde, con il nome) e modo manuale (rosso), come in PLC3_Graph. */
    private void paintTimeline(Graphics2D g2, Rectangle c) {
        int barH = 22;
        int barY = c.y + c.height - barH - 6;
        int barX = c.x + 2;
        int barW = Math.max(50, c.width - 4);
        long to = visibleEnd;
        long from = to - window().toMillis();
        long total = window().toMillis();
        g2.setColor(new Color(0xDD, 0xE6, 0xDD));
        g2.fillRect(barX, barY, barW, barH);
        List<B1TrendStore.Event> recipes = store.events("recipe");
        List<B1TrendStore.Event> modes = store.events("mode");
        if (recipes.isEmpty() && modes.isEmpty()) {
            g2.setColor(new Color(0x90, 0x98, 0x90));
            g2.drawRect(barX, barY, barW, barH);
            return;
        }
        // tratti manuali (per tagliare le scritte verdi)
        List<long[]> manual = new ArrayList<>();
        for (int i = 0; i < modes.size(); i++) {
            if ("manu".equalsIgnoreCase(modes.get(i).text)) {
                long s = modes.get(i).time;
                long e = i + 1 < modes.size() ? modes.get(i + 1).time : to;
                manual.add(new long[]{s, e});
            }
        }
        for (int i = 0; i < recipes.size(); i++) {
            long s = recipes.get(i).time;
            long e = i + 1 < recipes.size() ? recipes.get(i + 1).time : to;
            segment(g2, barX, barY, barW, barH, from, to, total, s, e, new Color(0, 115, 60), new Color(80, 210, 120));
        }
        for (long[] m : manual) {
            segment(g2, barX, barY, barW, barH, from, to, total, m[0], m[1], new Color(190, 25, 25), new Color(255, 95, 95));
            label(g2, barX, barY, barW, barH, from, to, total, m[0], m[1], "MANUALE", "M", true);
        }
        // nomi ricetta nei tratti non manuali
        for (int i = 0; i < recipes.size(); i++) {
            long s = recipes.get(i).time;
            long e = i + 1 < recipes.size() ? recipes.get(i + 1).time : to;
            long cur = s;
            for (long[] m : manual) {
                if (m[1] <= cur || m[0] >= e) {
                    continue;
                }
                if (m[0] > cur) {
                    label(g2, barX, barY, barW, barH, from, to, total, cur, m[0], recipes.get(i).text, "", false);
                }
                cur = Math.max(cur, m[1]);
            }
            if (cur < e) {
                label(g2, barX, barY, barW, barH, from, to, total, cur, e, recipes.get(i).text, "", false);
            }
        }
        g2.setColor(new Color(0, 160, 120));
        g2.drawRect(barX, barY, barW, barH);
    }

    private static void segment(Graphics2D g2, int barX, int barY, int barW, int barH, long from, long to, long total,
                                long s, long e, Color fill, Color border) {
        if (e <= from || s >= to) {
            return;
        }
        int x1 = barX + (int) Math.round((Math.max(s, from) - from) / (double) total * barW);
        int x2 = barX + (int) Math.round((Math.min(e, to) - from) / (double) total * barW);
        int w = Math.max(2, x2 - x1);
        g2.setColor(fill);
        g2.fillRect(x1, barY, w, barH);
        g2.setColor(border);
        g2.drawRect(x1, barY, w, barH);
    }

    private static void label(Graphics2D g2, int barX, int barY, int barW, int barH, long from, long to, long total,
                              long s, long e, String full, String shortLabel, boolean light) {
        if (e <= from || s >= to) {
            return;
        }
        int x1 = barX + (int) Math.round((Math.max(s, from) - from) / (double) total * barW);
        int x2 = barX + (int) Math.round((Math.min(e, to) - from) / (double) total * barW);
        int w = x2 - x1;
        if (w < 14) {
            return;
        }
        g2.setFont(new Font("Consolas", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        String t = full == null ? "" : full.trim();
        if (fm.stringWidth(t) + 10 > w) {
            t = shortLabel;
        }
        if (t.isEmpty() || fm.stringWidth(t) + 8 > w) {
            return;
        }
        g2.setColor(light ? new Color(255, 245, 245) : new Color(215, 255, 215));
        g2.drawString(t, x1 + (w - fm.stringWidth(t)) / 2, barY + (barH - fm.getHeight()) / 2 + fm.getAscent());
    }

    private void paintTemperature(Graphics2D g2, Rectangle c, long from, long to) {
        List<double[]> pts = store.buffer("temp").downsample(from, to, c.width);
        if (pts.isEmpty()) {
            return;
        }
        g2.setColor(LINE_COLOR);
        g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Point prev = null;
        long prevT = 0;
        Point last = null;
        double lastV = 0;
        for (double[] p : pts) {
            int px = c.x + (int) Math.round((p[0] - from) / (double) (to - from) * c.width);
            double v = Math.max(0, Math.min(MAX_TEMP, p[1]));
            int py = c.y + c.height - (int) Math.round(v / MAX_TEMP * c.height);
            Point cur = new Point(px, py);
            if (prev != null && p[0] - prevT <= 120_000) { // buco di dati: la linea si interrompe
                g2.drawLine(prev.x, prev.y, cur.x, cur.y);
            }
            prev = cur;
            prevT = (long) p[0];
            last = cur;
            lastV = p[1];
        }
        if (last != null && to - prevT < 30_000) {
            g2.setColor(new Color(0xFF, 0xD0, 0x40));
            g2.fillOval(last.x - 5, last.y - 5, 10, 10);
            g2.setColor(LINE_COLOR);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(last.x - 7, last.y - 7, 14, 14);
            String t = String.format(Locale.US, "%.1f \u00b0C", lastV);
            g2.setFont(new Font("Consolas", Font.BOLD, 12));
            int tw = g2.getFontMetrics().stringWidth(t);
            int tx = Math.min(last.x + 12, c.x + c.width - tw - 6);
            g2.setColor(POINT_COLOR);
            g2.drawString(t, tx, Math.max(c.y + 14, last.y - 10));
        }
    }

    /** Serie aggiuntive (corrente, giri, pressione): scala automatica sull'asse destro della prima selezionata. */
    private void paintExtra(Graphics2D g2, Rectangle c, long from, long to) {
        boolean axisDrawn = false;
        for (int k = 0; k < extra.size(); k++) {
            if (!chkExtra.get(k).isSelected()) {
                continue;
            }
            B1TrendChart.Series s = extra.get(k);
            List<double[]> pts = store.buffer(s.id).downsample(from, to, c.width);
            if (pts.isEmpty()) {
                continue;
            }
            double[] vals = new double[pts.size()];
            for (int i = 0; i < vals.length; i++) {
                vals[i] = pts.get(i)[1];
            }
            double max = chkAuto.isSelected() ? TrendScale.autoMax(vals) : s.defMax;
            g2.setStroke(new BasicStroke(1.6f));
            g2.setColor(s.color);
            Point prev = null;
            long prevT = 0;
            for (double[] p : pts) {
                int px = c.x + (int) Math.round((p[0] - from) / (double) (to - from) * c.width);
                double v = Math.max(0, Math.min(max, p[1]));
                int py = c.y + c.height - (int) Math.round(v / max * c.height);
                Point cur = new Point(px, py);
                if (prev != null && p[0] - prevT <= 120_000) {
                    g2.drawLine(prev.x, prev.y, cur.x, cur.y);
                }
                prev = cur;
                prevT = (long) p[0];
            }
            if (!axisDrawn) {
                axisDrawn = true;
                g2.setFont(new Font("Consolas", Font.BOLD, 12));
                g2.setColor(s.color.darker());
                for (int i = 0; i <= 4; i++) {
                    int y = c.y + c.height - c.height * i / 4;
                    g2.drawString(String.format(Locale.US, "%.0f", max * i / 4.0), c.x + c.width + 6, y + 4);
                }
                g2.drawString(s.unit, c.x + c.width + 6, c.y - 10);
            }
        }
    }

    private void paintMeasure(Graphics2D g2, Rectangle c, long from, long to) {
        if (!measureMode) {
            return;
        }
        long[] ms = {measure1, measure2};
        for (int i = 0; i < 2; i++) {
            if (ms[i] < 0 || ms[i] < from || ms[i] > to) {
                continue;
            }
            int x = c.x + (int) Math.round((ms[i] - from) / (double) (to - from) * c.width);
            g2.setColor(new Color(0x60, 0x60, 0x00));
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5f, 4f}, 0f));
            g2.drawLine(x, c.y, x, c.y + c.height);
            g2.setStroke(new BasicStroke(1f));
            Double v = store.buffer("temp").nearest(ms[i], 120_000);
            String t = "P" + (i + 1) + " " + fmt(ms[i], DateTimeFormatter.ofPattern("HH:mm:ss"))
                    + (v == null ? "" : String.format(Locale.US, "  %.1f \u00b0C", v));
            g2.setFont(new Font("Consolas", Font.BOLD, 12));
            int tw = g2.getFontMetrics().stringWidth(t);
            int tx = Math.min(x + 6, c.x + c.width - tw - 4);
            g2.setColor(new Color(255, 255, 200, 240));
            g2.fillRect(tx - 3, c.y + 4 + i * 18, tw + 6, 16);
            g2.setColor(new Color(0x60, 0x40, 0x00));
            g2.drawString(t, tx, c.y + 16 + i * 18);
        }
    }

    /** Immagine di prova con dati di esempio (5 ore), senza schermo. */
    public static void snapshot(String out, boolean withMeasure) throws Exception {
        System.setProperty("java.awt.headless", "true");
        B1TrendStore st = new B1TrendStore(B1TrendRecorder.seriesIds());
        long now = System.currentTimeMillis();
        long t0 = now - 5L * 3600_000L;
        for (long t = t0; t <= now; t += 1000) {
            double x = (t - t0) / 60_000.0;               // minuti
            double cyc = (x % 6.0) / 6.0;                 // cicli da 6 minuti
            double temp = 45 + 70 * cyc + 3 * Math.sin(x);
            double amp = cyc < 0.85 ? 120 + 60 * Math.sin(x * 3) : 20;
            st.add(t, new double[]{temp, amp, cyc < 0.9 ? 36 : 0, 4 + 5 * Math.abs(Math.sin(x))});
        }
        st.addEvent("recipe", t0, "gomma2");
        st.addEvent("mode", t0, "Auto");
        st.addEvent("mode", now - 90 * 60_000L, "Manu");
        st.addEvent("mode", now - 40 * 60_000L, "Auto");
        st.addEvent("recipe", now - 150 * 60_000L, "RFL8 V05 1er");
        B1TrendPanel p = new B1TrendPanel(st, null);
        p.setSize(1280, 720);
        p.layoutComponents();
        if (withMeasure) {
            p.toggleMeasure();
            p.measure1 = now - 200 * 60_000L;
            p.measure2 = now - 170 * 60_000L;
            p.updateMeasureLabel();
        }
        p.updateValue();
        p.updateRange();
        p.lblDate.setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        BufferedImage img = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        p.paint(g);
        for (Component comp : p.getComponents()) {
            Graphics cg = g.create(comp.getX(), comp.getY(), comp.getWidth(), comp.getHeight());
            comp.paint(cg);
            cg.dispose();
        }
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", new File(out));
    }
}
