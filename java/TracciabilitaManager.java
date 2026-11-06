package MES;

import SVC.DBBamburyReader;
import SVC.DbOracleManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tracciabilita' materie prime.
 *
 * Tre schede:
 *  1. Ingresso merce : registra un lotto in arrivo (bolla, fornitore, lotto
 *     fornitore, quantita'), genera il LOTTO INTERNO e ne mostra l'etichetta ZPL.
 *  2. Prelievo zona  : registra cio' che una zona preleva per un batch (lotto
 *     interno, quantita' pesata) con controllo di tolleranza e residuo.
 *  3. Tracciabilita' : ricerca per lotto interno / lotto fornitore / batch / MP.
 *
 * DATABASE: usa la connessione condivisa SVC.DBBamburyReader.openSharedConnection() verso
 * SQL Server "Mondo" (SRV-BAMBURY), sulle tabelle di sql/tracciabilita_tabelle.sql.
 * Se il database non e' raggiungibile il programma parte in modalita' manuale: dati solo
 * in memoria, persi alla chiusura. Nessuna credenziale in questo file.
 */
public class TracciabilitaManager extends JFrame {

    private static final String VERSION = "V0.02";

    /** true = connessione condivisa disponibile; passa a false se il database non risponde. */
    private boolean dbAttivo = true;

    /** Merce arrivata ma non ancora validata (controllo qualita'/accettazione): non utilizzabile. */
    private static final String STATO_DA_VALIDARE = "DA VALIDARE";

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

    /** Avviso "lotto quasi finito" quando il residuo scende sotto questa % della quantita' iniziale. */
    private int avvisoResiduoPct = 20;
    private final Map<String, String> bilanceIp = new LinkedHashMap<>();
    private final Map<String, OhausScale> bilance = new LinkedHashMap<>();

    @Override
    public void dispose() {
        for (OhausScale s : bilance.values()) {
            s.close();
        }
        bilance.clear();
        if (sync != null) {
            sync.shutdownNow();
        }
        super.dispose();
    }

    private final DefaultTableModel lottiModel = new DefaultTableModel(new String[]{
            "Lotto interno", "Code MP", "Désignation", "Fournisseur", "Bolla", "Lotto fornitore",
            "Scadenza", "Qté initiale", "Qté résiduelle", "UM", "Stato", "Silo", "Validato da"}, 0) {
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
        caricaConfigurazione();

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.BOLD, 15));
        tabs.addTab("Ingresso merce", buildIngressoPanel());
        tabs.addTab("Prelievo zona", buildPrelievoPanel());
        tabs.addTab("Tracciabilità", buildRicercaPanel());

        JLabel avviso = barraStato;
        avviso.setOpaque(true);
        avviso.setBorder(new EmptyBorder(4, 4, 4, 4));
        avviso.setBackground(new Color(255, 244, 199));
        avviso.setText("  Modalità manuale: database non disponibile, i dati restano solo in memoria.");
        if (dbAttivo) {
            try {
                caricaDaDb();
                avviso.setText("  Collegato al database Mondo (SRV-BAMBURY).");
                avviso.setBackground(new Color(214, 240, 214));
                bannerBase = avviso.getText();
                avviaSincronizzazioneQuasar();
            } catch (Exception ex) {
                ex.printStackTrace();
                dbAttivo = false;
                avviso.setText("  Database non raggiungibile (" + messaggio(ex)
                        + "): modalità manuale, i dati restano solo in memoria.");
            }
        }

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
        JTextField txtSilo = new JTextField(8);   // solo per materiale che va in un silo (bolla di carico silo)
        JTextField txtLottoQuasar = new JTextField(10);   // lotto interno assegnato da Quasar (es. 26/303)
        JComboBox<String> cbUm = new JComboBox<>(new String[]{"KG", "G", "L", "PZ"});
        JComboBox<String> cbStato = new JComboBox<>(new String[]{STATO_DA_VALIDARE, "OK"});
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
        addField(form, g, row, 2, "Silo di carico (se da silo):", txtSilo);
        row++;
        addField(form, g, row, 0, "Lotto interno Quasar (es. 26/303):", txtLottoQuasar);

        JButton btnAdd = new JButton("Registra lotto + genera lotto interno");
        JButton btnEtichetta = new JButton("Stampa etichetta lotto (QR)");
        JCheckBox chkStampaLotto = new JCheckBox("Stampa dopo la registrazione", true);
        JSpinner spCopie = new JSpinner(new SpinnerNumberModel(1, 1, 50, 1));
        spCopie.setToolTipText("Numero di copie dell'etichetta");
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
                // Il lotto interno e' quello assegnato da Quasar nella ricezione merce (BAMP): se indicato
                // si usa quello (etichetta gia' stampata da Quasar); altrimenti ne genera uno nostro.
                String lq = txtLottoQuasar.getText().trim().toUpperCase(Locale.ROOT);
                if (!lq.isEmpty() && !lq.matches("\\d{2}/\\d{1,6}")) {
                    throw new IllegalStateException("Lotto Quasar non valido: formato AA/NNN (es. 26/303).");
                }
                if (!lq.isEmpty() && lotti.containsKey(lq)) {
                    throw new IllegalStateException("Il lotto " + lq + " è già registrato.");
                }
                l.lottoInterno = !lq.isEmpty() ? lq : (dbAttivo ? "" : nextLottoInterno());
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
                l.silo = txtSilo.getText().trim().toUpperCase(Locale.ROOT);
                if (dbAttivo) {
                    salvaLottoDb(l);   // assegna l.lottoInterno e scrive MpLotto + MpMovimento in transazione
                }
                lotti.put(l.lottoInterno, l);
                refreshLottiTable();
                if (chkStampaLotto.isSelected() && !stampantiIp.getOrDefault("RICEZIONE", "").trim().isEmpty()) {
                    stampaZpl("RICEZIONE", buildZplLotto(l, (Integer) spCopie.getValue()));
                }
                JOptionPane.showMessageDialog(this, "Lotto interno creato: " + l.lottoInterno
                        + "\n(stampare l'etichetta e applicarla al sacco/pallet)",
                        "Lotto registrato", JOptionPane.INFORMATION_MESSAGE);
                txtLottoForn.setText("");
                txtQta.setText("");
                txtSilo.setText("");
                txtLottoQuasar.setText("");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnPulisci.addActionListener(e -> {
            for (JTextField t : new JTextField[]{txtMp, txtDescr, txtFornitore, txtBolla, txtLottoForn, txtScadenza, txtQta, txtSilo, txtLottoQuasar}) {
                t.setText("");
            }
        });

        btnEtichetta.addActionListener(e -> {
            Lotto l = selectedLotto();
            if (l != null) stampaZpl("RICEZIONE", buildZplLotto(l, (Integer) spCopie.getValue()));
        });

        btnStato.addActionListener(e -> {
            Lotto l = selectedLotto();
            if (l == null) return;
            if (STATO_DA_VALIDARE.equals(l.stato)) {
                JOptionPane.showMessageDialog(this,
                        "Il lotto " + l.lottoInterno + " è ancora da validare: usare il pulsante \"Valida lotto\".",
                        "Lotto da validare", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if ("ESAURITO".equals(l.stato)) {
                JOptionPane.showMessageDialog(this,
                        "Il lotto " + l.lottoInterno + " è esaurito: se in realtà c'è ancora materiale usare \"Rettifica residuo\".",
                        "Lotto esaurito", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String nuovo = "OK".equals(l.stato) ? "BLOCCATO" : "OK";
            try {
                if (dbAttivo) {
                    aggiornaStatoDb(l.lottoInterno, l.stato, nuovo);
                }
            } catch (Exception ex) {
                erroreDb(ex);
                return;
            }
            l.stato = nuovo;
            refreshLottiTable();
        });

        JButton btnValida = new JButton("Valida lotto");
        btnValida.addActionListener(e -> {
            Lotto l = selectedLotto();
            if (l == null) return;
            if (!STATO_DA_VALIDARE.equals(l.stato)) {
                JOptionPane.showMessageDialog(this, "Il lotto " + l.lottoInterno + " non è in stato DA VALIDARE.",
                        "Validazione", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String chi = JOptionPane.showInputDialog(this,
                    "Validazione del lotto " + l.lottoInterno + " (" + l.codiceMp + ").\nNome di chi valida:",
                    "Valida lotto", JOptionPane.QUESTION_MESSAGE);
            if (chi == null || chi.trim().isEmpty()) return;
            try {
                if (dbAttivo) {
                    validaLottoDb(l.lottoInterno, chi.trim());
                }
            } catch (Exception ex) {
                erroreDb(ex);
                return;
            }
            l.stato = "OK";
            l.validatoDa = chi.trim() + " " + LocalDateTime.now().format(TS);
            refreshLottiTable();
        });

        // Chiusura/ripesatura: il residuo di un lotto a consegna si scala solo dalle pesate, quindi
        // con il tempo si scosta da quello fisico. Qui si registra il residuo reale (movimento RETTIFICA).
        JButton btnRettifica = new JButton("Rettifica residuo");
        btnRettifica.addActionListener(e -> {
            try {
                Lotto sel = selectedLotto();
                if (sel == null) return;
                String id = sel.lottoInterno;
                if (dbAttivo) {
                    ricaricaLottiDb();
                }
                Lotto l = lotti.get(id);
                if (l == null) throw new IllegalStateException("Lotto " + id + " non trovato.");
                JTextField tReale = new JTextField(10);
                JTextField tOp = new JTextField(12);
                JPanel pnl = new JPanel(new GridLayout(0, 2, 6, 6));
                pnl.add(new JLabel("Residuo a sistema:"));
                pnl.add(new JLabel(formatNum(l.qtaResidua) + " " + l.um));
                pnl.add(new JLabel("Residuo reale pesato (" + l.um + "):"));
                pnl.add(tReale);
                pnl.add(new JLabel("Operatore:"));
                pnl.add(tOp);
                if (JOptionPane.showConfirmDialog(this, pnl, "Rettifica residuo " + id,
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
                    return;
                }
                double reale;
                try {
                    reale = Double.parseDouble(tReale.getText().trim().replace(',', '.'));
                } catch (NumberFormatException ex) {
                    throw new IllegalStateException("Residuo reale non valido.");
                }
                if (reale < 0) throw new IllegalStateException("Il residuo non può essere negativo.");
                double delta = reale - l.qtaResidua;
                if (Math.abs(delta) < 1e-9) {
                    JOptionPane.showMessageDialog(this, "Il residuo a sistema è già allineato.",
                            "Rettifica", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                if (reale <= 1e-6 && JOptionPane.showConfirmDialog(this,
                        "Residuo reale 0: il lotto " + id + " verrà segnato ESAURITO. Confermare?",
                        "Lotto esaurito", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                    return;
                }
                if (dbAttivo) {
                    rettificaResiduoDb(id, delta, reale, tOp.getText().trim());
                }
                l.qtaResidua = reale;
                if (reale <= 1e-6) {
                    l.stato = "ESAURITO";
                } else if ("ESAURITO".equals(l.stato)) {
                    l.stato = "OK";
                }
                refreshLottiTable();
            } catch (Exception ex) {
                erroreDb(ex);
            }
        });

        JButton btnAggiorna = new JButton("Aggiorna dal DB");
        btnAggiorna.setEnabled(dbAttivo);
        btnAggiorna.addActionListener(e -> {
            try {
                sincronizzaValidazioniQuasarDb();
            } catch (Throwable ex) {
                System.err.println("Validazioni Quasar non lette: " + ex);
            }
            try {
                ricaricaLottiDb();
            } catch (Exception ex) {
                erroreDb(ex);
            }
        });

        JButton btnLottoForn = new JButton("Lotto fornitore...");
        btnLottoForn.setEnabled(dbAttivo);
        btnLottoForn.addActionListener(e -> {
            try {
                Lotto l = selectedLotto();
                if (l == null) return;
                String nuovo = JOptionPane.showInputDialog(this,
                        "Lotto del fornitore per " + l.lottoInterno + " (" + l.codiceMp + "):",
                        l.lottoFornitore);
                if (nuovo == null) return;
                nuovo = nuovo.trim();
                aggiornaLottoFornitoreDb(l.lottoInterno, nuovo);
                l.lottoFornitore = nuovo;
                refreshLottiTable();
            } catch (Exception ex) {
                erroreDb(ex);
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        buttons.add(btnAdd);
        buttons.add(btnPulisci);
        buttons.add(btnEtichetta);
        buttons.add(new JLabel("Copie:"));
        buttons.add(spCopie);
        buttons.add(chkStampaLotto);
        buttons.add(btnValida);
        buttons.add(btnStato);
        buttons.add(btnRettifica);
        buttons.add(btnLottoForn);
        buttons.add(btnAggiorna);

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

    /**
     * Etichetta del lotto con QR da attaccare al box: il QR contiene solo il lotto interno
     * (es. 26/303), cosi' la pistola lo legge direttamente nel campo "Lotto interno".
     * Misure scalate sul formato e sui dpi impostati; "copie" usa ^PQ.
     */
    private String buildZplLotto(Lotto l, int copie) {
        double dpmm = stampanteDpi / 25.4;
        int w = (int) Math.round(etichettaLarghMm * dpmm);
        int h = (int) Math.round(etichettaAltMm * dpmm);
        int m = (int) Math.round(3 * dpmm);
        int big = (int) Math.round(9 * dpmm);
        int mid = (int) Math.round(5 * dpmm);
        int small = (int) Math.round(3.6 * dpmm);
        int gap = (int) Math.round(1.5 * dpmm);
        int mag = Math.max(3, (int) Math.round(28 * dpmm / 25));
        int qrX = Math.max(m, w - m - 25 * mag);

        StringBuilder z = new StringBuilder();
        z.append("^XA\n^CI28\n^PW").append(w).append("\n^LL").append(h).append('\n');
        int y = m;
        y = zplLine(z, m, y, big, l.lottoInterno, gap);
        y = zplLine(z, m, y, mid, l.codiceMp + " " + trunc(l.descrizione, 18), gap);
        y = zplLine(z, m, y, small, "Forn.: " + trunc(l.fornitore, 22), gap);
        y = zplLine(z, m, y, small, "Lotto forn.: " + trunc(l.lottoFornitore, 18), gap);
        y = zplLine(z, m, y, small, "Bolla: " + trunc(l.bolla, 20), gap);
        y = zplLine(z, m, y, mid, "Qta: " + formatNum(l.qtaIniziale) + " " + l.um, gap);
        zplLine(z, m, y, small, "Scad.: " + (l.scadenza.isEmpty() ? "-" : l.scadenza)
                + (l.silo.isEmpty() ? "" : "  Silo " + l.silo), gap);
        z.append("^FO").append(qrX).append(',').append(m).append("^BQN,2,").append(mag)
                .append("^FDQA,").append(zplSafe(l.lottoInterno)).append("^FS\n");
        z.append("^PQ").append(Math.max(1, copie)).append("\n^XZ\n");
        return z.toString();
    }

    private String nextLottoInterno() {
        contatoreLotto++;
        // Solo modalita' manuale: con il DB il progressivo viene dalla sequenza dbo.SeqLottoInterno.
        return String.format(Locale.ROOT, "L%02d-%06d", LocalDate.now().getYear() % 100, contatoreLotto);
    }

    private void refreshLottiTable() {
        lottiModel.setRowCount(0);
        for (Lotto l : lotti.values()) {
            lottiModel.addRow(new Object[]{l.lottoInterno, l.codiceMp, l.descrizione, l.fornitore, l.bolla,
                    l.lottoFornitore, l.scadenza, formatNum(l.qtaIniziale), formatNum(l.qtaResidua), l.um, l.stato, l.silo, l.validatoDa});
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
        JCheckBox chkParziale = new JCheckBox("Dose parziale (la completo con un altro lotto)");
        JComboBox<String> cbUm = new JComboBox<>(new String[]{"G", "KG", "L", "PZ"});
        JLabel lblBilancia = new JLabel("Bilancia: non connessa");
        lblBilancia.setFont(new Font("SansSerif", Font.BOLD, 16));
        lblBilancia.setForeground(Color.GRAY);
        JButton btnConnetti = new JButton("Connetti bilancia");
        JButton btnLeggi = new JButton("Leggi peso dalla bilancia");
        final String[] pesoLetto = {null};   // ultimo peso copiato dalla bilancia (per distinguere il peso digitato a mano)
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

        // Info live: residuo del lotto scansionato e avanzamento della dose (previsto / gia' pesato).
        JLabel lblLotto = new JLabel("Lotto: -");
        lblLotto.setFont(new Font("SansSerif", Font.BOLD, 14));
        JLabel lblDose = new JLabel("Dose: -");
        lblDose.setFont(new Font("SansSerif", Font.BOLD, 14));
        Runnable aggiornaInfo = () -> {
            String id = txtLotto.getText().trim().toUpperCase(Locale.ROOT);
            Lotto l = lotti.get(id);
            Color rosso = new Color(180, 0, 0);
            if (id.isEmpty()) {
                lblLotto.setText("Lotto: -");
                lblLotto.setForeground(Color.DARK_GRAY);
            } else if (l == null) {
                lblLotto.setText("Lotto " + id + ": sconosciuto");
                lblLotto.setForeground(rosso);
            } else {
                double pct = l.qtaIniziale > 0 ? 100.0 * l.qtaResidua / l.qtaIniziale : 0;
                lblLotto.setText("Lotto " + id + " - " + l.codiceMp + "   residuo " + formatNum(l.qtaResidua) + " "
                        + l.um + " (" + Math.round(pct) + "% dell'iniziale)   scad. "
                        + (l.scadenza.isEmpty() ? "-" : l.scadenza) + "   stato " + l.stato);
                lblLotto.setForeground(!"OK".equals(l.stato) || pct <= avvisoResiduoPct ? rosso : new Color(0, 110, 0));
            }
            String batch = txtBatch.getText().trim().toUpperCase(Locale.ROOT);
            String mp = l != null ? l.codiceMp : txtMp.getText().trim().toUpperCase(Locale.ROOT);
            double prev = toDouble(txtPrevista.getText());
            if (prev > 0 && !batch.isEmpty() && !mp.isEmpty()) {
                String um = String.valueOf(cbUm.getSelectedItem());
                double gia = giaPesatoMemoria(batch, String.valueOf(cbZona.getSelectedItem()), mp, um);
                lblDose.setText("Dose " + mp + ": già pesato " + formatNum(gia) + " / previsto " + formatNum(prev)
                        + " " + um + " - mancano " + formatNum(Math.max(0, prev - gia)));
            } else {
                lblDose.setText("Dose: -");
            }
        };
        DocumentListener infoDl = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { aggiornaInfo.run(); }
            @Override public void removeUpdate(DocumentEvent e) { aggiornaInfo.run(); }
            @Override public void changedUpdate(DocumentEvent e) { aggiornaInfo.run(); }
        };
        txtLotto.getDocument().addDocumentListener(infoDl);
        txtBatch.getDocument().addDocumentListener(infoDl);
        txtMp.getDocument().addDocumentListener(infoDl);
        txtPrevista.getDocument().addDocumentListener(infoDl);
        cbZona.addActionListener(ev -> aggiornaInfo.run());
        cbUm.addActionListener(ev -> aggiornaInfo.run());

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
        row++;
        g.gridx = 0; g.gridy = row; g.gridwidth = 4;
        form.add(lblLotto, g);
        row++;
        g.gridy = row;
        form.add(lblDose, g);
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
            pesoLetto[0] = formatNum(v);
            txtPesata.setText(pesoLetto[0]);
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
                if (dbAttivo) {
                    ricaricaLottiDb();   // stato, residui e lotti inseriti da altre postazioni
                }
                Lotto l = lotti.get(lottoId);
                if (l == null) throw new IllegalStateException("Lotto interno " + lottoId + " inesistente.");
                String mp = txtMp.getText().trim().toUpperCase(Locale.ROOT);
                if (!mp.isEmpty() && !mp.equals(l.codiceMp)) {
                    throw new IllegalStateException("Il lotto " + lottoId + " è di " + l.codiceMp
                            + ", non di " + mp + ". Materiale sbagliato!");
                }
                if (dbAttivo && STATO_DA_VALIDARE.equals(l.stato) && l.bolla.startsWith("MOMP/")) {
                    // Validazione fatta nel frattempo in Quasar (VALID_TES): si allinea prima di bloccare.
                    try {
                        ValidazioneQuasar vq = validazioniQuasar(java.util.Collections.singletonList(lottoId)).get(lottoId);
                        if (vq != null) {
                            validaLottoDb(lottoId, etichettaValidatore(vq.utente), vq.quando);
                            l.stato = "OK";
                            l.validatoDa = (etichettaValidatore(vq.utente) + " "
                                    + (vq.quando == null ? "" : vq.quando.toLocalDateTime().format(TS))).trim();
                        }
                    } catch (Exception ex) {
                        System.err.println("Controllo validazione Quasar non riuscito: " + messaggio(ex));
                    }
                }
                if (STATO_DA_VALIDARE.equals(l.stato)) {
                    JOptionPane.showMessageDialog(this,
                            "MERCE ANCORA DA VALIDARE\n\nIl lotto " + lottoId + " (" + l.codiceMp
                                    + ") è arrivato ma non è stato ancora validato.\n"
                                    + "Non può essere utilizzato: avvisare il responsabile qualità/magazzino.",
                            "Merce da validare", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (!"OK".equals(l.stato)) {
                    throw new IllegalStateException("Lotto " + lottoId + " in stato " + l.stato + ": non utilizzabile.");
                }
                if (!l.scadenza.isEmpty() && LocalDate.parse(l.scadenza).isBefore(LocalDate.now())) {
                    throw new IllegalStateException("Lotto " + lottoId + " scaduto il " + l.scadenza + ".");
                }
                // Avviso FEFO: esiste un altro lotto utilizzabile della stessa MP che scade prima?
                boolean fefoDeroga = false;
                List<Lotto> prima = lottiFefoMigliori(l);
                if (!prima.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (Lotto o : prima) {
                        sb.append("  ").append(o.lottoInterno).append("  scad. ").append(o.scadenza)
                                .append("  residuo ").append(formatNum(o.qtaResidua)).append(' ').append(o.um).append('\n');
                    }
                    int c = JOptionPane.showConfirmDialog(this,
                            "Attenzione FEFO: per " + l.codiceMp + " ci sono lotti che scadono prima di "
                                    + lottoId + (l.scadenza.isEmpty() ? " (senza scadenza)" : " (scad. " + l.scadenza + ")")
                                    + ":\n\n" + sb + "\nUsare comunque questo lotto?",
                            "Avviso FEFO", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (c != JOptionPane.YES_OPTION) return;
                    fefoDeroga = true;
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
                String zonaSel = String.valueOf(cbZona.getSelectedItem());
                // La tolleranza vale sul TOTALE della dose (pesato in precedenza con altri lotti + questo).
                double gia = dbAttivo ? giaPesatoDb(batch, zonaSel, l.codiceMp, umPesata)
                        : giaPesatoMemoria(batch, zonaSel, l.codiceMp, umPesata);
                double totale = gia + pesata;
                String esito = "OK";
                if (prev > 0) {
                    String dettaglio = gia > 0 ? "Totale dose " + formatNum(totale) + " (già pesato "
                            + formatNum(gia) + " + questo " + formatNum(pesata) + ")" : "Peso " + formatNum(pesata);
                    if (chkParziale.isSelected()) {
                        if (totale > max) {
                            int c = JOptionPane.showConfirmDialog(this,
                                    dettaglio + " supera il massimo (" + formatNum(max) + ").\nRegistrare comunque?",
                                    "Fuori tolleranza", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                            if (c != JOptionPane.YES_OPTION) return;
                            esito = "FUORI TOL.";
                        } else {
                            esito = "PARZIALE";
                        }
                    } else if (totale < min || totale > max) {
                        int c = JOptionPane.showConfirmDialog(this,
                                dettaglio + " fuori tolleranza (" + formatNum(min) + " - " + formatNum(max)
                                        + ").\nRegistrare comunque?",
                                "Fuori tolleranza", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                        if (c != JOptionPane.YES_OPTION) return;
                        esito = "FUORI TOL.";
                    }
                }

                if (fefoDeroga) {
                    esito += " + DEROGA FEFO";
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
                p.manuale = !txtPesata.getText().trim().equals(pesoLetto[0]);
                if (dbAttivo) {
                    // assegna p.id, ricontrolla stato/residuo e scrive BatchPrelievo + MpMovimento in transazione
                    salvaPrelievoDb(p, l, pesataInUmLotto);
                } else {
                    contatorePrelievo++;
                    p.id = String.format(Locale.ROOT, "P%02d-%07d", LocalDate.now().getYear() % 100, contatorePrelievo);
                }
                prelievi.add(p);
                l.qtaResidua -= pesataInUmLotto;
                if (l.qtaResidua <= 1e-6) {
                    l.stato = "ESAURITO";   // nel DB lo imposta salvaPrelievoDb nella stessa transazione
                }

                addPrelievoRow(p);
                refreshLottiTable();
                if (chkStampa.isSelected()) {
                    stampaZpl(p.zona, buildZplDose(p, l));
                }
                avvisoResiduo(l, prev, umPesata);
                chkParziale.setSelected(false);
                txtLotto.setText("");
                txtPesata.setText("");
                pesoLetto[0] = null;
                txtLotto.requestFocusInWindow();
            } catch (Exception ex) {
                erroreDb(ex);
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton btnSetup = new JButton("Setup stampanti / etichetta");
        btnSetup.addActionListener(e -> apriSetupEtichette());
        buttons.add(btnConnetti);
        buttons.add(btnLeggi);
        buttons.add(btnSave);
        buttons.add(chkStampa);
        buttons.add(chkParziale);
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

        // Lotti trovati (ingresso: bolla, fornitore, scadenza, stato, residuo) - sopra ai prelievi.
        DefaultTableModel ricercaLottiModel = new DefaultTableModel(new String[]{
                "Lotto interno", "Code MP", "Désignation", "Fournisseur", "Bolla", "Lotto fornitore",
                "Scadenza", "Qté initiale", "Qté résiduelle", "UM", "Stato", "Validato da"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        Runnable cerca = () -> {
            String q = txtCerca.getText().trim().toUpperCase(Locale.ROOT);
            if (dbAttivo) {
                try {
                    ricaricaLottiDb();   // stato e residui aggiornati
                } catch (Exception ex) {
                    erroreDb(ex);
                }
            }
            ricercaLottiModel.setRowCount(0);
            for (Lotto l : lotti.values()) {
                String chiave = (l.lottoInterno + " " + l.codiceMp + " " + l.descrizione + " " + l.fornitore + " "
                        + l.bolla + " " + l.lottoFornitore).toUpperCase(Locale.ROOT);
                if (q.isEmpty() || chiave.contains(q)) {
                    ricercaLottiModel.addRow(new Object[]{l.lottoInterno, l.codiceMp, l.descrizione, l.fornitore,
                            l.bolla, l.lottoFornitore, l.scadenza, formatNum(l.qtaIniziale), formatNum(l.qtaResidua),
                            l.um, l.stato, l.validatoDa});
                }
            }
            ricercaModel.setRowCount(0);
            if (dbAttivo) {
                try {
                    cercaDb(q);
                } catch (Exception ex) {
                    erroreDb(ex);
                }
                return;
            }
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
        };
        btnCerca.addActionListener(e -> cerca.run());
        txtCerca.addActionListener(e -> cerca.run());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.add(new JLabel("Batch / lotto interno / lotto fornitore / bolla / Code MP:"));
        top.add(txtCerca);
        top.add(btnCerca);

        JTable tLotti = new JTable(ricercaLottiModel);
        styleTable(tLotti);
        JScrollPane spLotti = new JScrollPane(tLotti);
        spLotti.setBorder(BorderFactory.createTitledBorder("Lotti trovati (ingresso)"));

        JTable t = new JTable(ricercaModel);
        styleTable(t);
        JScrollPane spPrelievi = new JScrollPane(t);
        spPrelievi.setBorder(BorderFactory.createTitledBorder("Utilizzo nei batch (prelievi)"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, spLotti, spPrelievi);
        split.setResizeWeight(0.4);

        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        p.add(top, BorderLayout.NORTH);
        p.add(split, BorderLayout.CENTER);
        return p;
    }

    // ------------------------------------------------------------------
    // Accesso al database (attivo solo se configurato: vedi caricaConfigurazione)
    // ------------------------------------------------------------------

    private void caricaConfigurazione() {
        caricaImpostazioniPostazione();
    }

    /** Impostazioni di questo PC (IP stampanti/bilance, formato etichetta): non contengono credenziali. */
    private static final String POSTAZIONE_FILE = "tracciabilita-postazione.properties";

    private void caricaImpostazioniPostazione() {
        java.io.File f = new java.io.File(POSTAZIONE_FILE);
        if (!f.isFile()) return;
        java.util.Properties pr = new java.util.Properties();
        try (java.io.InputStream in = new java.io.FileInputStream(f)) {
            pr.load(in);
        } catch (Exception ex) {
            System.err.println("Impossibile leggere " + POSTAZIONE_FILE + ": " + ex.getMessage());
            return;
        }
        for (String z : ZONE) {
            stampantiIp.put(z, pr.getProperty("stampante." + z, "").trim());
            bilanceIp.put(z, pr.getProperty("bilancia." + z, "").trim());
        }
        bilancePort = (int) toDouble(pr.getProperty("bilancia.porta", String.valueOf(bilancePort)));
        etichettaLarghMm = (int) toDouble(pr.getProperty("etichetta.larghezzaMm", String.valueOf(etichettaLarghMm)));
        etichettaAltMm = (int) toDouble(pr.getProperty("etichetta.altezzaMm", String.valueOf(etichettaAltMm)));
        stampanteDpi = (int) toDouble(pr.getProperty("stampante.dpi", String.valueOf(stampanteDpi)));
        avvisoResiduoPct = (int) toDouble(pr.getProperty("avviso.residuoPct", String.valueOf(avvisoResiduoPct)));
        stampantiIp.put("RICEZIONE", pr.getProperty("stampante.RICEZIONE", "").trim());
        quasarPrimoNumero = (int) toDouble(pr.getProperty("quasar.primoNumero", "0"));
    }

    private void salvaImpostazioniPostazione() {
        java.util.Properties pr = new java.util.Properties();
        for (String z : ZONE) {
            pr.setProperty("stampante." + z, stampantiIp.getOrDefault(z, ""));
            pr.setProperty("bilancia." + z, bilanceIp.getOrDefault(z, ""));
        }
        pr.setProperty("bilancia.porta", String.valueOf(bilancePort));
        pr.setProperty("etichetta.larghezzaMm", String.valueOf(etichettaLarghMm));
        pr.setProperty("etichetta.altezzaMm", String.valueOf(etichettaAltMm));
        pr.setProperty("stampante.dpi", String.valueOf(stampanteDpi));
        pr.setProperty("avviso.residuoPct", String.valueOf(avvisoResiduoPct));
        pr.setProperty("stampante.RICEZIONE", stampantiIp.getOrDefault("RICEZIONE", ""));
        pr.setProperty("quasar.primoNumero", String.valueOf(quasarPrimoNumero));
        try (java.io.OutputStream out = new java.io.FileOutputStream(POSTAZIONE_FILE)) {
            pr.store(out, "Impostazioni di questa postazione - TracciabilitaManager");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Impostazioni non salvate: " + messaggio(ex),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Connection openConnection() throws Exception {
        return DBBamburyReader.openSharedConnection();
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static String messaggio(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private void erroreDb(Exception ex) {
        if (!(ex instanceof IllegalStateException)) {
            ex.printStackTrace();
        }
        JOptionPane.showMessageDialog(this, messaggio(ex), "Erreur", JOptionPane.ERROR_MESSAGE);
    }

    /** Carica lotti e ultimi prelievi dal database all'avvio. */
    private void caricaDaDb() throws Exception {
        try (Connection conn = openConnection()) {
            leggiLotti(conn);
            prelievi.clear();
            prelieviModel.setRowCount(0);
            String sql = "SELECT TOP 1000 IdPrelievo, BatchId, Zona, CodiceMP, LottoInterno, QtaPrevista, PesoMin, "
                    + "PesoMax, QtaPesata, UM, Esito, PesoManuale, Operatore, "
                    + "CONVERT(VARCHAR(19), DataOra, 120) AS DataOra FROM dbo.BatchPrelievo ORDER BY Id DESC";
            List<Prelievo> recenti = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Prelievo p = new Prelievo();
                    p.id = nz(rs.getString("IdPrelievo"));
                    p.batch = nz(rs.getString("BatchId"));
                    p.zona = nz(rs.getString("Zona"));
                    p.codiceMp = nz(rs.getString("CodiceMP"));
                    p.lottoInterno = nz(rs.getString("LottoInterno"));
                    p.qtaPrevista = rs.getDouble("QtaPrevista");
                    p.min = rs.getDouble("PesoMin");
                    p.max = rs.getDouble("PesoMax");
                    p.qtaPesata = rs.getDouble("QtaPesata");
                    p.um = nz(rs.getString("UM"));
                    p.esito = nz(rs.getString("Esito"));
                    p.manuale = rs.getBoolean("PesoManuale");
                    p.operatore = nz(rs.getString("Operatore"));
                    p.dataOra = nz(rs.getString("DataOra"));
                    recenti.add(p);
                }
            }
            for (int i = recenti.size() - 1; i >= 0; i--) {   // dal piu' vecchio al piu' recente
                prelievi.add(recenti.get(i));
                addPrelievoRow(recenti.get(i));
            }
        }
        refreshLottiTable();
    }

    private void ricaricaLottiDb() throws Exception {
        try (Connection conn = openConnection()) {
            leggiLotti(conn);
        }
        refreshLottiTable();
    }

    /** Legge tutti i lotti; il residuo e' la somma dei movimenti (MpMovimento). */
    private void leggiLotti(Connection conn) throws SQLException {
        String sql = "SELECT l.LottoInterno, l.CodiceMP, l.Descrizione, l.Fornitore, l.BollaIngresso, "
                + "l.LottoFornitore, CONVERT(VARCHAR(10), l.Scadenza, 23) AS Scadenza, l.QtaIniziale, l.UM, "
                + "l.Stato, l.ValidatoDa, CONVERT(VARCHAR(19), l.DataValidazione, 120) AS DataValidazione, "
                + "COALESCE((SELECT TOP 1 s.Silo FROM dbo.SiloCarichi s WHERE s.LottoInterno = l.LottoInterno "
                + "ORDER BY s.DataCarico DESC), '') AS Silo, "
                + "COALESCE((SELECT SUM(m.Qta) FROM dbo.MpMovimento m WHERE m.LottoInterno = l.LottoInterno), 0) AS Residuo "
                + "FROM dbo.MpLotto l ORDER BY l.DataIngresso, l.LottoInterno";
        Map<String, Lotto> letti = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Lotto l = new Lotto();
                l.lottoInterno = nz(rs.getString("LottoInterno"));
                l.codiceMp = nz(rs.getString("CodiceMP"));
                l.descrizione = nz(rs.getString("Descrizione"));
                l.fornitore = nz(rs.getString("Fornitore"));
                l.bolla = nz(rs.getString("BollaIngresso"));
                l.lottoFornitore = nz(rs.getString("LottoFornitore"));
                l.scadenza = nz(rs.getString("Scadenza"));
                l.qtaIniziale = rs.getDouble("QtaIniziale");
                l.qtaResidua = rs.getDouble("Residuo");
                l.um = nz(rs.getString("UM"));
                l.stato = nz(rs.getString("Stato"));
                l.silo = nz(rs.getString("Silo"));
                String chi = nz(rs.getString("ValidatoDa"));
                String quando = nz(rs.getString("DataValidazione"));
                l.validatoDa = (chi + " " + quando).trim();
                letti.put(l.lottoInterno, l);
            }
        }
        lotti.clear();
        lotti.putAll(letti);
    }

    private int prossimoDaSequenza(Connection conn, String sequenza) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT NEXT VALUE FOR " + sequenza);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Nuovo lotto: numero dalla sequenza, riga in MpLotto e movimento INGRESSO, in una transazione. */
    private void salvaLottoDb(Lotto l) throws Exception {
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            try {
                if (l.lottoInterno.isEmpty()) {   // vuoto = nessun lotto Quasar indicato: genera il nostro
                    int n = prossimoDaSequenza(conn, "dbo.SeqLottoInterno");
                    l.lottoInterno = String.format(Locale.ROOT, "L%02d-%06d", LocalDate.now().getYear() % 100, n);
                }
                String sql = "INSERT INTO dbo.MpLotto (LottoInterno, CodiceMP, Descrizione, Fornitore, BollaIngresso, "
                        + "LottoFornitore, Scadenza, QtaIniziale, UM, Stato, ValidatoDa, DataValidazione) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?, CASE WHEN ? = '' THEN NULL ELSE COALESCE(?, SYSDATETIME()) END)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, l.lottoInterno);
                    ps.setString(2, l.codiceMp);
                    ps.setString(3, l.descrizione);
                    ps.setString(4, l.fornitore);
                    ps.setString(5, l.bolla);
                    ps.setString(6, l.lottoFornitore);
                    if (l.scadenza.isEmpty()) {
                        ps.setNull(7, java.sql.Types.DATE);
                    } else {
                        ps.setDate(7, java.sql.Date.valueOf(l.scadenza));
                    }
                    ps.setDouble(8, l.qtaIniziale);
                    ps.setString(9, l.um);
                    ps.setString(10, l.stato);
                    ps.setString(11, trunc(l.validatoDa, 100));
                    ps.setString(12, trunc(l.validatoDa, 100));
                    if (l.validatoQuando == null) {
                        ps.setNull(13, java.sql.Types.TIMESTAMP);
                    } else {
                        ps.setTimestamp(13, l.validatoQuando);
                    }
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO dbo.MpMovimento (LottoInterno, Tipo, Qta, Rif) VALUES (?, 'INGRESSO', ?, ?)")) {
                    ps.setString(1, l.lottoInterno);
                    ps.setDouble(2, l.qtaIniziale);
                    ps.setString(3, trunc(l.bolla, 50));
                    ps.executeUpdate();
                }
                if (!l.silo.isEmpty()) {
                    // Carico silo registrato da bolla come gli altri ingressi: il lotto risulta nel silo indicato.
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO dbo.SiloCarichi (Silo, LottoInterno, Qta) VALUES (?, ?, ?)")) {
                        ps.setString(1, trunc(l.silo, 30));
                        ps.setString(2, l.lottoInterno);
                        ps.setDouble(3, l.qtaIniziale);
                        ps.executeUpdate();
                    }
                }
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                if (ex instanceof SQLException
                        && (((SQLException) ex).getErrorCode() == 2627 || ((SQLException) ex).getErrorCode() == 2601)) {
                    throw new IllegalStateException("Il lotto " + l.lottoInterno + " è già registrato.");
                }
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /** Cambia stato solo se nel DB e' ancora quello atteso (evita di scavalcare una validazione fatta altrove). */
    private void aggiornaStatoDb(String lotto, String statoAtteso, String nuovoStato) throws Exception {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE dbo.MpLotto SET Stato = ? WHERE LottoInterno = ? AND Stato = ?")) {
            ps.setString(1, nuovoStato);
            ps.setString(2, lotto);
            ps.setString(3, statoAtteso);
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException("Lo stato del lotto " + lotto
                        + " è cambiato nel frattempo: premere \"Aggiorna dal DB\" e riprovare.");
            }
        }
    }

    private void validaLottoDb(String lotto, String chi) throws Exception {
        validaLottoDb(lotto, chi, null);
    }

    /** Porta il lotto a OK; "quando" e' la data reale di validazione (se nota), altrimenti l'ora attuale. */
    private void validaLottoDb(String lotto, String chi, java.sql.Timestamp quando) throws Exception {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE dbo.MpLotto SET Stato = 'OK', ValidatoDa = ?, DataValidazione = COALESCE(?, SYSDATETIME()) "
                             + "WHERE LottoInterno = ? AND Stato = 'DA VALIDARE'")) {
            ps.setString(1, trunc(chi, 100));
            if (quando == null) {
                ps.setNull(2, java.sql.Types.TIMESTAMP);
            } else {
                ps.setTimestamp(2, quando);
            }
            ps.setString(3, lotto);
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException("Il lotto " + lotto
                        + " non è più DA VALIDARE: premere \"Aggiorna dal DB\".");
            }
        }
    }

    /**
     * Registra il prelievo: sotto blocco ricontrolla stato e residuo (un'altra postazione potrebbe
     * aver consumato il lotto), poi scrive BatchPrelievo e il movimento PRELIEVO (quantita' negativa
     * nell'UM del lotto) nella stessa transazione.
     */
    private void salvaPrelievoDb(Prelievo p, Lotto l, double qtaInUmLotto) throws Exception {
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            try {
                double residuo;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COALESCE(SUM(Qta), 0) FROM dbo.MpMovimento WITH (UPDLOCK, HOLDLOCK) WHERE LottoInterno = ?")) {
                    ps.setString(1, l.lottoInterno);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        residuo = rs.getDouble(1);
                    }
                }
                if (qtaInUmLotto > residuo + 1e-9) {
                    throw new IllegalStateException("Residuo del lotto insufficiente (" + formatNum(residuo)
                            + " " + l.um + "): aggiornato da un'altra postazione.");
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT Stato FROM dbo.MpLotto WHERE LottoInterno = ?")) {
                    ps.setString(1, l.lottoInterno);
                    try (ResultSet rs = ps.executeQuery()) {
                        String stato = rs.next() ? nz(rs.getString(1)) : "";
                        if (!"OK".equals(stato)) {
                            throw new IllegalStateException("Il lotto " + l.lottoInterno + " ora è in stato '"
                                    + stato + "': non utilizzabile.");
                        }
                    }
                }
                int n = prossimoDaSequenza(conn, "dbo.SeqPrelievo");
                p.id = String.format(Locale.ROOT, "P%02d-%07d", LocalDate.now().getYear() % 100, n);
                String sql = "INSERT INTO dbo.BatchPrelievo (IdPrelievo, BatchId, Zona, CodiceMP, LottoInterno, "
                        + "QtaPrevista, PesoMin, PesoMax, QtaPesata, UM, Esito, PesoManuale, Operatore) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, p.id);
                    ps.setString(2, p.batch);
                    ps.setString(3, trunc(p.zona, 30));
                    ps.setString(4, p.codiceMp);
                    ps.setString(5, p.lottoInterno);
                    ps.setDouble(6, p.qtaPrevista);
                    ps.setDouble(7, p.min);
                    ps.setDouble(8, p.max);
                    ps.setDouble(9, p.qtaPesata);
                    ps.setString(10, p.um);
                    ps.setString(11, trunc(p.esito, 60));
                    ps.setBoolean(12, p.manuale);
                    ps.setString(13, trunc(p.operatore, 50));
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO dbo.MpMovimento (LottoInterno, Tipo, Qta, Rif, Operatore) "
                                + "VALUES (?, 'PRELIEVO', ?, ?, ?)")) {
                    ps.setString(1, l.lottoInterno);
                    ps.setDouble(2, -qtaInUmLotto);
                    ps.setString(3, p.id);
                    ps.setString(4, trunc(p.operatore, 50));
                    ps.executeUpdate();
                }
                if (residuo - qtaInUmLotto <= 1e-6) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE dbo.MpLotto SET Stato = 'ESAURITO' WHERE LottoInterno = ?")) {
                        ps.setString(1, l.lottoInterno);
                        ps.executeUpdate();
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

    // ------------------------------------------------------------------
    // Quasar (Oracle, schema POKER) - SOLA LETTURA
    // Entrate materie prime: POKER.BOLRIG con RB_PROT = 'MOMP' (documento BAMP); il lotto interno
    // e' RB_LOTTO; LOTTI ha scadenza e origine (LOT_ORIGINE = lotto del fornitore); un lotto e' VALIDATO se in POKER.VALID_TES
    // (modulo Quasar U6MLU01) ha VALIDAZ = 'SI'.
    // ------------------------------------------------------------------

    private static final class LottoQuasar {
        int num;
        int riga;
        String fornitore = "";
        String articolo = "";
        String descr = "";
        String um = "";
        String lotto = "";
        String lottoForn = "";
        double qta;
        LocalDate scadenza;
        boolean validato;
        String validatoDa = "";
        java.sql.Timestamp validatoQuando;
    }

    /** Validazione di un lotto in Quasar (modulo U6MLU01, tabella POKER.VALID_TES). */
    private static final class ValidazioneQuasar {
        String utente = "";
        java.sql.Timestamp quando;
    }

    private static String etichettaValidatore(String utente) {
        return utente == null || utente.isEmpty() ? "QUASAR" : utente + " (Quasar)";
    }

    private List<LottoQuasar> leggiQuasar(int anno, int daNum) throws Exception {
        String sql = "SELECT b.RB_NUM, b.RB_RIGA, TRIM(b.RB_CLFO), TRIM(b.RB_PARTE), TRIM(b.RB_DES), b.RB_QTA, "
                + "TRIM(b.RB_UM), TRIM(b.RB_LOTTO), TRIM(b.RB_FLOTTO), l.LOT_DTSCAD, TRIM(l.LOT_ORIGINE), "
                + "CASE WHEN v.LOTTO IS NULL THEN 0 ELSE 1 END, TRIM(v.UTENTE), v.DATA_VAR "
                + "FROM POKER.BOLRIG b "
                + "LEFT JOIN POKER.LOTTI l ON l.LOT_COD = b.RB_LOTTO AND l.LOT_PARTE = b.RB_PARTE "
                + "LEFT JOIN POKER.VALID_TES v ON TRIM(v.LOTTO) = TRIM(b.RB_LOTTO) "
                + "AND TRIM(v.ARTICOLO) = TRIM(b.RB_PARTE) AND TRIM(v.VALIDAZ) = 'SI' "
                + "WHERE b.RB_ESE = ? AND b.RB_PROT = 'MOMP' AND b.RB_NUM >= ? AND b.RB_LOTTO IS NOT NULL "
                + "ORDER BY b.RB_NUM, b.RB_RIGA";
        List<LottoQuasar> out = new ArrayList<>();
        try (Connection cn = DbOracleManager.getConnection(); PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(anno));
            ps.setInt(2, daNum);
            ps.setQueryTimeout(30);
            ps.setMaxRows(5000);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LottoQuasar q = new LottoQuasar();
                    q.num = rs.getInt(1);
                    q.riga = rs.getInt(2);
                    q.fornitore = nz(rs.getString(3));
                    q.articolo = nz(rs.getString(4));
                    q.descr = nz(rs.getString(5));
                    q.qta = rs.getDouble(6);
                    q.um = nz(rs.getString(7));
                    q.lotto = nz(rs.getString(8));
                    String flotto = nz(rs.getString(9));
                    q.lottoForn = flotto.isEmpty() ? nz(rs.getString(11)) : flotto;
                    java.sql.Date d = rs.getDate(10);
                    if (d != null && d.toLocalDate().getYear() < 2090) {
                        q.scadenza = d.toLocalDate();
                    }
                    q.validato = rs.getInt(12) == 1;
                    q.validatoDa = nz(rs.getString(13));
                    q.validatoQuando = rs.getTimestamp(14);
                    out.add(q);
                }
            }
        }
        return out;
    }

    /** Dei lotti indicati, quelli validati in Quasar (POKER.VALID_TES, VALIDAZ = 'SI') con utente e data. */
    private Map<String, ValidazioneQuasar> validazioniQuasar(Collection<String> daControllare) throws Exception {
        Map<String, ValidazioneQuasar> out = new LinkedHashMap<>();
        List<String> lista = new ArrayList<>(daControllare);
        if (lista.isEmpty()) return out;
        try (Connection cn = DbOracleManager.getConnection()) {
            for (int i2 = 0; i2 < lista.size(); i2 += 500) {
                List<String> blocco = lista.subList(i2, Math.min(lista.size(), i2 + 500));
                StringBuilder sb = new StringBuilder(
                        "SELECT TRIM(LOTTO), TRIM(UTENTE), DATA_VAR FROM POKER.VALID_TES WHERE TRIM(VALIDAZ) = 'SI' AND TRIM(LOTTO) IN (");
                for (int j2 = 0; j2 < blocco.size(); j2++) {
                    sb.append(j2 == 0 ? "?" : ",?");
                }
                sb.append(')');
                try (PreparedStatement ps = cn.prepareStatement(sb.toString())) {
                    for (int j2 = 0; j2 < blocco.size(); j2++) {
                        ps.setString(j2 + 1, blocco.get(j2));
                    }
                    ps.setQueryTimeout(30);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            ValidazioneQuasar v = new ValidazioneQuasar();
                            v.utente = nz(rs.getString(2));
                            v.quando = rs.getTimestamp(3);
                            out.put(nz(rs.getString(1)), v);
                        }
                    }
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Sincronizzazione AUTOMATICA con Quasar (in background, ogni minuto)
    //  - importa i nuovi lotti MOMP dal numero di bolla di partenza dell'anno;
    //  - porta a OK i lotti importati che nel frattempo risultano validati in POKER.VALID_TES.
    // Il numero di partenza e' salvato in dbo.QuasarSync (chiave MOMP_AAAA): al primo avvio in assoluto
    // e' "la prossima bolla" (non si importa lo storico, le cui quantita' residue non sono note); con un
    // nuovo anno riparte da 1. Si puo' forzare con quasar.primoNumero in tracciabilita-postazione.properties.
    // ------------------------------------------------------------------

    private static final long INTERVALLO_SYNC_SEC = 60;
    private static final String SQL_QUASAR_SYNC =
            "IF OBJECT_ID('dbo.QuasarSync', 'U') IS NULL CREATE TABLE dbo.QuasarSync ("
                    + "Chiave NVARCHAR(30) NOT NULL PRIMARY KEY, Valore INT NOT NULL, "
                    + "DataAgg DATETIME2 NOT NULL DEFAULT SYSDATETIME())";
    private final JLabel barraStato = new JLabel();
    private String bannerBase = "";
    private int quasarPrimoNumero = 0;
    private java.util.concurrent.ScheduledExecutorService sync;

    private void avviaSincronizzazioneQuasar() {
        sync = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread th = new Thread(r, "sync-quasar");
            th.setDaemon(true);
            return th;
        });
        sync.scheduleWithFixedDelay(this::cicloSincronizzazione, 3, INTERVALLO_SYNC_SEC,
                java.util.concurrent.TimeUnit.SECONDS);
    }

    /** Un giro di sincronizzazione: lavora fuori dal thread grafico e poi aggiorna schermo e barra di stato. */
    private void cicloSincronizzazione() {
        String esito;
        boolean cambiato = false;
        try {
            int nuovi = sincronizzaQuasar();
            int validati = sincronizzaValidazioniQuasarDb();
            cambiato = nuovi > 0 || validati > 0;
            esito = "Quasar sincronizzato alle " + java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                    + (nuovi > 0 ? " - nuovi lotti: " + nuovi : "")
                    + (validati > 0 ? " - lotti validati: " + validati : "");
        } catch (Throwable ex) {   // anche errori di caricamento del driver: il ciclo non deve fermarsi
            System.err.println("Sincronizzazione Quasar non riuscita: " + ex);
            esito = "Quasar: sincronizzazione non riuscita (" + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()) + ")";
        }
        final String testo = bannerBase + "  " + esito;
        final boolean aggiorna = cambiato;
        SwingUtilities.invokeLater(() -> {
            if (aggiorna) {
                try {
                    ricaricaLottiDb();
                } catch (Exception ex) {
                    System.err.println("Aggiornamento elenco lotti non riuscito: " + ex);
                }
            }
            barraStato.setText(testo);
        });
    }

    /** Crea dbo.QuasarSync solo se manca; se l'utente non ha il permesso lo dice chiaramente. */
    private void creaTabellaQuasarSyncSeManca(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT OBJECT_ID('dbo.QuasarSync', 'U')");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getObject(1) != null) {
                return;
            }
        }
        try (java.sql.Statement st = conn.createStatement()) {
            st.execute(SQL_QUASAR_SYNC);
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 2714) {
                return;   // esiste gia' (OBJECT_ID non la vedeva per mancanza di permessi di metadati)
            }
            throw new IllegalStateException("Manca la tabella dbo.QuasarSync e l'utente del database non può crearla: "
                    + "farla creare con lo script sql/tracciabilita_tabelle.sql (sezione QuasarSync).");
        }
    }

    private Integer leggiIntSync(Connection conn, String chiave) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT Valore FROM dbo.QuasarSync WHERE Chiave = ?")) {
            ps.setString(1, chiave);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Integer.valueOf(rs.getInt(1)) : null;
            }
        }
    }

    private int massimoNumeroMompQuasar(int anno) throws Exception {
        try (Connection cn = DbOracleManager.getConnection();
             PreparedStatement ps = cn.prepareStatement(
                     "SELECT NVL(MAX(RB_NUM), 0) FROM POKER.BOLRIG WHERE RB_ESE = ? AND RB_PROT = 'MOMP'")) {
            ps.setString(1, String.valueOf(anno));
            ps.setQueryTimeout(30);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Lotti, tra quelli indicati, gia' presenti nel nostro database. */
    private Set<String> lottiPresentiDb(Collection<String> ids) throws Exception {
        Set<String> presenti = new HashSet<>();
        List<String> lista = new ArrayList<>(ids);
        if (lista.isEmpty()) return presenti;
        try (Connection conn = openConnection()) {
            for (int i = 0; i < lista.size(); i += 500) {
                List<String> blocco = lista.subList(i, Math.min(lista.size(), i + 500));
                StringBuilder sb = new StringBuilder("SELECT LottoInterno FROM dbo.MpLotto WHERE LottoInterno IN (");
                for (int j = 0; j < blocco.size(); j++) {
                    sb.append(j == 0 ? "?" : ",?");
                }
                sb.append(')');
                try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
                    for (int j = 0; j < blocco.size(); j++) {
                        ps.setString(j + 1, blocco.get(j));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            presenti.add(nz(rs.getString(1)));
                        }
                    }
                }
            }
        }
        return presenti;
    }

    /** Importa da Quasar i lotti MOMP nuovi; ritorna quanti ne ha creati. */
    private int sincronizzaQuasar() throws Exception {
        int anno = LocalDate.now().getYear();
        String chiave = "MOMP_" + anno;
        int da;
        try (Connection conn = openConnection()) {
            creaTabellaQuasarSyncSeManca(conn);
            Integer salvato = leggiIntSync(conn, chiave);
            if (salvato == null) {
                boolean primaVolta;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM dbo.QuasarSync WHERE Chiave LIKE 'MOMP[_]%'");
                     ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    primaVolta = rs.getInt(1) == 0;
                }
                int iniziale = 1;
                if (primaVolta) {
                    iniziale = quasarPrimoNumero > 0 ? quasarPrimoNumero : massimoNumeroMompQuasar(anno) + 1;
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO dbo.QuasarSync (Chiave, Valore) VALUES (?, ?)")) {
                    ps.setString(1, chiave);
                    ps.setInt(2, iniziale);
                    ps.executeUpdate();
                }
                salvato = iniziale;
            }
            da = salvato;
        }

        List<LottoQuasar> trovati = leggiQuasar(anno, da);
        List<String> ids = new ArrayList<>();
        for (LottoQuasar q : trovati) {
            if (!q.lotto.isEmpty()) ids.add(q.lotto);
        }
        Set<String> presenti = lottiPresentiDb(ids);
        int importati = 0;
        for (LottoQuasar q : trovati) {
            if (q.lotto.isEmpty() || q.qta <= 0 || !presenti.add(q.lotto)) continue;
            Lotto l = new Lotto();
            l.lottoInterno = q.lotto;
            l.codiceMp = q.articolo;
            l.descrizione = q.descr;
            l.fornitore = q.fornitore;
            l.bolla = "MOMP/" + anno + "/" + q.num + "/" + q.riga;
            l.lottoFornitore = q.lottoForn;
            l.scadenza = q.scadenza == null ? "" : q.scadenza.toString();
            l.qtaIniziale = q.qta;
            l.qtaResidua = q.qta;
            l.um = q.um.isEmpty() ? "KG" : q.um;
            l.stato = q.validato ? "OK" : STATO_DA_VALIDARE;
            l.validatoDa = q.validato ? etichettaValidatore(q.validatoDa) : "";
            l.validatoQuando = q.validato ? q.validatoQuando : null;
            try {
                salvaLottoDb(l);
                importati++;
            } catch (IllegalStateException ex) {
                // inserito nel frattempo da un'altra postazione
            }
        }
        return importati;
    }

    /** Porta a OK (leggendo POKER.VALID_TES) i lotti importati da Quasar che risultano ancora DA VALIDARE. */
    private int sincronizzaValidazioniQuasarDb() throws Exception {
        List<String> da = new ArrayList<>();
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT LottoInterno FROM dbo.MpLotto WHERE Stato = 'DA VALIDARE' AND BollaIngresso LIKE 'MOMP/%'");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                da.add(nz(rs.getString(1)));
            }
        }
        int n = 0;
        for (Map.Entry<String, ValidazioneQuasar> en : validazioniQuasar(da).entrySet()) {
            try {
                validaLottoDb(en.getKey(), etichettaValidatore(en.getValue().utente), en.getValue().quando);
                n++;
            } catch (IllegalStateException ex) {
                // lo stato e' cambiato nel frattempo
            }
        }
        return n;
    }

    private void aggiornaLottoFornitoreDb(String lotto, String lottoFornitore) throws Exception {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE dbo.MpLotto SET LottoFornitore = ? WHERE LottoInterno = ?")) {
            ps.setString(1, trunc(lottoFornitore, 100));
            ps.setString(2, lotto);
            ps.executeUpdate();
        }
    }

    /** Quantita' gia' pesata per la stessa dose (batch + zona + MP), convertita nell'UM indicata. */
    private double giaPesatoDb(String batch, String zona, String mp, String um) throws Exception {
        double tot = 0;
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT QtaPesata, UM FROM dbo.BatchPrelievo WHERE BatchId = ? AND Zona = ? AND CodiceMP = ?")) {
            ps.setString(1, batch);
            ps.setString(2, trunc(zona, 30));
            ps.setString(3, mp);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        tot += convertiUm(rs.getDouble(1), nz(rs.getString(2)), um);
                    } catch (IllegalStateException ignored) {
                        // UM non convertibile: non si somma
                    }
                }
            }
        }
        return tot;
    }

    /** Registra il residuo reale: movimento RETTIFICA (differenza) e aggiornamento dello stato del lotto. */
    private void rettificaResiduoDb(String lotto, double delta, double nuovoResiduo, String operatore) throws Exception {
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO dbo.MpMovimento (LottoInterno, Tipo, Qta, Rif, Operatore) "
                                + "VALUES (?, 'RETTIFICA', ?, 'RIPESATURA', ?)")) {
                    ps.setString(1, lotto);
                    ps.setDouble(2, delta);
                    ps.setString(3, trunc(operatore, 50));
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE dbo.MpLotto SET Stato = CASE WHEN ? <= 0.000001 THEN 'ESAURITO' "
                                + "WHEN Stato = 'ESAURITO' THEN 'OK' ELSE Stato END WHERE LottoInterno = ?")) {
                    ps.setDouble(1, nuovoResiduo);
                    ps.setString(2, lotto);
                    ps.executeUpdate();
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

    /** Ricerca di tracciabilita' sul database (ultimi 1000 risultati). */
    private void cercaDb(String q) throws Exception {
        String sql = "SELECT TOP 1000 CONVERT(VARCHAR(19), p.DataOra, 120) AS DataOra, p.BatchId, p.Zona, p.CodiceMP, "
                + "p.LottoInterno, l.LottoFornitore, l.BollaIngresso, p.QtaPesata, p.UM, p.Esito, p.Operatore "
                + "FROM dbo.BatchPrelievo p LEFT JOIN dbo.MpLotto l ON l.LottoInterno = p.LottoInterno "
                + "WHERE (? = '' OR p.BatchId LIKE ? OR p.LottoInterno LIKE ? OR p.CodiceMP LIKE ? "
                + "OR l.LottoFornitore LIKE ? OR l.BollaIngresso LIKE ?) ORDER BY p.Id DESC";
        String like = "%" + q.replace("[", "[[]").replace("%", "[%]").replace("_", "[_]") + "%";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, q);
            for (int i = 2; i <= 6; i++) {
                ps.setString(i, like);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ricercaModel.addRow(new Object[]{nz(rs.getString("DataOra")), nz(rs.getString("BatchId")),
                            nz(rs.getString("Zona")), nz(rs.getString("CodiceMP")), nz(rs.getString("LottoInterno")),
                            nz(rs.getString("LottoFornitore")), nz(rs.getString("BollaIngresso")),
                            formatNum(rs.getDouble("QtaPesata")) + " " + nz(rs.getString("UM")),
                            nz(rs.getString("Esito")), nz(rs.getString("Operatore"))});
                }
            }
        }
    }

    private void addPrelievoRow(Prelievo p) {
        prelieviModel.addRow(new Object[]{p.dataOra, p.batch, p.zona, p.codiceMp, p.lottoInterno,
                formatNum(p.qtaPrevista), formatNum(p.min), formatNum(p.max),
                formatNum(p.qtaPesata) + " " + p.um + (p.manuale ? " (manuale)" : ""), p.esito, p.operatore});
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
        JTextField tAvviso = new JTextField(String.valueOf(avvisoResiduoPct), 6);
        pnl.add(new JLabel("Avviso lotto quasi finito (% residuo):"));
        pnl.add(tAvviso);
        JTextField tRicezione = new JTextField(stampantiIp.getOrDefault("RICEZIONE", ""), 14);
        pnl.add(new JLabel("IP stampante RICEZIONE (etichette lotto):"));
        pnl.add(tRicezione);
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
        pnl.add(new JLabel("(salvati su questo PC)"));
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
        stampantiIp.put("RICEZIONE", tRicezione.getText().trim());
        int soglia = (int) toDouble(tAvviso.getText());
        if (soglia >= 0 && soglia <= 100) {
            avvisoResiduoPct = soglia;
        }
        salvaImpostazioniPostazione();
    }

    /**
     * Lotti della stessa MP, utilizzabili (stato OK, residuo, non scaduti) e con scadenza
     * precedente a quella del lotto scelto. Se il lotto scelto non ha scadenza, qualunque
     * lotto con scadenza lo precede. Ordinati dalla scadenza piu' vicina.
     */
    private List<Lotto> lottiFefoMigliori(Lotto scelto) {
        List<Lotto> result = new ArrayList<>();
        LocalDate oggi = LocalDate.now();
        LocalDate scadScelto = scelto.scadenza.isEmpty() ? null : LocalDate.parse(scelto.scadenza);
        for (Lotto o : lotti.values()) {
            if (o == scelto || !o.codiceMp.equals(scelto.codiceMp)) continue;
            if (!"OK".equals(o.stato) || o.qtaResidua <= 0 || o.scadenza.isEmpty()) continue;
            LocalDate sc = LocalDate.parse(o.scadenza);
            if (sc.isBefore(oggi)) continue;
            if (scadScelto == null || sc.isBefore(scadScelto)) {
                result.add(o);
            }
        }
        result.sort((a, b) -> LocalDate.parse(a.scadenza).compareTo(LocalDate.parse(b.scadenza)));
        return result;
    }

    private double giaPesatoMemoria(String batch, String zona, String mp, String um) {
        double tot = 0;
        for (Prelievo p : prelievi) {
            if (p.batch.equals(batch) && p.zona.equals(zona) && p.codiceMp.equals(mp)) {
                try {
                    tot += convertiUm(p.qtaPesata, p.um, um);
                } catch (IllegalStateException ignored) {
                    // UM non convertibile: non si somma
                }
            }
        }
        return tot;
    }

    /** Lotti della stessa MP ancora utilizzabili (OK, residuo, non scaduti), scadenza piu' vicina per prima. */
    private List<Lotto> lottiAlternativi(Lotto l) {
        List<Lotto> result = new ArrayList<>();
        LocalDate oggi = LocalDate.now();
        for (Lotto o : lotti.values()) {
            if (o == l || !o.codiceMp.equals(l.codiceMp) || !"OK".equals(o.stato) || o.qtaResidua <= 1e-6) continue;
            if (!o.scadenza.isEmpty() && LocalDate.parse(o.scadenza).isBefore(oggi)) continue;
            result.add(o);
        }
        result.sort((a, b) -> {
            if (a.scadenza.isEmpty() && b.scadenza.isEmpty()) return 0;
            if (a.scadenza.isEmpty()) return 1;
            if (b.scadenza.isEmpty()) return -1;
            return LocalDate.parse(a.scadenza).compareTo(LocalDate.parse(b.scadenza));
        });
        return result;
    }

    /**
     * Dopo un prelievo avvisa se il lotto e' esaurito o quasi finito: residuo sotto la soglia in % della
     * quantita' iniziale, oppure meno di 2 dosi come quella prevista. Indica i lotti con cui proseguire.
     */
    private void avvisoResiduo(Lotto l, double prevista, String umPrevista) {
        boolean esaurito = l.qtaResidua <= 1e-6;
        double pct = l.qtaIniziale > 0 ? 100.0 * l.qtaResidua / l.qtaIniziale : 0;
        boolean basso = pct <= avvisoResiduoPct;
        if (prevista > 0) {
            try {
                if (l.qtaResidua < 2 * convertiUm(prevista, umPrevista, l.um)) basso = true;
            } catch (IllegalStateException ignored) {
                // UM non confrontabili: vale solo la soglia in %
            }
        }
        if (!esaurito && !basso) return;
        List<Lotto> altri = lottiAlternativi(l);
        StringBuilder sb = new StringBuilder();
        sb.append(esaurito ? "LOTTO ESAURITO: " : "Lotto quasi finito: ").append(l.lottoInterno).append(" (")
                .append(l.codiceMp).append(")\n");
        if (!esaurito) {
            sb.append("Residuo ").append(formatNum(l.qtaResidua)).append(' ').append(l.um).append(" (")
                    .append(Math.round(pct)).append("% dell'iniziale).\n");
        }
        sb.append('\n');
        if (altri.isEmpty()) {
            sb.append("ATTENZIONE: nessun altro lotto disponibile per ").append(l.codiceMp)
                    .append(".\nAvvisare subito il magazzino.");
        } else {
            sb.append("Lotti con cui proseguire (scadenza più vicina per primo):\n");
            for (Lotto o : altri) {
                sb.append("  ").append(o.lottoInterno).append("  residuo ").append(formatNum(o.qtaResidua)).append(' ')
                        .append(o.um).append("  scad. ").append(o.scadenza.isEmpty() ? "-" : o.scadenza).append('\n');
            }
        }
        JOptionPane.showMessageDialog(this, sb.toString(),
                esaurito ? "Lotto esaurito" : "Lotto quasi finito", JOptionPane.WARNING_MESSAGE);
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
        String validatoDa = "";
        java.sql.Timestamp validatoQuando;   // data/ora reale di validazione in Quasar, se importata
        String silo = "";   // silo in cui e' stato caricato il lotto (vuoto = materiale non da silo)
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
        boolean manuale;   // true = peso digitato a mano, non letto dalla bilancia
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
}
