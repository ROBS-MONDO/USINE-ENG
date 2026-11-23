package Banbury1;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;

/**
 * Disegno animato del mixer, preso da PLC3_Main (B2): pistone idraulico con frecce di movimento, porta di carico con nastro e pacchi,
 * rotori, vetro, porta di scarico e serbatoio, con un tubo dal serbatoio al mixer al posto del vecchio scivolo.
 * Coordinate del disegno = quelle di PLC3_Main (colonne a x 548..675); il chiamante le scala nell'area del sinottico.
 * Gli stati vengono da B1 (fine corsa del pistone, porte, giri rotori): nessuna scrittura nel PLC.
 */
final class B1MixerArt {

    // area del disegno originale (per il chiamante): colonne x 548..675, serbatoio fino a x 787, base y 497
    static final int ART_LEFT = 484;
    static final int ART_RIGHT = 790;
    static final int ART_TOP = 26;
    static final int ART_BOTTOM = 545;

    private static final int FRAME_MS = 40;
    private float flowOffset = 0f;
    private double rotorMasterAngle = 0.0;
    private final ArrayList<ChargePacket> chargePackets = new ArrayList<>();
    private final java.util.Random chargeRng = new java.util.Random();
    private float conveyorOffset = 0f;
    private int chargePacketCooldown = 0;
    private boolean chargeActive;
    private double chargeDoorProgress = 0.0;
    private double dischargeDoorProgress = 0.0;
    private Boolean ramLastStableDown = null;
    private int ramArrowDir = 0;
    private double ramProgress = 0.0;
    private double ramTarget = 0.0; // 0 alto, 1 basso, 0.5 flottante
    private double frameAcc = 0.0;

    private static final double RAM_STEP = (double) FRAME_MS / 1500;
    private static final int RAM_BEAM_UP_Y = 38;
    private static final int RAM_BEAM_DOWN_Y = 90;
    private static final int RAM_CYL_TOP_Y = 136;
    private static final int RAM_CYL_H = 26;
    private static final double ROTOR_RIGHT_PHASE_OFFSET = 90.0;
    private static final int CHARGE_DOOR_PIVOT_X = 553;
    private static final int CHARGE_DOOR_PIVOT_Y = 330;
    private static final int CHARGE_DOOR_PANEL_LENGTH = 84;
    private static final int CHARGE_DOOR_PANEL_WIDTH = 12;
    private static final double CHARGE_DOOR_CLOSED_ANGLE_DEG = 0.0;
    private static final double CHARGE_DOOR_OPEN_ANGLE_DEG = -60.0;
    private static final int DISCHARGE_DOOR_PIVOT_X = 670;
    private static final int DISCHARGE_DOOR_PIVOT_Y = 497;
    private static final int DISCHARGE_DOOR_PANEL_LENGTH = 84;
    private static final int DISCHARGE_DOOR_PANEL_WIDTH = 12;
    private static final double DISCHARGE_DOOR_CLOSED_ANGLE_DEG = 0.0;
    private static final double DISCHARGE_DOOR_OPEN_ANGLE_DEG = 120.0;

    /**
     * Avanza l'animazione di dt secondi con lo stato letto da B1.
     * Pistone: i tre fine corsa danno la posizione (alto / flottante / basso); "move" = in movimento, le frecce vanno nella direzione
     * opposta all'ultimo fine corsa (come nel pannello PISTON). Rotori: rpm reali del PLC.
     */
    void tick(double dt, Double rpm, boolean high, boolean low, boolean floating, boolean move, boolean loadDoorOpen,
              boolean dischargeDoorOpen, boolean stepRunning, boolean autoMode) {
        if (high) {
            ramLastStableDown = Boolean.FALSE;
            ramTarget = 0.0;
            ramArrowDir = 0;
        } else if (low) {
            ramLastStableDown = Boolean.TRUE;
            ramTarget = 1.0;
            ramArrowDir = 0;
        } else if (floating) {
            ramTarget = 0.5;
            ramArrowDir = 0;
        }
        if (move && !high && !low) {
            ramArrowDir = ramLastStableDown == null ? 0 : (ramLastStableDown ? -1 : 1);
        } else if (!move) {
            ramArrowDir = 0;
        }
        double d = Math.min(0.25, Math.max(0, dt));
        if (ramProgress < ramTarget) {
            ramProgress = Math.min(ramTarget, ramProgress + d * RAM_STEP * 1000.0 / FRAME_MS);
        } else if (ramProgress > ramTarget) {
            ramProgress = Math.max(ramTarget, ramProgress - d * RAM_STEP * 1000.0 / FRAME_MS);
        }
        // il resto gira a frame fissi da 40 ms come in PLC3_Main
        frameAcc += d * 1000.0;
        while (frameAcc >= FRAME_MS) {
            frameAcc -= FRAME_MS;
            flowOffset += 2f;
            if (flowOffset > 10000f) {
                flowOffset = 0f;
            }
            double speed = rpm == null || rpm <= 0 ? 0.0 : rpm / 20.0;
            rotorMasterAngle += speed;
            if (rotorMasterAngle >= 360.0) {
                rotorMasterAngle -= 360.0;
            }
            chargeDoorProgress = move(chargeDoorProgress, loadDoorOpen);
            dischargeDoorProgress = move(dischargeDoorProgress, dischargeDoorOpen);
            chargeActive = stepRunning && chargeDoorProgress >= 0.99 && autoMode;
            updateChargePackets();
        }
    }

