package MES;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tracciabilita' materie prime - VERSIONE DI PROVA con inserimento a mano.
 *
 * Tre schede:
 *  1. Ingresso merce : registra un lotto in arrivo (bolla, fornitore, lotto
 *     fornitore, quantita'), genera il LOTTO INTERNO e ne mostra l'etichetta ZPL.
 *  2. Prelievo zona  : registra cio' che una zona preleva per un batch (lotto
 *     interno, quantita' pesata) con controllo di tolleranza e residuo.
 *  3. Tracciabilita' : ricerca per lotto interno / lotto fornitore / batch / MP.
 *
 * IMPORTANTE: per ora NON c'e' nessun collegamento al database. I dati vivono
 * solo in memoria e si perdono alla chiusura. Tutto il codice SQL e' lasciato
 * in commento (cercare "DB-REM") ed e' pronto da riattivare, insieme allo
 * script CREATE TABLE riportato in fondo al file.
 */
public class TracciabilitaManager extends JFrame {

    private static final String VERSION = "V0.01 (manuale, senza DB)";

    // DB-REM: collegamento a SQL Server "Mondo" (stesso di DistintaBaseManager).
    // private static final String DB_URL =
    //         "jdbc:sqlserver://srv-bambury:1433;databaseName=Mondo;encrypt=true;"
    //                 + "trustServerCertificate=true;sslProtocol=TLSv1.2;loginTimeout=5";
    // private static final String DB_USER = "...";      // spostare in file di configurazione
    // private static final String DB_PASSWORD = "...";

    private static final String[] ZONE = {"ZONA 1", "ZONA 2", "ZONA 3", "ZONA 4", "ZONA 5"};
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ---- Dati in memoria (sostituiscono le tabelle finche' il DB e' scollegato) ----
    private final Map<String, Lotto> lotti = new LinkedHashMap<>();
    private final List<Prelievo> prelievi = new ArrayList<>();
    private int contatoreLotto = 0;
    private int contatorePrelievo = 0;

    // Stampa etichetta "dose": IP della stampante Zebra per zona (porta raw 9100) e formato
    // etichetta. Il formato non e' ancora deciso: si cambia da "Setup stampanti/etichetta".
    private static final int PORTA_STAMPANTE = 9100;
    private final Map<String, String> stampantiIp = new LinkedHashMap<>();
    private int etichettaLarghMm = 100;
    private int etichettaAltMm = 60;
    private int stampanteDpi = 203;

    // Bilance Ohaus (TCP, come ProdMelBanb/BalanceService): una connessione per zona.
    // L'Ohaus accetta un solo client alla volta: non collegarsi da qui mentre un altro
    // programma (es. ProdMelBanb) e' gia' connesso alla stessa bilancia.
    private int bilancePort = 9761;
    private final Map<String, String> bilanceIp = new LinkedHashMap<>();
    private final Map<String, OhausScale> bilance = new LinkedHashMap<>();

    @Override
    public void dispose() {
        for (OhausScale s : bilance.values()) {
            s.close();
        }
        bilance.clear();
        super.dispose();
    }

    private final DefaultTableModel lottiModel = new DefaultTableModel(new String[]{
            "Lotto interno", "Code MP", "Désignation", "Fournisseur", "Bolla", "Lotto fornitore",
            "Scadenza", "Qté initiale", "Qté résiduelle", "UM", "Stato"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final DefaultTableModel prelieviModel = new DefaultTableModel(new String[]{
            "Data/ora", "Batch", "Zona", "Code MP", "Lotto interno", "Qté prévue", "Min", "Max",
            "Qté pesée", "Esito", "Opérateur"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final DefaultTableModel ricercaModel = new DefaultTableModel(new String[]{
            "Data/ora", "Batch", "Zona", "Code MP", "Lotto interno", "Lotto fornitore", "Bolla",
            "Qté pesée", "Esito", "Opérateur"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };

    private JTable lottiTable;

    public TracciabilitaManager() {
        super("Traçabilité matières premières - " + VERSION);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.BOLD, 15));
        tabs.addTab("Ingresso merce", buildIngressoPanel());
        tabs.addTab("Prelievo zona", buildPrelievoPanel());
        tabs.addTab("Tracciabilità", buildRicercaPanel());

        JLabel avviso = new JLabel("  Modalità manuale: nessun collegamento al database, i dati restano solo in memoria.");
        avviso.setOpaque(true);
        avviso.setBackground(new Color(255, 244, 199));
        avviso.setBorder(new EmptyBorder(4, 4, 4, 4));

        JPanel root = new JPanel(new BorderLayout());
        root.add(avviso, BorderLayout.NORTH);
        root.add(tabs, BorderLayout.CENTER);
        setContentPane(root);
        setSize(1200, 720);
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------
    // Scheda 1: Ingresso merce
    // ------------------------------------------------------------------

    private JPanel buildIngressoPanel() {
        JTextField txtMp = new JTextField(12);
        JTextField txtDescr = new JTextField(28);
        JTextField txtFornitore = new JTextField(20);
        JTextField txtBolla = new JTextField(14);
        JTextField txtLottoForn = new JTextField(16);
        JTextField txtScadenza = new JTextField(10);
        JTextField txtQta = new JTextField(8);
        JComboBox<String> cbUm = new JComboBox<>(new String[]{"KG", "G", "L", "PZ"});
        JComboBox<String> cbStato = new JComboBox<>(new String[]{"OK", "QUARANTENA"});
        upper(txtMp);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;
        int row = 0;
        addField(form, g, row, 0, "Code MP:", txtMp);
        addField(form, g, row++, 2, "Désignation:", txtDescr);
        addField(form, g, row, 0, "Fournisseur:", txtFornitore);
        addField(form, g, row++, 2, "Bolla d'ingresso:", txtBolla);
        addField(form, g, row, 0, "Lotto fornitore:", txtLottoForn);
        addField(form, g, row++, 2, "Scadenza (AAAA-MM-GG):", txtScadenza);
        addField(form, g, row, 0, "Quantità:", txtQta);
        addField(form, g, row++, 2, "UM:", cbUm);
        addField(form, g, row, 0, "Stato iniziale:", cbStato);

        JButton btnAdd = new JButton("Registra lotto + genera lotto interno");
        JButton btnEtichetta = new JButton("Mostra etichetta ZPL");
        JButton btnStato = new JButton("Blocca / sblocca");
        JButton btnPulisci = new JButton("Pulisci campi");

        btnAdd.addActionListener(e -> {
            try {
                String mp = txtMp.getText().trim().toUpperCase(Locale.ROOT);
                if (mp.isEmpty()) throw new IllegalStateException("Code MP obbligatorio.");
                double qta = toDouble(txtQta.getText());
                if (qta <= 0) throw new IllegalStateException("Quantità deve essere maggiore di zero.");
                String scad = txtScadenza.getText().trim();
                if (!scad.isEmpty()) {
                    try { LocalDate.parse(scad); }
                    catch (Exception ex) { throw new IllegalStateException("Scadenza non valida (usare AAAA-MM-GG)."); }
                }
                Lotto l = new Lotto();
                l.lottoInterno = nextLottoInterno();
                l.codiceMp = mp;
                l.descrizione = txtDescr.getText().trim();
                l.fornitore = txtFornitore.getText().trim();
                l.bolla = txtBolla.getText().trim();
                l.lottoFornitore = txtLottoForn.getText().trim();
                l.scadenza = scad;
                l.qtaIniziale = qta;
                l.qtaResidua = qta;
                l.um = (String) cbUm.getSelectedItem();
                l.stato = (String) cbStato.getSelectedItem();
                lotti.put(l.lottoInterno, l);
                // DB-REM: salvaLotto(l);  // INSERT INTO dbo.MpLotto ... (vedi script in fondo)
                refreshLottiTable();
                JOptionPane.showMessageDialog(this, "Lotto interno creato: " + l.lottoInterno
                        + "\n(stampare l'etichetta e applicarla al sacco/pallet)",
                        "Lotto registrato", JOptionPane.INFORMATION_MESSAGE);
                txtLottoForn.setText("");
                txtQta.setText("");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnPulisci.addActionListener(e -> {
            for (JTextField t : new JTextField[]{txtMp, txtDescr, txtFornitore, txtBolla, txtLottoForn, txtScadenza, txtQta}) {
                t.setText("");
            }
        });

        btnEtichetta.addActionListener(e -> {
            Lotto l = selectedLotto();
            if (l != null) showZpl(l);
        });

        btnStato.addActionListener(e -> {
            Lotto l = selectedLotto();
            if (l == null) return;
            l.stato = "OK".equals(l.stato) ? "BLOCCATO" : "OK";
            // DB-REM: aggiornaStatoLotto(l);  // UPDATE dbo.MpLotto SET Stato = ? WHERE LottoInterno = ?
            refreshLottiTable();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        buttons.add(btnAdd);
        buttons.add(btnPulisci);
        buttons.add(btnEtichetta);
        buttons.add(btnStato);

        lottiTable = new JTable(lottiModel);
        styleTable(lottiTable);
        lottiTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.CENTER);
        top.add(buttons, BorderLayout.SOUTH);

        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(lottiTable), BorderLayout.CENTER);
        return p;
    }

    private Lotto selectedLotto() {
        int viewRow = lottiTable.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Selezionare prima un lotto nella tabella.",
                    "Nessuna selezione", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return lotti.get(String.valueOf(lottiModel.getValueAt(lottiTable.convertRowIndexToModel(viewRow), 0)));
    }

    private void showZpl(Lotto l) {
        String zpl = "^XA\n"
                + "^CI28\n"
                + "^FO30,25^A0N,40,40^FD" + l.codiceMp + "^FS\n"
                + "^FO30,70^A0N,26,26^FD" + trunc(l.descrizione, 34) + "^FS\n"
                + "^FO30,110^A0N,24,24^FDLotto int.: " + l.lottoInterno + "^FS\n"
                + "^FO30,140^A0N,24,24^FDLotto forn.: " + l.lottoFornitore + "^FS\n"
                + "^FO30,170^A0N,24,24^FDBolla: " + l.bolla + "^FS\n"
                + "^FO30,200^A0N,24,24^FDQta: " + formatNum(l.qtaIniziale) + " " + l.um
                + "  Scad: " + l.scadenza + "^FS\n"
                + "^FO380,40^BQN,2,6^FDQA," + l.lottoInterno + "^FS\n"
                + "^XZ\n";
        JTextArea area = new JTextArea(zpl, 14, 48);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setEditable(false);
        JOptionPane.showMessageDialog(this, new JScrollPane(area),
                "Etichetta " + l.lottoInterno + " (ZPL, per stampante Zebra)", JOptionPane.PLAIN_MESSAGE);
    }

    private String nextLottoInterno() {
        contatoreLotto++;
        // DB-REM: con il DB il progressivo va letto/riservato in tabella (es. SEQUENCE o
        // SELECT MAX) per evitare doppioni tra piu' postazioni.
        return String.format(Locale.ROOT, "L%02d-%06d", LocalDate.now().getYear() % 100, contatoreLotto);
    }

    private void refreshLottiTable() {
        lottiModel.setRowCount(0);
        for (Lotto l : lotti.values()) {
            lottiModel.addRow(new Object[]{l.lottoInterno, l.codiceMp, l.descrizione, l.fornitore, l.bolla,
                    l.lottoFornitore, l.scadenza, formatNum(l.qtaIniziale), formatNum(l.qtaResidua), l.um, l.stato});
        }
    }

    // ------------------------------------------------------------------
    // Scheda 2: Prelievo per zona
    // ------------------------------------------------------------------

    private JPanel buildPrelievoPanel() {
        JTextField txtBatch = new JTextField(12);
        JComboBox<String> cbZona = new JComboBox<>(ZONE);
        cbZona.setEditable(true);
        JTextField txtMp = new JTextField(12);
        JTextField txtLotto = new JTextField(14);
        JTextField txtPrevista = new JTextField(8);
        JTextField txtTol = new JTextField("1", 5);
        JTextField txtPesata = new JTextField(8);
        JTextField txtOperatore = new JTextField(12);
        JCheckBox chkStampa = new JCheckBox("Stampa etichetta dose dopo la registrazione", true);
        JComboBox<String> cbUm = new JComboBox<>(new String[]{"G", "KG", "L", "PZ"});
        JLabel lblBilancia = new JLabel("Bilancia: non connessa");
        lblBilancia.setFont(new Font("SansSerif", Font.BOLD, 16));
        lblBilancia.setForeground(Color.GRAY);
        JButton btnConnetti = new JButton("Connetti bilancia");
        JButton btnLeggi = new JButton("Leggi peso dalla bilancia");
        upper(txtBatch);
        upper(txtMp);
        upper(txtLotto);

        JLabel lblMinMax = new JLabel("Min / Max: -");
        lblMinMax.setFont(new Font("SansSerif", Font.BOLD, 14));
        DocumentListener dl = new DocumentListener() {
            private void upd() {
                double prev = toDouble(txtPrevista.getText());
                double tol = toDouble(txtTol.getText());
                lblMinMax.setText(prev <= 0 ? "Min / Max: -"
                        : "Min / Max: " + formatNum(Math.rint(prev * (1 - tol / 100.0)))
                        + " / " + formatNum(Math.rint(prev * (1 + tol / 100.0))));
            }
            @Override public void insertUpdate(DocumentEvent e) { upd(); }
            @Override public void removeUpdate(DocumentEvent e) { upd(); }
            @Override public void changedUpdate(DocumentEvent e) { upd(); }
        };
        txtPrevista.getDocument().addDocumentListener(dl);
        txtTol.getDocument().addDocumentListener(dl);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;
        int row = 0;
        addField(form, g, row, 0, "Batch (N° LOT):", txtBatch);
        addField(form, g, row++, 2, "Zona:", cbZona);
        addField(form, g, row, 0, "Code MP:", txtMp);
        addField(form, g, row++, 2, "Lotto interno (scan):", txtLotto);
        addField(form, g, row, 0, "Qté prévue (g/kg):", txtPrevista);
        addField(form, g, row++, 2, "Tol. %:", txtTol);
        g.gridx = 0; g.gridy = row; g.gridwidth = 4;
        form.add(lblMinMax, g);
        g.gridwidth = 1;
        row++;
        addField(form, g, row, 0, "Qté pesée:", txtPesata);
        addField(form, g, row++, 2, "Opérateur:", txtOperatore);
        addField(form, g, row, 0, "UM (previsto/pesato):", cbUm);
        g.gridx = 2; g.gridy = row; g.gridwidth = 2;
        form.add(lblBilancia, g);
        g.gridwidth = 1;

        // Peso live della bilancia della zona selezionata, aggiornato 3 volte al secondo.
        javax.swing.Timer uiTimer = new javax.swing.Timer(300, ev -> {
            OhausScale s = bilance.get(String.valueOf(cbZona.getSelectedItem()));
            if (s == null || !s.isConnected()) {
                lblBilancia.setText("Bilancia: non connessa");
                lblBilancia.setForeground(Color.GRAY);
                lblBilancia.setToolTipText(null);
                btnConnetti.setText("Connetti bilancia");
                return;
            }
            btnConnetti.setText("Disconnetti bilancia");
            lblBilancia.setToolTipText("Ultima riga ricevuta: " + s.getLastLine());
            Double kg = s.getKg();
            if (kg == null) {
                lblBilancia.setText("Bilancia: in attesa di dati...");
                lblBilancia.setForeground(Color.GRAY);
                return;
            }
            boolean stabile = s.getStableKg() != null;
            double v = daKg(kg, String.valueOf(cbUm.getSelectedItem()));
            String um = String.valueOf(cbUm.getSelectedItem());
            lblBilancia.setText("Bilancia: " + (Double.isNaN(v) ? formatNum(kg) + " KG" : formatNum(v) + " " + um)
                    + (stabile ? "  STABILE" : "  instabile"));
            lblBilancia.setForeground(stabile ? new Color(0, 140, 0) : new Color(200, 110, 0));
        });
        uiTimer.start();

        btnConnetti.addActionListener(e -> {
            String zona = String.valueOf(cbZona.getSelectedItem());
            OhausScale old = bilance.get(zona);
            if (old != null && old.isConnected()) {
                old.close();
                bilance.remove(zona);
                return;
            }
            String ip = bilanceIp.getOrDefault(zona, "").trim();
            if (ip.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Impostare l'IP della bilancia di " + zona + " da \"Setup stampanti / etichetta / bilance\".",
                        "IP bilancia mancante", JOptionPane.WARNING_MESSAGE);
                return;
            }
            btnConnetti.setEnabled(false);
            OhausScale s = new OhausScale(ip, bilancePort);
            new Thread(() -> {
                String err = s.connect();
                SwingUtilities.invokeLater(() -> {
                    btnConnetti.setEnabled(true);
                    if (err == null) {
                        bilance.put(zona, s);
                    } else {
                        JOptionPane.showMessageDialog(this,
                                "Connessione a " + ip + ":" + bilancePort + " non riuscita:\n" + err
                                        + "\n\nControllare IP/porta e che nessun altro programma sia collegato alla bilancia.",
                                "Erreur bilancia", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }, "connect-bilancia").start();
        });

        btnLeggi.addActionListener(e -> {
            OhausScale s = bilance.get(String.valueOf(cbZona.getSelectedItem()));
            if (s == null || !s.isConnected()) {
                JOptionPane.showMessageDialog(this, "Bilancia non connessa per questa zona.",
                        "Bilancia", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Double kg = s.getStableKg();
            if (kg == null) {
                JOptionPane.showMessageDialog(this, "Peso instabile o non disponibile: attendere che si stabilizzi.",
                        "Bilancia", JOptionPane.WARNING_MESSAGE);
                return;
            }
            double v = daKg(kg, String.valueOf(cbUm.getSelectedItem()));
            if (Double.isNaN(v)) {
                JOptionPane.showMessageDialog(this, "La bilancia legge solo in G o KG: scegliere una di queste UM.",
                        "Bilancia", JOptionPane.WARNING_MESSAGE);
                return;
            }
            txtPesata.setText(formatNum(v));
        });

        JButton btnSave = new JButton("Registra prelievo");
        JButton btnLista = new JButton("Lotti disponibili per questo MP");

        btnLista.addActionListener(e -> {
            String mp = txtMp.getText().trim().toUpperCase(Locale.ROOT);
            StringBuilder sb = new StringBuilder();
            for (Lotto l : lotti.values()) {
                if (mp.isEmpty() || l.codiceMp.equals(mp)) {
                    sb.append(l.lottoInterno).append("  |  forn. ").append(l.lottoFornitore)
                            .append("  |  residuo ").append(formatNum(l.qtaResidua)).append(' ').append(l.um)
                            .append("  |  scad. ").append(l.scadenza.isEmpty() ? "-" : l.scadenza)
                            .append("  |  ").append(l.stato).append('\n');
                }
            }
            JOptionPane.showMessageDialog(this,
                    sb.length() == 0 ? "Nessun lotto registrato." : sb.toString(),
                    "Lotti disponibili", JOptionPane.INFORMATION_MESSAGE);
        });

        btnSave.addActionListener(e -> {
            try {
                String batch = txtBatch.getText().trim().toUpperCase(Locale.ROOT);
                if (batch.isEmpty()) throw new IllegalStateException("Batch obbligatorio.");
                String lottoId = txtLotto.getText().trim().toUpperCase(Locale.ROOT);
                Lotto l = lotti.get(lottoId);
                if (l == null) throw new IllegalStateException("Lotto interno " + lottoId + " inesistente.");
                String mp = txtMp.getText().trim().toUpperCase(Locale.ROOT);
                if (!mp.isEmpty() && !mp.equals(l.codiceMp)) {
                    throw new IllegalStateException("Il lotto " + lottoId + " è di " + l.codiceMp
                            + ", non di " + mp + ". Materiale sbagliato!");
                }
                if (!"OK".equals(l.stato)) {
                    throw new IllegalStateException("Lotto " + lottoId + " in stato " + l.stato + ": non utilizzabile.");
                }
                if (!l.scadenza.isEmpty() && LocalDate.parse(l.scadenza).isBefore(LocalDate.now())) {
                    throw new IllegalStateException("Lotto " + lottoId + " scaduto il " + l.scadenza + ".");
                }
                double pesata = toDouble(txtPesata.getText());
                if (pesata <= 0) throw new IllegalStateException("Quantità pesata obbligatoria.");
                String umPesata = String.valueOf(cbUm.getSelectedItem());
                double pesataInUmLotto = convertiUm(pesata, umPesata, l.um);
                if (pesataInUmLotto > l.qtaResidua + 1e-9) {
                    throw new IllegalStateException("Residuo del lotto insufficiente ("
                            + formatNum(l.qtaResidua) + " " + l.um + ").");
                }

                double prev = toDouble(txtPrevista.getText());
                double tol = toDouble(txtTol.getText());
                double min = Math.rint(prev * (1 - tol / 100.0));
                double max = Math.rint(prev * (1 + tol / 100.0));
                String esito = "OK";
                if (prev > 0 && (pesata < min || pesata > max)) {
                    int c = JOptionPane.showConfirmDialog(this,
                            "Peso " + formatNum(pesata) + " fuori tolleranza (" + formatNum(min) + " - "
                                    + formatNum(max) + ").\nRegistrare comunque?",
                            "Fuori tolleranza", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (c != JOptionPane.YES_OPTION) return;
                    esito = "FUORI TOL.";
                }

                Prelievo p = new Prelievo();
                p.dataOra = LocalDateTime.now().format(TS);
                p.batch = batch;
                p.zona = String.valueOf(cbZona.getSelectedItem());
                p.codiceMp = l.codiceMp;
                p.lottoInterno = l.lottoInterno;
                p.qtaPrevista = prev;
                p.min = min;
                p.max = max;
                p.qtaPesata = pesata;
                p.um = umPesata;
                p.esito = esito;
                p.operatore = txtOperatore.getText().trim();
                contatorePrelievo++;
                p.id = String.format(Locale.ROOT, "P%02d-%07d", LocalDate.now().getYear() % 100, contatorePrelievo);
                prelievi.add(p);
                l.qtaResidua -= pesataInUmLotto;
                // DB-REM: salvaPrelievo(p);  // INSERT INTO dbo.BatchPrelievo + INSERT dbo.MpMovimento (in transazione)

                prelieviModel.addRow(new Object[]{p.dataOra, p.batch, p.zona, p.codiceMp, p.lottoInterno,
                        formatNum(p.qtaPrevista), formatNum(p.min), formatNum(p.max),
                        formatNum(p.qtaPesata) + " " + p.um, p.esito, p.operatore});
                refreshLottiTable();
                if (chkStampa.isSelected()) {
                    stampaZpl(p.zona, buildZplDose(p, l));
                }
                txtLotto.setText("");
                txtPesata.setText("");
                txtLotto.requestFocusInWindow();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton btnSetup = new JButton("Setup stampanti / etichetta");
        btnSetup.addActionListener(e -> apriSetupEtichette());
        buttons.add(btnConnetti);
        buttons.add(btnLeggi);
        buttons.add(btnSave);
        buttons.add(chkStampa);
        buttons.add(btnLista);
        buttons.add(btnSetup);

        JTable t = new JTable(prelieviModel);
        styleTable(t);

        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.CENTER);
        top.add(buttons, BorderLayout.SOUTH);

        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        return p;
    }

    // ------------------------------------------------------------------
    // Scheda 3: Tracciabilita' (ricerca avanti/indietro)
    // ------------------------------------------------------------------

    private JPanel buildRicercaPanel() {
        JTextField txtCerca = new JTextField(24);
        upper(txtCerca);
        JButton btnCerca = new JButton("Cerca");

        Runnable cerca = () -> {
            String q = txtCerca.getText().trim().toUpperCase(Locale.ROOT);
            ricercaModel.setRowCount(0);
            for (Prelievo p : prelievi) {
                Lotto l = lotti.get(p.lottoInterno);
                String lf = l == null ? "" : l.lottoFornitore;
                String bolla = l == null ? "" : l.bolla;
                boolean match = q.isEmpty()
                        || p.batch.contains(q) || p.lottoInterno.contains(q) || p.codiceMp.contains(q)
                        || lf.toUpperCase(Locale.ROOT).contains(q) || bolla.toUpperCase(Locale.ROOT).contains(q);
                if (match) {
                    ricercaModel.addRow(new Object[]{p.dataOra, p.batch, p.zona, p.codiceMp, p.lottoInterno,
                            lf, bolla, formatNum(p.qtaPesata) + " " + p.um, p.esito, p.operatore});
                }
            }
            // DB-REM: versione con DB = SELECT da dbo.BatchPrelievo JOIN dbo.MpLotto con gli stessi filtri.
        };
        btnCerca.addActionListener(e -> cerca.run());
        txtCerca.addActionListener(e -> cerca.run());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.add(new JLabel("Batch / lotto interno / lotto fornitore / bolla / Code MP:"));
        top.add(txtCerca);
        top.add(btnCerca);

        JTable t = new JTable(ricercaModel);
        styleTable(t);

        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        return p;
    }

    // ------------------------------------------------------------------
    // Utility
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Etichetta "dose" (una per prelievo) e invio alla stampante Zebra
    // ------------------------------------------------------------------

    /** Etichetta del prelievo: le misure si adattano al formato impostato (mm) e ai dpi. */
    private String buildZplDose(Prelievo p, Lotto l) {
        double dpmm = stampanteDpi / 25.4;
        int w = (int) Math.round(etichettaLarghMm * dpmm);
        int h = (int) Math.round(etichettaAltMm * dpmm);
        int m = (int) Math.round(3 * dpmm);
        int big = (int) Math.round(7 * dpmm);
        int mid = (int) Math.round(4.5 * dpmm);
        int small = (int) Math.round(3.4 * dpmm);
        int gap = (int) Math.round(1.5 * dpmm);
        int mag = Math.max(2, (int) Math.round(20 * dpmm / 25));
        int qrX = Math.max(m, w - m - 27 * mag);

        StringBuilder z = new StringBuilder();
        z.append("^XA\n^CI28\n^PW").append(w).append("\n^LL").append(h).append('\n');
        int y = m;
        y = zplLine(z, m, y, big, l.codiceMp, gap);
        y = zplLine(z, m, y, mid, trunc(l.descrizione, 28), gap);
        y = zplLine(z, m, y, mid, "Batch: " + p.batch + "  " + p.zona, gap);
        y = zplLine(z, m, y, small, "Lotto int.: " + p.lottoInterno, gap);
        y = zplLine(z, m, y, small, "Lotto forn.: " + l.lottoFornitore, gap);
        y = zplLine(z, m, y, big, "NETTO: " + formatNum(p.qtaPesata) + " " + p.um, gap);
        y = zplLine(z, m, y, small, "Tol: " + formatNum(p.min) + "-" + formatNum(p.max) + "  " + p.esito, gap);
        zplLine(z, m, y, small, p.dataOra + "  Op: " + p.operatore, gap);
        z.append("^FO").append(qrX).append(',').append(m)
                .append("^BQN,2,").append(mag).append("^FDQA,").append(p.id).append("^FS\n");
        z.append("^XZ\n");
        return z.toString();
    }

    private static int zplLine(StringBuilder z, int x, int y, int size, String text, int gap) {
        z.append("^FO").append(x).append(',').append(y).append("^A0N,").append(size).append(',').append(size)
                .append("^FD").append(zplSafe(text)).append("^FS\n");
        return y + size + gap;
    }

    private static String zplSafe(String s) {
        return s == null ? "" : s.replace('^', ' ').replace('~', ' ');
    }

    /** Invia lo ZPL alla stampante della zona (TCP 9100); senza IP configurato lo mostra a video. */
    private void stampaZpl(String zona, String zpl) {
        String ip = stampantiIp.getOrDefault(zona, "").trim();
        if (ip.isEmpty()) {
            JTextArea area = new JTextArea(zpl, 16, 50);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            area.setEditable(false);
            JOptionPane.showMessageDialog(this, new JScrollPane(area),
                    "Nessuna stampante per " + zona + " - ZPL dell'etichetta", JOptionPane.PLAIN_MESSAGE);
            return;
        }
        new Thread(() -> {
            try (java.net.Socket s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress(ip, PORTA_STAMPANTE), 3000);
                java.io.OutputStream o = s.getOutputStream();
                o.write(zpl.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                o.flush();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Stampa non riuscita su " + ip + ": " + ex.getMessage(),
                        "Erreur stampante", JOptionPane.ERROR_MESSAGE));
            }
        }, "stampa-etichetta").start();
    }

    private void apriSetupEtichette() {
        JPanel pnl = new JPanel(new GridLayout(0, 2, 6, 6));
        Map<String, JTextField> campi = new LinkedHashMap<>();
        Map<String, JTextField> campiBil = new LinkedHashMap<>();
        for (String z : ZONE) {
            JTextField t = new JTextField(stampantiIp.getOrDefault(z, ""), 14);
            campi.put(z, t);
            pnl.add(new JLabel("IP stampante " + z + ":"));
            pnl.add(t);
            JTextField tb = new JTextField(bilanceIp.getOrDefault(z, ""), 14);
            campiBil.put(z, tb);
            pnl.add(new JLabel("IP bilancia " + z + ":"));
            pnl.add(tb);
        }
        JTextField tPortaBil = new JTextField(String.valueOf(bilancePort), 6);
        pnl.add(new JLabel("Porta TCP bilance:"));
        pnl.add(tPortaBil);
        JTextField tw = new JTextField(String.valueOf(etichettaLarghMm), 5);
        JTextField th = new JTextField(String.valueOf(etichettaAltMm), 5);
        JComboBox<Integer> cbDpi = new JComboBox<>(new Integer[]{203, 300, 600});
        cbDpi.setSelectedItem(stampanteDpi);
        pnl.add(new JLabel("Larghezza etichetta (mm):"));
        pnl.add(tw);
        pnl.add(new JLabel("Altezza etichetta (mm):"));
        pnl.add(th);
        pnl.add(new JLabel("Risoluzione (dpi):"));
        pnl.add(cbDpi);
        pnl.add(new JLabel("(valori solo in memoria)"));
        pnl.add(new JLabel(""));

        if (JOptionPane.showConfirmDialog(this, pnl, "Setup stampanti ed etichetta",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        for (Map.Entry<String, JTextField> en : campi.entrySet()) {
            stampantiIp.put(en.getKey(), en.getValue().getText().trim());
        }
        for (Map.Entry<String, JTextField> en : campiBil.entrySet()) {
            bilanceIp.put(en.getKey(), en.getValue().getText().trim());
        }
        int porta = (int) toDouble(tPortaBil.getText());
        if (porta > 0 && porta <= 65535) {
            bilancePort = porta;
        }
        int lw = (int) toDouble(tw.getText());
        int ah = (int) toDouble(th.getText());
        if (lw >= 20 && ah >= 20) {
            etichettaLarghMm = lw;
            etichettaAltMm = ah;
        }
        stampanteDpi = (Integer) cbDpi.getSelectedItem();
    }

    private static double convertiUm(double v, String da, String a) {
        if (da.equals(a)) return v;
        if ("G".equals(da) && "KG".equals(a)) return v / 1000.0;
        if ("KG".equals(da) && "G".equals(a)) return v * 1000.0;
        throw new IllegalStateException("Unità incompatibili: peso in " + da + ", lotto in " + a + ".");
    }

    /** Peso della bilancia (kg) nell'UM scelta; NaN se non e' G o KG. */
    private static double daKg(double kg, String um) {
        if ("KG".equals(um)) return Math.round(kg * 1000.0) / 1000.0;
        if ("G".equals(um)) return Math.round(kg * 1000.0 * 1000.0) / 1000.0;
        return Double.NaN;
    }

    /**
     * Lettura di una bilancia Ohaus via TCP (stesso schema di SVC.BalanceService: invia "C"/"P"
     * per avviare lo stream continuo). Differenze volute: mantiene i decimali (BalanceService
     * arrotonda ai kg interi) e fornisce un peso STABILE: nessun '?' nella riga e almeno
     * 3 letture identiche (entro 0,5 g) nell'ultimo secondo circa. Il formato esatto della riga
     * Ohaus va verificato sul campo (la riga ricevuta e' nel tooltip del peso live).
     */
    static final class OhausScale {
        private static final java.util.regex.Pattern PESO = java.util.regex.Pattern.compile(
                "([-+]?\\d+(?:\\.\\d+)?)\\s*(kg|g)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);

        private final String host;
        private final int port;
        private final Object lock = new Object();
        private java.net.Socket socket;
        private volatile boolean running;
        private volatile double lastKg = Double.NaN;
        private volatile long lastTime;
        private volatile boolean flagInstabile;
        private volatile String lastLine = "";
        private final java.util.ArrayDeque<double[]> recenti = new java.util.ArrayDeque<>();

        OhausScale(String host, int port) {
            this.host = host;
            this.port = port;
        }

        /** Ritorna null se connessa, altrimenti il messaggio d'errore. */
        String connect() {
            try {
                java.net.Socket s = new java.net.Socket();
                s.setKeepAlive(true);
                s.setTcpNoDelay(true);
                s.connect(new java.net.InetSocketAddress(host, port), 8000);
                s.setSoTimeout(3000);
                synchronized (lock) {
                    socket = s;
                    running = true;
                }
                send("C\r\n");
                send("P\r\n");
                Thread t = new Thread(this::leggi, "ohaus-" + host);
                t.setDaemon(true);
                t.start();
                return null;
            } catch (Exception ex) {
                close();
                return ex.getMessage() == null ? ex.toString() : ex.getMessage();
            }
        }

        boolean isConnected() {
            synchronized (lock) {
                return running && socket != null && !socket.isClosed();
            }
        }

        String getLastLine() {
            return lastLine;
        }

        /** Ultimo peso ricevuto (kg), null se non ci sono dati recenti (oltre 3 s). */
        Double getKg() {
            if (Double.isNaN(lastKg) || System.currentTimeMillis() - lastTime > 3000) return null;
            return lastKg;
        }

        /** Peso (kg) solo se stabile, altrimenti null. */
        Double getStableKg() {
            Double kg = getKg();
            if (kg == null || flagInstabile) return null;
            synchronized (recenti) {
                if (recenti.size() < 3) return null;
                double first = recenti.peekFirst()[0];
                double last = recenti.peekLast()[0];
                if (last - first < 800) return null;
                double min = Double.MAX_VALUE;
                double max = -Double.MAX_VALUE;
                for (double[] r : recenti) {
                    min = Math.min(min, r[1]);
                    max = Math.max(max, r[1]);
                }
                if (max - min > 0.0005) return null;
            }
            return kg;
        }

        private void leggi() {
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[256];
            try {
                java.io.InputStream in;
                synchronized (lock) {
                    in = socket.getInputStream();
                }
                while (running) {
                    int n;
                    try {
                        n = in.read(buf);
                    } catch (java.net.SocketTimeoutException ex) {
                        continue;
                    }
                    if (n < 0) break;
                    for (int i = 0; i < n; i++) {
                        char c = (char) (buf[i] & 0xff);
                        if (c == '\n') {
                            elabora(sb.toString());
                            sb.setLength(0);
                        } else if (c != '\r' && sb.length() < 200) {
                            sb.append(c);
                        }
                    }
                }
            } catch (Exception ignored) {
                // stream chiuso o connessione persa: isConnected() diventera' false
            } finally {
                running = false;
            }
        }

        private void elabora(String raw) {
            String l = raw.trim().replace(',', '.');
            if (l.isEmpty()) return;
            lastLine = l;
            java.util.regex.Matcher m = PESO.matcher(l);
            if (!m.find()) return;
            double v;
            try {
                v = Double.parseDouble(m.group(1));
            } catch (NumberFormatException ex) {
                return;
            }
            double kg = m.group(2).equalsIgnoreCase("g") ? v / 1000.0 : v;
            flagInstabile = l.indexOf('?') >= 0;
            long now = System.currentTimeMillis();
            synchronized (recenti) {
                recenti.addLast(new double[]{now, kg});
                while (!recenti.isEmpty() && now - recenti.peekFirst()[0] > 1500) {
                    recenti.removeFirst();
                }
            }
            lastKg = kg;
            lastTime = now;
        }

        private void send(String cmd) {
            synchronized (lock) {
                try {
                    if (socket != null) {
                        socket.getOutputStream().write(cmd.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                        socket.getOutputStream().flush();
                    }
                } catch (Exception ignored) {
                }
            }
        }

        /** Chiusura pulita (ESC per fermare lo stream, poi FIN): l'Ohaus libera cosi' lo slot client. */
        void close() {
            send("\u001b\r\n");
            java.net.Socket s;
            synchronized (lock) {
                running = false;
                s = socket;
                socket = null;
            }
            if (s != null) {
                try { s.close(); } catch (Exception ignored) { }
            }
        }
    }

    private static void addField(JPanel panel, GridBagConstraints g, int row, int col, String label, JComponent field) {
        g.gridx = col; g.gridy = row;
        panel.add(new JLabel(label), g);
        g.gridx = col + 1;
        panel.add(field, g);
    }

    private static void styleTable(JTable t) {
        t.setRowHeight(26);
        t.setFont(new Font("SansSerif", Font.PLAIN, 14));
        t.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 14));
    }

    /** Il testo digitato diventa sempre maiuscolo (come i campi codice di DistintaBaseManager). */
    private static void upper(JTextField field) {
        ((javax.swing.text.AbstractDocument) field.getDocument()).setDocumentFilter(new javax.swing.text.DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String s, javax.swing.text.AttributeSet a)
                    throws javax.swing.text.BadLocationException {
                super.insertString(fb, offset, s == null ? null : s.toUpperCase(Locale.ROOT), a);
            }
            @Override
            public void replace(FilterBypass fb, int offset, int len, String s, javax.swing.text.AttributeSet a)
                    throws javax.swing.text.BadLocationException {
                super.replace(fb, offset, len, s == null ? null : s.toUpperCase(Locale.ROOT), a);
            }
        });
    }

    private static double toDouble(String s) {
        String v = s == null ? "" : s.trim().replace(",", ".");
        if (v.isEmpty()) return 0;
        try { return Double.parseDouble(v); } catch (NumberFormatException ex) { return 0; }
    }

    private static String formatNum(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.3f", v);
    }

    private static String trunc(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n);
    }

    static class Lotto {
        String lottoInterno = "";
        String codiceMp = "";
        String descrizione = "";
        String fornitore = "";
        String bolla = "";
        String lottoFornitore = "";
        String scadenza = "";
        double qtaIniziale;
        double qtaResidua;
        String um = "KG";
        String stato = "OK";
    }

    static class Prelievo {
        String id = "";
        String dataOra = "";
        String batch = "";
        String zona = "";
        String codiceMp = "";
        String lottoInterno = "";
        double qtaPrevista;
        double min;
        double max;
        double qtaPesata;
        String um = "G";
        String esito = "";
        String operatore = "";
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> new TracciabilitaManager().setVisible(true));
    }

    /* ======================================================================
     * DB-REM: SCRIPT TABELLE (SQL Server "Mondo") - da eseguire quando si riattiva il DB
     * ======================================================================
     *
     * CREATE TABLE dbo.MpLotto (
     *   LottoInterno   NVARCHAR(20)  NOT NULL PRIMARY KEY,
     *   CodiceMP       NVARCHAR(50)  NOT NULL,
     *   Descrizione    NVARCHAR(255) NOT NULL DEFAULT '',
     *   Fornitore      NVARCHAR(100) NOT NULL DEFAULT '',
     *   BollaIngresso  NVARCHAR(50)  NOT NULL DEFAULT '',
     *   LottoFornitore NVARCHAR(100) NOT NULL DEFAULT '',
     *   Scadenza       DATE NULL,
     *   QtaIniziale    FLOAT NOT NULL,
     *   UM             NVARCHAR(10)  NOT NULL DEFAULT 'KG',
     *   Stato          NVARCHAR(20)  NOT NULL DEFAULT 'OK',
     *   DataIngresso   DATETIME2 NOT NULL DEFAULT SYSDATETIME()
     * );
     *
     * CREATE TABLE dbo.MpMovimento (          -- registro append-only: residuo = SUM(Qta)
     *   Id           INT IDENTITY(1,1) PRIMARY KEY,
     *   LottoInterno NVARCHAR(20) NOT NULL,
     *   Tipo         NVARCHAR(20) NOT NULL,   -- INGRESSO / PRELIEVO / RETTIFICA / SCARTO
     *   Qta          FLOAT NOT NULL,          -- positivo in ingresso, negativo in prelievo
     *   Rif          NVARCHAR(50) NOT NULL DEFAULT '',  -- es. batch
     *   DataOra      DATETIME2 NOT NULL DEFAULT SYSDATETIME()
     * );
     *
     * CREATE TABLE dbo.BatchPrelievo (
     *   Id           INT IDENTITY(1,1) PRIMARY KEY,
     *   Batch        NVARCHAR(30) NOT NULL,
     *   Zona         NVARCHAR(30) NOT NULL,
     *   CodiceMP     NVARCHAR(50) NOT NULL,
     *   LottoInterno NVARCHAR(20) NOT NULL,
     *   QtaPrevista  FLOAT NOT NULL DEFAULT 0,
     *   PesoMin      FLOAT NOT NULL DEFAULT 0,
     *   PesoMax      FLOAT NOT NULL DEFAULT 0,
     *   QtaPesata    FLOAT NOT NULL,
     *   Esito        NVARCHAR(20) NOT NULL DEFAULT 'OK',
     *   Operatore    NVARCHAR(50) NOT NULL DEFAULT '',
     *   DataOra      DATETIME2 NOT NULL DEFAULT SYSDATETIME()
     * );
     *
     * Metodi Java da riattivare (salvaLotto, salvaPrelievo, aggiornaStatoLotto): usare
     * PreparedStatement con conn.setAutoCommit(false) e commit/rollback, come
     * saveDistinta() in DistintaBaseManager.
     */
}
