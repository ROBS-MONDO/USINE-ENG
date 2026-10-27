package Banbury;

import java.awt.*;

/** SYNOPTIQUE 2: BAMBURY 1 (mescolatore W118, serbatoio Ceraclor CS201). */
public class GovoniBY1 extends SynoptiquePanel {

    public GovoniBY1() {
        cfg.title = "SYNOPTIQUE 2: BAMBURY 1";
        cfg.lslFormat = "LSL1%02d";
        cfg.suffix = "A";
        cfg.mixerName = "W118"; cfg.filterName = "F116"; cfg.cellName = "FL120";
        cfg.valve1 = "V123"; cfg.valve2 = "V124";
        cfg.mixerCx = 712; cfg.banburyX = 592;
        cfg.fastValve = "V126"; cfg.fan = "GV130"; cfg.gc = "GC128"; cfg.pls = "PLS128";
        cfg.panels.add(new WeighPanel(296, 640, "W118", "----", "-41.380", "0.000", "0.000"));
        cfg.panels.add(new WeighPanel(296, 760, "CL201", "----", "0.000", "0.000", "0.000"));
        cfg.recetteX = 386; cfg.recetteY = 878; cfg.recette = "A"; cfg.lot = "2";
        cfg.planningX = 500; cfg.planningY = 697; cfg.planningW = 124;
        cfg.productionX = 598; cfg.productionY = 912; cfg.productionW = 124;
        cfg.bottomButtons = new String[]{"SIn: SILOS", "SIn: BY2"};
    }

    @Override
    protected void paintLowerRight(Graphics2D g) {
        pipe(g, 712, 845, 1100, 845, 1100, 780, 1255, 780);
        pipe(g, 712, 875, 1180, 875);
        pipe(g, 1275, 880, 1538, 880, 1538, 862);
        pipe(g, 1255, 780, 1330, 780, 1330, 730, 1405, 730, 1405, 760);
        pump(g, 1275, 795); label(g, 1252, 828, "P201");
        pump(g, 1275, 885); label(g, 1252, 918, "P203");
        inlineValve(g, 1188, 875); label(g, 1165, 915, "V204");
        inlineValve(g, 1272, 733); label(g, 1183, 711, "V200");
        ceraclorTank(g, 1409, 755, "CS201", 1340, 840);
        cistern(g, 1535, 688);
        weighPanel(g, 796, 676, 196, 118, value("CL203.silo", "----"), value("CL203.brut", "94.600"),
                value("CL203.set", "0.000"), value("CL203.net", "0.000"));
        button(g, 995, 677, 62, 38, "CL203", 14);
        inverterBox(g, 806, 798, 166, value("Inverter", "0"));
    }
}