    private static double move(double current, boolean open) {
        double target = open ? 1.0 : 0.0;
        if (current < target) {
            return Math.min(target, current + 0.10);
        }
        return Math.max(target, current - 0.10);
    }

    private void updateChargePackets() {
        if (chargeActive) {
            conveyorOffset += 8.2f;
            if (conveyorOffset > 200f) {
                conveyorOffset = 0f;
            }
            if (chargePacketCooldown > 0) {
                chargePacketCooldown--;
            } else {
                long onBelt = chargePackets.stream().filter(p -> !p.falling).count();
                if (onBelt < 2) {
                    spawnChargePacket();
                    chargePacketCooldown = (int) ((42 + 20) / 1.25);
                }
            }
        } else {
            chargePacketCooldown = 0;
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

    /** Disegna tutto il mixer nelle coordinate di PLC3_Main. */
    void paint(Graphics2D g2) {
        paintMixer(g2);
        paintHopper(g2);
        paintRightIndicators(g2);
    }

    /** Tubo dal serbatoio al mixer: stessa grafica (grigio con bordo scuro) del vecchio scivolo. */
    private void drawTube(Graphics2D g2) {
        // tubo curvo come in PLC3_Main (B2): dalla valvola del serbatoio verso la parete destra del mixer
        Path2D.Double pipe = new Path2D.Double();
        pipe.moveTo(754, 217);
        pipe.lineTo(754, 250);
        pipe.curveTo(754, 265, 745, 270, 730, 270);
        pipe.lineTo(675, 270);
        java.awt.Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(16f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(79, 79, 79));
        g2.draw(pipe);
        g2.setStroke(new BasicStroke(12f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(150, 150, 150));
        g2.draw(pipe);
        g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(195, 195, 195));
        g2.draw(pipe);
        g2.setStroke(oldStroke);
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

        drawHydraulicRam(g2);
        drawRamMovementArrows(g2);

        // Contenuto interno del vano superiore rimosso su richiesta.
        // Il traverso marrone centrale e' stato rimosso; il vetro viene
        // gestito in paintRightIndicators per estendersi piu' in alto.

        drawChargeDoor(g2);
        drawChargeConveyorAndPackets(g2);
    }

    private void drawHydraulicRam(Graphics2D g2) {
        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        final int leftX = 553;
        final int rightX = 670;
        final int centerX = 611;
        final int cylTop = RAM_CYL_TOP_Y;

        int beamY = (int) Math.round(lerp(RAM_BEAM_UP_Y, RAM_BEAM_DOWN_Y, ramProgress));
        int beamH = 10;
        int faceBottom = beamY + beamH + 26 + 4;

        Color[] chromeColors = {new Color(120, 120, 125), new Color(245, 245, 248),
                new Color(150, 150, 155)};
        float[] fr = {0f, 0.4f, 1f};

        // Steli cromati dei due cilindri laterali (salgono lungo le colonne)
        drawChromeRod(g, leftX - 2, beamY, 4, cylTop - beamY, chromeColors, fr);
        drawChromeRod(g, rightX - 2, beamY, 4, cylTop - beamY, chromeColors, fr);

        // Stelo del cilindro centrale: dalla testata del cilindro alla faccia del pistone
        drawChromeRod(g, centerX - 4, faceBottom, 8, cylTop - faceBottom, chromeColors, fr);

        // Corpo del pistone: due pareti inclinate (struttura) + stelo centrale + faccia di spinta.
        // L'interno della conca e' vuoto (si vede lo sfondo).
        final int topY = beamY + beamH;
        final int botY = beamY + beamH + 26;
        final int wallT = 8; // spessore orizzontale delle pareti

        Path2D leftWall = new Path2D.Double();
        leftWall.moveTo(561, topY);
        leftWall.lineTo(561 + wallT, topY);
        leftWall.lineTo(575 + wallT, botY);
        leftWall.lineTo(575, botY);
        leftWall.closePath();

        Path2D rightWall = new Path2D.Double();
        rightWall.moveTo(661 - wallT, topY);
        rightWall.lineTo(661, topY);
        rightWall.lineTo(647, botY);
        rightWall.lineTo(647 - wallT, botY);
        rightWall.closePath();

        Color[] wallColors = {new Color(95, 95, 100), new Color(170, 172, 178), new Color(100, 100, 105)};
        float[] wallFr = {0f, 0.5f, 1f};
        g.setPaint(new LinearGradientPaint(561, 0, 561 + wallT + 14, 0, wallFr, wallColors));
        g.fill(leftWall);
        g.setPaint(new LinearGradientPaint(647 - wallT, 0, 661, 0, wallFr, wallColors));
        g.fill(rightWall);

        // Stelo centrale che scende dalla traversa fino alla faccia di spinta
        g.setPaint(new GradientPaint(0, topY, new Color(175, 177, 183), 0, botY, new Color(105, 105, 110)));
        g.fillRect(centerX - 4, topY, 8, botY - topY);

        g.setColor(new Color(50, 50, 55));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(leftWall);
        g.draw(rightWall);
        g.drawRect(centerX - 4, topY, 8, botY - topY);
        g.setColor(new Color(60, 60, 66));
        g.fillRect(575, beamY + beamH + 26, 72, 4);

        // Traversa (colore originale) con collari di fissaggio degli steli
        g.setPaint(new GradientPaint(0, beamY, new Color(175, 177, 183), 0, beamY + beamH, new Color(105, 105, 110)));
        g.fillRect(559, beamY, 104, beamH);
        g.setColor(new Color(50, 50, 55));
        g.drawRect(559, beamY, 104, beamH);
        g.setColor(new Color(110, 105, 35));
        g.fillRoundRect(leftX - 5, beamY - 2, 10, beamH + 4, 3, 3);
        g.fillRoundRect(rightX - 5, beamY - 2, 10, beamH + 4, 3, 3);
        g.setColor(new Color(60, 60, 60));
        g.drawRoundRect(leftX - 5, beamY - 2, 10, beamH + 4, 3, 3);
        g.drawRoundRect(rightX - 5, beamY - 2, 10, beamH + 4, 3, 3);
        // Collare dello stelo centrale sotto la faccia del pistone
        g.setColor(new Color(110, 105, 35));
        g.fillRoundRect(centerX - 9, faceBottom - 2, 18, 6, 3, 3);
        g.setColor(new Color(60, 60, 60));
        g.drawRoundRect(centerX - 9, faceBottom - 2, 18, 6, 3, 3);

        // Corpi dei cilindri idraulici, fissi in basso
        drawHydraulicCylinderBody(g, leftX - 8, cylTop, 16, RAM_CYL_H);
        drawHydraulicCylinderBody(g, rightX - 8, cylTop, 16, RAM_CYL_H);
        drawHydraulicCylinderBody(g, centerX - 11, cylTop, 22, RAM_CYL_H);

        g.dispose();
    }

    private void drawRamMovementArrows(Graphics2D g2) {
        int dir = ramArrowDir;
        if (dir == 0) {
            return;
        }
        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Zona libera: sopra il pistone se e' basso, sotto se e' alto
        final int top = dir < 0 ? 40 : 94;
        final int bottom = dir < 0 ? 84 : 132;
        final int spacing = 18;
        final int[] columns = {584, 638};

        double phase = (flowOffset * 0.35) % spacing;
        for (int cx : columns) {
            for (int k = -1; k * spacing < (bottom - top); k++) {
                double y = dir < 0 ? bottom - (k * spacing + phase) : top + (k * spacing + phase);
                if (y < top || y > bottom) {
                    continue;
                }
                double edge = Math.min(y - top, bottom - y) / 10.0;
                float alpha = (float) Math.max(0.0, Math.min(1.0, edge));
                double t = 5 * dir;
                Path2D chevron = new Path2D.Double();
                chevron.moveTo(cx - 9, y - t);
                chevron.lineTo(cx, y + t);
                chevron.lineTo(cx + 9, y - t);
                g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(40, 40, 40, (int) (alpha * 200)));
                g.draw(chevron);
                g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(255, 200, 0, (int) (alpha * 255)));
                g.draw(chevron);
            }
        }
        g.dispose();
    }

