package Banbury1;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Finestra del trend di B1 (stesso aspetto e pulsanti del grafico di B2). I dati sono quelli registrati
 * in background da B1TrendRecorder (2 giorni, anche a finestra chiusa). SOLA LETTURA.
 */
public final class B1TrendWindow extends JFrame {

    private static B1TrendWindow shared;

    private B1TrendWindow(B1TrendStore store) {
        super("Banbury 1 - Trend (sola lettura)");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(new B1TrendPanel(store, this::dispose));
        setSize(1280, 720);
        setLocationRelativeTo(null);
    }

    public static void open(B1Source src) {
        SwingUtilities.invokeLater(() -> {
            synchronized (B1TrendWindow.class) {
                if (shared == null || !shared.isDisplayable()) {
                    shared = new B1TrendWindow(B1TrendStore.shared(B1TrendRecorder.seriesIds()));
                    shared.setVisible(true);
                }
                shared.toFront();
            }
        });
    }
}
