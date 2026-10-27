package Banbury;

import javax.swing.*;
import java.awt.*;

/** Finestra con i due sinottici (Bambury 1 e Bambury 2) in schede. */
public class SynoptiqueFrame extends JFrame {

    public SynoptiqueFrame() {
        super("Sinottici Bambury");
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Synoptique 2: Bambury 1", new GovoniBY1());
        tabs.addTab("Synoptique 3: Bambury 2", new GovoniBY2());
        add(tabs, BorderLayout.CENTER);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SynoptiqueFrame().setVisible(true));
    }
}