    private void drawChromeRod(Graphics2D g, int x, int y, int w, int h, Color[] colors, float[] fractions) {
        if (h <= 0) {
            return;
        }
        g.setPaint(new LinearGradientPaint(x, 0, x + w, 0, fractions, colors));
        g.fillRect(x, y, w, h);
        g.setColor(new Color(70, 70, 75));
        g.drawRect(x, y, w, h);
    }

    private void drawHydraulicCylinderBody(Graphics2D g, int x, int y, int w, int h) {
        g.setPaint(new LinearGradientPaint(x, 0, x + w, 0, new float[]{0f, 0.35f, 1f},
                new Color[]{new Color(55, 80, 120), new Color(130, 160, 200), new Color(40, 60, 95)}));
        g.fillRect(x, y, w, h);
        g.setColor(new Color(25, 35, 55));
        g.drawRect(x, y, w, h);
        // Fondello superiore e testata inferiore (premistoppa)
        g.setColor(new Color(35, 45, 65));
        g.fillRect(x - 1, y, w + 2, 4);
        g.fillRect(x - 1, y + h - 5, w + 2, 5);
        // Raccordi olio
        g.setColor(new Color(200, 160, 40));
        g.fillRect(x + w, y + 7, 3, 4);
        g.fillRect(x + w, y + h - 13, 3, 4);
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

    private void drawChargeConveyorAndPackets(Graphics2D g2) {
        boolean active = chargeActive;
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

    private void drawRotorAssembly(Graphics2D g2, double cx, double cy, double angleDeg, boolean mirrorX) {
        drawRotor(g2, cx, cy, 30.0, angleDeg, mirrorX,
                new Color(55, 70, 85),
                new Color(145, 185, 225));
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

    private double lerp(double start, double end, double progress) {
        return start + ((end - start) * progress);
    }

    private void spawnChargePacket() {
        // Nastro a +30\u00b0 rispetto all'orizzontale.
        // Estremo DESTRO (basso) = pivot della porta CHARGE (553, 330).
        final double BELT_ANGLE_RAD = Math.toRadians(30.0);
        final double BELT_LEN       = 80.0;

        // Direzione nastro: da sinistra-alto verso destra-basso (+30\u00b0)
        double dirX = Math.cos(BELT_ANGLE_RAD);
        double dirY = Math.sin(BELT_ANGLE_RAD);

        // Spawn all'estremo sinistro del nastro (pivot - direzione * lunghezza)
        double startX = CHARGE_DOOR_PIVOT_X - dirX * BELT_LEN;
        double startY = CHARGE_DOOR_PIVOT_Y - dirY * BELT_LEN;

        int w = 32 + chargeRng.nextInt(10);
        int h = 18 + chargeRng.nextInt(6);

        // Sposta il centro del pacco sul lato SUPERIORE del nastro:
        // normale superiore al nastro +30\u00b0 in coord schermo (Y verso il basso):
        // la normale "verso l'alto" rispetto al nastro \u00e8 (+sin30\u00b0, -cos30\u00b0) = (+0.5, -0.866)
        double normX =  Math.sin(BELT_ANGLE_RAD);
        double normY = -Math.cos(BELT_ANGLE_RAD);
        double beltHalfH = 5.0; // met\u00e0 spessore nastro (BELT_H=10)
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

        drawTube(g2);
    }

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
                    // centro mixer \u2248 X 611 (tra parete sx 558 e parete dx 665)
                    final double MIXER_CENTER_X = 611.0;
                    double dx = MIXER_CENTER_X - x;
                    double dist = Math.max(1.0, Math.abs(dx));
                    // velocit\u00e0 orizzontale verso il centro, verticale fissa
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
}
