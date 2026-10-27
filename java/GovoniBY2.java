package Banbury;

import java.awt.*;

/** SYNOPTIQUE 3: BAMBURY 2 (mescolatore W117, serbatoi CProc/CLub, Ceraclor CS200). */
public class GovoniBY2 extends SynoptiquePanel {

    public GovoniBY2() {
        cfg.title = "SYNOPTIQUE 3: BAMBURY 2";
        cfg.lslFormat = "LSL%02d";
        cfg.suffix = "B";
        cfg.activeSilo = 3;   // C103B in marcia, V103B aperta
        cfg.mixerName = "W117"; cfg.filterName = "F115"; cfg.cellName = "FL119";
        cfg.valve1 = "V121"; cfg.valve2 = "V122";
        cfg.mixerCx = 688; cfg.banburyX = 566;
        cfg.fastValve = "V125"; cfg.fan = "GV127"; cfg.gc = "GC127"; cfg.pls = "PSL127";
        cfg.fanArrows = true;
        cfg.panels.add(new WeighPanel(298, 632, "W117", "S3", "140.080", "60.000", "137.720"));
        cfg.panels.add(new WeighPanel(298, 750, "CL200", "----", "0.000", "0.000", "0.000"));
        cfg.recetteX = 362; cfg.recetteY = 870; cfg.recette = "RFL18"; cfg.lot = "";
        cfg.planningX = 757; cfg.planningY = 680; cfg.planningW = 122;
        cfg.productionX = 566; cfg.productionY = 910; cfg.productionW = 122;
        cfg.bottomButtons = new String[]{"SIn: SILOS", "SIn: BY1"};
    }

    @Override
    protected void paintLowerRight(Graphics2D g) {
        pipe(g, 690, 835, 730, 835, 730, 695, 1235, 695);
        pipe(g, 690, 850, 748, 850, 748, 738, 1225, 738);
        pipe(g, 690, 868, 765, 868, 765, 905, 1520, 905);
        pipe(g, 690, 880, 760, 880, 760, 905);
        pipe(g, 898, 770, 898, 760, 948, 760, 948, 820);
        pipe(g, 830, 810, 900, 810);
        pump(g, 1245, 710); label(g, 1225, 676, "P205");
        pump(g, 1245, 748); label(g, 1248, 778, "P204");
        pump(g, 1530, 918); label(g, 1480, 935, "P202");
        pump(g, 850, 830); label(g, 825, 862, "P200");
        inlineValve(g, 1243, 908); label(g, 1220, 945, "V203");
        inlineValve(g, 850, 780);
        ceraclorTank(g, 950, 780, "CS200", 875, 873);
        // CProc / CLub
        pipe(g, 1290, 700, 1430, 700, 1430, 730);
        rect(g, 1294, 704, 110, 26, BLUE, null);
        text(g, 1349, 724, "CProc", 20, true, CENTER, Color.WHITE);
        fillOrange(g, new Rectangle(1323, 730, 72, 95), 1323, 72);
        fillOrange(g, new Rectangle(1395, 722, 70, 103), 1395, 70);
        rect(g, 1405, 756, 122, 26, BLUE, null);
        text(g, 1466, 776, "CLub", 20, true, CENTER, Color.WHITE);
        ledDot(g, 1338, 745, led("LSH200", true)); text(g, 1350, 750, "LSH200", 14, true, LEFT, Color.BLACK);
        ledDot(g, 1410, 745, led("LSH201", true)); text(g, 1420, 750, "LSH201", 14, true, LEFT, Color.BLACK);
        redLed(g, 1338, 793); text(g, 1350, 798, "LSL200", 14, true, LEFT, Color.BLACK);
        redLed(g, 1410, 791); text(g, 1420, 796, "LSL201", 14, true, LEFT, Color.BLACK);
        text(g, 1247, 815, "OLIO PROC.", 14, false, LEFT, Color.BLACK);
        text(g, 1465, 818, "OLIO LUBR.", 14, false, LEFT, Color.BLACK);
        label(g, 1263, 840, "V201");
        label(g, 1473, 840, "V202");
        pipe(g, 1356, 825, 1356, 905);
        pipe(g, 1430, 825, 1430, 905);
        rect(g, 1346, 845, 20, 14, new Color(221, 221, 221), new Color(51, 51, 51));
        rect(g, 1420, 845, 20, 14, new Color(221, 221, 221), new Color(51, 51, 51));
        cistern(g, 1580, 685);
        button(g, 1000, 748, 62, 34, "CL202", 14);
        weighPanel(g, 1000, 785, 193, 117, value("CL202.silo", "----"), value("CL202.brut", "0.000"),
                value("CL202.set", "0.000"), value("CL202.net", "0.000"));
        inverterBox(g, 712, 920, 168, value("Inverter", "40"));
    }
}
