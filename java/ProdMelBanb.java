package Banbury;

import SVC.BalanceService;
import SVC.DBBamburyReader;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import javax.swing.text.*;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.*;
import SVC.DbOracleManager;
import java.io.File;
import java.io.FileInputStream;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.view.JasperViewer;
import net.sf.jasperreports.engine.util.JRLoader;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.awt.print.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Date;
import java.sql.Types;
import java.util.concurrent.*;
import java.util.regex.*;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.export.JRPrintServiceExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimplePrintServiceExporterConfiguration;


public class ProdMelBanb extends JFrame {

    private static final String KG_VERSION = "V194.29";
    private static final int PRODUCTION_COL_COUNT = 16;
    private static final String ZBEC_B1_DATA_FILE_PATH = "C:\\_tools\\banbury1\\zbec-b1.csv";
    private static final String ZBEC_B2_DATA_FILE_PATH = "C:\\_tools\\banbury2\\zbec-b2.csv";

    private final Color GRID = new Color(145, 145, 145);
    private static final Color FORM_BACKGROUND = new Color(238, 238, 238);
    private static final Color TABLE_GRID_LIGHT = new Color(0xC8, 0xD8, 0xE8);
    private static final Color TABLE_ROW_WHITE = Color.WHITE;
    private static final Color TABLE_ROW_BLUE = new Color(0xF2, 0xF8, 0xFF);
    private static final Color TABLE_HEADER_BG = new Color(0xF7, 0xFB, 0xFF);
    private static final Color TABLE_SELECTION_BG = new Color(0xD8, 0xEC, 0xFF);
    private static final Color TABLE_SELECTION_BORDER = new Color(0x70, 0xA8, 0xD8);
    private final Font FONT_NORMAL = new Font("Arial", Font.PLAIN, 16);
    private final Font FONT_BOLD = new Font("Arial", Font.BOLD, 16);
    private final Font FONT_TITLE = new Font("Arial", Font.BOLD, 32);

   
    
    private static final int PERSONNEL_ROW_H = 28;
    private static final int PERSONNEL_ROW_GAP = 0;

    private JTextField txtHeuresTotales;
    private final List<PersonnelTimeRow> personnelTimeRows = new ArrayList<>();
    private final List<PersonnelReportRow> personnelReportRows = new ArrayList<>();
    private final List<Component> personnelFocusOrder = new ArrayList<>();
    private final List<ArretItem> arretItems = new ArrayList<>();
    private final List<String> personnelRoles = new ArrayList<>();
    private final List<OperateurItem> operateurItems = new ArrayList<>();
    private final List<String> maintenanceNames = new ArrayList<>();
    private final List<JComboBox<String>> maintenanceCombos = new ArrayList<>();
    private final List<String> anomalieNames = new ArrayList<>();
    private final List<JComboBox<String>> anomalieCombos = new ArrayList<>();
    private JLabel lblEquipeValue;
    private JTextField txtResponsableCode;
    private JTextField txtResponsableName;
    private JTextField txtChefSecteurName;
    private JTable productionTable;
    private DefaultTableModel productionModel;
    private JTable poidsTable;
    private DefaultTableModel poidsModel;
    private JLabel lblPoidsVersion;
    private JLabel lblPoidsStatus;
    private boolean refreshingPoidsRealise = false;
    private boolean pendingPoidsRealiseRefresh = false;
    private boolean resettingForm = false;
    private String manualTourneeId = null;
    private LocalDateTime manualShiftExpiresAt = null;
    private int selectedBanbury = 1;
    private ReportPanel activeReportPanel;

    // V191.07: parent unico per tutti i popup. Evita che JOptionPane/JDialog
    // si centrino sul pannello scrollabile e finiscano troppo in basso.
    private Window getDialogOwnerWindow() {
        Window w = activeReportPanel == null ? null : SwingUtilities.getWindowAncestor(activeReportPanel);
        if (w != null && w.isShowing()) return w;
        if (this.isShowing()) return this;
        return null;
    }

    private Component getDialogParentComponent() {
        Window w = getDialogOwnerWindow();
        return w != null ? w : this;
    }


    private void initDefaultPersonnelRoles() {
        if (!personnelRoles.isEmpty()) return;
        personnelRoles.add("Responsable");
        personnelRoles.add("Pupitre");
        personnelRoles.add("Opérateur Banbury");
        personnelRoles.add("Préparateur caoutchoucs");
        personnelRoles.add("Aide préparateur");
        personnelRoles.add("Formation Opérateur Melangeur");
        personnelRoles.add("Carriste");
        personnelRoles.add("Manège Découpe Manuelle");
        personnelRoles.add("Opérateur Melangeur");
    }

    private String[] getPersonnelRoleValues() {
        String[] values = new String[personnelRoles.size() + 1];
        values[0] = "";
        for (int i = 0; i < personnelRoles.size(); i++) values[i + 1] = personnelRoles.get(i);
        return values;
    }

    private void initDefaultMaintenanceNames() {
        if (!maintenanceNames.isEmpty()) return;
        maintenanceNames.add("PIQUARD Stephane");
        maintenanceNames.add("PILLOIS Alain");
        maintenanceNames.add("BEN EL ALI ID ZAKARIA");
        maintenanceNames.add("JACQUES OLIVIER");
        maintenanceNames.add("PACI Julien");
        maintenanceNames.add("TOPBAS MUHAMMED");
        maintenanceNames.add("ROCHE Florent");
        maintenanceNames.add("GUY");
    }

    private String[] getMaintenanceValues() {
        String[] values = new String[maintenanceNames.size() + 1];
        values[0] = "";
        for (int i = 0; i < maintenanceNames.size(); i++) values[i + 1] = maintenanceNames.get(i);
        return values;
    }

    private JComboBox<String> createMaintenanceCombo() {
        JComboBox<String> combo = new JComboBox<>(getMaintenanceValues());
        combo.setFont(new Font("Arial", Font.PLAIN, 13));
        combo.setEditable(true);
        combo.setBackground(Color.WHITE);
        combo.setMaximumRowCount(10);
        maintenanceCombos.add(combo);
        return combo;
    }

    private void refreshMaintenanceCombos() {
        for (JComboBox<String> combo : maintenanceCombos) {
            Object selected = combo.getSelectedItem();
            combo.setModel(new DefaultComboBoxModel<>(getMaintenanceValues()));
            combo.setSelectedItem(selected);
        }
    }

    private void initDefaultAnomalies() {
        if (!anomalieNames.isEmpty()) return;

        String[] values = {
                "PROBLEME DE PRESSION CALANDRE",
                "LAPORTE DU LOCAL INFORMATIQUE EST CASSE",
                "PROBLEME DE PRESSION CALANDRE ET FUITE DIVERSES",
                "LE LOGICIEL DES CHARGES EST HORS SERVICE",
                "FUITE DIVERSES.",
                "PANNE SERVEUR",
                "PROBLEME AVEC SONDE TEMPERATURE MELANGEUR",
                "PANNE RHEOMETRE MDR-C B2",
                "PRODUCTION EN MODE MANUEL - PROBLEME INFORMATIQUE",
                "PROBLEME AVEC HUILE PLASTIFIANTE",
                "PANNE LOGICIEL RHEOMETER ODR 2000 B2",
                "PANNE BALANCE B2",
                "MANQUE PERSONNEL",
                "PROBLEME AVEC CHARGES (TOLERANCE)",
                "PROBLEME AVEC LOGICIEL CHARGES",
                "PROBLEME AVEC COUPEUSE ( MANEGE)",
                "PRODUCTION FAIT AVEC DES SACS KAOLIN (SILOS VIDES)",
                "PROGRAMMATION MOINS 10KG DE CHARGE (PROBLEME DE CHARGE SILO)",
                "PROBLEME DE SYNCHRONISATION ENTRE CALANDRE INF ET SUP A L'ARRET",
                "MANQUE CARTOUCHE IMPRIMANTE PESAGE BALANCE B1",
                "PROBLEME AVEC PISTON HAUT MANQUE PRESSION",
                "VERIFICATION B1 AVEC MECANO",
                "PROBLEME ECRAN TACTILE PUPITRE - CALANDRES",
                "PROBLEMES AVEC CALANDRES SUP - TOURNE RALENTI",
                "PROBLEME AVEC TEMPS D ARRET BAC A SAVON",
                "PREPARATION NOUVEAUX MEL. MERE CAUSE MORCEAUX DE FERRAILLE",
                "MISE EN PLACE ET DEMARRAGE",
                "MANQUE CARISTE"
        };

        for (String v : values) anomalieNames.add(v);
    }

    private String[] getAnomalieValues() {
        String[] values = new String[anomalieNames.size() + 1];
        values[0] = "";
        for (int i = 0; i < anomalieNames.size(); i++) values[i + 1] = anomalieNames.get(i);
        return values;
    }

    private JComboBox<String> createAnomalieCombo() {
        JComboBox<String> combo = new JComboBox<>(getAnomalieValues());
        combo.setFont(new Font("Arial", Font.PLAIN, 13));
        combo.setEditable(true);
        combo.setBackground(Color.WHITE);
        combo.setMaximumRowCount(12);
        anomalieCombos.add(combo);
        return combo;
    }

    private void refreshAnomalieCombos() {
        for (JComboBox<String> combo : anomalieCombos) {
            Object selected = combo.getSelectedItem();
            combo.setModel(new DefaultComboBoxModel<>(getAnomalieValues()));
            combo.setSelectedItem(selected);
        }
    }

    private void initDefaultOperateurs() {
        if (!operateurItems.isEmpty()) return;
        String[][] defaults = {
                {"KELLER LOIC", "int"}, {"JANEAULT Kilian", "int"}, {"HAZINE ALI", "0"},
                {"HUBNER Maxime", "0"}, {"BARBAS Damien", "0"}, {"MANDIT Ludovic", "0"},
                {"GONCALVES Bruno", "0"}, {"LOI Julien", "int"}, {"CLEMENT Emmanuel", "int"},
                {"MARASSE Adrien", "int"}, {"MENDES Jerson", "0"}, {"DUURCEAU Dany", "int"},
                {"RINALDI Maxime", "0"}, {"POILPRE Christopher", "int"}, {"DA COSTA Christophe", "0"},
                {"PERNELET Jeremy", "int"}, {"SCHROER LOIC", "int"}, {"CASIMIRO Joao", "0"},
                {"CHOUADRA Rabah", "int"}, {"LOBAO Carlos", "0"}, {"MARABUTO Miguel", "0"},
                {"BOUHOUCH Oualid", "int"}, {"LENNINGER Jeremy", "int"}, {"SAGHI Ahmed", "int"},
                {"CLARENN Yoann", "int"}, {"KARL LUDOVIC", ""}, {"KHOUDJA Ismail", "int"}
        };
        for (String[] row : defaults) operateurItems.add(new OperateurItem(row[0], row[1]));
    }

    private void initDefaultArrets() {
        if (!arretItems.isEmpty()) return;
        String[][] defaults = {
                {"ARRET1", "ACCIDENT DE TRAVAIL"}, {"ARRET2", "PANNE TUNNEL"},
                {"ARRET3", "ARRET B2 DEMARRAGE B1"}, {"ARRET4", "ARRET COULEUR ERRONEE"},
                {"ARRET5", "ATTENDRE CONFIRMATION LABO"}, {"ARRET6", "BAC A SAVON"},
                {"ARRET7", "COULEUR ERRONEE"}, {"ARRET8", "COUPER MEL. MERE CALANDRE"},
                {"ARRET9", "CROISEMENT MELANGES"}, {"ARRET10", "FIN PRODUCTION / Nettoyage"},
                {"ARRET11", "MELANGE BRULE"}, {"ARRET12", "NETTOYAGE INTERNE"},
                {"ARRET13", "NETTOYAGE MACHINE"}, {"ARRET14", "PANNE COUPEUSE MANEGE"},
                {"ARRET15", "PANNE COURANT GENERAL"}, {"ARRET16", "PANNE HUILE PLASTIFIANTE"},
                {"ARRET17", "PANNE LUBRIFICATION CALANDRE"}, {"ARRET18", "PANNE MANEGE"},
                {"ARRET19", "PANNE MOTEUR MELANGEUR"}, {"ARRET20", "PANNE RHEOMETRE"},
                {"ARRET21", "PANNE SILO"}, {"ARRET22", "PANNE SONDE TEMPS MELANGE"},
                {"ARRET23", "PAUSE"}, {"ARRET24", "PAUSE + ARRET B2 DEMARRAGE B1"},
                {"ARRET25", "PAUSE (manque personnel qualifié)"}, {"ARRET26", "PAUSE (manque personnel)"},
                {"ARRET27", "VERIFICATION B1 AVEC MECANO"}, {"ARRET28", "PROB.VERRIN PORTE DECHARGE"},
                {"ARRET29", "PROBLEME INFORMATIQUE"}, {"ARRET30", "PANNE PORTE DE DECHARGE"},
                {"ARRET31", "PROB. PORTE DE CHARGE"}, {"ARRET32", "VIDER MACHINE+TUNNEL"},
                {"ARRET33", "PANNE EXTRUDEUSE"}, {"ARRET34", "VIDER MACHINE+NETTOYAGE"},
                {"ARRET35", "CHANGEMENT DE PROGRAMME"}, {"ARRET36", "FUITE D'EAU PORTE DECHARGE"},
                {"ARRET37", "PANNE CHAUDIERE"}, {"ARRET38", "PROBLEME CHARGE SILO"},
                {"ARRET39", "PROB.POMPE HIDRAULIQUE HAUT"}, {"ARRET40", "PANNE COUTEAU (BAC A SAVON)"},
                {"ARRET41", "PANNE LUBRIFICATION TENNUES"}, {"ARRET42", "PAUSE (Contrôle Moteur Melangeur)"},
                {"ARRET43", "PANNE ASCENSEUR"}, {"ARRET44", "PANNE / PROBLEME PISTON HAUT"},
                {"ARRET45", "PANNE POMPE SILANO"}, {"ARRET46", "PAUSE (Manque matiere premiere)"},
                {"ARRET47", "PANNE Pompe Hydraulique Haut"}, {"ARRET48", "PROBLEME PRESSION CALANDRE"},
                {"ARRET49", "ARRET ANNUEL"}, {"ARRET50", "MANQUE PERSONNEL"},
                {"ARRET51", "PANNE FUITE D'EAU REFROIDISSEMENT"}, {"ARRET52", "PANNE PUPITRE MELANGEUR"},
                {"ARRET53", "PREP. NOUVEAUX MEL. MERE"}, {"ARRET54", "Arret Changement de programme"},
                {"ARRET55", "COUPER MEL. MERE CALANDRE"}, {"ARRET56", "VIDER Manege manque pers.qualifier"},
                {"ARRET57", "PROBLEME DEMARRAGE SYSTÈME"}, {"ARRET58", "Préparations suit programme"},
                {"ARRET59", "MANQUE MATIERE PREMIERE"}, {"ARRET60", "PROBLEME COM. PUPITRE BAS"},
                {"ARRET61", "PANNE CALANDRE"}, {"ARRET62", "Prob.chargement.silo(secteur irrespirable)"},
                {"ARRET63", "PROBLEME DEMARRAGE MELANGEUR"}, {"ARRET64", "TEMP.haute bague rotative"},
                {"ARRET65", "CHanger Flexible Huile Plastifiante"}, {"ARRET66", "NETTOYAGE BANBURY 1"},
                {"ARRET67", "PROBLEME LOGICIEL CHARGES"}, {"ARRET68", "Préparation Mélange pour la Ligne"},
                {"ARRET69", "Rangement et nettoyage secteur"}, {"ARRET70", "Preparation Demarrage Annuel"},
                {"ARRET71", "CONTROL ROTORS MELANGEUR"}, {"ARRET72", "PANNE POMPE EAU PRINCIPAL"},
                {"ARRET73", "Manque CARISTE pour vider manège"}, {"ARRET74", "ARRET Peser pallettes pour repasser."},
                {"ARRET75", "PANNE ASPIRATION PORTE"}, {"ARRET76", "ARRET pas de pallettes plastique"},
                {"ARRET77", "ATTENDRE GRANULES"}, {"ARRET78", "niveaux basse graisse conteneur mélangeur"},
                {"ARRET79", "PANNE TENUE"}, {"ARRET80", "PANNE Porte de CHARGE"},
                {"ARRET81", "Arret entretien disposifit incendie"}, {"ARRET83", "PROBLEME MOTEUR CALANDRE"},
                {"ARRET84", "PANNE MOTEUR EXTRUSEUSE"}, {"ARRET85", "VIDER MACHINE A LA MAIN"},
                {"ARRET86", "PANNE ELECTRIQUE"}, {"ARRET86", "PANNE photo cellule melangeur"},
                {"ARRET88", "MISE EN PLACE ET DEMARRAGE"}, {"ARRET89", "VIDER MANEGE probleme Coupeuse"},
                {"ARRET90", "Mise en sécurité porte de décharge"}, {"ARRET91", "Remise en OFF Securites machine"},
                {"ARRET92", "VIDER MANEGE (manque personnel)"}, {"ARRET93", "Fuite de kaolin compresseur"},
                {"ARRET93", "Attendre Confirmation Courbes MP PL33"}, {"ARRET94", "ATTENDRE MECANICIEN"},
                {"ARRET95", "PANNE BAC A SAVON"}, {"ARRET96", "PANNE TUNNEL"},
                {"ARRET97", "PANNE PUPITRE MELANGEUR"}, {"ARRET98", "ARRET AIR IRRESPIRABLE"},
                {"ARRET99", "PROBLEME ASPIRATION"}, {"ARRET100", "PANNE INFORMATIQUE"},
                {"ARRET101", "PANNE FESTONATOR"}, {"ARRET102", "PREPARATION CORRECTION"},
                {"ARRET103", "ATMOSPHERE IRRESPIRABLE (SILO)"}, {"ARRET104", "MANQUE PERSONNEL"},
                {"ARRET105", "FUITE D'HUILE CENTRALE HYDRAULIQUE"}, {"ARRET106", "Nettoyage Huile Hydraulique"},
                {"ARRET107", "PANNE PISTON MELANGEUR"}, {"ARRET108", "CONTROL SILOS"},
                {"ARRET109", "Remplacement Couteau Bac Savon"}, {"ARRET110", "ATTENDRE VIDER MANEGE B1"},
                {"ARRET111", "NETTOYAGE PISTON HAUT"}, {"ARRET112", "PREPARATION + PAUSE"},
                {"ARRET113", "Problemes tennues"}
        };
        for (String[] row : defaults) arretItems.add(new ArretItem(row[0], row[1]));
    }

    private String findArretDescription(String code) {
        if (code == null) return "";
        String target = code.trim().toUpperCase(Locale.ROOT);
        for (ArretItem item : arretItems) if (item.code.equalsIgnoreCase(target)) return item.description;
        return "";
    }

    private ArretItem chooseArret(Component parent) {
        DefaultTableModel m = new DefaultTableModel(new String[]{"CodeArret", "Description"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        for (ArretItem item : arretItems) m.addRow(new Object[]{item.code, item.description});
        JTable t = new JTable(m);
        t.setRowHeight(26);
        t.setFont(new Font("Arial", Font.PLAIN, 15));
        t.getTableHeader().setFont(new Font("Arial", Font.BOLD, 15));
        JDialog d = new JDialog(getDialogOwnerWindow(), "Choisir ARRET", Dialog.ModalityType.APPLICATION_MODAL);
        final ArretItem[] selected = {null};
        t.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 2 && t.getSelectedRow() >= 0) {
                    int r = t.convertRowIndexToModel(t.getSelectedRow());
                    selected[0] = new ArretItem(String.valueOf(m.getValueAt(r,0)), String.valueOf(m.getValueAt(r,1)));
                    d.dispose();
                }
            }
        });
        d.add(new JScrollPane(t), BorderLayout.CENTER);
        d.setSize(620, 520);
        d.setLocationRelativeTo(getDialogParentComponent());
        d.setVisible(true);
        return selected[0];
    }

    private void openSetupDialog() {
        JDialog dialog = new JDialog(getDialogOwnerWindow(), "SETUP", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 12));
        JButton btnListArret = new JButton("List ARRET");
        JButton btnListPersonnel = new JButton("List PERSONNEL");
        JButton btnListOperateur = new JButton("List NOMS OPERATEURS");
        btnListOperateur.setToolTipText("Modifier ou ajouter les noms des opérateurs utilisés dans Personnel");
        JButton btnListMaintenance = new JButton("List MAINTENANCE");
        JButton btnListAnomalie = new JButton("List ANOMALIE");
        JButton btnSetupBalance = new JButton("Setup Bilancia");
        JButton btnClose = new JButton("Fermer");
        btnListArret.addActionListener(e -> openArretListDialog(dialog));
        btnListPersonnel.addActionListener(e -> openPersonnelListDialog(dialog));
        btnListOperateur.addActionListener(e -> openOperateurListDialog(dialog));
        btnListMaintenance.addActionListener(e -> openMaintenanceListDialog(dialog));
        btnListAnomalie.addActionListener(e -> openAnomalieListDialog(dialog));
        btnSetupBalance.addActionListener(e -> openEmbeddedBalanceSetup());
        btnClose.addActionListener(e -> dialog.dispose());
        dialog.add(btnListArret);
        dialog.add(btnListPersonnel);
        dialog.add(btnListOperateur);
        dialog.add(btnListMaintenance);
        dialog.add(btnListAnomalie);
        dialog.add(btnSetupBalance);
        dialog.add(btnClose);
        dialog.setSize(1120, 115);
        dialog.setLocationRelativeTo(getDialogParentComponent());
        dialog.setVisible(true);
    }

    private void openEmbeddedBalanceSetup() {
        if (activeReportPanel != null) activeReportPanel.openEmbeddedBalanceSetup();
    }

    private void openArretListDialog(Component parent) {
        DefaultTableModel m = new DefaultTableModel(new String[]{"CodeArret", "Description"}, 0);
        for (ArretItem item : arretItems) m.addRow(new Object[]{item.code, item.description});
        JTable t = new JTable(m);
        t.setRowHeight(26);
        JButton add = new JButton("Ajouter");
        JButton del = new JButton("Effacer");
        JButton save = new JButton("Sauver");
        add.addActionListener(e -> m.addRow(new Object[]{"ARRET", ""}));
        del.addActionListener(e -> { int r=t.getSelectedRow(); if(r>=0) m.removeRow(t.convertRowIndexToModel(r)); });
        JDialog d = new JDialog(getDialogOwnerWindow(), "List ARRET", Dialog.ModalityType.APPLICATION_MODAL);
        save.addActionListener(e -> { arretItems.clear(); for(int i=0;i<m.getRowCount();i++){ String c=String.valueOf(m.getValueAt(i,0)).trim(); String desc=String.valueOf(m.getValueAt(i,1)).trim(); if(!c.isEmpty()||!desc.isEmpty()) arretItems.add(new ArretItem(c, desc)); } d.dispose(); });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT)); buttons.add(add); buttons.add(del); buttons.add(save);
        d.add(new JScrollPane(t), BorderLayout.CENTER); d.add(buttons, BorderLayout.SOUTH);
        d.setSize(720, 560); d.setLocationRelativeTo(getDialogParentComponent()); d.setVisible(true);
    }

    private void openPersonnelListDialog(Component parent) {
        DefaultListModel<String> m = new DefaultListModel<>();
        for (String role : personnelRoles) m.addElement(role);
        JList<String> list = new JList<>(m);
        list.setFont(new Font("Arial", Font.PLAIN, 16));
        JTextField edit = new JTextField(25);
        JButton add = new JButton("Ajouter");
        JButton del = new JButton("Effacer");
        JButton save = new JButton("Sauver");
        list.addListSelectionListener(e -> { if(!e.getValueIsAdjusting() && list.getSelectedValue()!=null) edit.setText(list.getSelectedValue()); });
        add.addActionListener(e -> { String v=edit.getText().trim(); if(!v.isEmpty()) { int idx=list.getSelectedIndex(); if(idx>=0) m.set(idx, v); else m.addElement(v); edit.setText(""); }});
        del.addActionListener(e -> { int idx=list.getSelectedIndex(); if(idx>=0) m.remove(idx); });
        JDialog d = new JDialog(getDialogOwnerWindow(), "List PERSONNEL", Dialog.ModalityType.APPLICATION_MODAL);
        save.addActionListener(e -> { personnelRoles.clear(); for(int i=0;i<m.size();i++) personnelRoles.add(m.get(i)); d.dispose(); });
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT)); top.add(new JLabel("Valeur:")); top.add(edit); top.add(add); top.add(del);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT)); bottom.add(save);
        d.add(top, BorderLayout.NORTH); d.add(new JScrollPane(list), BorderLayout.CENTER); d.add(bottom, BorderLayout.SOUTH);
        d.setSize(560, 430); d.setLocationRelativeTo(getDialogParentComponent()); d.setVisible(true);
    }


    private void openMaintenanceListDialog(Component parent) {
        DefaultListModel<String> m = new DefaultListModel<>();
        for (String name : maintenanceNames) m.addElement(name);

        JList<String> list = new JList<>(m);
        list.setFont(new Font("Arial", Font.PLAIN, 16));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JTextField edit = new JTextField(28);
        JButton add = new JButton("Ajouter");
        JButton del = new JButton("Effacer");
        JButton save = new JButton("Sauver");

        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                edit.setText(list.getSelectedValue());
            }
        });

        add.addActionListener(e -> {
            String v = edit.getText() == null ? "" : edit.getText().trim();
            if (v.isEmpty()) return;

            int idx = list.getSelectedIndex();
            if (idx >= 0) m.set(idx, v);
            else m.addElement(v);

            edit.setText("");
            list.clearSelection();
        });

        del.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx >= 0) {
                m.remove(idx);
                edit.setText("");
            }
        });

        JDialog d = new JDialog(getDialogOwnerWindow(), "List MAINTENANCE", Dialog.ModalityType.APPLICATION_MODAL);
        save.addActionListener(e -> {
            maintenanceNames.clear();
            for (int i = 0; i < m.size(); i++) {
                String v = m.get(i) == null ? "" : m.get(i).trim();
                if (!v.isEmpty()) maintenanceNames.add(v);
            }
            refreshMaintenanceCombos();
            d.dispose();
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Valeur:"));
        top.add(edit);
        top.add(add);
        top.add(del);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(save);

        d.add(top, BorderLayout.NORTH);
        d.add(new JScrollPane(list), BorderLayout.CENTER);
        d.add(bottom, BorderLayout.SOUTH);
        d.setSize(580, 430);
        d.setLocationRelativeTo(getDialogParentComponent());
        d.setVisible(true);
    }


    private void openAnomalieListDialog(Component parent) {
        DefaultListModel<String> m = new DefaultListModel<>();
        for (String value : anomalieNames) m.addElement(value);

        JList<String> list = new JList<>(m);
        list.setFont(new Font("Arial", Font.PLAIN, 16));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JTextField edit = new JTextField(40);
        JButton add = new JButton("Ajouter");
        JButton del = new JButton("Effacer");
        JButton save = new JButton("Sauver");

        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                edit.setText(list.getSelectedValue());
            }
        });

        add.addActionListener(e -> {
            String v = edit.getText() == null ? "" : edit.getText().trim();
            if (v.isEmpty()) return;

            int idx = list.getSelectedIndex();
            if (idx >= 0) m.set(idx, v);
            else m.addElement(v);

            edit.setText("");
            list.clearSelection();
        });

        del.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx >= 0) {
                m.remove(idx);
                edit.setText("");
            }
        });

        JDialog d = new JDialog(getDialogOwnerWindow(), "List ANOMALIE", Dialog.ModalityType.APPLICATION_MODAL);
        save.addActionListener(e -> {
            anomalieNames.clear();
            for (int i = 0; i < m.size(); i++) {
                String v = m.get(i) == null ? "" : m.get(i).trim();
                if (!v.isEmpty()) anomalieNames.add(v);
            }
            refreshAnomalieCombos();
            d.dispose();
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Valeur:"));
        top.add(edit);
        top.add(add);
        top.add(del);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(save);

        d.add(top, BorderLayout.NORTH);
        d.add(new JScrollPane(list), BorderLayout.CENTER);
        d.add(bottom, BorderLayout.SOUTH);
        d.setSize(780, 470);
        d.setLocationRelativeTo(getDialogParentComponent());
        d.setVisible(true);
    }


    private void openOperateurListDialog(Component parent) {
        DefaultTableModel m = new DefaultTableModel(new String[]{"Operateur", "Numero"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return true; }
        };
        for (OperateurItem op : operateurItems) {
            m.addRow(new Object[]{op.name, op.numero});
        }

        JTable table = new JTable(m);
        table.setFont(new Font("Arial", Font.PLAIN, 18));
        table.setRowHeight(28);
        table.getTableHeader().setFont(new Font("Arial", Font.BOLD, 16));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(0).setPreferredWidth(470);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);

        JTextField editName = new JTextField(28);
        JTextField editNumero = new JTextField(8);
        JButton add = new JButton("Ajouter");
        JButton del = new JButton("Effacer");
        JButton save = new JButton("Sauver");

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() >= 0) {
                int r = table.convertRowIndexToModel(table.getSelectedRow());
                editName.setText(String.valueOf(m.getValueAt(r, 0)));
                editNumero.setText(String.valueOf(m.getValueAt(r, 1)));
            }
        });

        add.addActionListener(e -> {
            String name = editName.getText() == null ? "" : editName.getText().trim();
            String numero = editNumero.getText() == null ? "" : editNumero.getText().trim();
            int selected = table.getSelectedRow();
            if (selected >= 0) {
                int r = table.convertRowIndexToModel(selected);
                m.setValueAt(name, r, 0);
                m.setValueAt(numero, r, 1);
            } else if (!name.isEmpty() || !numero.isEmpty()) {
                m.addRow(new Object[]{name, numero});
            }
            editName.setText("");
            editNumero.setText("");
            table.clearSelection();
        });

        del.addActionListener(e -> {
            int r = table.getSelectedRow();
            if (r >= 0) {
                m.removeRow(table.convertRowIndexToModel(r));
                editName.setText("");
                editNumero.setText("");
            }
        });

        JDialog d = new JDialog(getDialogOwnerWindow(), "List NOMS OPERATEURS", Dialog.ModalityType.APPLICATION_MODAL);
        save.addActionListener(e -> {
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
            operateurItems.clear();
            for (int i = 0; i < m.getRowCount(); i++) {
                String name = String.valueOf(m.getValueAt(i, 0)).trim();
                String numero = String.valueOf(m.getValueAt(i, 1)).trim();
                if (!name.isEmpty() || !numero.isEmpty()) {
                    operateurItems.add(new OperateurItem(name, numero));
                }
            }
            d.dispose();
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Nom opérateur:"));
        top.add(editName);
        top.add(new JLabel("Numero:"));
        top.add(editNumero);
        top.add(add);
        top.add(del);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(save);

        d.add(top, BorderLayout.NORTH);
        d.add(new JScrollPane(table), BorderLayout.CENTER);
        d.add(bottom, BorderLayout.SOUTH);
        d.setSize(760, 520);
        d.setLocationRelativeTo(getDialogParentComponent());
        d.setVisible(true);
    }

    private String getSelectedBanburyProvenance() {
        return selectedBanbury == 2 ? "CBANB2" : "CBANB1";
    }

    private String getSelectedZbecDataFilePath() {
        return selectedBanbury == 2 ? ZBEC_B2_DATA_FILE_PATH : ZBEC_B1_DATA_FILE_PATH;
    }

    private int chooseBanburyOnStartup() {
        Object[] options = {"Banbury 1", "Banbury 2"};
        while (true) {
            int choice = JOptionPane.showOptionDialog(
                    getDialogParentComponent(),
                    "Choisir obligatoirement le Banbury avant de continuer:",
                    "Choix Banbury",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]
            );

            if (choice == 0) return 1;
            if (choice == 1) return 2;

            // V193.33: se l'utilisateur ferme la fenetre avec X ou Annuler,
            // quitter le programme au lieu de bloquer dans la boucle de choix.
            if (choice == JOptionPane.CLOSED_OPTION || choice == JOptionPane.CANCEL_OPTION) {
                System.exit(0);
            }

            JOptionPane.showMessageDialog(
                    getDialogParentComponent(),
                    "Vous devez choisir Banbury 1 ou Banbury 2 pour ouvrir le rapport.",
                    "Choix obligatoire",
                    JOptionPane.WARNING_MESSAGE
            );
        }
    }

    public ProdMelBanb() {
        selectedBanbury = chooseBanburyOnStartup();
        initDefaultPersonnelRoles();
        initDefaultOperateurs();
        initDefaultMaintenanceNames();
        initDefaultArrets();
        initDefaultAnomalies();
        setTitle("Production de secteur mélangeur - " + KG_VERSION);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // V190.03: mantiene il layout responsive della V190.02 e corregge il ripristino finestra.
        // Prima di massimizzare viene assegnata una dimensione normale valida: senza questa,
        // premendo il pulsante Ripristina di Windows il JFrame poteva tornare quasi a 0x0.
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int normalW = Math.min(1450, Math.max(1100, screen.width - 80));
        int normalH = Math.min(950, Math.max(720, screen.height - 120));
        setMinimumSize(new Dimension(1000, 650));
        setSize(normalW, normalH);
        setLocationRelativeTo(null);

        ReportPanel reportPanel = new ReportPanel();
        activeReportPanel = reportPanel;

        JScrollPane mainScroll = new JScrollPane(reportPanel,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        mainScroll.setBorder(null);
        mainScroll.getVerticalScrollBar().setUnitIncrement(22);
        mainScroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        mainScroll.getViewport().setBackground(FORM_BACKGROUND);
        mainScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                reportPanel.updateResponsiveWidth(mainScroll.getViewport().getExtentSize().width);
            }
        });
        setContentPane(mainScroll);
        addWindowStateListener(e -> SwingUtilities.invokeLater(() -> {
            if (getWidth() < 1000 || getHeight() < 650) {
                setSize(normalW, normalH);
                setLocationRelativeTo(null);
            }
            reportPanel.updateResponsiveWidth(mainScroll.getViewport().getExtentSize().width);
        }));
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        SwingUtilities.invokeLater(() -> reportPanel.updateResponsiveWidth(mainScroll.getViewport().getExtentSize().width));

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                reportPanel.saveCurrentShiftStateOnClose();
            }
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                // V194.24: ogni passo e' isolato nel proprio try/catch. Prima, se
                // saveCurrentShiftStateOnClose() o stopShiftStateAutosave() lanciavano
                // un'eccezione imprevista, il resto del metodo (disconnessione bilancia
                // + System.exit) non veniva mai eseguito e il processo restava vivo in
                // background con il socket della bilancia ancora aperto: esattamente il
                // sintomo "devo uscire dalla sessione Windows per riconnettere la bilancia".
                try {
                    reportPanel.saveCurrentShiftStateOnClose();
                } catch (Exception ex) {
                    System.err.println("Erreur sauvegarde persistance a la fermeture: " + ex.getMessage());
                }
                try {
                    reportPanel.stopShiftStateAutosave();
                } catch (Exception ex) {
                    System.err.println("Erreur arret autosave a la fermeture: " + ex.getMessage());
                }
                try {
                    reportPanel.shutdownEmbeddedBalanceConnection();
                } catch (Exception ex) {
                    System.err.println("Erreur deconnexion balance a la fermeture: " + ex.getMessage());
                } finally {
                    System.exit(0);
                }
            }
        });

        // V194.24: rete di sicurezza aggiuntiva. Se per qualsiasi motivo il processo
        // dovesse terminare senza passare da windowClosed qui sopra (es. chiusura
        // innescata in un altro modo), questo hook forza comunque la disconnessione
        // pulita del socket della bilancia prima che la JVM finisca di spegnersi.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                reportPanel.shutdownEmbeddedBalanceConnection();
            } catch (Exception ignored) {
            }
        }, "ProdMelBanb-balance-shutdown-hook"));

        revalidate();
    }

    private class ReportPanel extends JPanel {
        private static final int DESIGN_WIDTH = 1700;
        private static final String RESPONSIVE_BASE_BOUNDS = "responsive.base.bounds";
        private static final String RESPONSIVE_BASE_FONT = "responsive.base.font";
        private static final String RESPONSIVE_BASE_COLUMNS = "responsive.base.columns";
        private double responsiveScaleX = 1.0;
        private boolean applyingResponsiveLayout = false;

        private JCheckBox chkArretOui;
        private JCheckBox chkArretNon;
        private JLabel lblArretProductionInfo;
        private JTextField txtTempsArretAuto;
        private final java.util.List<JTextField> txtRaisonsArretAuto = new ArrayList<>();
        private final java.util.List<Component> arretProductionDetails = new ArrayList<>();
        private final java.util.List<Component> additionalInfoComponents = new ArrayList<>();
        private final java.util.Map<Component, Integer> additionalInfoBaseY = new java.util.HashMap<>();
        private JPanel productionTotalPanel;
        private JPanel productionBottomPanel;
        private JTextField txtDechetsMelangeur;
        private JTextField txtConsommationHuile;
        private JTextArea txtRemarquesReport;
        private JTextArea txtPreparationsReport;
        private int productionFooterBaseY = 0;
        private static final int BALANCE_Y = 460;  // V189.35: blocco prodotto/bilancia vicino agli operatori con piccolo spazio
        private static final int BALANCE_PANEL_HEIGHT = 295;  // V192.04: aumenta il riquadro per rendere visibile COMMENT e abbassare il blocco Poids
        private static final int PRODUCTION_SHIFT_Y = BALANCE_PANEL_HEIGHT + 24;
        private int additionalInfoBaseYStart = 1023 + PRODUCTION_SHIFT_Y;
        private ProdMelBanbBalanceSupport.FormFrame embeddedBalanceFrame;
        private JPanel embeddedBalanceHost;
        private Container embeddedBalanceContent;
        private JTextField selectedBalancePersonnelNameField;
        private int currentProductionBottomY = 0;
        private int currentPoidsBottomY = 0;
        private JLabel lblBanbury1;
        private JLabel lblBanbury2;
        private boolean loadingShiftState = false;
        private boolean autosaveInstalled = false;
        private Timer shiftStateSaveTimer;
        private boolean shiftStateDirty = false;
        private String sentShiftKey = null;
        private String confirmedAutomaticTourneeId = null;
        private LocalDateTime nextShiftChangePromptAt = null;
        private boolean shiftChangePromptOpen = false;

        ReportPanel() {
            setLayout(null);
            setBackground(FORM_BACKGROUND);
            buildHeader();
            buildPersonnel();
            buildBanburyEmbeddedSection();
            buildProductionTable();
            buildAdditionalInfoSection();
            loadCurrentShiftStateFromDisk();
            installShiftStateAutosave();
            setPreferredSize(new Dimension(DESIGN_WIDTH, 2400 + PRODUCTION_SHIFT_Y));
            setMinimumSize(new Dimension(DESIGN_WIDTH, 2400 + PRODUCTION_SHIFT_Y));
            captureResponsiveBaselines();
        }

        private void captureResponsiveBaselines() {
            captureResponsiveBaselines(this);
        }

        private void captureResponsiveBaselines(Component c) {
            if (c != this && c instanceof JComponent) {
                JComponent jc = (JComponent) c;
                if (jc.getClientProperty(RESPONSIVE_BASE_BOUNDS) == null) {
                    jc.putClientProperty(RESPONSIVE_BASE_BOUNDS, new Rectangle(c.getBounds()));
                    Font f = c.getFont();
                    if (f != null) jc.putClientProperty(RESPONSIVE_BASE_FONT, f);
                }
            }
            if (c instanceof JTable) {
                JTable table = (JTable) c;
                if (table.getClientProperty(RESPONSIVE_BASE_COLUMNS) == null) {
                    int[] widths = new int[table.getColumnModel().getColumnCount()];
                    for (int i = 0; i < widths.length; i++) {
                        widths[i] = table.getColumnModel().getColumn(i).getPreferredWidth();
                    }
                    table.putClientProperty(RESPONSIVE_BASE_COLUMNS, widths);
                }
            }
            if (c instanceof Container) {
                for (Component child : ((Container) c).getComponents()) {
                    captureResponsiveBaselines(child);
                }
            }
        }

        private void refreshResponsiveBaselinesFromCurrentBounds() {
            refreshResponsiveBaselinesFromCurrentBounds(this);
        }

        private void refreshResponsiveBaselinesFromCurrentBounds(Component c) {
            if (c != this && c instanceof JComponent) {
                JComponent jc = (JComponent) c;
                Rectangle r = c.getBounds();
                int baseX = responsiveScaleX <= 0.0 ? r.x : (int) Math.round(r.x / responsiveScaleX);
                int baseW = responsiveScaleX <= 0.0 ? r.width : (int) Math.round(r.width / responsiveScaleX);
                jc.putClientProperty(RESPONSIVE_BASE_BOUNDS, new Rectangle(baseX, r.y, baseW, r.height));
                if (jc.getClientProperty(RESPONSIVE_BASE_FONT) == null && c.getFont() != null) {
                    jc.putClientProperty(RESPONSIVE_BASE_FONT, c.getFont());
                }
                if (c instanceof JTable && jc.getClientProperty(RESPONSIVE_BASE_COLUMNS) == null) {
                    JTable table = (JTable) c;
                    int[] widths = new int[table.getColumnModel().getColumnCount()];
                    for (int i = 0; i < widths.length; i++) {
                        widths[i] = table.getColumnModel().getColumn(i).getPreferredWidth();
                    }
                    jc.putClientProperty(RESPONSIVE_BASE_COLUMNS, widths);
                }
            }
            if (c instanceof Container) {
                for (Component child : ((Container) c).getComponents()) {
                    refreshResponsiveBaselinesFromCurrentBounds(child);
                }
            }
        }

        private void updateResponsiveWidth(int viewportWidth) {
            if (viewportWidth <= 0 || applyingResponsiveLayout) return;
            applyingResponsiveLayout = true;
            try {
                captureResponsiveBaselines();
                int usableWidth = Math.max(1, viewportWidth - 24);
                double sx = Math.min(1.0, Math.max(0.52, (double) usableWidth / (double) DESIGN_WIDTH));
                responsiveScaleX = sx;
                int targetWidth = Math.max(usableWidth, (int) Math.round(DESIGN_WIDTH * sx));
                applyResponsiveScale(this, sx);
                int maxY = 2400;
                for (Component component : getComponents()) {
                    maxY = Math.max(maxY, component.getY() + component.getHeight() + 30);
                }
                setPreferredSize(new Dimension(targetWidth, maxY));
                setMinimumSize(new Dimension(targetWidth, maxY));
                revalidate();
                repaint();
            } finally {
                applyingResponsiveLayout = false;
            }
        }

        private void applyResponsiveScale(Component c, double sx) {
            if (c != this && c instanceof JComponent) {
                JComponent jc = (JComponent) c;
                Object b = jc.getClientProperty(RESPONSIVE_BASE_BOUNDS);
                if (b instanceof Rectangle) {
                    Rectangle base = (Rectangle) b;
                    c.setBounds(
                            (int) Math.round(base.x * sx),
                            base.y,
                            Math.max(1, (int) Math.round(base.width * sx)),
                            base.height
                    );
                }
                Object f = jc.getClientProperty(RESPONSIVE_BASE_FONT);
                if (f instanceof Font) {
                    Font baseFont = (Font) f;
                    float size = Math.max(8f, (float) (baseFont.getSize2D() * Math.max(0.60, sx)));
                    c.setFont(baseFont.deriveFont(baseFont.getStyle(), size));
                }
                if (c instanceof JTable) {
                    JTable table = (JTable) c;
                    Object widthsObj = jc.getClientProperty(RESPONSIVE_BASE_COLUMNS);
                    if (widthsObj instanceof int[]) {
                        int[] widths = (int[]) widthsObj;
                        for (int i = 0; i < widths.length && i < table.getColumnModel().getColumnCount(); i++) {
                            int w = Math.max(20, (int) Math.round(widths[i] * sx));
                            TableColumn column = table.getColumnModel().getColumn(i);
                            column.setPreferredWidth(w);
                            column.setWidth(w);
                        }
                    }
                    table.getTableHeader().resizeAndRepaint();
                }
            }
            if (c instanceof Container) {
                for (Component child : ((Container) c).getComponents()) {
                    applyResponsiveScale(child, sx);
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
           
        }

        private void buildHeader() {
            addLogo(260, 34, 320, 58);
            addLabel("Journée du", 775, 62, 100, 22, FONT_NORMAL, SwingConstants.RIGHT);
            addLabel(java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    890, 62, 110, 22, FONT_NORMAL, SwingConstants.LEFT);
            JLabel lblCurrentTime = addLabel(java.time.LocalTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                    990, 62, 80, 22, FONT_NORMAL, SwingConstants.LEFT);
            Timer currentTimeTimer = new Timer(30000, e -> lblCurrentTime.setText(java.time.LocalTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))));
            currentTimeTimer.setInitialDelay(1000);
            currentTimeTimer.start();
            addLabel("Equipe:", 520, 183, 70, 24, FONT_NORMAL, SwingConstants.RIGHT);
            lblEquipeValue = addLabel("", 595, 183, 170, 24, FONT_BOLD, SwingConstants.LEFT);
            lblEquipeValue.setToolTipText("Double-clic pour modifier le turno actuel");
            lblEquipeValue.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() >= 2) chooseCurrentShiftManually();
                }
            });
            initializeShiftConfirmationState();
            updateShiftLabel();
            Timer shiftTimer = new Timer(60000, e -> {
                checkAutomaticShiftChangeConfirmation();
                updateShiftLabel();
            });
            shiftTimer.setInitialDelay(1000);
            shiftTimer.start();

            addLabel("Production de secteur mélangeur", 235, 122, 650, 40, FONT_TITLE, SwingConstants.LEFT);
            addLabel("BANBURY", 875, 132, 125, 28, new Font("Arial", Font.BOLD, 22), SwingConstants.CENTER);
            if (selectedBanbury == 1) {
                lblBanbury1 = addBoxLabel("1", 1012, 125, 44, 34, new Font("Arial", Font.BOLD, 24), Color.WHITE);
                lblBanbury2 = null;
            } else {
                lblBanbury1 = null;
                lblBanbury2 = addBoxLabel("2", 1012, 125, 44, 34, new Font("Arial", Font.BOLD, 24), Color.WHITE);
            }
            applyBanburySelectionStyle();

            JButton btnSetup = new JButton("SETUP");
            btnSetup.setFont(new Font("Arial", Font.BOLD, 13));
            btnSetup.setBounds(5, 32, 105, 34);
            btnSetup.addActionListener(e -> openSetupDialog());
            add(btnSetup);

            JButton btnReset = new JButton("RESET RAPPORT");
            btnReset.setFont(new Font("Arial", Font.BOLD, 13));
            btnReset.setBounds(5, 72, 150, 34);
            btnReset.addActionListener(e -> resetAllFieldsAndCalculations());
            add(btnReset);

            JButton btnACC = new JButton("ACC");
            btnACC.setFont(new Font("Arial", Font.BOLD, 13));
            btnACC.setBounds(1175, 0, 230, 34);
            btnACC.addActionListener(e -> openAccDialog());
            add(btnACC);

            JButton btnZBEC = new JButton("ZBEC");
            btnZBEC.setFont(new Font("Arial", Font.BOLD, 13));
            btnZBEC.setBounds(1175, 35, 230, 34);
            btnZBEC.addActionListener(e -> new ZBEC(selectedBanbury).setVisible(true));
            add(btnZBEC);

            JButton btnTicket = new JButton("Ticket");
            btnTicket.setFont(new Font("Arial", Font.BOLD, 13));
            btnTicket.setBounds(1175, 76, 230, 34);
            btnTicket.addActionListener(e -> new TicketFormula().setVisible(true));
            add(btnTicket);

            JButton btnImprimer = new JButton("Imprimer");
            btnImprimer.setFont(new Font("Arial", Font.BOLD, 13));
            btnImprimer.setBounds(1175, 117, 230, 34);
            btnImprimer.addActionListener(e -> printJasperReport());
            add(btnImprimer);

            JButton btnStampaInvia = new JButton("Enregistrer + Envoyer");
            btnStampaInvia.setFont(new Font("Arial", Font.BOLD, 13));
            btnStampaInvia.setBounds(1175, 258, 230, 34);
            btnStampaInvia.addActionListener(e -> printAndSendJasperReport());
            add(btnStampaInvia);

        }


        private void applyBanburySelectionStyle() {
            Color selectedBg = new Color(255, 235, 120);
            Color unselectedBg = new Color(230, 230, 230);
            Color selectedBorder = new Color(0, 90, 180);
            Color unselectedBorder = new Color(45, 45, 45);

            if (lblBanbury1 != null) {
                boolean selected = selectedBanbury == 1;
                lblBanbury1.setBackground(selected ? selectedBg : unselectedBg);
                lblBanbury1.setBorder(new LineBorder(selected ? selectedBorder : unselectedBorder, selected ? 3 : 2));
                lblBanbury1.repaint();
            }

            if (lblBanbury2 != null) {
                boolean selected = selectedBanbury == 2;
                lblBanbury2.setBackground(selected ? selectedBg : unselectedBg);
                lblBanbury2.setBorder(new LineBorder(selected ? selectedBorder : unselectedBorder, selected ? 3 : 2));
                lblBanbury2.repaint();
            }
        }

        private void chooseCurrentShiftManually() {
            saveCurrentShiftStateQuietly();
            String[] values = {"T1 Matin", "T2 Apres Midi", "T3 Nuit"};
            String selected = (String) JOptionPane.showInputDialog(getDialogParentComponent(), "Choisir le turno actuel:", "Equipe",
                    JOptionPane.QUESTION_MESSAGE, null, values, shiftDisplayName(getCurrentShiftWindow().tourneeId));
            if (selected == null) return;
            if (selected.startsWith("T1")) manualTourneeId = "1";
            else if (selected.startsWith("T2")) manualTourneeId = "2";
            else manualTourneeId = "3";
            manualShiftExpiresAt = nextShiftBoundary(LocalDateTime.now());
            updateShiftLabel();
            loadCurrentShiftStateFromDisk();
            refreshProductionPassagesIfPossible();
            refreshDechetsMelangeurFromDb();
        }

        private void initializeShiftConfirmationState() {
            if (confirmedAutomaticTourneeId == null) {
                confirmedAutomaticTourneeId = getAutomaticShiftWindow(LocalDateTime.now()).tourneeId;
            }
        }

        private void checkAutomaticShiftChangeConfirmation() {
            if (shiftChangePromptOpen) return;

            LocalDateTime now = LocalDateTime.now();
            CurrentShiftWindow automaticShift = getAutomaticShiftWindow(now);
            if (confirmedAutomaticTourneeId == null) {
                confirmedAutomaticTourneeId = automaticShift.tourneeId;
                return;
            }

            boolean manualExpired = manualShiftExpiresAt != null && !now.isBefore(manualShiftExpiresAt);
            boolean automaticChanged = !automaticShift.tourneeId.equals(confirmedAutomaticTourneeId);
            if (!manualExpired && !automaticChanged) {
                nextShiftChangePromptAt = null;
                return;
            }
            if (nextShiftChangePromptAt != null && now.isBefore(nextShiftChangePromptAt)) return;

            String previousTourneeId = manualTourneeId != null ? manualTourneeId : confirmedAutomaticTourneeId;
            String previousShift = shiftDisplayName(previousTourneeId);
            String nextShift = automaticShift.displayName;

            shiftChangePromptOpen = true;
            try {
                Object[] options = {"PAS ENCORE", "OUI"};
                int choice = JOptionPane.showOptionDialog(
                        getDialogParentComponent(),
                        "Il est l'heure de passer de " + previousShift + " à " + nextShift + ".\n"
                                + "Peut-on changer l'équipe maintenant ?",
                        "Changement d'équipe",
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        options,
                        options[0]
                );

                if (choice == 1) {
                    saveCurrentShiftStateQuietly();
                    manualTourneeId = null;
                    manualShiftExpiresAt = null;
                    nextShiftChangePromptAt = null;
                    confirmedAutomaticTourneeId = automaticShift.tourneeId;
                    updateShiftLabel();
                    loadCurrentShiftStateFromDisk();
                    refreshProductionPassagesIfPossible();
                    refreshDechetsMelangeurFromDb();
                } else {
                    // L'utilisateur a choisi "PAS ENCORE": on garde volontairement
                    // l'équipe précédente dans le formulaire et on repropose la question
                    // toutes les 5 minutes jusqu'à la confirmation avec "OUI".
                    nextShiftChangePromptAt = LocalDateTime.now().plusMinutes(5);
                    if (manualExpired) {
                        manualShiftExpiresAt = null;
                    }
                }
            } finally {
                shiftChangePromptOpen = false;
            }
        }

        private void updateShiftLabel() {
            if (lblEquipeValue != null) {
                CurrentShiftWindow w = getCurrentShiftWindow();
                lblEquipeValue.setText(w.displayName + (manualTourneeId != null ? " *" : ""));
            }
        }

        private String shiftDisplayName(String tourneeId) {
            if ("1".equals(tourneeId)) return "T1 Matin";
            if ("2".equals(tourneeId)) return "T2 Apres Midi";
            return "T3 Nuit";
        }

        private LocalDateTime nextShiftBoundary(LocalDateTime now) {
            LocalDate d = now.toLocalDate();
            LocalTime t = now.toLocalTime();
            if (t.isBefore(LocalTime.of(6, 0))) return LocalDateTime.of(d, LocalTime.of(6, 0));
            if (t.isBefore(LocalTime.of(14, 0))) return LocalDateTime.of(d, LocalTime.of(14, 0));
            if (t.isBefore(LocalTime.of(22, 0))) return LocalDateTime.of(d, LocalTime.of(22, 0));
            return LocalDateTime.of(d.plusDays(1), LocalTime.of(6, 0));
        }

        private void resetAllFieldsAndCalculations() {
            int choice = JOptionPane.showConfirmDialog(getDialogParentComponent(), "Voulez-vous vider tous les champs et recalculs ?", "RESET", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) return;
            resettingForm = true;
            try {
                clearFieldsInContainer(this);
                forceEmbeddedBalanceBanburySelection();
                resetBalanceOperateurDisplay();
                if (productionModel != null) {
                    productionModel.setRowCount(0);
                    for (int i = 0; i < 10; i++) productionModel.addRow(createEmptyRow(PRODUCTION_COL_COUNT));
                }
                if (txtHeuresTotales != null) txtHeuresTotales.setText("0H00");
                updatePersonnelTotals();
                deleteCurrentShiftStateFileAfterReset();
            } finally {
                resettingForm = false;
            }
        }

        private void clearFieldsInContainer(Container container) {
            for (Component c : container.getComponents()) {
                // V194.28: RESET RAPPORT deve svuotare solo la tabella di produzione e i
                // relativi totali, non la sezione BALANCE (prodotto/pesata in corso,
                // Provenance/Destination, palette). Salta interamente il sottoalbero
                // della bilancia incorporata.
                // V194.29: eccezione al salto sopra: il campo Operateur Balance viene
                // comunque azzerato subito dopo, perche' deve seguire il campo Operatore
                // (Personnel) da cui dipende - vedi resetBalanceOperateurDisplay().
                if (c == embeddedBalanceContent) continue;
                if (c instanceof JTextField) ((JTextField) c).setText("");
                else if (c instanceof JComboBox) ((JComboBox<?>) c).setSelectedItem("");
                else if (c instanceof JScrollPane) {
                    JViewport vp = ((JScrollPane) c).getViewport();
                    if (vp != null && vp.getView() instanceof Container) clearFieldsInContainer((Container) vp.getView());
                } else if (c instanceof Container) clearFieldsInContainer((Container) c);
            }
        }

        private void refreshProductionPassagesIfPossible() {
            if (productionTable != null) refreshProductionPassages(productionTable);
        }


        private File findRappProdMelReportDir() {
            String[] dirs = {
                    "C:\\_Tools\\Banbury2",
                    "C:\\_tools\\banbury2",
                    "C:\\_Tools\\Banbury",
                    "C:\\_tools\\banbury",
                    "C:\\_tools",
                    System.getProperty("user.dir", ".")
            };

            for (String dirName : dirs) {
                File dir = new File(dirName);
                File jasper = new File(dir, "RappProdMel_RPT.jasper");
                File jrxml = new File(dir, "RappProdMel_RPT.jrxml");
                if (jasper.exists() || jrxml.exists()) {
                    return dir;
                }
            }

            // Cartella preferita per il report produzione. Se non esiste ancora,
            // il messaggio di errore spieghera dove copiare il file .jasper.
            return new File("C:\\_Tools\\Banbury2");
        }

        private void appendRappProdMelReportSearchDebug(StringBuilder debug) {
            if (debug == null) return;
            String[] dirs = {
                    "C:\\_Tools\\Banbury2",
                    "C:\\_tools\\banbury2",
                    "C:\\_Tools\\Banbury",
                    "C:\\_tools\\banbury",
                    "C:\\_tools",
                    System.getProperty("user.dir", ".")
            };
            debug.append("Percorsi controllati per RappProdMel_RPT:\n");
            for (String dirName : dirs) {
                File dir = new File(dirName);
                debug.append(" - ").append(dir.getAbsolutePath())
                        .append(" | dirExists=").append(dir.exists())
                        .append(" | jasper=").append(new File(dir, "RappProdMel_RPT.jasper").exists())
                        .append(" | jrxml=").append(new File(dir, "RappProdMel_RPT.jrxml").exists())
                        .append("\n");
            }
        }

        private void printJasperReport() {
            // Durante la stampa bisogna leggere i valori gia presenti nel form.
            // stopCellEditing() deve solo confermare il testo digitato, senza rilanciare
            // la ricerca DB e senza riaprire la finestra "Controllo pesate DB prima della somma".
            if (productionTable != null) {
                productionTable.putClientProperty("suppressProductionDbLookupDuringPrint", Boolean.TRUE);
            }

            if (productionTable != null && productionTable.isEditing()) {
                TableCellEditor editor = productionTable.getCellEditor();
                if (editor != null) editor.stopCellEditing();
            }
            if (poidsTable != null && poidsTable.isEditing()) {
                TableCellEditor editor = poidsTable.getCellEditor();
                if (editor != null) editor.stopCellEditing();
            }

            File reportDir = findRappProdMelReportDir();
            File jrxmlFile = new File(reportDir, "RappProdMel_RPT.jrxml");
            File jasperFile = new File(reportDir, "RappProdMel_RPT.jasper");
            File logFile = new File(reportDir, "RappProdMel_print_error.log");

            StringBuilder debug = new StringBuilder();
            debug.append("Version: ").append(KG_VERSION).append("\n");
            debug.append("Java: ").append(System.getProperty("java.version")).append("\n");
            debug.append("Report dir: ").append(reportDir.getAbsolutePath()).append("\n");
            debug.append("Report dir exists: ").append(reportDir.exists()).append(" / canRead: ").append(reportDir.canRead()).append(" / canWrite: ").append(reportDir.canWrite()).append("\n");
            debug.append(describeReportFile("JRXML", jrxmlFile)).append("\n");
            debug.append(describeReportFile("JASPER", jasperFile)).append("\n");
            appendRappProdMelReportSearchDebug(debug);

            try {
                if (!jrxmlFile.exists() && !jasperFile.exists()) {
                    JOptionPane.showMessageDialog(getDialogParentComponent(),
                            "Report introuvable.\n\n" + debug
                                    + "\nCopiez RappProdMel_RPT.jasper dans C:\\_Tools\\Banbury2 ou C:\\_Tools\\Banbury",
                            "Imprimer", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                java.util.Map<String, Object> params = buildJasperParameters();
                debug.append("Parametres Jasper: ").append(params.size()).append("\n");

                JasperReport jasperReport = null;
                Exception compileException = null;

                // V190.11: preferisce il file .jasper gia compilato. Nel JAR runnable
                // la compilazione del .jrxml puo fallire o sembrare bloccata se mancano
                // le dipendenze del compilatore Jasper/Janino.
                if (jasperFile.exists()) {
                    try {
                        debug.append("Tentative: chargement JASPER compile\n");
                        try (FileInputStream in = new FileInputStream(jasperFile)) {
                            jasperReport = (JasperReport) JRLoader.loadObject(in);
                        }
                        debug.append("Chargement JASPER: OK\n");
                    } catch (Exception exLoad) {
                        debug.append("Chargement JASPER: ERREUR\n");
                        debug.append(getFullErrorMessageDeep(exLoad)).append("\n");
                    }
                }

                if (jasperReport == null && jrxmlFile.exists()) {
                    debug.append("Controle XML avant compilation:\n");
                    debug.append(validateXmlForDebug(jrxmlFile)).append("\n");

                    try {
                        debug.append("Tentative: compilation JRXML avec validation XML Jasper DESACTIVEE\n");
                        System.setProperty("net.sf.jasperreports.compiler.xml.validation", "false");
                        System.setProperty("net.sf.jasperreports.xml.validation", "false");
                        jasperReport = JasperCompileManager.compileReport(jrxmlFile.getAbsolutePath());
                        debug.append("Compilation JRXML: OK\n");
                    } catch (Exception exCompile) {
                        compileException = exCompile;
                        debug.append("Compilation JRXML: ERREUR\n");
                        debug.append(getFullErrorMessageDeep(exCompile)).append("\n");
                        debug.append("\nAnalyse JRXML rapide:\n");
                        debug.append(analyzeJrxmlForDebug(jrxmlFile)).append("\n");
                    }
                }

                if (jasperReport == null) {
                    throw new IllegalStateException("Aucun report charge. Le JRXML existe mais ne compile pas, et aucun JASPER valide n'est disponible.", compileException);
                }

                debug.append("Tentative: fillReport\n");
                JasperPrint print = JasperFillManager.fillReport(
                        jasperReport,
                        params,
                        new JREmptyDataSource()
                );
                debug.append("fillReport: OK, pages=").append(print == null ? "null" : print.getPages().size()).append("\n");

                saveCurrentShiftStateQuietly();
                JasperViewer.viewReport(print, false);
            } catch (Exception ex) {
                String details = debug.toString() + "\nErreur complete:\n" + getFullErrorMessageDeep(ex) + "\n\nStack trace:\n" + getStackTraceText(ex);
                String savedLogPath = writePrintErrorLog(logFile, details);

                JTextArea area = new JTextArea(details + "\nLog: " + savedLogPath);
                area.setEditable(false);
                area.setLineWrap(true);
                area.setWrapStyleWord(true);
                area.setFont(new Font("Consolas", Font.PLAIN, 12));
                JScrollPane scroll = new JScrollPane(area);
                scroll.setPreferredSize(new Dimension(900, 520));

                JOptionPane.showMessageDialog(getDialogParentComponent(), scroll, "Erreur impression JasperReport - details", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            } finally {
                if (productionTable != null) {
                    productionTable.putClientProperty("suppressProductionDbLookupDuringPrint", Boolean.FALSE);
                }
            }
        }


        private JasperPrint createCurrentJasperPrint(StringBuilder debug) throws Exception {
            File reportDir = findRappProdMelReportDir();
            File jrxmlFile = new File(reportDir, "RappProdMel_RPT.jrxml");
            File jasperFile = new File(reportDir, "RappProdMel_RPT.jasper");

            debug.append("Version: ").append(KG_VERSION).append("\n");
            debug.append("Java: ").append(System.getProperty("java.version")).append("\n");
            debug.append("Report dir: ").append(reportDir.getAbsolutePath()).append("\n");
            debug.append("Report dir exists: ").append(reportDir.exists()).append(" / canRead: ").append(reportDir.canRead()).append(" / canWrite: ").append(reportDir.canWrite()).append("\n");
            debug.append(describeReportFile("JRXML", jrxmlFile)).append("\n");
            debug.append(describeReportFile("JASPER", jasperFile)).append("\n");
            appendRappProdMelReportSearchDebug(debug);

            if (!jrxmlFile.exists() && !jasperFile.exists()) {
                throw new IllegalStateException("Report introuvable. Copiez RappProdMel_RPT.jasper dans C:\\_Tools\\Banbury2 ou C:\\_Tools\\Banbury");
            }

            java.util.Map<String, Object> params = buildJasperParameters();
            debug.append("Parametres Jasper: ").append(params.size()).append("\n");

            JasperReport jasperReport = null;
            Exception compileException = null;

            // V190.11: preferisce il file .jasper gia compilato. Nel JAR runnable
            // la compilazione del .jrxml puo fallire o sembrare bloccata se mancano
            // le dipendenze del compilatore Jasper/Janino.
            if (jasperFile.exists()) {
                try {
                    debug.append("Tentative: chargement JASPER compile\n");
                    try (FileInputStream in = new FileInputStream(jasperFile)) {
                        jasperReport = (JasperReport) JRLoader.loadObject(in);
                    }
                    debug.append("Chargement JASPER: OK\n");
                } catch (Exception exLoad) {
                    debug.append("Chargement JASPER: ERREUR\n");
                    debug.append(getFullErrorMessageDeep(exLoad)).append("\n");
                }
            }

            if (jasperReport == null && jrxmlFile.exists()) {
                debug.append("Controle XML avant compilation:\n");
                debug.append(validateXmlForDebug(jrxmlFile)).append("\n");
                try {
                    debug.append("Tentative: compilation JRXML avec validation XML Jasper DESACTIVEE\n");
                    System.setProperty("net.sf.jasperreports.compiler.xml.validation", "false");
                    System.setProperty("net.sf.jasperreports.xml.validation", "false");
                    jasperReport = JasperCompileManager.compileReport(jrxmlFile.getAbsolutePath());
                    debug.append("Compilation JRXML: OK\n");
                } catch (Exception exCompile) {
                    compileException = exCompile;
                    debug.append("Compilation JRXML: ERREUR\n");
                    debug.append(getFullErrorMessageDeep(exCompile)).append("\n");
                    debug.append("\nAnalyse JRXML rapide:\n");
                    debug.append(analyzeJrxmlForDebug(jrxmlFile)).append("\n");
                }
            }

            if (jasperReport == null) {
                throw new IllegalStateException("Aucun report charge. Le JRXML existe mais ne compile pas, et aucun JASPER valide n'est disponible.", compileException);
            }

            debug.append("Tentative: fillReport\n");
            JasperPrint print = JasperFillManager.fillReport(jasperReport, params, new JREmptyDataSource());
            debug.append("fillReport: OK, pages=").append(print == null ? "null" : print.getPages().size()).append("\n");
            return print;
        }

        private void printAndSendJasperReport() {
            saveCurrentShiftStateQuietly();
            if (productionTable != null) {
                productionTable.putClientProperty("suppressProductionDbLookupDuringPrint", Boolean.TRUE);
            }
            if (productionTable != null && productionTable.isEditing()) {
                TableCellEditor editor = productionTable.getCellEditor();
                if (editor != null) editor.stopCellEditing();
            }
            if (poidsTable != null && poidsTable.isEditing()) {
                TableCellEditor editor = poidsTable.getCellEditor();
                if (editor != null) editor.stopCellEditing();
            }

            File logFile = new File(getPersistenceDir(), "RappProdMel_print_send_error.log");
            StringBuilder debug = new StringBuilder();
            try {
                JasperPrint print = createCurrentJasperPrint(debug);
                File target = new File(getReportOutputDir(), getCurrentReportBaseName() + ".pdf");
                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                JasperExportManager.exportReportToPdfFile(print, target.getAbsolutePath());
                JasperViewer.viewReport(print, false);
                saveShiftReportToDatabase();
                deleteCurrentShiftStateFile();
                JOptionPane.showMessageDialog(getDialogParentComponent(),
                        "Rapport imprimé et sauvegardé:\n" + target.getAbsolutePath(),
                        "Enregistrer + Envoyer", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                String details = debug.toString() + "\nErreur complete:\n" + getFullErrorMessageDeep(ex) + "\n\nStack trace:\n" + getStackTraceText(ex);
                String savedLogPath = writePrintErrorLog(logFile, details);
                JTextArea area = new JTextArea(details + "\nLog: " + savedLogPath);
                area.setEditable(false);
                area.setLineWrap(true);
                area.setWrapStyleWord(true);
                area.setFont(new Font("Consolas", Font.PLAIN, 12));
                JScrollPane scroll = new JScrollPane(area);
                scroll.setPreferredSize(new Dimension(900, 520));
                JOptionPane.showMessageDialog(getDialogParentComponent(), scroll, "Erreur Stampa + Invia", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            } finally {
                if (productionTable != null) {
                    productionTable.putClientProperty("suppressProductionDbLookupDuringPrint", Boolean.FALSE);
                }
            }
        }

        // V194.19: salvataggio del rapporto turno su SQL Server (DATA_BAMBURY / SRV-BAMBURY).
        // Riusa gli stessi valori gia' assemblati per il PDF Jasper (buildJasperParameters())
        // cosi' il contenuto salvato a DB coincide sempre con quello stampato.
        private void saveShiftReportToDatabase() {
            try {
                DBBamburyReader.ShiftReportData data = buildShiftReportDataForDb();
                DBBamburyReader.saveShiftReport(data);
            } catch (IllegalStateException duplicate) {
                // Rapporto gia' registrato per questo turno: non deve bloccare la stampa.
                System.out.println("[DATA_BAMBURY] " + duplicate.getMessage());
            } catch (Exception ex) {
                File logFile = new File(getPersistenceDir(), "RappProdMel_db_save_error.log");
                String details = "Errore salvataggio su DATA_BAMBURY:\n" + getFullErrorMessageDeep(ex)
                        + "\n\nStack trace:\n" + getStackTraceText(ex);
                String savedLogPath = writePrintErrorLog(logFile, details);
                JOptionPane.showMessageDialog(getDialogParentComponent(),
                        "Il rapport a été imprimé mais N'A PAS été enregistré dans la base DATA_BAMBURY.\n"
                                + "Détails: " + savedLogPath,
                        "Attention - Enregistrement DB échoué", JOptionPane.WARNING_MESSAGE);
                ex.printStackTrace();
            }
        }

        private DBBamburyReader.ShiftReportData buildShiftReportDataForDb() {
            java.util.Map<String, Object> p = buildJasperParameters();
            CurrentShiftWindow shift = getCurrentShiftWindow();

            DBBamburyReader.ShiftReportData data = new DBBamburyReader.ShiftReportData();
            data.reportDate = shift.start.toLocalDate();
            data.tourneeId = shift.tourneeId;
            data.tourneeName = shift.displayName;
            data.banbury = selectedBanbury;
            data.center = getSelectedBanburyProvenance();
            data.hhFrom = shift.start.toLocalTime();
            data.hhTo = shift.end.toLocalTime();
            data.totHH = minutesToHours(parseFrenchTime(str(p.get("HEURES_TOTALES"))));
            data.chefSecteur = str(p.get("CHEF_SECTEUR"));
            data.dechetsMelangeur = str(p.get("DECHETS_MELANGEUR"));
            data.consommationHuile = str(p.get("CONSOMMATION_HUILE"));
            data.remarques = str(p.get("REMARQUES"));
            data.preparations = str(p.get("PREPARATIONS"));
            data.arretInfo = str(p.get("ARRET_INFO"));
            data.tempsArretMin = parseFrenchTime(str(p.get("TEMPS_ARRET")));
            data.totalMelanges = str(p.get("TOTAL_MELANGES"));
            data.totalKg = str(p.get("TOTAL_KG"));
            data.totalMinutes = str(p.get("TOTAL_MINUTES"));
            data.appVersion = KG_VERSION;
            data.createdBy = System.getProperty("user.name", "");

            for (int r = 0; r < 8; r++) {
                String role = str(p.get("PERSONNEL_" + r + "_ROLE"));
                String code = str(p.get("PERSONNEL_" + r + "_CODE"));
                String name = str(p.get("PERSONNEL_" + r + "_NAME"));
                String debut = str(p.get("PERSONNEL_" + r + "_DE"));
                String fin = str(p.get("PERSONNEL_" + r + "_A"));
                String total = str(p.get("PERSONNEL_" + r + "_TOTAL"));
                if (role.isEmpty() && code.isEmpty() && name.isEmpty() && debut.isEmpty() && fin.isEmpty()) {
                    continue;
                }

                DBBamburyReader.PersonnelRow row = new DBBamburyReader.PersonnelRow();
                row.rowIndex = r;
                row.duty = role;
                row.dep = null; // Non presente nel modello dati attuale di ProdMelBanb.
                row.badge = code;
                row.name = name;
                row.hhFrom = debut;
                row.hhTo = fin;
                row.hh = minutesToHours(parseFrenchTime(total));
                data.personnel.add(row);
            }

            if (productionModel != null) {
                for (int r = 0; r < productionModel.getRowCount(); r++) {
                    if (isProductionRowEmpty(productionModel, r)) continue;

                    String article = valueAt(productionModel, r, 0);
                    String produit = valueAt(productionModel, r, 1);
                    String lot = valueAt(productionModel, r, 2);
                    String nombreMelanges = valueAt(productionModel, r, 3);
                    String couche = valueAt(productionModel, r, 5);
                    String passage = valueAt(productionModel, r, 6);
                    String poids = valueAt(productionModel, r, 7);
                    String couleur = valueAt(productionModel, r, 8);
                    String nSerie = valueAt(productionModel, r, 9);
                    String remarque = valueAt(productionModel, r, 10);
                    String de = valueAt(productionModel, r, 11);
                    String a = valueAt(productionModel, r, 12);
                    String temps = valueAt(productionModel, r, 13);
                    String zbecInf = valueAt(productionModel, r, 14);
                    String zbecSup = valueAt(productionModel, r, 15);
                    Integer timeMinutes = parseFrenchTime(temps);

                    if (article.toUpperCase(Locale.ROOT).startsWith("ARRET")) {
                        DBBamburyReader.BreakRow brk = new DBBamburyReader.BreakRow();
                        brk.rowIndex = r;
                        brk.type = article;
                        brk.description = produit;
                        brk.batch = lot;
                        brk.minutes = timeMinutes;
                        data.breaks.add(brk);
                        continue;
                    }

                    DBBamburyReader.ProductionRow row = new DBBamburyReader.ProductionRow();
                    row.rowIndex = r;
                    row.batch = lot;
                    row.item = article;
                    row.nrLotti = parseLenientInt(nombreMelanges);
                    row.um = "KG";
                    row.valid = false;
                    row.timeMinutes = timeMinutes;
                    row.hhFrom = de;
                    row.hhTo = a;
                    row.production = produit;
                    row.type = passage.isEmpty() ? couche : passage;
                    row.quantity = poids;
                    row.couleur = couleur;
                    row.nSerie = nSerie;
                    row.remarque = remarque;
                    row.zbecInf = zbecInf;
                    row.zbecSup = zbecSup;
                    data.production.add(row);
                }
            }

            return data;
        }

        private String str(Object value) {
            return value == null ? "" : value.toString().trim();
        }

        private Double minutesToHours(Integer minutes) {
            return minutes == null ? null : minutes / 60.0;
        }

        private Integer parseLenientInt(String raw) {
            if (raw == null) return null;
            String digits = raw.replaceAll("[^0-9-]", "");
            if (digits.isEmpty() || "-".equals(digits)) return null;
            try {
                return Integer.parseInt(digits);
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        private File getReportOutputDir() {
            if (selectedBanbury == 1) {
                return new File("\\\\neo\\PUBLIC-DOCUMENTS\\Direction technique\\Docs Banbury\\Rapports Banbury\\B1");
            } else {
                return new File("\\\\neo\\PUBLIC-DOCUMENTS\\Direction technique\\Docs Banbury\\Rapports Banbury\\B2");
            }
        }

        private File getPersistenceDir() {
            return new File("C:\\_tools\\banbury");
        }

        private String getCurrentReportBaseName() {
            CurrentShiftWindow shift = getCurrentShiftWindow();
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyMMdd");
            String datePart = shift.start.toLocalDate().format(fmt);
            return datePart + "-B" + selectedBanbury + "-T" + shift.tourneeId;
        }

        private File getCurrentShiftStateFile() {
            return new File(getPersistenceDir(), getCurrentReportBaseName() + ".properties");
        }

        private void deleteCurrentShiftStateFile() {
            sentShiftKey = getCurrentReportBaseName();
            deleteCurrentShiftStateFileOnly();
        }

        private void deleteCurrentShiftStateFileAfterReset() {
            sentShiftKey = null;
            shiftStateDirty = false;
            deleteCurrentShiftStateFileOnly();
        }

        private void deleteCurrentShiftStateFileOnly() {
            File f = getCurrentShiftStateFile();
            if (f.exists() && !f.delete()) {
                System.err.println("Impossible d'effacer le fichier de persistance: " + f.getAbsolutePath());
            }
        }

        private void installShiftStateAutosave() {
            if (autosaveInstalled) return;
            autosaveInstalled = true;
            // V193.09: il salvataggio automatico non parte piu a ogni modifica.
            // Le modifiche marcano solo il rapporto come dirty; il file viene scritto
            // ogni 3 minuti, oppure immediatamente quando si chiude il programma.
            shiftStateSaveTimer = new Timer(180000, e -> saveCurrentShiftStatePeriodically());
            shiftStateSaveTimer.setRepeats(true);
            shiftStateSaveTimer.start();
            installAutosaveListenersInContainer(this);
            if (productionModel != null) productionModel.addTableModelListener(e -> markShiftStateDirty());
            if (poidsModel != null) poidsModel.addTableModelListener(e -> markShiftStateDirty());
        }

        private void installAutosaveListenersInContainer(Container container) {
            if (container == null) return;
            for (Component c : container.getComponents()) {
                attachAutosaveListener(c);

                if (c instanceof JComboBox) {
                    continue;
                }

                // V194.22: un JTextArea dentro un JScrollPane (es. REMARQUES/PREPARATIONS
                // in fondo al form) non ha componenti figli: ricorrere su di esso come
                // fosse un Container non installava mai il listener. Ora il listener viene
                // attaccato direttamente alla view dello JViewport, come gia fatto in
                // collectPersistableComponents() per il salvataggio dei valori.
                if (c instanceof JScrollPane) {
                    JViewport vp = ((JScrollPane) c).getViewport();
                    Component view = vp == null ? null : vp.getView();
                    attachAutosaveListener(view);
                    if (view instanceof Container && !isPersistableComponent(view)) {
                        installAutosaveListenersInContainer((Container) view);
                    }
                } else if (c instanceof Container) {
                    installAutosaveListenersInContainer((Container) c);
                }
            }
        }

        private void attachAutosaveListener(Component c) {
            if (c instanceof JTextField) {
                ((JTextField) c).getDocument().addDocumentListener(new DocumentListener() {
                    @Override public void insertUpdate(DocumentEvent e) { markShiftStateDirty(); }
                    @Override public void removeUpdate(DocumentEvent e) { markShiftStateDirty(); }
                    @Override public void changedUpdate(DocumentEvent e) { markShiftStateDirty(); }
                });
            } else if (c instanceof JTextArea) {
                ((JTextArea) c).getDocument().addDocumentListener(new DocumentListener() {
                    @Override public void insertUpdate(DocumentEvent e) { markShiftStateDirty(); }
                    @Override public void removeUpdate(DocumentEvent e) { markShiftStateDirty(); }
                    @Override public void changedUpdate(DocumentEvent e) { markShiftStateDirty(); }
                });
            } else if (c instanceof JComboBox) {
                ((JComboBox<?>) c).addActionListener(e -> markShiftStateDirty());
            } else if (c instanceof JCheckBox) {
                // V194.22: anche le JCheckBox vengono persistite (collectPersistableComponents)
                // ma finora non marcavano il rapporto come "dirty" quando l'utente le spuntava.
                ((JCheckBox) c).addActionListener(e -> markShiftStateDirty());
            }
        }

        private boolean isAnyReportTableEditing() {
            return (productionTable != null && productionTable.isEditing())
                    || (poidsTable != null && poidsTable.isEditing());
        }

        private void markShiftStateDirty() {
            if (loadingShiftState || resettingForm || getCurrentReportBaseName().equals(sentShiftKey)) return;
            shiftStateDirty = true;
        }

        private void saveCurrentShiftStatePeriodically() {
            if (!shiftStateDirty) return;
            saveCurrentShiftStateQuietly();
        }

        public void stopShiftStateAutosave() {
            if (shiftStateSaveTimer != null) {
                shiftStateSaveTimer.stop();
            }
        }

        // V194.23: alla chiusura del form va chiuso anche lo stream/socket della
        // bilancia incorporata, altrimenti resta agganciato al processo Java
        // anche dopo che la finestra e sparita.
        void shutdownEmbeddedBalanceConnection() {
            if (embeddedBalanceFrame != null) {
                embeddedBalanceFrame.forceDisconnectBalanceServiceOnExit();
            }
        }

        public void saveCurrentShiftStateQuietly() {
            if (loadingShiftState || resettingForm || getCurrentReportBaseName().equals(sentShiftKey)) return;
            // V193.09: il timer a 3 minuti non deve interrompere una cella in modifica.
            // Se una JTable e ancora in editing, il salvataggio periodico salta questo giro
            // e salvera al giro successivo dopo INVIO/TAB.
            if (isAnyReportTableEditing()) return;
            try {
                saveCurrentShiftStateToDisk();
                shiftStateDirty = false;
            } catch (Exception ex) {
                System.err.println("Erreur sauvegarde persistance rapport: " + ex.getMessage());
            }
        }

        public void saveCurrentShiftStateOnClose() {
            if (loadingShiftState || resettingForm || getCurrentReportBaseName().equals(sentShiftKey)) return;
            try {
                if (productionTable != null && productionTable.isEditing()) {
                    TableCellEditor editor = productionTable.getCellEditor();
                    if (editor != null) editor.stopCellEditing();
                }
                if (poidsTable != null && poidsTable.isEditing()) {
                    TableCellEditor editor = poidsTable.getCellEditor();
                    if (editor != null) editor.stopCellEditing();
                }
                saveCurrentShiftStateToDisk();
                shiftStateDirty = false;
            } catch (Exception ex) {
                System.err.println("Erreur sauvegarde persistance rapport fermeture: " + ex.getMessage());
            }
        }

        private void saveCurrentShiftStateToDisk() throws Exception {
            // V151: prima di salvare la persistenza, ricalcola sempre la colonna Temps
            // dai valori gia presenti nelle colonne de / à. Cosi il turno ricaricato
            // mantiene i tempi corretti anche dopo uscita/rientro dal programma.
            if (productionModel != null) {
                recalculateAllProductionDurations(productionModel);
            }

            File dir = getPersistenceDir();
            if (!dir.exists()) dir.mkdirs();
            java.util.Properties props = new java.util.Properties();
            props.setProperty("version", KG_VERSION);
            props.setProperty("date", getCurrentShiftWindow().start.toLocalDate().toString());
            props.setProperty("banbury", String.valueOf(selectedBanbury));
            props.setProperty("tournee", getCurrentShiftWindow().tourneeId);

            java.util.List<Component> fields = collectPersistableComponents();
            props.setProperty("field.count", String.valueOf(fields.size()));
            for (int i = 0; i < fields.size(); i++) {
                Component c = fields.get(i);
                if (c instanceof JTextField) props.setProperty("field." + i, ((JTextField) c).getText() == null ? "" : ((JTextField) c).getText());
                else if (c instanceof JTextArea) props.setProperty("field." + i, ((JTextArea) c).getText() == null ? "" : ((JTextArea) c).getText());
                else if (c instanceof JComboBox) {
                    Object value = ((JComboBox<?>) c).getSelectedItem();
                    props.setProperty("field." + i, value == null ? "" : value.toString());
                } else if (c instanceof JCheckBox) {
                    props.setProperty("field." + i, Boolean.toString(((JCheckBox) c).isSelected()));
                }
            }
            saveTableModel(props, "production", productionModel);
            saveTableModel(props, "poids", poidsModel);
            savePesageTrackingForProduction(props);
            try (java.io.OutputStream out = new java.io.FileOutputStream(getCurrentShiftStateFile())) {
                props.store(out, "RappProdMel autosave " + getCurrentReportBaseName());
            }
        }

        private void loadCurrentShiftStateFromDisk() {
            File f = getCurrentShiftStateFile();
            if (f.exists() && getCurrentReportBaseName().equals(sentShiftKey)) sentShiftKey = null;
            final boolean hasSavedShiftState = f.exists();
            loadingShiftState = true;
            if (productionTable != null) {
                productionTable.putClientProperty("suspendProductionDbLookup", Boolean.TRUE);
                // V193.26: alla riapertura di un rapporto salvato non deve partire nessuna
                // ricerca DB sulle righe ricaricate. Le righe devono restare esattamente come
                // salvate; il controllo pesate DB si riabilita solo quando l'utente conferma
                // manualmente Nombre de Melanges su una riga.
                if (hasSavedShiftState) {
                    productionTable.putClientProperty("suppressProductionDbLookupUntilUserNombre", Boolean.TRUE);
                } else {
                    productionTable.putClientProperty("suppressProductionDbLookupUntilUserNombre", Boolean.FALSE);
                }
            }
            try {
                if (productionModel != null) {
                    productionModel.setRowCount(0);
                    for (int i = 0; i < 10; i++) productionModel.addRow(createEmptyRow(PRODUCTION_COL_COUNT));
                }
                if (poidsModel != null) {
                    poidsModel.setRowCount(0);
                    for (int i = 0; i < 4; i++) poidsModel.addRow(new Object[]{"", "", "", "", "", "", "", ""});
                }
                clearFieldsInContainer(this);
                if (!f.exists()) {
                    // V194.22: nessun salvataggio da ripristinare, quindi i valori di
                    // default Provenance/Destination della bilancia embedded vanno
                    // reimpostati qui (clearFieldsInContainer li ha appena svuotati).
                    forceEmbeddedBalanceBanburySelection();
                    if (txtHeuresTotales != null) txtHeuresTotales.setText("0H00");
                    updatePersonnelTotals();
                    refreshDechetsMelangeurFromDb();
                    relayoutGrowingTables();
                    return;
                }
                java.util.Properties props = new java.util.Properties();
                try (java.io.InputStream in = new java.io.FileInputStream(f)) {
                    props.load(in);
                }
                java.util.List<Component> fields = collectPersistableComponents();
                int count = parseIntQuiet(props.getProperty("field.count"), 0);
                for (int i = 0; i < Math.min(count, fields.size()); i++) {
                    String value = props.getProperty("field." + i, "");
                    Component c = fields.get(i);
                    if (c instanceof JTextField) ((JTextField) c).setText(value);
                    else if (c instanceof JTextArea) ((JTextArea) c).setText(value);
                    else if (c instanceof JComboBox) ((JComboBox<?>) c).setSelectedItem(value);
                    else if (c instanceof JCheckBox) setPersistedCheckBoxSelected((JCheckBox) c, Boolean.parseBoolean(value));
                }
                // V191.04: dopo aver ricaricato i campi salvati, riallinea LE CHEF SECTEUR
                // al nome del Responsable. Questo corregge eventuali vecchi autosave dove
                // nel campo era rimasto salvato il codice operatore.
                syncResponsableToChefSecteur();
                loadTableModel(props, "production", productionModel, 10, PRODUCTION_COL_COUNT);
                loadTableModel(props, "poids", poidsModel, 4, 8);
                restorePesageTrackingForProduction(props);
                repairPoidsRealiseSeriesFromProductionTable();
                compactPoidsRealiseBlankRows();
                updatePersonnelTotals();
                for (int r = 0; productionModel != null && r < productionModel.getRowCount(); r++) updateProductionDuration(productionModel, r);
                refreshArretSummaryFromProductionTable();
                // V193.25: alla riapertura NON deve partire nessun ricalcolo automatico.
                // Il salvataggio deve ripristinare direttamente tutte le righe e tutti i Poids salvati.
                if (lblPoidsStatus != null) {
                    lblPoidsStatus.setText("V9.3 Poids charge depuis sauvegarde");
                }
                refreshDechetsMelangeurFromDb();
                relayoutGrowingTables();
            } catch (Exception ex) {
                System.err.println("Erreur chargement persistance rapport: " + ex.getMessage());
            } finally {
                loadingShiftState = false;
                // V193.25: lascia sospeso il lookup DB ancora per un piccolo intervallo,
                // per assorbire gli eventi JTable generati dal caricamento del file.
                if (productionTable != null) {
                    Timer resumeLookupTimer = new Timer(600, ev -> {
                        productionTable.putClientProperty("suspendProductionDbLookup", Boolean.FALSE);
                    });
                    resumeLookupTimer.setRepeats(false);
                    resumeLookupTimer.start();
                }
                // Solo se non esiste nessun salvataggio si puo inizializzare Poids realise.
                if (!hasSavedShiftState && isPoidsModelEffectivelyEmpty()) {
                    SwingUtilities.invokeLater(() -> refreshPoidsRealiseFromProductionTable());
                }
            }
        }

        private java.util.List<Component> collectPersistableComponents() {
            java.util.List<Component> result = new ArrayList<>();
            collectPersistableComponents(this, result);
            return result;
        }

        private boolean isPersistableComponent(Component c) {
            return c instanceof JTextField
                    || c instanceof JTextArea
                    || c instanceof JComboBox
                    || c instanceof JCheckBox;
        }

        private void addPersistableComponent(Component c, java.util.List<Component> result) {
            if (c == null || c == txtChefSecteurName) {
                return;
            }
            // V191.04: campo derivato dal Responsable; non salvarlo per evitare
            // che vecchi valori/codici operatore restino memorizzati negli autosave.
            if (isPersistableComponent(c)) {
                result.add(c);
            }
        }

        private void collectPersistableComponents(Container container, java.util.List<Component> result) {
            if (container == null) return;
            for (Component c : container.getComponents()) {
                if (c == txtChefSecteurName) {
                    continue;
                }

                // V194.16: salva anche JCheckBox e i campi dentro JScrollPane.
                // Prima i JTextArea delle sezioni REMARQUES / PREPARATIONS non venivano
                // raccolti perche il metodo entrava nel JTextArea come Container senza
                // aggiungere il JTextArea stesso alla lista dei campi persistenti.
                addPersistableComponent(c, result);

                if (c instanceof JComboBox) {
                    continue;
                }

                if (c instanceof JScrollPane) {
                    JViewport vp = ((JScrollPane) c).getViewport();
                    Component view = vp == null ? null : vp.getView();
                    addPersistableComponent(view, result);
                    if (view instanceof Container && !isPersistableComponent(view)) {
                        collectPersistableComponents((Container) view, result);
                    }
                } else if (c instanceof Container && !isPersistableComponent(c)) {
                    collectPersistableComponents((Container) c, result);
                }
            }
        }

        private void setPersistedCheckBoxSelected(JCheckBox cb, boolean selected) {
            if (cb == null) return;
            setChoiceSelected(cb, selected);
        }

        private void saveTableModel(java.util.Properties props, String prefix, DefaultTableModel model) {
            if (model == null) return;

            // V193.24: salvare SEMPRE tutte le righe e tutte le colonne della Production realisee.
            // Non compattare piu la tabella: il ripristino deve riportare esattamente gli stessi
            // valori, inclusa la colonna Poids e le righe vuote/intermedie.
            props.setProperty(prefix + ".rows", String.valueOf(model.getRowCount()));
            props.setProperty(prefix + ".cols", String.valueOf(model.getColumnCount()));
            for (int r = 0; r < model.getRowCount(); r++) {
                for (int c = 0; c < model.getColumnCount(); c++) {
                    Object value = model.getValueAt(r, c);
                    props.setProperty(prefix + "." + r + "." + c, value == null ? "" : value.toString());
                }
            }
        }

        // V194.19: gli ID_PESAGE gia' usati da ogni riga (client property "usedPesageIds:<row>"
        // sulla JTable) vivono solo in memoria. Senza persisterli, chiudendo e riaprendo il
        // rapporto la finestra "Controllo pesate DB prima della somma" non sa piu' quali pesate
        // sono gia' state consumate dalle righe ricaricate e le ripropone per una riga nuova
        // sullo stesso Article/Lot.
        private void savePesageTrackingForProduction(java.util.Properties props) {
            if (productionTable == null || productionModel == null) return;

            for (int r = 0; r < productionModel.getRowCount(); r++) {
                @SuppressWarnings("unchecked")
                java.util.Set<String> ids = (java.util.Set<String>) productionTable.getClientProperty("usedPesageIds:" + r);
                if (ids == null || ids.isEmpty()) continue;

                Object signatureObj = productionTable.getClientProperty("usedPesageRowSignature:" + r);
                props.setProperty("production.pesage." + r + ".ids", String.join(",", ids));
                props.setProperty("production.pesage." + r + ".signature", signatureObj == null ? "" : signatureObj.toString());
            }
        }

        private void restorePesageTrackingForProduction(java.util.Properties props) {
            if (productionTable == null || productionModel == null) return;

            for (int r = 0; r < productionModel.getRowCount(); r++) {
                String idsProperty = props.getProperty("production.pesage." + r + ".ids");
                if (idsProperty == null || idsProperty.trim().isEmpty()) continue;

                java.util.Set<String> ids = new java.util.LinkedHashSet<>();
                for (String id : idsProperty.split(",")) {
                    if (!id.trim().isEmpty()) ids.add(id.trim());
                }
                if (ids.isEmpty()) continue;

                String signature = props.getProperty("production.pesage." + r + ".signature", "");
                productionTable.putClientProperty("usedPesageIds:" + r, ids);
                productionTable.putClientProperty("usedPesageRowSignature:" + r, signature);
            }
        }

        private void saveCompactedTableModel(java.util.Properties props, String prefix, DefaultTableModel model) {
            java.util.List<Object[]> usedRows = new ArrayList<>();
            for (int r = 0; r < model.getRowCount(); r++) {
                if (isGenericRowEmpty(model, r)) {
                    continue;
                }
                Object[] row = new Object[model.getColumnCount()];
                for (int c = 0; c < row.length; c++) {
                    Object value = model.getValueAt(r, c);
                    row[c] = value == null ? "" : value.toString();
                }
                usedRows.add(row);
            }

            props.setProperty(prefix + ".rows", String.valueOf(usedRows.size()));
            props.setProperty(prefix + ".cols", String.valueOf(model.getColumnCount()));
            for (int r = 0; r < usedRows.size(); r++) {
                Object[] row = usedRows.get(r);
                for (int c = 0; c < row.length; c++) {
                    props.setProperty(prefix + "." + r + "." + c, row[c] == null ? "" : row[c].toString());
                }
            }
        }

        private void loadTableModel(java.util.Properties props, String prefix, DefaultTableModel model, int minRows, int expectedCols) {
            if (model == null) return;
            int rows = Math.max(minRows, parseIntQuiet(props.getProperty(prefix + ".rows"), minRows));
            int savedCols = parseIntQuiet(props.getProperty(prefix + ".cols"), expectedCols);
            int cols = Math.min(model.getColumnCount(), Math.max(expectedCols, savedCols));
            model.setRowCount(0);
            for (int r = 0; r < rows; r++) {
                Object[] row = new Object[model.getColumnCount()];
                for (int c = 0; c < row.length; c++) row[c] = c < cols ? props.getProperty(prefix + "." + r + "." + c, "") : "";

                // V193.16: migrazione POIDS REALISE dopo aggiunta colonna N° Série.
                // Vecchi salvataggi a 7 colonne: Couleur/Palette/Total devono scorrere di una colonna a destra.
                // Salvataggi V193.15 gia spostati male: se Couleur contiene in realta Palette N°, corregge la riga.
                if ("production".equals(prefix)) {
                    row = migrateProductionSavedRow(row, savedCols);
                } else if ("poids".equals(prefix)) {
                    row = migratePoidsSavedRow(row, savedCols);
                }

                model.addRow(row);
            }

            // V193.24: non compattare piu Production al caricamento. Ripristinare righe/colonne cosi come salvate.
        }

        private Object[] migrateProductionSavedRow(Object[] row, int savedCols) {
            if (row == null) return row;
            Object[] fixed = new Object[Math.max(PRODUCTION_COL_COUNT, row.length)];
            for (int i = 0; i < fixed.length; i++) fixed[i] = i < row.length && row[i] != null ? row[i].toString() : "";

            if (isGenericArrayEmpty(fixed)) return fixed;

            String oldCoucheCandidate = valueFromArray(fixed, 4).trim().toUpperCase(Locale.ROOT);
            String oldPassageCandidate = valueFromArray(fixed, 5).trim().toUpperCase(Locale.ROOT);
            boolean looksOldLayout = "SUP".equals(oldCoucheCandidate)
                    || "INF".equals(oldCoucheCandidate)
                    || (!oldCoucheCandidate.matches("[0-9]*") && !oldCoucheCandidate.isEmpty())
                    || oldPassageCandidate.matches(".*(1|2|3|4|ERE|EME).*");

            // Versioni fino a V194.04: colonna 4=Couche, 5=Passage, 6=Poids, ... 14=ZBEC-SUP, 15=RETARD.
            // Versione V194.05: colonna 4=Repass, colonna 15=ZBEC-SUP ou RETARD.
            if (looksOldLayout) {
                Object[] migrated = new Object[PRODUCTION_COL_COUNT];
                for (int i = 0; i < migrated.length; i++) migrated[i] = "";
                migrated[0] = valueFromArray(fixed, 0);
                migrated[1] = valueFromArray(fixed, 1);
                migrated[2] = valueFromArray(fixed, 2);
                migrated[3] = valueFromArray(fixed, 3);
                migrated[4] = "";
                for (int oldCol = 4; oldCol <= 14; oldCol++) {
                    migrated[oldCol + 1] = valueFromArray(fixed, oldCol);
                }
                migrated[15] = combineZbecSupRetard(valueFromArray(fixed, 14), valueFromArray(fixed, 15));
                return migrated;
            }

            return fixed;
        }

        private Object[] migratePoidsSavedRow(Object[] row, int savedCols) {
            if (row == null) return row;
            Object[] fixed = new Object[Math.max(8, row.length)];
            for (int i = 0; i < fixed.length; i++) fixed[i] = i < row.length && row[i] != null ? row[i].toString() : "";

            if (isGenericArrayEmpty(fixed)) {
                return fixed;
            }

            if (savedCols <= 7) {
                // Layout precedente: 0 Produit, 1 Couche, 2 Lot, 3 Couleur, 4 Palette N°, 5 Palette Poids, 6 Total.
                fixed[7] = valueFromArray(row, 6);
                fixed[6] = valueFromArray(row, 5);
                fixed[5] = valueFromArray(row, 4);
                fixed[4] = valueFromArray(row, 3);
                fixed[3] = "";
                return fixed;
            }

            String c4 = valueFromArray(fixed, 4);
            String c5 = valueFromArray(fixed, 5);
            String c6 = valueFromArray(fixed, 6);
            String c7 = valueFromArray(fixed, 7);

            // Caso mostrato in V193.15: Palette N° finita in Couleur, Palette Poids in Palette N°,
            // Total in Palette Poids, e colonna Total vuota.
            if (looksLikePaletteNumbers(c4) && !c5.trim().isEmpty() && !c6.trim().isEmpty() && c7.trim().isEmpty()) {
                fixed[7] = c6;
                fixed[6] = c5;
                fixed[5] = c4;
                fixed[4] = "";
            }
            return fixed;
        }

        private String valueFromArray(Object[] row, int index) {
            if (row == null || index < 0 || index >= row.length || row[index] == null) return "";
            return row[index].toString();
        }

        private boolean isGenericArrayEmpty(Object[] row) {
            if (row == null) return true;
            for (Object v : row) {
                if (v != null && !v.toString().trim().isEmpty()) return false;
            }
            return true;
        }

        private boolean looksLikePaletteNumbers(String value) {
            if (value == null) return false;
            String t = value.trim();
            return t.matches("[0-9]+(?:[-/][0-9]+)*");
        }

        private void compactLoadedTableModel(DefaultTableModel model, int minRows) {
            if (model == null) return;

            java.util.List<Object[]> usedRows = new ArrayList<>();
            for (int r = 0; r < model.getRowCount(); r++) {
                if (isGenericRowEmpty(model, r)) {
                    continue;
                }
                Object[] row = new Object[model.getColumnCount()];
                for (int c = 0; c < row.length; c++) {
                    Object value = model.getValueAt(r, c);
                    row[c] = value == null ? "" : value.toString();
                }
                usedRows.add(row);
            }

            int targetRows = usedRows.isEmpty() ? minRows : Math.max(minRows, usedRows.size() + 1);
            model.setRowCount(0);
            for (Object[] row : usedRows) {
                model.addRow(row);
            }
            while (model.getRowCount() < targetRows) {
                model.addRow(createEmptyRow(model.getColumnCount()));
            }
        }

        private Object[] createEmptyRow(int columnCount) {
            Object[] row = new Object[columnCount];
            for (int i = 0; i < row.length; i++) row[i] = "";
            return row;
        }

        private int parseIntQuiet(String value, int fallback) {
            try { return Integer.parseInt(value == null ? "" : value.trim()); } catch (Exception ex) { return fallback; }
        }


        private String validateXmlForDebug(File xmlFile) {
            if (xmlFile == null || !xmlFile.exists()) return "XML: fichier absent";
            try {
                String content = new String(java.nio.file.Files.readAllBytes(xmlFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                String t = content.trim();
                if (t.isEmpty()) return "XML: fichier vide";
                if (!t.startsWith("<?xml") && !t.startsWith("<jasperReport")) {
                    return "XML: debut inattendu: " + t.substring(0, Math.min(80, t.length())).replace('\n', ' ').replace('\r', ' ');
                }
                if (!content.contains("<jasperReport")) return "XML: balise <jasperReport> non trouvee";
                if (!content.contains("</jasperReport>")) return "XML: balise </jasperReport> non trouvee";
                return "XML controle simple: OK";
            } catch (Exception ex) {
                return "XML controle simple: ERREUR - " + ex.getClass().getSimpleName() + " - " + ex.getMessage();
            }
        }

        private String analyzeJrxmlForDebug(File jrxmlFile) {
            StringBuilder sb = new StringBuilder();
            try {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(jrxmlFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                sb.append("Nombre de lignes: ").append(lines.size()).append("\n");
                int titleCount = 0, pageHeaderCount = 0, summaryCount = 0, detailCount = 0, parameterCount = 0;
                for (int i = 0; i < lines.size(); i++) {
                    String s = lines.get(i);
                    if (s.contains("<parameter ")) parameterCount++;
                    if (s.contains("<title")) { titleCount++; sb.append("title ligne: ").append(i + 1).append("\n"); }
                    if (s.contains("<pageHeader")) { pageHeaderCount++; sb.append("pageHeader ligne: ").append(i + 1).append("\n"); }
                    if (s.contains("<detail")) { detailCount++; sb.append("detail ligne: ").append(i + 1).append("\n"); }
                    if (s.contains("<summary")) { summaryCount++; sb.append("summary ligne: ").append(i + 1).append("\n"); }
                }
                sb.append("Parametres declares: ").append(parameterCount).append("\n");
                sb.append("Bandes: title=").append(titleCount)
                        .append(", pageHeader=").append(pageHeaderCount)
                        .append(", detail=").append(detailCount)
                        .append(", summary=").append(summaryCount).append("\n");
                sb.append("Premiere ligne: ").append(lines.isEmpty() ? "" : lines.get(0)).append("\n");
                if (lines.size() > 1) sb.append("Racine: ").append(lines.get(1)).append("\n");
            } catch (Exception ex) {
                sb.append("Analyse impossible: ").append(ex.getClass().getSimpleName()).append(" - ").append(ex.getMessage()).append("\n");
            }
            return sb.toString();
        }

        private String describeReportFile(String label, File file) {
            if (file == null) return label + ": fichier null";
            StringBuilder sb = new StringBuilder();
            sb.append(label).append(": ").append(file.getAbsolutePath());
            sb.append(" | exists=").append(file.exists());
            sb.append(" | isFile=").append(file.isFile());
            sb.append(" | canRead=").append(file.canRead());
            sb.append(" | length=").append(file.exists() ? file.length() : 0);
            if (file.exists()) {
                try {
                    sb.append(" | modified=").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(file.lastModified())));
                } catch (Exception ignored) {
                }
            }
            return sb.toString();
        }

        private String writePrintErrorLog(File preferredLogFile, String details) {
            File target = preferredLogFile;
            try {
                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try (java.io.PrintWriter out = new java.io.PrintWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(target, false), java.nio.charset.StandardCharsets.UTF_8))) {
                    out.print(details);
                }
                return target.getAbsolutePath();
            } catch (Exception firstError) {
                try {
                    target = new File(System.getProperty("java.io.tmpdir"), "RappProdMel_print_error.log");
                    try (java.io.PrintWriter out = new java.io.PrintWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(target, false), java.nio.charset.StandardCharsets.UTF_8))) {
                        out.print(details);
                        out.print("\n\nErreur sauvegarde log principal: ");
                        out.print(firstError.toString());
                    }
                    return target.getAbsolutePath();
                } catch (Exception secondError) {
                    return "Impossible de sauvegarder le log: " + secondError.getMessage();
                }
            }
        }

        private String getStackTraceText(Throwable ex) {
            if (ex == null) return "";
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            ex.printStackTrace(pw);
            pw.flush();
            return sw.toString();
        }


        private String getFullErrorMessageDeep(Throwable ex) {
            if (ex == null) return "";
            StringBuilder sb = new StringBuilder();
            java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
            appendThrowableDeep(sb, ex, "", seen, 0);
            return sb.toString();
        }

        private void appendThrowableDeep(StringBuilder sb, Throwable t, String prefix, java.util.Set<Throwable> seen, int level) {
            if (t == null || level > 12 || seen.contains(t)) return;
            seen.add(t);
            if (sb.length() > 0) sb.append("\n");
            sb.append(prefix).append(t.getClass().getName());
            if (t.getMessage() != null && !t.getMessage().trim().isEmpty()) sb.append(" - ").append(t.getMessage());

            try {
                java.lang.reflect.Method m = t.getClass().getMethod("getNestedThrowable");
                Object nested = m.invoke(t);
                if (nested instanceof Throwable && nested != t) {
                    appendThrowableDeep(sb, (Throwable) nested, prefix + "Nested: ", seen, level + 1);
                }
            } catch (Exception ignored) {
            }

            for (Throwable s : t.getSuppressed()) {
                appendThrowableDeep(sb, s, prefix + "Suppressed: ", seen, level + 1);
            }
            appendThrowableDeep(sb, t.getCause(), prefix + "Cause: ", seen, level + 1);
        }

        private String getFullErrorMessage(Throwable ex) {
            if (ex == null) return "";
            StringBuilder sb = new StringBuilder();
            Throwable t = ex;
            int level = 0;
            while (t != null && level < 6) {
                if (level > 0) sb.append("\nCause: ");
                sb.append(t.getClass().getSimpleName());
                if (t.getMessage() != null && !t.getMessage().trim().isEmpty()) {
                    sb.append(" - ").append(t.getMessage());
                }
                t = t.getCause();
                level++;
            }
            return sb.toString();
        }

        private java.util.Map<String, Object> buildJasperParameters() {
            java.util.Map<String, Object> p = new java.util.HashMap<>();
            p.put("VERSION", KG_VERSION);
            p.put("JOURNEE", java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            p.put("EQUIPE", lblEquipeValue == null ? "" : lblEquipeValue.getText());
            p.put("BANBURY", String.valueOf(selectedBanbury));
            p.put("HEURES_TOTALES", txtHeuresTotales == null ? "" : txtHeuresTotales.getText());
            p.put("DECHETS_MELANGEUR", txtDechetsMelangeur == null ? "" : txtDechetsMelangeur.getText());
            p.put("CONSOMMATION_HUILE", txtConsommationHuile == null ? "" : txtConsommationHuile.getText());
            p.put("REMARQUES", txtRemarquesReport == null ? "" : txtRemarquesReport.getText());
            p.put("PREPARATIONS", txtPreparationsReport == null ? "" : txtPreparationsReport.getText());
            p.put("CHEF_SECTEUR", txtChefSecteurName == null ? "" : txtChefSecteurName.getText().trim());
            p.put("ARRET_INFO", lblArretProductionInfo == null ? "" : lblArretProductionInfo.getText());
            p.put("TEMPS_ARRET", txtTempsArretAuto == null ? "" : txtTempsArretAuto.getText());

            for (int r = 0; r < 8; r++) {
                PersonnelReportRow row = r < personnelReportRows.size() ? personnelReportRows.get(r) : null;
                p.put("PERSONNEL_" + r + "_ROLE", row == null ? "" : componentText(row.roleComponent));
                p.put("PERSONNEL_" + r + "_CODE", row == null || row.code == null ? "" : row.code.getText());
                p.put("PERSONNEL_" + r + "_NAME", row == null || row.name == null ? "" : row.name.getText());
                p.put("PERSONNEL_" + r + "_DE", row == null || row.debut == null ? "" : row.debut.getText());
                p.put("PERSONNEL_" + r + "_A", row == null || row.fin == null ? "" : row.fin.getText());
                p.put("PERSONNEL_" + r + "_TOTAL", row == null || row.total == null ? "" : row.total.getText());
            }

            for (int r = 0; r < 30; r++) {
                for (int c = 0; c < PRODUCTION_COL_COUNT; c++) {
                    p.put("P" + r + "_" + c, productionModel != null && r < productionModel.getRowCount() ? valueAt(productionModel, r, c) : "");
                }
            }

            if (productionTotalPanel != null) {
                for (Component component : productionTotalPanel.getComponents()) {
                    if (component instanceof JTextField) {
                        JTextField field = (JTextField) component;
                        int x = field.getX();
                        if (x >= 450 && x < 570) p.put("TOTAL_MELANGES", field.getText());
                        else if (x >= 690 && x < 850) p.put("TOTAL_KG", field.getText());
                        else if (x >= 1160) p.put("TOTAL_MINUTES", field.getText());
                    }
                }
            }
            if (!p.containsKey("TOTAL_MELANGES")) p.put("TOTAL_MELANGES", "");
            if (!p.containsKey("TOTAL_KG")) p.put("TOTAL_KG", "");
            if (!p.containsKey("TOTAL_MINUTES")) p.put("TOTAL_MINUTES", "");

            for (int r = 0; r < 10; r++) {
                for (int c = 0; c < 8; c++) {
                    p.put("W" + r + "_" + c, poidsModel != null && r < poidsModel.getRowCount() ? valueAt(poidsModel, r, c) : "");
                }
            }
            return p;
        }

        public void resetManualShiftAfterPrint() {
            manualTourneeId = null;
            manualShiftExpiresAt = null;
            updateShiftLabel();
            refreshProductionPassagesIfPossible();
        }

        private void buildPersonnel() {
            addLabel("Personnel", 35, 183, 160, 24, FONT_BOLD, SwingConstants.LEFT);

            String[][] rows = {
                    {"Responsable", "", ""},
                    {"Pupitre", "", ""},
                    {"Opérateur Banbury", "", ""},
                    {"Préparateur caoutchoucs", "", ""},
                    {"Aide préparateur", "", ""}
            };

            int y = 218;
            final int rowH = PERSONNEL_ROW_H;
            final int rowStep = PERSONNEL_ROW_H + PERSONNEL_ROW_GAP;
            for (String[] r : rows) {
                addLabel(r[0], 35, y, 250, rowH, FONT_NORMAL, SwingConstants.LEFT);
                addLabel(":", 375, y, 15, rowH, FONT_NORMAL, SwingConstants.CENTER);

                JTextField txtOperateurCode = createOperateurCodeField(r[2]);
                txtOperateurCode.setBounds(395, y, 45, rowH);
                add(txtOperateurCode);

                JTextField txtOperateurName = createOperateurNameField(r[1]);
                txtOperateurName.setBounds(445, y, 300, rowH);
                add(txtOperateurName);

                installOperateurLookup(txtOperateurCode, txtOperateurName);
                installBalancePersonnelDoubleClick(txtOperateurCode, txtOperateurName);

                if ("Responsable".equalsIgnoreCase(r[0])) {
                    txtResponsableCode = txtOperateurCode;
                    txtResponsableName = txtOperateurName;
                    installResponsableChefSecteurSync();
                }

                registerPersonnelFocusField(txtOperateurCode);

                addLabel("de", 750, y, 35, rowH, FONT_NORMAL, SwingConstants.CENTER);
                JTextField txtDebut = createTimeField();
                txtDebut.setBounds(790, y, 75, rowH);
                add(txtDebut);
                registerPersonnelFocusField(txtDebut);
                addLabel("à", 875, y, 25, rowH, FONT_NORMAL, SwingConstants.CENTER);
                JTextField txtFin = createTimeField();
                txtFin.setBounds(910, y, 75, rowH);
                add(txtFin);
                registerPersonnelFocusField(txtFin);
                addLabel(":", 1015, y, 15, rowH, FONT_NORMAL, SwingConstants.CENTER);
                JTextField txtTotalLigne = createTotalField();
                txtTotalLigne.setBounds(1035, y, 75, rowH);
                add(txtTotalLigne);
                registerPersonnelTimeRow(addHiddenPersonnelRole(r[0]), txtOperateurCode, txtOperateurName, txtDebut, txtFin, txtTotalLigne);
                installPersonnelShiftTimeAutoFill(txtOperateurCode, txtOperateurName, txtDebut, txtFin);
                y += rowStep;
            }

         
            for (int i = 0; i < 3; i++) {
                JComboBox<String> cbRuoloExtra = createPersonnelRoleCombo();
                cbRuoloExtra.setBounds(35, y, 290, rowH);
                add(cbRuoloExtra);
                registerPersonnelFocusField(cbRuoloExtra);

                addLabel(":", 375, y, 15, rowH, FONT_NORMAL, SwingConstants.CENTER);

                JTextField txtOperateurCodeExtra = createOperateurCodeField("");
                txtOperateurCodeExtra.setBounds(395, y, 45, rowH);
                add(txtOperateurCodeExtra);

                JTextField txtOperateurNameExtra = createOperateurNameField("");
                txtOperateurNameExtra.setBounds(445, y, 300, rowH);
                add(txtOperateurNameExtra);

                installOperateurLookup(txtOperateurCodeExtra, txtOperateurNameExtra);
                installBalancePersonnelDoubleClick(txtOperateurCodeExtra, txtOperateurNameExtra);
                registerPersonnelFocusField(txtOperateurCodeExtra);

                addLabel("de", 750, y, 35, rowH, FONT_NORMAL, SwingConstants.CENTER);
                JTextField txtDebut = createTimeField();
                txtDebut.setBounds(790, y, 75, rowH);
                add(txtDebut);
                registerPersonnelFocusField(txtDebut);
                addLabel("à", 875, y, 25, rowH, FONT_NORMAL, SwingConstants.CENTER);
                JTextField txtFin = createTimeField();
                txtFin.setBounds(910, y, 75, rowH);
                add(txtFin);
                registerPersonnelFocusField(txtFin);
                addLabel(":", 1015, y, 15, rowH, FONT_NORMAL, SwingConstants.CENTER);
                JTextField txtTotalLigne = createTotalField();
                txtTotalLigne.setBounds(1035, y, 75, rowH);
                add(txtTotalLigne);
                registerPersonnelTimeRow(cbRuoloExtra, txtOperateurCodeExtra, txtOperateurNameExtra, txtDebut, txtFin, txtTotalLigne);
                installPersonnelShiftTimeAutoFill(txtOperateurCodeExtra, txtOperateurNameExtra, txtDebut, txtFin);
                y += rowStep;
            }

            int totalsY = y - 105;
            addLabel("Heures totales", 1180, totalsY, 160, 22, FONT_NORMAL, SwingConstants.CENTER);
            txtHeuresTotales = createTotalField();
            txtHeuresTotales.setBounds(1225, totalsY + 28, 70, 26);
            txtHeuresTotales.setBorder(new LineBorder(new Color(45,45,45), 2));
            add(txtHeuresTotales);
            updatePersonnelTotals();
            addLabel("Production réalisée", 5, 450 + PRODUCTION_SHIFT_Y, 210, 22, FONT_BOLD, SwingConstants.LEFT);
        }


        private void installPersonnelShiftTimeAutoFill(JTextField codeField, JTextField nameField,
                                                       JTextField debutField, JTextField finField) {
            if (debutField == null || finField == null) {
                return;
            }

            // V134:
            // Non avvia piu la ricerca ne il riempimento orari mentre l'operatore sta digitando.
            // Gli orari vengono proposti solo dopo conferma del codice con TAB o INVIO,
            // quando loadOperateurName(...) ha trovato/valorizzato il nome.
            if (codeField != null) {
                codeField.putClientProperty("personnelDebutField", debutField);
                codeField.putClientProperty("personnelFinField", finField);
                codeField.putClientProperty("personnelNameField", nameField);
                codeField.putClientProperty("personnelOperatorCodeField", Boolean.TRUE);
            }

            if (nameField != null) {
                nameField.putClientProperty("personnelDebutField", debutField);
                nameField.putClientProperty("personnelFinField", finField);
                nameField.putClientProperty("personnelCodeField", codeField);
            }
        }

        private void autoFillPersonnelShiftTimeFromLookup(JTextField codeField, JTextField nameField) {
            JTextField debutField = null;
            JTextField finField = null;

            Object debutObj = codeField == null ? null : codeField.getClientProperty("personnelDebutField");
            Object finObj = codeField == null ? null : codeField.getClientProperty("personnelFinField");

            if (debutObj instanceof JTextField) {
                debutField = (JTextField) debutObj;
            }
            if (finObj instanceof JTextField) {
                finField = (JTextField) finObj;
            }

            if (debutField == null && nameField != null && nameField.getClientProperty("personnelDebutField") instanceof JTextField) {
                debutField = (JTextField) nameField.getClientProperty("personnelDebutField");
            }
            if (finField == null && nameField != null && nameField.getClientProperty("personnelFinField") instanceof JTextField) {
                finField = (JTextField) nameField.getClientProperty("personnelFinField");
            }

            if (debutField == null || finField == null) {
                return;
            }

            autoFillPersonnelShiftTimeIfNeeded(codeField, nameField, debutField, finField);
            moveFocusToNextPersonnelOperatorCode(codeField);
        }

        private void autoFillPersonnelShiftTimeIfNeeded(JTextField codeField, JTextField nameField,
                                                        JTextField debutField, JTextField finField) {
            if (resettingForm || debutField == null || finField == null) {
                return;
            }

            String code = codeField == null || codeField.getText() == null ? "" : codeField.getText().trim();
            String name = nameField == null || nameField.getText() == null ? "" : nameField.getText().trim();

            if (code.isEmpty() && name.isEmpty()) {
                return;
            }

            String debut = debutField.getText() == null ? "" : debutField.getText().trim();
            String fin = finField.getText() == null ? "" : finField.getText().trim();

            // Non sovrascrive mai un orario gia inserito/modificato dall'operatore.
            if (!debut.isEmpty() || !fin.isEmpty()) {
                return;
            }

            CurrentShiftWindow shift = getCurrentShiftWindow();
            if (shift == null) {
                return;
            }

            if ("1".equals(shift.tourneeId)) {
                debutField.setText("6H00");
                finField.setText("14H00");
            } else if ("2".equals(shift.tourneeId)) {
                debutField.setText("14H00");
                finField.setText("22H00");
            } else {
                debutField.setText("22H00");
                finField.setText("6H00");
            }

            updatePersonnelTotals();
        }

        private void moveFocusToNextPersonnelOperatorCode(Component current) {
            if (current == null || personnelFocusOrder.isEmpty()) {
                return;
            }

            int index = personnelFocusOrder.indexOf(current);
            if (index < 0) {
                return;
            }

            for (int i = index + 1; i < personnelFocusOrder.size(); i++) {
                Component next = personnelFocusOrder.get(i);
                if (next instanceof JTextField
                        && next.isEnabled()
                        && next.isVisible()
                        && next.isFocusable()
                        && Boolean.TRUE.equals(((JTextField) next).getClientProperty("personnelOperatorCodeField"))) {
                    SwingUtilities.invokeLater(() -> {
                        next.requestFocusInWindow();
                        if (next instanceof JTextField) {
                            ((JTextField) next).selectAll();
                        }
                    });
                    return;
                }
            }
        }


        private void installResponsableChefSecteurSync() {
            if (txtResponsableCode == null || txtResponsableName == null) {
                return;
            }

            DocumentListener listener = new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { syncResponsableToChefSecteur(); }
                @Override public void removeUpdate(DocumentEvent e) { syncResponsableToChefSecteur(); }
                @Override public void changedUpdate(DocumentEvent e) { syncResponsableToChefSecteur(); }
            };

            txtResponsableCode.getDocument().addDocumentListener(listener);
            txtResponsableName.getDocument().addDocumentListener(listener);
            syncResponsableToChefSecteur();
        }

        private void syncResponsableToChefSecteur() {
            if (txtChefSecteurName == null) {
                return;
            }

            String nomResponsable = txtResponsableName == null || txtResponsableName.getText() == null
                    ? ""
                    : txtResponsableName.getText().trim();

            // V191.04: il campo LE CHEF SECTEUR deve mostrare sempre il NOME,
            // mai il codice operatore. Se il nome non e ancora valorizzato ma il codice
            // Responsable e presente, prova a risolvere subito il nome tramite DB.
            if (nomResponsable.isEmpty() && txtResponsableCode != null && txtResponsableCode.getText() != null) {
                String code = txtResponsableCode.getText().trim().toUpperCase(Locale.ROOT);
                if (!code.isEmpty()) {
                    try {
                        String nomDb = DbOracleManager.findUserNameByCode(code);
                        if (nomDb != null && !nomDb.trim().isEmpty()) {
                            nomResponsable = nomDb.trim();
                            if (txtResponsableName != null) {
                                txtResponsableName.setText(nomResponsable);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            txtChefSecteurName.setText(nomResponsable);
        }


        private void registerPersonnelFocusField(Component component) {
            if (component == null) {
                return;
            }

            personnelFocusOrder.add(component);

            if (component instanceof JTextField) {
                JTextField field = (JTextField) component;
                field.addActionListener(e -> {
                    if (field.isEditable()) {
                        // V134: per il codice operatore Personnel, TAB/INVIO devono prima fare la ricerca.
                        // Il focus alla riga successiva viene gestito dopo il lookup e il riempimento orari.
                        if (Boolean.TRUE.equals(field.getClientProperty("personnelOperatorCodeField"))) {
                            return;
                        }
                        normalizeTimeFieldIfNeeded(field);
                        moveToNextPersonnelField(field);
                    }
                });
            } else if (component instanceof JComboBox) {
                JComboBox<?> combo = (JComboBox<?>) component;
                combo.addActionListener(e -> {
                    if (combo.isPopupVisible()) {
                        return;
                    }
                    moveToNextPersonnelField(combo);
                });

                Component editor = combo.getEditor() != null ? combo.getEditor().getEditorComponent() : null;
                if (editor instanceof JTextField) {
                    JTextField editorField = (JTextField) editor;
                    editorField.addActionListener(e -> moveToNextPersonnelField(combo));
                }
            }
        }

        private void normalizeTimeFieldIfNeeded(JTextField field) {
            if (field == null) {
                return;
            }

           
            
            if (!Boolean.TRUE.equals(field.getClientProperty("personnelTimeField"))) {
                return;
            }

            String text = field.getText();
            Integer minutes = parseFrenchTime(text);
            if (minutes != null) {
                field.setText(formatFrenchTime(minutes));
                updatePersonnelTotals();
            }
        }

        private void moveToNextPersonnelField(Component current) {
            if (current == null || personnelFocusOrder.isEmpty()) {
                return;
            }

            int index = personnelFocusOrder.indexOf(current);
            if (index < 0) {
                return;
            }

            for (int i = index + 1; i < personnelFocusOrder.size(); i++) {
                Component next = personnelFocusOrder.get(i);
                if (next != null && next.isEnabled() && next.isVisible() && next.isFocusable()) {
                    SwingUtilities.invokeLater(() -> {
                        next.requestFocusInWindow();

                        if (next instanceof JTextField) {
                            ((JTextField) next).selectAll();
                        } else if (next instanceof JComboBox) {
                            Component editor = ((JComboBox<?>) next).getEditor() != null
                                    ? ((JComboBox<?>) next).getEditor().getEditorComponent()
                                    : null;
                            if (editor != null) {
                                editor.requestFocusInWindow();
                                if (editor instanceof JTextField) {
                                    ((JTextField) editor).selectAll();
                                }
                            }
                        }
                    });
                    return;
                }
            }
        }

        private JComboBox<String> createPersonnelRoleCombo() {
            JComboBox<String> combo = new JComboBox<>(getPersonnelRoleValues());
            combo.setFont(FONT_NORMAL);
            combo.setEditable(true);
            combo.setSelectedItem("");
            combo.setBackground(Color.WHITE);
            combo.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
            combo.setMaximumRowCount(9);
            combo.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                              boolean isSelected, boolean cellHasFocus) {
                    JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    lbl.setPreferredSize(new Dimension(lbl.getPreferredSize().width, PERSONNEL_ROW_H));
                    lbl.setVerticalAlignment(SwingConstants.CENTER);
                    return lbl;
                }
            });
            Component editor = combo.getEditor().getEditorComponent();
            if (editor instanceof JTextField) {
                ((JTextField) editor).setMargin(new Insets(0, 4, 0, 4));
                ((JTextField) editor).setFont(FONT_NORMAL);
            }
            combo.setToolTipText("Sélectionner un rôle ou écrire manuellement");
            return combo;
        }

        private JTextField createOperateurCodeField(String text) {
            JTextField field = new JTextField(text);
            field.setFont(FONT_NORMAL);
            field.setHorizontalAlignment(SwingConstants.CENTER);
            field.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
            field.setBackground(Color.WHITE);
            field.setMargin(new Insets(0, 2, 0, 2));
            field.setToolTipText("Code opérateur: Entrée ou Tab pour charger le nom");
            return field;
        }

        private JTextField createOperateurNameField(String text) {
            JTextField field = new JTextField(text);
            field.setFont(FONT_NORMAL);
            field.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
            field.setBackground(Color.WHITE);
            field.setMargin(new Insets(0, 4, 0, 4));
            field.setEditable(true);
            field.setFocusable(true);
            field.setToolTipText("Nom opérateur: écrire les premières lettres puis Entrée ou Tab pour ouvrir la liste DB");
            ((AbstractDocument) field.getDocument()).setDocumentFilter(new SerieUppercaseDocumentFilter());
            return field;
        }

        private void installOperateurLookup(JTextField codeField, JTextField nameField) {
            // V134:
            // La recherche du nom operateur ne part plus a la perte de focus ni pendant la saisie.
            // Elle demarre uniquement quand l'operateur confirme avec TAB ou INVIO/ENTER.
            codeField.setFocusTraversalKeysEnabled(false);
            nameField.setFocusTraversalKeysEnabled(false);

            AbstractAction codeLookupAction = new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    loadOperateurName(codeField, nameField);
                }
            };

            codeField.addActionListener(codeLookupAction);
            codeField.getInputMap(JComponent.WHEN_FOCUSED).put(
                    KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "lookupOperateurCodeEnter");
            codeField.getActionMap().put("lookupOperateurCodeEnter", codeLookupAction);
            codeField.getInputMap(JComponent.WHEN_FOCUSED).put(
                    KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_TAB, 0), "lookupOperateurCodeTab");
            codeField.getActionMap().put("lookupOperateurCodeTab", codeLookupAction);

            AbstractAction nameLookupAction = new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (!lookupOperateurByNamePrefix(codeField, nameField)) {
                        saveManualOperateurIfNeeded(codeField, nameField);
                    }
                }
            };

            nameField.addActionListener(nameLookupAction);
            nameField.getInputMap(JComponent.WHEN_FOCUSED).put(
                    KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "lookupOperateurName");
            nameField.getActionMap().put("lookupOperateurName", nameLookupAction);
            nameField.getInputMap(JComponent.WHEN_FOCUSED).put(
                    KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_TAB, 0), "lookupOperateurNameTab");
            nameField.getActionMap().put("lookupOperateurNameTab", nameLookupAction);
        }

        private void loadOperateurName(JTextField codeField, JTextField nameField) {
            if (codeField == null || nameField == null) {
                return;
            }

            String code = codeField.getText() != null ? codeField.getText().trim().toUpperCase() : "";
            if (code.isEmpty()) {
                nameField.setText("");
                setOperateurNameManualMode(nameField, false);
                codeField.setBackground(Color.WHITE);
                codeField.repaint();
                return;
            }

            String lastListCode = (String) codeField.getClientProperty("lastOperateurListCode");
            String lastListName = (String) codeField.getClientProperty("lastOperateurListName");
            if (lastListCode != null && lastListCode.equalsIgnoreCase(code)
                    && lastListName != null && lastListName.equals(nameField.getText() == null ? "" : nameField.getText().trim())) {
                moveFocusToNextPersonnelOperatorCode(codeField);
                return;
            }

            codeField.setText(code);

            
            if ("INT".equals(code) || "0".equals(code) || "?".equals(code)) {
                if (Boolean.TRUE.equals(codeField.getClientProperty("operateurListOpen"))) {
                    return;
                }
                codeField.putClientProperty("operateurListOpen", Boolean.TRUE);
                try {
                    OperateurItem selected = chooseOperateurFromList(codeField, code);
                    if (selected != null) {
                        setOperateurNameManualMode(nameField, false);
                        nameField.setText(selected.name);
                        codeField.setText(selected.numero == null ? "" : selected.numero);
                        codeField.putClientProperty("lastOperateurListCode", codeField.getText() == null ? "" : codeField.getText().trim().toUpperCase(Locale.ROOT));
                        codeField.putClientProperty("lastOperateurListName", selected.name == null ? "" : selected.name.trim());
                        codeField.setBackground(Color.WHITE);
                        codeField.repaint();
                        autoFillPersonnelShiftTimeFromLookup(codeField, nameField);
                    }
                } finally {
                    codeField.putClientProperty("operateurListOpen", Boolean.FALSE);
                }
                return;
            }

       
            try {
                String nom = DbOracleManager.findUserNameByCode(code);

                if (nom == null || nom.trim().isEmpty()) {
                    nameField.setText("");
                    askManualOperateurRow(codeField, nameField, code);
                } else {
                    setOperateurNameManualMode(nameField, false);
                    nameField.setText(nom);
                    if (codeField == txtResponsableCode || nameField == txtResponsableName) {
                        syncResponsableToChefSecteur();
                    }
                    codeField.setBackground(Color.WHITE);
                    codeField.repaint();
                    autoFillPersonnelShiftTimeFromLookup(codeField, nameField);
                }
                nameField.repaint();
            } catch (Exception ex) {
                nameField.setText("");
                System.err.println("Erreur recherche opérateur " + code + ": " + ex.getMessage());
                askManualOperateurRow(codeField, nameField, code);
            }
        }

        private void askManualOperateurRow(JTextField codeField, JTextField nameField, String code) {
            blinkOperateurCodeRed(codeField);

            int choice = JOptionPane.showConfirmDialog(
                    getDialogParentComponent(),
                    "Le code opérateur " + code + " n'existe pas dans la base.\n"
                            + "Voulez-vous écrire le nom manuellement ?",
                    "Opérateur non trouvé",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            if (choice == JOptionPane.YES_OPTION) {
                setOperateurNameManualMode(nameField, true);
                SwingUtilities.invokeLater(() -> {
                    nameField.requestFocusInWindow();
                    nameField.selectAll();
                });
            } else {
                setOperateurNameManualMode(nameField, false);
                nameField.setText("");
                codeField.setText("");
                SwingUtilities.invokeLater(() -> codeField.requestFocusInWindow());
            }
        }

        private boolean lookupOperateurByNamePrefix(JTextField codeField, JTextField nameField) {
            if (codeField == null || nameField == null) {
                return false;
            }

            if (Boolean.TRUE.equals(nameField.getClientProperty("nameLookupOpen"))) {
                return true;
            }

            String typedName = nameField.getText() == null ? "" : nameField.getText().trim().toUpperCase(Locale.ROOT);
            String typedCode = codeField.getText() == null ? "" : codeField.getText().trim().toUpperCase(Locale.ROOT);

          
            
            if (!typedCode.isEmpty() && typedCode.matches("\\d+")) {
                return false;
            }

         
            if (typedName.length() < 2 || !typedName.matches(".*[A-ZÀ-Ü].*")) {
                return false;
            }

            nameField.putClientProperty("nameLookupOpen", Boolean.TRUE);
            setNameLookupBusy(nameField, true, typedName);

            new SwingWorker<java.util.List<OperateurItem>, Void>() {
                private String errorMessage = null;

                @Override
                protected java.util.List<OperateurItem> doInBackground() {
                    try {
                        return findOperateursByNamePrefix(typedName);
                    } catch (Exception ex) {
                        errorMessage = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                        return new ArrayList<>();
                    }
                }

                @Override
                protected void done() {
                    try {
                        java.util.List<OperateurItem> matches = get();
                        setNameLookupBusy(nameField, false, typedName);

                        if (errorMessage != null && !errorMessage.trim().isEmpty()) {
                            JOptionPane.showMessageDialog(
                                    getDialogParentComponent(),
                                    "Erreur recherche opérateur: " + errorMessage,
                                    "Recherche opérateur",
                                    JOptionPane.WARNING_MESSAGE
                            );
                            return;
                        }

                        if (matches == null || matches.isEmpty()) {
                            Toolkit.getDefaultToolkit().beep();
                            nameField.setBackground(new Color(255, 245, 200));
                            Timer reset = new Timer(700, ev -> {
                                nameField.setBackground(Color.WHITE);
                                nameField.repaint();
                            });
                            reset.setRepeats(false);
                            reset.start();
                            return;
                        }

                        OperateurItem selected = chooseOperateurFromCandidates(nameField, matches, typedName);
                        if (selected != null) {
                            setOperateurNameManualMode(nameField, true);
                            nameField.setText(selected.name == null ? "" : selected.name);
                            codeField.setText(selected.numero == null ? "" : selected.numero);
                            codeField.setBackground(Color.WHITE);
                            codeField.repaint();
                            nameField.repaint();
                            autoFillPersonnelShiftTimeFromLookup(codeField, nameField);
                        }
                    } catch (Exception ex) {
                        setNameLookupBusy(nameField, false, typedName);
                        JOptionPane.showMessageDialog(
                                getDialogParentComponent(),
                                "Erreur recherche opérateur: " + ex.getMessage(),
                                "Recherche opérateur",
                                JOptionPane.WARNING_MESSAGE
                        );
                    } finally {
                        setNameLookupBusy(nameField, false, typedName);
                        nameField.putClientProperty("nameLookupOpen", Boolean.FALSE);
                    }
                }
            }.execute();

            return true;
        }

        private void setNameLookupBusy(Component parent, boolean busy, String prefix) {
            Window window = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
            if (window != null) {
                window.setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
            }

            if (window instanceof RootPaneContainer) {
                RootPaneContainer rpc = (RootPaneContainer) window;
                Component glassComponent = rpc.getGlassPane();
                if (glassComponent instanceof JComponent) {
                    JComponent glass = (JComponent) glassComponent;
                    if (busy) {
                        glass.removeAll();
                        glass.setLayout(new GridBagLayout());
                        glass.setOpaque(false);
                        glass.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                        JPanel box = new JPanel(new BorderLayout(8, 8));
                        box.setBorder(new CompoundBorder(
                                new LineBorder(new Color(80, 80, 80), 1),
                                new EmptyBorder(12, 18, 12, 18)));
                        box.setBackground(new Color(255, 255, 225));

                        JLabel label = new JLabel("Recherche en cours" + (prefix == null || prefix.trim().isEmpty() ? "..." : " pour: " + prefix.trim().toUpperCase(Locale.ROOT)));
                        label.setFont(new Font("Arial", Font.BOLD, 16));
                        label.setHorizontalAlignment(SwingConstants.CENTER);

                        JProgressBar progress = new JProgressBar();
                        progress.setIndeterminate(true);
                        progress.setStringPainted(false);

                        box.add(label, BorderLayout.NORTH);
                        box.add(progress, BorderLayout.CENTER);
                        glass.add(box, new GridBagConstraints());
                        glass.setVisible(true);
                        glass.revalidate();
                        glass.repaint();
                        glass.paintImmediately(0, 0, Math.max(1, glass.getWidth()), Math.max(1, glass.getHeight()));
                    } else {
                        glass.setVisible(false);
                        glass.removeAll();
                        glass.setCursor(Cursor.getDefaultCursor());
                        glass.revalidate();
                        glass.repaint();
                    }
                }
            }
        }

        private java.util.List<OperateurItem> findOperateursByNamePrefix(String prefix) {
            java.util.List<OperateurItem> result = new ArrayList<>();
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            String pfx = prefix == null ? "" : prefix.trim().toUpperCase(Locale.ROOT);
            if (pfx.length() < 2) {
                return result;
            }

            for (OperateurItem op : operateurItems) {
                String name = op.name == null ? "" : op.name.trim();
                if (!name.isEmpty() && name.toUpperCase(Locale.ROOT).startsWith(pfx)) {
                    addOperateurCandidate(result, seen, op.name, op.numero);
                }
            }

            // Ricerca DB 1: usa il metodo esistente DbOracleManager.findUserNameByCode(code).
            // Questo è il metodo già funzionante quando viene inserito il codice operatore.
            // Facciamo una scansione leggera dei codici e mettiamo in cache i risultati.
            for (OperateurItem op : getCachedDbOperateursByCode()) {
                String name = op.name == null ? "" : op.name.trim();
                if (!name.isEmpty() && name.toUpperCase(Locale.ROOT).startsWith(pfx)) {
                    addOperateurCandidate(result, seen, op.name, op.numero);
                }
            }

            String[][] queries = {
                    {"SELECT NOM, CODE FROM DBM_OPERATEUR WHERE UPPER(TRIM(NOM)) LIKE ? ORDER BY NOM", "2"},
                    {"SELECT NOM, NUMERO FROM DBM_OPERATEUR WHERE UPPER(TRIM(NOM)) LIKE ? ORDER BY NOM", "2"},
                    {"SELECT NAME, CODE FROM DBM_OPERATEUR WHERE UPPER(TRIM(NAME)) LIKE ? ORDER BY NAME", "2"},
                    {"SELECT OPERATEUR, NUMERO FROM DBM_OPERATEUR WHERE UPPER(TRIM(OPERATEUR)) LIKE ? ORDER BY OPERATEUR", "2"},
                    {"SELECT NOM, CODE FROM OPERATEUR WHERE UPPER(TRIM(NOM)) LIKE ? ORDER BY NOM", "2"},
                    {"SELECT NOM, NUMERO FROM OPERATEUR WHERE UPPER(TRIM(NOM)) LIKE ? ORDER BY NOM", "2"},
                    {"SELECT NOM, CODE FROM OPERATEURS WHERE UPPER(TRIM(NOM)) LIKE ? ORDER BY NOM", "2"},
                    {"SELECT NOM, NUMERO FROM OPERATEURS WHERE UPPER(TRIM(NOM)) LIKE ? ORDER BY NOM", "2"},
                    {"SELECT NOM, MATRICULE FROM PERSONNEL WHERE UPPER(TRIM(NOM)) LIKE ? ORDER BY NOM", "2"},
                    {"SELECT NOM_PRENOM, MATRICULE FROM PERSONNEL WHERE UPPER(TRIM(NOM_PRENOM)) LIKE ? ORDER BY NOM_PRENOM", "2"},
                    {"SELECT USER_NAME, USER_ID FROM USERS WHERE UPPER(TRIM(USER_NAME)) LIKE ? ORDER BY USER_NAME", "2"},
                    {"SELECT NOM_UTILISATEUR, CODE_UTILISATEUR FROM UTILISATEUR WHERE UPPER(TRIM(NOM_UTILISATEUR)) LIKE ? ORDER BY NOM_UTILISATEUR", "2"},
                    {"SELECT NOM_UTILISATEUR, CODE_UTILISATEUR FROM UTILISATEURS WHERE UPPER(TRIM(NOM_UTILISATEUR)) LIKE ? ORDER BY NOM_UTILISATEUR", "2"}
            };

            try (Connection cn = DbOracleManager.getConnection()) {
                for (String[] q : queries) {
                    try (PreparedStatement ps = cn.prepareStatement(q[0])) {
                        ps.setString(1, pfx + "%");
                        try (ResultSet rs = ps.executeQuery()) {
                            int guard = 0;
                            while (rs.next() && guard < 100) {
                                String name = rs.getString(1);
                                String code = rs.getString(2);
                                addOperateurCandidate(result, seen, name, code);
                                guard++;
                            }
                        }
                    } catch (SQLException ignored) {
                    
                    	
                    }
                }
            } catch (Exception ex) {
                System.err.println("Erreur recherche opérateurs par nom '" + pfx + "': " + ex.getMessage());
            }

            result.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
            return result;
        }

        private java.util.List<OperateurItem> cachedDbOperateursByCode = null;

        private java.util.List<OperateurItem> getCachedDbOperateursByCode() {
            if (cachedDbOperateursByCode != null) {
                return cachedDbOperateursByCode;
            }

            java.util.List<OperateurItem> cache = new ArrayList<>();
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();

        
            for (int i = 0; i <= 999; i++) {
                String code = String.valueOf(i);
                try {
                    String name = DbOracleManager.findUserNameByCode(code);
                    if (name != null && !name.trim().isEmpty()) {
                        addOperateurCandidate(cache, seen, name, code);
                    }
                } catch (Exception ignored) {
                    
                }
            }

            cachedDbOperateursByCode = cache;
            return cachedDbOperateursByCode;
        }

        private void addOperateurCandidate(java.util.List<OperateurItem> result, java.util.Set<String> seen, String name, String code) {
            String n = name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
            String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
            if (n.isEmpty()) {
                return;
            }
            String key = n + "|" + c;
            if (seen.add(key)) {
                result.add(new OperateurItem(n, c));
            }
        }

        private OperateurItem chooseOperateurFromCandidates(Component parent, java.util.List<OperateurItem> candidates, String prefix) {
            if (candidates == null || candidates.isEmpty()) {
                return null;
            }

            DefaultTableModel model = new DefaultTableModel(new String[]{"Opérateur", "Code"}, 0) {
                @Override public boolean isCellEditable(int row, int column) { return false; }
            };
            for (OperateurItem op : candidates) {
                model.addRow(new Object[]{op.name, op.numero});
            }

            JTable listTable = new JTable(model);
            listTable.setFont(new Font("Arial", Font.PLAIN, 16));
            listTable.setRowHeight(30);
            listTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 15));
            listTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            listTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            listTable.getColumnModel().getColumn(0).setPreferredWidth(360);
            listTable.getColumnModel().getColumn(1).setPreferredWidth(90);

            final OperateurItem[] selected = {null};
            JDialog dropdown = new JDialog(SwingUtilities.getWindowAncestor(parent), "Recherche opérateur: " + prefix, Dialog.ModalityType.APPLICATION_MODAL);
            dropdown.setUndecorated(true);
            dropdown.getRootPane().setBorder(new LineBorder(new Color(80, 80, 80), 1));

            JLabel title = new JLabel("  Double-clic ou Entrée pour choisir un opérateur");
            title.setFont(new Font("Arial", Font.BOLD, 14));
            title.setOpaque(true);
            title.setBackground(new Color(240, 240, 240));
            title.setBorder(new EmptyBorder(4, 4, 4, 4));

            Runnable choose = () -> {
                if (listTable.getSelectedRow() >= 0) {
                    int r = listTable.convertRowIndexToModel(listTable.getSelectedRow());
                    selected[0] = new OperateurItem(String.valueOf(model.getValueAt(r, 0)), String.valueOf(model.getValueAt(r, 1)));
                    dropdown.dispose();
                }
            };

            listTable.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() >= 2) {
                        choose.run();
                    }
                }
            });
            listTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "chooseOperateur");
            listTable.getActionMap().put("chooseOperateur", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { choose.run(); }
            });
            listTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "closeDropdown");
            listTable.getActionMap().put("closeDropdown", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { dropdown.dispose(); }
            });

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(title, BorderLayout.NORTH);
            panel.add(new JScrollPane(listTable), BorderLayout.CENTER);
            dropdown.setContentPane(panel);

            int visibleRows = Math.min(12, candidates.size());
            dropdown.setSize(480, 34 + (visibleRows * 30) + 24);
            if (parent != null && parent.isShowing()) {
                Point pt = parent.getLocationOnScreen();
                dropdown.setLocation(pt.x, pt.y + parent.getHeight());
            } else {
                dropdown.setLocationRelativeTo(SwingUtilities.getWindowAncestor(parent));
            }

            SwingUtilities.invokeLater(() -> {
                listTable.requestFocusInWindow();
                if (listTable.getRowCount() > 0) {
                    listTable.setRowSelectionInterval(0, 0);
                }
            });
            dropdown.setVisible(true);
            return selected[0];
        }


        private OperateurItem chooseOperateurFromList(Component parent, String filterCode) {
            java.util.List<OperateurItem> filtered = new ArrayList<>();
            String filter = filterCode == null ? "" : filterCode.trim().toUpperCase(Locale.ROOT);

            for (OperateurItem op : operateurItems) {
                String numero = op.numero == null ? "" : op.numero.trim();
                if ("INT".equals(filter)) {
                    if ("INT".equalsIgnoreCase(numero)) {
                        filtered.add(op);
                    }
                } else if ("0".equals(filter)) {
                    if ("0".equals(numero)) {
                        filtered.add(op);
                    }
                } else {
                    filtered.add(op);
                }
            }

            if (filtered.isEmpty()) {
                JOptionPane.showMessageDialog(
                        getDialogParentComponent(),
                        "Aucun opérateur disponible dans List OPERATEUR.",
                        "List OPERATEUR",
                        JOptionPane.INFORMATION_MESSAGE
                );
                return null;
            }

            DefaultTableModel model = new DefaultTableModel(new String[]{"Opérateur", "Numero"}, 0) {
                @Override public boolean isCellEditable(int row, int column) { return false; }
            };
            for (OperateurItem op : filtered) {
                model.addRow(new Object[]{op.name, op.numero});
            }

            JTable listTable = new JTable(model);
            listTable.setFont(new Font("Arial", Font.PLAIN, 16));
            listTable.setRowHeight(30);
            listTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 15));
            listTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            listTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            listTable.getColumnModel().getColumn(0).setPreferredWidth(330);
            listTable.getColumnModel().getColumn(1).setPreferredWidth(90);

            final OperateurItem[] selected = {null};

            JDialog dropdown = new JDialog(SwingUtilities.getWindowAncestor(parent), "List OPERATEUR", Dialog.ModalityType.APPLICATION_MODAL);
            dropdown.setUndecorated(true);
            dropdown.getRootPane().setBorder(new LineBorder(new Color(80, 80, 80), 1));

            JLabel title = new JLabel("  Double-clic pour choisir un opérateur");
            title.setFont(new Font("Arial", Font.BOLD, 14));
            title.setOpaque(true);
            title.setBackground(new Color(240, 240, 240));
            title.setBorder(new EmptyBorder(4, 4, 4, 4));

            listTable.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() >= 2 && listTable.getSelectedRow() >= 0) {
                        int r = listTable.convertRowIndexToModel(listTable.getSelectedRow());
                        selected[0] = new OperateurItem(
                                String.valueOf(model.getValueAt(r, 0)),
                                String.valueOf(model.getValueAt(r, 1))
                        );
                        dropdown.dispose();
                    }
                }
            });

            listTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "closeDropdown");
            listTable.getActionMap().put("closeDropdown", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { dropdown.dispose(); }
            });

            JScrollPane scroll = new JScrollPane(listTable);
            scroll.setBorder(null);

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(title, BorderLayout.NORTH);
            panel.add(scroll, BorderLayout.CENTER);
            dropdown.setContentPane(panel);

            int visibleRows = Math.min(12, filtered.size());
            dropdown.setSize(430, 34 + (visibleRows * 30) + 24);

            if (parent != null && parent.isShowing()) {
                Point p = parent.getLocationOnScreen();
                dropdown.setLocation(p.x, p.y + parent.getHeight());
            } else {
                dropdown.setLocationRelativeTo(SwingUtilities.getWindowAncestor(parent));
            }

            SwingUtilities.invokeLater(() -> {
                listTable.requestFocusInWindow();
                if (listTable.getRowCount() > 0) {
                    listTable.setRowSelectionInterval(0, 0);
                }
            });

            dropdown.setVisible(true);
            return selected[0];
        }

        private void saveManualOperateurIfNeeded(JTextField codeField, JTextField nameField) {
            if (codeField == null || nameField == null || !nameField.isEditable()) {
                return;
            }

            String code = codeField.getText() == null ? "" : codeField.getText().trim();
            String name = nameField.getText() == null ? "" : nameField.getText().trim();

            if (!"0".equals(code) || name.isEmpty()) {
                return;
            }

            boolean exists = false;
            for (OperateurItem op : operateurItems) {
                if (op.name != null && op.name.trim().equalsIgnoreCase(name)) {
                    op.numero = "0";
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                operateurItems.add(new OperateurItem(name, "0"));
            }

            setOperateurNameManualMode(nameField, false);
            codeField.setBackground(Color.WHITE);
            codeField.repaint();
        }

        private void setOperateurNameManualMode(JTextField nameField, boolean manual) {
            if (nameField == null) {
                return;
            }

          
            nameField.setEditable(true);
            nameField.setFocusable(true);
            nameField.setBackground(manual ? new Color(255, 255, 210) : Color.WHITE);
            nameField.repaint();
        }

        private void blinkOperateurCodeRed(JTextField codeField) {
            if (codeField == null) {
                return;
            }

            final Color baseColor = Color.WHITE;
            final Color blinkColor = new Color(0xFF, 0x66, 0x66);

            Timer blinkTimer = new Timer(180, null);
            blinkTimer.addActionListener(new ActionListener() {
                private int step = 0;

                @Override
                public void actionPerformed(ActionEvent e) {
                    if (step % 2 == 0) {
                        codeField.setBackground(blinkColor);
                    } else {
                        codeField.setBackground(baseColor);
                    }

                    codeField.repaint();
                    step++;

                    if (step >= 4) {
                        codeField.setBackground(baseColor);
                        codeField.repaint();
                        blinkTimer.stop();
                    }
                }
            });
            blinkTimer.setInitialDelay(0);
            blinkTimer.start();
        }


        private JTextField createTimeField() {
            JTextField field = new JTextField("");
            field.setFont(FONT_NORMAL);
            field.setHorizontalAlignment(SwingConstants.CENTER);
            field.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
            field.setBackground(Color.WHITE);
            field.setMargin(new Insets(0, 2, 0, 2));
            field.setToolTipText("Format: 22, 22h00, 22:00, 6:30 ou 6.30");
            field.putClientProperty("personnelTimeField", Boolean.TRUE);

            field.addActionListener(e -> normalizeTimeField(field));
            field.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    normalizeTimeField(field);
                }
            });

            return field;
        }

        private JTextField createTotalField() {
            JTextField field = new JTextField("");
            field.setFont(FONT_NORMAL);
            field.setHorizontalAlignment(SwingConstants.CENTER);
            field.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
            field.setBackground(Color.WHITE);
            field.setMargin(new Insets(0, 4, 0, 4));
            field.setEditable(false);
            field.setFocusable(false);
            return field;
        }

        private void normalizeTimeField(JTextField field) {
            Integer minutes = parseFrenchTime(field.getText());
            if (minutes != null) {
                field.setText(formatFrenchTime(minutes));
            }
            updatePersonnelTotals();
        }

        private JLabel addHiddenPersonnelRole(String role) {
            JLabel label = new JLabel(role == null ? "" : role);
            label.setVisible(false);
            return label;
        }

        private String componentText(Component component) {
            if (component == null) return "";
            if (component instanceof JLabel) return ((JLabel) component).getText() == null ? "" : ((JLabel) component).getText();
            if (component instanceof JTextField) return ((JTextField) component).getText() == null ? "" : ((JTextField) component).getText();
            if (component instanceof JComboBox) {
                Object selected = ((JComboBox<?>) component).getSelectedItem();
                return selected == null ? "" : selected.toString();
            }
            return "";
        }

        private void registerPersonnelTimeRow(Component roleComponent, JTextField code, JTextField name, JTextField debut, JTextField fin, JTextField totalLigne) {
            PersonnelTimeRow row = new PersonnelTimeRow(debut, fin, totalLigne);
            personnelTimeRows.add(row);
            personnelReportRows.add(new PersonnelReportRow(roleComponent, code, name, debut, fin, totalLigne));

            DocumentListener listener = new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { updatePersonnelTotals(); }
                @Override public void removeUpdate(DocumentEvent e) { updatePersonnelTotals(); }
                @Override public void changedUpdate(DocumentEvent e) { updatePersonnelTotals(); }
            };
            debut.getDocument().addDocumentListener(listener);
            fin.getDocument().addDocumentListener(listener);
        }

        private void updatePersonnelTotals() {
            int totalMinutes = 0;

            for (PersonnelTimeRow row : personnelTimeRows) {
                Integer start = parseFrenchTime(row.debut.getText());
                Integer end = parseFrenchTime(row.fin.getText());

                if (start == null || end == null) {
                    row.total.setText("");
                    continue;
                }

                int minutes = end - start;
                if (minutes < 0) {
                    minutes += 24 * 60;
                }

                row.total.setText(formatFrenchDuration(minutes));
                totalMinutes += minutes;
            }

            if (txtHeuresTotales != null) {
                txtHeuresTotales.setText(formatFrenchDuration(totalMinutes));
            }
        }

        private Integer parseFrenchTime(String value) {
            if (value == null) return null;

            String text = value.trim().toLowerCase();
            if (text.isEmpty()) return null;

            text = text.replace('H', 'h')
                    .replace(':', 'h')
                    .replace('.', 'h');

            try {
                int hours;
                int minutes;

                if (text.contains("h")) {
                    String[] parts = text.split("h", -1);
                    if (parts.length != 2) return null;
                    hours = Integer.parseInt(parts[0].trim());
                    minutes = parts[1].trim().isEmpty() ? 0 : Integer.parseInt(parts[1].trim());
                } else {
                    hours = Integer.parseInt(text.trim());
                    minutes = 0;
                }

                if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
                    return null;
                }

                return hours * 60 + minutes;
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        private String formatFrenchTime(int totalMinutes) {
            int normalized = ((totalMinutes % (24 * 60)) + (24 * 60)) % (24 * 60);
            int hours = normalized / 60;
            int minutes = normalized % 60;
            return String.format("%dH%02d", hours, minutes);
        }

        private String formatFrenchDuration(int totalMinutes) {
            int hours = totalMinutes / 60;
            int minutes = totalMinutes % 60;
            return String.format("%dH%02d", hours, minutes);
        }


        private JTextField createArticleTableField() {
            JTextField field = new JTextField();
            field.setFont(FONT_BOLD);
            field.setHorizontalAlignment(SwingConstants.CENTER);
            field.setBorder(new CompoundBorder(
                    new LineBorder(new Color(0x00, 0x70, 0xC0), 2),
                    new EmptyBorder(0, 4, 0, 4)));
            field.setBackground(new Color(0xE8, 0xF3, 0xFF));
            field.setSelectionColor(new Color(0x00, 0x70, 0xC0));
            field.setSelectedTextColor(Color.WHITE);
            ((AbstractDocument) field.getDocument()).setDocumentFilter(new UppercaseDocumentFilter());
            return field;
        }

        private void installEnterConfirmAndMove(JTable table) {
            if (table == null) {
                return;
            }

            table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "confirmAndMoveNextCell");

            table.getActionMap().put("confirmAndMoveNextCell", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int row = table.getSelectedRow();
                    int col = table.getSelectedColumn();

                    if (table.isEditing()) {
                        if (isEditingArticlePrefixOnly(table)) {
                            return;
                        }
                        TableCellEditor editor = table.getCellEditor();
                        if (editor != null) {
                            editor.stopCellEditing();
                        }
                    }

                    moveToNextEditableCell(table, row, col);
                }
            });

            TableCellEditor currentDefault = table.getDefaultEditor(Object.class);
            if (!(currentDefault instanceof EnterMoveCellEditor)) {
                table.setDefaultEditor(Object.class, new EnterMoveCellEditor(table));
            }
        }

        private boolean isEditingArticlePrefixOnly(JTable table) {
            if (table == null || !table.isEditing()) {
                return false;
            }
            int editingCol = table.getEditingColumn();
            if (editingCol < 0 || table.convertColumnIndexToModel(editingCol) != 0) {
                return false;
            }
            Component editor = table.getEditorComponent();
            if (!(editor instanceof JTextField)) {
                return false;
            }
            JTextField field = (JTextField) editor;
            String text = field.getText() == null ? "" : field.getText().trim();
            if (!"AL".equalsIgnoreCase(text)) {
                return false;
            }
            SwingUtilities.invokeLater(() -> {
                field.requestFocusInWindow();
                field.setCaretPosition(field.getText() == null ? 0 : field.getText().length());
            });
            return true;
        }

        private void installEnterKeyOnEditorField(JTable table, JTextField field) {
            if (table == null || field == null) {
                return;
            }

            field.addActionListener(e -> {
                int row = table.getEditingRow();
                int col = table.getEditingColumn();

                if (table.isEditing()) {
                    TableCellEditor editor = table.getCellEditor();
                    if (editor != null) {
                        editor.stopCellEditing();
                    }
                }

                moveToNextEditableCell(table, row, col);
            });
        }

        private void moveToNextEditableCell(JTable table, int row, int col) {
            if (table == null || row < 0 || col < 0) {
                return;
            }

            int targetRow = row;
            int targetCol = col;

            int safety = table.getRowCount() * table.getColumnCount() + 1;
            do {
                targetCol++;
                if (targetCol >= table.getColumnCount()) {
                    targetCol = 0;
                    targetRow++;
                }

                if (targetRow >= table.getRowCount()) {
                    targetRow = table.getRowCount() - 1;
                    targetCol = table.getColumnCount() - 1;
                    break;
                }

                safety--;
            } while (safety > 0 && !table.isCellEditable(targetRow, targetCol));

            final int finalRow = targetRow;
            final int finalCol = targetCol;

            SwingUtilities.invokeLater(() -> {
                table.requestFocusInWindow();
                table.changeSelection(finalRow, finalCol, false, false);

                if (table.isCellEditable(finalRow, finalCol)) {
                    table.editCellAt(finalRow, finalCol);
                    Component editor = table.getEditorComponent();
                    if (editor != null) {
                        editor.requestFocusInWindow();
                        positionProductionEditorCaret(table, finalRow, finalCol, editor);
                    }
                }
            });
        }

        private class EnterMoveCellEditor extends DefaultCellEditor {
            EnterMoveCellEditor(JTable table) {
                super(new JTextField());
                setClickCountToStart(1);

                JTextField field = (JTextField) getComponent();
                field.setFont(FONT_NORMAL);
                field.setHorizontalAlignment(SwingConstants.CENTER);
                field.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                field.setMargin(new Insets(0, 2, 0, 2));
                installEnterKeyOnEditorField(table, field);
            }
        }

        private void installNombreMelangesLookup(JTable table) {
            if (table == null || table.getColumnCount() <= 3) return;
            table.getColumnModel().getColumn(3).setCellEditor(new NombreMelangesCellEditor(table));
        }

        private void installRepassNumericEditor(JTable table) {
            if (table == null || table.getColumnCount() <= 4) return;
            table.getColumnModel().getColumn(4).setCellEditor(new RepassCellEditor(table));
        }

        private class RepassCellEditor extends DefaultCellEditor {
            private final JTable table;

            RepassCellEditor(JTable table) {
                super(new JTextField());
                this.table = table;
                setClickCountToStart(1);

                JTextField field = (JTextField) getComponent();
                field.setFont(FONT_NORMAL);
                field.setHorizontalAlignment(SwingConstants.CENTER);
                field.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                field.setMargin(new Insets(0, 2, 0, 2));
                ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
                    @Override public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                        if (string != null) string = string.replaceAll("[^0-9]", "");
                        if (string != null && !string.isEmpty()) super.insertString(fb, offset, string, attr);
                    }
                    @Override public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                        if (text != null) text = text.replaceAll("[^0-9]", "");
                        super.replace(fb, offset, length, text, attrs);
                    }
                });
                installEnterKeyOnEditorField(table, field);
            }

            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                Component c = super.getTableCellEditorComponent(table, value, isSelected, row, column);
                if (c instanceof JTextField) {
                    JTextField field = (JTextField) c;
                    String text = value == null ? "" : value.toString().replaceAll("[^0-9]", "");
                    field.setText(text);
                    field.selectAll();
                }
                return c;
            }

            @Override
            public Object getCellEditorValue() {
                String text = ((JTextField) getComponent()).getText();
                text = text == null ? "" : text.replaceAll("[^0-9]", "").trim();
                return text.isEmpty() ? "" : text + "R";
            }

            @Override
            public boolean stopCellEditing() {
                boolean ok = super.stopCellEditing();
                if (ok && table != null) {
                    SwingUtilities.invokeLater(() -> refreshProductionTotalsFromTableClientProperties(table));
                }
                return ok;
            }
        }

        private class NombreMelangesCellEditor extends DefaultCellEditor {
            private final JTable table;
            private String valueAtStart = "";
            private boolean applyingEditorValue = false;
            private boolean editorTextDirty = false;

            NombreMelangesCellEditor(JTable table) {
                super(new JTextField());
                this.table = table;
                setClickCountToStart(1);
                JTextField field = (JTextField) getComponent();
                field.setFont(FONT_NORMAL);
                field.setHorizontalAlignment(SwingConstants.CENTER);
                field.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                field.setMargin(new Insets(0, 2, 0, 2));
                field.getDocument().addDocumentListener(new DocumentListener() {
                    @Override public void insertUpdate(DocumentEvent e) { markDirty(); }
                    @Override public void removeUpdate(DocumentEvent e) { markDirty(); }
                    @Override public void changedUpdate(DocumentEvent e) { markDirty(); }
                    private void markDirty() {
                        if (!applyingEditorValue) editorTextDirty = true;
                    }
                });
                installEnterKeyOnEditorField(table, field);
                field.getInputMap(JComponent.WHEN_FOCUSED).put(
                        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_TAB, 0), "confirmNombreTab");
                field.getActionMap().put("confirmNombreTab", new AbstractAction() {
                    @Override public void actionPerformed(ActionEvent e) {
                        int row = table.getEditingRow();
                        int col = table.getEditingColumn();
                        TableCellEditor editor = table.getCellEditor();
                        if (editor != null) editor.stopCellEditing();
                        moveToNextEditableCell(table, row, col);
                    }
                });
            }

            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                Component c = super.getTableCellEditorComponent(table, value, isSelected, row, column);
                if (c instanceof JTextField) {
                    JTextField field = (JTextField) c;
                    valueAtStart = value == null ? "" : value.toString().trim();
                    editorTextDirty = false;
                    applyingEditorValue = true;
                    try {
                        field.setText(valueAtStart);
                    } finally {
                        applyingEditorValue = false;
                    }
                    // V193.29: marca esplicitamente che l'eventuale ricalcolo DB nasce
                    // da un editing manuale di Nombre de Melanges e non dal caricamento
                    // del file salvato. Questo sblocca il popup quando l'utente conferma
                    // il campo, ma continua a bloccarlo alla riapertura del form.
                    int modelRow = row >= 0 ? table.convertRowIndexToModel(row) : -1;
                    table.putClientProperty("userEditingNombreMelanges", Boolean.TRUE);
                    table.putClientProperty("userEditingNombreMelangesRow", Integer.valueOf(modelRow));
                    field.selectAll();
                }
                return c;
            }

            @Override
            public boolean stopCellEditing() {
                int editingRow = table.getEditingRow();
                int modelRowBeforeStop = editingRow >= 0 ? table.convertRowIndexToModel(editingRow) : -1;
                Object value = getCellEditorValue();
                boolean wasDirty = editorTextDirty;
                Object manualEditFlag = table.getClientProperty("userEditingNombreMelanges");

                // V193.32: la conferma di Nombre de Melanges generava due percorsi contemporanei:
                // 1) il TableModelListener scatenato da super.stopCellEditing()
                // 2) il refresh esplicito schedulato qui sotto.
                // Per evitare due finestre consecutive per lo stesso prodotto, il prossimo evento
                // automatico del modello su questa riga viene ignorato e rimane un solo refresh esplicito.
                if (Boolean.TRUE.equals(manualEditFlag) && modelRowBeforeStop >= 0) {
                    table.putClientProperty("ignoreNextProductionDbLookupModelEvent:" + modelRowBeforeStop, Boolean.TRUE);
                }

                boolean stopped = super.stopCellEditing();
                if (stopped && editingRow >= 0) {
                    int modelRow = table.convertRowIndexToModel(editingRow);
                    String newValue = value == null ? "" : value.toString().trim();
                    // V193.29: se l'utente conferma Nombre de Melanges con INVIO/TAB
                    // o uscendo dalla cella, abilita una sola ricerca DB sulla riga.
                    // Non dipendiamo piu solo da DocumentListener/wasDirty, perche in
                    // alcuni casi Swing conferma il valore senza segnalare correttamente
                    // la modifica e il popup non si apre.
                    if (Boolean.TRUE.equals(manualEditFlag)
                            && !loadingShiftState
                            && !Boolean.TRUE.equals(table.getClientProperty("suspendProductionDbLookup"))
                            && !newValue.isEmpty()) {
                        // V193.30: confermando manualmente Nombre de Melanges deve sempre
                        // ripartire il controllo DB, anche se il valore digitato e uguale a quello
                        // precedente o se la riga aveva gia un Poids/lastConfirmedKey salvato.
                        table.putClientProperty("suppressProductionDbLookupUntilUserNombre", Boolean.FALSE);
                        table.putClientProperty("allowProductionDbLookupOnce:" + modelRow, Boolean.TRUE);
                        table.putClientProperty("lastConfirmedProductionLookupKey:" + modelRow, null);
                        SwingUtilities.invokeLater(() -> refreshProductionPassageFromRowOnly(table, modelRow, true));
                    }
                } else if (modelRowBeforeStop >= 0) {
                    table.putClientProperty("ignoreNextProductionDbLookupModelEvent:" + modelRowBeforeStop, Boolean.FALSE);
                }
                table.putClientProperty("userEditingNombreMelanges", Boolean.FALSE);
                table.putClientProperty("userEditingNombreMelangesRow", null);
                editorTextDirty = false;
                return stopped;
            }
        }

        private void installArticleLookup(JTable table) {
            // V193.08: non terminare l'editor dell'ARTICLE per semplice perdita focus.
            // La conferma deve avvenire solo con INVIO/TAB o con una conferma esplicita del codice.
            table.putClientProperty("terminateEditOnFocusLost", Boolean.FALSE);
            table.getColumnModel().getColumn(0).setCellEditor(new ArticleCellEditor(table, createArticleTableField()));
        }


        private void installArticleEditActivation(JTable table) {
            if (table == null) return;

            // V193.28: la colonna ARTICLE deve rimanere sempre modificabile manualmente.
            // Alcuni refresh/selection listener della Production realisee potevano lasciare la cella
            // selezionata ma senza aprire davvero l'editor. Con doppio click sull'ARTICLE forziamo
            // l'apertura dell'editor e mettiamo il cursore nel campo, senza avviare lookup DB.
            table.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e == null || e.getClickCount() < 2) return;
                    int viewRow = table.rowAtPoint(e.getPoint());
                    int viewCol = table.columnAtPoint(e.getPoint());
                    if (viewRow < 0 || viewCol < 0) return;
                    int modelCol = table.convertColumnIndexToModel(viewCol);
                    if (modelCol != 0) return;

                    if (table.isEditing()) {
                        TableCellEditor currentEditor = table.getCellEditor();
                        if (currentEditor != null) currentEditor.cancelCellEditing();
                    }

                    SwingUtilities.invokeLater(() -> {
                        table.requestFocusInWindow();
                        table.changeSelection(viewRow, viewCol, false, false);
                        if (table.isCellEditable(viewRow, viewCol)) {
                            table.editCellAt(viewRow, viewCol);
                            Component editor = table.getEditorComponent();
                            if (editor != null) {
                                editor.requestFocusInWindow();
                                if (editor instanceof JTextField) {
                                    JTextField tf = (JTextField) editor;
                                    tf.selectAll();
                                }
                            }
                        }
                    });
                }
            });
        }

        private JTextField createSerieTableField() {
            JTextField field = new JTextField();
            field.setFont(FONT_NORMAL);
            field.setHorizontalAlignment(SwingConstants.CENTER);
            field.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
            field.setMargin(new Insets(0, 2, 0, 2));
            ((AbstractDocument) field.getDocument()).setDocumentFilter(new SerieUppercaseDocumentFilter());
            return field;
        }

        private void installSerieUppercaseEditor(JTable table) {
            if (table == null || table.getColumnCount() <= 2) {
                return;
            }
            table.getColumnModel().getColumn(2).setCellEditor(new SerieCellEditor(table, createSerieTableField()));
            if (table.getColumnCount() > 9) {
                table.getColumnModel().getColumn(9).setCellEditor(new UppercaseTextCellEditor(table, createSerieTableField()));
            }
        }

        private JTextField createRemarqueTableField() {
            JTextField field = new JTextField();
            field.setFont(FONT_NORMAL);
            field.setHorizontalAlignment(SwingConstants.LEFT);
            field.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
            field.setMargin(new Insets(0, 4, 0, 4));
            ((AbstractDocument) field.getDocument()).setDocumentFilter(new SerieUppercaseDocumentFilter());
            return field;
        }

        private void installRemarqueUppercaseEditor(JTable table) {
            if (table == null || table.getColumnCount() <= 10) {
                return;
            }
            table.getColumnModel().getColumn(10).setCellEditor(new UppercaseTextCellEditor(table, createRemarqueTableField()));
        }

        private class UppercaseTextCellEditor extends DefaultCellEditor {
            UppercaseTextCellEditor(JTable table, JTextField field) {
                super(field);
                setClickCountToStart(1);
                installEnterKeyOnEditorField(table, field);
            }

            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                Component c = super.getTableCellEditorComponent(table, value, isSelected, row, column);
                if (c instanceof JTextField) {
                    JTextField field = (JTextField) c;
                    field.setText(value != null ? value.toString().toUpperCase(Locale.ROOT) : "");
                    field.selectAll();
                }
                return c;
            }
        }

        private class PrefixedUppercaseCellEditor extends DefaultCellEditor {
            private final String prefix;

            PrefixedUppercaseCellEditor(JTable table, JTextField field, String prefix) {
                super(field);
                this.prefix = prefix == null ? "" : prefix;
                setClickCountToStart(1);
                installEnterKeyOnEditorField(table, field);
            }

            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                Component c = super.getTableCellEditorComponent(table, value, isSelected, row, column);
                if (c instanceof JTextField) {
                    JTextField field = (JTextField) c;
                    String text = value != null ? value.toString().trim().toUpperCase(Locale.ROOT) : "";
                    if (text.isEmpty()) {
                        field.setText(prefix);
                        field.setCaretPosition(field.getText().length());
                        field.moveCaretPosition(field.getText().length());
                    } else {
                        field.setText(text);
                        if (text.equals(prefix)) {
                            field.setCaretPosition(field.getText().length());
                        } else {
                            field.selectAll();
                        }
                    }
                }
                return c;
            }
        }

        private class SerieCellEditor extends DefaultCellEditor {
            private final JTable table;

            SerieCellEditor(JTable table, JTextField field) {
                super(field);
                this.table = table;
                setClickCountToStart(1);
                installEnterKeyOnEditorField(table, field);
            }

            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                Component c = super.getTableCellEditorComponent(table, value, isSelected, row, column);
                if (c instanceof JTextField) {
                    JTextField field = (JTextField) c;
                    String text = value != null ? value.toString().trim().toUpperCase(Locale.ROOT) : "";
                    if (text.isEmpty()) {
                        field.setText("26/");
                        field.setCaretPosition(field.getText().length());
                    } else {
                        field.setText(text);
                        if (text.startsWith("26/")) {
                            field.setCaretPosition(field.getText().length());
                        } else {
                            field.selectAll();
                        }
                    }
                }
                return c;
            }

            @Override
            public boolean stopCellEditing() {
                int editingRow = table.getEditingRow();
                Object value = getCellEditorValue();
                boolean stopped = super.stopCellEditing();
                if (stopped && editingRow >= 0) {
                    int modelRow = table.convertRowIndexToModel(editingRow);
                    String serie = value == null ? "" : value.toString().trim().toUpperCase(Locale.ROOT);
                    table.getModel().setValueAt(serie, modelRow, 2);
                }
                return stopped;
            }
        }

        private class ArticleCellEditor extends DefaultCellEditor {
            private final JTable table;
            private boolean lookupDoneForThisEdit = false;
            private String articleValueAtStart = "";

            ArticleCellEditor(JTable table, JTextField field) {
                super(field);
                this.table = table;
                setClickCountToStart(1);
                field.setFocusTraversalKeysEnabled(false);

                AbstractAction confirmArticleAction = new AbstractAction() {
                    @Override public void actionPerformed(ActionEvent e) {
                        // V190.22: se ARTICLE contiene solo il prefisso automatico "AL",
                        // non confermare e non avanzare. Questo evita che l'ENTER usato
                        // nella colonna "à" venga riapplicato subito alla nuova riga Article.
                        String currentText = field.getText() == null ? "" : field.getText().trim();
                        if ("AL".equalsIgnoreCase(currentText)) {
                            SwingUtilities.invokeLater(() -> {
                                field.requestFocusInWindow();
                                field.setCaretPosition(field.getText() == null ? 0 : field.getText().length());
                            });
                            return;
                        }

                        // V190.07: ARTICLE viene confermato con INVIO/TAB/click.
                        // Il focus successivo viene deciso da lookupArticleForRow(...), non dal movimento generico,
                        // cosi non si apre la cella sbagliata e non viene selezionato/cancellato il prefisso.
                        stopCellEditing();
                    }
                };

                field.addActionListener(confirmArticleAction);
                field.getInputMap(JComponent.WHEN_FOCUSED).put(
                        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "confirmArticleEnter");
                field.getActionMap().put("confirmArticleEnter", confirmArticleAction);
                field.getInputMap(JComponent.WHEN_FOCUSED).put(
                        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_TAB, 0), "confirmArticleTab");
                field.getActionMap().put("confirmArticleTab", confirmArticleAction);
            }

            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                lookupDoneForThisEdit = false;
                articleValueAtStart = value == null ? "" : value.toString().trim().toUpperCase(Locale.ROOT);
                Component c = super.getTableCellEditorComponent(table, value, isSelected, row, column);
                if (c instanceof JTextField) {
                    JTextField field = (JTextField) c;
                    String text = value != null ? value.toString().trim().toUpperCase(Locale.ROOT) : "";
                    if (text.isEmpty()) {
                        // V193.19: precompila solo l'editor, senza scrivere "AL" nel model.
                        // Scrivere nel model all'apertura dell'editor ARTICLE genera TableModelEvent
                        // mentre l'utente sta ancora modificando la cella; questo poteva lanciare
                        // refresh/ricalcoli e far uscire automaticamente dalla modifica.
                        text = "AL";
                        field.setText("AL");
                        field.setCaretPosition(field.getText().length());
                    } else {
                        field.setText(text);
                        if ("AL".equalsIgnoreCase(text.trim())) {
                            field.setCaretPosition(field.getText().length());
                        } else {
                            field.selectAll();
                        }
                    }
                }
                return c;
            }

            @Override
            public boolean stopCellEditing() {
                int editingRow = table.getEditingRow();
                int editingColumn = table.getEditingColumn();
                boolean stopped = super.stopCellEditing();

                
                if (stopped && !lookupDoneForThisEdit && editingColumn == 0 && editingRow >= 0) {
                    Object value = getCellEditorValue();
                    String articleText = value == null ? "" : value.toString().trim();
                    String normalizedArticleText = articleText.toUpperCase(Locale.ROOT);
                    if (normalizedArticleText.equals(articleValueAtStart)) {
                        // V193.28: se l'utente apre l'ARTICLE ma non cambia il valore, non rilanciare lookup/refresh.
                        lookupDoneForThisEdit = true;
                        return stopped;
                    }
                    if ("AL".equalsIgnoreCase(articleText)) {
                        // V190.26: AL da solo e solo un prefisso di inserimento.
                        // Non rilanciare il focus/editor e non fare lookup, altrimenti il prefisso lampeggia o sparisce.
                        lookupDoneForThisEdit = true;
                        return stopped;
                    }
                    lookupDoneForThisEdit = true;
                    SwingUtilities.invokeLater(() -> {
                        lookupArticleForRow(table, editingRow);
                        if (pendingPoidsRealiseRefresh && (productionTable == null || !productionTable.isEditing())) {
                            pendingPoidsRealiseRefresh = false;
                            refreshPoidsRealiseFromProductionTable();
                        }
                    });
                }
                return stopped;
            }

            @Override
            public void cancelCellEditing() {
                lookupDoneForThisEdit = true;
                super.cancelCellEditing();
            }
        }

        private void lookupArticleForRow(JTable table, int viewRow) {
            if (table == null || viewRow < 0 || viewRow >= table.getRowCount()) {
                return;
            }

            int modelRow = table.convertRowIndexToModel(viewRow);
            Object value = table.getModel().getValueAt(modelRow, 0);
            String article = value != null ? value.toString().trim().toUpperCase() : "";

            @SuppressWarnings("unchecked")
            java.util.Map<Integer, String> manualArticleRows =
                    (java.util.Map<Integer, String>) table.getClientProperty("manualArticleRows");
            if (manualArticleRows == null) {
                manualArticleRows = new java.util.HashMap<>();
                table.putClientProperty("manualArticleRows", manualArticleRows);
            }

            if (article.isEmpty()) {
                manualArticleRows.remove(modelRow);
                clearProductionRow(table, (DefaultTableModel) table.getModel(), modelRow);
                return;
            }

            table.getModel().setValueAt(article, modelRow, 0);

            if ("ARRET".equals(article)) {
                ArretItem selected = chooseArret(table);
                if (selected != null) {
                    // Quando ARRET viene scelto dalla tendina, l'editor della colonna Produit
                    // puo essere gia aperto e rischia di riscrivere vuoto sopra la descrizione.
                    // Lo chiudiamo prima di valorizzare codice e prodotto.
                    if (table.isEditing()) {
                        TableCellEditor editor = table.getCellEditor();
                        if (editor != null) {
                            editor.cancelCellEditing();
                        }
                    }

                    table.getModel().setValueAt(selected.code, modelRow, 0);
                    table.getModel().setValueAt(selected.description, modelRow, 1);
                    clearPassageWeights((DefaultTableModel) table.getModel(), modelRow);
                    clearProductionRowAfterArret((DefaultTableModel) table.getModel(), modelRow);
                    table.repaint();
                    moveProductionFocusToColumn(table, modelRow, 10, false);
                } else {
                    table.getModel().setValueAt("", modelRow, 0);
                    table.getModel().setValueAt("", modelRow, 1);
                    clearPassageWeights((DefaultTableModel) table.getModel(), modelRow);
                }
                return;
            }

            if (article.startsWith("ARRET")) {
                String desc = findArretDescription(article);
                if (!desc.isEmpty()) {
                    table.getModel().setValueAt(desc, modelRow, 1);
                    clearProductionRowAfterArret((DefaultTableModel) table.getModel(), modelRow);
                    moveProductionFocusToColumn(table, modelRow, 10, false);
                    return;
                }
            }

            String manualArticle = manualArticleRows.get(modelRow);
            if (article.equals(manualArticle)) {
                // Riga già accettata come manuale: non rifare la query.
                return;
            }
            manualArticleRows.remove(modelRow);
            applyZbecValuesForArticle(table, modelRow, article);

            try {
                String produit = DbOracleManager.findPartDescriptionByCode(article);

                if (produit != null) {
                    produit = produit.replace("MESCOLA", "").trim();
                }

                table.getModel().setValueAt(produit != null ? produit : "", modelRow, 1);
              
                if (isProductionLookupReady((DefaultTableModel) table.getModel(), modelRow)) {
                    refreshProductionPassageFromRowOnly(table, modelRow);
                }
                if (produit == null || produit.trim().isEmpty()) {
                    askManualArticleRow(table, viewRow, modelRow, article, manualArticleRows);
                } else {
                    moveProductionFocusToSerie(table, modelRow);
                }
            } catch (Exception ex) {
                table.getModel().setValueAt("", modelRow, 1);
                System.err.println("Erreur recherche article " + article + ": " + ex.getMessage());
                askManualArticleRow(table, viewRow, modelRow, article, manualArticleRows);
            }
        }


        private void applyZbecValuesForArticle(JTable table, int modelRow, String article) {
            if (table == null || modelRow < 0 || article == null) return;
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            if (modelRow >= model.getRowCount() || model.getColumnCount() < PRODUCTION_COL_COUNT) return;

            String[] values = findZbecValuesByArticle(article);
            for (int c = 14; c <= 15; c++) {
                model.setValueAt("", modelRow, c);
            }
            if (values != null) {
                model.setValueAt(values[0], modelRow, 14); // ZBEC-INF
                model.setValueAt(combineZbecSupRetard(values[1], values[2]), modelRow, 15); // ZBEC-SUP ou RETARD
            }
        }

        private String combineZbecSupRetard(String zbecSup, String retard) {
            String sup = zbecSup == null ? "" : zbecSup.trim();
            String ret = retard == null ? "" : retard.trim();
            if (!sup.isEmpty() && !ret.isEmpty()) return sup + " / " + ret;
            return !sup.isEmpty() ? sup : ret;
        }

        private String[] findZbecValuesByArticle(String article) {
            String target = article == null ? "" : article.trim().toUpperCase(Locale.ROOT);
            if (target.isEmpty()) return null;

            File file = new File(getSelectedZbecDataFilePath());
            if (!file.exists() || !file.isFile()) return null;

            try (BufferedReader in = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                String line;
                boolean firstLine = true;
                while ((line = in.readLine()) != null) {
                    if (firstLine) {
                        firstLine = false;
                        if (line.toUpperCase(Locale.ROOT).startsWith("FORMULE;")) continue;
                    }
                    java.util.List<String> values = parseSemicolonCsvLine(line);
                    if (values.size() < 7) continue;
                    String rowArticle = values.get(1) == null ? "" : values.get(1).trim().toUpperCase(Locale.ROOT);
                    if (target.equals(rowArticle)) {
                        return new String[] { safeCsvValue(values, 4), safeCsvValue(values, 5), safeCsvValue(values, 6) };
                    }
                }
            } catch (Exception ex) {
                System.err.println("Erreur lecture ZBEC pour article " + target + ": " + ex.getMessage());
            }
            return null;
        }

        private String safeCsvValue(java.util.List<String> values, int index) {
            return index >= 0 && index < values.size() && values.get(index) != null ? values.get(index) : "";
        }

        private java.util.List<String> parseSemicolonCsvLine(String line) {
            java.util.List<String> out = new ArrayList<>();
            if (line == null) return out;
            StringBuilder cur = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (quoted) {
                    if (ch == '"') {
                        if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                            cur.append('"');
                            i++;
                        } else {
                            quoted = false;
                        }
                    } else {
                        cur.append(ch);
                    }
                } else {
                    if (ch == '"') quoted = true;
                    else if (ch == ';') {
                        out.add(cur.toString());
                        cur.setLength(0);
                    } else {
                        cur.append(ch);
                    }
                }
            }
            out.add(cur.toString());
            return out;
        }

        private int totalWidth(int[] widths) {
            int total = 0;
            if (widths != null) for (int w : widths) total += w;
            return total;
        }

        private void clearProductionRowAfterArret(DefaultTableModel model, int modelRow) {
            if (model == null || modelRow < 0 || modelRow >= model.getRowCount()) {
                return;
            }

            // V190.07: per ARRET non prepariamo N. Lot (26/) e non lasciamo valori automatici
            // nelle colonne della riga. Restano solo codice ARRET e descrizione.
            for (int c = 2; c < model.getColumnCount(); c++) {
                model.setValueAt("", modelRow, c);
            }
        }

        private void moveProductionFocusToSerie(JTable table, int modelRow) {
            moveProductionFocusToColumn(table, modelRow, 2, false);
        }

        private void moveProductionFocusToColumn(JTable table, int modelRow, int modelCol, boolean selectAll) {
            if (table == null || modelRow < 0 || modelCol < 0) {
                return;
            }

            int viewRow = table.convertRowIndexToView(modelRow);
            int viewCol = table.convertColumnIndexToView(modelCol);
            if (viewRow < 0 || viewCol < 0) {
                return;
            }

            SwingUtilities.invokeLater(() -> {
                table.requestFocusInWindow();
                table.changeSelection(viewRow, viewCol, false, false);
                table.editCellAt(viewRow, viewCol);

                Component editor = table.getEditorComponent();
                if (editor != null) {
                    editor.requestFocusInWindow();
                    if (selectAll && editor instanceof JTextField) {
                        ((JTextField) editor).selectAll();
                    } else {
                        positionProductionEditorCaret(table, viewRow, viewCol, editor);
                    }
                }
            });
        }

        private void positionProductionEditorCaret(JTable table, int viewRow, int viewCol, Component editor) {
            if (!(editor instanceof JTextField)) {
                return;
            }

            JTextField field = (JTextField) editor;
            String text = field.getText() == null ? "" : field.getText();
            int modelCol = table == null ? viewCol : table.convertColumnIndexToModel(viewCol);

            if (modelCol == 0 && "AL".equalsIgnoreCase(text.trim())) {
                // V190.07: nuova riga Article = AL, cursore a destra di AL senza selezione.
                field.setCaretPosition(text.length());
            } else if (modelCol == 2 && text.startsWith("26/")) {
                // V190.07: N. Lot = 26/, cursore subito dopo lo slash senza selezione.
                field.setCaretPosition(text.length());
            } else {
                field.selectAll();
            }
        }

        private void askManualArticleRow(JTable table, int viewRow, int modelRow, String article,
                                         java.util.Map<Integer, String> manualArticleRows) {
            blinkTableCell(table, viewRow, 0);

            int choice = JOptionPane.showConfirmDialog(
                    getDialogParentComponent(),
                    "L'article " + article + " n'existe pas dans la base.\n"
                            + "Voulez-vous écrire cette ligne manuellement ?",
                    "Article non trouvé",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            if (choice == JOptionPane.YES_OPTION) {
                manualArticleRows.put(modelRow, article);
                table.getModel().setValueAt(article, modelRow, 0);
                table.getModel().setValueAt("", modelRow, 1);

                SwingUtilities.invokeLater(() -> {
                    table.changeSelection(viewRow, 1, false, false);
                    table.editCellAt(viewRow, 1);
                    Component editor = table.getEditorComponent();
                    if (editor != null) {
                        editor.requestFocusInWindow();
                        if (editor instanceof JTextField) {
                            ((JTextField) editor).selectAll();
                        }
                    }
                });
            } else {
                manualArticleRows.remove(modelRow);
                table.getModel().setValueAt("", modelRow, 0);
                table.getModel().setValueAt("", modelRow, 1);
                SwingUtilities.invokeLater(() -> {
                    table.changeSelection(viewRow, 0, false, false);
                    table.editCellAt(viewRow, 0);
                    Component editor = table.getEditorComponent();
                    if (editor != null) {
                        editor.requestFocusInWindow();
                    }
                });
            }
        }

        private void blinkTableCell(JTable table, int row, int column) {
            if (table == null || row < 0 || column < 0) {
                return;
            }

            Rectangle rect = table.getCellRect(row, column, true);
            Timer blinkTimer = new Timer(180, null);
            blinkTimer.addActionListener(new ActionListener() {
                private int step = 0;

                @Override
                public void actionPerformed(ActionEvent e) {
                    if (step % 2 == 0) {
                        table.putClientProperty("blinkCell", new Point(row, column));
                    } else {
                        table.putClientProperty("blinkCell", null);
                    }
                    table.repaint(rect);
                    step++;

                    if (step >= 4) {
                        table.putClientProperty("blinkCell", null);
                        table.repaint(rect);
                        blinkTimer.stop();
                    }
                }
            });
            blinkTimer.setInitialDelay(0);
            blinkTimer.start();
        }

        private JTable createNoScrollGridTable(DefaultTableModel model) {
            JTable table = new JTable(model) {
                @Override
                public void paint(Graphics g) {
                    super.paint(g);
                    paintManualGridLines(this, g);
                }
            };
            table.setShowGrid(true);
            table.setShowHorizontalLines(true);
            table.setShowVerticalLines(true);
            table.setGridColor(TABLE_GRID_LIGHT);
            table.setIntercellSpacing(new Dimension(1, 1));
            table.setOpaque(true);
            table.setBackground(Color.WHITE);
            table.setFillsViewportHeight(false);
            return table;
        }

        private void paintManualGridLines(JTable table, Graphics g) {
            if (table == null || g == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(TABLE_GRID_LIGHT);
                g2.setStroke(new BasicStroke(0.8f));

                int tableWidth = Math.max(0, table.getWidth() - 1);
                int tableHeight = Math.max(0, table.getHeight() - 1);

               
                int y = 0;
                g2.drawLine(0, 0, tableWidth, 0);
                for (int r = 0; r < table.getRowCount(); r++) {
                    y += table.getRowHeight(r);
                    g2.drawLine(0, y, tableWidth, y);
                }
                if (y < tableHeight) {
                   
                    int rowH = table.getRowHeight() > 0 ? table.getRowHeight() : 31;
                    while (y < tableHeight) {
                        y += rowH;
                        g2.drawLine(0, Math.min(y, tableHeight), tableWidth, Math.min(y, tableHeight));
                    }
                }
                g2.drawLine(0, tableHeight, tableWidth, tableHeight);

              
                int x = 0;
                g2.drawLine(0, 0, 0, tableHeight);
                for (int c = 0; c < table.getColumnCount(); c++) {
                    x += table.getColumnModel().getColumn(c).getWidth();
                    g2.drawLine(x, 0, x, tableHeight);
                }
                g2.drawLine(tableWidth, 0, tableWidth, tableHeight);
            } finally {
                g2.dispose();
            }
        }





        private void buildBanburyEmbeddedSection() {
            try {
                embeddedBalanceFrame = new ProdMelBanbBalanceSupport.FormFrame(selectedBanbury);
                Container balanceContent = embeddedBalanceFrame.getContentPane();

                // Rimuove il contenuto dalla finestra bilancia e lo inserisce direttamente
                // nel form esterno, senza JPanel contenitore/bordo intermedio.
                embeddedBalanceFrame.setContentPane(new JPanel());
                embeddedBalanceFrame.dispose();

                hideBalanceTopRowsAndTestButton(balanceContent);
                setBackgroundRecursive(balanceContent, FORM_BACKGROUND);
                forceEmbeddedBalanceBanburySelection();

                if (balanceContent instanceof JComponent) {
                    JComponent jc = (JComponent) balanceContent;
                    jc.setOpaque(true);
                    jc.setBorder(null);
                }

                // V189.17: contentPane/bordo bilancia esteso fino all'estrema destra del form.
                balanceContent.setBounds(3, BALANCE_Y, 1660, BALANCE_PANEL_HEIGHT);
                add(balanceContent);
                embeddedBalanceContent = balanceContent;
            } catch (Exception ex) {
                JTextArea error = new JTextArea("Erreur chargement Balance Banbury:\n" + getFullErrorMessageDeep(ex));
                error.setEditable(false);
                error.setForeground(Color.RED.darker());
                error.setBackground(FORM_BACKGROUND);
                error.setBounds(3, BALANCE_Y, 1660, BALANCE_PANEL_HEIGHT);
                add(error);
            }
        }

        private void setBackgroundRecursive(Component component, Color color) {
            if (component == null || color == null) return;

            if (component instanceof JComponent) {
                JComponent jc = (JComponent) component;

                // V190.04: i campi dinamici della bilancia e i ComboBox devono restare bianchi.
                // Eccezione: POIDS NET conserva il colore azzurro/ciano di evidenza.
                if (Boolean.TRUE.equals(jc.getClientProperty("KEEP_POIDS_NET_CYAN"))) {
                    jc.setBackground(ProdMelBanbBalanceSupport.C_NET_BG);
                    jc.setOpaque(true);
                } else if (Boolean.TRUE.equals(jc.getClientProperty("KEEP_FORM_BACKGROUND"))) {
                    // V194.13: il campo visuale Operateur Balance deve restare
                    // dello stesso grigio del form, anche dopo setBackgroundRecursive().
                    jc.setBackground(FORM_BACKGROUND);
                    jc.setOpaque(true);
                } else if (jc instanceof JTextField || jc instanceof JTextArea || jc instanceof JComboBox) {
                    jc.setBackground(Color.WHITE);
                    jc.setOpaque(true);
                    if (jc instanceof JComboBox) {
                        forceComboWhite((JComboBox<?>) jc);
                    }
                } else {
                    jc.setBackground(color);
                }

                if (jc instanceof JPanel) {
                    jc.setOpaque(true);
                }
            } else {
                component.setBackground(color);
            }

            if (component instanceof Container) {
                for (Component child : ((Container) component).getComponents()) {
                    setBackgroundRecursive(child, color);
                }
            }
        }

        private void forceComboWhite(JComboBox<?> combo) {
            if (combo == null) return;
            combo.setBackground(Color.WHITE);
            combo.setOpaque(true);
            Component editor = combo.isEditable() && combo.getEditor() != null
                    ? combo.getEditor().getEditorComponent()
                    : null;
            if (editor != null) {
                editor.setBackground(Color.WHITE);
                if (editor instanceof JComponent) ((JComponent) editor).setOpaque(true);
            }
            ListCellRenderer<?> originalRenderer = combo.getRenderer();
            if (!(originalRenderer instanceof WhiteComboRenderer)) {
                combo.setRenderer(new WhiteComboRenderer(originalRenderer));
            }
        }

        private class WhiteComboRenderer extends DefaultListCellRenderer {
            private final ListCellRenderer originalRenderer;
            WhiteComboRenderer(ListCellRenderer originalRenderer) {
                this.originalRenderer = originalRenderer;
            }
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                Component c;
                if (originalRenderer != null && originalRenderer != this) {
                    c = originalRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                } else {
                    c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                }
                if (!isSelected) {
                    c.setBackground(Color.WHITE);
                    c.setForeground(Color.BLACK);
                }
                if (c instanceof JComponent) ((JComponent) c).setOpaque(true);
                return c;
            }
        }

        private void forceEmbeddedBalanceBanburySelection() {
            if (embeddedBalanceFrame == null) return;
            String code = getSelectedBanburyProvenance();
            String name = selectedBanbury == 2 ? "BANBURY 2" : "BANBURY 1";
            String poste = selectedBanbury == 2 ? "POSTE 2" : "POSTE 1";

            try {
                java.lang.reflect.Method m = ProdMelBanbBalanceSupport.FormFrame.class.getDeclaredMethod("loadAppConfig", String.class);
                m.setAccessible(true);
                m.invoke(embeddedBalanceFrame, poste);
            } catch (Exception ignored) {
            }

            setBalanceComboSelectedItem("cbProvenance", code);
            setBalanceComboSelectedItem("cbDestination", code);
            setBalanceTextField("tfProvName", name);
            setBalanceTextField("tfDestName", name);

            try {
                java.lang.reflect.Method m = ProdMelBanbBalanceSupport.FormFrame.class.getDeclaredMethod("refreshPosteLabel");
                m.setAccessible(true);
                m.invoke(embeddedBalanceFrame);
            } catch (Exception ignored) {
            }
        }

        private void setBalanceComboSelectedItem(String fieldName, String value) {
            if (embeddedBalanceFrame == null) return;
            try {
                java.lang.reflect.Field f = ProdMelBanbBalanceSupport.FormFrame.class.getDeclaredField(fieldName);
                f.setAccessible(true);
                Object obj = f.get(embeddedBalanceFrame);
                if (obj instanceof JComboBox) {
                    JComboBox<?> combo = (JComboBox<?>) obj;
                    combo.setSelectedItem(value == null ? "" : value);
                    if ("cbProvenance".equals(fieldName)) {
                        combo.setEnabled(false);
                        combo.setFocusable(false);
                        combo.setToolTipText("Provenance bloquée par le choix Banbury au démarrage: " + (value == null ? "" : value));
                    }
                }
            } catch (Exception ex) {
                System.err.println("Impossible de définir " + fieldName + " Balance: " + ex.getMessage());
            }
        }

        private void openEmbeddedBalanceSetup() {
            if (embeddedBalanceFrame != null) {
                embeddedBalanceFrame.refreshProvenanceForSetupEntry();
                embeddedBalanceFrame.openSetupDialog();
            } else {
                JOptionPane.showMessageDialog(getDialogParentComponent(), "Balance Banbury non initialisée.", "Setup Bilancia", JOptionPane.WARNING_MESSAGE);
            }
        }

        private void resetEmbeddedBalance() {
            if (embeddedBalanceFrame != null) {
                embeddedBalanceFrame.doReset();
            }
        }

        private void hideBalanceTopRowsAndTestButton(Container root) {
            if (root == null) return;
            hideByReflection("tfDate");
            hideByReflection("tfTournee");
            hideByReflection("tfOperateurCode");
            hideByReflection("tfOperateurName");
            hideByReflection("lblTourneeDesc");
            hideTextComponents(root, "Date", "Tournée", "Opérateur", "Test");
        }

        private void hideByReflection(String fieldName) {
            try {
                java.lang.reflect.Field f = ProdMelBanbBalanceSupport.FormFrame.class.getDeclaredField(fieldName);
                f.setAccessible(true);
                Object obj = f.get(embeddedBalanceFrame);
                if (obj instanceof Component) ((Component) obj).setVisible(false);
            } catch (Exception ignored) {
            }
        }

        private void hideTextComponents(Container root, String... texts) {
            for (Component c : root.getComponents()) {
                String text = null;
                if (c instanceof AbstractButton) text = ((AbstractButton) c).getText();
                else if (c instanceof JLabel) text = ((JLabel) c).getText();
                if (text != null) {
                    String clean = text.replaceAll("<[^>]*>", "").trim();
                    for (String t : texts) {
                        if (clean.equalsIgnoreCase(t) || clean.toUpperCase(Locale.ROOT).contains(t.toUpperCase(Locale.ROOT))) {
                            c.setVisible(false);
                        }
                    }
                }
                if (c instanceof Container) hideTextComponents((Container) c, texts);
            }
        }

        private void installBalancePersonnelDoubleClick(final JTextField codeField, final JTextField nameField) {
            if (nameField == null) return;
            nameField.setToolTipText("Double-clic: sélectionner cet opérateur pour la Balance Banbury");
            nameField.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() >= 2) {
                        setBalanceOperateurFromPersonnel(codeField, nameField);
                    }
                }
            });
        }

        private void setBalanceOperateurFromPersonnel(JTextField codeField, JTextField nameField) {
            String code = codeField == null || codeField.getText() == null ? "" : codeField.getText().trim();
            String name = nameField == null || nameField.getText() == null ? "" : nameField.getText().trim();

            // V194.12: nel campo dinamico Operateur Balance deve apparire il NOME,
            // non il codice. Se il nome non e valorizzato, prova a risolverlo dal codice.
            String displayName = name;
            if ((displayName.isEmpty() || displayName.equalsIgnoreCase(code)) && !code.isEmpty()) {
                try {
                    String nomDb = DbOracleManager.findUserNameByCode(code);
                    if (nomDb != null && !nomDb.trim().isEmpty()) {
                        displayName = nomDb.trim();
                        if (nameField != null) nameField.setText(displayName);
                    }
                } catch (Exception ignored) {
                }
            }

            if (code.isEmpty() && displayName.isEmpty()) return;
            if (selectedBalancePersonnelNameField != null) selectedBalancePersonnelNameField.setBackground(Color.WHITE);
            selectedBalancePersonnelNameField = nameField;
            if (nameField != null) nameField.setBackground(new Color(255, 80, 80));
            setBalanceTextField("tfOperateurCode", code);
            setBalanceTextField("tfOperateurName", displayName);
        }

        private void resetBalanceOperateurDisplay() {
            // V194.29: il campo Operateur Balance e' solo uno specchio dell'operatore
            // Personnel selezionato con il doppio-clic (setBalanceOperateurFromPersonnel).
            // Quando RESET RAPPORT azzera l'operatore in Personnel, deve azzerare anche
            // questo specchio, pur lasciando intatto il resto della sezione BALANCE.
            setBalanceTextField("tfOperateurCode", "");
            setBalanceTextField("tfOperateurName", "");
            if (selectedBalancePersonnelNameField != null) {
                selectedBalancePersonnelNameField.setBackground(Color.WHITE);
                selectedBalancePersonnelNameField = null;
            }
        }

        private void setBalanceTextField(String fieldName, String value) {
            if (embeddedBalanceFrame == null) return;
            try {
                java.lang.reflect.Field f = ProdMelBanbBalanceSupport.FormFrame.class.getDeclaredField(fieldName);
                f.setAccessible(true);
                Object obj = f.get(embeddedBalanceFrame);
                if (obj instanceof JTextField) ((JTextField) obj).setText(value == null ? "" : value);
                if ("tfOperateurName".equals(fieldName)) {
                    try {
                        java.lang.reflect.Field displayField = ProdMelBanbBalanceSupport.FormFrame.class.getDeclaredField("tfOperateurBalanceDisplay");
                        displayField.setAccessible(true);
                        Object displayObj = displayField.get(embeddedBalanceFrame);
                        if (displayObj instanceof JTextField) {
                            ((JTextField) displayObj).setText(value == null ? "" : value);
                        }
                    } catch (Exception ignoredDisplay) {
                    }
                }
            } catch (Exception ignored) {
            }
        }

        private void buildProductionTable() {
            String[] columns = {"Article", "Produit", "N° Lot", "Nombre de\nMelanges", "Repass", "Couche", "Passage", "Poids", "Couleur", "N° Série", "Remarq.", "de", "à", "Temps", "ZBEC-INF", "ZBEC-SUP ou\nRETARD"};

            Object[][] data = new Object[10][columns.length];
            for (int r = 0; r < data.length; r++) {
                for (int c = 0; c < columns.length; c++) {
                    data[r][c] = "";
                }
            }

            productionModel = new DefaultTableModel(data, columns) {
                @Override public boolean isCellEditable(int row, int column) {
                    return column != 5 && column != 6 && column != 7 && column != 13 && column != 14 && column != 15;
                }
            };

            JTable table = createNoScrollGridTable(productionModel);
            productionTable = table;
            table.setFont(FONT_NORMAL);
            table.setRowHeight(31);
            table.setShowGrid(true);
            table.setShowHorizontalLines(true);
            table.setShowVerticalLines(true);
            table.setGridColor(TABLE_GRID_LIGHT);
            table.setIntercellSpacing(new Dimension(1, 1));
            table.setBorder(new LineBorder(TABLE_GRID_LIGHT, 1));
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            table.setSurrendersFocusOnKeystroke(true);

            
            installEnterConfirmAndMove(table);
            table.putClientProperty("terminateEditOnFocusLost", Boolean.FALSE);
            table.setCellSelectionEnabled(true);
            installArticleLookup(table);
            installArticleEditActivation(table);
            installNombreMelangesLookup(table);
            installRepassNumericEditor(table);
            installSerieUppercaseEditor(table);
            installRemarqueUppercaseEditor(table);
            installCouleurCombo(table);
            installCoucheDoubleClickToggle(table, productionModel);
            installProductionTimeCalculation(table);
            installProductionTimeEditors(table);
            installProductionDbLookup(table, productionModel);
            installPoidsRefreshOnProductionConfirm(table, productionModel);

            int[] widths = {85, 303, 98, 85, 70, 77, 98, 104, 87, 98, 131, 70, 70, 79, 85, 130};
            for (int i = 0; i < widths.length; i++) {
                table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            }

            DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                               boolean hasFocus, int row, int column) {
                    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    setHorizontalAlignment(SwingConstants.CENTER);
                    c.setFont(((column == 6 || column == 7) || column == 0 || column == 5 || column == 8 || column >= 14) ? FONT_BOLD : FONT_NORMAL);
                    setHorizontalAlignment(column == 10 ? SwingConstants.LEFT : SwingConstants.CENTER);

                    Point blinkCell = (Point) table.getClientProperty("blinkCell");
                    boolean blinkThisCell = blinkCell != null && blinkCell.x == row && blinkCell.y == column;

                    if (blinkThisCell) {
                        c.setBackground(new Color(0xFF, 0x66, 0x66));
                        c.setForeground(Color.BLACK);
                        setBorder(new LineBorder(Color.RED.darker(), 2));
                    } else if (isSelected) {
                        c.setBackground(TABLE_SELECTION_BG);
                        c.setForeground(Color.BLACK);
                        setBorder(hasFocus ? new LineBorder(TABLE_SELECTION_BORDER, 2) : new LineBorder(TABLE_SELECTION_BORDER, 1));
                    } else {
                        c.setForeground(Color.BLACK);
                        c.setBackground(row % 2 == 0 ? TABLE_ROW_WHITE : TABLE_ROW_BLUE);
                        setBorder(new LineBorder(TABLE_GRID_LIGHT, 1));
                    }
                    return c;
                }
            };
            for (int i = 0; i < table.getColumnCount(); i++) {
                table.getColumnModel().getColumn(i).setCellRenderer(renderer);
            }
            installAutoWrapRows(table, 31);

            table.getTableHeader().setReorderingAllowed(false);
            GroupedProductionHeader header = new GroupedProductionHeader(table.getColumnModel());
            header.setFont(FONT_NORMAL);
            table.setTableHeader(header);

            header.setBounds(3, 472 + PRODUCTION_SHIFT_Y, totalWidth(widths), 72);
            add(header);

            table.setBounds(3, 544 + PRODUCTION_SHIFT_Y, totalWidth(widths), table.getRowHeight() * table.getRowCount());
            add(table);

            JPanel totalPanel = new JPanel(null);
            productionTotalPanel = totalPanel;
            productionFooterBaseY = 544 + PRODUCTION_SHIFT_Y + table.getHeight();
            totalPanel.setBackground(FORM_BACKGROUND);
            totalPanel.setBorder(new LineBorder(TABLE_GRID_LIGHT, 1));
            totalPanel.setBounds(3, productionFooterBaseY, totalWidth(widths), 28);
            add(totalPanel);

            int xNombreMelanges = widths[0] + widths[1] + widths[2];
            int xTemps = 0;
            for (int i = 0; i < 13; i++) {
                xTemps += widths[i];
            }

            int xPoids = widths[0] + widths[1] + widths[2] + widths[3] + widths[4] + widths[5] + widths[6];

            JLabel lblTotalMel = createFooterLabel("Total Mel:", SwingConstants.RIGHT, FONT_NORMAL);
            lblTotalMel.setBounds(xNombreMelanges - 95, 3, 95, 22);
            totalPanel.add(lblTotalMel);

            JTextField txtTotalMelanges = createFooterField();
            txtTotalMelanges.setBounds(xNombreMelanges, 3, widths[3], 22);
            totalPanel.add(txtTotalMelanges);

            JLabel lblTotalKg = createFooterLabel("Total Kg:", SwingConstants.RIGHT, FONT_NORMAL);
            lblTotalKg.setBounds(xPoids - 95, 3, 95, 22);
            totalPanel.add(lblTotalKg);

            JTextField txtTotalKg = createFooterField();
            txtTotalKg.setBounds(xPoids, 3, 130, 22);
            totalPanel.add(txtTotalKg);

            JLabel lblTotalMinutes = createFooterLabel("TOTAL minutes :", SwingConstants.RIGHT, FONT_NORMAL);
            lblTotalMinutes.setBounds(xTemps - 145, 3, 145, 22);
            totalPanel.add(lblTotalMinutes);

            JTextField txtTotalTemps = createFooterField();
            txtTotalTemps.setBounds(xTemps, 3, widths[13], 22);
            totalPanel.add(txtTotalTemps);

            installDynamicProductionRowsAndTotals(table, productionModel, txtTotalMelanges, txtTotalTemps, txtTotalKg);

            productionBottomPanel = new JPanel(null);
            productionBottomPanel.setBackground(FORM_BACKGROUND);
            productionBottomPanel.setBounds(3, productionFooterBaseY + 34, totalWidth(widths), 38);
            add(productionBottomPanel);

            JLabel lblDechets = createFooterLabel("Déchets au mélangeur :", SwingConstants.LEFT, FONT_NORMAL);
            lblDechets.setBounds(36, 4, 210, 24);
            productionBottomPanel.add(lblDechets);
            txtDechetsMelangeur = createUnderlineField();
            txtDechetsMelangeur.setBounds(250, 4, 210, 24);
            txtDechetsMelangeur.setEditable(false);
            txtDechetsMelangeur.setFocusable(false);
            txtDechetsMelangeur.setToolTipText("Somme automatique DB: provenance Banbury sélectionné, turno actuel, destination CDECHET");
            productionBottomPanel.add(txtDechetsMelangeur);
            JLabel lblDechetsUnit = createFooterLabel("kg", SwingConstants.LEFT, FONT_NORMAL);
            lblDechetsUnit.setBounds(466, 4, 30, 24);
            productionBottomPanel.add(lblDechetsUnit);

            JLabel lblHuile = createFooterLabel("Consommation huile :", SwingConstants.LEFT, FONT_NORMAL);
            lblHuile.setBounds(535, 4, 190, 24);
            productionBottomPanel.add(lblHuile);
            txtConsommationHuile = createUnderlineField();
            txtConsommationHuile.setBounds(730, 4, 400, 24);
            txtConsommationHuile.setEditable(false);
            txtConsommationHuile.setFocusable(false);
            txtConsommationHuile.setToolTipText("Double-clic pour modifier manuellement");
            txtConsommationHuile.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() >= 2) {
                        txtConsommationHuile.setEditable(true);
                        txtConsommationHuile.setFocusable(true);
                        txtConsommationHuile.setBackground(new Color(255, 255, 210));
                        txtConsommationHuile.requestFocusInWindow();
                        txtConsommationHuile.selectAll();
                    }
                }
            });
            productionBottomPanel.add(txtConsommationHuile);
            JLabel lblHuileUnit = createFooterLabel("L", SwingConstants.LEFT, FONT_NORMAL);
            lblHuileUnit.setBounds(1138, 4, 30, 24);
            productionBottomPanel.add(lblHuileUnit);

            refreshDechetsMelangeurFromDb();
        }


        private void refreshDechetsMelangeurFromDb() {
            if (txtDechetsMelangeur == null) {
                return;
            }

            txtDechetsMelangeur.setText("...");
            txtDechetsMelangeur.setToolTipText("Calcolo in corso: provenance " + getSelectedBanburyProvenance()
                    + ", turno " + getCurrentShiftWindow().displayName + ", destination CDECHET");

            new SwingWorker<BigDecimal, Void>() {
                private String error = null;

                @Override
                protected BigDecimal doInBackground() {
                    CurrentShiftWindow shift = getCurrentShiftWindow();
                    String sql = "SELECT NVL(SUM(NVL(NET_P, 0)), 0) AS TOTAL_KG "
                            + "FROM DBM_PESAGE_NEW "
                            + "WHERE UPPER(TRIM(PROVENANCE_ID)) = ? "
                            + "AND UPPER(TRIM(DESTINATION_ID)) = 'CDECHET' "
                            + "AND TO_DATE(DATE2, 'DD/MM/RR HH24:MI') >= ? "
                            + "AND TO_DATE(DATE2, 'DD/MM/RR HH24:MI') < ?";
                    try (Connection cn = DbOracleManager.getConnection();
                         PreparedStatement ps = cn.prepareStatement(sql)) {
                        ps.setString(1, getSelectedBanburyProvenance());
                        ps.setTimestamp(2, Timestamp.valueOf(shift.start));
                        ps.setTimestamp(3, Timestamp.valueOf(shift.end));
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                return readBigDecimal(rs, "TOTAL_KG");
                            }
                        }
                    } catch (Exception ex) {
                        error = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                        System.err.println("Erreur chargement déchets mélangeur: " + error);
                    }
                    return BigDecimal.ZERO;
                }

                @Override
                protected void done() {
                    try {
                        BigDecimal total = get();
                        txtDechetsMelangeur.setText(formatKgNumberOnly(total));
                        txtDechetsMelangeur.setToolTipText(error == null || error.trim().isEmpty()
                                ? "Somme automatique DB: provenance " + getSelectedBanburyProvenance()
                                + ", turno actuel, destination CDECHET"
                                : "Erreur DB déchets: " + error);
                    } catch (Exception ex) {
                        txtDechetsMelangeur.setText("0");
                        txtDechetsMelangeur.setToolTipText("Erreur DB déchets: " + ex.getMessage());
                    }
                }
            }.execute();
        }

        private String formatKgNumberOnly(BigDecimal value) {
            if (value == null) {
                return "0";
            }
            BigDecimal normalized = value.stripTrailingZeros();
            if (normalized.scale() < 0) {
                normalized = normalized.setScale(0);
            }
            return normalized.toPlainString().replace('.', ',');
        }


        private void buildAdditionalInfoSection() {
            int beforeComponentCount = getComponentCount();
            int y = additionalInfoBaseYStart;

            addLabel("Poids réalisé", 35, y, 170, 22, FONT_BOLD, SwingConstants.LEFT);
            JButton btnRefreshPoids = new JButton("Actualiser Poids V9.2");
            btnRefreshPoids.setFont(new Font("Arial", Font.BOLD, 11));
            btnRefreshPoids.setBounds(190, y - 2, 145, 24);
            btnRefreshPoids.addActionListener(e -> refreshPoidsRealiseNowFromUi());
            add(btnRefreshPoids);
            lblPoidsVersion = addLabel(KG_VERSION, 345, y - 2, 360, 22, new Font("Arial", Font.BOLD, 11), SwingConstants.LEFT);
            lblPoidsStatus = addLabel("", 710, y - 2, 360, 22, new Font("Arial", Font.PLAIN, 11), SwingConstants.LEFT);
            addLabel("Palette 2° passage", 725, y - 2, 170, 18, new Font("Arial", Font.PLAIN, 11), SwingConstants.LEFT);
            addLabel("Mélanges Mère", 725, y + 14, 170, 18, new Font("Arial", Font.PLAIN, 11), SwingConstants.LEFT);

            String[] cols = {"Type de produit", "COUCHE", "N° Lot", "N° Série", "Couleur", "Palette N°", "Palette Poids", "Total"};
            Object[][] rows = new Object[4][cols.length];
            for (int r = 0; r < rows.length; r++) {
                for (int c = 0; c < cols.length; c++) rows[r][c] = "";
            }

            poidsModel = new DefaultTableModel(rows, cols) {
                @Override public boolean isCellEditable(int row, int column) { return true; }
            };
            poidsTable = createNoScrollGridTable(poidsModel);
            poidsTable.setFont(FONT_NORMAL);
            poidsTable.setRowHeight(31);
            poidsTable.setShowGrid(true);
            poidsTable.setShowHorizontalLines(true);
            poidsTable.setShowVerticalLines(true);
            poidsTable.setGridColor(TABLE_GRID_LIGHT);
            poidsTable.setIntercellSpacing(new Dimension(1, 1));
            poidsTable.setCellSelectionEnabled(true);
            poidsTable.setSurrendersFocusOnKeystroke(true);
            poidsTable.putClientProperty("terminateEditOnFocusLost", Boolean.FALSE);
            poidsTable.setBorder(new LineBorder(TABLE_GRID_LIGHT, 1));
            poidsTable.getTableHeader().setReorderingAllowed(false);
            poidsTable.getTableHeader().setFont(FONT_NORMAL);
            poidsTable.getTableHeader().setBackground(TABLE_HEADER_BG);
            poidsTable.getTableHeader().setBorder(new LineBorder(TABLE_GRID_LIGHT, 1));
            int[] w = {330, 80, 130, 115, 100, 220, 220, 70};
            for (int i = 0; i < w.length; i++) poidsTable.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
            DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                               boolean hasFocus, int row, int column) {
                    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    setHorizontalAlignment(SwingConstants.CENTER);
                    c.setFont(FONT_NORMAL);
                    c.setBackground(isSelected ? TABLE_SELECTION_BG : (row % 2 == 0 ? TABLE_ROW_WHITE : TABLE_ROW_BLUE));
                    c.setForeground(Color.BLACK);
                    setBorder(new LineBorder(isSelected && hasFocus ? TABLE_SELECTION_BORDER : TABLE_GRID_LIGHT, isSelected && hasFocus ? 2 : 1));
                    return c;
                }
            };
            for (int i = 0; i < poidsTable.getColumnCount(); i++) poidsTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
            installAutoWrapRows(poidsTable, 31);

            JTableHeader poidsHeader = poidsTable.getTableHeader();
            poidsHeader.setBounds(3, y + 35, 1285, 28);
            add(poidsHeader);
            poidsTable.setBounds(3, y + 63, 1285, poidsTable.getRowHeight() * poidsTable.getRowCount());
            add(poidsTable);
            installDynamicPoidsRows(poidsTable, poidsModel);
            // V193.14: non avviare un ricalcolo DB durante la costruzione del form.
            // Il caricamento del file salvato avviene subito dopo e deve restare prioritario.
            // L'aggiornamento automatico avverra solo se non esiste un salvataggio valido,
            // oppure manualmente con il pulsante Actualiser Poids.

            int infoY = y + 63 + poidsTable.getHeight() + 15;
            arretProductionDetails.clear();

            lblArretProductionInfo = addLabel(
                    "Il n'y a eu aucun arrêt de production",
                    55, infoY, 700, 28, new Font("Arial", Font.BOLD, 17), SwingConstants.LEFT
            );

            JCheckBox cbTemps = createSmallCheckBox(true);
            cbTemps.setBounds(28, infoY + 38, 22, 22);
            addArretProductionDetail(cbTemps);
            JLabel lblTempsArret = addLabel("Combien de temps :", 55, infoY + 38, 250, 22, new Font("Arial", Font.PLAIN, 13), SwingConstants.LEFT);
            addArretProductionDetail(lblTempsArret);
            txtTempsArretAuto = createGridField("0H00", SwingConstants.CENTER, false);
            txtTempsArretAuto.setBounds(480, infoY + 38, 95, 22);
            addArretProductionDetail(txtTempsArretAuto);

            JCheckBox cbRaisons = createSmallCheckBox(true);
            cbRaisons.setBounds(28, infoY + 70, 22, 22);
            addArretProductionDetail(cbRaisons);
            JLabel lblRaisons = addLabel("Raisons :", 55, infoY + 70, 120, 22, new Font("Arial", Font.PLAIN, 13), SwingConstants.LEFT);
            addArretProductionDetail(lblRaisons);
            txtRaisonsArretAuto.clear();
            for (int i = 0; i < 3; i++) {
                JTextField txtRaison = createGridField("", SwingConstants.LEFT, true);
                txtRaison.setBounds(210, infoY + 70 + (i * 24), 530, 22);
                addArretProductionDetail(txtRaison);
                txtRaisonsArretAuto.add(txtRaison);
            }

            JCheckBox cbIntervenu = createSmallCheckBox(false);
            cbIntervenu.setBounds(28, infoY + 142, 22, 22);
            addArretProductionDetail(cbIntervenu);
            JLabel lblIntervenu = addLabel("Qui est intervenu pour la réparation :", 55, infoY + 142, 320, 22, new Font("Arial", Font.PLAIN, 13), SwingConstants.LEFT);
            addArretProductionDetail(lblIntervenu);

            JComboBox<String> cbMaintenance1 = createMaintenanceCombo();
            cbMaintenance1.setBounds(380, infoY + 142, 260, 24);
            addArretProductionDetail(cbMaintenance1);

            JComboBox<String> cbMaintenance2 = createMaintenanceCombo();
            cbMaintenance2.setBounds(660, infoY + 142, 260, 24);
            addArretProductionDetail(cbMaintenance2);

            JComboBox<String> cbMaintenance3 = createMaintenanceCombo();
            cbMaintenance3.setBounds(940, infoY + 142, 260, 24);
            addArretProductionDetail(cbMaintenance3);

            JCheckBox cbTravaux = createSmallCheckBox(false);
            cbTravaux.setBounds(28, infoY + 184, 22, 22);
            addArretProductionDetail(cbTravaux);
            JLabel lblTravaux = addLabel("Travaux effectués pendant les arrêts :", 55, infoY + 184, 330, 22, new Font("Arial", Font.PLAIN, 13), SwingConstants.LEFT);
            addArretProductionDetail(lblTravaux);
            JTextArea txtTravaux = createGridArea();
            JScrollPane spTravaux = new JScrollPane(txtTravaux);
            spTravaux.setBounds(55, infoY + 212, 1195, 60);
            spTravaux.setBorder(new LineBorder(GRID, 1));
            addArretProductionDetail(spTravaux);

            int dotY = infoY + 280;
            JCheckBox cbDotation = createSmallCheckBox(true);
            cbDotation.setBounds(28, dotY, 22, 22);
            add(cbDotation);
            addLabel("La dotation des machines est", 55, dotY, 350, 22, new Font("Arial", Font.PLAIN, 13), SwingConstants.LEFT);
            addCheckBoxChoice("COMPLETE", 500, dotY, false);
            addCheckBoxChoice("INCOMPLETE", 760, dotY, false);
            addCheckBoxChoice("ABIMEE", 1010, dotY, false);

            JCheckBox cbRemarques = createSmallCheckBox(true);
            cbRemarques.setBounds(28, dotY + 10, 22, 22);
            add(cbRemarques);
            addLabel("Remarques :", 55, dotY + 40, 150, 22, new Font("Arial", Font.PLAIN, 13), SwingConstants.LEFT);
            JComboBox<String> cbRemarquesListe = createAnomalieCombo();
            cbRemarquesListe.setBounds(55, dotY + 68, 1195, 28);
            add(cbRemarquesListe);

            // V193.38: REMARQUES en saisie libre sur 2 lignes maximum.
            JTextArea txtRemarques = createLimitedGridArea(2);
            txtRemarquesReport = txtRemarques;
            JScrollPane spRemarques = new JScrollPane(txtRemarques,
                    JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            spRemarques.setBounds(55, dotY + 102, 1195, 46);
            spRemarques.setBorder(new LineBorder(GRID, 1));
            add(spRemarques);

            cbRemarquesListe.addActionListener(e -> {
                Object selected = cbRemarquesListe.getSelectedItem();
                String value = selected == null ? "" : selected.toString().trim();
                if (!value.isEmpty()) {
                    txtRemarques.setText(value);
                }
            });

            JCheckBox cbPreparations = createSmallCheckBox(true);
            cbPreparations.setBounds(28, dotY + 170, 22, 22);
            add(cbPreparations);
            addLabel("Préparations :", 55, dotY + 166, 150, 22, new Font("Arial", Font.PLAIN, 13), SwingConstants.LEFT);
            // V193.38: PREPARATIONS en saisie libre sur 5 lignes maximum.
            JTextArea txtPreparations = createLimitedGridArea(5);
            txtPreparationsReport = txtPreparations;
            JScrollPane spPreparations = new JScrollPane(txtPreparations,
                    JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            spPreparations.setBounds(55, dotY + 194, 1195, 106);
            spPreparations.setBorder(new LineBorder(GRID, 1));
            add(spPreparations);

            addLabel("LE CHEF SECTEUR", 955, dotY + 318, 260, 28, new Font("Arial", Font.BOLD, 18), SwingConstants.LEFT);

            txtChefSecteurName = createOperateurNameField("");
            txtChefSecteurName.setBounds(955, dotY + 350, 330, PERSONNEL_ROW_H);
            txtChefSecteurName.setEditable(false);
            txtChefSecteurName.setFocusable(false);
            add(txtChefSecteurName);

            syncResponsableToChefSecteur();
            refreshArretSummaryFromProductionTable();

            additionalInfoComponents.clear();
            additionalInfoBaseY.clear();
            for (int i = beforeComponentCount; i < getComponentCount(); i++) {
                Component component = getComponent(i);
                additionalInfoComponents.add(component);
                additionalInfoBaseY.put(component, component.getY());
            }
            relayoutGrowingTables();
        }


        private void refreshPoidsRealiseNowFromUi() {
            if (productionTable != null && productionTable.isEditing()) {
                TableCellEditor editor = productionTable.getCellEditor();
                if (editor != null) {
                    editor.stopCellEditing();
                }
            }
            refreshPoidsRealiseFromProductionTable();
        }

        private void installPoidsRefreshOnProductionConfirm(JTable table, DefaultTableModel model) {
            if (table == null || model == null) {
                return;
            }

            model.addTableModelListener(e -> {
                int row = e.getFirstRow();
                int col = e.getColumn();
                if (row < 0 || row >= model.getRowCount()) {
                    return;
                }

                // Dopo conferma con TAB/INVIO o perdita focus, aggiorna Poids réalisé
                // solo quando cambiano i campi necessari: Article, Produit, N° Lot, Nombre,
                // Couche o N° Série.
                if (loadingShiftState) {
                    return;
                }
                if (col == 0 || col == 1 || col == 2 || col == 3 || col == 4 || col == 7 || col == 8) {
                    if (table.isEditing()) {
                        pendingPoidsRealiseRefresh = true;
                        return;
                    }
                    SwingUtilities.invokeLater(() -> refreshPoidsRealiseFromProductionTable());
                }
            });
        }

        private void refreshPoidsRealiseFromProductionTable() {
            // V193.14: durante il caricamento da file di persistenza non deve partire nessun
            // ricalcolo DB di POIDS REALISE. Il salvataggio deve ripristinare esattamente
            // le righe salvate, senza che un worker asincrono le sovrascriva.
            if (loadingShiftState || resettingForm || productionModel == null || poidsModel == null) {
                return;
            }

            // V193.19: non aggiornare POIDS REALISE mentre l'utente sta modificando una cella
            // della Production réalisée. In particolare, l'editor ARTICLE deve restare aperto
            // fino a INVIO/TAB o conferma esplicita; nessun refresh asincrono deve chiuderlo.
            if (productionTable != null && productionTable.isEditing()) {
                pendingPoidsRealiseRefresh = true;
                return;
            }

            if (refreshingPoidsRealise) {
                pendingPoidsRealiseRefresh = true;
                return;
            }

            java.util.List<PoidsRequest> requests = buildPoidsRequestsFromProductionTable();
            if (lblPoidsStatus != null) {
                lblPoidsStatus.setText("V7 Recherche CL: " + requests.size() + " ligne(s)");
            }
            if (requests.isEmpty()) {
                clearPoidsModel();
                if (lblPoidsStatus != null) lblPoidsStatus.setText("V7 Aucune ligne a chercher");
                return;
            }

            refreshingPoidsRealise = true;
            pendingPoidsRealiseRefresh = false;

            new SwingWorker<java.util.List<PoidsRealiseRow>, Void>() {
                @Override
                protected java.util.List<PoidsRealiseRow> doInBackground() {
                    return loadPoidsRealiseRowsSequential(requests);
                }

                @Override
                protected void done() {
                    try {
                        java.util.List<PoidsRealiseRow> loaded = get();
                        if (productionTable != null && productionTable.isEditing()) {
                            // V193.19: un worker lanciato prima non deve applicare righe/relayout
                            // mentre l'utente è dentro l'editor ARTICLE o in un'altra cella.
                            pendingPoidsRealiseRefresh = true;
                            return;
                        }
                        applyPoidsRealiseRows(loaded);
                    } catch (Exception ex) {
                        System.err.println("Erreur mise a jour tableau poids realise: " + ex.getMessage());
                    } finally {
                        refreshingPoidsRealise = false;
                        if (pendingPoidsRealiseRefresh && (productionTable == null || !productionTable.isEditing())) {
                            pendingPoidsRealiseRefresh = false;
                            SwingUtilities.invokeLater(() -> refreshPoidsRealiseFromProductionTable());
                        }
                    }
                }
            }.execute();
        }

        private java.util.List<PoidsRequest> buildPoidsRequestsFromProductionTable() {
            java.util.List<PoidsRequest> requests = new ArrayList<>();
            for (int row = 0; row < productionModel.getRowCount(); row++) {
                String article = valueAt(productionModel, row, 0).trim().toUpperCase(Locale.ROOT);
                String produit = valueAt(productionModel, row, 1).trim().toUpperCase(Locale.ROOT);
                String lot = valueAt(productionModel, row, 2).trim().toUpperCase(Locale.ROOT);
                String coucheRaw = valueAt(productionModel, row, 5).trim().toUpperCase(Locale.ROOT);
                String couche = coucheRaw.contains("SUP") ? "SUP" : (coucheRaw.contains("INF") ? "INF" : coucheRaw);
                String serie = readProductionSerieForPoids(row);
                int nombreMelanges = parseIntegerValue(productionModel.getValueAt(row, 3));
                int desiredTotal = parsePoidsInteger(valueAt(productionModel, row, 7));

                if (article.isEmpty() || lot.isEmpty() || article.startsWith("ARRET") || nombreMelanges <= 0) {
                    continue;
                }

                // V193.31: POIDS REALISE doit suivre les lignes de Production realisee une par une.
                // On ne fusionne plus les demandes ARTICLE/LOT/COUCHE avant la recherche DB: cela evitait
                // de perdre les palettes des lignes suivantes ou de les affecter a une mauvaise serie.
                // Les palettes CL sont ensuite attribuees sequentiellement (N° pesee croissant) en s'arretant
                // au poids de la ligne Production realisee, ce qui separe correctement INF/SUP et SERIE A/B.
                PoidsRequest request = new PoidsRequest(article, produit, couche, lot, serie);
                request.takeCount = nombreMelanges;
                request.desiredTotal = desiredTotal;
                request.sourceRow = row;
                requests.add(request);
            }
            return requests;
        }

        private LocalDateTime buildProductionRowDateTime(String value, boolean start) {
            Integer minutes = parseFrenchTime(value);
            if (minutes == null) {
                return null;
            }
            CurrentShiftWindow shift = getCurrentShiftWindow();
            if (shift == null || shift.start == null) {
                return null;
            }
            LocalDate baseDate = shift.start.toLocalDate();
            LocalDateTime dt = LocalDateTime.of(baseDate, LocalTime.of(minutes / 60, minutes % 60));
            if (dt.isBefore(shift.start.minusMinutes(1))) {
                dt = dt.plusDays(1);
            }
            return dt;
        }


        private String readProductionSerieForPoids(int row) {
            if (productionModel == null || row < 0 || row >= productionModel.getRowCount()) {
                return "";
            }

            String serie = valueAt(productionModel, row, 9).trim().toUpperCase(Locale.ROOT);
            if (looksLikeProductionSerie(serie)) {
                return serie;
            }

            // Sicurezza: se in qualche vecchio salvataggio la serie si trova in una colonna
            // vicina, la recupera comunque. Questo evita di raggruppare SUP/INF senza serie.
            for (int c = 0; c < productionModel.getColumnCount(); c++) {
                String v = valueAt(productionModel, row, c).trim().toUpperCase(Locale.ROOT);
                if (looksLikeProductionSerie(v)) {
                    return v;
                }
            }
            return serie;
        }

        private boolean looksLikeProductionSerie(String value) {
            if (value == null) return false;
            String v = value.trim().toUpperCase(Locale.ROOT);
            return v.matches("SERIE\\s+[A-Z0-9]+") || v.matches("SÉRIE\\s+[A-Z0-9]+") || v.matches("SERIE[A-Z0-9]+") || v.matches("SÉRIE[A-Z0-9]+");
        }

        private String normalizeForPoidsKey(String value) {
            if (value == null) return "";
            return value.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        }

        private int parsePoidsInteger(String value) {
            if (value == null) return 0;
            String digits = value.replaceAll("[^0-9-]", "");
            if (digits.isEmpty() || "-".equals(digits)) return 0;
            try { return Integer.parseInt(digits); } catch (Exception ex) { return 0; }
        }

        private java.util.List<String> splitDashValues(String value) {
            java.util.List<String> result = new ArrayList<>();
            if (value == null) return result;
            for (String part : value.trim().split("-")) {
                String v = part == null ? "" : part.trim();
                if (!v.isEmpty()) result.add(v);
            }
            return result;
        }

        private String joinDashValues(java.util.List<String> values, int from, int to) {
            if (values == null || values.isEmpty() || from >= to) return "";
            int a = Math.max(0, from);
            int b = Math.min(values.size(), to);
            java.util.List<String> slice = new ArrayList<>();
            for (int i = a; i < b; i++) slice.add(values.get(i));
            return String.join("-", slice);
        }

        private void repairPoidsRealiseSeriesFromProductionTable() {
            if (productionModel == null || poidsModel == null || poidsModel.getColumnCount() < 8) {
                return;
            }

            DefaultTableModel repaired = new DefaultTableModel(new Object[]{"Type de produit", "COUCHE", "N° Lot", "N° Série", "Couleur", "Palette N°", "Palette Poids", "Total"}, 0) {
                @Override public boolean isCellEditable(int row, int column) { return true; }
            };

            boolean changed = false;

            for (int pr = 0; pr < poidsModel.getRowCount(); pr++) {
                String produit = valueAt(poidsModel, pr, 0).trim();
                String couche = normalizeCouche(valueAt(poidsModel, pr, 1));
                String lot = valueAt(poidsModel, pr, 2).trim();
                String serie = valueAt(poidsModel, pr, 3).trim().toUpperCase(Locale.ROOT);
                String couleur = valueAt(poidsModel, pr, 4).trim();
                String palettes = valueAt(poidsModel, pr, 5).trim();
                String poids = valueAt(poidsModel, pr, 6).trim();
                String total = valueAt(poidsModel, pr, 7).trim();

                if (produit.isEmpty() && couche.isEmpty() && lot.isEmpty() && serie.isEmpty()
                        && couleur.isEmpty() && palettes.isEmpty() && poids.isEmpty() && total.isEmpty()) {
                    repaired.addRow(new Object[]{"", "", "", "", "", "", "", ""});
                    continue;
                }

                java.util.List<Integer> matchingProductionRows = findProductionRowsForPoidsLine(produit, couche, lot);
                java.util.List<Integer> matchingSeriesRows = new ArrayList<>();
                for (Integer r : matchingProductionRows) {
                    String s = readProductionSerieForPoids(r);
                    if (!s.isEmpty()) matchingSeriesRows.add(r);
                }

                if (serie.isEmpty() && matchingSeriesRows.size() == 1) {
                    String fixedSerie = readProductionSerieForPoids(matchingSeriesRows.get(0));
                    repaired.addRow(new Object[]{produit, couche, lot, fixedSerie, couleur, palettes, poids, total});
                    changed = true;
                    continue;
                }

                if (serie.isEmpty() && matchingSeriesRows.size() > 1 && !palettes.isEmpty() && !poids.isEmpty()) {
                    java.util.List<String> paletteList = splitDashValues(palettes);
                    java.util.List<String> poidsList = splitDashValues(poids);
                    if (!paletteList.isEmpty() && paletteList.size() == poidsList.size()) {
                        int cursor = 0;
                        boolean splitDone = false;
                        for (Integer prodRow : matchingSeriesRows) {
                            String prodSerie = readProductionSerieForPoids(prodRow);
                            int desiredTotal = parsePoidsInteger(valueAt(productionModel, prodRow, 7));
                            if (desiredTotal <= 0) desiredTotal = parsePoidsInteger(total);

                            int start = cursor;
                            int sum = 0;
                            while (cursor < poidsList.size()) {
                                sum += parsePoidsInteger(poidsList.get(cursor));
                                cursor++;
                                if (desiredTotal > 0 && sum >= desiredTotal) {
                                    break;
                                }
                            }

                            if (cursor > start) {
                                repaired.addRow(new Object[]{
                                        produit,
                                        couche,
                                        lot,
                                        prodSerie,
                                        couleur,
                                        joinDashValues(paletteList, start, cursor),
                                        joinDashValues(poidsList, start, cursor),
                                        String.valueOf(sum)
                                });
                                splitDone = true;
                            }
                        }
                        if (cursor < poidsList.size()) {
                            repaired.addRow(new Object[]{produit, couche, lot, "", couleur,
                                    joinDashValues(paletteList, cursor, paletteList.size()),
                                    joinDashValues(poidsList, cursor, poidsList.size()),
                                    ""});
                        }
                        if (splitDone) {
                            changed = true;
                            continue;
                        }
                    }
                }

                repaired.addRow(new Object[]{produit, couche, lot, serie, couleur, palettes, poids, total});
            }

            if (changed) {
                poidsModel.setRowCount(0);
                for (int r = 0; r < repaired.getRowCount(); r++) {
                    Object[] row = new Object[8];
                    for (int c = 0; c < row.length; c++) row[c] = repaired.getValueAt(r, c);
                    poidsModel.addRow(row);
                }
            }
        }

        private java.util.List<Integer> findProductionRowsForPoidsLine(String produit, String couche, String lot) {
            java.util.List<Integer> rows = new ArrayList<>();
            if (productionModel == null) return rows;

            String targetProduit = normalizeForPoidsKey(produit);
            String targetCouche = normalizeCouche(couche);
            String targetLot = normalizeForPoidsKey(lot);

            for (int r = 0; r < productionModel.getRowCount(); r++) {
                String pProduit = normalizeForPoidsKey(valueAt(productionModel, r, 1));
                String pCouche = normalizeCouche(valueAt(productionModel, r, 5));
                String pLot = normalizeForPoidsKey(valueAt(productionModel, r, 2));
                if (pProduit.equals(targetProduit) && pCouche.equals(targetCouche) && pLot.equals(targetLot)) {
                    rows.add(r);
                }
            }
            return rows;
        }

        private void compactPoidsRealiseBlankRows() {
            if (poidsModel == null) {
                return;
            }

            java.util.List<Object[]> usedRows = new ArrayList<>();
            for (int r = 0; r < poidsModel.getRowCount(); r++) {
                boolean empty = true;
                Object[] row = new Object[poidsModel.getColumnCount()];
                for (int c = 0; c < poidsModel.getColumnCount(); c++) {
                    Object value = poidsModel.getValueAt(r, c);
                    String text = value == null ? "" : value.toString().trim();
                    row[c] = text;
                    if (!text.isEmpty()) {
                        empty = false;
                    }
                }
                if (!empty) {
                    usedRows.add(row);
                }
            }

            if (usedRows.size() == poidsModel.getRowCount()) {
                return;
            }

            poidsModel.setRowCount(0);
            for (Object[] row : usedRows) {
                poidsModel.addRow(row);
            }
            while (poidsModel.getRowCount() < 4) {
                poidsModel.addRow(new Object[]{"", "", "", "", "", "", "", ""});
            }
        }

        private void clearPoidsModel() {
            if (poidsModel == null) {
                return;
            }
            poidsModel.setRowCount(0);
            for (int i = 0; i < 4; i++) {
                poidsModel.addRow(new Object[]{"", "", "", "", "", "", "", ""});
            }
            adjustAllRowHeights(poidsTable, 31);
            relayoutGrowingTables();
        }

        private boolean isPoidsModelEffectivelyEmpty() {
            if (poidsModel == null || poidsModel.getRowCount() == 0) {
                return true;
            }

            for (int r = 0; r < poidsModel.getRowCount(); r++) {
                for (int c = 0; c < poidsModel.getColumnCount(); c++) {
                    Object value = poidsModel.getValueAt(r, c);
                    if (value != null && !value.toString().trim().isEmpty()) {
                        return false;
                    }
                }
            }
            return true;
        }

        private void applyPoidsRealiseRows(java.util.List<PoidsRealiseRow> rows) {
            if (poidsModel == null) {
                return;
            }

            poidsModel.setRowCount(0);
            if (rows != null) {
                for (PoidsRealiseRow row : rows) {
                    poidsModel.addRow(new Object[]{
                            row.produit,
                            row.couche,
                            row.lot,
                            row.serie,
                            row.couleur,
                            String.join("-", row.noPesees),
                            String.join("-", row.netValues),
                            formatPlainNumber(row.total)
                    });
                }
            }

            repairPoidsRealiseSeriesFromProductionTable();
            compactPoidsRealiseBlankRows();
            while (poidsModel.getRowCount() < 4) {
                poidsModel.addRow(new Object[]{"", "", "", "", "", "", "", ""});
            }
            adjustAllRowHeights(poidsTable, 31);
            relayoutGrowingTables();
            if (lblPoidsStatus != null) {
                int count = rows == null ? 0 : rows.size();
                lblPoidsStatus.setText("V9.3 Poids CL: " + count + " ligne(s) chargee(s)");
            }
        }

        private java.util.List<PoidsRealiseRow> loadPoidsRealiseRowsSequential(java.util.List<PoidsRequest> requests) {
            java.util.LinkedHashMap<String, PoidsRealiseRow> groupedRows = new java.util.LinkedHashMap<>();
            java.util.Set<String> usedDbRows = new java.util.LinkedHashSet<>();

            if (requests == null || requests.isEmpty()) {
                return new ArrayList<>();
            }

            for (PoidsRequest request : requests) {
                if (request == null || request.takeCount <= 0) {
                    continue;
                }

                try {
                    java.util.List<PoidsDbRow> allRows = loadPoidsDbRowsDynamic(request);
                    allRows.removeIf(r -> r == null || r.net == null || r.net.compareTo(BigDecimal.ZERO) < 0);
                    if (allRows.isEmpty()) {
                        continue;
                    }

                    // Attribuzione reale: palette in ordine crescente di N° pesata.
                    // Non uso piu la finestra oraria per tagliare le palette: in produzione alcune pesate
                    // cadono esattamente al cambio riga/orario e venivano escluse o spostate alla serie dopo.
                    allRows.sort((a, b) -> {
                        int cmp = Integer.compare(extractFirstInt(a.noPesee), extractFirstInt(b.noPesee));
                        if (cmp != 0) return cmp;
                        cmp = compareDate2Asc(a.date2, b.date2);
                        if (cmp != 0) return cmp;
                        return compareNullable(a.idPesage, b.idPesage);
                    });

                    java.util.List<PoidsDbRow> selectedRows = new ArrayList<>();
                    BigDecimal selectedTotal = BigDecimal.ZERO;
                    int desiredTotal = request.desiredTotal;

                    for (PoidsDbRow dbRow : allRows) {
                        String uniqueKey = makePoidsDbRowUniqueKey(request, dbRow);
                        if (usedDbRows.contains(uniqueKey)) {
                            continue;
                        }

                        selectedRows.add(dbRow);
                        usedDbRows.add(uniqueKey);
                        selectedTotal = selectedTotal.add(dbRow.net == null ? BigDecimal.ZERO : dbRow.net);

                        if (desiredTotal > 0 && selectedTotal.compareTo(new BigDecimal(desiredTotal)) >= 0) {
                            break;
                        }
                        if (desiredTotal <= 0 && selectedRows.size() >= request.takeCount) {
                            break;
                        }
                    }

                    if (selectedRows.isEmpty()) {
                        continue;
                    }

                    for (PoidsDbRow dbRow : selectedRows) {
                        String dbCouche = normalizeCouche(dbRow.couche);
                        if (dbCouche.isEmpty()) dbCouche = request.couche;
                        String dbCouleur = dbRow.couleur == null ? "" : dbRow.couleur.trim();
                        String displaySerie = request.serie == null ? "" : request.serie.trim().toUpperCase(Locale.ROOT);
                        String key = (request.produit.isEmpty() ? request.article : request.produit) + "\u0001"
                                + dbCouche + "\u0001" + request.lot + "\u0001" + displaySerie + "\u0001" + dbCouleur;

                        PoidsRealiseRow row = groupedRows.get(key);
                        if (row == null) {
                            row = new PoidsRealiseRow(
                                    request.produit.isEmpty() ? request.article : request.produit,
                                    dbCouche,
                                    request.lot,
                                    displaySerie,
                                    dbCouleur
                            );
                            groupedRows.put(key, row);
                        }
                        row.noPesees.add(dbRow.noPesee == null ? "" : dbRow.noPesee.trim());
                        row.netValues.add(formatPlainNumber(dbRow.net));
                        row.total = row.total.add(dbRow.net == null ? BigDecimal.ZERO : dbRow.net);
                    }
                } catch (Exception ex) {
                    System.err.println("Erreur chargement poids realise article "
                            + request.article + " lot " + request.lot + ": " + ex.getMessage());
                }
            }

            return new ArrayList<>(groupedRows.values());
        }

        private String makePoidsDbRowUniqueKey(PoidsRequest request, PoidsDbRow row) {
            if (row == null) return "";
            String id = row.idPesage == null ? "" : row.idPesage.trim();
            if (!id.isEmpty()) return "ID:" + id;
            return "ROW:" + (request == null ? "" : request.article) + "|" + (request == null ? "" : request.lot)
                    + "|" + (request == null ? "" : request.couche) + "|" + row.noPesee + "|" + row.date2 + "|" + formatPlainNumber(row.net);
        }

        private java.util.List<PoidsRealiseRow> loadPoidsRealiseRows(PoidsRequest request) throws Exception {
            if (request == null || request.takeCount <= 0) {
                return null;
            }

            java.util.List<PoidsDbRow> allRows = loadPoidsDbRowsDynamic(request);

            // V107: esclude completamente tutte le pesate con NET_P < 0.
            // I valori negativi non devono entrare ne nella selezione delle pesate valide
            // ne nel totale visualizzato.
            allRows.removeIf(r -> r == null || r.net == null || r.net.compareTo(BigDecimal.ZERO) < 0);

            if (allRows.isEmpty()) {
                return new ArrayList<>();
            }

            // Prende prima le pesate piu recenti fino a Nombre de Melanges,
            // poi le riordina per N°PESEE crescente per mostrare 43-44-45-46-47.
            allRows.sort((a, b) -> {
                int cmp = compareDate2Desc(a.date2, b.date2);
                if (cmp != 0) return cmp;
                cmp = Integer.compare(extractFirstInt(b.noPesee), extractFirstInt(a.noPesee));
                if (cmp != 0) return cmp;
                return compareNullable(b.idPesage, a.idPesage);
            });

            java.util.List<PoidsDbRow> dbRows = new ArrayList<>();
            for (int i = 0; i < allRows.size() && i < request.takeCount; i++) {
                dbRows.add(allRows.get(i));
            }

            dbRows.sort((a, b) -> {
                int cmp = Integer.compare(extractFirstInt(a.noPesee), extractFirstInt(b.noPesee));
                if (cmp != 0) return cmp;
                cmp = compareDate2Asc(a.date2, b.date2);
                if (cmp != 0) return cmp;
                return compareNullable(a.idPesage, b.idPesage);
            });

            java.util.LinkedHashMap<String, PoidsRealiseRow> groupedRows = new java.util.LinkedHashMap<>();
            for (PoidsDbRow dbRow : dbRows) {
                String dbCouche = normalizeCouche(dbRow.couche);
                if (dbCouche.isEmpty()) {
                    dbCouche = request.couche;
                }

                // V193.15: in POIDS REALISE, N° Lot e N° Série sono colonne distinte.
                // La riga viene separata per COUCHE e per SERIE, oltre che per lotto/couleur.
                String displayLot = request.lot;
                String displaySerie = request.serie;
                if (displaySerie.isEmpty()) {
                    displaySerie = dbRow.melange == null ? "" : dbRow.melange.trim().toUpperCase(Locale.ROOT);
                }

                String dbCouleur = dbRow.couleur == null ? "" : dbRow.couleur.trim();
                String key = dbCouche + "\u0001" + displayLot + "\u0001" + displaySerie + "\u0001" + dbCouleur;
                PoidsRealiseRow row = groupedRows.get(key);
                if (row == null) {
                    row = new PoidsRealiseRow(
                            request.produit.isEmpty() ? request.article : request.produit,
                            dbCouche,
                            displayLot,
                            displaySerie,
                            dbCouleur
                    );
                    groupedRows.put(key, row);
                }
                row.noPesees.add(dbRow.noPesee == null ? "" : dbRow.noPesee.trim());
                row.netValues.add(formatPlainNumber(dbRow.net));
                row.total = row.total.add(dbRow.net == null ? BigDecimal.ZERO : dbRow.net);
            }
            return new ArrayList<>(groupedRows.values());
        }


        private java.util.List<PoidsDbRow> loadPoidsDbRowsDynamic(PoidsRequest request) throws Exception {
            java.util.List<PoidsDbRow> rows;
            SQLException lastError = null;

            try (Connection cn = DbOracleManager.getConnection()) {
                // V190.20: non prova piu schemi con colonne inesistenti (es. PRODUIT),
                // per evitare ORA-00904 quando DBM_PESAGE_NEW usa i nomi reali PRODUIT_ID/LOT_P.
                java.util.Set<String> cols = getDbColumns(cn, "DBM_PESAGE_NEW");
                java.util.List<String[]> schemas = new ArrayList<>();
                addPoidsSchemaIfComplete(schemas, cols, "PRODUIT_ID", "LOT_P", "DESTINATION_ID", "NO_PESEE_P", "NET_P", "DATE2", "ID_PESAGE");
                addPoidsSchemaIfComplete(schemas, cols, "PRODUIT_ID", "LOT_P", "DESTINATION_ID", "NO_PESEE_P", "NET", "DATE2", "ID_PESAGE");
                addPoidsSchemaIfComplete(schemas, cols, "PRODUIT", "LOT", "DESTINATION_ID", "NO_PESEE", "NET", "DATE", "ID");
                addPoidsSchemaIfComplete(schemas, cols, "PRODUIT", "LOT_P", "DESTINATION_ID", "NO_PESEE_P", "NET_P", "DATE2", "ID_PESAGE");

                if (schemas.isEmpty()) {
                    throw new SQLException("DBM_PESAGE_NEW: colonnes poids CL introuvables. Colonnes detectees: " + cols);
                }

                for (String[] sc : schemas) {
                    try {
                        rows = queryPoidsRowsV7(cn, request, sc[0], sc[1], sc[2], sc[3], sc[4], sc[5], sc[6], true);
                        if (!rows.isEmpty()) {
                            return rows;
                        }
                    } catch (SQLException ex) {
                        lastError = ex;
                    }
                }

                // Ultimo fallback: prodotto + DESTINATION CL, senza filtro lotto.
                // Utile per capire se il problema e solo il formato lotto.
                for (String[] sc : schemas) {
                    try {
                        rows = queryPoidsRowsV7(cn, request, sc[0], sc[1], sc[2], sc[3], sc[4], sc[5], sc[6], false);
                        if (!rows.isEmpty()) {
                            return rows;
                        }
                    } catch (SQLException ex) {
                        lastError = ex;
                    }
                }
            }

            if (lastError != null) {
                System.err.println("V8.8 Poids CL: 0 ligne pour " + request.article + " / " + request.lot
                        + " - derniere erreur SQL: " + lastError.getMessage());
                if (lblPoidsStatus != null) {
                    final String finalErrorMessage = lastError.getMessage();
                    SwingUtilities.invokeLater(() -> lblPoidsStatus.setText(
                            "V8.8 Poids CL: 0 ligne - SQL: " + shortStatus(finalErrorMessage)));
                }
            } else if (lblPoidsStatus != null) {
                SwingUtilities.invokeLater(() -> lblPoidsStatus.setText(
                        "V8.8 Poids CL: 0 ligne pour " + request.article + " / " + request.lot));
            }
            return new ArrayList<>();
        }

        private void addPoidsSchemaIfComplete(java.util.List<String[]> schemas, java.util.Set<String> cols,
                                              String produitCol, String lotCol, String destinationCol,
                                              String noPeseeCol, String netCol, String dateCol, String idCol) {
            String[] sc = {produitCol, lotCol, destinationCol, noPeseeCol, netCol, dateCol, idCol};
            if (cols == null || cols.isEmpty()) return;
            for (String col : sc) {
                if (col == null || !cols.contains(col.toUpperCase(Locale.ROOT))) {
                    return;
                }
            }
            schemas.add(sc);
        }

        private String destinationProductionClCondition(String destinationCol) {
            // V193.12: POIDS REALISE doit reprendre seulement les pesées envoyées vers les lignes de production
            // CL numériques (CL200, CL201, CL202, etc.). Les autres destinations comme CLABO sont exclues.
            return "REGEXP_LIKE(UPPER(TRIM(" + destinationCol + ")), '^CL[0-9]+$')";
        }

        private java.util.List<PoidsDbRow> queryPoidsRowsV7(Connection cn, PoidsRequest request,
                                                            String produitCol, String lotCol, String destinationCol,
                                                            String noPeseeCol, String netCol, String dateCol,
                                                            String idCol, boolean filterLot) throws SQLException {
            java.util.List<PoidsDbRow> rows = new ArrayList<>();

            String article = request.article == null ? "" : request.article.trim().toUpperCase(Locale.ROOT);
            String lot = request.lot == null ? "" : request.lot.trim().toUpperCase(Locale.ROOT);
            String lotNoSpaces = lot.replace(" ", "");
            String lotAlphaNum = lot.replaceAll("[^0-9A-Z]", "");
            String couche = request.couche == null ? "" : request.couche.trim().toUpperCase(Locale.ROOT);
            String serie = request.serie == null ? "" : request.serie.trim().toUpperCase(Locale.ROOT);
            String serieNoSpaces = serie.replace(" ", "");
            boolean hasProductionTimeWindow = false; // V193.31: non usare finestra oraria per POIDS REALISE, assegna per sequenza palette

            String couleurExpr = "CAST(NULL AS VARCHAR2(100))";
            try {
                String couleurCol = firstExistingColumn(
                        getDbColumns(cn, "DBM_PESAGE_NEW"),
                        "COULEUR", "COULEUR_P", "COULEUR_ID", "CLAIR_FONCE", "CLAIR_FONC", "CLAIREFONCE", "COLOR"
                );
                if (couleurCol != null && !couleurCol.trim().isEmpty()) {
                    couleurExpr = couleurCol;
                }
            } catch (SQLException ignored) {
                // Se la colonna couleur non esiste nel DB, la tabella resta compilabile con Couleur vuota.
            }

            CurrentShiftWindow shift = getCurrentShiftWindow();
            String shiftTourneeId = shift == null || shift.tourneeId == null ? "" : shift.tourneeId.trim();

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ").append(noPeseeCol).append(" AS NO_PESEE_X, ")
                    .append(netCol).append(" AS NET_X, ")
                    .append(dateCol).append(" AS DATE_X, ")
                    .append(idCol).append(" AS ID_X, ")
                    .append("COUCHE AS COUCHE_X, ")
                    .append("MELANGE AS MELANGE_X, ")
                    .append(couleurExpr).append(" AS COULEUR_X ")
                    .append("FROM DBM_PESAGE_NEW ")
                    .append("WHERE UPPER(TRIM(").append(produitCol).append(")) = ? ")
                    .append("AND ").append(destinationProductionClCondition(destinationCol)).append(" ")
                    // V190.10: Poids realise deve prendere solo le pesate del turno del rapport.
                    // Prima il filtro era solo TRUNC(SYSDATE): cosi entravano anche palette di altri turni
                    // della stessa giornata (es. T3 Nuit dentro T1 Matin).
                    .append("AND TO_DATE(").append(dateCol).append(", 'DD/MM/RR HH24:MI') >= ? ")
                    .append("AND TO_DATE(").append(dateCol).append(", 'DD/MM/RR HH24:MI') < ? ")
                    .append("AND TRIM(TO_CHAR(TOURNEE_ID)) = ? ")
                    .append("AND NVL(").append(netCol).append(", 0) >= 0 ");

            if (hasProductionTimeWindow) {
                sql.append("AND TO_DATE(").append(dateCol).append(", 'DD/MM/RR HH24:MI') >= ? ")
                        .append("AND TO_DATE(").append(dateCol).append(", 'DD/MM/RR HH24:MI') < ? ");
            }

            if (!couche.isEmpty()) {
                sql.append("AND UPPER(TRIM(COUCHE)) LIKE ? ");
            }
            if (false && !hasProductionTimeWindow && !serie.isEmpty()) {
                sql.append("AND (UPPER(TRIM(MELANGE)) = ? ")
                        .append("OR REPLACE(UPPER(TRIM(MELANGE)), ' ', '') = ? ")
                        .append("OR UPPER(TRIM(MELANGE)) LIKE ? ");
                if (serie.startsWith("SERIE ")) {
                    sql.append("OR UPPER(TRIM(MELANGE)) = ? ")
                            .append("OR UPPER(TRIM(MELANGE)) LIKE ? ");
                }
                sql.append(") ");
            }

            if (filterLot) {
                sql.append("AND (UPPER(TRIM(").append(lotCol).append(")) = ? ")
                        .append("OR REPLACE(UPPER(TRIM(").append(lotCol).append(")), ' ', '') = ? ")
                        .append("OR REGEXP_REPLACE(UPPER(TRIM(").append(lotCol).append(")), '[^0-9A-Z]', '') = ? ")
                        .append("OR UPPER(TRIM(").append(lotCol).append(")) LIKE ? ")
                        .append("OR REPLACE(UPPER(TRIM(").append(lotCol).append(")), ' ', '') LIKE ? ")
                        .append("OR REGEXP_REPLACE(UPPER(TRIM(").append(lotCol).append(")), '[^0-9A-Z]', '') LIKE ?) ");
            }

            sql.append("ORDER BY TO_DATE(").append(dateCol).append(", 'DD/MM/RR HH24:MI') DESC, ")
                    .append("TO_NUMBER(NULLIF(REGEXP_REPLACE(").append(noPeseeCol).append(", '[^0-9]', ''), '')) DESC, ")
                    .append(idCol).append(" DESC");

            try (PreparedStatement ps = cn.prepareStatement(sql.toString())) {
                int p = 1;
                ps.setString(p++, article);
                ps.setTimestamp(p++, Timestamp.valueOf(shift.start));
                ps.setTimestamp(p++, Timestamp.valueOf(shift.end));
                ps.setString(p++, shiftTourneeId);
                if (hasProductionTimeWindow) {
                    ps.setTimestamp(p++, Timestamp.valueOf(request.rowStart));
                    ps.setTimestamp(p++, Timestamp.valueOf(request.rowEnd));
                }
                if (!couche.isEmpty()) {
                    ps.setString(p++, couche + "%");
                }
                if (false && !hasProductionTimeWindow && !serie.isEmpty()) {
                    ps.setString(p++, serie);
                    ps.setString(p++, serieNoSpaces);
                    ps.setString(p++, serie + "%");
                    if (serie.startsWith("SERIE ")) {
                        String serieShort = serie.substring("SERIE ".length()).trim();
                        ps.setString(p++, serieShort);
                        ps.setString(p++, serieShort + "%");
                    }
                }
                if (filterLot) {
                    ps.setString(p++, lot);
                    ps.setString(p++, lotNoSpaces);
                    ps.setString(p++, lotAlphaNum);
                    ps.setString(p++, lot + "%");
                    ps.setString(p++, lotNoSpaces + "%");
                    ps.setString(p++, lotAlphaNum + "%");
                }

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new PoidsDbRow(
                                rs.getString("NO_PESEE_X"),
                                readBigDecimal(rs, "NET_X"),
                                rs.getString("DATE_X"),
                                rs.getString("ID_X"),
                                rs.getString("COUCHE_X"),
                                rs.getString("MELANGE_X"),
                                rs.getString("COULEUR_X")
                        ));
                    }
                }
            }
            return rows;
        }

        private String shortStatus(String text) {
            if (text == null) return "";
            text = text.replace('\n', ' ').replace('\r', ' ').trim();
            return text.length() > 85 ? text.substring(0, 85) + "..." : text;
        }

        private String[] existingColumnsOrFallback(java.util.Set<String> cols, String... names) {
            java.util.List<String> found = new ArrayList<>();
            if (cols != null && names != null) {
                for (String name : names) {
                    if (name != null && cols.contains(name.toUpperCase(Locale.ROOT))) {
                        found.add(name.toUpperCase(Locale.ROOT));
                    }
                }
            }
            if (found.isEmpty() && names != null) {
                for (String name : names) if (name != null) found.add(name);
            }
            return found.toArray(new String[0]);
        }

        private String[] optionalExistingColumns(java.util.Set<String> cols, String... names) {
            java.util.List<String> found = new ArrayList<>();
            if (cols != null && names != null) {
                for (String name : names) {
                    if (name != null && cols.contains(name.toUpperCase(Locale.ROOT))) {
                        found.add(name.toUpperCase(Locale.ROOT));
                    }
                }
            }
            return found.toArray(new String[0]);
        }

        private java.util.List<PoidsDbRow> queryPoidsRows(Connection cn, PoidsRequest request,
                                                          String produitCol, String lotCol, String destinationCol,
                                                          String noPeseeCol, String netCol, String dateCol, String idCol) throws SQLException {
            java.util.List<PoidsDbRow> rows = new ArrayList<>();

            String select =
                    "SELECT " + noPeseeCol + " AS NO_PESEE_X, " +
                    netCol + " AS NET_X" +
                    (dateCol != null ? ", " + dateCol + " AS DATE_X" : ", NULL AS DATE_X") +
                    (idCol != null ? ", " + idCol + " AS ID_X" : ", NULL AS ID_X") +
                    " FROM DBM_PESAGE_NEW ";

            String article = request.article == null ? "" : request.article.trim().toUpperCase(Locale.ROOT);
            String lot = request.lot == null ? "" : request.lot.trim().toUpperCase(Locale.ROOT);
            String lotCompact = lot.replace(" ", "");
            String lotDigits = lot.replaceAll("[^0-9A-Z]", "");

            // Tentativo 1: prodotto + lotto compatto + destination CL.
            String sql1 = select +
                    "WHERE UPPER(TRIM(" + produitCol + ")) = ? " +
                    "AND REPLACE(UPPER(TRIM(" + lotCol + ")), ' ', '') LIKE ? " +
                    "AND " + destinationProductionClCondition(destinationCol) + " " +
                    "AND NVL(" + netCol + ", 0) >= 0";
            rows = executePoidsQuery(cn, sql1, article, lotCompact + "%");
            if (!rows.isEmpty()) return rows;

            // Tentativo 2: prodotto + lotto normalizzato solo alfanumerico + destination CL.
            String sql2 = select +
                    "WHERE UPPER(TRIM(" + produitCol + ")) = ? " +
                    "AND REGEXP_REPLACE(UPPER(TRIM(" + lotCol + ")), '[^0-9A-Z]', '') LIKE ? " +
                    "AND " + destinationProductionClCondition(destinationCol) + " " +
                    "AND NVL(" + netCol + ", 0) >= 0";
            rows = executePoidsQuery(cn, sql2, article, lotDigits + "%");
            if (!rows.isEmpty()) return rows;

            // Tentativo 3: prodotto + destination CL, senza filtro lotto.
            // Serve quando il lotto e scritto con formato diverso nel DB.
            String sql3 = select +
                    "WHERE UPPER(TRIM(" + produitCol + ")) = ? " +
                    "AND " + destinationProductionClCondition(destinationCol) + " " +
                    "AND NVL(" + netCol + ", 0) >= 0";
            rows = executePoidsQuery(cn, sql3, article);
            return rows;
        }

        private java.util.List<PoidsDbRow> executePoidsQuery(Connection cn, String sql, String... params) throws SQLException {
            java.util.List<PoidsDbRow> rows = new ArrayList<>();
            try (PreparedStatement ps = cn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setString(i + 1, params[i] == null ? "" : params[i]);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new PoidsDbRow(
                                rs.getString("NO_PESEE_X"),
                                readBigDecimal(rs, "NET_X"),
                                rs.getString("DATE_X"),
                                rs.getString("ID_X")
                        ));
                    }
                }
            }
            return rows;
        }

        private java.util.Set<String> getDbColumns(Connection cn, String tableName) throws SQLException {
            java.util.Set<String> cols = new java.util.HashSet<>();
            java.sql.DatabaseMetaData meta = cn.getMetaData();

            String[] tableNames = {tableName, tableName.toUpperCase(Locale.ROOT), tableName.toLowerCase(Locale.ROOT)};
            for (String t : tableNames) {
                try (ResultSet rs = meta.getColumns(null, null, t, null)) {
                    while (rs.next()) {
                        String name = rs.getString("COLUMN_NAME");
                        if (name != null) cols.add(name.trim().toUpperCase(Locale.ROOT));
                    }
                }
                if (!cols.isEmpty()) break;
            }

return cols;
        }

        private String firstExistingColumn(java.util.Set<String> cols, String... names) {
            if (cols == null || names == null) {
                return null;
            }
            for (String name : names) {
                if (name != null && cols.contains(name.toUpperCase(Locale.ROOT))) {
                    return name.toUpperCase(Locale.ROOT);
                }
            }
            return null;
        }

        private int compareDate2Asc(String a, String b) {
            LocalDateTime da = parseDbDate2(a);
            LocalDateTime db = parseDbDate2(b);
            if (da != null && db != null) return da.compareTo(db);
            if (da != null) return -1;
            if (db != null) return 1;
            return compareNullable(a, b);
        }

        private int compareDate2Desc(String a, String b) {
            return compareDate2Asc(b, a);
        }

        private LocalDateTime parseDbDate2(String value) {
            if (value == null) {
                return null;
            }
            String text = value.trim();
            if (text.isEmpty()) {
                return null;
            }
            java.time.format.DateTimeFormatter[] formats = new java.time.format.DateTimeFormatter[]{
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy HH:mm"),
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy H:mm"),
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy H:mm")
            };
            for (java.time.format.DateTimeFormatter f : formats) {
                try {
                    return LocalDateTime.parse(text, f);
                } catch (Exception ignored) {
                }
            }
            return null;
        }

        private int compareNullable(String a, String b) {
            String aa = a == null ? "" : a.trim();
            String bb = b == null ? "" : b.trim();
            return aa.compareTo(bb);
        }

        private int extractFirstInt(String value) {
            if (value == null) {
                return 0;
            }
            String digits = value.replaceAll("[^0-9-]", "");
            if (digits.isEmpty() || "-".equals(digits)) {
                return 0;
            }
            try {
                return Integer.parseInt(digits);
            } catch (NumberFormatException ex) {
                return 0;
            }
        }

        private String formatPlainNumber(BigDecimal value) {
            if (value == null) {
                return "0";
            }
            BigDecimal normalized = value.stripTrailingZeros();
            if (normalized.scale() < 0) {
                normalized = normalized.setScale(0);
            }
            return normalized.toPlainString().replace('.', ',');
        }

        private void refreshArretSummaryFromProductionTable() {
            if (productionModel == null) {
                return;
            }

            boolean hasArret = false;
            int totalMinutes = 0;
            java.util.List<String> raisons = new ArrayList<>();

            for (int row = 0; row < productionModel.getRowCount(); row++) {
                String article = valueAt(productionModel, row, 0).trim().toUpperCase(Locale.ROOT);
                if (!article.startsWith("ARRET")) {
                    continue;
                }

                hasArret = true;
                totalMinutes += parseDurationValue(productionModel.getValueAt(row, 13));

                String raison = valueAt(productionModel, row, 1).trim();
                if (!raison.isEmpty() && !raisons.contains(raison)) {
                    raisons.add(raison);
                }
            }

            updateArretProductionDetailsVisibility(hasArret);

            if (txtTempsArretAuto != null) {
                txtTempsArretAuto.setText(formatFrenchDuration(totalMinutes));
            }

            for (int i = 0; i < txtRaisonsArretAuto.size(); i++) {
                JTextField field = txtRaisonsArretAuto.get(i);
                field.setText(i < raisons.size() ? raisons.get(i) : "");
            }
        }

        private void addArretProductionDetail(Component component) {
            if (component == null) {
                return;
            }
            add(component);
            arretProductionDetails.add(component);
        }

        private void updateArretProductionDetailsVisibility(boolean hasArret) {
            if (lblArretProductionInfo != null) {
                lblArretProductionInfo.setText(hasArret
                        ? "Il y a eu un arrêt de production"
                        : "Il n'y a eu aucun arrêt de production");
                lblArretProductionInfo.setFont(new Font("Arial", Font.BOLD, 17));
            }

            for (Component component : arretProductionDetails) {
                if (component != null) {
                    component.setVisible(hasArret);
                }
            }

            revalidate();
            repaint();
        }

        private void setChoiceSelected(JCheckBox cb, boolean selected) {
            if (cb == null) {
                return;
            }
            cb.setSelected(selected);
            Object labelObj = cb.getClientProperty("choiceLabel");
            if (labelObj instanceof JLabel) {
                ((JLabel) labelObj).setBackground(selected ? new Color(255, 255, 180) : Color.WHITE);
                ((JLabel) labelObj).repaint();
            }
        }

        private JCheckBox createSmallCheckBox(boolean selected) {
            JCheckBox cb = new JCheckBox();
            cb.setSelected(selected);
            cb.setOpaque(false);
            cb.setMargin(new Insets(0, 0, 0, 0));
            cb.setFocusable(false);
            return cb;
        }

        private JCheckBox addCheckBoxChoice(String text, int x, int y, boolean selected) {
            JCheckBox cb = createSmallCheckBox(selected);
            cb.setBounds(x, y, 22, 22);
            add(cb);
            JLabel label = addLabel(text, x + 24, y, 100, 22, new Font("Arial", Font.BOLD, 13), SwingConstants.LEFT);
            label.setBorder(new LineBorder(new Color(60,60,60), 1));
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setOpaque(true);
            label.setBackground(selected ? new Color(255, 255, 180) : Color.WHITE);
            cb.putClientProperty("choiceLabel", label);
            return cb;
        }

        private JTextField createGridField(String text, int align, boolean editable) {
            JTextField field = new JTextField(text);
            field.setFont(new Font("Arial", Font.PLAIN, 13));
            field.setHorizontalAlignment(align);
            field.setBorder(new LineBorder(GRID, 1));
            field.setBackground(Color.WHITE);
            field.setEditable(editable);
            field.setMargin(new Insets(0, 4, 0, 4));
            return field;
        }

        private JTextArea createGridArea() {
            JTextArea area = new JTextArea();
            area.setFont(new Font("Arial", Font.PLAIN, 13));
            area.setLineWrap(true);
            area.setWrapStyleWord(false);
            area.setBorder(new EmptyBorder(2, 4, 2, 4));
            area.setBackground(Color.WHITE);
            return area;
        }

        private JTextArea createLimitedGridArea(int maxLines) {
            JTextArea area = createGridArea();
            area.setRows(Math.max(1, maxLines));
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            if (area.getDocument() instanceof AbstractDocument) {
                ((AbstractDocument) area.getDocument()).setDocumentFilter(new MaxTextAreaLinesFilter(maxLines));
            }
            return area;
        }


        private JLabel createFooterLabel(String text, int align, Font font) {
            JLabel label = new JLabel(text, align);
            label.setFont(font);
            label.setForeground(Color.BLACK);
            label.setOpaque(true);
            label.setBackground(FORM_BACKGROUND);
            return label;
        }

        private JTextField createFooterField() {
            JTextField field = new JTextField("0");
            field.setFont(FONT_BOLD);
            field.setHorizontalAlignment(SwingConstants.CENTER);
            field.setBorder(new LineBorder(new Color(60,60,60), 1));
            field.setBackground(Color.WHITE);
            field.setMargin(new Insets(0, 4, 0, 4));
            field.setEditable(false);
            field.setFocusable(false);
            return field;
        }

        private JTextField createUnderlineField() {
            JTextField field = new JTextField("");
            field.setFont(FONT_NORMAL);
            field.setHorizontalAlignment(SwingConstants.LEFT);
            field.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, GRID));
            field.setBackground(FORM_BACKGROUND);
            field.setMargin(new Insets(0, 4, 0, 4));
            field.setEditable(true);
            field.setFocusable(true);
            return field;
        }

        private void installDynamicProductionRowsAndTotals(JTable table, DefaultTableModel model,
                                                           JTextField txtTotalMelanges, JTextField txtTotalTemps,
                                                           JTextField txtTotalKg) {
            final boolean[] updating = {false};

            table.putClientProperty("productionTotalMelangesField", txtTotalMelanges);
            table.putClientProperty("productionTotalTempsField", txtTotalTemps);
            table.putClientProperty("productionTotalKgField", txtTotalKg);

            model.addTableModelListener(e -> {
                // V190.01: durante il caricamento della persistenza non aggiungere righe
                // automatiche; altrimenti ogni addRow del load inserisce una riga vuota
                // tra una riga salvata e la successiva.
                if (updating[0] || loadingShiftState) {
                    return;
                }
                // V193.20: durante l'editing di Production réalisée non eseguire righe automatiche,
                // ricalcoli, relayout o refresh. Queste operazioni possono far perdere l'editor
                // della cella ARTICLE prima che l'operatore prema INVIO/TAB.
                if (table != null && table.isEditing()) {
                    return;
                }

                updating[0] = true;
                try {
                    int lastRow = model.getRowCount() - 1;
                    if (lastRow >= 0 && !isProductionRowEmpty(model, lastRow)) {
                        model.addRow(createEmptyRow(PRODUCTION_COL_COUNT));
                    }
                    // V151: sicurezza aggiuntiva. Anche se l'evento di edit viene assorbito
                    // da altri listener o dalla persistenza, la colonna Temps viene sempre
                    // riallineata ai valori de / à prima dei totali.
                    recalculateAllProductionDurations(model);
                    adjustAllRowHeights(table, 31);
                    relayoutGrowingTables();
                    updateProductionTotals(model, txtTotalMelanges, txtTotalTemps, txtTotalKg);
                    refreshArretSummaryFromProductionTable();
                    refreshPoidsRealiseFromProductionTable();
                } finally {
                    updating[0] = false;
                }
            });

            updateProductionTotals(model, txtTotalMelanges, txtTotalTemps, txtTotalKg);
            refreshPoidsRealiseFromProductionTable();
        }


        private void installDynamicPoidsRows(JTable table, DefaultTableModel model) {
            if (table == null || model == null) return;
            final boolean[] updating = {false};
            model.addTableModelListener(e -> {
                if (updating[0] || loadingShiftState) return;
                updating[0] = true;
                try {
                    int lastRow = model.getRowCount() - 1;
                    if (lastRow >= 0 && !isGenericRowEmpty(model, lastRow)) {
                        model.addRow(new Object[]{"", "", "", "", ""});
                    }
                    adjustAllRowHeights(table, 31);
                    relayoutGrowingTables();
                } finally {
                    updating[0] = false;
                }
            });
        }

        private boolean isGenericRowEmpty(DefaultTableModel model, int row) {
            if (model == null || row < 0 || row >= model.getRowCount()) return true;
            for (int col = 0; col < model.getColumnCount(); col++) {
                Object value = model.getValueAt(row, col);
                String text = value == null ? "" : value.toString().trim();
                // V190.26: nella tabella Production il solo prefisso automatico AL non rende la riga compilata.
                if (col == 0 && "AL".equalsIgnoreCase(text)) continue;
                if (!text.isEmpty()) return false;
            }
            return true;
        }

        private void installAutoWrapRows(JTable table, int baseHeight) {
            if (table == null) return;
            table.setRowHeight(baseHeight);
            for (int c = 0; c < table.getColumnCount(); c++) {
                TableCellRenderer existing = table.getColumnModel().getColumn(c).getCellRenderer();
                if (existing == null) existing = table.getDefaultRenderer(Object.class);
                if (!(existing instanceof WrapCellRenderer)) {
                    table.getColumnModel().getColumn(c).setCellRenderer(new WrapCellRenderer(existing));
                }
            }
            table.getModel().addTableModelListener(e -> SwingUtilities.invokeLater(() -> {
                adjustAllRowHeights(table, baseHeight);
                relayoutGrowingTables();
            }));
            SwingUtilities.invokeLater(() -> adjustAllRowHeights(table, baseHeight));
        }

        private void adjustAllRowHeights(JTable table, int baseHeight) {
            if (table == null) return;
            FontMetrics fm = table.getFontMetrics(table.getFont());
            for (int r = 0; r < table.getRowCount(); r++) {
                int maxHeight = baseHeight;
                for (int c = 0; c < table.getColumnCount(); c++) {
                    Object value = table.getValueAt(r, c);
                    String text = value == null ? "" : value.toString();
                    if (text.trim().isEmpty()) continue;
                    int colWidth = Math.max(20, table.getColumnModel().getColumn(c).getWidth() - 12);
                    if (table == poidsTable && (c == 2 || c == 4 || c == 5)) {
                        text = wrapAfterLastDashThatFits(text, fm, colWidth);
                    }
                    int lines = 0;
                    for (String part : text.split("\\n", -1)) {
                        int textWidth = fm.stringWidth(part);
                        lines += Math.max(1, (int) Math.ceil((double) textWidth / (double) colWidth));
                    }
                    maxHeight = Math.max(maxHeight, 8 + lines * fm.getHeight());
                }
                if (table.getRowHeight(r) != maxHeight) table.setRowHeight(r, maxHeight);
            }
        }

        private String wrapAfterLastDashThatFits(String text, FontMetrics fm, int maxWidth) {
            if (text == null || text.isEmpty() || fm == null || maxWidth <= 0) return text == null ? "" : text;
            String normalized = text.replace("\r", "").replace("\n", "");
            StringBuilder out = new StringBuilder();
            StringBuilder line = new StringBuilder();
            int i = 0;
            while (i < normalized.length()) {
                int dash = normalized.indexOf('-', i);
                String piece;
                if (dash >= 0) {
                    piece = normalized.substring(i, dash + 1);
                    i = dash + 1;
                } else {
                    piece = normalized.substring(i);
                    i = normalized.length();
                }

                if (line.length() > 0 && fm.stringWidth(line.toString() + piece) > maxWidth) {
                    out.append(line).append('\n');
                    line.setLength(0);
                }
                line.append(piece);
            }
            out.append(line);
            return out.toString();
        }

        private void relayoutGrowingTables() {
            if (productionTable != null) {
                int productionHeight = 0;
                for (int r = 0; r < productionTable.getRowCount(); r++) productionHeight += productionTable.getRowHeight(r);
                productionTable.setBounds(productionTable.getX(), productionTable.getY(), productionTable.getWidth(), productionHeight);
                currentProductionBottomY = productionTable.getY() + productionHeight;
                if (productionTotalPanel != null) {
                    productionTotalPanel.setBounds(productionTotalPanel.getX(), currentProductionBottomY, productionTotalPanel.getWidth(), productionTotalPanel.getHeight());
                }
                if (productionBottomPanel != null && productionTotalPanel != null) {
                    productionBottomPanel.setBounds(productionBottomPanel.getX(), productionTotalPanel.getY() + productionTotalPanel.getHeight() + 6, productionBottomPanel.getWidth(), productionBottomPanel.getHeight());
                }
            }

            int additionalShift = 0;
            Component productionAnchor = productionBottomPanel != null ? productionBottomPanel : productionTotalPanel;
            if (productionAnchor != null) {
                additionalShift = Math.max(0, productionAnchor.getY() + productionAnchor.getHeight() + 22 - additionalInfoBaseYStart);
            }
            int poidsHeight = 0;
            if (poidsTable != null) {
                for (int r = 0; r < poidsTable.getRowCount(); r++) poidsHeight += poidsTable.getRowHeight(r);
            }
            int basePoidsBottom = additionalInfoBaseYStart + 63 + (4 * 31);
            int poidsExtraShift = Math.max(0, poidsHeight - (4 * 31));

            for (Component component : additionalInfoComponents) {
                Integer baseY = additionalInfoBaseY.get(component);
                if (baseY != null) {
                    int rowWrapShift = baseY > basePoidsBottom ? poidsExtraShift : 0;
                    component.setBounds(component.getX(), baseY + additionalShift + rowWrapShift, component.getWidth(), component.getHeight());
                }
            }

            if (poidsTable != null) {
                poidsTable.setBounds(poidsTable.getX(), poidsTable.getY(), poidsTable.getWidth(), poidsHeight);
                currentPoidsBottomY = poidsTable.getY() + poidsHeight;
            }

            int maxY = 2400;
            for (Component component : getComponents()) {
                maxY = Math.max(maxY, component.getY() + component.getHeight() + 30);
            }
            setPreferredSize(new Dimension((int) Math.round(DESIGN_WIDTH * responsiveScaleX), maxY));
            setMinimumSize(new Dimension((int) Math.round(DESIGN_WIDTH * responsiveScaleX), maxY));
            refreshResponsiveBaselinesFromCurrentBounds();
            Container parent = getParent();
            if (parent instanceof JViewport) {
                updateResponsiveWidth(((JViewport) parent).getExtentSize().width);
            }
            revalidate();
            repaint();
        }

        private class WrapCellRenderer implements TableCellRenderer {
            private final TableCellRenderer delegate;
            WrapCellRenderer(TableCellRenderer delegate) { this.delegate = delegate; }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component base = delegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String text = value == null ? "" : value.toString();
                if (table == poidsTable && (column == 2 || column == 4 || column == 5)) {
                    FontMetrics metrics = table.getFontMetrics(base.getFont());
                    int width = Math.max(20, table.getColumnModel().getColumn(column).getWidth() - 12);
                    text = wrapAfterLastDashThatFits(text, metrics, width);
                }
                JTextArea area = new JTextArea(text);
                area.setFont(base.getFont());
                area.setForeground(base.getForeground());
                area.setBackground(base.getBackground());
                // Con il renderer a capo automatico (JTextArea) la griglia nativa di JTable
                // puo restare coperta dal componente renderer. Disegniamo quindi il bordo
                // della cella direttamente nel renderer, per mantenere visibile la griglia
                // anche senza JScrollPane.
                if (hasFocus) {
                    area.setBorder(new CompoundBorder(
                            new LineBorder(new Color(0x00, 0x40, 0x80), 2),
                            new EmptyBorder(0, 3, 0, 3)));
                } else {
                    area.setBorder(new CompoundBorder(
                            new MatteBorder(0, 0, 1, 1, Color.BLACK),
                            new EmptyBorder(0, 4, 0, 4)));
                }
                area.setLineWrap(true);
                area.setWrapStyleWord(false);
                area.setOpaque(true);
                area.setEditable(false);
                area.setFocusable(false);
                area.setMargin(new Insets(0, 4, 0, 4));
                return area;
            }
        }

        private boolean isProductionRowEmpty(DefaultTableModel model, int row) {
            for (int col = 0; col < model.getColumnCount(); col++) {
                Object value = model.getValueAt(row, col);
                String text = value == null ? "" : value.toString().trim();
                // V190.26: il solo prefisso automatico AL non deve essere considerato una riga compilata.
                if (col == 0 && "AL".equalsIgnoreCase(text)) continue;
                if (!text.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        private void refreshProductionTotalsFromTableClientProperties(JTable table) {
            if (table == null || !(table.getModel() instanceof DefaultTableModel)) return;
            Object mel = table.getClientProperty("productionTotalMelangesField");
            Object temps = table.getClientProperty("productionTotalTempsField");
            Object kg = table.getClientProperty("productionTotalKgField");
            if (mel instanceof JTextField && temps instanceof JTextField && kg instanceof JTextField) {
                updateProductionTotals((DefaultTableModel) table.getModel(), (JTextField) mel, (JTextField) temps, (JTextField) kg);
            }
        }

        private void updateProductionTotals(DefaultTableModel model, JTextField txtTotalMelanges,
                                            JTextField txtTotalTemps, JTextField txtTotalKg) {
            int totalMelanges = 0;
            int totalMinutes = 0;
            BigDecimal totalKg = BigDecimal.ZERO;

            for (int row = 0; row < model.getRowCount(); row++) {
                totalMelanges += parseIntegerValue(model.getValueAt(row, 3)) + parseIntegerValue(model.getValueAt(row, 4));
                totalMinutes += parseDurationValue(model.getValueAt(row, 13));
                totalKg = totalKg.add(parseKgValue(model.getValueAt(row, 7)));
            }

            txtTotalMelanges.setText(String.valueOf(totalMelanges));
            txtTotalTemps.setText(String.valueOf(totalMinutes));
            txtTotalKg.setText(totalKg.compareTo(BigDecimal.ZERO) == 0 ? "0 Kg" : formatKg(totalKg));
        }

        private BigDecimal parseKgValue(Object value) {
            if (value == null) return BigDecimal.ZERO;

            String text = value.toString()
                    .replace("Kg", "")
                    .replace("kg", "")
                    .replace(" ", "")
                    .replace(",", ".")
                    .trim();

            if (text.isEmpty()) return BigDecimal.ZERO;

            try {
                return new BigDecimal(text);
            } catch (Exception ex) {
                return BigDecimal.ZERO;
            }
        }

        private int parseIntegerValue(Object value) {
            if (value == null) {
                return 0;
            }

            String text = value.toString().trim();
            if (text.isEmpty()) {
                return 0;
            }

            try {
                return Integer.parseInt(text.replaceAll("[^0-9-]", ""));
            } catch (NumberFormatException ex) {
                return 0;
            }
        }

        private int parseDurationValue(Object value) {
            if (value == null) {
                return 0;
            }

            String text = value.toString().trim();
            if (text.isEmpty()) {
                return 0;
            }

            Integer parsedTime = parseFrenchTime(text);
            if (parsedTime != null && (text.toLowerCase().contains("h") || text.contains(":"))) {
                return parsedTime;
            }

            try {
                return Integer.parseInt(text.replaceAll("[^0-9-]", ""));
            } catch (NumberFormatException ex) {
                return 0;
            }
        }



        private void installProductionDbLookup(JTable table, DefaultTableModel model) {
            if (table == null || model == null) {
                return;
            }

            final boolean[] updating = {false};

            model.addTableModelListener(e -> {
                if (updating[0]
                        || loadingShiftState
                        || Boolean.TRUE.equals(table.getClientProperty("suspendProductionDbLookup"))
                        || Boolean.TRUE.equals(table.getClientProperty("applyingPassageSplit"))
                        || Boolean.TRUE.equals(table.getClientProperty("suppressProductionDbLookupDuringPrint"))) {
                    return;
                }
                int row = e.getFirstRow();
                int col = e.getColumn();

                if (row < 0 || row >= model.getRowCount()) {
                    return;
                }

                if (col == 3 && Boolean.TRUE.equals(table.getClientProperty("ignoreNextProductionDbLookupModelEvent:" + row))) {
                    // V193.32: evento generato dalla chiusura editor di Nombre de Melanges.
                    // Il refresh viene gia lanciato esplicitamente da NombreMelangesCellEditor.
                    table.putClientProperty("ignoreNextProductionDbLookupModelEvent:" + row, Boolean.FALSE);
                    return;
                }

                // V193.26: dopo il caricamento da salvataggio ignora tutti gli eventi
                // automatici della tabella. Si riabilita solo per la riga su cui l'utente
                // conferma manualmente Nombre de Melanges.
                if (Boolean.TRUE.equals(table.getClientProperty("suppressProductionDbLookupUntilUserNombre"))
                        && !Boolean.TRUE.equals(table.getClientProperty("allowProductionDbLookupOnce:" + row))) {
                    return;
                }

                if (col != 0 && col != 2 && col != 3) {
                    return;
                }

                final int targetRow = row;
                final int targetCol = col;

                // V193.24: l'evento TableModel puo arrivare quando JTable risulta ancora in editing.
                // In quel caso non perdere il lookup: lo rimandiamo a subito dopo la chiusura editor.
                if (table.isEditing()) {
                    scheduleProductionLookupAfterEditing(table, model, targetRow, targetCol);
                    return;
                }

                normalizeAndRunProductionLookup(table, model, targetRow, targetCol);
            });
        }


        private void scheduleProductionLookupAfterEditing(JTable table, DefaultTableModel model, int row, int col) {
            if (table == null || model == null) return;
            Timer timer = new Timer(40, null);
            timer.addActionListener(new ActionListener() {
                private int attempts = 0;
                @Override public void actionPerformed(ActionEvent e) {
                    attempts++;
                    if (!table.isEditing()) {
                        timer.stop();
                        if (!Boolean.TRUE.equals(table.getClientProperty("suspendProductionDbLookup"))
                                && (!Boolean.TRUE.equals(table.getClientProperty("suppressProductionDbLookupUntilUserNombre"))
                                    || Boolean.TRUE.equals(table.getClientProperty("allowProductionDbLookupOnce:" + row)))) {
                            normalizeAndRunProductionLookup(table, model, row, col);
                        }
                    } else if (attempts >= 25) {
                        timer.stop();
                    }
                }
            });
            timer.setRepeats(true);
            timer.start();
        }

        private void normalizeAndRunProductionLookup(JTable table, DefaultTableModel model, int row, int col) {
            if (table == null || model == null || row < 0 || row >= model.getRowCount()) return;
            if (Boolean.TRUE.equals(table.getClientProperty("suspendProductionDbLookup"))) return;
            if (Boolean.TRUE.equals(table.getClientProperty("suppressProductionDbLookupDuringPrint"))) return;
            if (Boolean.TRUE.equals(table.getClientProperty("applyingPassageSplit"))) return;

            // V193.24: non riscrivere qui il model mentre gestiamo il suo evento:
            // evita eventi ricorsivi e doppie aperture della finestra DB.

            // V193.24: se Poids e gia valorizzato, non riaprire la finestra DB.
            // Eccezione: se l'utente riscrive Nombre de Melanges, allora forza il ricalcolo.
            boolean forceRecalculate = (col == 3);
            refreshProductionPassageFromRowOnly(table, row, forceRecalculate);
        }


        private void refreshProductionPassages(JTable table) {
            if (resettingForm || table == null || !(table.getModel() instanceof DefaultTableModel)) return;
            if (Boolean.TRUE.equals(table.getClientProperty("refreshingPassages"))) return;

            table.putClientProperty("refreshingPassages", Boolean.TRUE);
            try {
                DefaultTableModel model = (DefaultTableModel) table.getModel();
                Map<String, Integer> usedByArticleLotCouche = new HashMap<>();
                Map<String, Integer> rowIndexByArticleLot = new HashMap<>();

                for (int row = 0; row < model.getRowCount(); row++) {
                    String article = valueAt(model, row, 0).toUpperCase(Locale.ROOT);
                    String lot = valueAt(model, row, 2).toUpperCase(Locale.ROOT);
                    String couche = normalizeCouche(valueAt(model, row, 5));
                    int nombreMelanges = parseIntegerValue(model.getValueAt(row, 3));

                    clearPassageWeights(model, row);
                    if (article.isEmpty() || lot.isEmpty() || nombreMelanges <= 0 || article.startsWith("ARRET")) continue;

                    String articleLotKey = article + "\u0001" + lot;

                    // NUOVA GESTIONE KG:
                    // La ricerca delle pesate NON forza piu la COUCHE a SUP/INF.
                    // Cerca solo per PRODUIT + LOT + turno corrente e prende le pesate in ordine cronologico.
                    // Se esistono piu pesate e Nombre de Melanges = 1, viene presa la piu vecchia.
                    int skipCount = usedByArticleLotCouche.getOrDefault(articleLotKey, 0);
                    usedByArticleLotCouche.put(articleLotKey, skipCount + nombreMelanges);
                    lookupProductionPassagesForRow(table, row, article, lot, "", skipCount, nombreMelanges);
                }
            } finally {
                table.putClientProperty("refreshingPassages", Boolean.FALSE);
            }
        }

        private void refreshProductionPassageFromRowOnly(JTable table, int modelRow) {
            refreshProductionPassageFromRowOnly(table, modelRow, false);
        }

        private void refreshProductionPassageFromRowOnly(JTable table, int modelRow, boolean forceRecalculate) {
            if (resettingForm || loadingShiftState || table == null || !(table.getModel() instanceof DefaultTableModel)) return;
            if (Boolean.TRUE.equals(table.getClientProperty("refreshingPassages"))) return;
            if (Boolean.TRUE.equals(table.getClientProperty("suspendProductionDbLookup"))) return;
            if (Boolean.TRUE.equals(table.getClientProperty("suppressProductionDbLookupDuringPrint"))) return;

            boolean allowedOnce = Boolean.TRUE.equals(table.getClientProperty("allowProductionDbLookupOnce:" + modelRow));
            if (Boolean.TRUE.equals(table.getClientProperty("suppressProductionDbLookupUntilUserNombre")) && !allowedOnce) {
                return;
            }

            DefaultTableModel model = (DefaultTableModel) table.getModel();
            if (modelRow < 0 || modelRow >= model.getRowCount()) return;
            if (allowedOnce) {
                table.putClientProperty("allowProductionDbLookupOnce:" + modelRow, Boolean.FALSE);
            }

            String article = valueAt(model, modelRow, 0).toUpperCase(Locale.ROOT);
            String lot = valueAt(model, modelRow, 2).toUpperCase(Locale.ROOT);
            String couche = normalizeCouche(valueAt(model, modelRow, 5));
            int nombreMelanges = parseIntegerValue(model.getValueAt(modelRow, 3));

            // V106: la finestra "Controllo pesate DB prima della somma" e la query DB
            // devono partire solo dopo conferma dei 3 campi obbligatori della riga.
            if (article.isEmpty() || lot.isEmpty() || nombreMelanges <= 0 || article.startsWith("ARRET")) {
                table.putClientProperty("lastConfirmedProductionLookupKey:" + modelRow, null);
                return;
            }

            // V193.24: se il peso e gia presente, non rifare il calcolo e non riaprire la finestra.
            // Si forza solo quando l'utente conferma/riscrive Nombre de Melanges.
            if (!forceRecalculate && !valueAt(model, modelRow, 7).trim().isEmpty()) {
                table.putClientProperty("lastConfirmedProductionLookupKey:" + modelRow,
                        productionLookupRowKey(article, lot, couche, nombreMelanges));
                return;
            }

            // V190.31: dopo l'OK del controllo pesate, alcuni eventi JTable/stopCellEditing
            // possono richiamare subito la stessa ricerca sulla stessa riga. Se i campi chiave
            // non sono cambiati, non riaprire una seconda finestra consecutiva.
            String rowLookupKey = productionLookupRowKey(article, lot, couche, nombreMelanges);
            Object lastConfirmedKey = table.getClientProperty("lastConfirmedProductionLookupKey:" + modelRow);
            if (!forceRecalculate && rowLookupKey.equals(lastConfirmedKey)) {
                return;
            }

            clearPassageWeights(model, modelRow);
            clearUsedPesageIdsForRow(table, modelRow);

            java.util.Set<String> excludedIds = collectUsedPesageIdsFromOtherRows(table, model, modelRow, article, lot);

            table.putClientProperty("refreshingPassages", Boolean.TRUE);
            try {
                lookupProductionPassagesForRow(table, modelRow, article, lot, couche, 0, nombreMelanges, excludedIds);
            } finally {
                table.putClientProperty("refreshingPassages", Boolean.FALSE);
            }
        }

        private java.util.Set<String> collectUsedPesageIdsFromOtherRows(JTable table, DefaultTableModel model,
                                                                     int modelRow, String article, String lot) {
            java.util.Set<String> excludedIds = new java.util.LinkedHashSet<>();
            if (table == null || model == null) return excludedIds;

            String targetArticle = article == null ? "" : article.trim().toUpperCase(Locale.ROOT);
            String targetLot = lot == null ? "" : lot.trim().toUpperCase(Locale.ROOT);

            // V193.43: elenco ID ricostruito da zero ad ogni apertura della finestra,
            // ma usando SOLO le righe gia registrate sopra la riga corrente. Le pesate DB
            // successive alla riga in compilazione devono restare disponibili: se ad esempio
            // la prima riga usa 1704 e la riga corrente chiede 2 melanges, la finestra deve
            // proporre 1707 e 1708, non solo 1707.
            for (int r = 0; r < modelRow && r < model.getRowCount(); r++) {

                String rowArticle = valueAt(model, r, 0).trim().toUpperCase(Locale.ROOT);
                String rowLot = valueAt(model, r, 2).trim().toUpperCase(Locale.ROOT);
                if (!targetArticle.equals(rowArticle) || !targetLot.equals(rowLot)) continue;

                if (!isStoredPesageRowStillVisible(table, model, r)) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                java.util.Set<String> rowIds = (java.util.Set<String>) table.getClientProperty("usedPesageIds:" + r);
                if (rowIds != null) {
                    for (String id : rowIds) {
                        if (id != null && !id.trim().isEmpty()) excludedIds.add(id.trim());
                    }
                }
            }

            return excludedIds;
        }

        private boolean isStoredPesageRowStillVisible(JTable table, DefaultTableModel model, int row) {
            if (table == null || model == null || row < 0 || row >= model.getRowCount()) return false;

            @SuppressWarnings("unchecked")
            java.util.Set<String> rowIds = (java.util.Set<String>) table.getClientProperty("usedPesageIds:" + row);
            if (rowIds == null || rowIds.isEmpty()) return false;

            Object signatureObj = table.getClientProperty("usedPesageRowSignature:" + row);
            String storedSignature = signatureObj == null ? "" : signatureObj.toString();
            String currentSignature = currentProductionRowPesageSignature(model, row);

            return !storedSignature.trim().isEmpty() && storedSignature.equals(currentSignature);
        }

        private String currentProductionRowPesageSignature(DefaultTableModel model, int row) {
            if (model == null || row < 0 || row >= model.getRowCount()) return "";
            return (valueAt(model, row, 0).trim().toUpperCase(Locale.ROOT))
                    + "\u0001" + (valueAt(model, row, 2).trim().toUpperCase(Locale.ROOT))
                    + "\u0001" + normalizeCouche(valueAt(model, row, 5))
                    + "\u0001" + normalizePassage(valueAt(model, row, 6))
                    + "\u0001" + valueAt(model, row, 9).trim().toUpperCase(Locale.ROOT)
                    + "\u0001" + parseKgValue(valueAt(model, row, 7)).stripTrailingZeros().toPlainString();
        }

        private String usedPesageArticleLotKey(String article, String lot) {
            return (article == null ? "" : article.trim().toUpperCase(Locale.ROOT))
                    + "\u0001" + (lot == null ? "" : lot.trim().toUpperCase(Locale.ROOT));
        }

        @SuppressWarnings("unchecked")
        private java.util.Map<String, java.util.Set<String>> getUsedPesageIdsByArticleLot(JTable table) {
            java.util.Map<String, java.util.Set<String>> map =
                    (java.util.Map<String, java.util.Set<String>>) table.getClientProperty("usedPesageIdsByArticleLot");
            if (map == null) {
                map = new java.util.HashMap<>();
                table.putClientProperty("usedPesageIdsByArticleLot", map);
            }
            return map;
        }

        private void storeUsedPesageIdsForRow(JTable table, DefaultTableModel model, int modelRow, PassageWeights weights) {
            if (table == null || model == null || weights == null || modelRow < 0 || modelRow >= model.getRowCount()) return;

            clearUsedPesageIdsForRow(table, modelRow);

            java.util.Set<String> ids = new java.util.LinkedHashSet<>();
            for (PassageWeightRow row : weights.rows) {
                if (row != null && row.idPesage != null && !row.idPesage.trim().isEmpty()) {
                    ids.add(row.idPesage.trim());
                }
            }

            String key = usedPesageArticleLotKey(valueAt(model, modelRow, 0), valueAt(model, modelRow, 2));
            table.putClientProperty("usedPesageIds:" + modelRow, ids);
            table.putClientProperty("usedPesageIdsKey:" + modelRow, key);
            table.putClientProperty("usedPesageRowSignature:" + modelRow, currentProductionRowPesageSignature(model, modelRow));

            // V193.41: niente piu registro globale da mantenere. Gli ID esclusi vengono
            // ricostruiti ogni volta scansionando solo le righe realmente visibili.
        }

        private void clearUsedPesageIdsForRow(JTable table, int modelRow) {
            if (table == null || modelRow < 0) return;

            @SuppressWarnings("unchecked")
            java.util.Set<String> oldIds = (java.util.Set<String>) table.getClientProperty("usedPesageIds:" + modelRow);
            Object oldKeyObj = table.getClientProperty("usedPesageIdsKey:" + modelRow);
            String oldKey = oldKeyObj == null ? "" : oldKeyObj.toString();

            if (oldIds != null && !oldIds.isEmpty() && !oldKey.isEmpty()) {
                java.util.Map<String, java.util.Set<String>> byArticleLot = getUsedPesageIdsByArticleLot(table);
                java.util.Set<String> globalIds = byArticleLot.get(oldKey);
                if (globalIds != null) {
                    globalIds.removeAll(oldIds);
                    if (globalIds.isEmpty()) byArticleLot.remove(oldKey);
                }
            }

            table.putClientProperty("usedPesageIds:" + modelRow, null);
            table.putClientProperty("usedPesageIdsKey:" + modelRow, null);
            table.putClientProperty("usedPesageRowSignature:" + modelRow, null);
        }

        private String productionLookupRowKey(String article, String lot, String couche, int nombreMelanges) {
            return (article == null ? "" : article.trim().toUpperCase(Locale.ROOT))
                    + "\u0001" + (lot == null ? "" : lot.trim().toUpperCase(Locale.ROOT))
                    + "\u0001" + normalizeCouche(couche)
                    + "\u0001" + nombreMelanges;
        }

        private boolean isProductionLookupReady(DefaultTableModel model, int modelRow) {
            if (model == null || modelRow < 0 || modelRow >= model.getRowCount()) {
                return false;
            }

            String article = valueAt(model, modelRow, 0).trim().toUpperCase(Locale.ROOT);
            String lot = valueAt(model, modelRow, 2).trim().toUpperCase(Locale.ROOT);
            int nombreMelanges = parseIntegerValue(model.getValueAt(modelRow, 3));

            return !article.isEmpty()
                    && !article.startsWith("ARRET")
                    && !lot.isEmpty()
                    && nombreMelanges > 0;
        }

        private void lookupProductionPassagesForRow(JTable table, int modelRow) {
            refreshProductionPassages(table);
        }

        private void lookupProductionPassagesForRow(JTable table, int modelRow, String article, String lot,
                                                    String couche, int skipCount, int nombreMelanges) {
            lookupProductionPassagesForRow(table, modelRow, article, lot, couche, skipCount, nombreMelanges,
                    new java.util.LinkedHashSet<>());
        }

        private int removePassageWeightsAlreadyDisplayed(JTable table, DefaultTableModel model, int modelRow,
                                                         String article, String lot, PassageWeights weights) {
            if (table == null || model == null || weights == null || weights.rows == null || weights.rows.isEmpty()) {
                return 0;
            }

            String targetArticle = article == null ? "" : article.trim().toUpperCase(Locale.ROOT);
            String targetLot = lot == null ? "" : lot.trim().toUpperCase(Locale.ROOT);

            // V193.43: oltre agli ID_PESAGE salvati come clientProperty, filtra anche le
            // pesate gia visibili, ma SOLO nelle righe sopra quella corrente. Le righe sotto
            // non sono ancora considerate registrate per questa ricerca e non devono togliere
            // disponibilita alle pesate successive trovate nel DB.
            java.util.Map<String, Integer> visibleSignatures = buildDisplayedPassageWeightSignatureCounts(
                    model, modelRow, targetArticle, targetLot);
            if (visibleSignatures.isEmpty()) {
                return 0;
            }

            int removed = 0;
            java.util.Iterator<PassageWeightRow> it = weights.rows.iterator();
            while (it.hasNext()) {
                PassageWeightRow dbRow = it.next();
                String signature = displayedPassageWeightSignature(dbRow);
                Integer remaining = visibleSignatures.get(signature);
                if (remaining != null && remaining > 0) {
                    it.remove();
                    removed++;
                    if (remaining == 1) visibleSignatures.remove(signature);
                    else visibleSignatures.put(signature, remaining - 1);
                }
            }

            if (removed > 0) {
                rebuildPassageWeightsTotals(weights);
            }
            return removed;
        }

        private java.util.Map<String, Integer> buildDisplayedPassageWeightSignatureCounts(DefaultTableModel model, int modelRow,
                                                                                          String targetArticle, String targetLot) {
            java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
            if (model == null) return counts;

            // V193.43: controlla solo le righe gia compilate sopra quella corrente.
            // Non filtrare le righe successive, altrimenti una pesata DB successiva puo
            // sparire dalla finestra anche se deve essere ancora proposta.
            for (int r = 0; r < modelRow && r < model.getRowCount(); r++) {

                String rowArticle = valueAt(model, r, 0).trim().toUpperCase(Locale.ROOT);
                String rowLot = valueAt(model, r, 2).trim().toUpperCase(Locale.ROOT);
                if (!targetArticle.equals(rowArticle) || !targetLot.equals(rowLot)) continue;

                String rowSignature = displayedPassageWeightSignature(
                        normalizePassage(valueAt(model, r, 6)),
                        normalizeCouche(valueAt(model, r, 5)),
                        valueAt(model, r, 9).trim().toUpperCase(Locale.ROOT),
                        parseKgValue(valueAt(model, r, 7))
                );
                if (!rowSignature.trim().isEmpty()) {
                    counts.put(rowSignature, counts.getOrDefault(rowSignature, 0) + 1);
                }
            }
            return counts;
        }

        private String displayedPassageWeightSignature(PassageWeightRow row) {
            if (row == null) return "";
            return displayedPassageWeightSignature(
                    normalizePassage(row.passage),
                    normalizeCouche(row.couche),
                    formatSerieFromMelange(row.melange).trim().toUpperCase(Locale.ROOT),
                    row.net == null ? BigDecimal.ZERO : row.net
            );
        }

        private String displayedPassageWeightSignature(String passage, String couche, String serie, BigDecimal kg) {
            BigDecimal normalizedKg = kg == null ? BigDecimal.ZERO : kg.stripTrailingZeros();
            return (passage == null ? "" : passage.trim().toUpperCase(Locale.ROOT))
                    + "\u0001" + (couche == null ? "" : couche.trim().toUpperCase(Locale.ROOT))
                    + "\u0001" + (serie == null ? "" : serie.trim().toUpperCase(Locale.ROOT))
                    + "\u0001" + normalizedKg.toPlainString();
        }

        private BigDecimal parseKgValue(String value) {
            if (value == null) return BigDecimal.ZERO;
            String text = value.trim().replace("Kg", "").replace("kg", "").replace(" ", "").replace(',', '.');
            if (text.isEmpty()) return BigDecimal.ZERO;
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException ex) {
                return BigDecimal.ZERO;
            }
        }

        private void rebuildPassageWeightsTotals(PassageWeights weights) {
            if (weights == null) return;
            weights.passage1 = BigDecimal.ZERO;
            weights.passage2 = BigDecimal.ZERO;
            weights.passage3 = BigDecimal.ZERO;
            weights.passage4 = BigDecimal.ZERO;
            weights.couche = "";
            weights.melange = "";

            for (PassageWeightRow row : weights.rows) {
                if (row == null) continue;
                if (weights.couche.trim().isEmpty()) weights.couche = normalizeCouche(row.couche);
                if (weights.melange.trim().isEmpty()) weights.melange = row.melange == null ? "" : row.melange.trim();
                String passage = normalizePassage(row.passage);
                if ("1".equals(passage)) weights.passage1 = weights.passage1.add(row.net);
                else if ("2".equals(passage)) weights.passage2 = weights.passage2.add(row.net);
                else if ("3".equals(passage)) weights.passage3 = weights.passage3.add(row.net);
                else if ("4".equals(passage)) weights.passage4 = weights.passage4.add(row.net);
            }
        }

        private void lookupProductionPassagesForRow(JTable table, int modelRow, String article, String lot,
                                                    String couche, int skipCount, int nombreMelanges,
                                                    java.util.Set<String> excludedPesageIds) {
            if (table == null || !(table.getModel() instanceof DefaultTableModel)) return;
            DefaultTableModel model = (DefaultTableModel) table.getModel();

            String excludedSignature = excludedPesageIds == null ? "" : new java.util.TreeSet<String>(excludedPesageIds).toString();
            String activeLookupKey = modelRow + "" + article + "" + lot + ""
                    + normalizeCouche(couche) + "" + skipCount + "" + nombreMelanges + "" + excludedSignature;
            @SuppressWarnings("unchecked")
            java.util.Set<String> activeLookups = (java.util.Set<String>) table.getClientProperty("activeProductionDbLookupRequests");
            if (activeLookups == null) {
                activeLookups = new java.util.HashSet<>();
                table.putClientProperty("activeProductionDbLookupRequests", activeLookups);
            }
            if (!activeLookups.add(activeLookupKey)) {
                return;
            }

            new SwingWorker<PassageWeights, Void>() {
                private String error;
                @Override protected PassageWeights doInBackground() {
                    try { return loadPassageWeights(article, lot, couche, skipCount, nombreMelanges, excludedPesageIds); }
                    catch (Exception ex) { error = ex.getMessage() != null ? ex.getMessage() : ex.toString(); return new PassageWeights(); }
                }
                @Override protected void done() {
                    try {
                        PassageWeights weights = get();
                        if (!article.equals(valueAt(model, modelRow, 0).toUpperCase(Locale.ROOT))
                                || !lot.equals(valueAt(model, modelRow, 2).toUpperCase(Locale.ROOT))
                                || !isProductionLookupReady(model, modelRow)
                                || parseIntegerValue(model.getValueAt(modelRow, 3)) != nombreMelanges) return;
                        int excludedCount = excludedPesageIds == null ? 0 : excludedPesageIds.size();
                        int alreadyDisplayedCount = removePassageWeightsAlreadyDisplayed(table, model, modelRow, article, lot, weights);
                        excludedCount += alreadyDisplayedCount;
                        // Il dialog di controllo mostra tutte le pesate DB trovate per
                        // Article/Lot (nel turno, gia depurate delle pesate gia registrate),
                        // senza troncarle al numero di Melanges richiesti sulla riga.
                        if (weights == null || weights.rows.isEmpty()) {
                            clearPassageWeights(model, modelRow);
                            clearUsedPesageIdsForRow(table, modelRow);
                            forceProductionTableImmediateRefresh(table, model, modelRow, modelRow);

                            NoPassageWeightsAction action = showNoPassageWeightsDialog(
                                    table, article, lot, couche, nombreMelanges, getSelectedBanburyProvenance(), error);

                            if (action == NoPassageWeightsAction.EQUIPE_SUIVANTE_ZERO) {
                                applyNoPassageWeightsEquipeSuivanteZero(table, model, modelRow, article, lot, couche, nombreMelanges);
                                table.putClientProperty("lastConfirmedProductionLookupKey:" + modelRow,
                                        productionLookupRowKey(article, lot, couche, nombreMelanges));
                            } else if (action == NoPassageWeightsAction.EN_ATTENTE) {
                                table.putClientProperty("lastConfirmedProductionLookupKey:" + modelRow, null);
                            }
                            return;
                        }
                        if (!confirmPassageWeightsBeforeApply(table, article, lot, couche, skipCount, nombreMelanges, excludedCount, weights)) {
                            return;
                        }

                        applyPassageWeightsSplitByCouche(table, model, modelRow, weights);
                        if (weights.enAttente) {
                            table.putClientProperty("lastConfirmedProductionLookupKey:" + modelRow, null);
                        } else {
                            table.putClientProperty("lastConfirmedProductionLookupKey:" + modelRow,
                                    productionLookupRowKey(article, lot, couche, nombreMelanges));
                        }
                        if (error != null) System.err.println("Erreur chargement poids passages article " + article + " lot " + lot + ": " + error);
                    } catch (Exception ex) {
                        System.err.println("Erreur SwingWorker poids passages article " + article + " lot " + lot + ": " + ex.getMessage());
                    } finally {
                        @SuppressWarnings("unchecked")
                        java.util.Set<String> activeLookups = (java.util.Set<String>) table.getClientProperty("activeProductionDbLookupRequests");
                        if (activeLookups != null) {
                            activeLookups.remove(activeLookupKey);
                        }
                    }
                }
            }.execute();
        }

        private void forceProductionTableImmediateRefresh(JTable table, DefaultTableModel model, int firstRow, int lastRow) {
            if (table == null || model == null) {
                return;
            }

            if (firstRow < 0) firstRow = 0;
            if (lastRow < firstRow) lastRow = firstRow;
            if (lastRow >= model.getRowCount()) lastRow = model.getRowCount() - 1;

            final int finalFirstRow = firstRow;
            final int finalLastRow = lastRow;

            Runnable refresh = () -> {
                // IMPORTANTE:
                // dopo OK della finestra di controllo, la JTable puo avere ancora aperto
                // l'editor della cella "Passage". Se non lo chiudiamo con cancelCellEditing(),
                // l'editor vuoto resta disegnato sopra al valore appena scritto nel model.
                if (table.isEditing()) {
                    int editingViewRow = table.getEditingRow();
                    int editingViewCol = table.getEditingColumn();
                    int editingModelRow = editingViewRow >= 0 ? table.convertRowIndexToModel(editingViewRow) : -1;
                    int editingModelCol = editingViewCol >= 0 ? table.convertColumnIndexToModel(editingViewCol) : -1;
                    // V193.20: non cancellare mai l'editor ARTICLE e non toccare editor aperti su altre righe.
                    // Un refresh arrivato dalla Production réalisée non deve interrompere la modifica manuale.
                    if (editingModelCol == 0 || editingModelRow < finalFirstRow || editingModelRow > finalLastRow) {
                        table.repaint();
                        return;
                    }
                    TableCellEditor editor = table.getCellEditor();
                    if (editor != null) {
                        editor.cancelCellEditing();
                    }
                }

                model.fireTableRowsUpdated(finalFirstRow, finalLastRow);

                // Non spostare il focus sulla cella peso/passaggio appena aggiornata.
                // La selezione forzata qui riportava il cursore sulla riga precedente
                // quando l'operatore iniziava a compilare la riga successiva.
                // Manteniamo quindi la cella corrente scelta dall'utente.
                Rectangle rect = table.getCellRect(finalFirstRow, 0, true);
                rect = rect.union(table.getCellRect(finalLastRow, table.getColumnCount() - 1, true));

                table.revalidate();
                table.repaint(rect);
                table.repaint();

                Container parent = table.getParent();
                if (parent != null) {
                    parent.invalidate();
                    parent.validate();
                    parent.repaint();
                }

                Component dialogParent = getDialogParentComponent();
                if (dialogParent != null) {
                    dialogParent.invalidate();
                    dialogParent.validate();
                    dialogParent.repaint();
                }
            };

            if (SwingUtilities.isEventDispatchThread()) {
                refresh.run();
                SwingUtilities.invokeLater(refresh);
            } else {
                SwingUtilities.invokeLater(refresh);
            }
        }

        private void applyPassageWeightsSplitByCouche(JTable table, DefaultTableModel model, int modelRow, PassageWeights weights) {
            if (table == null || model == null || weights == null || modelRow < 0 || modelRow >= model.getRowCount()) {
                return;
            }

            if (table.isEditing()) {
                TableCellEditor editor = table.getCellEditor();
                if (editor != null) {
                    editor.cancelCellEditing();
                }
            }

            java.util.List<PassageWeights> byCouche = splitPassageWeightsByCoucheAndPassage(weights);
            if (byCouche.isEmpty()) {
                model.setValueAt("", modelRow, 4);
                model.setValueAt("", modelRow, 5);
                model.setValueAt("", modelRow, 6);
                forceProductionTableImmediateRefresh(table, model, modelRow, modelRow);
                return;
            }

            table.putClientProperty("applyingPassageSplit", Boolean.TRUE);
            final int[] lastUpdatedRow = {modelRow};
            try {
                String article = valueAt(model, modelRow, 0);
                String produit = valueAt(model, modelRow, 1);
                String serie = valueAt(model, modelRow, 2);
                String nombreMelangesOriginal = valueAt(model, modelRow, 3);
                String couleur = valueAt(model, modelRow, 8);
                String nSerie = valueAt(model, modelRow, 9);
                String remarq = valueAt(model, modelRow, 10);
                String debut = valueAt(model, modelRow, 11);
                String fin = valueAt(model, modelRow, 12);

                // Rimuove solo le righe di split generate sotto la riga attuale.
                // Non tocca mai le righe precedenti: cambiare Nombre de Melanges su una riga duplicata
                // deve influire solo su quella riga e sulle sue eventuali righe successive.
                int removeRow = modelRow + 1;
                while (removeRow < model.getRowCount()
                        && article.equalsIgnoreCase(valueAt(model, removeRow, 0))
                        && serie.equalsIgnoreCase(valueAt(model, removeRow, 2))
                        && Boolean.TRUE.equals(table.getClientProperty("splitRow:" + removeRow))) {
                    clearUsedPesageIdsForRow(table, removeRow);
                    model.removeRow(removeRow);
                }

                for (int i = 0; i < byCouche.size(); i++) {
                    PassageWeights coucheWeights = byCouche.get(i);
                    int targetRow = modelRow + i;

                    if (i > 0) {
                        model.insertRow(targetRow, new Object[]{article, produit, serie, nombreMelangesOriginal, "", "", "", "", couleur, nSerie, remarq, debut, fin, "", "", ""});
                        table.putClientProperty("splitRow:" + targetRow, Boolean.TRUE);
                    }

                    model.setValueAt(article, targetRow, 0);
                    model.setValueAt(produit, targetRow, 1);
                    model.setValueAt(serie, targetRow, 2);
                    // Quando la riga viene sdoppiata per SUP/INF o per passaggi diversi,
                    // copia lo stesso NOMBRE DE MELANGES anche sulle righe duplicate.
                    // Il campo resta comunque modificabile manualmente dall'operatore.
                    // Se viene cambiato su una riga duplicata, la logica V93 ricalcola
                    // solo dalla riga attuale in poi, senza influire sulla riga sopra.
                    model.setValueAt(nombreMelangesOriginal, targetRow, 3);
                    model.setValueAt(!coucheWeights.couche.isEmpty() ? coucheWeights.couche : weights.couche, targetRow, 5);
                    model.setValueAt(displayPassage(coucheWeights.displayPassage()), targetRow, 6);
                    model.setValueAt(formatKg(coucheWeights.total()), targetRow, 7);
                    model.setValueAt(couleur, targetRow, 8);
                    String serieFromMelange = formatSerieFromMelange(coucheWeights.firstValidMelange());
                    model.setValueAt(!serieFromMelange.isEmpty() ? serieFromMelange : nSerie, targetRow, 9);
                    model.setValueAt(remarq, targetRow, 10);
                    model.setValueAt(debut, targetRow, 11);
                    model.setValueAt(fin, targetRow, 12);
                    updateProductionDuration(model, targetRow);
                    storeUsedPesageIdsForRow(table, model, targetRow, coucheWeights);
                    lastUpdatedRow[0] = Math.max(lastUpdatedRow[0], targetRow);
                }

                model.fireTableRowsUpdated(modelRow, lastUpdatedRow[0]);
            } finally {
                table.putClientProperty("applyingPassageSplit", Boolean.FALSE);

                // Dopo OK nella finestra di controllo pesate, forza subito il ridisegno
                // e chiude l'editor vuoto che puo coprire il valore scritto nel model.
                forceProductionTableImmediateRefresh(table, model, modelRow, lastUpdatedRow[0]);
            }
        }

        private java.util.List<PassageWeights> splitPassageWeightsByCoucheAndPassage(PassageWeights weights) {
            java.util.LinkedHashMap<String, PassageWeights> grouped = new java.util.LinkedHashMap<>();
            if (weights == null || weights.rows == null || weights.rows.isEmpty()) {
                return new ArrayList<>();
            }

            for (PassageWeightRow row : weights.rows) {
                String rowCouche = normalizeCouche(row.couche);
                if (rowCouche.isEmpty()) {
                    rowCouche = weights.couche == null || weights.couche.trim().isEmpty() ? "" : normalizeCouche(weights.couche);
                }

                String passage = normalizePassage(row.passage);
                if (!"1".equals(passage) && !"2".equals(passage) && !"3".equals(passage) && !"4".equals(passage)) {
                    passage = "";
                }

                String rowSerie = formatSerieFromMelange(row.melange);

                // V193.07:
                // Sommare nella stessa riga tutte le pesate selezionate che hanno stesso
                // Article/Lot, Couche e Passage. La colonna MELANGE/SERIE del DB non deve
                // creare righe separate: in alcuni casi una pesata ha MELANGE valorizzato
                // (es. L1) e l'altra lo ha vuoto, ma appartengono allo stesso lotto e vanno
                // sommate insieme.
                String key = rowCouche + "\u0001" + passage;

                PassageWeights target = grouped.get(key);
                if (target == null) {
                    target = new PassageWeights();
                    target.couche = rowCouche;
                    target.melange = rowSerie.isEmpty() ? "" : row.melange;
                    target.filteredByCurrentShift = weights.filteredByCurrentShift;
                    grouped.put(key, target);
                }

                target.rows.add(row);
                if (target.melange.isEmpty() && !rowSerie.isEmpty()) target.melange = row.melange;
                if ("1".equals(passage)) target.passage1 = target.passage1.add(row.net);
                else if ("2".equals(passage)) target.passage2 = target.passage2.add(row.net);
                else if ("3".equals(passage)) target.passage3 = target.passage3.add(row.net);
                else if ("4".equals(passage)) target.passage4 = target.passage4.add(row.net);
            }

            java.util.List<PassageWeights> result = new ArrayList<>();
            result.addAll(grouped.values());
            return result;
        }

        private PassageWeights loadPassageWeights(String article, String lot, String requestedCouche,
                                                  int skipCount, int takeCount) throws Exception {
            return loadPassageWeights(article, lot, requestedCouche, skipCount, takeCount,
                    new java.util.LinkedHashSet<>());
        }

        private PassageWeights loadPassageWeights(String article, String lot, String requestedCouche,
                                                  int skipCount, int takeCount,
                                                  java.util.Set<String> excludedPesageIds) throws Exception {
            // NUOVA GESTIONE KG:
            // niente fallback fuori turno e niente filtro COUCHE.
            // La selezione e sempre nel turno corrente, per Article/Lot, in ordine dalla pesata piu vecchia.
            // Per il ricalcolo mirato dell'ultima riga, excludedPesageIds evita di riusare
            // le pesate DB gia assegnate alle righe precedenti.
            return loadPassageWeightsInternal(article, lot, "", skipCount, takeCount, true, excludedPesageIds);
        }

        private boolean confirmPassageWeightsBeforeApply(JTable table, String article, String lot, String couche,
                                                         int skipCount, int nombreMelanges, PassageWeights weights) {
            return confirmPassageWeightsBeforeApply(table, article, lot, couche, skipCount, nombreMelanges, 0, weights);
        }

        private boolean confirmPassageWeightsBeforeApply(JTable table, String article, String lot, String couche,
                                                         int skipCount, int nombreMelanges, int excludedCount,
                                                         PassageWeights weights) {
            if (table == null || weights == null) return true;

            String confirmKey = article + "\u0001" + lot + "\u0001" + normalizeCouche(couche)
                    + "\u0001" + skipCount + "\u0001" + nombreMelanges + "\u0001" + excludedCount + "\u0001" + weights.signature();

            @SuppressWarnings("unchecked")
            java.util.Set<String> confirmedKeys = (java.util.Set<String>) table.getClientProperty("confirmedPassageWeightsKeys");
            if (confirmedKeys == null) {
                confirmedKeys = new java.util.HashSet<>();
                table.putClientProperty("confirmedPassageWeightsKeys", confirmedKeys);
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, java.util.Set<String>> confirmedSelections =
                    (java.util.Map<String, java.util.Set<String>>) table.getClientProperty("confirmedPassageWeightsSelections");
            if (confirmedSelections == null) {
                confirmedSelections = new java.util.HashMap<>();
                table.putClientProperty("confirmedPassageWeightsSelections", confirmedSelections);
            }

            String activeConfirmKey = article + "\u0001" + lot + "\u0001" + normalizeCouche(couche)
                    + "\u0001" + skipCount + "\u0001" + nombreMelanges;

            @SuppressWarnings("unchecked")
            java.util.Set<String> activeConfirmKeys =
                    (java.util.Set<String>) table.getClientProperty("activePassageWeightsConfirmKeys");
            if (activeConfirmKeys == null) {
                activeConfirmKeys = new java.util.HashSet<>();
                table.putClientProperty("activePassageWeightsConfirmKeys", activeConfirmKeys);
            }

            // V190.31: se due SwingWorker arrivano quasi insieme, il secondo non deve aprire
            // un'altra finestra mentre la prima e ancora aperta. La prima conferma applichera
            // gia la selezione dell'operatore.
            if (activeConfirmKeys.contains(activeConfirmKey)) {
                return false;
            }

            // V190.30: la protezione contro la doppia apertura non puo piu applicare
            // le pesate originali. Se un secondo evento JTable/SwingWorker arriva subito
            // dopo OK, riapplica esattamente la selezione confermata nel dialog, quindi
            // le righe senza spunta restano escluse dalla colonna Poids.
            if (confirmedKeys.contains(confirmKey)) {
                java.util.Set<String> selectedKeys = confirmedSelections.get(confirmKey);
                if (selectedKeys != null) {
                    applySelectedPassageWeightKeys(weights, selectedKeys);
                }
                return true;
            }

            // V152: ripristina la finestra di controllo pesate DB durante l'inserimento articoli.
            // Resta disattivata durante stampa / stampa+invia tramite suppressProductionDbLookupDuringPrint
            // e durante il caricamento della persistenza turno.
            boolean confirmed;
            activeConfirmKeys.add(activeConfirmKey);
            try {
                confirmed = showPassageWeightsDialog(table, article, lot, couche, skipCount, nombreMelanges, excludedCount, weights);
            } finally {
                activeConfirmKeys.remove(activeConfirmKey);
            }
            if (confirmed) {
                confirmedKeys.add(confirmKey);
                confirmedSelections.put(confirmKey, selectedPassageWeightKeys(weights));
            }
            return confirmed;
        }

        private String passageWeightStableKey(PassageWeightRow row) {
            if (row == null) return "";
            String id = row.idPesage == null ? "" : row.idPesage.trim();
            if (!id.isEmpty()) return "ID:" + id;
            return "ROW:"
                    + (row.passage == null ? "" : row.passage.trim()) + "|"
                    + (row.noPesee == null ? "" : row.noPesee.trim()) + "|"
                    + (row.couche == null ? "" : row.couche.trim()) + "|"
                    + (row.melange == null ? "" : row.melange.trim()) + "|"
                    + (row.date2 == null ? "" : row.date2.trim()) + "|"
                    + (row.net == null ? "" : row.net.toPlainString());
        }

        private java.util.Set<String> selectedPassageWeightKeys(PassageWeights weights) {
            java.util.Set<String> keys = new java.util.LinkedHashSet<>();
            if (weights == null) return keys;
            for (PassageWeightRow row : weights.rows) {
                String key = passageWeightStableKey(row);
                if (!key.isEmpty()) keys.add(key);
            }
            return keys;
        }

        private void applySelectedPassageWeightKeys(PassageWeights weights, java.util.Set<String> selectedKeys) {
            if (weights == null || selectedKeys == null) return;

            java.util.List<PassageWeightRow> selectedRows = new ArrayList<>();
            for (PassageWeightRow row : weights.rows) {
                if (selectedKeys.contains(passageWeightStableKey(row))) {
                    selectedRows.add(row);
                }
            }

            weights.rows.clear();
            weights.rows.addAll(selectedRows);
            rebuildPassageWeightsTotals(weights);
        }

        private boolean showPassageWeightsDialog(Component parent, String article, String lot, String couche,
                                                 int skipCount, int nombreMelanges, int excludedCount,
                                                 PassageWeights weights) {
            String[] columns = {"Passage", "NET_P", "NO_PESEE_P", "ID_PESAGE", "Couche", "MELANGE", "DATE2", "Somma"};
            DefaultTableModel dialogModel = new DefaultTableModel(columns, 0) {
                @Override public boolean isCellEditable(int row, int column) { return column == 7; }
                @Override public Class<?> getColumnClass(int columnIndex) {
                    return columnIndex == 7 ? Boolean.class : String.class;
                }
            };

            for (PassageWeightRow row : weights.rows) {
                dialogModel.addRow(new Object[]{
                        row.passage,
                        formatKg(row.net),
                        row.noPesee,
                        row.idPesage,
                        row.couche,
                        row.melange,
                        row.date2,
                        Boolean.TRUE
                });
            }

            JTable detailsTable = new JTable(dialogModel);
            detailsTable.setFont(new Font("Arial", Font.PLAIN, 14));
            detailsTable.setRowHeight(26);
            detailsTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 14));
            detailsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            int[] widths = {90, 120, 140, 140, 90, 110, 150, 70};
            for (int i = 0; i < widths.length; i++) {
                detailsTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            }

            JPanel panel = new JPanel(new BorderLayout(8, 8));
            panel.setBorder(new EmptyBorder(10, 10, 10, 10));

            String source = "PROVENANCE_ID=" + getSelectedBanburyProvenance() + " - turno corrente 06-14 / 14-22 / 22-06 - nessun filtro DESTINATION - colonna PASSAGE reale - split COUCHE/PASSAGE/SERIE";
            JLabel header = new JLabel("<html><b>Article:</b> " + article
                    + " &nbsp;&nbsp; <b>Lot:</b> " + lot
                    + "<br><b>Melanges richiesti:</b> " + nombreMelanges
                    + " &nbsp;&nbsp; <b>Righe saltate:</b> " + skipCount
                    + " &nbsp;&nbsp; <b>ID gia usati esclusi:</b> " + excludedCount
                    + " &nbsp;&nbsp; <b>Fonte:</b> " + source
                    + "<br><b>Nota:</b> togliendo una spunta, la riga non viene sommata ne marcata come usata; verra riproposta alla prossima ricerca dello stesso Article / N. Lot."
                    + "</html>");
            header.setFont(new Font("Arial", Font.PLAIN, 14));
            panel.add(header, BorderLayout.NORTH);

            panel.add(new JScrollPane(detailsTable), BorderLayout.CENTER);

            JPanel totals = new JPanel(new GridLayout(3, 4, 8, 4));
            totals.add(new JLabel("Somma Passage 1:"));
            final JLabel lblPassage1 = new JLabel(formatKgOrZero(weights.passage1));
            totals.add(lblPassage1);
            totals.add(new JLabel("Somma Passage 2:"));
            final JLabel lblPassage2 = new JLabel(formatKgOrZero(weights.passage2));
            totals.add(lblPassage2);
            totals.add(new JLabel("Somma Passage 3:"));
            final JLabel lblPassage3 = new JLabel(formatKgOrZero(weights.passage3));
            totals.add(lblPassage3);
            totals.add(new JLabel("Somma Passage 4:"));
            final JLabel lblPassage4 = new JLabel(formatKgOrZero(weights.passage4));
            totals.add(lblPassage4);
            totals.add(new JLabel("Totale:"));
            final JLabel lblTotal = new JLabel(formatKgOrZero(weights.total()));
            totals.add(lblTotal);
            totals.add(new JLabel(""));
            totals.add(new JLabel(""));
            for (Component c : totals.getComponents()) c.setFont(new Font("Arial", Font.BOLD, 14));
            panel.add(totals, BorderLayout.SOUTH);

            Runnable refreshDialogTotals = () -> {
                BigDecimal p1 = BigDecimal.ZERO;
                BigDecimal p2 = BigDecimal.ZERO;
                BigDecimal p3 = BigDecimal.ZERO;
                BigDecimal p4 = BigDecimal.ZERO;

                for (int r = 0; r < dialogModel.getRowCount() && r < weights.rows.size(); r++) {
                    Object selectedValue = dialogModel.getValueAt(r, 7);
                    if (!Boolean.TRUE.equals(selectedValue)) {
                        continue;
                    }

                    PassageWeightRow row = weights.rows.get(r);
                    String passage = normalizePassage(row.passage);
                    if ("1".equals(passage)) p1 = p1.add(row.net);
                    else if ("2".equals(passage)) p2 = p2.add(row.net);
                    else if ("3".equals(passage)) p3 = p3.add(row.net);
                    else if ("4".equals(passage)) p4 = p4.add(row.net);
                }

                lblPassage1.setText(formatKgOrZero(p1));
                lblPassage2.setText(formatKgOrZero(p2));
                lblPassage3.setText(formatKgOrZero(p3));
                lblPassage4.setText(formatKgOrZero(p4));
                lblTotal.setText(formatKgOrZero(p1.add(p2).add(p3).add(p4)));
            };

            dialogModel.addTableModelListener(e -> {
                if (e.getColumn() == 7 || e.getColumn() == javax.swing.event.TableModelEvent.ALL_COLUMNS) {
                    refreshDialogTotals.run();
                }
            });

            // V127: calcola subito i totali della finestra partendo dalle righe spuntate.
            // Prima i label potevano restare a 0 quando il PASSAGE arrivava dal DB come "2eme".
            refreshDialogTotals.run();

            final int[] result = {-1};
            JDialog dialog = new JDialog(
                    SwingUtilities.getWindowAncestor(parent),
                    "Controllo pesate DB prima della somma",
                    Dialog.ModalityType.APPLICATION_MODAL
            );


            JButton okButton = new JButton("OK");
            okButton.setFont(new Font("Arial", Font.BOLD, 14));
            okButton.addActionListener(e -> {
                // V190.29: prima di chiudere il dialog conferma esplicitamente l'editor
                // della checkbox SOMMA. In questo modo, se l'operatore toglie la spunta
                // e preme subito OK, il valore FALSE viene scritto nel model e la riga
                // non viene inserita nella colonna Poids.
                if (detailsTable.isEditing()) {
                    TableCellEditor editor = detailsTable.getCellEditor();
                    if (editor != null && !editor.stopCellEditing()) {
                        return;
                    }
                }
                result[0] = 0;
                dialog.dispose();
            });

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.add(okButton);

            JPanel dialogContent = new JPanel(new BorderLayout());
            dialogContent.add(panel, BorderLayout.CENTER);
            dialogContent.add(buttonPanel, BorderLayout.SOUTH);

            dialog.setContentPane(dialogContent);
            dialog.getRootPane().setDefaultButton(okButton);
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            dialog.setSize(940, 520);
            dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(parent));
            dialog.setVisible(true);

            if (detailsTable.isEditing()) {
                TableCellEditor editor = detailsTable.getCellEditor();
                if (editor != null) editor.stopCellEditing();
            }

            if (result[0] == 0) {
                java.util.List<PassageWeightRow> selectedRows = new ArrayList<>();
                for (int r = 0; r < dialogModel.getRowCount() && r < weights.rows.size(); r++) {
                    Object selectedValue = dialogModel.getValueAt(r, 7);
                    if (Boolean.TRUE.equals(selectedValue)) {
                        selectedRows.add(weights.rows.get(r));
                    }
                }

                weights.rows.clear();
                weights.rows.addAll(selectedRows);
                rebuildPassageWeightsTotals(weights);
            }

            return result[0] == 0;
        }

        private enum NoPassageWeightsAction {
            EN_ATTENTE,
            EQUIPE_SUIVANTE_ZERO,
            FERMER
        }

        private NoPassageWeightsAction showNoPassageWeightsDialog(Component parent, String article, String lot, String couche,
                                             int nombreMelanges, String provenance, String error) {
            String msg = "Aucune pesée trouvée pour le calcul KG de la ligne.\n"
                    + "Article: " + (article == null ? "" : article) + "\n"
                    + "Lot: " + (lot == null ? "" : lot) + "\n"
                    + "Mélanges demandés: " + nombreMelanges + "\n"
                    + "PROVENANCE_ID demandée: " + (provenance == null ? "" : provenance) + "\n"
                    + "Équipe: " + getCurrentShiftWindow().displayName
                    + (error == null || error.trim().isEmpty() ? "" : "\nErreur SQL: " + error)
                    + "\n\nChoisissez EN ATTENTE pour laisser la ligne vide et reproposer la recherche plus tard."
                    + "\nChoisissez ÉQUIPE SUIVANTE - KG 0 si la pesée appartient à l'équipe suivante: les KG seront mis à zéro et la ligne sera considérée comme traitée.";

            Object[] options = {"EN ATTENTE", "ÉQUIPE SUIVANTE - KG 0", "FERMER"};
            int choice = JOptionPane.showOptionDialog(
                    getDialogParentComponent(),
                    msg,
                    "Contrôle pesées DB avant la somme",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    options[0]
            );

            if (choice == 0) return NoPassageWeightsAction.EN_ATTENTE;
            if (choice == 1) return NoPassageWeightsAction.EQUIPE_SUIVANTE_ZERO;
            return NoPassageWeightsAction.FERMER;
        }

        private void applyNoPassageWeightsEquipeSuivanteZero(JTable table, DefaultTableModel model, int modelRow,
                                                             String article, String lot, String couche, int nombreMelanges) {
            if (model == null || modelRow < 0 || modelRow >= model.getRowCount()) {
                return;
            }

            if (table != null && table.isEditing()) {
                TableCellEditor editor = table.getCellEditor();
                if (editor != null) editor.cancelCellEditing();
            }

            // V193.04: nessuna pesata trovata nel turno corrente, ma l'operatore conferma
            // che le pesate appartengono all'équipe suivante. La riga resta senza Couche
            // e senza Passage, i KG vengono messi a zero e la ricerca non viene riproposta
            // finche Article / Lot / Nombre de Mélanges restano invariati.
            model.setValueAt(article == null ? "" : article, modelRow, 0);
            model.setValueAt(lot == null ? "" : lot, modelRow, 2);
            model.setValueAt(String.valueOf(nombreMelanges), modelRow, 3);
            model.setValueAt("", modelRow, 4);
            model.setValueAt("", modelRow, 5);
            model.setValueAt("0 Kg", modelRow, 6);

            if (table != null) {
                table.putClientProperty("ligneEquipeSuivanteKgZero:" + modelRow, Boolean.TRUE);
                clearUsedPesageIdsForRow(table, modelRow);
            }

            model.fireTableRowsUpdated(modelRow, modelRow);
            forceProductionTableImmediateRefresh(table, model, modelRow, modelRow);
        }

        private String formatSerieFromMelange(String melange) {
            if (melange == null) return "";
            String value = melange.trim();
            if (value.isEmpty()) return "";
            if ("NULL".equalsIgnoreCase(value)) return "";
            if (".".equals(value)) return "";
            if (value.toUpperCase(Locale.ROOT).startsWith("SERIE ")) {
                return value.toUpperCase(Locale.ROOT);
            }
            return "SERIE " + value.toUpperCase(Locale.ROOT);
        }

        private String formatKgOrZero(BigDecimal value) {
            return value == null || value.compareTo(BigDecimal.ZERO) == 0 ? "0 Kg" : formatKg(value);
        }

        private PassageWeights loadPassageWeightsInternal(String article, String lot, String requestedCouche,
                                                          int skipCount, int takeCount, boolean filterCurrentShift,
                                                          java.util.Set<String> excludedPesageIds) throws Exception {
            PassageWeights weights = new PassageWeights();
            if (takeCount <= 0) return weights;

            CurrentShiftWindow shiftWindow = getCurrentShiftWindow();

            String sql = "SELECT NVL(PASSAGE, POSTE_P) AS PASSAGE, NET_P, NO_PESEE_P, ID_PESAGE, COUCHE, MELANGE, DATE2, DESTINATION_ID, PROVENANCE_ID AS PROVENANCE " +
                    "FROM DBM_PESAGE_NEW " +
                    "WHERE UPPER(TRIM(PROVENANCE_ID)) = ? " +
                    "AND UPPER(TRIM(PRODUIT_ID)) = ? " +
                    // V193.05: il N. LOT della tabella deve coincidere con LOT_P della pesata.
                    // Rimangono accettate solo differenze di spazi/separatori, ma non piu match per prefisso
                    // (es. LOT digitato 26 non deve trovare LOT_P 26/0428).
                    "AND (UPPER(TRIM(LOT_P)) = ? " +
                    "     OR REPLACE(UPPER(TRIM(LOT_P)), ' ', '') = ? " +
                    "     OR REGEXP_REPLACE(UPPER(TRIM(LOT_P)), '[^0-9A-Z]', '') = ?) " +
                    "AND TO_DATE(DATE2, 'DD/MM/RR HH24:MI') >= ? " +
                    "AND TO_DATE(DATE2, 'DD/MM/RR HH24:MI') < ? " +
                    "AND NVL(NET_P, 0) >= 0 " +
                    "ORDER BY TO_DATE(DATE2, 'DD/MM/RR HH24:MI') ASC, " +
                    "TO_NUMBER(NULLIF(REGEXP_REPLACE(NO_PESEE_P, '[^0-9]', ''), '')) ASC, ID_PESAGE ASC";

            Map<String, Integer> skippedByPassage = new HashMap<>();
            try (Connection cn = DbOracleManager.getConnection(); PreparedStatement ps = cn.prepareStatement(sql)) {
                int p = 1;
                String articleParam = article == null ? "" : article.trim().toUpperCase(Locale.ROOT);
                String lotParam = lot == null ? "" : lot.trim().toUpperCase(Locale.ROOT);
                String lotNoSpaces = lotParam.replace(" ", "");
                String lotAlphaNum = lotParam.replaceAll("[^0-9A-Z]", "");

                ps.setString(p++, getSelectedBanburyProvenance());
                ps.setString(p++, articleParam);
                ps.setString(p++, lotParam);
                ps.setString(p++, lotNoSpaces);
                ps.setString(p++, lotAlphaNum);
                ps.setTimestamp(p++, Timestamp.valueOf(shiftWindow.start));
                ps.setTimestamp(p++, Timestamp.valueOf(shiftWindow.end));

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String idPesage = rs.getString("ID_PESAGE");
                        if (excludedPesageIds != null && idPesage != null
                                && excludedPesageIds.contains(idPesage.trim())) {
                            continue;
                        }

                        String passage = normalizePassage(rs.getString("PASSAGE"));
                        if (!"1".equals(passage) && !"2".equals(passage) && !"3".equals(passage) && !"4".equals(passage)) continue;

                        BigDecimal net = readBigDecimal(rs, "NET_P");
                        if (net.compareTo(BigDecimal.ZERO) < 0) {
                            continue;
                        }

                        String rowCouche = normalizeCouche(rs.getString("COUCHE"));

                        int skipped = skippedByPassage.getOrDefault(passage, 0);
                        if (skipped < skipCount) {
                            skippedByPassage.put(passage, skipped + 1);
                            continue;
                        }

                        // V193.44: non limitare qui ai primi N record, perche dopo questa
                        // lettura vengono ancora eliminate le righe gia registrate nel form.
                        // Il limite ai prossimi N melanges viene applicato dopo la rimozione.
                        String originalPassage = rs.getString("PASSAGE") == null ? passage : rs.getString("PASSAGE");
                        weights.rows.add(new PassageWeightRow(
                                originalPassage,
                                net,
                                rs.getString("NO_PESEE_P"),
                                idPesage,
                                rowCouche,
                                rs.getString("MELANGE"),
                                rs.getString("DATE2")
                        ));
                        weights.filteredByCurrentShift = filterCurrentShift;
                        if (weights.couche.isEmpty()) weights.couche = rowCouche;
                        if ("1".equals(passage)) weights.passage1 = weights.passage1.add(net);
                        else if ("2".equals(passage)) weights.passage2 = weights.passage2.add(net);
                        else if ("3".equals(passage)) weights.passage3 = weights.passage3.add(net);
                        else if ("4".equals(passage)) weights.passage4 = weights.passage4.add(net);
                    }
                }
            }
            return weights;
        }

        private BigDecimal readBigDecimal(ResultSet rs, String columnName) throws SQLException {
            Object value = rs.getObject(columnName);
            if (value == null) return BigDecimal.ZERO;
            if (value instanceof BigDecimal) return (BigDecimal) value;
            if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
            String text = value.toString().trim().replace(" ", "").replace(',', '.');
            if (text.isEmpty()) return BigDecimal.ZERO;
            try { return new BigDecimal(text); } catch (NumberFormatException ex) { return BigDecimal.ZERO; }
        }


        private CurrentShiftWindow getCurrentShiftWindow() {
            LocalDateTime nowDateTime = LocalDateTime.now();
            LocalDate today = nowDateTime.toLocalDate();
            LocalTime now = nowDateTime.toLocalTime();

            if (manualTourneeId != null && (manualShiftExpiresAt == null || nowDateTime.isBefore(manualShiftExpiresAt))) {
                return shiftWindowForTournee(manualTourneeId, today, now);
            }

            CurrentShiftWindow automaticShift = getAutomaticShiftWindow(nowDateTime);
            if (confirmedAutomaticTourneeId == null) {
                confirmedAutomaticTourneeId = automaticShift.tourneeId;
            }

            if (!automaticShift.tourneeId.equals(confirmedAutomaticTourneeId)) {
                return shiftWindowForTournee(confirmedAutomaticTourneeId, today, now);
            }

            if (manualTourneeId != null && manualShiftExpiresAt != null && !nowDateTime.isBefore(manualShiftExpiresAt)) {
                manualTourneeId = null;
                manualShiftExpiresAt = null;
            }

            return automaticShift;
        }

        private CurrentShiftWindow getAutomaticShiftWindow(LocalDateTime dateTime) {
            LocalDate today = dateTime.toLocalDate();
            LocalTime now = dateTime.toLocalTime();
            if (!now.isBefore(LocalTime.of(6, 0)) && now.isBefore(LocalTime.of(14, 0))) {
                return new CurrentShiftWindow("1", LocalDateTime.of(today, LocalTime.of(6, 0)), LocalDateTime.of(today, LocalTime.of(14, 0)));
            }
            if (!now.isBefore(LocalTime.of(14, 0)) && now.isBefore(LocalTime.of(22, 0))) {
                return new CurrentShiftWindow("2", LocalDateTime.of(today, LocalTime.of(14, 0)), LocalDateTime.of(today, LocalTime.of(22, 0)));
            }
            if (!now.isBefore(LocalTime.of(22, 0))) {
                return new CurrentShiftWindow("3", LocalDateTime.of(today, LocalTime.of(22, 0)), LocalDateTime.of(today.plusDays(1), LocalTime.of(6, 0)));
            }
            return new CurrentShiftWindow("3", LocalDateTime.of(today.minusDays(1), LocalTime.of(22, 0)), LocalDateTime.of(today, LocalTime.of(6, 0)));
        }

        private CurrentShiftWindow shiftWindowForTournee(String tourneeId, LocalDate today, LocalTime now) {
            if ("1".equals(tourneeId)) {
                LocalDate d = now.isBefore(LocalTime.of(6, 0)) ? today.minusDays(1) : today;
                return new CurrentShiftWindow("1", LocalDateTime.of(d, LocalTime.of(6, 0)), LocalDateTime.of(d, LocalTime.of(14, 0)));
            }
            if ("2".equals(tourneeId)) {
                LocalDate d = now.isBefore(LocalTime.of(14, 0)) ? today.minusDays(1) : today;
                return new CurrentShiftWindow("2", LocalDateTime.of(d, LocalTime.of(14, 0)), LocalDateTime.of(d, LocalTime.of(22, 0)));
            }
            if (now.isBefore(LocalTime.of(22, 0))) {
                return new CurrentShiftWindow("3", LocalDateTime.of(today.minusDays(1), LocalTime.of(22, 0)), LocalDateTime.of(today, LocalTime.of(6, 0)));
            }
            return new CurrentShiftWindow("3", LocalDateTime.of(today, LocalTime.of(22, 0)), LocalDateTime.of(today.plusDays(1), LocalTime.of(6, 0)));
        }

        private static class MaxTextAreaLinesFilter extends DocumentFilter {
        private final int maxLines;

        MaxTextAreaLinesFilter(int maxLines) {
            this.maxLines = Math.max(1, maxLines);
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            replace(fb, offset, 0, string, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            if (text == null) return;
            Document doc = fb.getDocument();
            String current = doc.getText(0, doc.getLength());
            String candidate = current.substring(0, offset) + text + current.substring(offset + length);
            if (countLogicalLines(candidate) <= maxLines) {
                super.replace(fb, offset, length, text, attrs);
            } else {
                Toolkit.getDefaultToolkit().beep();
            }
        }

        private int countLogicalLines(String text) {
            if (text == null || text.isEmpty()) return 1;
            int lines = 1;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') lines++;
            }
            return lines;
        }
    }

    private static class CurrentShiftWindow {
            final String tourneeId;
            final String tourneeName;
            final String displayName;
            final LocalDateTime start;
            final LocalDateTime end;
            CurrentShiftWindow(String tourneeId, LocalDateTime start, LocalDateTime end) {
                this.tourneeId = tourneeId;
                this.tourneeName = "1".equals(tourneeId) ? "MATIN" : ("2".equals(tourneeId) ? "APRES-MIDI" : "NUIT");
                this.displayName = "1".equals(tourneeId) ? "T1 Matin" : ("2".equals(tourneeId) ? "T2 Apres Midi" : "T3 Nuit");
                this.start = start;
                this.end = end;
            }
        }

        private String nextAutomaticCouche(DefaultTableModel model, int currentRow, String article, String lot) {
            int count = 0;
            for (int r = 0; r < currentRow; r++) {
                String rowArticle = valueAt(model, r, 0).toUpperCase(Locale.ROOT);
                String rowLot = valueAt(model, r, 2).toUpperCase(Locale.ROOT);
                if (article.equals(rowArticle) && lot.equals(rowLot)) {
                    String existing = normalizeCouche(valueAt(model, r, 5));
                    if ("SUP".equals(existing) || "INF".equals(existing)) count++;
                }
            }
            return (count % 2 == 0) ? "SUP" : "INF";
        }

        private String normalizeCouche(String value) {
            if (value == null) {
                return "";
            }

            String text = value.trim().toUpperCase(Locale.ROOT);
            if (text.contains("SUP")) {
                return "SUP";
            }
            if (text.contains("INF")) {
                return "INF";
            }
            return text;
        }

        private String normalizePassage(String passage) {
            if (passage == null) {
                return "";
            }

            String text = passage.trim().toLowerCase(Locale.ROOT);
            text = text.replace("è", "e").replace("é", "e").replace("ê", "e").replace("ë", "e");
            text = text.replace(" ", "");

            // Mappatura colonna DB PASSAGE -> nuova colonna unica Passage.
            // Il peso relativo viene scritto solo nella colonna Poids.
            if (text.startsWith("1") || text.contains("1ere") || text.contains("1er")) {
                return "1";
            }
            if (text.startsWith("2") || text.contains("2eme")) {
                return "2";
            }
            if (text.startsWith("3") || text.contains("3eme")) {
                return "3";
            }
            if (text.startsWith("4") || text.contains("4eme")) {
                return "4";
            }

            return text;
        }

        private String displayPassage(String passage) {
            String p = normalizePassage(passage);
            if ("1".equals(p)) return "1ere";
            if ("2".equals(p)) return "2eme";
            if ("3".equals(p)) return "3eme";
            if ("4".equals(p)) return "4eme";
            return passage == null ? "" : passage.trim();
        }


        private void clearPassageWeights(DefaultTableModel model, int row) {
            if (model == null || row < 0 || row >= model.getRowCount()) {
                return;
            }

            model.setValueAt("", row, 5); // Couche
            model.setValueAt("", row, 6); // Passage
            model.setValueAt("", row, 7); // Poids
        }

        private void clearProductionRow(JTable table, DefaultTableModel model, int row) {
            if (model == null || row < 0 || row >= model.getRowCount()) {
                return;
            }

            if (table != null && table.isEditing()) {
                TableCellEditor editor = table.getCellEditor();
                if (editor != null) {
                    editor.cancelCellEditing();
                }
            }

            if (table != null) {
                clearUsedPesageIdsForRow(table, row);
                table.putClientProperty("splitRow:" + row, null);
            }

            for (int col = 0; col < model.getColumnCount(); col++) {
                model.setValueAt("", row, col);
            }

            if (table != null) {
                forceProductionTableImmediateRefresh(table, model, row, row);
            }

            refreshPoidsRealiseFromProductionTable();
            refreshArretSummaryFromProductionTable();
        }

        private String valueAt(DefaultTableModel model, int row, int col) {
            Object value = model.getValueAt(row, col);
            return value == null ? "" : value.toString().trim();
        }

        private String formatKg(BigDecimal value) {
            if (value == null || value.compareTo(BigDecimal.ZERO) == 0) {
                return "";
            }

            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
            symbols.setGroupingSeparator(' ');
            symbols.setDecimalSeparator(',');

            DecimalFormat format = new DecimalFormat("#,##0.###", symbols);
            return format.format(value) + " Kg";
        }


        private static class PoidsRequest {
            final String article;
            String produit;
            final String couche;
            final String lot;
            final String serie;
            int takeCount;
            int desiredTotal;
            int sourceRow = -1;
            LocalDateTime rowStart;
            LocalDateTime rowEnd;

            PoidsRequest(String article, String produit, String couche, String lot, String serie) {
                this.article = article == null ? "" : article.trim().toUpperCase(Locale.ROOT);
                this.produit = produit == null ? "" : produit.trim().toUpperCase(Locale.ROOT);
                this.couche = couche == null ? "" : couche.trim().toUpperCase(Locale.ROOT);
                this.lot = lot == null ? "" : lot.trim().toUpperCase(Locale.ROOT);
                this.serie = serie == null ? "" : serie.trim().toUpperCase(Locale.ROOT);
            }

            void includeTimeWindow(LocalDateTime start, LocalDateTime end) {
                if (start == null || end == null) {
                    return;
                }
                if (rowStart == null || start.isBefore(rowStart)) {
                    rowStart = start;
                }
                if (rowEnd == null || end.isAfter(rowEnd)) {
                    rowEnd = end;
                }
            }
        }

        private static class PoidsDbRow {
            final String noPesee;
            final BigDecimal net;
            final String date2;
            final String idPesage;
            final String couche;
            final String melange;
            final String couleur;

            PoidsDbRow(String noPesee, BigDecimal net, String date2, String idPesage) {
                this(noPesee, net, date2, idPesage, "", "", "");
            }

            PoidsDbRow(String noPesee, BigDecimal net, String date2, String idPesage, String couche, String melange) {
                this(noPesee, net, date2, idPesage, couche, melange, "");
            }

            PoidsDbRow(String noPesee, BigDecimal net, String date2, String idPesage, String couche, String melange, String couleur) {
                this.noPesee = noPesee == null ? "" : noPesee.trim();
                this.net = net == null ? BigDecimal.ZERO : net;
                this.date2 = date2 == null ? "" : date2.trim();
                this.idPesage = idPesage == null ? "" : idPesage.trim();
                this.couche = couche == null ? "" : couche.trim();
                this.melange = melange == null ? "" : melange.trim();
                this.couleur = couleur == null ? "" : couleur.trim();
            }
        }

        private static class PoidsRealiseRow {
            final String produit;
            final String couche;
            final String lot;
            final String serie;
            final String couleur;
            final java.util.List<String> noPesees = new ArrayList<>();
            final java.util.List<String> netValues = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;

            PoidsRealiseRow(String produit, String couche, String lot) {
                this(produit, couche, lot, "", "");
            }

            PoidsRealiseRow(String produit, String couche, String lot, String serie, String couleur) {
                this.produit = produit == null ? "" : produit.trim().toUpperCase(Locale.ROOT);
                this.couche = couche == null ? "" : couche.trim().toUpperCase(Locale.ROOT);
                this.lot = lot == null ? "" : lot.trim().toUpperCase(Locale.ROOT);
                this.serie = serie == null ? "" : serie.trim().toUpperCase(Locale.ROOT);
                this.couleur = couleur == null ? "" : couleur.trim().toUpperCase(Locale.ROOT);
            }
        }

        private static class PassageWeightRow {
            final String passage;
            final BigDecimal net;
            final String noPesee;
            final String idPesage;
            final String couche;
            final String melange;
            final String date2;

            PassageWeightRow(String passage, BigDecimal net, String noPesee, String idPesage, String couche) {
                this(passage, net, noPesee, idPesage, couche, "", "");
            }

            PassageWeightRow(String passage, BigDecimal net, String noPesee, String idPesage, String couche, String date2) {
                this(passage, net, noPesee, idPesage, couche, "", date2);
            }

            PassageWeightRow(String passage, BigDecimal net, String noPesee, String idPesage, String couche, String melange, String date2) {
                this.passage = passage == null ? "" : passage.trim();
                this.net = net == null ? BigDecimal.ZERO : net;
                this.noPesee = noPesee == null ? "" : noPesee.trim();
                this.idPesage = idPesage == null ? "" : idPesage.trim();
                this.couche = couche == null ? "" : couche.trim();
                this.melange = melange == null ? "" : melange.trim();
                this.date2 = date2 == null ? "" : date2.trim();
            }
        }

        private static class PassageWeights {
            String couche = "";
            BigDecimal passage1 = BigDecimal.ZERO;
            BigDecimal passage2 = BigDecimal.ZERO;
            BigDecimal passage3 = BigDecimal.ZERO;
            BigDecimal passage4 = BigDecimal.ZERO;
            String melange = "";
            boolean filteredByCurrentShift = true;
            boolean enAttente = false;
            final java.util.List<PassageWeightRow> rows = new ArrayList<>();

            boolean hasAnyValue() {
                return passage1.compareTo(BigDecimal.ZERO) != 0
                        || passage2.compareTo(BigDecimal.ZERO) != 0
                        || passage3.compareTo(BigDecimal.ZERO) != 0
                        || passage4.compareTo(BigDecimal.ZERO) != 0;
            }

            BigDecimal total() {
                return passage1.add(passage2).add(passage3).add(passage4);
            }

            String displayPassage() {
                for (PassageWeightRow row : rows) {
                    if (row == null) continue;
                    String p = row.passage == null ? "" : row.passage.trim();
                    if (!p.isEmpty()) return p;
                }
                if (passage1.compareTo(BigDecimal.ZERO) != 0) return "1";
                if (passage2.compareTo(BigDecimal.ZERO) != 0) return "2";
                if (passage3.compareTo(BigDecimal.ZERO) != 0) return "3";
                if (passage4.compareTo(BigDecimal.ZERO) != 0) return "4";
                return "";
            }

            String firstValidMelange() {
                if (melange != null && !melange.trim().isEmpty()
                        && !"NULL".equalsIgnoreCase(melange.trim())
                        && !".".equals(melange.trim())) {
                    return melange.trim();
                }
                for (PassageWeightRow row : rows) {
                    if (row == null || row.melange == null) continue;
                    String value = row.melange.trim();
                    if (!value.isEmpty() && !"NULL".equalsIgnoreCase(value) && !".".equals(value)) {
                        return value;
                    }
                }
                return "";
            }

            int melangeCount() {
                java.util.HashSet<String> noPesees = new java.util.HashSet<>();
                java.util.HashMap<String, Integer> countsByPassage = new java.util.HashMap<>();
                for (PassageWeightRow row : rows) {
                    String no = row.noPesee == null ? "" : row.noPesee.trim();
                    if (!no.isEmpty()) noPesees.add(no);
                    String passage = row.passage == null ? "" : row.passage.trim();
                    countsByPassage.put(passage, countsByPassage.getOrDefault(passage, 0) + 1);
                }
                if (!noPesees.isEmpty()) return noPesees.size();
                int max = 0;
                for (Integer count : countsByPassage.values()) if (count != null && count > max) max = count;
                return max;
            }

            String signature() {
                StringBuilder sb = new StringBuilder();
                sb.append(couche).append('|')
                        .append(melange).append('|')
                        .append(passage1.toPlainString()).append('|')
                        .append(passage2.toPlainString()).append('|')
                        .append(passage3.toPlainString()).append('|')
                        .append(passage4.toPlainString()).append('|')
                        .append(filteredByCurrentShift).append('|');
                for (PassageWeightRow row : rows) {
                    sb.append(row.passage).append(':')
                            .append(row.net.toPlainString()).append(':')
                            .append(row.noPesee).append(':')
                            .append(row.idPesage).append(':')
                            .append(row.couche).append(':')
                            .append(row.melange).append(':')
                            .append(row.date2).append(';');
                }
                return sb.toString();
            }
        }


        private JComboBox<String> createCouleurCombo() {
            JComboBox<String> combo = new JComboBox<>(new String[]{"", "Claire", "Fonce", "Medium"});
            combo.setFont(FONT_NORMAL);
            combo.setBackground(Color.WHITE);
            return combo;
        }

        private void installCouleurCombo(JTable table) {
            if (table == null || table.getColumnCount() <= 8) {
                return;
            }

            table.getColumnModel().getColumn(8).setCellEditor(new DefaultCellEditor(createCouleurCombo()));
        }


        private void installCoucheDoubleClickToggle(JTable table, DefaultTableModel model) {
            if (table == null || model == null) {
                return;
            }

            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() < 2) {
                        return;
                    }

                    int viewRow = table.rowAtPoint(e.getPoint());
                    int viewCol = table.columnAtPoint(e.getPoint());

                    // Colonna 5 = COUCHE
                    if (viewRow < 0 || viewCol != 5) {
                        return;
                    }

                    if (table.isEditing()) {
                        TableCellEditor editor = table.getCellEditor();
                        if (editor != null) {
                            editor.cancelCellEditing();
                        }
                    }

                    int modelRow = table.convertRowIndexToModel(viewRow);
                    String current = valueAt(model, modelRow, 5).trim().toUpperCase(Locale.ROOT);
                    String next;

                    if ("SUP".equals(current)) {
                        next = "INF";
                    } else if ("INF".equals(current)) {
                        next = "SUP";
                    } else {
                        next = "SUP";
                    }

                    model.setValueAt(next, modelRow, 5);
                    model.fireTableRowsUpdated(modelRow, modelRow);
                    table.repaint(table.getCellRect(viewRow, viewCol, true));
                }
            });
        }

        private void installProductionTimeCalculation(JTable table) {
            if (table == null || !(table.getModel() instanceof DefaultTableModel)) {
                return;
            }

            DefaultTableModel model = (DefaultTableModel) table.getModel();
            final boolean[] updating = {false};

            model.addTableModelListener(e -> {
                if (updating[0]) {
                    return;
                }

                int row = e.getFirstRow();
                int col = e.getColumn();

                if (row < 0 || row >= model.getRowCount()) {
                    return;
                }

                // V148: ricalcolo robusto del Temps anche quando l'editor della cella
                // chiude con TAB, Invio o perdita focus. Alcuni update arrivano come
                // ALL_COLUMNS (-1), quindi ricalcoliamo comunque la riga interessata.
                if (col != 11 && col != 12 && col != -1) {
                    return;
                }

                updating[0] = true;
                try {
                    if (col == 11 || col == 12) {
                        normalizeProductionTimeCell(model, row, col);
                    } else {
                        normalizeProductionTimeCell(model, row, 11);
                        normalizeProductionTimeCell(model, row, 12);
                    }
                    updateProductionDuration(model, row);

                    // Quando viene inserita/confermata l'ora nella colonna "à",
                    // la stessa ora viene riportata automaticamente nella colonna "de"
                    // della riga successiva.
                    if (col == 12) {
                        copyEndTimeToNextRowStart(model, row);
                    }
                } finally {
                    updating[0] = false;
                }
            });
        }

        private void installProductionTimeEditors(JTable table) {
            if (table == null || table.getColumnCount() <= 12) {
                return;
            }
            table.getColumnModel().getColumn(11).setCellEditor(new ProductionTimeCellEditor(table, createProductionTimeTableField()));
            table.getColumnModel().getColumn(12).setCellEditor(new ProductionTimeCellEditor(table, createProductionTimeTableField()));
        }

        private JTextField createProductionTimeTableField() {
            JTextField field = new JTextField();
            field.setFont(FONT_NORMAL);
            field.setHorizontalAlignment(SwingConstants.CENTER);
            field.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
            field.setMargin(new Insets(0, 2, 0, 2));
            return field;
        }

        private class ProductionTimeCellEditor extends DefaultCellEditor {
            private final JTable table;

            ProductionTimeCellEditor(JTable table, JTextField field) {
                super(field);
                this.table = table;
                setClickCountToStart(1);
                installEnterKeyOnEditorField(table, field);
                field.addFocusListener(new FocusAdapter() {
                    @Override public void focusLost(FocusEvent e) {
                        if (table.isEditing() && table.getCellEditor() == ProductionTimeCellEditor.this) {
                            ProductionTimeCellEditor.this.stopCellEditing();
                        }
                    }
                });
            }

            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                Component c = super.getTableCellEditorComponent(table, value, isSelected, row, column);
                if (c instanceof JTextField) {
                    JTextField field = (JTextField) c;
                    field.setText(value == null ? "" : value.toString());
                    field.selectAll();
                }
                return c;
            }

            @Override
            public boolean stopCellEditing() {
                int viewRow = table.getEditingRow();
                int viewCol = table.getEditingColumn();
                boolean stopped = super.stopCellEditing();
                if (stopped && viewRow >= 0 && viewCol >= 0 && table.getModel() instanceof DefaultTableModel) {
                    DefaultTableModel model = (DefaultTableModel) table.getModel();
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    int modelCol = table.convertColumnIndexToModel(viewCol);
                    SwingUtilities.invokeLater(() -> {
                        normalizeProductionTimeCell(model, modelRow, 11);
                        normalizeProductionTimeCell(model, modelRow, 12);
                        updateProductionDuration(model, modelRow);
                        if (modelCol == 12) {
                            copyEndTimeToNextRowStart(model, modelRow);
                        }
                    });
                }
                return stopped;
            }
        }

        private void normalizeProductionTimeCell(DefaultTableModel model, int row, int col) {
            Object value = model.getValueAt(row, col);
            Integer minutes = parseFrenchTime(value != null ? value.toString() : "");
            if (minutes != null) {
                model.setValueAt(formatFrenchTime(minutes), row, col);
            }
        }

        private void copyEndTimeToNextRowStart(DefaultTableModel model, int row) {
            int nextRow = row + 1;
            if (nextRow >= model.getRowCount()) {
                model.addRow(createEmptyRow(PRODUCTION_COL_COUNT));
            }

            Object endValue = model.getValueAt(row, 12); // à
            String endText = endValue != null ? endValue.toString().trim() : "";

            if (endText.isEmpty()) {
                return;
            }

            model.setValueAt(endText, nextRow, 11); // de della riga successiva
            updateProductionDuration(model, nextRow);
        }

        private void recalculateAllProductionDurations(DefaultTableModel model) {
            if (model == null) {
                return;
            }
            for (int r = 0; r < model.getRowCount(); r++) {
                updateProductionDuration(model, r);
            }
        }

        private void updateProductionDuration(DefaultTableModel model, int row) {
            if (model == null || row < 0 || row >= model.getRowCount()) {
                return;
            }
            Object startValue = model.getValueAt(row, 11); // de
            Object endValue = model.getValueAt(row, 12);   // à

            Integer start = parseFrenchTime(startValue != null ? startValue.toString() : "");
            Integer end = parseFrenchTime(endValue != null ? endValue.toString() : "");

            String newValue;
            if (start == null || end == null) {
                newValue = "";
            } else {
                int minutes = end - start;
                if (minutes < 0) {
                    minutes += 24 * 60;
                }
                newValue = formatFrenchDuration(minutes);
            }

            Object oldValueObj = model.getValueAt(row, 13);
            String oldValue = oldValueObj == null ? "" : oldValueObj.toString();
            if (!newValue.equals(oldValue)) {
                model.setValueAt(newValue, row, 13);
            }
        }


        private void openAccDialog() {
            new AccelerateurMenu(SwingUtilities.getWindowAncestor(this)).setVisible(true);
        }


        private void addLogo(int x, int y, int w, int h) {
            JLabel logo = new JLabel("MONDO", SwingConstants.CENTER);
            logo.setFont(new Font("Arial Black", Font.BOLD, 38));
            logo.setForeground(new Color(0, 70, 130));
            logo.setBounds(x, y, w, h);
            add(logo);
        }

        private JLabel addLabel(String text, int x, int y, int w, int h, Font font, int align) {
            JLabel l = new JLabel(text, align);
            l.setFont(font);
            l.setForeground(Color.BLACK);
            l.setBounds(x, y, w, h);
            add(l);
            return l;
        }

        private JLabel addBoxLabel(String text, int x, int y, int w, int h, Font font, Color bg) {
            JLabel l = addLabel(text, x, y, w, h, font, SwingConstants.CENTER);
            l.setOpaque(true);
            l.setBackground(bg);
            l.setBorder(new LineBorder(new Color(45,45,45), 2));
            return l;
        }
    }


    private static class UppercaseDocumentFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            if (string != null) {
                string = string.toUpperCase().replaceAll("[^A-Z0-9]", "");
            }
            super.insertString(fb, offset, string, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            if (text != null) {
                text = text.toUpperCase().replaceAll("[^A-Z0-9]", "");
            }
            super.replace(fb, offset, length, text, attrs);
        }
    }


    private static class SerieUppercaseDocumentFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            if (string != null) {
                string = string.toUpperCase(Locale.ROOT);
            }
            super.insertString(fb, offset, string, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            if (text != null) {
                text = text.toUpperCase(Locale.ROOT);
            }
            super.replace(fb, offset, length, text, attrs);
        }
    }

    private static class ArretItem {
        final String code;
        final String description;
        ArretItem(String code, String description) {
            this.code = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
            this.description = description == null ? "" : description.trim();
        }
    }

    private static class OperateurItem {
        String name;
        String numero;

        OperateurItem(String name, String numero) {
            this.name = name == null ? "" : name;
            this.numero = numero == null ? "" : numero;
        }

        @Override
        public String toString() {
            String n = numero == null || numero.trim().isEmpty() ? "" : " (" + numero.trim() + ")";
            return name + n;
        }
    }

    private static class PersonnelReportRow {
        final Component roleComponent;
        final JTextField code;
        final JTextField name;
        final JTextField debut;
        final JTextField fin;
        final JTextField total;

        PersonnelReportRow(Component roleComponent, JTextField code, JTextField name, JTextField debut, JTextField fin, JTextField total) {
            this.roleComponent = roleComponent;
            this.code = code;
            this.name = name;
            this.debut = debut;
            this.fin = fin;
            this.total = total;
        }
    }

    private static class PersonnelTimeRow {
        final JTextField debut;
        final JTextField fin;
        final JTextField total;

        PersonnelTimeRow(JTextField debut, JTextField fin, JTextField total) {
            this.debut = debut;
            this.fin = fin;
            this.total = total;
        }
    }



    private static class GroupedProductionARealiserHeader extends JTableHeader {
        private static final int TOP_H = 32;
        private static final int BOTTOM_H = 32;

        GroupedProductionARealiserHeader(TableColumnModel columnModel) {
            super(columnModel);
            setReorderingAllowed(false);
            setResizingAllowed(true);
            setPreferredSize(new Dimension(0, TOP_H + BOTTOM_H));
            setBackground(TABLE_HEADER_BG);
            setForeground(Color.BLACK);
            setFont(new Font("Arial", Font.PLAIN, 14));
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.height = TOP_H + BOTTOM_H;
            return d;
        }

        @Override
        public void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(TABLE_HEADER_BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(TABLE_GRID_LIGHT);
                g2.setStroke(new BasicStroke(0.8f));

                int[] x = new int[getColumnModel().getColumnCount() + 1];
                x[0] = 0;
                for (int i = 0; i < getColumnModel().getColumnCount(); i++) {
                    x[i + 1] = x[i] + getColumnModel().getColumn(i).getWidth();
                }

                drawHeaderCell(g2, "Type de produit et couleur", x[0], 0, x[3] - x[0], TOP_H, 16);
                drawHeaderCell(g2, "Article", x[0], TOP_H, x[1] - x[0], BOTTOM_H, 16);
                drawHeaderCell(g2, "Produit", x[1], TOP_H, x[2] - x[1], BOTTOM_H, 16);
                drawHeaderCell(g2, "N° Lot", x[2], TOP_H, x[3] - x[2], BOTTOM_H, 16);

                drawHeaderCell(g2, "Nombre de\nMelanges", x[3], 0, x[4] - x[3], TOP_H + BOTTOM_H, 14);
                drawHeaderCell(g2, "Couche", x[4], 0, x[5] - x[4], TOP_H + BOTTOM_H, 14);
                drawHeaderCell(g2, "Passage", x[5], 0, x[6] - x[5], TOP_H + BOTTOM_H, 14);
                drawHeaderCell(g2, "Couleur", x[6], 0, x[7] - x[6], TOP_H + BOTTOM_H, 14);
                drawHeaderCell(g2, "N.SERIE", x[7], 0, x[8] - x[7], TOP_H + BOTTOM_H, 14);
                drawHeaderCell(g2, "Notes Sur La\nComposition", x[8], 0, x[9] - x[8], TOP_H + BOTTOM_H, 14);

                g2.setStroke(new BasicStroke(1f));
                g2.setColor(TABLE_GRID_LIGHT);
                g2.drawRect(0, 0, x[9] - 1, TOP_H + BOTTOM_H - 1);
            } finally {
                g2.dispose();
            }
        }

        private void drawHeaderCell(Graphics2D g2, String text, int x, int y, int w, int h, int fontSize) {
            g2.setColor(TABLE_HEADER_BG);
            g2.fillRect(x, y, w, h);
            g2.setColor(TABLE_GRID_LIGHT);
            g2.setStroke(new BasicStroke(0.8f));
            g2.drawRect(x, y, w, h);
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("Arial", Font.PLAIN, fontSize));
            FontMetrics fm = g2.getFontMetrics();
            String[] lines = text.split("\\n", -1);
            int lineHeight = fm.getHeight();
            int totalTextHeight = lineHeight * lines.length;
            int startY = y + (h - totalTextHeight) / 2 + fm.getAscent();
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int textX = x + Math.max(0, (w - fm.stringWidth(line)) / 2);
                g2.drawString(line, textX, startY + i * lineHeight);
            }
        }
    }

    private static class GroupedProductionHeader extends JTableHeader {
        private static final int TOP_H = 36;
        private static final int BOTTOM_H = 38;

        GroupedProductionHeader(TableColumnModel columnModel) {
            super(columnModel);
            setReorderingAllowed(false);
            setResizingAllowed(true);
            setPreferredSize(new Dimension(0, TOP_H + BOTTOM_H));
            setBackground(TABLE_HEADER_BG);
            setForeground(Color.BLACK);
            setFont(new Font("Arial", Font.PLAIN, 14));
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.height = TOP_H + BOTTOM_H;
            return d;
        }

        @Override
        public void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(TABLE_HEADER_BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(TABLE_GRID_LIGHT);
                g2.setStroke(new BasicStroke(0.8f));
                g2.setFont(new Font("Arial", Font.PLAIN, 16));

                int[] x = new int[getColumnModel().getColumnCount() + 1];
                x[0] = 0;
                for (int i = 0; i < getColumnModel().getColumnCount(); i++) {
                    x[i + 1] = x[i] + getColumnModel().getColumn(i).getWidth();
                }

                drawHeaderCell(g2, "Nombre de\nMelanges", x[3], 0, x[4] - x[3], TOP_H + BOTTOM_H, 14);
                drawHeaderCell(g2, "Repass", x[4], 0, x[5] - x[4], TOP_H + BOTTOM_H, 14);
                drawHeaderCell(g2, "Couche", x[5], 0, x[6] - x[5], TOP_H + BOTTOM_H, 14);
                drawHeaderCell(g2, "Passage", x[6], 0, x[7] - x[6], TOP_H + BOTTOM_H, 14);
                drawHeaderCell(g2, "Poids", x[7], 0, x[8] - x[7], TOP_H + BOTTOM_H, 14);
                drawHeaderCell(g2, "Couleur", x[8], 0, x[9] - x[8], TOP_H + BOTTOM_H, 14);
                drawHeaderCell(g2, "N° Série", x[9], 0, x[10] - x[9], TOP_H + BOTTOM_H, 14);
                drawHeaderCell(g2, "Remarq.", x[10], 0, x[11] - x[10], TOP_H + BOTTOM_H, 14);
                drawHeaderCell(g2, "Temps", x[13], 0, x[14] - x[13], TOP_H + BOTTOM_H, 16);
                drawHeaderCell(g2, "ZBEC-INF", x[14], 0, x[15] - x[14], TOP_H + BOTTOM_H, 13);
                drawHeaderCell(g2, "ZBEC-SUP ou\nRETARD", x[15], 0, x[16] - x[15], TOP_H + BOTTOM_H, 12);

                drawHeaderCell(g2, "Type de produit", x[0], 0, x[3] - x[0], TOP_H, 16);
                drawHeaderCell(g2, "Temps de travail", x[11], 0, x[13] - x[11], TOP_H, 14);

                drawHeaderCell(g2, "Article", x[0], TOP_H, x[1] - x[0], BOTTOM_H, 16);
                drawHeaderCell(g2, "Produit", x[1], TOP_H, x[2] - x[1], BOTTOM_H, 14);
                drawHeaderCell(g2, "N° Lot", x[2], TOP_H, x[3] - x[2], BOTTOM_H, 14);
                drawHeaderCell(g2, "de", x[11], TOP_H, x[12] - x[11], BOTTOM_H, 16);
                drawHeaderCell(g2, "à", x[12], TOP_H, x[13] - x[12], BOTTOM_H, 16);

                g2.setStroke(new BasicStroke(1f));
                g2.setColor(TABLE_GRID_LIGHT);
                g2.drawRect(0, 0, x[x.length - 1] - 1, TOP_H + BOTTOM_H - 1);
            } finally {
                g2.dispose();
            }
        }

        private void drawHeaderCell(Graphics2D g2, String text, int x, int y, int w, int h, int fontSize) {
            g2.setColor(TABLE_HEADER_BG);
            g2.fillRect(x, y, w, h);
            g2.setColor(TABLE_GRID_LIGHT);
            g2.setStroke(new BasicStroke(0.8f));
            g2.drawRect(x, y, w, h);
            g2.setColor(Color.BLACK);

            g2.setFont(new Font("Arial", Font.PLAIN, fontSize));
            FontMetrics fm = g2.getFontMetrics();
            String[] lines = text.split("\\n", -1);
            int lineHeight = fm.getHeight();
            int totalTextHeight = lineHeight * lines.length;
            int startY = y + (h - totalTextHeight) / 2 + fm.getAscent();

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int textX = x + Math.max(0, (w - fm.stringWidth(line)) / 2);
                g2.drawString(line, textX, startY + i * lineHeight);
            }
        }
    }

    private static class HeaderRenderer extends DefaultTableCellRenderer {
        HeaderRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.CENTER);
            setBorder(new LineBorder(TABLE_GRID_LIGHT, 1));
            setBackground(TABLE_HEADER_BG);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            String text = String.valueOf(value).replace("\n", "<br>");
            setText("<html><center>" + text + "</center></html>");
            setFont(new Font("Arial", Font.PLAIN, 14));
            return this;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ProdMelBanb().setVisible(true));
    }
}



// ---- Support bilancia incorporato da BanburyForm(21).java ----
class ProdMelBanbBalanceSupport {

    public static final String APP_VERSION = "2026-07-08 v23 - RESET RAPPORT reinitialise aussi Operateur Balance V194.29";

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new ProdMelBanbBalanceSupport.FormFrame().setVisible(true));
    }

    static final Color C_BG         = new Color(0xEE, 0xEE, 0xEE);
    static final Color C_PANEL      = new Color(0xEE, 0xEE, 0xEE);
    static final Color C_HEADER     = new Color(0xAA, 0xCC, 0xFF);
    static final Color C_FIELD_BG   = Color.WHITE;
    static final Color C_TEXT       = Color.BLACK;
    static final Color C_BTN_BLUE   = new Color(0x00, 0x70, 0xC0);
    static final Color C_BTN_RED    = new Color(0xCC, 0x00, 0x00);
    static final Color C_BTN_ORANGE = new Color(0xE0, 0x80, 0x00);
    static final Color C_BTN_YELLOW = new Color(0xFF, 0xDD, 0x00);
    static final Color C_NET_BG     = new Color(0x00, 0xFF, 0xFF);
    static final Color PALETTE_BTN_DEFAULT_BG = new Color(0xDD, 0xDD, 0xDD);
    static final Color PALETTE_BTN_SELECTED_BG = new Color(0x00, 0x99, 0x33);

    static class PaletteToggleButton extends JToggleButton {
        PaletteToggleButton(String label) {
            super(label);
            setModel(new ToggleButtonModel());
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorderPainted(false);
            setRolloverEnabled(false);
        }

        @Override
        public void updateUI() {
            setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Color fill = isSelected() ? new Color(0x00, 0x99, 0x33) : new Color(0xDD, 0xDD, 0xDD);
            g2.setColor(fill);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Color border = (isSelected() ? new Color(0x00, 0x99, 0x33) : new Color(0xDD, 0xDD, 0xDD)).darker();
            g2.setColor(border);
            g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            g2.dispose();
        }
    }

    public static class FormFrame extends JFrame {

        // V191.07: parent coerente per i popup della bilancia.
        private Component getDialogParentComponent() {
            Window w = SwingUtilities.getWindowAncestor(getRootPane());
            if (w != null && w.isShowing()) return w;
            if (this.isShowing()) return this;
            Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            return active != null ? active : this;
        }
    static class UppercaseDocumentFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            if (string != null) {
                string = string.toUpperCase().replaceAll("[^A-Z0-9]", "");
            }
            super.insertString(fb, offset, string, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            if (text != null) {
                text = text.toUpperCase().replaceAll("[^A-Z0-9]", "");
            }
            super.replace(fb, offset, length, text, attrs);
        }
    }


        private static final String CONFIG_FILE_NAME = "banbury.properties";
        private final Properties appConfig = new Properties();

        private final double initialUiScale;
        private double uiScale;
        private final Dimension screenSize;
        private final int targetFrameWidth;
        private final int targetFrameHeight;
        private Dimension baseContentPreferredSize;

        private final BalanceService balanceService = new BalanceService(new BalanceService.Callback() {
            @Override public boolean isManualMode() {
                // V194.17: la saisie manuelle de POIDS BRUT ne doit plus bloquer
                // le flux TCP de la balance. Le service continue a lire le stream;
                // pendant la saisie manuelle on ignore seulement l'affichage automatique.
                return false;
            }

            @Override public void onWeightKg(int kg) {
                lastAutomaticPoidsBrut = String.valueOf(kg);
                if (tfPoidsBrut != null && !poidsBrutManualEdit) {
                    tfPoidsBrut.setText(lastAutomaticPoidsBrut);
                }
                updatePoidsNet();
            }

            @Override public void onStatus(String message, Color color) {
                showStatusMessage(message, color);
            }

            @Override public String getConfiguredHost() {
                return getConfiguredScaleIpForCurrentPoste();
            }

            @Override public int getConfiguredPort() {
                return getConfiguredScalePortForCurrentPoste();
            }
        });

        private JTextField tfDate, tfTournee, tfOperateurCode, tfOperateurName, tfOperateurBalanceDisplay;

        private JLabel lblTourneeDesc;
        private Color operateurCodeDefaultBg;
        private Color produitCodeDefaultBg;
        private JTextField tfProduitCode, tfProduitName;
        private JTextField tfProvName, tfDestName;
        private JTextField tfNLot, tfNbrMelange, tfPesees;
        private JTextField tfCommentaires;
        private JTextField tfPoidsBrut, tfTare, tfPoidsNet;
        private Border poidsBrutDefaultBorder;
        private boolean poidsBrutManualEdit = false;
        private String poidsBrutBeforeManualEdit = "0";
        private String lastAutomaticPoidsBrut = "0";
        private JComboBox<String> cbProvenance, cbDestination, cbCouche, cbType;
        private JComboBox<String> cbSuffixe, cbPassage, cbNMelange, cbClairFonce, cbRemarque;

        private JButton btnImpressionBanbury, btnAnnulerTare, btnCorrectionEntree;
        private JButton btnStampaProva, btnDestockage, btnReset, btnEnregistrer;
        private PaletteToggleButton btnPal9, btnPal18, btnPal27, btnPal36;
        private PaletteToggleButton selectedPaletteButton;
        private JButton btnSetup;
        private JLabel lblPoste, lblStatusMessage;
        private volatile boolean printInProgress = false;
        private final int startupBanbury;

        // V190.06: gestione scelta turno al cambio turno per le pesate Banbury.
        // V193.03: alert alla prima pesata dopo l'avvio e dopo ogni cambio turno
        // (06:00, 14:00, 22:00). Resta attivo fino alla scelta "Equipe actuelle".
        private String lastConfirmedCurrentShiftBoundaryKey = null;
        private String pendingSaveTourneeCode = null;
        private String pendingSaveTourneeId = null;

        private Timer clockTimer;

        FormFrame() {
            this(0);
        }

        FormFrame(int startupBanbury) {
            super("Banbury – Saisie Pesée - " + APP_VERSION);
            this.startupBanbury = (startupBanbury == 1 || startupBanbury == 2) ? startupBanbury : 0;
            if (startupBanbury == 1 || startupBanbury == 2) {
                loadAppConfig(startupBanbury == 2 ? "POSTE 2" : "POSTE 1");
            } else {
                loadAppConfig();
            }
            this.screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            this.initialUiScale = computeScale(screenSize);
            this.uiScale = initialUiScale;
            this.targetFrameWidth = Math.max(s(840), (int) Math.round(screenSize.width * 0.485));
            this.targetFrameHeight = Math.max(s(800), (int) Math.round(screenSize.height * 0.88));

            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setResizable(true);
            buildUI();
            applyStartupBanburySelection();
            pack();
            baseContentPreferredSize = getContentPane().getPreferredSize();
            captureBaseMetrics(getContentPane());
            setSize(targetFrameWidth, targetFrameHeight);
            setMinimumSize(new Dimension(Math.max(760, (int) Math.round(targetFrameWidth * 0.72)),
                    Math.max(620, (int) Math.round(targetFrameHeight * 0.72))));
            installResponsiveResize();

            setLocationRelativeTo(null);
            startClock();
            // V193.03: alla prima pesata dopo l'avvio il turno deve essere sempre confermato.
            // Dopo ogni cambio turno (06:00, 14:00, 22:00) la chiave cambia e la domanda
            // resta attiva finche l'operatore non sceglie "Equipe actuelle".
            lastConfirmedCurrentShiftBoundaryKey = null;
        }

        private double computeScale(Dimension screen) {
            double byWidth = screen.width / 1366.0;
            double byHeight = screen.height / 768.0;
            double s = Math.min(byWidth, byHeight);
            // V190.02: nel report integrato la bilancia non deve ingrandirsi oltre la base,
            // altrimenti su PC con DPI/risoluzioni diverse la parte destra viene tagliata.
            if (s < 0.85) s = 0.85;
            if (s > 1.00) s = 1.00;
            return s;
        }

        private int s(int px) {
            return Math.max(1, (int) Math.round(px * uiScale));
        }

        private Font uiFont(int style, int size) {
            return new Font("SansSerif", style, s(size));
        }

        private JPanel rowPanel() {
            JPanel p = new JPanel(new GridBagLayout());
            p.setOpaque(false);
            return p;
        }

        void buildUI() {
            JPanel root = new JPanel(new BorderLayout(s(4), s(4)));
            root.setBackground(C_BG);
            root.setBorder(new EmptyBorder(s(6), s(6), s(6), s(6)));

            root.add(buildMainContent(), BorderLayout.CENTER);
            root.add(buildBottomBar(), BorderLayout.SOUTH);

            setContentPane(root);
            ensureLotPrefix();
            updateTourneeLabel();
        }

        private void installResponsiveResize() {
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    SwingUtilities.invokeLater(() -> refreshResponsiveScale());
                }
            });
            SwingUtilities.invokeLater(() -> refreshResponsiveScale());
        }

        private void captureBaseMetrics(Component component) {
            if (component instanceof JComponent) {
                JComponent jc = (JComponent) component;
                jc.putClientProperty("basePrefSize", jc.getPreferredSize());
                jc.putClientProperty("baseMinSize", jc.getMinimumSize());
                jc.putClientProperty("baseMaxSize", jc.getMaximumSize());
                jc.putClientProperty("baseFont", jc.getFont());
            }
            if (component instanceof Container) {
                Container container = (Container) component;
                for (Component child : container.getComponents()) {
                    captureBaseMetrics(child);
                }
            }
        }

        private void refreshResponsiveScale() {
            if (baseContentPreferredSize == null) {
                return;
            }

            int w = Math.max(1, getContentPane().getWidth());
            int h = Math.max(1, getContentPane().getHeight());
            double scaleX = w / (double) Math.max(1, baseContentPreferredSize.width);
            double scaleY = h / (double) Math.max(1, baseContentPreferredSize.height);
            double ratio = Math.min(scaleX, scaleY);
            ratio = Math.max(0.72, Math.min(1.15, ratio));
            uiScale = initialUiScale * ratio;

            applyResponsiveScale(getContentPane(), ratio);
            revalidate();
            repaint();
        }

        private void applyResponsiveScale(Component component, double ratio) {
            if (component instanceof JComponent) {
                JComponent jc = (JComponent) component;
                Font baseFont = (Font) jc.getClientProperty("baseFont");
                if (baseFont != null) {
                    float newSize = Math.max(9f, (float) (baseFont.getSize2D() * ratio));
                    jc.setFont(baseFont.deriveFont(baseFont.getStyle(), newSize));
                }

                Dimension basePref = (Dimension) jc.getClientProperty("basePrefSize");
                Dimension baseMin = (Dimension) jc.getClientProperty("baseMinSize");
                Dimension baseMax = (Dimension) jc.getClientProperty("baseMaxSize");

                if (basePref != null) {
                    jc.setPreferredSize(scaleDimension(basePref, ratio));
                }
                if (baseMin != null) {
                    jc.setMinimumSize(scaleDimension(baseMin, ratio));
                }
                if (baseMax != null && baseMax.width < 10000 && baseMax.height < 10000) {
                    jc.setMaximumSize(scaleDimension(baseMax, ratio));
                }
            }

            if (component instanceof Container) {
                Container container = (Container) component;
                for (Component child : container.getComponents()) {
                    applyResponsiveScale(child, ratio);
                }
            }
        }

        private Dimension scaleDimension(Dimension d, double ratio) {
            return new Dimension(
                    Math.max(1, (int) Math.round(d.width * ratio)),
                    Math.max(1, (int) Math.round(d.height * ratio))
            );
        }

        JPanel buildMainContent() {
            JPanel main = new JPanel();
            main.setBackground(C_BG);
            main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
            main.setBorder(new CompoundBorder(
                    new LineBorder(new Color(0x9A, 0x9A, 0x9A), 1),
                    new EmptyBorder(s(6), s(6), s(6), s(6))));

            JPanel top = buildTopBar();
            top.setAlignmentX(Component.CENTER_ALIGNMENT);
            top.setMaximumSize(new Dimension(Integer.MAX_VALUE, top.getPreferredSize().height));

            JPanel form = buildFormArea();
            form.setAlignmentX(Component.CENTER_ALIGNMENT);
            form.setMaximumSize(new Dimension(Integer.MAX_VALUE, form.getPreferredSize().height));

            JPanel weight = buildWeightArea();
            weight.setAlignmentX(Component.CENTER_ALIGNMENT);
            weight.setMaximumSize(new Dimension(Integer.MAX_VALUE, weight.getPreferredSize().height));

            main.add(top);
            main.add(Box.createVerticalStrut(0));
            main.add(form);
            main.add(Box.createVerticalStrut(0));
            main.add(weight);
            return main;
        }

        JPanel buildTopBar() {
            // V189.05: area prodotto convertita in JPanel assoluto.
            // Tutti i campi/label/combo principali sono ora oggetti Swing normali
            // posizionati con setBounds(...) per poterli ordinare rapidamente.
            JPanel p = new JPanel(null);
            p.setBackground(C_BG);
            p.setBorder(new EmptyBorder(0, 0, s(2), 0));
            // V189.44: riduce solo lo spazio vuoto sotto COMMENT/REMARQUE/COUCHE
            // nel pannello interno bilancia. Le coordinate degli oggetti sopra restano
            // identiche, e il blocco Poids/Palette sale senza sovrapporsi o finire
            // dietro al pannello superiore.
            p.setPreferredSize(new Dimension(s(1660), s(94)));  // V192.04: lascia piu spazio alla riga Comment prima del blocco Poids

            // Componenti tecnici ancora usati dalla logica, ma nascosti nel report integrato.
            tfDate = field("", 14);
            tfDate.setVisible(false);
            tfDate.setBounds(s(0), s(0), s(1), s(1));
            p.add(tfDate);

            tfTournee = field("", 16);
            tfTournee.setEditable(false);
            tfTournee.setFocusable(false);
            tfTournee.setVisible(false);
            tfTournee.setNextFocusableComponent(tfOperateurCode);
            tfTournee.setBounds(s(0), s(0), s(1), s(1));
            p.add(tfTournee);

            lblTourneeDesc = new JLabel();
            lblTourneeDesc.setVisible(false);
            lblTourneeDesc.setBounds(s(0), s(0), s(1), s(1));
            p.add(lblTourneeDesc);

            tfOperateurCode = field("", 8);
            operateurCodeDefaultBg = tfOperateurCode.getBackground();
            tfOperateurCode.setVisible(false);
            tfOperateurCode.setBounds(s(0), s(0), s(1), s(1));
            tfOperateurCode.addActionListener(e -> {
                loadOperateurName();
                if (tfProduitCode != null) {
                    SwingUtilities.invokeLater(() -> tfProduitCode.requestFocusInWindow());
                }
            });
            tfOperateurCode.addFocusListener(new FocusAdapter() {
                @Override public void focusLost(FocusEvent e) { loadOperateurName(); }
            });
            p.add(tfOperateurCode);

            tfOperateurName = field("", 22);
            tfOperateurName.setEditable(false);
            tfOperateurName.setFocusable(false);
            tfOperateurName.setVisible(false);
            tfOperateurName.setBounds(s(0), s(0), s(1), s(1));
            p.add(tfOperateurName);

            lblPoste = new JLabel(getSelectedPosteLabel());
            lblPoste.setVisible(false);
            lblPoste.setBounds(s(0), s(0), s(1), s(1));
            p.add(lblPoste);
            updateTourneeLabel();

            // Coordinate modificabili dell'area rossa.
            final int row0 = 12;
            final int row1 = 50;
            final int row2 = 52;  // V192.00: Comment/Couche/Passage/Couleur alzati vicino a Destination
            final int row3 = 126;
            final int h = 28;

            // ---- Riga 0: N° Produit / N° LOT ----
            JLabel lblProd = new JLabel("N°Produit");
            lblProd.setFont(uiFont(Font.BOLD, 16));
            lblProd.setBounds(s(440), s(row0), s(105), s(h));
            p.add(lblProd);

            tfProduitCode = field("", 8);
            ((AbstractDocument) tfProduitCode.getDocument()).setDocumentFilter(new UppercaseDocumentFilter());
            produitCodeDefaultBg = tfProduitCode.getBackground();
            tfProduitCode.setBounds(s(530), s(row0), s(120), s(h));
            tfProduitCode.addActionListener(e -> {
                loadProduitName();
                refreshPeseeFromDatabase();
                if (tfNLot != null) SwingUtilities.invokeLater(() -> tfNLot.requestFocusInWindow());
            });
            tfProduitCode.addFocusListener(new FocusAdapter() {
                @Override public void focusLost(FocusEvent e) {
                    loadProduitName();
                    refreshPeseeFromDatabase();
                }
            });
            tfProduitCode.setNextFocusableComponent(tfNLot);
            p.add(tfProduitCode);

            tfProduitName = field("", 22);
            tfProduitName.setEditable(false);
            tfProduitName.setFocusable(false);
            tfProduitName.setBounds(s(660), s(row0), s(360), s(h));
            p.add(tfProduitName);

            JLabel lblLotTop = new JLabel("N°LOT");
            lblLotTop.setFont(uiFont(Font.BOLD, 16));
            lblLotTop.setBounds(s(1050), s(row0), s(70), s(h));
            p.add(lblLotTop);

            tfNLot = field("", 14);
            tfNLot.setBounds(s(1120), s(row0), s(140), s(h));
            ensureLotPrefix();
            tfNLot.addActionListener(e -> refreshPeseeFromDatabase());
            tfNLot.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { ensureLotPrefix(); }
                @Override public void focusLost(FocusEvent e) { refreshPeseeFromDatabase(); }
            });
            p.add(tfNLot);

            // ---- Riga 1: Provenance / Destination ----
            JLabel lblProv = new JLabel("Provenance");
            lblProv.setFont(uiFont(Font.BOLD, 16));
            lblProv.setBounds(s(10), s(row1), s(115), s(h));
            lblProv.setVisible(false);
            p.add(lblProv);

            cbProvenance = new JComboBox<>(new String[]{"CBANB1", "CBANB2"});
            styleCombo(cbProvenance);
            cbProvenance.setBounds(s(125), s(row1), s(120), s(h));
            cbProvenance.setVisible(false);
            cbProvenance.addActionListener(e -> {
                if (startupBanbury == 1 || startupBanbury == 2) {
                    String lockedCode = startupBanbury == 2 ? "CBANB2" : "CBANB1";
                    if (!lockedCode.equals(normalizeBanburyCode((String) cbProvenance.getSelectedItem()))) {
                        cbProvenance.setSelectedItem(lockedCode);
                        return;
                    }
                }
                tfProvName.setText(mapBanburyName(normalizeBanburyCode((String) cbProvenance.getSelectedItem())));
            });
            p.add(cbProvenance);

            tfProvName = field("", 22);
            tfProvName.setEditable(false);
            tfProvName.setFocusable(false);
            tfProvName.setBounds(s(260), s(row1), s(120), s(h));
            tfProvName.setVisible(false);
            p.add(tfProvName);

            JLabel lblDest = new JLabel("Destination");
            lblDest.setFont(uiFont(Font.BOLD, 16));
            lblDest.setBounds(s(10), s(row0), s(115), s(h));
            p.add(lblDest);

            cbDestination = new JComboBox<>(new String[]{
                    "CBANB1", "CBANB2", "CDECHET", "CL100", "CL200",
                    "CL201", "CL202", "CL300", "CLABO", "CS212", "CSTOCKBANB"
            });
            styleCombo(cbDestination);
            cbDestination.setBounds(s(125), s(row0), s(150), s(h));
            cbProvenance.setNextFocusableComponent(cbDestination);
            cbDestination.setNextFocusableComponent(tfNLot);
            cbDestination.addActionListener(e -> updateDestinationText());
            p.add(cbDestination);

            tfDestName = field("", 22);
            tfDestName.setEditable(false);
            tfDestName.setFocusable(false);
            tfDestName.setBounds(s(290), s(row0), s(140), s(h));
            p.add(tfDestName);

            // ---- Riga 2: Couche / Nbr Melange / Suffixe / #Pesées ----
            JLabel lblCouche = lbl("Couche");
            lblCouche.setBounds(s(980), s(row2), s(95), s(h));
            p.add(lblCouche);

            cbCouche = new JComboBox<>(new String[]{"", "SUPÉRIEUR", "INFÉRIEUR", "SOUS TAPIS", "NETTOYAGE", "PREMAST."});
                        cbCouche.setBackground(Color.WHITE);
            cbCouche.setMaximumRowCount(8);
            cbCouche.setBounds(s(1050), s(row2), s(150), s(h));
            cbCouche.setNextFocusableComponent(tfNbrMelange);
            p.add(cbCouche);

            tfNbrMelange = field("", 5);
            tfNbrMelange.setNextFocusableComponent(cbSuffixe);
            if (tfNLot != null) tfNLot.setNextFocusableComponent(cbNMelange);

            JLabel lblSuffixe = lbl("Suffixe");
            lblSuffixe.setBounds(s(1490), s(row0), s(75), s(h));
            p.add(lblSuffixe);

            cbSuffixe = new JComboBox<>(new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9"});
            styleCombo(cbSuffixe);
            cbSuffixe.setBounds(s(1560), s(row0), s(60), s(h));
            cbSuffixe.setNextFocusableComponent(cbDestination);
            p.add(cbSuffixe);

            JLabel lblNbr = new JLabel("Nbr Melange");
            lblNbr.setFont(uiFont(Font.BOLD, 16));
            lblNbr.setHorizontalAlignment(SwingConstants.CENTER);
            // V189.41: posizione finale impostata nella zona pulsanti,
            // tra Palette 18Kg e Impression Banbury Haut.
            
           
           
            // ---- Riga 3: Passage / N° Série / Couleur / Type ----
            JLabel lblPassage = lbl("Passage");
            lblPassage.setBounds(s(1230), s(row2), s(95), s(h));
            p.add(lblPassage);

            cbPassage = new JComboBox<>(new String[]{"1er", "2eme", "3eme", "4eme"});
            styleCombo(cbPassage);
            cbPassage.setBounds(s(1320), s(row2), s(90), s(h));
            cbPassage.setNextFocusableComponent(cbNMelange);
            p.add(cbPassage);

            JLabel lblSerie = lbl("N° Série");
            lblSerie.setBounds(s(1290), s(row0), s(95), s(h));
            p.add(lblSerie);

            cbNMelange = new JComboBox<>(new String[]{"", "SERIE A", "A", "A1", "A2", "A3", "A4", "SERIE B", "SERIE C", "SERIE D", "SERIE E", "SERIE F", "SERIE G", "SERIE H", "SERIE I", "SERIE J", "SERIE K", "SERIE L", "L", "L1", "L2", "L3", "L4", "SERIE M", "SERIE R"});
            styleCombo(cbNMelange);
            cbNMelange.setBounds(s(1365), s(row0), s(95), s(h));
            cbNMelange.setNextFocusableComponent(cbSuffixe);
            p.add(cbNMelange);

            JLabel lblCouleur = lbl("Couleur");
            lblCouleur.setBounds(s(1440), s(row2), s(90), s(h));
            p.add(lblCouleur);

            cbClairFonce = new JComboBox<>(new String[]{"", "CLAIR", "FONCÉ", "MEDIUM"});
            styleCombo(cbClairFonce);
            cbClairFonce.setBounds(s(1520), s(row2), s(100), s(h));
            cbClairFonce.setNextFocusableComponent(cbType);
            p.add(cbClairFonce);

            
            // ---- Riga 4: Comment / Remarque ----
            // V189.31: riga COMMENT/REMARQUE ripristinata alla quota originale; zona bilancia abbassata.
            JLabel lblComment = lbl("Comment");
            lblComment.setBounds(s(10), s(row2), s(95), s(h));
            p.add(lblComment);

            tfCommentaires = field("", 40);
            ((AbstractDocument) tfCommentaires.getDocument()).setDocumentFilter(new DocumentFilter() {
                private final int MAX = 7550;
                private String normalizeCommentaire(String value) {
                    if (value == null) return null;
                    String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD);
                    normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
                    return normalized.toUpperCase();
                }
                @Override public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                        throws BadLocationException {
                    if (string == null) return;
                    string = normalizeCommentaire(string);
                    if (fb.getDocument().getLength() + string.length() <= MAX) super.insertString(fb, offset, string, attr);
                }
                @Override public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                        throws BadLocationException {
                    if (text == null) return;
                    text = normalizeCommentaire(text);
                    int currentLength = fb.getDocument().getLength();
                    int overLimit = (currentLength - length + text.length()) - MAX;
                    if (overLimit <= 0) super.replace(fb, offset, length, text, attrs);
                    else {
                        String cut = text.substring(0, text.length() - overLimit);
                        super.replace(fb, offset, length, cut, attrs);
                    }
                }
            });
            tfCommentaires.setBounds(s(125), s(row2), s(420), s(h));
            tfCommentaires.setNextFocusableComponent(cbRemarque);
            p.add(tfCommentaires);

            JLabel lblRemarque = lbl("Remarque");
            lblRemarque.setBounds(s(570), s(row2), s(90), s(h));
            p.add(lblRemarque);

            cbRemarque = new JComboBox<>(new String[]{
                    "", "Avec Correction Couleur", "A repasser", "Repassé", "A repasser courbe HT",
                    "Controle couleur", "Avec Accelerateur", "Avec Accelerateur + paillette",
                    "1er passage", "2eme passage", "3eme passage", "Phase Direct"
            });
            styleCombo(cbRemarque);
            cbRemarque.setBounds(s(675), s(row2), s(280), s(h));
            cbRemarque.setNextFocusableComponent(btnEnregistrer);
            p.add(cbRemarque);

            tfCommentaires.addActionListener(e -> cbRemarque.requestFocusInWindow());

            updateDestinationText();
            applyDefaultProvenanceDestination();

            // V189.40: uniforma il font di tutti i campi dinamici e combo del riquadro
            // allo stesso carattere/dimensione della combo N° Série.
            Font topFormValueFont = cbNMelange.getFont();
            Component[] topFormValueComponents = {
                    tfProduitCode, tfProduitName, tfNLot,
                    cbProvenance, tfProvName, cbDestination, tfDestName,
                    cbCouche, tfNbrMelange, tfPesees,
                    cbPassage, cbNMelange, cbSuffixe,
                    cbClairFonce, cbType,
                    tfCommentaires, cbRemarque
            };
            for (Component c : topFormValueComponents) {
                if (c != null) c.setFont(topFormValueFont);
            }

            return p;
        }

        JPanel buildFormArea() {
            // V189.05: i controlli prima presenti qui sono stati spostati in buildTopBar()
            // dentro un unico pannello assoluto. Manteniamo il metodo per non alterare
            // la struttura di buildMainContent().
            JPanel p = new JPanel(null);
            p.setBackground(C_BG);
            p.setPreferredSize(new Dimension(s(1660), 0));
            p.setMinimumSize(new Dimension(s(1660), 0));
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 0));
            return p;
        }

        JPanel buildWeightArea() {
            // V189.07: area bilancia / palette convertita in JPanel assoluto con altezza aumentata.
            // Tutti gli oggetti del riquadro rosso sono componenti Swing normali
            // posizionati con setBounds(...) per poterli ordinare rapidamente.
            JPanel p = new JPanel(null);
            p.setBackground(C_BG);
            p.setBorder(new EmptyBorder(s(2), 0, 0, 0));
            // V189.20: riquadro bilancia compattato in altezza; bordo inferiore alzato.
            // V189.21: bordo inferiore interno bilancia abbassato di pochi pixel,
            // senza modificare BALANCE_PANEL_HEIGHT e quindi senza spostare le righe sottostanti.
            // V189.22: bordo inferiore interno bilancia abbassato ancora leggermente,
            // senza modificare BALANCE_PANEL_HEIGHT e senza spostare Production realisee.
            p.setPreferredSize(new Dimension(s(1660), s(180)));  // V192.01: altezza aumentata per non tagliare la riga Poids Net

            final int labelX = 10;
            final int fieldX = 125;
            final int kgX = 232;  // V189.20: Kg spostati a destra per distanziarli dai campi peso
            // V190.06: sposta verso l'alto il blocco Poids/Palette/azioni.
            // Modificare solo questo valore per alzare/abbassare tutto il gruppo.
            final int weightBlockShiftY = 8;  // V192.00: mantiene Poids a 12 px sotto Comment dopo la riduzione della top bar
            // V189.19: interlinea verticale ridotta, altezze invariate.
            final int yBrut = 16 - weightBlockShiftY;
            final int yTare = 72 - weightBlockShiftY;
            final int yNet = 128 - weightBlockShiftY;
            final int labelW = 110;
            final int fieldW = 90;
            // V189.18: altezze ridotte di circa 1/4, interlinee/coordinate Y invariate.
            final int fieldH = 41;
            final int kgW = 35;

            JLabel lblPoidsBrut = new JLabel("Poids Brut");
            lblPoidsBrut.setFont(uiFont(Font.BOLD, 17));
            lblPoidsBrut.setBounds(s(labelX), s(yBrut), s(labelW), s(fieldH));
            p.add(lblPoidsBrut);

            tfPoidsBrut = new JTextField("0", 5);
            tfPoidsBrut.setFont(new Font("Monospaced", Font.BOLD, s(22)));
            tfPoidsBrut.setHorizontalAlignment(SwingConstants.RIGHT);
            tfPoidsBrut.setBackground(Color.WHITE);
            tfPoidsBrut.setForeground(Color.BLACK);
            tfPoidsBrut.setDisabledTextColor(Color.BLACK);
            tfPoidsBrut.setEditable(false);
            tfPoidsBrut.setFocusable(true);
            tfPoidsBrut.setBorder(new LineBorder(Color.DARK_GRAY, 1));
            tfPoidsBrut.setBounds(s(fieldX), s(yBrut), s(fieldW), s(fieldH));
            p.add(tfPoidsBrut);

            JLabel lblKgBrut = new JLabel("Kg");
            lblKgBrut.setFont(uiFont(Font.BOLD, 15));
            lblKgBrut.setBounds(s(kgX), s(yBrut), s(kgW), s(fieldH));
            p.add(lblKgBrut);

            JLabel lblTare = new JLabel("Tare");
            lblTare.setFont(uiFont(Font.BOLD, 17));
            lblTare.setBounds(s(labelX), s(yTare), s(labelW), s(fieldH));
            p.add(lblTare);

            tfTare = new JTextField("0", 5);
            tfTare.setFont(new Font("Monospaced", Font.BOLD, s(22)));
            tfTare.setHorizontalAlignment(SwingConstants.RIGHT);
            tfTare.setBackground(Color.WHITE);
            tfTare.setForeground(Color.BLACK);
            tfTare.setDisabledTextColor(Color.BLACK);
            tfTare.setEditable(true);
            tfTare.setFocusable(false);
            tfTare.setBorder(new LineBorder(Color.DARK_GRAY, 1));
            tfTare.setBounds(s(fieldX), s(yTare), s(fieldW), s(fieldH));
            p.add(tfTare);

            JLabel lblKgTare = new JLabel("Kg");
            lblKgTare.setFont(uiFont(Font.BOLD, 15));
            lblKgTare.setBounds(s(kgX), s(yTare), s(kgW), s(fieldH));
            p.add(lblKgTare);

            JLabel lblPoidsNet = new JLabel("Poids Net");
            lblPoidsNet.setFont(uiFont(Font.BOLD, 17));
            lblPoidsNet.setBounds(s(labelX), s(yNet), s(labelW), s(fieldH));
            p.add(lblPoidsNet);

            tfPoidsNet = new JTextField("0", 5);
            tfPoidsNet.setFont(new Font("Monospaced", Font.BOLD, s(22)));
            tfPoidsNet.setHorizontalAlignment(SwingConstants.RIGHT);
            tfPoidsNet.putClientProperty("KEEP_POIDS_NET_CYAN", Boolean.TRUE);
            tfPoidsNet.setBackground(C_NET_BG);
            tfPoidsNet.setForeground(Color.BLACK);
            tfPoidsNet.setDisabledTextColor(Color.BLACK);
            tfPoidsNet.setOpaque(true);
            tfPoidsNet.setEditable(false);
            tfPoidsNet.setFocusable(false);
            tfPoidsNet.setBorder(new LineBorder(Color.DARK_GRAY, 1));
            tfPoidsNet.setBounds(s(fieldX), s(yNet), s(fieldW), s(fieldH));
            p.add(tfPoidsNet);

            JLabel lblKgNet = new JLabel("Kg");
            lblKgNet.setFont(uiFont(Font.BOLD, 15));
            lblKgNet.setBounds(s(kgX), s(yNet), s(kgW), s(fieldH));
            p.add(lblKgNet);

            poidsBrutDefaultBorder = tfPoidsBrut.getBorder();
            ((AbstractDocument) tfPoidsBrut.getDocument()).setDocumentFilter(new DigitsOnlyDocumentFilter());
            tfPoidsBrut.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        enablePoidsBrutManualEdit();
                    }
                }
            });
            tfPoidsBrut.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    disablePoidsBrutManualEdit();
                }
            });
            // V193.21: se l'operatore entra in modifica manuale del Poids Brut
            // e preme ESC, annulla la modalita manuale e riattiva subito lo stream bilancia.
            tfPoidsBrut.getInputMap(JComponent.WHEN_FOCUSED).put(
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "resumePoidsBrutStreamEsc");
            tfPoidsBrut.getActionMap().put("resumePoidsBrutStreamEsc", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    resumePoidsBrutStreamFromEsc();
                }
            });
            // V194.08: in modifica manuale, INVIO/TAB confermano il valore scritto
            // senza riaprire subito lo stream. Lo stream resta in pausa fino a Enregistrer,
            // cosi il valore manuale non viene sovrascritto dalla bilancia prima del salvataggio.
            AbstractAction confirmPoidsBrutManualAction = new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    confirmPoidsBrutManualInput();
                }
            };
            tfPoidsBrut.getInputMap(JComponent.WHEN_FOCUSED).put(
                    KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "confirmPoidsBrutManualEnter");
            tfPoidsBrut.getActionMap().put("confirmPoidsBrutManualEnter", confirmPoidsBrutManualAction);
            tfPoidsBrut.getInputMap(JComponent.WHEN_FOCUSED).put(
                    KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "confirmPoidsBrutManualTab");
            tfPoidsBrut.getActionMap().put("confirmPoidsBrutManualTab", confirmPoidsBrutManualAction);

            // V189.13: altezze uniformate e ridotte di 3 px per campi dinamici e pulsanti.
            // Mantiene invariate posizioni X/Y e interlinea tra i pulsanti.
            final int palX1 = 280;
            final int palX2 = 515;
            final int actX1 = 750;
            final int actX2 = 985;
            final int btnY1 = yBrut;
            final int btnY2 = yTare;
            final int btnW = 150;
            final int btnH = fieldH;
            final int gap = 10;

            btnPal9 = new PaletteToggleButton("Palette 9Kg");
            btnPal18 = new PaletteToggleButton("Palette 18Kg");
            btnPal27 = new PaletteToggleButton("Palette 27Kg");
            btnPal36 = new PaletteToggleButton("Palette 36Kg");

            for (PaletteToggleButton b : new PaletteToggleButton[]{btnPal9, btnPal18, btnPal27, btnPal36}) {
                String t = b.getText();
                b.setText("<html><center>" + t + "</center></html>");
                b.setFont(uiFont(Font.BOLD, 13));
                b.setForeground(Color.BLACK);
                b.setFocusable(false);
                applyPaletteButtonStyle(b, false);
            }

            btnPal9.setBounds(s(palX1), s(btnY1), s(140), s(btnH));
            btnPal18.setBounds(s(446), s(btnY1), s(140), s(btnH));
            btnPal27.setBounds(s(palX1), s(btnY2), s(140), s(btnH));
            btnPal36.setBounds(s(446), s(btnY2), s(140), s(btnH));
            btnPal9.addActionListener(e -> handlePaletteSelection(btnPal9, 9));
            btnPal18.addActionListener(e -> handlePaletteSelection(btnPal18, 18));
            btnPal27.addActionListener(e -> handlePaletteSelection(btnPal27, 27));
            btnPal36.addActionListener(e -> handlePaletteSelection(btnPal36, 36));
            p.add(btnPal9);
            p.add(btnPal18);
            p.add(btnPal27);
            p.add(btnPal36);

            btnImpressionBanbury = makeButton("<html><center>Impression<br>Banbury Haut</center></html>",
                    new Color(0x80, 0x80, 0x80), Color.WHITE);
            btnImpressionBanbury.setBounds(s(850), s(btnY1), s(130), s(btnH));
            btnImpressionBanbury.setFocusable(false);
            btnImpressionBanbury.addActionListener(e -> {
                try {
                    printBanburyHautWithJasper();
                    clearNbrMelangeAfterBanburyHautPrint();
                    resumePoidsBrutStream();
                    showStatusMessage("Impression Banbury Haut OK. Nbr Melange cancellato. Stream bilancia riattivato.", new Color(0x00, 0x80, 0x20));
                } catch (PrinterException ex) {
                    showStatusMessage("Errore Impression Banbury Haut: " + ex.getMessage(), Color.RED);
                }
            });
            p.add(btnImpressionBanbury);
            
            JLabel lblNbr = new JLabel("Nbr Melange");
            lblNbr.setFont(uiFont(Font.BOLD, 16));
            lblNbr.setHorizontalAlignment(SwingConstants.CENTER);
            lblNbr.setBounds(s(595), s(btnY1), s(140), s(btnH));
            p.add(lblNbr);

            tfNbrMelange.setBounds(s(725), s(btnY1), s(85), s(btnH));
            p.add(tfNbrMelange);

            JLabel lblPes = new JLabel("<html><center><i>#Pesées</i></center></html>");
            lblPes.setFont(uiFont(Font.BOLD, 16));
            lblPes.setHorizontalAlignment(SwingConstants.CENTER);
            lblPes.setBounds(s(595), s(btnY2), s(100), s(btnH));
            p.add(lblPes);

            tfPesees = field("1", 4);
            tfPesees.setBounds(s(725), s(btnY2), s(85), s(btnH));
            tfPesees.setEditable(false);
            tfPesees.setFocusable(false);
            p.add(tfPesees);

            p.setComponentZOrder(lblNbr, 0);
            p.setComponentZOrder(tfNbrMelange, 0);
            p.setComponentZOrder(lblPes, 0);
            p.setComponentZOrder(tfPesees, 0);

            // V189.43: NBR MELANGE spostato nella posizione originale di #Pesées (riga alta).
            // #Pesées resta sulla riga bassa.
         

            btnCorrectionEntree = makeButton("<html><center>Correction<br>entrée</center></html>",
                    C_BTN_RED, Color.WHITE);
            btnCorrectionEntree.setBounds(s(1005), s(btnY1), s(130), s(btnH));
            btnCorrectionEntree.setFocusable(false);
            btnCorrectionEntree.addActionListener(e -> openCorrectionEntreeForm());
            p.add(btnCorrectionEntree);

            btnAnnulerTare = makeButton("<html><center>Annuler Tare<br>(F8)</center></html>",
                    new Color(0xDD, 0xDD, 0xDD), Color.BLACK);
            btnAnnulerTare.setBounds(s(850), s(btnY2), s(130), s(btnH));
            btnAnnulerTare.setFocusable(false);
            btnAnnulerTare.addActionListener(e -> {
                if (selectedPaletteButton != null) {
                    applyPaletteButtonStyle(selectedPaletteButton, false);
                    selectedPaletteButton = null;
                }
                setTare(0);
            });
            p.add(btnAnnulerTare);

            btnDestockage = makeButton("Déstockage", C_BTN_ORANGE, Color.WHITE);
            btnDestockage.setBounds(s(1005), s(btnY2), s(130), s(btnH));
            btnDestockage.setFocusable(false);
            btnDestockage.addActionListener(e -> openDestockageForm());
            p.add(btnDestockage);

            btnEnregistrer = new JButton("<html><center>Enregistrer + Etiquette&nbsp;&nbsp;(F2)</center></html>") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(C_BTN_YELLOW);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            btnEnregistrer.setBackground(C_BTN_YELLOW);
            btnEnregistrer.setForeground(Color.BLACK);
            btnEnregistrer.setOpaque(false);
            btnEnregistrer.setContentAreaFilled(false);
            btnEnregistrer.setBorder(new LineBorder(new Color(0xB0, 0x90, 0x00), 1));
            btnEnregistrer.setBorderPainted(true);
            btnEnregistrer.setFont(uiFont(Font.BOLD, 22));
            // V191.05: ENREGISTRER ridotto per lasciare spazio a RESET BAL (F9) a destra.
            btnEnregistrer.setBounds(s(palX1), s(yNet), s(700), s(fieldH));
            btnEnregistrer.addActionListener(e -> onEnregistrer());
            btnEnregistrer.setFocusable(true);
            p.add(btnEnregistrer);

            // V191.05: pulsante RESET BAL (F9) spostato a destra di ENREGISTRER
            // con lo stesso stile blu del riferimento grafico.
            btnReset = makeButton("<html><center>RESET BAL<br>(F9)</center></html>", C_BTN_BLUE, Color.WHITE);
            btnReset.setFont(uiFont(Font.BOLD, 14));
            btnReset.setBounds(s(1005), s(yNet), s(130), s(fieldH));
            btnReset.setFocusable(false);
            btnReset.addActionListener(e -> doReset());
            p.add(btnReset);

            // Colori finali del blocco bilancia/pulsanti come layout approvato.
            btnPal9.setBackground(PALETTE_BTN_DEFAULT_BG);
            btnPal18.setBackground(PALETTE_BTN_DEFAULT_BG);
            btnPal27.setBackground(PALETTE_BTN_DEFAULT_BG);
            btnPal36.setBackground(PALETTE_BTN_DEFAULT_BG);

            btnImpressionBanbury.putClientProperty("forcedBg", new Color(0x80, 0x80, 0x80));
            btnImpressionBanbury.setBackground(new Color(0x80, 0x80, 0x80));
            btnImpressionBanbury.setForeground(Color.WHITE);
            btnImpressionBanbury.setBorder(new LineBorder(new Color(0x55, 0x55, 0x55), 1));

            btnCorrectionEntree.putClientProperty("forcedBg", C_BTN_RED);
            btnCorrectionEntree.setBackground(C_BTN_RED);
            btnCorrectionEntree.setForeground(Color.WHITE);
            btnCorrectionEntree.setBorder(new LineBorder(C_BTN_RED.darker(), 1));

            btnAnnulerTare.putClientProperty("forcedBg", new Color(0xDD, 0xDD, 0xDD));
            btnAnnulerTare.setBackground(new Color(0xDD, 0xDD, 0xDD));
            btnAnnulerTare.setForeground(Color.BLACK);
            btnAnnulerTare.setBorder(new LineBorder(new Color(0xAA, 0xAA, 0xAA), 1));

            btnDestockage.putClientProperty("forcedBg", C_BTN_ORANGE);
            btnDestockage.setBackground(C_BTN_ORANGE);
            btnDestockage.setForeground(Color.WHITE);
            btnDestockage.setBorder(new LineBorder(C_BTN_ORANGE.darker(), 1));

            btnReset.putClientProperty("forcedBg", C_BTN_BLUE);
            btnReset.setBackground(C_BTN_BLUE);
            btnReset.setForeground(Color.WHITE);

            // V194.14: visualizza nel riquadro destro l'operatore selezionato
            // per la bilancia tramite doppio click nella sezione Personnel.
            // Il campo dinamico e sulla stessa riga del pulsante Destockage.
            JLabel lblOperateurBalance = new JLabel("Operateur Balance");
            lblOperateurBalance.setFont(uiFont(Font.BOLD, 18));
            lblOperateurBalance.setHorizontalAlignment(SwingConstants.CENTER);
            lblOperateurBalance.setBounds(s(1185), s(btnY2 - 30), s(430), s(26));
            p.add(lblOperateurBalance);

            tfOperateurBalanceDisplay = new JTextField("");
            tfOperateurBalanceDisplay.setFont(uiFont(Font.BOLD, 18));
            tfOperateurBalanceDisplay.setEditable(false);
            tfOperateurBalanceDisplay.setFocusable(false);
            tfOperateurBalanceDisplay.setBackground(new Color(238, 238, 238));
            tfOperateurBalanceDisplay.putClientProperty("KEEP_FORM_BACKGROUND", Boolean.TRUE);
            tfOperateurBalanceDisplay.setOpaque(true);
            tfOperateurBalanceDisplay.setForeground(Color.BLACK);
            tfOperateurBalanceDisplay.setBorder(new LineBorder(Color.GRAY, 1));
            tfOperateurBalanceDisplay.setMargin(new Insets(0, 6, 0, 6));
            tfOperateurBalanceDisplay.setBounds(s(1185), s(btnY2), s(430), s(fieldH));
            p.add(tfOperateurBalanceDisplay);
            btnReset.setBorder(new LineBorder(C_BTN_BLUE.darker(), 1));

            DocumentListenerHelper dl = this::updatePoidsNet;
            addDocListener(tfPoidsBrut, dl);
            addDocListener(tfTare, dl);

            KeyStroke f8 = KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0);
            p.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(f8, "annulerTare");
            p.getActionMap().put("annulerTare", new AbstractAction() {
                public void actionPerformed(ActionEvent e) { setTare(0); }
            });

            return p;
        }

        JPanel buildBottomBar() {
            JPanel p = new JPanel(new BorderLayout(s(8), s(4)));
            p.setBackground(C_BG);
            p.setBorder(new EmptyBorder(s(4), 0, 0, 0));

            JPanel buttons = new JPanel(new BorderLayout(s(8), 0));
            buttons.setOpaque(false);
            // Pulsante Enregistrer spostato nella zona bilancia/palette.
            // buttons.add(btnReset, BorderLayout.WEST); // rimosso

            lblStatusMessage = new JLabel(" ");
            lblStatusMessage.setFont(uiFont(Font.BOLD, 15));
            lblStatusMessage.setForeground(new Color(0x20, 0x20, 0x20));
            lblStatusMessage.setBorder(new EmptyBorder(s(2), s(4), 0, s(4)));

            p.add(buttons, BorderLayout.CENTER);
            p.add(lblStatusMessage, BorderLayout.SOUTH);

            KeyStroke f9 = KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0);
            p.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(f9, "reset");
            p.getActionMap().put("reset", new AbstractAction() {
                public void actionPerformed(ActionEvent e) { doReset(); }
            });

            KeyStroke f2 = KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0);
            p.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(f2, "enregistrer");
            p.getActionMap().put("enregistrer", new AbstractAction() {
                public void actionPerformed(ActionEvent e) { btnEnregistrer.doClick(); }
            });

            return p;
        }

        private void applyPaletteButtonStyle(AbstractButton button, boolean selected) {
            if (button == null) {
                return;
            }
            button.setSelected(selected);
            button.setForeground(selected ? Color.WHITE : Color.BLACK);
            button.repaint();
            button.revalidate();
        }

        void selectPaletteButton(PaletteToggleButton button, int kg) {
            if (selectedPaletteButton != null && selectedPaletteButton != button) {
                applyPaletteButtonStyle(selectedPaletteButton, false);
            }

            if (selectedPaletteButton == button && button.isSelected()) {
                applyPaletteButtonStyle(button, false);
                selectedPaletteButton = null;
                setTare(0);
                return;
            }

            applyPaletteButtonStyle(button, true);
            selectedPaletteButton = button;
            setTare(kg);
        }

        void handlePaletteSelection(PaletteToggleButton button, int kg) {
            if (button == null) {
                return;
            }

            if (button.isSelected()) {
                if (selectedPaletteButton != null && selectedPaletteButton != button) {
                    applyPaletteButtonStyle(selectedPaletteButton, false);
                }
                selectedPaletteButton = button;
                applyPaletteButtonStyle(button, true);
                setTare(kg);
            } else {
                applyPaletteButtonStyle(button, false);
                if (selectedPaletteButton == button) {
                    selectedPaletteButton = null;
                }
                setTare(0);
            }
        }

        private File getConfigDirectoryForPoste(String poste) {
            if (isPoste2(poste)) {
                return new File("C:\\_tools\\banbury2");
            }
            return new File("C:\\_tools\\banbury1");
        }

        private File getConfigFileForPoste(String poste) {
            return new File(getConfigDirectoryForPoste(poste), CONFIG_FILE_NAME);
        }

        private File getCurrentConfigFile() {
            return getConfigFileForPoste(appConfig.getProperty("posteSelection", "POSTE 1"));
        }

        private void loadAppConfig() {
            loadAppConfig(appConfig.getProperty("posteSelection", "POSTE 1"));
        }

        private void loadAppConfig(String poste) {
            String selectedPoste = isPoste2(poste) ? "POSTE 2" : "POSTE 1";
            appConfig.clear();

            File configFile = getConfigFileForPoste(selectedPoste);
            if (configFile.exists() && configFile.isFile()) {
                try (InputStream in = new FileInputStream(configFile)) {
                    appConfig.load(in);
                } catch (IOException e) {
                    System.err.println("Errore lettura configurazione " + configFile.getAbsolutePath() + ": " + e.getMessage());
                }
            }

            appConfig.setProperty("posteSelection", selectedPoste);
        }

        private void saveAppConfig() {
            File configFile = getCurrentConfigFile();
            File parent = configFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (OutputStream out = new FileOutputStream(configFile)) {
                appConfig.store(out, "BanburyForm configuration");
            } catch (IOException e) {
                System.err.println("Errore salvataggio configurazione " + configFile.getAbsolutePath() + ": " + e.getMessage());
                showStatusMessage("Errore salvataggio configurazione: " + e.getMessage(), Color.RED);
            }
        }

        private String getConfig(String key, String defaultValue) {
            return appConfig.getProperty(key, defaultValue);
        }

        private void putConfig(String key, String value) {
            if (key == null) {
                return;
            }
            appConfig.setProperty(key, value != null ? value : "");
        }

        private void saveSetupConfig(String ip, String port, String poste, Object selectedPrinter) {
            putConfig("scaleIp", ip);
            putConfig("scalePort", port);
            putConfig("posteSelection", poste);
            if (selectedPrinter != null) {
                putConfig("printerName", selectedPrinter.toString());
            }
            saveAppConfig();
        }

        private void showStatusMessage(String message, Color color) {
            if (lblStatusMessage != null) {
                lblStatusMessage.setText(message != null ? message : " ");
                lblStatusMessage.setForeground(color != null ? color : Color.BLACK);
            }
        }

        private String safeText(JTextField tf) {
            return tf != null ? tf.getText().trim() : "";
        }

        private String safeCombo(JComboBox<String> cb) {
            Object value = cb != null ? cb.getSelectedItem() : null;
            return value != null ? value.toString().trim() : "";
        }

        private String stripAccentsForLegacyJasper(String value) {
            if (value == null) {
                return "";
            }

            String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD);
            normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            normalized = normalized.replace('œ', 'o').replace('Œ', 'O');
            normalized = normalized.replace('æ', 'a').replace('Æ', 'A');
            return normalized.replaceAll("[^\\x20-\\x7E\\r\\n\\t]", "").trim();
        }

        private String cleanOptionalDisplayText(String value) {
            if (value == null) {
                return "";
            }

            String v = value.trim();
            if (v.isEmpty() || "NULL".equalsIgnoreCase(v)) {
                return "";
            }
            return v;
        }

        private void putJasperParam(Map<String, Object> params, String key, String value) {
            params.put(key, stripAccentsForLegacyJasper(cleanOptionalDisplayText(value)));
        }

        private String normalizePassageForLegacyJasper(String value) {
            if (value == null) {
                return "";
            }

            return value.trim()
                    .replace("è", "e")
                    .replace("é", "e")
                    .replace("ê", "e")
                    .replace("È", "E")
                    .replace("É", "E")
                    .replace("Ê", "E");
        }

        private String normalizeMelangeForDb(String value) {
            if (value == null) {
                return "";
            }

            String v = value.trim();
            if (v.isEmpty()) {
                return "";
            }

            // La colonna DBM_PESAGE_NEW.MELANGE accetta massimo 2 caratteri.
            // In interfaccia il combo mostra valori leggibili tipo "SERIE A" / "SERIE A1".
            // Nel DB salviamo solo il codice corto: A, A1, B, C, ...
            if (v.toUpperCase().startsWith("SERIE ")) {
                v = v.substring("SERIE ".length()).trim();
            }

            return v.length() > 2 ? v.substring(0, 2) : v;
        }

        private Integer parseIntegerOrNull(String value) {
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private Double parseDoubleOrNull(String value) {
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            try {
                return Double.parseDouble(value.trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private String validateWeightBeforeSaveMessage() {
            Double brut = parseDoubleOrNull(safeText(tfPoidsBrut));
            Double net = parseDoubleOrNull(safeText(tfPoidsNet));

            if (brut == null || brut.doubleValue() <= 0.0 || net == null || net.doubleValue() <= 0.0) {
                return "Erreur pesée : le poids est NULL ou égal à 0.\n" +
                        "Veuillez refaire la pesée avant d'enregistrer.\n\n" +
                        "Aucune donnée n'a été enregistrée dans le DB et aucune étiquette n'a été imprimée.";
            }

            return null;
        }

        private void showWeightBeforeSaveError(String message) {
            JOptionPane.showMessageDialog(
                    getDialogParentComponent(),
                    message,
                    "Erreur pesée",
                    JOptionPane.ERROR_MESSAGE
            );
            showStatusMessage("Enregistrement annulé: poids NULL ou égal à 0. Refaire la pesée.", Color.RED);
        }

        private boolean isPoste2(String poste) {
            return poste != null && poste.startsWith("POSTE 2");
        }

        private boolean isPoste3(String poste) {
            return poste != null && poste.startsWith("POSTE 3");
        }

        private int getPosteValue() {
            String poste = getConfig("posteSelection", "POSTE 1");
            if (isPoste3(poste)) {
                return 3;
            }
            return isPoste2(poste) ? 2 : 1;
        }

        private String getTourneeIdForDb() {
            if (pendingSaveTourneeId != null && !pendingSaveTourneeId.trim().isEmpty()) {
                return pendingSaveTourneeId;
            }

            String tournee = getCurrentTourneeCode();
            if ("MATIN".equals(tournee)) return "1";
            if ("APRES-MIDI".equals(tournee)) return "2";
            return "3";
        }

        private String getTourneeCodeForPrint() {
            if (pendingSaveTourneeCode != null && !pendingSaveTourneeCode.trim().isEmpty()) {
                return pendingSaveTourneeCode;
            }
            return safeText(tfTournee);
        }

        private String tourneeCodeFromId(String id) {
            if ("1".equals(id)) return "MATIN";
            if ("2".equals(id)) return "APRES-MIDI";
            return "NUIT";
        }

        private String tourneeDescriptionFromId(String id) {
            if ("1".equals(id)) return "TURNEE 1 MATIN";
            if ("2".equals(id)) return "TURNEE 2 APRES-MIDI";
            return "TURNEE 3 NUIT";
        }

        private String getCurrentShiftBoundaryKey() {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);

            if (hour >= 6 && hour < 14) {
                cal.set(java.util.Calendar.HOUR_OF_DAY, 6);
            } else if (hour >= 14 && hour < 22) {
                cal.set(java.util.Calendar.HOUR_OF_DAY, 14);
            } else if (hour >= 22) {
                cal.set(java.util.Calendar.HOUR_OF_DAY, 22);
            } else {
                cal.add(java.util.Calendar.DATE, -1);
                cal.set(java.util.Calendar.HOUR_OF_DAY, 22);
            }

            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            return new SimpleDateFormat("yyyyMMddHHmm").format(cal.getTime());
        }

        private String getCurrentTourneeIdAutomatic() {
            String tournee = getCurrentTourneeCode();
            if ("MATIN".equals(tournee)) return "1";
            if ("APRES-MIDI".equals(tournee)) return "2";
            return "3";
        }

        private String getPreviousTourneeId(String currentId) {
            if ("1".equals(currentId)) return "3";
            if ("2".equals(currentId)) return "1";
            return "2";
        }

        private TourneeChoice chooseTourneeForSaveAtShiftChange() {
            String boundaryKey = getCurrentShiftBoundaryKey();
            String currentId = getCurrentTourneeIdAutomatic();

            // V193.03: se il turno corrente e gia stato confermato per questa fascia
            // oraria, non chiedere piu fino al prossimo cambio turno.
            if (boundaryKey != null && boundaryKey.equals(lastConfirmedCurrentShiftBoundaryKey)) {
                return new TourneeChoice(currentId, tourneeCodeFromId(currentId), false);
            }

            String previousId = getPreviousTourneeId(currentId);
            Object[] options = {
                    "Equipe précédente - " + tourneeDescriptionFromId(previousId),
                    "Equipe actuelle - " + tourneeDescriptionFromId(currentId)
            };

            int choice = JOptionPane.showOptionDialog(
                    getDialogParentComponent(),
                    "Changement d'équipe.\n\nCette pesée appartient-elle à l'équipe précédente ou à l'équipe actuelle ?",
                    "Choix équipe pesée",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[1]
            );

            if (choice == 0) {
                // Equipe precedente: non confermare il turno attuale, cosi la domanda
                // viene riproposta alla prossima pesata.
                return new TourneeChoice(previousId, tourneeCodeFromId(previousId), true);
            }
            if (choice == 1) {
                lastConfirmedCurrentShiftBoundaryKey = boundaryKey;
                return new TourneeChoice(currentId, tourneeCodeFromId(currentId), true);
            }

            return null;
        }

        private class TourneeChoice {
            final String tourneeId;
            final String tourneeCode;
            final boolean asked;

            TourneeChoice(String tourneeId, String tourneeCode, boolean asked) {
                this.tourneeId = tourneeId;
                this.tourneeCode = tourneeCode;
                this.asked = asked;
            }
        }

        private void setStringOrNull(PreparedStatement ps, int index, String value) throws SQLException {
            if (value == null || value.trim().isEmpty() || "NULL".equalsIgnoreCase(value.trim())) {
                ps.setNull(index, Types.VARCHAR);
            } else {
                ps.setString(index, value.trim());
            }
        }

        private void setStringOrBlank(PreparedStatement ps, int index, String value) throws SQLException {
            String v = cleanOptionalDisplayText(value);
            if (v.isEmpty()) {
                // Oracle salva la stringa vuota come NULL.
                // Per il campo COULEUR/CL_FO usiamo uno spazio: resta visivamente vuoto, ma non diventa NULL.
                ps.setString(index, " ");
            } else {
                ps.setString(index, v);
            }
        }

        private void setDoubleOrNull(PreparedStatement ps, int index, Double value) throws SQLException {
            if (value == null) {
                ps.setNull(index, Types.NUMERIC);
            } else {
                ps.setDouble(index, value);
            }
        }

        private void setIntOrNull(PreparedStatement ps, int index, Integer value) throws SQLException {
            if (value == null) {
                ps.setNull(index, Types.NUMERIC);
            } else {
                ps.setInt(index, value);
            }
        }

        private int getNextIdPesage(Connection connection) throws SQLException {
            String sql = "SELECT NVL(MAX(ID_PESAGE), 0) + 1 FROM DBM_PESAGE_NEW";
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            throw new SQLException("Impossibile determinare il prossimo ID_PESAGE");
        }

        private void onEnregistrer() {
            // V194.08: se l'operatore ha scritto POIDS BRUT a mano e preme subito
            // ENREGISTRER, confermare prima il valore manuale senza riattivare lo stream.
            // In questo modo il peso non viene sovrascritto dalla bilancia prima del salvataggio.
            if (poidsBrutManualEdit) {
                confirmPoidsBrutManualInput();
            }

            String weightErrorMessage = validateWeightBeforeSaveMessage();
            if (weightErrorMessage != null) {
                showWeightBeforeSaveError(weightErrorMessage);
                resumePoidsBrutStream();
                return;
            }

            TourneeChoice tourneeChoice = chooseTourneeForSaveAtShiftChange();
            if (tourneeChoice == null) {
                showStatusMessage("Enregistrement annulé: choix tournée non confirmé.", Color.RED);
                resumePoidsBrutStream();
                return;
            }

            pendingSaveTourneeId = tourneeChoice.tourneeId;
            pendingSaveTourneeCode = tourneeChoice.tourneeCode;

            btnEnregistrer.setEnabled(false);
            showStatusMessage("Salvataggio in corso...", new Color(0x80, 0x50, 0x00));

            new SwingWorker<SavedDbPesageValues, Void>() {
                private String errorMessage;

                @Override
                protected SavedDbPesageValues doInBackground() {
                    try {
                        return saveCurrentRecordToDatabase();
                    } catch (Exception ex) {
                        errorMessage = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                        return null;
                    }
                }

                @Override
                protected void done() {
                    btnEnregistrer.setEnabled(true);
                    if (errorMessage == null) {
                        try {
                            SavedDbPesageValues dbValues = get();
                            applySavedDbPesageValuesToForm(dbValues);
                            printOnePassWithJasper();
                            incrementPeseeField();
                            showStatusMessage("Scrittura DB completata con successo + stampa etichetta 1 pass con valori DB OK.", new Color(0x00, 0x80, 0x20));
                        } catch (PrinterException ex) {
                            showStatusMessage("DB OK, errore stampa: " + ex.getMessage(), Color.RED);
                        } catch (Exception ex) {
                            showStatusMessage("DB OK, errore rilettura valori DB: " + ex.getMessage(), Color.RED);
                        }
                    } else if (errorMessage.startsWith("INVALID_WEIGHT:")) {
                        showWeightBeforeSaveError(errorMessage.substring("INVALID_WEIGHT:".length()));
                    } else if (errorMessage.startsWith("DUPLICATE_PESEE:")) {
                        String duplicateMessage = errorMessage.substring("DUPLICATE_PESEE:".length());
                        JOptionPane.showMessageDialog(
                                getDialogParentComponent(),
                                duplicateMessage,
                                "Contrôle #Pesée",
                                JOptionPane.WARNING_MESSAGE
                        );
                        showStatusMessage("Enregistrement annulé: #Pesée déjà présente dans le DB.", Color.RED);
                    } else {
                        showStatusMessage("Errore scrittura DB: " + errorMessage, Color.RED);
                    }
                    pendingSaveTourneeId = null;
                    pendingSaveTourneeCode = null;
                    resumePoidsBrutStream();
                }
            }.execute();
        }

        private SavedDbPesageValues saveCurrentRecordToDatabase() throws Exception {
            String operateurId = safeText(tfOperateurCode);
            String produitId = safeText(tfProduitCode);
            String lot = safeText(tfNLot);
            String provenanceId = normalizeBanburyCode(safeCombo(cbProvenance));
            String destinationId = normalizeBanburyCode(safeCombo(cbDestination));
            Double brut = parseDoubleOrNull(safeText(tfPoidsBrut));
            Double net = parseDoubleOrNull(safeText(tfPoidsNet));
            Double tare = parseDoubleOrNull(safeText(tfTare));
            Integer noPesee = parseIntegerOrNull(safeText(tfPesees));
            String commentaire = safeText(tfCommentaires);
            String remarque = safeCombo(cbRemarque);
            Integer posteP = getPosteValue();
            String tourneeId = getTourneeIdForDb();
            String couche = safeCombo(cbCouche);
            String type = safeCombo(cbType);
            String date2 = safeText(tfDate);
            Integer nbrMelange = parseIntegerOrNull(safeText(tfNbrMelange));
            String suffixe = safeCombo(cbSuffixe);
            String passage = normalizePassageForLegacyJasper(safeCombo(cbPassage));
            String melange = normalizeMelangeForDb(safeCombo(cbNMelange));
            String clFo = cleanOptionalDisplayText(safeCombo(cbClairFonce));

            String weightErrorMessage = validateWeightBeforeSaveMessage();
            if (weightErrorMessage != null) {
                throw new IllegalArgumentException("INVALID_WEIGHT:" + weightErrorMessage);
            }

            if (operateurId.isEmpty()) throw new IllegalArgumentException("Codice operatore mancante");
            if (produitId.isEmpty()) throw new IllegalArgumentException("Codice prodotto mancante");
            if (lot.isEmpty() || lot.equals(defaultLotPrefix())) throw new IllegalArgumentException("N° LOT mancante");
            if (noPesee == null) throw new IllegalArgumentException("#Pesées non valido");

            String sql =
                    "INSERT INTO DBM_PESAGE_NEW (" +
                    "ID_PESAGE, OPERATEUR_ID, PRODUIT_ID, LOT_P, PROVENANCE_ID, DESTINATION_ID, " +
                    "BRUT_P, NET_P, TARE_P, NO_PESEE_P, COMMENTAIRE, REMARQUE, POSTE_P, TOURNEE_ID, " +
                    "COUCHE, TYPE, DATE2, NBRMELANGE, SUFFIXE, PASSAGE, MELANGE, CL_FO" +
                    ") VALUES (" +
                    "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?" +
                    ")";

            try (Connection connection = DbOracleManager.getConnection();
                 PreparedStatement ps = connection.prepareStatement(sql)) {

                if (peseeAlreadyExists(connection, produitId, lot, noPesee)) {
                    throw new IllegalStateException(
                            "DUPLICATE_PESEE:Attention : il existe déjà dans la base de données une ligne avec le même numéro de pesée pour le produit et le lot actuels.\n\n" +
                            "N° Produit : " + produitId + "\n" +
                            "N° Lot : " + lot + "\n" +
                            "#Pesée : " + noPesee + "\n\n" +
                            "Veuillez contrôler dans le DB avant de continuer."
                    );
                }

                int idPesage = getNextIdPesage(connection);

                ps.setInt(1, idPesage);
                setStringOrNull(ps, 2, operateurId);
                setStringOrNull(ps, 3, produitId);
                setStringOrNull(ps, 4, lot);
                setStringOrNull(ps, 5, provenanceId);
                setStringOrNull(ps, 6, destinationId);
                setDoubleOrNull(ps, 7, brut);
                setDoubleOrNull(ps, 8, net);
                setDoubleOrNull(ps, 9, tare);
                setIntOrNull(ps, 10, noPesee);
                setStringOrNull(ps, 11, commentaire);
                setStringOrNull(ps, 12, remarque);
                setIntOrNull(ps, 13, posteP);
                setStringOrNull(ps, 14, tourneeId);
                setStringOrNull(ps, 15, couche);
                setStringOrNull(ps, 16, type);
                setStringOrNull(ps, 17, date2);
                setIntOrNull(ps, 18, nbrMelange);
                setStringOrNull(ps, 19, suffixe);
                setStringOrNull(ps, 20, passage);
                setStringOrNull(ps, 21, melange);
                setStringOrBlank(ps, 22, clFo);

                int affected = ps.executeUpdate();
                if (affected != 1) {
                    throw new SQLException("Inserimento non riuscito");
                }

                // V194.21: dopo l'INSERT rilegge subito dal DB i pesi realmente registrati.
                // L'etichetta viene stampata usando questi valori DB, non i valori ancora in memoria nel form.
                return readSavedDbPesageValues(connection, idPesage);
            }
        }

        private class SavedDbPesageValues {
            String brut;
            String net;
            String tare;
        }

        private SavedDbPesageValues readSavedDbPesageValues(Connection connection, int idPesage) throws SQLException {
            String sql = "SELECT BRUT_P, NET_P, TARE_P FROM DBM_PESAGE_NEW WHERE ID_PESAGE = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, idPesage);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Pesée enregistrée mais impossible à relire dans le DB. ID_PESAGE=" + idPesage);
                    }
                    SavedDbPesageValues values = new SavedDbPesageValues();
                    values.brut = formatDbWeightForDisplay(rs.getBigDecimal("BRUT_P"));
                    values.net = formatDbWeightForDisplay(rs.getBigDecimal("NET_P"));
                    values.tare = formatDbWeightForDisplay(rs.getBigDecimal("TARE_P"));
                    return values;
                }
            }
        }

        private String formatDbWeightForDisplay(BigDecimal value) {
            if (value == null) {
                return "";
            }
            BigDecimal normalized = value.stripTrailingZeros();
            if (normalized.scale() < 0) {
                normalized = normalized.setScale(0);
            }
            return normalized.toPlainString();
        }

        private void applySavedDbPesageValuesToForm(SavedDbPesageValues values) {
            if (values == null) {
                return;
            }
            if (tfPoidsBrut != null) {
                tfPoidsBrut.setText(values.brut == null ? "" : values.brut);
            }
            if (tfPoidsNet != null) {
                tfPoidsNet.setText(values.net == null ? "" : values.net);
            }
            if (tfTare != null) {
                tfTare.setText(values.tare == null ? "" : values.tare);
            }
        }





        private boolean peseeAlreadyExists(Connection connection, String produitId, String lot, Integer noPesee) throws SQLException {
            if (produitId == null || produitId.trim().isEmpty() ||
                    lot == null || lot.trim().isEmpty() ||
                    noPesee == null) {
                return false;
            }

            String sql =
                    "SELECT COUNT(*) FROM DBM_PESAGE_NEW " +
                    "WHERE TRIM(PRODUIT_ID) = TRIM(?) " +
                    "AND TRIM(LOT_P) = TRIM(?) " +
                    "AND NO_PESEE_P = ?";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, produitId.trim());
                ps.setString(2, lot.trim());
                ps.setInt(3, noPesee);

                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        }


        private Map<String, Object> buildJasperParams() {
            Map<String, Object> params = new HashMap<>();
            putJasperParam(params, "date", safeText(tfDate));
            putJasperParam(params, "tournee", getTourneeCodeForPrint());
            putJasperParam(params, "operateurCode", safeText(tfOperateurCode));
            putJasperParam(params, "operateurName", safeText(tfOperateurName));
            putJasperParam(params, "produitCode", safeText(tfProduitCode));
            putJasperParam(params, "produitName", sanitizeProduitDescription(safeText(tfProduitName)));
            putJasperParam(params, "lot", safeText(tfNLot));
            putJasperParam(params, "passage", normalizePassageForLegacyJasper(safeCombo(cbPassage)));
            putJasperParam(params, "suffixe", safeCombo(cbSuffixe));
            putJasperParam(params, "couche", safeCombo(cbCouche));
            putJasperParam(params, "type", buildTypeForPrint());
            putJasperParam(params, "pesee", safeText(tfPesees));
            putJasperParam(params, "provenance", !safeText(tfProvName).isEmpty() ? safeText(tfProvName) : safeCombo(cbProvenance));
            putJasperParam(params, "destination", !safeText(tfDestName).isEmpty() ? safeText(tfDestName) : safeCombo(cbDestination));
            putJasperParam(params, "remarque", safeCombo(cbRemarque));
            putJasperParam(params, "commentaire", safeText(tfCommentaires));
            putJasperParam(params, "poidsBrut", safeText(tfPoidsBrut));
            putJasperParam(params, "poidsNet", safeText(tfPoidsNet));
            String couleur = cleanOptionalDisplayText(safeCombo(cbClairFonce));
            putJasperParam(params, "melange", safeCombo(cbNMelange));
            putJasperParam(params, "clairFonce", couleur);
            putJasperParam(params, "couleur", couleur);
            putJasperParam(params, "nbrMelange", safeText(tfNbrMelange));

            String topBarcodeValue = (getTourneeCodeForPrint() + safeText(tfNLot) + safeText(tfProduitCode) + safeText(tfPoidsNet))
                    .replaceAll("[^0-9A-Za-z:]", "");
            String bottomBarcodeValue = (safeText(tfPesees) + ";" + safeText(tfNLot) + ";" + safeText(tfProduitCode) + ";" + safeText(tfPoidsNet))
                    .replaceAll("[^0-9A-Za-z;/]", "");

            params.put("topBarcodeValue", topBarcodeValue.isEmpty() ? "0000000000" : topBarcodeValue);
            params.put("bottomBarcodeValue", bottomBarcodeValue.isEmpty() ? "0000000000" : bottomBarcodeValue);

            return params;
        }


        private String buildTypeForPrint() {
            String type = safeCombo(cbType);
            String clairFonce = cleanOptionalDisplayText(safeCombo(cbClairFonce));

            if (type.isEmpty()) {
                return clairFonce;
            }
            if (clairFonce.isEmpty()) {
                return type;
            }

            // Nel report 1 pass il campo `type` è stampato tra PALETTE/CAISSE e #Pesée.
            // Aggiungiamo qui Clair/Foncé così appare in quella zona anche con il .jasper esistente.
            return type + " - " + clairFonce;
        }

        private String wrapTextForPrint(String value, int maxCharsPerLine, int maxLines) {
            if (value == null) {
                return "";
            }

            String text = value.trim().replaceAll("\\s+", " ");
            if (text.isEmpty() || maxCharsPerLine <= 0 || maxLines <= 1 || text.length() <= maxCharsPerLine) {
                return text;
            }

            StringBuilder result = new StringBuilder();
            StringBuilder line = new StringBuilder();
            int lines = 1;

            for (String word : text.split(" ")) {
                if (line.length() == 0) {
                    line.append(word);
                } else if (line.length() + 1 + word.length() <= maxCharsPerLine) {
                    line.append(' ').append(word);
                } else {
                    if (result.length() > 0) {
                        result.append('\n');
                    }
                    result.append(line);
                    lines++;
                    line.setLength(0);
                    line.append(word);

                    if (lines >= maxLines) {
                        break;
                    }
                }
            }

            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(line);

            String[] split = result.toString().split("\\n", -1);
            if (split.length <= maxLines) {
                return result.toString();
            }

            String last = split[maxLines - 1];
            if (last.length() > maxCharsPerLine - 3) {
                last = last.substring(0, Math.max(0, maxCharsPerLine - 3));
            }
            last = last + "...";

            StringBuilder limited = new StringBuilder();
            for (int i = 0; i < maxLines; i++) {
                if (i > 0) {
                    limited.append('\n');
                }
                limited.append(i == maxLines - 1 ? last : split[i]);
            }
            return limited.toString();
        }

        private void printBanburyHautWithJasper() throws PrinterException {
            // ATTENZIONE: il tasto "Impression Banbury Haut" deve usare SOLO il report 2 pass.
            printJasperReport("C:\\_tools\\banbury2\\Banb_label_2pass.jrxml", "Banb_label_2pass", "Banbury Haut");
        }


        private void clearNbrMelangeAfterBanburyHautPrint() {
            if (tfNbrMelange == null) {
                return;
            }
            tfNbrMelange.setText("");
            tfNbrMelange.repaint();
        }
        private void printWithJasper() throws PrinterException {
            printOnePassWithJasper();
        }

        private void printOnePassWithJasper() throws PrinterException {
            printJasperReport("C:\\_tools\\banbury2\\Banb_label_1pass.jrxml", "Banb_label_1pass", "Etiquette 1 pass");
        }

        private PrintService findPrintServiceByName(String printerName) {
            if (printerName == null || printerName.trim().isEmpty()) {
                return PrintServiceLookup.lookupDefaultPrintService();
            }

            PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
            for (PrintService service : services) {
                if (service != null && service.getName().equalsIgnoreCase(printerName.trim())) {
                    return service;
                }
            }

            return null;
        }

        private void printJasperDirect(JasperPrint print, String displayName) throws PrinterException {
            String printerName = getConfig("printerName", "");
            PrintService printService = findPrintServiceByName(printerName);

            if (printService == null) {
                throw new PrinterException("Stampante non trovata: " + printerName);
            }

            try {
                JRPrintServiceExporter exporter = new JRPrintServiceExporter();
                SimplePrintServiceExporterConfiguration config = new SimplePrintServiceExporterConfiguration();

                config.setPrintService(printService);
                config.setDisplayPrintDialog(false);
                config.setDisplayPageDialog(false);

                exporter.setExporterInput(new SimpleExporterInput(print));
                exporter.setConfiguration(config);
                exporter.exportReport();
            } catch (JRException e) {
                PrinterException pe = new PrinterException("Errore stampa diretta " + displayName + ": " + e.getMessage());
                pe.initCause(e);
                throw pe;
            }
        }

        private void printJasperReport(String windowsReportPath, String jasperPrintName, String displayName) throws PrinterException {
            if (printInProgress) {
                throw new PrinterException("Stampa gia in corso: attendere la fine della stampa precedente");
            }

            printInProgress = true;
            try {
                // Cerca prima il file .jasper pre-compilato (stessa cartella, stessa nome, estensione .jasper)
                String jasperPath = windowsReportPath.replaceAll("(?i)\\.jrxml$", ".jasper");
                File jasperFile = new File(jasperPath);
                JasperReport report;

                if (jasperFile.exists() && jasperFile.isFile()) {
                    // PERCORSO PREFERITO PER IL JAR RUNNABLE:
                    // carica il .jasper pre-compilato. In questo modo non serve il compilatore
                    // Jasper/Janino dentro il jar e si evita l'errore "compiling report expressions class file".
                    System.out.println("Jasper report " + displayName + " caricato da .jasper pre-compilato: " + jasperFile.getAbsolutePath());
                    report = (JasperReport) JRLoader.loadObject(jasperFile);
                } else {
                    // FALLBACK: compila il .jrxml solo se manca il .jasper.
                    File reportFile = new File(windowsReportPath);
                    if (!reportFile.exists() || !reportFile.isFile()) {
                        throw new PrinterException("Report Jasper " + displayName + " non trovato in " + windowsReportPath + " ne' come .jasper ne' come .jrxml");
                    }
                    System.out.println("File .jasper non trovato, compilazione da .jrxml: " + reportFile.getAbsolutePath());
                    File sanitizedReportFile = sanitizeJrxmlForLegacyJasper(reportFile);
                    report = JasperCompileManager.compileReport(sanitizedReportFile.getAbsolutePath());
                }

                JasperPrint print = JasperFillManager.fillReport(report, buildJasperParams(), new JREmptyDataSource(1));
                print.setName(jasperPrintName + "_" + System.currentTimeMillis());

                // Stampa diretta sulla stampante salvata in Setup, senza dialog.
                printJasperDirect(print, displayName);

            } catch (PrinterException e) {
                throw e;
            } catch (Exception e) {
                PrinterException pe = new PrinterException("Errore Jasper " + displayName + ": " + e.getMessage());
                pe.initCause(e);
                throw pe;
            } finally {
                printInProgress = false;
            }
        }


        private File sanitizeJrxmlForLegacyJasper(File reportFile) throws IOException {
            String xml = new String(Files.readAllBytes(reportFile.toPath()), StandardCharsets.UTF_8);

            // Rimuove qualunque attributo uuid="..." aggiunto da Jasper Studio.
            xml = xml.replaceAll("\\s+uuid=\"[^\"]*\"", "");

            // Rimuove anche eventuali attributi jasperReport UUID scritti con apici singoli.
            xml = xml.replaceAll("\\s+uuid='[^']*'", "");

            Path tempFile = Files.createTempFile("banbury_jasper_clean_", ".jrxml");
            Files.write(tempFile, xml.getBytes(StandardCharsets.UTF_8));

            File cleaned = tempFile.toFile();
            cleaned.deleteOnExit();

            System.out.println("JRXML pulito per stampa: " + cleaned.getAbsolutePath());
            return cleaned;
        }

        private void printPreviewLabels() throws PrinterException {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setJobName("Banbury Labels PREVIEW");

            PageFormat pf = job.defaultPage();
            pf.setOrientation(PageFormat.LANDSCAPE);
            pf = job.validatePage(pf);

            job.setPrintable(new Printable() {
                @Override
                public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
                    if (pageIndex > 0) {
                        return NO_SUCH_PAGE;
                    }

                    Graphics2D g2 = (Graphics2D) graphics.create();
                    try {
                        g2.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                        int iw = (int) Math.round(pageFormat.getImageableWidth());
                        int ih = (int) Math.round(pageFormat.getImageableHeight());

                        int gap = 0;
                        int labelWidthLeft = iw / 2;
                        int labelWidthRight = iw - labelWidthLeft;
                        int labelHeight = ih;

                        drawPreviewLabel(g2, 0, 0, labelWidthLeft, labelHeight);
                        drawPreviewLabel(g2, labelWidthLeft + gap, 0, labelWidthRight, labelHeight);

                        g2.setColor(Color.BLACK);
                        g2.setStroke(new BasicStroke(Math.max(1f, iw / 900f)));
                        g2.drawLine(labelWidthLeft, 0, labelWidthLeft, ih);

                        return PAGE_EXISTS;
                    } finally {
                        g2.dispose();
                    }
                }
            }, pf);

            if (!job.printDialog()) {
                throw new PrinterException("Stampa annullata dall'utente");
            }

            job.print();
        }

        private void drawPreviewLabel(Graphics2D g2, int x, int y, int w, int h) {
            Graphics2D gg = (Graphics2D) g2.create();
            try {
                gg.translate(x, y);
                gg.setColor(Color.WHITE);
                gg.fillRect(0, 0, w, h);
                gg.setColor(Color.BLACK);

                int m = Math.max(6, Math.min(w, h) / 70);
                int thin = Math.max(1, Math.min(w, h) / 320);
                int thick = Math.max(2, Math.min(w, h) / 170);

                String operateurCode = safeOrDash(safeText(tfOperateurCode));
                String operateurName = safeOrDash(safeText(tfOperateurName));
                String produitCode = safeOrDash(safeText(tfProduitCode));
                String produitName = safeOrDash(sanitizeProduitDescription(safeText(tfProduitName)));
                String lot = safeOrDash(safeText(tfNLot));
                String passage = safeOrDash(safeCombo(cbPassage));
                String suffixe = safeOrDash(safeCombo(cbSuffixe));
                String couche = safeOrDash(safeCombo(cbCouche));
                String type = safeOrDash(safeCombo(cbType));
                String clairFonce = safeOrDash(safeCombo(cbClairFonce));
                String melange = safeOrDash(safeCombo(cbNMelange));
                String pesee = safeOrDash(safeText(tfPesees));
                String provenance = safeOrDash(tfProvName != null && !safeText(tfProvName).isEmpty() ? safeText(tfProvName) : safeCombo(cbProvenance));
                String destination = safeOrDash(tfDestName != null && !safeText(tfDestName).isEmpty() ? safeText(tfDestName) : safeCombo(cbDestination));
                String remarque = safeOrDash(safeCombo(cbRemarque));
                String commentaire = safeOrDash(safeText(tfCommentaires));
                String brut = safeOrDash(safeText(tfPoidsBrut)) + " Kg";
                String net = safeOrDash(safeText(tfPoidsNet)) + " Kg";
                String date = safeOrDash(safeText(tfDate));

                String topBarcodeValue = compactDigits(compactJoin(getTourneeCodeForPrint(), safeText(tfNLot)) + safeText(tfProduitCode) + safeText(tfPoidsNet));
                String bottomBarcodeValue = compactBarcodeValue(safeText(tfPesees), safeText(tfNLot), safeText(tfProduitCode), safeText(tfPoidsNet));
                if ("-".equals(bottomBarcodeValue)) {
                    bottomBarcodeValue = "0000000000";
                }

                int headerTextSize = Math.max(10, h / 46);
                int valueTextSize = Math.max(13, h / 30);
                int strongTextSize = Math.max(16, h / 24);
                int netTextSize = Math.max(22, h / 18);

                gg.setFont(new Font("SansSerif", Font.BOLD, headerTextSize));
                int titleY = m + headerTextSize;
                gg.drawString("MONDO LUXEMBOURG", m, titleY);

                int topBarcodeW = Math.max(90, w / 4);
                int topBarcodeH = Math.max(10, h / 55);
                int topBarcodeX = (w - topBarcodeW) / 2;
                int topBarcodeY = m + 2;
                drawBarcodeLike(gg, topBarcodeX, topBarcodeY, topBarcodeW, topBarcodeH, topBarcodeValue, false);

                gg.setFont(new Font("SansSerif", Font.PLAIN, Math.max(8, h / 70)));
                drawCenteredString(gg, topBarcodeValue, topBarcodeX, topBarcodeY + topBarcodeH + Math.max(10, h / 55), topBarcodeW);

                gg.setFont(new Font("SansSerif", Font.ITALIC, Math.max(11, h / 34)));
                drawRightAligned(gg, "Date : " + date, w - m, titleY);

                int headerLineY = m + Math.max(22, h / 16);
                gg.setStroke(new BasicStroke(thick));
                gg.drawLine(m, headerLineY, w - m, headerLineY);

                int leftX = m;
                int rightX = (int) (w * 0.57);
                int rightW = w - m - rightX;
                int y0 = headerLineY + Math.max(18, h / 28);
                int line = Math.max(23, h / 18);

                drawMixedLine(gg, leftX, y0, w - 2 * m, valueTextSize, "Opérateur", operateurCode, operateurName, false, false);
                drawMixedLineWrappedSecondValue(gg, leftX, y0 + line, w - 2 * m, strongTextSize, "Produit", produitCode, produitName, true, false, line);
                drawFieldLine(gg, leftX, y0 + line * 2, rightX - leftX - m, strongTextSize, "Lot N°", lot, true);
                drawFieldLine(gg, leftX, y0 + line * 3, rightX - leftX - m, valueTextSize, "Passage", passage, true);
                drawFieldLine(gg, rightX, y0 + line * 3, rightW, valueTextSize, "Suffixe", suffixe, true);

                gg.setFont(new Font("SansSerif", Font.PLAIN, strongTextSize));
                drawFittedString(gg, produitName, leftX, y0 + line * 4 + strongTextSize / 2, w - 2 * m);

                drawFieldLine(gg, leftX, y0 + line * 6, rightX - leftX - m, strongTextSize, "Couche", couche, true);
                gg.setFont(new Font("SansSerif", Font.PLAIN, strongTextSize));
                drawFittedString(gg, type, rightX, y0 + line * 6, rightW / 2);
                if (!"-".equals(clairFonce)) {
                    gg.setFont(new Font("SansSerif", Font.BOLD, valueTextSize));
                    drawFittedString(gg, clairFonce, rightX, y0 + line * 7, rightW / 2);
                }
                gg.setFont(new Font("SansSerif", Font.BOLD, valueTextSize));
                drawRightAligned(gg, "#Pesée : " + pesee, w - m, y0 + line * 6);

                int provY = y0 + line * 8;
                gg.setStroke(new BasicStroke(thin));
                gg.drawLine(m, provY - Math.max(12, h / 60), w - m, provY - Math.max(12, h / 60));
                drawFieldLine(gg, leftX, provY, w - 2 * m, valueTextSize, "Provenance", provenance, true);
                drawFieldLine(gg, leftX, provY + line, w - 2 * m, valueTextSize, "Destination", destination, true);
                gg.drawLine(m, provY + line + Math.max(8, h / 70), w - m, provY + line + Math.max(8, h / 70));

                int remY = provY + line * 2 + Math.max(10, h / 55);
                drawFieldLine(gg, leftX, remY, w - 2 * m, valueTextSize, "Remarque", remarque, false);
                drawFieldLine(gg, leftX, remY + line, w - 2 * m, valueTextSize, "Commentaire", commentaire, false);

                int sepY = h - Math.max(150, h / 3);
                gg.drawLine(m, sepY, w - m, sepY);

                int poidsY = sepY + Math.max(24, h / 18);
                drawFieldLine(gg, leftX, poidsY, w / 3, valueTextSize, "P. brut", brut, false);
                gg.setFont(new Font("SansSerif", Font.BOLD, netTextSize));
                drawRightAligned(gg, "P. net : " + net, w - m, poidsY);

                int barcodeX = m + Math.max(50, w / 7);
                int barcodeW = w - 2 * barcodeX + m;
                barcodeW = Math.max(w / 2, barcodeW);
                int barcodeH = Math.max(72, h / 6);
                int barcodeY = h - barcodeH - Math.max(26, h / 18);
                drawBarcodeLike(gg, barcodeX, barcodeY, barcodeW, barcodeH, bottomBarcodeValue, true);

                // Barcode finale senza testo aggiuntivo sotto le barre.
            } finally {
                gg.dispose();
            }
        }

        private void drawFieldLine(Graphics2D g, int x, int y, int width, int fontSize, String label, String value, boolean boldValue) {
            Font labelFont = new Font("SansSerif", Font.PLAIN, fontSize);
            Font valueFont = new Font("SansSerif", boldValue ? Font.BOLD : Font.PLAIN, fontSize);
            g.setFont(labelFont);
            FontMetrics fm = g.getFontMetrics();
            String prefix = label + " : ";
            g.drawString(prefix, x, y);
            int valueX = x + fm.stringWidth(prefix) + 2;
            g.setFont(valueFont);
            drawFittedString(g, safeOrDash(value), valueX, y, Math.max(10, width - (valueX - x)));
        }

        private void drawMixedLine(Graphics2D g, int x, int y, int width, int fontSize, String label, String firstValue, String secondValue, boolean firstBold, boolean secondBold) {
            Font labelFont = new Font("SansSerif", Font.PLAIN, fontSize);
            g.setFont(labelFont);
            FontMetrics fmLabel = g.getFontMetrics();
            String prefix = label + " : ";
            g.drawString(prefix, x, y);
            int cursor = x + fmLabel.stringWidth(prefix) + 2;

            Font firstFont = new Font("SansSerif", firstBold ? Font.BOLD : Font.PLAIN, fontSize);
            g.setFont(firstFont);
            FontMetrics fmFirst = g.getFontMetrics();
            String first = safeOrDash(firstValue);
            g.drawString(first, cursor, y);
            cursor += fmFirst.stringWidth(first) + Math.max(8, fontSize / 2);

            Font secondFont = new Font("SansSerif", secondBold ? Font.BOLD : Font.PLAIN, fontSize);
            g.setFont(secondFont);
            drawFittedString(g, safeOrDash(secondValue), cursor, y, Math.max(10, width - (cursor - x)));
        }


        private void drawMixedLineWrappedSecondValue(Graphics2D g, int x, int y, int width, int fontSize, String label, String firstValue, String secondValue, boolean firstBold, boolean secondBold, int lineHeight) {
            Font labelFont = new Font("SansSerif", Font.PLAIN, fontSize);
            g.setFont(labelFont);
            FontMetrics fmLabel = g.getFontMetrics();
            String prefix = label + " : ";
            g.drawString(prefix, x, y);
            int cursor = x + fmLabel.stringWidth(prefix) + 2;

            Font firstFont = new Font("SansSerif", firstBold ? Font.BOLD : Font.PLAIN, fontSize);
            g.setFont(firstFont);
            FontMetrics fmFirst = g.getFontMetrics();
            String first = safeOrDash(firstValue);
            g.drawString(first, cursor, y);
            cursor += fmFirst.stringWidth(first) + Math.max(8, fontSize / 2);

            Font secondFont = new Font("SansSerif", secondBold ? Font.BOLD : Font.PLAIN, fontSize);
            g.setFont(secondFont);
            drawFittedStringTwoLines(g, safeOrDash(secondValue), cursor, y, Math.max(10, width - (cursor - x)), lineHeight);
        }

        private void drawFittedStringTwoLines(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
            if (text == null || text.trim().isEmpty()) {
                g.drawString("-", x, y);
                return;
            }

            FontMetrics fm = g.getFontMetrics();
            String normalized = text.trim().replaceAll("\\s+", " ");
            if (fm.stringWidth(normalized) <= maxWidth) {
                g.drawString(normalized, x, y);
                return;
            }

            String[] words = normalized.split(" ");
            StringBuilder first = new StringBuilder();
            StringBuilder second = new StringBuilder();
            boolean onSecond = false;

            for (String word : words) {
                StringBuilder target = onSecond ? second : first;
                String candidate = target.length() == 0 ? word : target + " " + word;
                if (fm.stringWidth(candidate) <= maxWidth) {
                    if (target.length() > 0) {
                        target.append(' ');
                    }
                    target.append(word);
                } else if (!onSecond) {
                    onSecond = true;
                    second.append(word);
                } else {
                    String ellipsis = "...";
                    while (second.length() > 0 && fm.stringWidth(second + ellipsis) > maxWidth) {
                        second.setLength(second.length() - 1);
                    }
                    second.append(ellipsis);
                    break;
                }
            }

            g.drawString(first.length() > 0 ? first.toString() : normalized, x, y);
            if (second.length() > 0) {
                g.drawString(second.toString(), x, y + Math.max(lineHeight / 2, fm.getHeight()));
            }
        }

        private String safeOrDash(String value) {
            if (value == null) return "-";
            String v = value.trim();
            return v.isEmpty() ? "-" : v;
        }

        private String compactJoin(String left, String right) {
            String a = safeOrDash(left);
            String b = safeOrDash(right);
            if ("-".equals(a)) return b;
            if ("-".equals(b)) return a;
            return a + " " + b;
        }

        private String compactDigits(String value) {
            if (value == null) return "-";
            String s = value.replaceAll("[^0-9A-Za-z;/]", "");
            return s.isEmpty() ? "-" : s;
        }

        private String compactBarcodeValue(String pesee, String lot, String produit, String poidsNet) {
            return compactDigits(
                    safeTextValue(pesee) + ";" +
                    safeTextValue(lot) + ";" +
                    safeTextValue(produit) + ";" +
                    safeTextValue(poidsNet)
            );
        }

        private String safeTextValue(String value) {
            return value == null ? "" : value.trim();
        }

        private void drawBarcodeLike(Graphics2D g, int x, int y, int width, int height, String value, boolean fillOnly) {
            String src = compactDigits(value);
            if ("-".equals(src)) {
                src = "0000000000";
            }
            int cursor = x;
            g.setColor(Color.BLACK);
            for (int i = 0; i < src.length() && cursor < x + width - 2; i++) {
                int code = src.charAt(i);
                int bars = 7;
                for (int b = 0; b < bars && cursor < x + width - 1; b++) {
                    int bit = (code >> b) & 1;
                    int barW = bit == 1 ? 2 : 1;
                    g.fillRect(cursor, y, barW, height);
                    cursor += barW + 1;
                }
                cursor += 1;
            }
            while (cursor < x + width - 1) {
                g.fillRect(cursor, y, 1, height);
                cursor += 3;
            }
            if (!fillOnly) {
                g.drawRect(x, y, width, height);
            }
        }

        private void drawCenteredString(Graphics2D g, String text, int x, int y, int width) {
            FontMetrics fm = g.getFontMetrics();
            int tx = x + Math.max(0, (width - fm.stringWidth(text)) / 2);
            g.drawString(text, tx, y);
        }

        private void drawRightAligned(Graphics2D g, String text, int rightX, int baselineY) {
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, rightX - fm.stringWidth(text), baselineY);
        }

        private void drawFittedString(Graphics2D g, String text, int x, int y, int maxWidth) {
            if (text == null) text = "-";
            FontMetrics fm = g.getFontMetrics();

            if (fm.stringWidth(text) <= maxWidth) {
                g.drawString(text, x, y);
                return;
            }

            String ellipsis = "...";
            int ellipsisWidth = fm.stringWidth(ellipsis);
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < text.length(); i++) {
                String candidate = sb.toString() + text.charAt(i);
                if (fm.stringWidth(candidate) + ellipsisWidth > maxWidth) {
                    break;
                }
                sb.append(text.charAt(i));
            }

            g.drawString(sb + ellipsis, x, y);
        }

        private void enablePoidsBrutManualEdit() {
            if (tfPoidsBrut == null) {
                return;
            }
            poidsBrutBeforeManualEdit = tfPoidsBrut.getText() == null ? "" : tfPoidsBrut.getText().trim();
            if (poidsBrutBeforeManualEdit.isEmpty()) poidsBrutBeforeManualEdit = lastAutomaticPoidsBrut == null ? "0" : lastAutomaticPoidsBrut;
            poidsBrutManualEdit = true;
            // V194.17: ne pas appeler balanceService.enterManualMode().
            // Le stream reste actif; on bloque uniquement la mise a jour du champ
            // tant que l'utilisateur ecrit le poids manuel.
            tfPoidsBrut.setEditable(true);
            tfPoidsBrut.setFocusable(true);
            tfPoidsBrut.setBackground(new Color(0xFF, 0xFF, 0xCC));
            tfPoidsBrut.setBorder(new LineBorder(Color.YELLOW.darker(), 2));
            tfPoidsBrut.requestFocusInWindow();
            tfPoidsBrut.selectAll();
            showStatusMessage("P. BRUT manuel: ecrire le poids. Le stream balance reste actif en arriere-plan.", new Color(0x80, 0x50, 0x00));
        }

        private void confirmPoidsBrutManualInput() {
            if (tfPoidsBrut == null) {
                return;
            }
            String value = tfPoidsBrut.getText() == null ? "" : tfPoidsBrut.getText().trim();
            if (value.isEmpty()) {
                tfPoidsBrut.setText("0");
            }
            tfPoidsBrut.setEditable(false);
            tfPoidsBrut.setBackground(Color.WHITE);
            tfPoidsBrut.setBorder(poidsBrutDefaultBorder != null ? poidsBrutDefaultBorder : new LineBorder(Color.DARK_GRAY, 1));
            updatePoidsNet();
            showStatusMessage("P. BRUT manuel confirmé. Appuyer sur Enregistrer pour sauver ce poids.", new Color(0x80, 0x50, 0x00));
            if (btnEnregistrer != null) {
                btnEnregistrer.requestFocusInWindow();
            } else {
                tfPoidsBrut.transferFocus();
            }
        }

        private void disablePoidsBrutManualEdit() {
            if (tfPoidsBrut == null) {
                return;
            }
            // Il campo non resta editabile quando perde il focus,
            // ma il valore manuale resta protetto fino a Enregistrer ou ESC.
            // V194.17: le stream balance reste actif en arriere-plan.
            tfPoidsBrut.setEditable(false);
            tfPoidsBrut.setBackground(Color.WHITE);
            tfPoidsBrut.setBorder(poidsBrutDefaultBorder != null ? poidsBrutDefaultBorder : new LineBorder(Color.DARK_GRAY, 1));
            updatePoidsNet();
        }

        private void resumePoidsBrutStream() {
            boolean wasManual = poidsBrutManualEdit;
            poidsBrutManualEdit = false;
            if (tfPoidsBrut != null) {
                tfPoidsBrut.setEditable(false);
                tfPoidsBrut.setBackground(Color.WHITE);
                tfPoidsBrut.setBorder(poidsBrutDefaultBorder != null ? poidsBrutDefaultBorder : new LineBorder(Color.DARK_GRAY, 1));
                // V194.17: dopo Enregistrer il campo deve tornare a mostrare subito
                // il valore automatico gia letto dallo stream, senza chiudere/riaprire il socket.
                String autoValue = lastAutomaticPoidsBrut == null || lastAutomaticPoidsBrut.trim().isEmpty()
                        ? "0"
                        : lastAutomaticPoidsBrut.trim();
                tfPoidsBrut.setText(autoValue);
            }
            updatePoidsNet();
            if (wasManual) {
                showStatusMessage("P. BRUT: retour au poids automatique sans reset connexion.", new Color(0x00, 0x80, 0x20));
            }
        }

        private void resumePoidsBrutStreamFromEsc() {
            boolean wasManual = poidsBrutManualEdit;
            poidsBrutManualEdit = false;
            if (tfPoidsBrut != null) {
                // V194.17: ESC annulla il valore manuale e torna subito al valore
                // automatico letto dallo stream, senza bloccare o resettare la connessione.
                String restoreValue = lastAutomaticPoidsBrut == null || lastAutomaticPoidsBrut.trim().isEmpty()
                        ? "0"
                        : lastAutomaticPoidsBrut.trim();
                tfPoidsBrut.setText(restoreValue);
                tfPoidsBrut.setEditable(false);
                tfPoidsBrut.setBackground(Color.WHITE);
                tfPoidsBrut.setBorder(poidsBrutDefaultBorder != null ? poidsBrutDefaultBorder : new LineBorder(Color.DARK_GRAY, 1));
                tfPoidsBrut.transferFocus();
            }
            updatePoidsNet();
            if (wasManual) {
                showStatusMessage("P. BRUT manuel annule: retour au stream automatique.", new Color(0x00, 0x80, 0x20));
            } else {
                showStatusMessage("P. BRUT: modifica manuale annullata.", new Color(0x00, 0x80, 0x20));
            }
        }

        private void openCorrectionEntreeForm() {
            CorrectionEntreeFrame frame = new CorrectionEntreeFrame(this);
            frame.setVisible(true);
        }

        private void openDestockageForm() {
            Destocker frame = new Destocker(this);
            frame.setVisible(true);
        }

        void setTare(int kg) {
            tfTare.setText(String.valueOf(kg));
            updatePoidsNet();
        }

        void updatePoidsNet() {
            try {
                int brut = Integer.parseInt(tfPoidsBrut.getText().trim());
                int tare = Integer.parseInt(tfTare.getText().trim());
                tfPoidsNet.setText(String.valueOf(brut - tare));
            } catch (NumberFormatException e) {
                tfPoidsNet.setText("0");
            }
        }

        private String defaultLotPrefix() {
            return new SimpleDateFormat("yy").format(new Date()) + "/";
        }

        private void ensureLotPrefix() {
            if (tfNLot == null) {
                return;
            }

            String txt = tfNLot.getText();
            String prefix = defaultLotPrefix();

            if (txt == null || txt.trim().isEmpty()) {
                tfNLot.setText(prefix);
                SwingUtilities.invokeLater(() -> tfNLot.setCaretPosition(Math.min(prefix.length(), tfNLot.getText() == null ? 0 : tfNLot.getText().length())));
            }
        }

        void doReset() {
            // Reset completo del foglio: riporta tutti i campi ai valori iniziali/default.
            resetPoidsBrutManualState();

            if (tfOperateurCode != null) tfOperateurCode.setText("");
            if (tfOperateurName != null) tfOperateurName.setText("");
            if (tfOperateurBalanceDisplay != null) tfOperateurBalanceDisplay.setText("");
            resetOperateurCodeBackground();

            if (tfProduitCode != null) tfProduitCode.setText("");
            if (tfProduitName != null) tfProduitName.setText("");
            resetProduitCodeBackground();

            if (tfNLot != null) {
                tfNLot.setText(defaultLotPrefix());
                SwingUtilities.invokeLater(() -> tfNLot.setCaretPosition(tfNLot.getText() == null ? 0 : tfNLot.getText().length()));
            }

            if (tfNbrMelange != null) tfNbrMelange.setText("");
            if (tfPesees != null) tfPesees.setText("1");
            if (tfCommentaires != null) tfCommentaires.setText("");

            resetCombo(cbCouche);
            resetCombo(cbType);
            resetCombo(cbSuffixe);
            resetCombo(cbPassage);
            resetCombo(cbNMelange);
            resetCombo(cbClairFonce);
            resetCombo(cbRemarque);

            if (selectedPaletteButton != null) {
                applyPaletteButtonStyle(selectedPaletteButton, false);
                selectedPaletteButton = null;
            }

            if (tfPoidsBrut != null) tfPoidsBrut.setText("0");
            if (tfTare != null) tfTare.setText("0");
            updatePoidsNet();

            updateTourneeLabel();
            applyDefaultProvenanceDestination();
            showStatusMessage("Foglio reinizializzato.", new Color(0x00, 0x80, 0x20));

            SwingUtilities.invokeLater(() -> {
                if (tfOperateurCode != null) {
                    tfOperateurCode.requestFocusInWindow();
                }
            });
        }

        private void resetCombo(JComboBox<String> combo) {
            if (combo != null && combo.getItemCount() > 0) {
                combo.setSelectedIndex(0);
            }
        }

        private void resetPoidsBrutManualState() {
            poidsBrutManualEdit = false;
            if (tfPoidsBrut != null) {
                tfPoidsBrut.setEditable(false);
                tfPoidsBrut.setFocusable(true);
                tfPoidsBrut.setBackground(Color.WHITE);
                tfPoidsBrut.setBorder(poidsBrutDefaultBorder != null ? poidsBrutDefaultBorder : new LineBorder(Color.DARK_GRAY, 1));
            }
        }

        void startClock() {
            clockTimer = new Timer(1000, e -> {
                if (tfDate != null) {
                    tfDate.setText(new SimpleDateFormat("dd/MM/yy HH:mm").format(new Date()));
                }
                updateTourneeLabel();
            });
            clockTimer.setRepeats(true);
            clockTimer.start();
            if (tfDate != null) {
                tfDate.setText(new SimpleDateFormat("dd/MM/yy HH:mm").format(new Date()));
            }
            updateTourneeLabel();
        }


        private void updateTourneeLabel() {
            String code = getCurrentTourneeCode();
            String desc = getCurrentTourneeDescription();

            if (tfTournee != null) {
                tfTournee.setText(code);
            }
            if (lblTourneeDesc != null) {
                lblTourneeDesc.setText(desc);
            }
        }

        private String getCurrentTourneeCode() {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);

            if (hour >= 6 && hour < 14) return "MATIN";
            if (hour >= 14 && hour < 22) return "APRES-MIDI";
            return "NUIT";
        }

        private String getCurrentTourneeDescription() {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);

            if (hour >= 6 && hour < 14) return "TURNEE 1 MATIN";
            if (hour >= 14 && hour < 22) return "TURNEE 2 APRES-MIDI";
            return "TURNEE 3 NUIT";
        }



        private void refreshPeseeFromDatabase() {
            if (tfPesees == null) {
                return;
            }

            String produit = tfProduitCode != null ? tfProduitCode.getText().trim() : "";
            String lot = tfNLot != null ? tfNLot.getText().trim() : "";
            String prefix = defaultLotPrefix();

            if (produit.isEmpty() || lot.isEmpty() || lot.equals(prefix)) {
                return;
            }

            try {
                int current = DbOracleManager.findCurrentPeseeNumber(produit, lot);
                tfPesees.setText(String.valueOf(current > 0 ? current + 1 : 1));
            } catch (Exception e) {
                tfPesees.setText("1");
                System.err.println("Errore ricerca #Pesée: " + e.getMessage());
            }
        }

        private void incrementPeseeField() {
            if (tfPesees == null) {
                return;
            }
            try {
                int current = Integer.parseInt(tfPesees.getText().trim());
                tfPesees.setText(String.valueOf(Math.max(1, current) + 1));
            } catch (NumberFormatException e) {
                tfPesees.setText("2");
            }
        }

        private void updateDestinationText() {
            if (cbDestination == null || tfDestName == null) {
                return;
            }

            String val = (String) cbDestination.getSelectedItem();
            if (val == null) {
                tfDestName.setText("");
                return;
            }

            switch (val) {
                case "CBANB1":
                case "CBAN1": tfDestName.setText("BANBURY 1"); break;
                case "CBANB2":
                case "CBAN2": tfDestName.setText("BANBURY 2"); break;
                case "CDECHET": tfDestName.setText("DECHET CAOUTCHOUC"); break;
                case "CL100": tfDestName.setText("LIGNE DE PRODUCTION 100"); break;
                case "CL200": tfDestName.setText("LIGNE DE PRODUCTION 200"); break;
                case "CL201": tfDestName.setText("LIGNE DE PRODUCTION 201"); break;
                case "CL202": tfDestName.setText("LIGNE DE PRODUCTION 202"); break;
                case "CL300": tfDestName.setText("LIGNE DE PRODUCTION 300"); break;
                case "CLABO": tfDestName.setText("LABORATOIRE CAOU"); break;
                case "CS212": tfDestName.setText("GRANULATEUR S212"); break;
                case "CSTOCKBANB": tfDestName.setText("STOCK BANBURY"); break;
                default: tfDestName.setText(""); break;
            }
        }

        private void loadOperateurName() {
            String codice = tfOperateurCode != null ? tfOperateurCode.getText().trim() : "";
            if (tfOperateurName == null || tfOperateurCode == null) {
                return;
            }

            if (codice.isEmpty()) {
                tfOperateurName.setText("");
                resetOperateurCodeBackground();
                tfOperateurName.repaint();
                return;
            }

            try {
                String nome = DbOracleManager.findUserNameByCode(codice);
                tfOperateurName.setText(nome);
                if (nome == null || nome.trim().isEmpty()) {
                    blinkOperateurCodeRed();
                } else {
                    resetOperateurCodeBackground();
                }
                tfOperateurName.repaint();
            } catch (Exception e) {
                tfOperateurName.setText("");
                blinkOperateurCodeRed();
                tfOperateurName.repaint();
                System.err.println("Errore ricerca operatore: " + e.getMessage());
            }
        }

        private void resetOperateurCodeBackground() {
            if (tfOperateurCode != null) {
                tfOperateurCode.setBackground(operateurCodeDefaultBg != null ? operateurCodeDefaultBg : C_FIELD_BG);
                tfOperateurCode.repaint();
            }
        }

        private void blinkOperateurCodeRed() {
            if (tfOperateurCode == null) {
                return;
            }

            final Color baseColor = operateurCodeDefaultBg != null ? operateurCodeDefaultBg : C_FIELD_BG;
            final Color blinkColor = new Color(0xFF, 0x66, 0x66);

            Timer blinkTimer = new Timer(180, null);
            blinkTimer.addActionListener(new ActionListener() {
                private int step = 0;

                @Override
                public void actionPerformed(ActionEvent e) {
                    if (tfOperateurCode == null) {
                        blinkTimer.stop();
                        return;
                    }

                    if (step % 2 == 0) {
                        tfOperateurCode.setBackground(blinkColor);
                    } else {
                        tfOperateurCode.setBackground(baseColor);
                    }

                    tfOperateurCode.repaint();
                    step++;

                    if (step >= 4) {
                        tfOperateurCode.setBackground(baseColor);
                        tfOperateurCode.repaint();
                        blinkTimer.stop();
                    }
                }
            });
            blinkTimer.setInitialDelay(0);
            blinkTimer.start();
        }


        private String sanitizeProduitDescription(String value) {
            if (value == null) {
                return "";
            }

            // Non visualizzare e non stampare la parola MESCOLA nella descrizione prodotto.
            // (?iu) = case-insensitive + Unicode, cosi funziona anche con lettere accentate.
            return value
                    .replaceAll("(?iu)\\bMESCOLA\\b", "")
                    .replaceAll("\\s{2,}", " ")
                    .trim();
        }

        private void loadProduitName() {
            String codice = tfProduitCode != null ? tfProduitCode.getText().trim() : "";
            if (tfProduitName == null || tfProduitCode == null) {
                return;
            }

            if (codice.isEmpty()) {
                tfProduitName.setText("");
                resetProduitCodeBackground();
                tfProduitName.repaint();
                return;
            }

            try {
                String desc = sanitizeProduitDescription(DbOracleManager.findPartDescriptionByCode(codice));
                tfProduitName.setText(desc);
                if (desc == null || desc.trim().isEmpty()) {
                    blinkProduitCodeRed();
                } else {
                    resetProduitCodeBackground();
                }
                tfProduitName.repaint();
            } catch (Exception e) {
                tfProduitName.setText("");
                blinkProduitCodeRed();
                tfProduitName.repaint();
                System.err.println("Errore ricerca prodotto: " + e.getMessage());
            }
        }

        private void resetProduitCodeBackground() {
            if (tfProduitCode != null) {
                tfProduitCode.setBackground(produitCodeDefaultBg != null ? produitCodeDefaultBg : C_FIELD_BG);
                tfProduitCode.repaint();
            }
        }

        private void blinkProduitCodeRed() {
            if (tfProduitCode == null) {
                return;
            }

            final Color baseColor = produitCodeDefaultBg != null ? produitCodeDefaultBg : C_FIELD_BG;
            final Color blinkColor = new Color(0xFF, 0x66, 0x66);

            Timer blinkTimer = new Timer(180, null);
            blinkTimer.addActionListener(new ActionListener() {
                private int step = 0;

                @Override
                public void actionPerformed(ActionEvent e) {
                    if (tfProduitCode == null) {
                        blinkTimer.stop();
                        return;
                    }

                    if (step % 2 == 0) {
                        tfProduitCode.setBackground(blinkColor);
                    } else {
                        tfProduitCode.setBackground(baseColor);
                    }

                    tfProduitCode.repaint();
                    step++;

                    if (step >= 4) {
                        tfProduitCode.setBackground(baseColor);
                        tfProduitCode.repaint();
                        blinkTimer.stop();
                    }
                }
            });
            blinkTimer.setInitialDelay(0);
            blinkTimer.start();
        }

        private String getStartupPosteSelection() {
            if (startupBanbury == 2) return "POSTE 2";
            if (startupBanbury == 1) return "POSTE 1";
            return getConfig("posteSelection", "POSTE 1");
        }

        void applyStartupBanburySelection() {
            if (startupBanbury != 1 && startupBanbury != 2) {
                return;
            }
            String poste = getStartupPosteSelection();
            loadAppConfig(poste);
            appConfig.setProperty("posteSelection", poste);
            refreshPosteLabel();
            applyDefaultProvenanceDestination();
        }

        // V194.27: come applyStartupBanburySelection(), ma senza toccare la
        // Destination. Va usato quando si RIAPRE il Setup Bilancia a sessione
        // avviata: il campo Destination puo' gia' contenere una scelta manuale
        // dell'operatore per la pesata in corso e non deve essere azzerato solo
        // perche' si apre il dialogo di setup.
        void refreshProvenanceForSetupEntry() {
            if (startupBanbury != 1 && startupBanbury != 2) {
                return;
            }
            String poste = getStartupPosteSelection();
            loadAppConfig(poste);
            appConfig.setProperty("posteSelection", poste);
            refreshPosteLabel();
            applyDefaultProvenance();
        }

        private String getDefaultBanburyCode() {
            if (startupBanbury == 2) return "CBANB2";
            if (startupBanbury == 1) return "CBANB1";
            String poste = getConfig("posteSelection", "POSTE 1");
            if (isPoste2(poste)) {
                return "CBANB2";
            }
            // POSTE 3 Balance non ha un codice Banbury dedicato nelle liste Provenance/Destination.
            // Manteniamo CBANB1 come default applicativo, mentre POSTE_P viene salvato a 3.
            return "CBANB1";
        }

        private String normalizeBanburyCode(String code) {
            if (code == null) return "";
            if ("CBANB1".equals(code) || "CBAN1".equals(code)) return "CBANB1";
            if ("CBANB2".equals(code) || "CBAN2".equals(code)) return "CBANB2";
            return code;
        }

        private void applyDefaultProvenanceDestination() {
            applyDefaultProvenance();

            String defaultCode = normalizeBanburyCode(getDefaultBanburyCode());

            if (cbDestination != null) {
                cbDestination.setSelectedItem(defaultCode);
            }

            if (tfDestName != null) {
                tfDestName.setText(mapBanburyName(defaultCode));
            }

            updateDestinationText();
        }

        // V194.25: variante che aggiorna solo la Provenance (che deve seguire il
        // Banbury/poste selezionato), senza toccare la Destination. Da usare nel
        // Setup Bilancia, dove cambiare IP/porta/stampante o anche solo chiudere
        // il dialogo non deve azzerare la Destination scelta manualmente
        // dall'operatore per la pesata in corso.
        private void applyDefaultProvenance() {
            String defaultCode = normalizeBanburyCode(getDefaultBanburyCode());

            if (cbProvenance != null) {
                cbProvenance.setSelectedItem(defaultCode);
                if (startupBanbury == 1 || startupBanbury == 2) {
                    cbProvenance.setEnabled(false);
                    cbProvenance.setFocusable(false);
                    cbProvenance.setToolTipText("Provenance bloquée par le choix Banbury au démarrage: " + defaultCode);
                } else {
                    cbProvenance.setEnabled(true);
                    cbProvenance.setFocusable(true);
                }
            }

            if (tfProvName != null) {
                tfProvName.setText(mapBanburyName(defaultCode));
            }
        }

        private String getSelectedPosteLabel() {
            String poste = getConfig("posteSelection", "POSTE 1");
            if (isPoste2(poste)) {
                return "POSTE   2      Banbury 2";
            }
            return "POSTE   1      Banbury 1";
        }

        private void refreshPosteLabel() {
            if (lblPoste != null) {
                lblPoste.setText(getSelectedPosteLabel());
            }
        }


        private String getDefaultIpForPoste(String poste) {
            if (isPoste2(poste)) return "10.0.0.96";
            return "10.0.0.95";
        }

        private String getDefaultPortForPoste(String poste) {
            return "9761";
        }

        private String getConfiguredScaleIpForCurrentPoste() {
            String poste = getStartupPosteSelection();
            loadAppConfig(poste);
            appConfig.setProperty("posteSelection", poste);
            return getConfig("scaleIp", getDefaultIpForPoste(poste)).trim();
        }

        private int getConfiguredScalePortForCurrentPoste() {
            String poste = getStartupPosteSelection();
            loadAppConfig(poste);
            appConfig.setProperty("posteSelection", poste);
            String portText = getConfig("scalePort", getDefaultPortForPoste(poste)).trim();
            try {
                return Integer.parseInt(portText);
            } catch (Exception ex) {
                return -1;
            }
        }

        void openSetupDialog() {
            JDialog dlg = new JDialog(this, "Setup Bilancia", true);
            dlg.setLayout(new BorderLayout(s(8), s(8)));
            dlg.getRootPane().setBorder(new EmptyBorder(s(10), s(10), s(10), s(10)));

            JPanel form = new JPanel(new GridBagLayout());
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(s(3), s(5), s(3), s(5));
            g.anchor = GridBagConstraints.WEST;
            g.fill = GridBagConstraints.HORIZONTAL;

            g.gridx = 0; g.gridy = 0;
            form.add(new JLabel("IP Bilancia:"), g);
            String savedPosteForSetup = getStartupPosteSelection();
            loadAppConfig(savedPosteForSetup);
            JTextField tfIp = new JTextField(getConfig("scaleIp", getDefaultIpForPoste(savedPosteForSetup)), 16);
            tfIp.setFont(uiFont(Font.BOLD, 16));
            g.gridx = 1;
            form.add(tfIp, g);

            g.gridx = 0; g.gridy = 1;
            form.add(new JLabel("Porta TCP:"), g);
            JTextField tfPort = new JTextField(getConfig("scalePort", getDefaultPortForPoste(savedPosteForSetup)), 8);
            tfPort.setFont(uiFont(Font.BOLD, 16));
            g.gridx = 1;
            form.add(tfPort, g);

            g.gridx = 0; g.gridy = 2;
            form.add(new JLabel("Poste:"), g);
            JComboBox<String> cbPoste = new JComboBox<>(new String[]{"POSTE 1", "POSTE 2"});
            cbPoste.setFont(uiFont(Font.BOLD, 16));
            String savedPoste = getStartupPosteSelection();
            cbPoste.setSelectedItem(isPoste3(savedPoste) ? "POSTE 3 Balance" : savedPoste);
            g.gridx = 1;
            form.add(cbPoste, g);

            g.gridx = 0; g.gridy = 3;
            form.add(new JLabel("Stampante:"), g);

            PrintService[] printers = PrintServiceLookup.lookupPrintServices(null, null);
            JComboBox<String> cbPrinter = new JComboBox<>();
            for (PrintService ps : printers) {
                if (ps != null) {
                    cbPrinter.addItem(ps.getName());
                }
            }

            String savedPrinter = getConfig("printerName", "");
            if (!savedPrinter.isEmpty()) {
                cbPrinter.setSelectedItem(savedPrinter);
            } else {
                PrintService defaultPrinter = PrintServiceLookup.lookupDefaultPrintService();
                if (defaultPrinter != null) {
                    cbPrinter.setSelectedItem(defaultPrinter.getName());
                }
            }
            cbPrinter.setFont(uiFont(Font.BOLD, 16));
            g.gridx = 1;
            form.add(cbPrinter, g);

            cbPoste.addActionListener(ev -> {
                String selectedPoste = (String) cbPoste.getSelectedItem();
                loadAppConfig(selectedPoste);
                tfIp.setText(getConfig("scaleIp", getDefaultIpForPoste(selectedPoste)));
                tfPort.setText(getConfig("scalePort", getDefaultPortForPoste(selectedPoste)));

                String savedPrinterForPoste = getConfig("printerName", "");
                if (!savedPrinterForPoste.isEmpty()) {
                    cbPrinter.setSelectedItem(savedPrinterForPoste);
                } else {
                    PrintService defaultPrinter = PrintServiceLookup.lookupDefaultPrintService();
                    if (defaultPrinter != null) {
                        cbPrinter.setSelectedItem(defaultPrinter.getName());
                    }
                }

                refreshPosteLabel();
                applyDefaultProvenance();
            });

            JLabel lblStatus = new JLabel("Non connesso");
            lblStatus.setForeground(Color.GRAY);
            lblStatus.setFont(uiFont(Font.BOLD, 16));
            g.gridx = 0; g.gridy = 4; g.gridwidth = 2;
            form.add(lblStatus, g);
            g.gridwidth = 1;

            JButton btnConn = makeButton("Connetti", new Color(0x00, 0x80, 0x40), Color.WHITE);
            btnConn.addActionListener(ev -> {
                String ip = tfIp.getText().trim();
                String port = tfPort.getText().trim();
                Object selectedPrinter = cbPrinter.getSelectedItem();
                saveSetupConfig(ip, port, (String) cbPoste.getSelectedItem(), selectedPrinter);
                refreshPosteLabel();
                applyDefaultProvenance();

                lblStatus.setText("Disconnessione forzata + nuova connessione...");
                lblStatus.setForeground(Color.ORANGE);
                btnConn.setEnabled(false);

                new SwingWorker<String, Void>() {
                    protected String doInBackground() {
                        try {
                            // V194.07: prima di una nuova connessione dal Setup Bilancia,
                            // chiudiamo in modo forzato ogni stream/socket ancora agganciato.
                            // Questo evita che una vecchia sessione TCP Ohaus resti bloccata
                            // e faccia fallire subito il nuovo tentativo con Connect timed out.
                            forceDisconnectBalanceServiceForSetup();
                            Thread.sleep(2500L);
                            return balanceService.connectBlocking(ip, Integer.parseInt(port), 4, 12000);
                        } catch (Exception ex) {
                            return ex.getMessage();
                        }
                    }

                    protected void done() {
                        btnConn.setEnabled(true);
                        try {
                            String err = get();
                            if (err == null) {
                                lblStatus.setText("✓ Connesso – Stream attivo");
                                lblStatus.setForeground(new Color(0, 140, 0));
                                btnConn.setText("Disconnetti");
                                btnConn.setBackground(C_BTN_RED);
                            } else {
                                lblStatus.setText("Errore: " + err);
                                lblStatus.setForeground(Color.RED);
                            }
                        } catch (Exception ex) {
                            lblStatus.setText("Errore interno");
                            lblStatus.setForeground(Color.RED);
                        }
                    }
                }.execute();
            });

            g.gridx = 0; g.gridy = 5; g.gridwidth = 2; g.fill = GridBagConstraints.NONE;
            g.anchor = GridBagConstraints.CENTER;
            form.add(btnConn, g);

            JButton btnChiudi = makeButton("Chiudi", new Color(0x70, 0x70, 0x70), Color.WHITE);
            btnChiudi.addActionListener(ev -> {
                saveSetupConfig(tfIp.getText().trim(), tfPort.getText().trim(), (String) cbPoste.getSelectedItem(), cbPrinter.getSelectedItem());
                refreshPosteLabel();
                applyDefaultProvenance();
                dlg.dispose();
            });
            g.gridy = 6;
            form.add(btnChiudi, g);

            dlg.add(form, BorderLayout.CENTER);
            dlg.pack();
            dlg.setLocationRelativeTo(getDialogParentComponent());
            dlg.setVisible(true);
        }


        // V194.23: chiusura pulita della connessione/stream bilancia quando il form
        // viene chiuso, cosi il processo non resta "orfano" in background con il
        // socket TCP ancora aperto verso la bilancia (impedendo la riconnessione
        // al riavvio successivo finche non si termina l'intera sessione Windows).
        // V194.26: usa disconnectGraceful() (FIN standard) invece di forceDisconnect()
        // (RST abortivo): il terminale bilancia sembra non liberare subito lo slot
        // del client con un RST, mentre una chiusura pulita (come quella che il SO
        // fa comunque quando il processo termina, es. End Task) funziona.
        void forceDisconnectBalanceServiceOnExit() {
            balanceService.disconnectGraceful();
        }

        private void forceDisconnectBalanceServiceForSetup() {
            try {
                // Usa reflection per restare compatibile anche se BalanceService cambia nome metodo.
                // Prova prima i nomi piu probabili per una chiusura dura della connessione.
                String[] methodNames = {
                        "forceDisconnect", "hardDisconnect", "disconnectHard", "disconnectNow",
                        "disconnect", "close", "stop", "shutdown"
                };
                Class<?> cls = balanceService.getClass();
                for (String name : methodNames) {
                    try {
                        java.lang.reflect.Method m = cls.getDeclaredMethod(name);
                        m.setAccessible(true);
                        m.invoke(balanceService);
                        return;
                    } catch (NoSuchMethodException ignored) {
                    }
                }

                // Fallback: se non esiste un metodo pubblico/privato di chiusura,
                // tenta di chiudere eventuali Socket/Closeable conservati come campi interni.
                for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(balanceService);
                        if (v instanceof java.net.Socket) {
                            ((java.net.Socket) v).close();
                        } else if (v instanceof java.io.Closeable) {
                            ((java.io.Closeable) v).close();
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        }

        private String mapBanburyName(String code) {
            if (code == null) return "";
            if (code.equals("CBANB1") || code.equals("CBAN1")) return "BANBURY 1";
            if (code.equals("CBANB2") || code.equals("CBAN2")) return "BANBURY 2";
            return "";
        }

        JLabel lbl(String text) {
            JLabel l = new JLabel(text);
            l.setFont(uiFont(Font.BOLD, 16));
            return l;
        }

        JTextField field(String text, int cols) {
            JTextField tf = new JTextField(text, cols);
            tf.setBackground(C_FIELD_BG);
            tf.setForeground(C_TEXT);
            tf.setBorder(new LineBorder(Color.DARK_GRAY, 1));
            tf.setFont(uiFont(Font.BOLD, 16));
            return tf;
        }

        void styleCombo(JComboBox<String> cb) {
            cb.setBackground(C_FIELD_BG);
            cb.setForeground(C_TEXT);
            cb.setFont(uiFont(Font.BOLD, 16));
        }

        JButton makeButton(String label, Color bg, Color fg) {
            JButton b = new JButton(label) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    Object forced = getClientProperty("forcedBg");
                    Color fill = forced instanceof Color ? (Color) forced : getBackground();

                    g2.setColor(fill);
                    g2.fillRect(0, 0, getWidth(), getHeight());

                    String raw = getText() == null ? "" : getText();
                    raw = raw.replace("<html>", "")
                             .replace("</html>", "")
                             .replace("<center>", "")
                             .replace("</center>", "")
                             .replace("<br>", "\n")
                             .replace("<br/>", "\n")
                             .replace("<br />", "\n")
                             .replaceAll("<[^>]+>", "");
                    String[] lines = raw.split("\n", -1);

                    g2.setFont(getFont());
                    g2.setColor(getForeground());
                    FontMetrics fm = g2.getFontMetrics();
                    int lineHeight = fm.getHeight();
                    int totalHeight = lineHeight * lines.length;
                    int y = (getHeight() - totalHeight) / 2 + fm.getAscent();

                    for (String line : lines) {
                        int x = (getWidth() - fm.stringWidth(line)) / 2;
                        g2.drawString(line, x, y);
                        y += lineHeight;
                    }
                    g2.dispose();
                }
            };
            b.putClientProperty("forcedBg", bg);
            b.setBackground(bg);
            b.setForeground(fg);
            b.setFont(uiFont(Font.PLAIN, 11));
            b.setFocusPainted(false);
            b.setOpaque(false);
            b.setContentAreaFilled(false);
            b.setBorderPainted(true);
            b.setBorder(new CompoundBorder(
                    new LineBorder(bg.darker(), 1),
                    new EmptyBorder(s(4), s(8), s(4), s(8))));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return b;
        }

        JToggleButton makeToggleButton(String label, Color bg, Color fg) {
            JToggleButton b = new JToggleButton(label) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    Color fill = isSelected() ? PALETTE_BTN_SELECTED_BG : PALETTE_BTN_DEFAULT_BG;
                    g2.setColor(fill);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            b.setBackground(bg);
            b.setForeground(fg);
            b.setFont(uiFont(Font.PLAIN, 11));
            b.setFocusPainted(false);
            b.setOpaque(false);
            b.setContentAreaFilled(false);
            b.setBorderPainted(true);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return b;
        }

        void forceSize(JComponent c, int w, int h) {
            Dimension d = new Dimension(s(w), s(h));
            c.setPreferredSize(d);
            c.setMinimumSize(d);
            c.setMaximumSize(d);
        }


        void addDocListener(JTextField tf, DocumentListenerHelper dl) {
            tf.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { dl.update(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { dl.update(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { dl.update(); }
            });
        }
    }

    static class DigitsOnlyDocumentFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            if (string != null) {
                string = string.replaceAll("[^0-9]", "");
            }
            if (string != null && !string.isEmpty()) {
                super.insertString(fb, offset, string, attr);
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            if (text != null) {
                text = text.replaceAll("[^0-9]", "");
            }
            super.replace(fb, offset, length, text, attrs);
        }
    }


    @FunctionalInterface
    interface DocumentListenerHelper {
        void update();
    }


}
