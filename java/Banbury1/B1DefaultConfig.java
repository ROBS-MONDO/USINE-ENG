package Banbury1;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Crea i file di configurazione di B1 mancanti in C:\\_tools\\Banbury1 con i valori predefiniti (utile con il runnable jar su un PC nuovo).
 * Non sovrascrive mai un file esistente (ne' in C:\\_tools\\Banbury1 ne' nella cartella corrente). La scrittura nel PLC (b1_recipe_transfer)
 * nel file creato e' SPENTA; la abilita chi la vuole usare. Funziona solo su Windows.
 */
final class B1DefaultConfig {

    static final Path DIR = Paths.get("C:\\_tools\\Banbury1");
    static final String[] NAMES = {
        "b1_tags.properties", "b1_alarms.properties", "b1_recipe_layout.properties", "b1_recipes_sql.properties",
        "b1_recipe_transfer.properties", "b1_label_sql.properties", "b1_govoni.properties"};

    private B1DefaultConfig() {
    }

    /** Crea i file mancanti (da chiamare all'avvio, prima di leggere la configurazione). */
    static void ensureAll() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return;
        }
        for (String name : NAMES) {
            Path target = DIR.resolve(name);
            if (Files.exists(target) || Files.exists(Paths.get(name))) {
                continue;
            }
            try {
                Files.createDirectories(DIR);
                Files.write(target, text(name).getBytes(StandardCharsets.UTF_8));
                System.out.println("Config creata con i valori predefiniti: " + target);
            } catch (IOException | RuntimeException e) {
                System.err.println("Config " + name + ": creazione non riuscita (" + e + ")");
            }
        }
    }

    static String text(String name) {
        switch (name) {
            case "b1_tags.properties": return C0;
            case "b1_alarms.properties": return C1;
            case "b1_recipe_layout.properties": return C2;
            case "b1_recipes_sql.properties": return C3;
            case "b1_recipe_transfer.properties": return C4;
            case "b1_label_sql.properties": return C5;
            case "b1_govoni.properties": return C6;
            default: throw new IllegalArgumentException(name);
        }
    }

    private static final String C0 =
            "# Mappa tag Banbury 1 (CPU IM 151-8 F-CPU, 192.168.10.100, rack 0 slot 2)\n" +
            "# Sintassi: chiave = DB110.W80 | DB110.UW80 | DB110.W80*0.1 | DB110.W6.B12 | DB201.X41.0 | DB150.S6 | !DB110.W6.B3\n" +
            "# Riga vuota = indirizzo predefinito del programma. 'none' = volutamente non collegato (mostra '--').\n" +
            "# Le righe preimpostate possono essere IPOTESI da confermare (vedi DB_MAP.md).\n" +
            "# Premere F2 nel sinottico per vedere la chiave di ogni campo.\n" +
            "\n" +
            "# Pressione (barra alta)\n" +
            "press_top = DB110.W70*0.1\n" +
            "# Temperatura camera (barra alta e riquadro)\n" +
            "temp = DB110.W100\n" +
            "# Velocita' rotori (barra alta)\n" +
            "speed_top = DB110.W60\n" +
            "# Tempo ciclo, secondi (mm:ss)\n" +
            "cycle_time = \n" +
            "DB110.UW202\n" +
            "mix_total = DB110.W210\n" +
            "# Miscele fatte (WinCC: DBW212)\n" +
            "mix_done = DB110.W212\n" +
            "# Miscele da fare (vuoto = totali - fatte)\n" +
            "mix_todo = \n" +
            "# Pistone: pressione consegna\n" +
            "press_set = DB110.W70*0.1\n" +
            "# Pistone: pressione attuale\n" +
            "press_act = DB110.W72*0.1\n" +
            "# Pistone: % 1\n" +
            "piston_pct1 = DB110.W86\n" +
            "# Pistone: % 2 (ipotesi: velocita' discesa)\n" +
            "piston_pct2 = DB110.W106\n" +
            "# Pistone: % 3 (ipotesi: velocita' salita)\n" +
            "piston_pct3 = DB110.W108\n" +
            "# Rotori: giri consegna\n" +
            "rpm_set = DB110.W58\n" +
            "# Rotori: giri attuali\n" +
            "rpm_act = DB110.W60\n" +
            "# Rotori: corrente (A)\n" +
            "current = DB110.W62\n" +
            "# Cycle malaxeur: passi (ipotesi)\n" +
            "steps = DB110.W234\n" +
            "# Suffisso\n" +
            "suffix = \n" +
            "# SEQ prima cifra (ipotesi dal dump)\n" +
            "seq_a = DB150.W0\n" +
            "# SEQ seconda cifra (ipotesi dal dump)\n" +
            "seq_b = DB150.W2\n" +
            "# LOT anno (stringa)\n" +
            "lot_year = DB150.S6\n" +
            "# LOT numero (stringa)\n" +
            "lot_num = DB150.S12\n" +
            "# TYPE\n" +
            "type = \n" +
            "# PASS\n" +
            "pass = \n" +
            "# COULEUR\n" +
            "color = \n" +
            "# NUM MELANGE\n" +
            "num_melange = \n" +
            "# Nome ricetta (stringa)\n" +
            "recipe_name = DB201.S4\n" +
            "# Pistone: posizione alta\n" +
            "piston_high = DB110.W22.B0\n" +
            "# Pistone: flottante\n" +
            "piston_float = DB110.W22.B2\n" +
            "# Pistone: posizione bassa\n" +
            "piston_low = DB110.W22.B1\n" +
            "# Pistone: bloccato\n" +
            "piston_blocked = none\n" +
            "# Rotori: running\n" +
            "rotors_running = M20.3\n" +
            "# Lubrificazione: running\n" +
            "lube_running = M46.5\n" +
            "# Silos: aperto (ipotesi da PDL)\n" +
            "silos_open = M30.0\n" +
            "# Silos: chiuso (ipotesi da PDL)\n" +
            "silos_closed = M30.1\n" +
            "# Porta carico: aperta\n" +
            "door_load_open = DB110.W26.B0\n" +
            "# Porta carico: chiusa\n" +
            "door_load_closed = DB110.W26.B1\n" +
            "# Poids presseur: aperto\n" +
            "weight_open = DB110.W24.B2\n" +
            "# Poids presseur: chiuso\n" +
            "weight_closed = DB110.W24.B3\n" +
            "# Porta scarico: aperta\n" +
            "door_dis_open = DB110.W24.B0\n" +
            "# Porta scarico: chiusa\n" +
            "door_dis_closed = DB110.W24.B1\n" +
            "# Pronto: temp. min. OK avviamento\n" +
            "rdy_temp = DB110.W54.B0\n" +
            "# Pronto: scarico OK\n" +
            "rdy_discharge = DB110.W54.B6\n" +
            "# Pronto: motore principale OK\n" +
            "rdy_motor = DB110.W54.B7\n" +
            "# Pronto: porta scarico chiusa\n" +
            "rdy_door = DB110.W54.B8\n" +
            "# Pronto: tempo min. tra cicli\n" +
            "rdy_mintime = DB110.W54.B9\n" +
            "# Pronto: n. cicli passi effettuati\n" +
            "rdy_cycles = DB110.W54.B10\n" +
            "# Liquidi (grafica e animazione del mixer): da collegare ai tag del PLC\n" +
            "liq_doa = none\n" +
            "liq_silano = none\n" +
            "liq_huile = none\n" +
            "liq_consigne = none\n" +
            "# Ciclo: auto (CONFERMATO in produzione 20/11: coincide con il modo automatico, W44.B3)\n" +
            "cyc_auto = DB110.W44.B3\n" +
            "# Modo automatico (spie AUTO/MANUAL del Display e trend). CONFERMATO in produzione 20/11: W44.B3 (acceso a riposo 0x100A e in produzione 0x3A, spento a download 0x06)\n" +
            "mode_auto = DB110.W44.B3\n" +
            "# Ciclo: continuo\n" +
            "cyc_cont = DB110.W54.B12\n" +
            "# Ciclo: sospeso\n" +
            "cyc_susp = DB110.W54.B15\n" +
            "# Ciclo: ciclo\n" +
            "# CONFERMATO in produzione 20/11: W44.B4\n" +
            "cyc_cycle = DB110.W44.B4\n" +
            "# Posizione carico\n" +
            "pos_load = DB110.W140.B11\n" +
            "# Decelerazione carico\n" +
            "decel_load = DB110.W140.B13\n" +
            "# Decelerazione scarico\n" +
            "decel_dis = DB110.W140.B12\n" +
            "# Posizione scarico\n" +
            "pos_dis = DB110.W140.B14\n" +
            "# Pronto per produzione\n" +
            "prod_ready = DB110.W44.B0\n" +
            "# Ciclo auto ON\n" +
            "# CONFERMATO in produzione 20/11: W44.B5\n" +
            "prod_auto_on = DB110.W44.B5\n" +
            "# Caricamento nuova (ipotesi da PDL)\n" +
            "new_load = DB201.X41.0\n" +
            "# Funzionamento produzione auto\n" +
            "# CONFERMATO in produzione 20/11: W44.B3 (il bit 1 e' 'recette valide')\n" +
            "prod_auto = DB110.W44.B3\n" +
            "# Ciclo arrestato\n" +
            "cycle_stopped = DB110.W44.B2\n" +
            "# Ricetta valida. CONFERMATO in produzione 20/11: W44.B1 (PDL sinottico; a 1 anche durante il download 0x06 e in produzione). Il bit 12 e' 'trasferimento abilitato'\n" +
            "recipe_ok = DB110.W44.B1\n" +
            "# Pistone: in movimento (osservato: si accende in salita e in discesa; la direzione si ricava dall'ultimo fine corsa)\n" +
            "piston_move = DB110.W22.B5\n" +
            "# Malaxage (Grande vue), secondi (mm:ss): tempo della fase attuale (DBW194). Il DBW198 e' l'energia della fase, non un tempo.\n" +
            "# CONFERMATO in produzione 20/11.\n" +
            "mix_time = DB110.UW194\n" +
            "\n" +
            "# --- pagina Process: fase attuale (impostato = DB110.142..171, attuale = DB110.172..201) ---\n" +
            "ph_set_ops = DB110.UW142\n" +
            "ph_set_tmo = DB110.W144\n" +
            "ph_set_bits = DB110.UW152\n" +
            "ph_set_rpm = DB110.W154\n" +
            "ph_set_press = DB110.W156*0.1\n" +
            "ph_set_ctrl = DB110.UW162\n" +
            "ph_set_time = DB110.W164\n" +
            "ph_set_temp = DB110.W166\n" +
            "ph_set_energy = DB110.W168*0.1\n" +
            "ph_act_ops = DB110.UW172\n" +
            "ph_act_tmo = DB110.W174\n" +
            "ph_act_bits = DB110.UW182\n" +
            "ph_act_rpm = DB110.W184\n" +
            "ph_act_press = DB110.W186*0.1\n" +
            "ph_act_ctrl = DB110.UW192\n" +
            "ph_act_time = DB110.W194\n" +
            "ph_act_temp = DB110.W196\n" +
            "ph_act_energy = DB110.W198*0.1\n";

    private static final String C1 =
            "# Allarmi di Banbury 1 (messaggi a bit come nel WinCC Alarm Logging), ricavati dal progetto WinCC CC_Bambury1.\n" +
            "# alarm.<numero> = <indirizzo bit> ; <Point d'erreur> ; <Testo del messaggio> ; <classe>\n" +
            "# Indirizzo: DB113.W<byte>.B<bit> = bit <bit> della parola DBW<byte> (bit 0 = meno significativo), le 9 parole DB113.DBW0..DBW16.\n" +
            "# Testi e punti d'errore in francese (anche dove WinCC aveva inglese o italiano). Il numero e' quello del messaggio WinCC.\n" +
            "# I messaggi senza testo e senza punto d'errore (slot vuoti) non sono inclusi; quelli con solo il punto d'errore (es. PISTON) hanno testo vuoto come in WinCC. Il 999 (PLC comunication fault) e' una variabile interna di WinCC.\n" +
            "alarm.3 = DB113.W0.B2 ; URGENCE ; Arr\u00eat d'urgence ; Erreur\n" +
            "alarm.4 = DB113.W0.B3 ; URGENCE ; Auxiliaires ne sont pas pr\u00e9sents ; Erreur\n" +
            "alarm.5 = DB113.W0.B4 ; URGENCE ; [S51U3] temp\u00e9rature max. dans le panneau principal ; Erreur\n" +
            "alarm.6 = DB113.W0.B5 ; URGENCE ; Porte rotor ouverte (arr\u00eat du moteur) ; Erreur\n" +
            "alarm.7 = DB113.W0.B6 ; URGENCE ; Porte de s\u00e9curit\u00e9 d'\u00e9chappement ouverte (arr\u00eat du moteur et du mouvement de la porte) ; Erreur\n" +
            "alarm.16 = DB113.W0.B15 ; URGENCE ; [Q03O4] Ventilateur fum\u00e9es thermiques d'aspiration ; Erreur\n" +
            "alarm.18 = DB113.W2.B1 ; UNIT\u00c9 HYDRAULIQUE ; [72S7] Huile \u00e0 haute temp\u00e9rature ; Erreur\n" +
            "alarm.19 = DB113.W2.B2 ; UNIT\u00c9 HYDRAULIQUE ; [ 74S1] Servo de pression du filtre bouch\u00e9 piston ; Erreur\n" +
            "alarm.20 = DB113.W2.B3 ; UNIT\u00c9 HYDRAULIQUE ; [ 74S2] Obstru\u00e9 vitesse de piston filtre d'asservissement ; Erreur\n" +
            "alarm.21 = DB113.W2.B4 ; UNIT\u00c9 HYDRAULIQUE ; [72S3] Circuit hydraulique haut niveau ; Erreur\n" +
            "alarm.22 = DB113.W2.B5 ; UNIT\u00c9 HYDRAULIQUE ; [72S2] Niveau bas unit\u00e9 hydraulique ; Erreur\n" +
            "alarm.23 = DB113.W2.B6 ; UNIT\u00c9 HYDRAULIQUE ; [72S4] Filtre obstru\u00e9 Unit\u00e9 Hydraulique ; Erreur\n" +
            "alarm.24 = DB113.W2.B7 ; UNIT\u00c9 HYDRAULIQUE ; [Q03C4]Unit\u00e9 Hydraulique Drop door surcharge thermique moteur ; Erreur\n" +
            "alarm.25 = DB113.W2.B8 ; UNIT\u00c9 HYDRAULIQUE ; [F04C10] Unit\u00e9 Hydraulique Ram surcharge thermique moteur ; Erreur\n" +
            "alarm.28 = DB113.W2.B11 ; R\u00c9DUCTEUR ; [Q03S4] R\u00e9ducteur Mixer surcharge thermique moteur ; Erreur\n" +
            "alarm.29 = DB113.W2.B12 ; R\u00c9DUCTEUR ; [76S3] Filtre \u00e0 huile obstru\u00e9 R\u00e9ducteur Mixer ; Erreur\n" +
            "alarm.30 = DB113.W2.B13 ; R\u00c9DUCTEUR ; [76S4] Basse pression d'huile R\u00e9ducteur Mixer ; Erreur\n" +
            "alarm.31 = DB113.W2.B14 ; R\u00c9DUCTEUR ; [76S2] Huile \u00e0 haute temp\u00e9rature R\u00e9ducteur Mixer ; Erreur\n" +
            "alarm.32 = DB113.W2.B15 ; R\u00c9DUCTEUR ; [76S1] D\u00e9bit d'huile bas R\u00e9ducteur Mixer ; Erreur\n" +
            "alarm.33 = DB113.W4.B0 ; LUBRIFICATION JOINTS ; [Q03G4] lubrification Anneaux surcharge thermique moteur ; Erreur\n" +
            "alarm.34 = DB113.W4.B1 ; URGENCE ;  ; Erreur\n" +
            "alarm.35 = DB113.W4.B2 ; THERMOR\u00c9GULATION ;  ; Erreur\n" +
            "alarm.37 = DB113.W4.B4 ; LUBRIFICATION ANNEAUX ; [84S2] Timeout remplissage r\u00e9servoir huile de lubrification ; Erreur\n" +
            "alarm.38 = DB113.W4.B5 ; LUBRIFICATION ANNEAUX ; [84S2] Bas niveau d'huile de lubrification Anneaux ; Erreur\n" +
            "alarm.41 = DB113.W4.B8 ; GRAISSE ; [82S2] Gras bas niveau ; Erreur\n" +
            "alarm.42 = DB113.W4.B9 ; GRAISSE ; [82S2] Fin de cycle Gras ; Erreur\n" +
            "alarm.44 = DB113.W4.B11 ; LUBRIFICATION JOINTS ; [83S1] Basse pression ligne tenue ; Erreur\n" +
            "alarm.45 = DB113.W4.B12 ; LUBRIFICATION JOINTS ; [83S2] Basse pression  lubrification Anneaux ; Erreur\n" +
            "alarm.46 = DB113.W4.B13 ; THERMOR\u00c9GULATION ;  ; Erreur\n" +
            "alarm.47 = DB113.W4.B14 ; THERMOR\u00c9GULATION ;  ; Erreur\n" +
            "alarm.48 = DB113.W4.B15 ; THERMOR\u00c9GULATION ;  ; Erreur\n" +
            "alarm.49 = DB113.W6.B0 ; PORTE DE D\u00c9CHARGEMENT ; [81S5] Temps maximum d'ouverture ; Erreur\n" +
            "alarm.50 = DB113.W6.B1 ; PORTE DE D\u00c9CHARGEMENT ; [81S4] Temps de fermeture maximale ; Erreur\n" +
            "alarm.51 = DB113.W6.B2 ; PORTE DE D\u00c9CHARGEMENT ; [81S1] s\u00e9curit\u00e9 ferm\u00e9 ; Erreur\n" +
            "alarm.52 = DB113.W6.B3 ; PISTON ; [80S1] Position haute pas atteint ; Erreur\n" +
            "alarm.53 = DB113.W6.B4 ; PISTON ; [80S2] Position basse pas atteint ; Erreur\n" +
            "alarm.54 = DB113.W6.B5 ; PISTON ;  ; Erreur\n" +
            "alarm.55 = DB113.W6.B6 ; PISTON ;  ; Erreur\n" +
            "alarm.56 = DB113.W6.B7 ; PISTON ;  ; Erreur\n" +
            "alarm.57 = DB113.W6.B8 ; PORTE DE CHARGEMENT ; [80S6] \u00e9chec de l'ouverture ; Erreur\n" +
            "alarm.58 = DB113.W6.B9 ; PORTE DE CHARGEMENT ; [80S7] \u00e9chec de la fermeture ; Erreur\n" +
            "alarm.59 = DB113.W6.B10 ; PISTON ;  ; Erreur\n" +
            "alarm.60 = DB113.W6.B11 ; PISTON ;  ; Erreur\n" +
            "alarm.62 = DB113.W6.B13 ; LACH ; [81S3] \u00e9chec de l'ouverture Lach ; Erreur\n" +
            "alarm.63 = DB113.W6.B14 ; LACH ; [81S3] \u00e9chec de la fermeture Lach ; Erreur\n" +
            "alarm.64 = DB113.W6.B15 ; PORTE DE D\u00c9CHARGEMENT ; [72S1] Circuit pression minimum ; Erreur\n" +
            "alarm.65 = DB113.W8.B0 ; MOTEUR ROTORS ; Perte d'air de refroidissement ; Erreur\n" +
            "alarm.66 = DB113.W8.B1 ; MOTEUR ROTORS ; Haute Temp\u00e9rature ; Erreur\n" +
            "alarm.67 = DB113.W8.B2 ; MOTEUR ROTORS ; Tr\u00e8s haute temp\u00e9rature ; Erreur\n" +
            "alarm.68 = DB113.W8.B3 ; MOTEUR ROTORS ; D\u00e9faillance du  Moteur Mixer ; Erreur\n" +
            "alarm.69 = DB113.W8.B4 ; MOTEUR ROTORS ;  ; Erreur\n" +
            "alarm.70 = DB113.W8.B5 ; MOTEUR ROTORS ; Flux minimum d'air du refroidisseur du moteur mixer ; Erreur\n" +
            "alarm.71 = DB113.W8.B6 ; MOTEUR ROTORS ; [Q03K4] Moteur de ventilateur Mixer surcharge thermique moteur ; Erreur\n" +
            "alarm.72 = DB113.W8.B7 ; MOTEUR ROTORS ; Pression minimum eau du refroidisseur moteur mixer ; Erreur\n" +
            "alarm.73 = DB113.W8.B8 ; MOTEUR ROTORS ; Temp\u00e9rature maximum eau du refroidisseur moteur mixer ; Erreur\n" +
            "alarm.75 = DB113.W8.B10 ;  ; Temp\u00e9rature palier moteur mixer PT100 ; Erreur\n" +
            "alarm.76 = DB113.W8.B11 ;  ; Temp\u00e9rature palier collecteur PT100 ; Erreur\n" +
            "alarm.81 = DB113.W10.B0 ; MOTEUR ROTORS ; Vitesse du rotor hors tol\u00e9rance ; Erreur\n" +
            "alarm.82 = DB113.W10.B1 ; PISTON ; Piston de pression hors de tol\u00e9rance ; Erreur\n" +
            "alarm.86 = DB113.W10.B5 ; THERMOCOUPLE ; Temp\u00e9rature mati\u00e8re \u00e9lev\u00e9e ; Erreur\n" +
            "alarm.87 = DB113.W10.B6 ; THERMOCOUPLE ; Temp\u00e9rature mati\u00e8re tr\u00e8s \u00e9lev\u00e9e : d\u00e9chargement ; Erreur\n" +
            "alarm.88 = DB113.W10.B7 ; THERMOCOUPLE ; Diff\u00e9rence entre les deux thermocouples ; Erreur\n" +
            "alarm.92 = DB113.W10.B11 ; G\u00c9N\u00c9RAL ; Pression minimale de l'air ; Erreur\n" +
            "alarm.97 = DB113.W12.B0 ; CYCLE ; Temps de cycle maximum ; Erreur\n" +
            "alarm.98 = DB113.W12.B1 ; CYCLE ; \u00c9nergie maxi ; Erreur\n" +
            "alarm.99 = DB113.W12.B2 ; CYCLE ; Timeout de phase ; Erreur\n" +
            "alarm.100 = DB113.W12.B3 ; CYCLE ; Cycle suspendu ; Erreur\n" +
            "alarm.101 = DB113.W12.B4 ; CYCLE ; Lach ouvert avec cycle en marche (phase 3) ; Erreur\n" +
            "alarm.102 = DB113.W12.B5 ; MOTEUR ROTORS ; Arr\u00eat moteur principal ; Erreur\n" +
            "alarm.103 = DB113.W12.B6 ; PALIERS ;  ; Erreur\n" +
            "alarm.104 = DB113.W12.B7 ; PALIERS ;  ; Erreur\n" +
            "alarm.105 = DB113.W12.B8 ; PALIERS ;  ; Erreur\n" +
            "alarm.106 = DB113.W12.B9 ; PALIERS ;  ; Erreur\n" +
            "alarm.107 = DB113.W12.B10 ; PALIERS ;  ; Erreur\n" +
            "alarm.108 = DB113.W12.B11 ; PALIERS ;  ; Erreur\n" +
            "alarm.109 = DB113.W12.B12 ; PALIERS ;  ; Erreur\n" +
            "alarm.110 = DB113.W12.B13 ; PALIERS ;  ; Erreur\n" +
            "alarm.111 = DB113.W12.B14 ; PALIERS ;  ; Erreur\n" +
            "alarm.112 = DB113.W12.B15 ; PALIERS ;  ; Erreur\n" +
            "alarm.113 = DB113.W14.B0 ; CYCLE ; Temp\u00e9rature Mixer n'est pas OK pour d\u00e9marrer le cycle ; Message\n" +
            "alarm.116 = DB113.W14.B3 ; DOSAGE ; Dosage pas pr\u00eat ; Message\n" +
            "alarm.117 = DB113.W14.B4 ; CYCLE ; M\u00e9langes termin\u00e9s ; Message\n" +
            "alarm.118 = DB113.W14.B5 ; MESSAGES ; Courant \u00e9lev\u00e9 piston flottant ; Message\n" +
            "alarm.119 = DB113.W14.B6 ; MOTEUR ROTORS ; Vitesse moteur mixer < vitesse min. de chargement ; Message\n" +
            "alarm.120 = DB113.W14.B7 ; PALIERS ;  ; Message\n" +
            "alarm.121 = DB113.W14.B8 ; UNIT\u00c9 HYDRAULIQUE ; [72S6] Huile \u00e0 haute temp\u00e9rature ; Message\n" +
            "alarm.122 = DB113.W14.B9 ; PALIERS ;  ; Message\n" +
            "alarm.123 = DB113.W14.B10 ; PALIERS ;  ; Message\n" +
            "alarm.124 = DB113.W14.B11 ; PALIERS ;  ; Message\n" +
            "alarm.125 = DB113.W14.B12 ; PALIERS ;  ; Message\n" +
            "alarm.126 = DB113.W14.B13 ; PALIERS ;  ; Message\n" +
            "alarm.127 = DB113.W14.B14 ; PALIERS ;  ; Message\n" +
            "alarm.129 = DB113.W16.B8 ; PORTE DE CHARGEMENT ;  ; Message\n" +
            "alarm.130 = DB113.W16.B9 ; PORTE DE CHARGEMENT ;  ; Message\n" +
            "alarm.137 = DB113.W16.B0 ; MOTEUR ROTORS ;  ; Erreur\n" +
            "alarm.138 = DB113.W16.B1 ; CONVOYEUR ;  ; Erreur\n" +
            "alarm.139 = DB113.W16.B2 ; MOTEUR ROTORS ;  ; Erreur\n" +
            "alarm.140 = DB113.W16.B3 ; CONVOYEUR ;  ; Erreur\n" +
            "alarm.141 = DB113.W16.B4 ; PORTE DE D\u00c9CHARGEMENT ;  ; Erreur\n" +
            "alarm.142 = DB113.W16.B5 ; PORTE DE CHARGEMENT ;  ; Erreur\n" +
            "alarm.143 = DB113.W16.B6 ; PORTE DE CHARGEMENT ;  ; Erreur\n" +
            "alarm.144 = DB113.W16.B7 ; PISTON ;  ; Erreur\n";

    private static final String C2 =
            "# Layout ricetta Banbury 1 (DB201), CONFERMATO dal database ricette del WinCC (tabelle A_RECIPE_PHASE_PARAMS/_TAGS).\n" +
            "# 10 fasi da 30 byte a partire da DBW42. Per ogni fase: +0 operazioni, +2 timeout, +10 set, +12 rotor, +14 pressione (x10), +20 controllo, +22 tempo, +24 temp., +26 energia (x10).\n" +
            "# Operazioni (bit della parola a offset 0 del blocco fase): CONFERMATE dal WinCC (btnBitJob): 0-6, 13, 14, 15 = Dech.\n" +
            "# Controlli (btnBitControl): et = bit 0, ou = bit 1, Skip = bit 15 (parola a offset 20, CONTROLS_word).\n" +
            "# Etichette confermate dal WinCC (002_Process): safety_temp, drop_door, safety_time, max_energy, restart_temp.\n" +
            "# Colonne WinCC (006_Recipes): Operazione = Gomme, Add., All Black, Plastif., Cerac., Silano, Half Black, Degas, Net., Dech.;\n" +
            "#   Set = Timeout, Rotor (rpm), Press. (bar), Porte, Piston; Controllo = Skip, Temps (s), Temp. (C), Ener. (kWh), et, ou.\n" +
            "# Campo: chiave = W<offset>|UW<offset> ; Etichetta ; Unita' ; Scala ; Min ; Max\n" +
            "# header.* = offset assoluto nel DB; phase.* = offset dall'inizio del blocco fase.\n" +
            "db = 201\n" +
            "db.size = 384\n" +
            "recipeno.offset = 2\n" +
            "name.offset = 4   # STRING S7 [max][len][caratteri]\n" +
            "name.max = 32\n" +
            "phase.base = 42\n" +
            "phase.size = 30\n" +
            "phase.count = 10\n" +
            "ops.offset = 0   # parola a bit delle operazioni (offset nel blocco fase)\n" +
            "discharge.bit = 15   # bit 'scarico' (ipotesi)\n" +
            "ops.bit0 = Gomme\n" +
            "ops.bit1 = Add.\n" +
            "ops.bit2 = All Black\n" +
            "ops.bit3 = Plastif.\n" +
            "ops.bit4 = Cerac.\n" +
            "ops.bit5 = Silano\n" +
            "ops.bit6 = Half Black\n" +
            "ops.bit13 = Degas\n" +
            "ops.bit14 = Net.\n" +
            "ops.bit15 = D\u00e9ch. (d\u00e9chargement)\n" +
            "# ops.bit12 = nome operazione   (aggiungere le righe che servono)\n" +
            "header.safety_temp = W28 ; Temp. de s\u00e9curit\u00e9 ; \u00b0C ; 1.0 ; 0.0 ; 200.0\n" +
            "header.drop_door = W30 ; Porte de d\u00e9chargement ; s ; 1.0 ; 0.0 ; 3600.0\n" +
            "header.safety_time = W32 ; Temps de s\u00e9curit\u00e9 ; s ; 1.0 ; 0.0 ; 3600.0\n" +
            "header.max_energy = W34 ; \u00c9nergie max ; kWh ; 0.1 ; 0.0 ; 999.9\n" +
            "header.restart_temp = W38 ; Temp. de red\u00e9marrage ; \u00b0C ; 1.0 ; 0.0 ; 200.0\n" +
            "phase.timeout = W2 ; Timeout ; s ; 1.0 ; 0.0 ; 9999.0\n" +
            "phase.set_bits = UW10 ; Consigne : porte et piston (bits) ;  ; 1.0 ;  ; \n" +
            "phase.rpm = W12 ; Rotor ; rpm ; 1.0 ; 5.0 ; 50.0\n" +
            "phase.press = W14 ; Press. ; bar ; 0.1 ; 0.0 ; 500.0\n" +
            "phase.ctrl_bits = UW20 ; Contr\u00f4le : et=bit0, ou=bit1, saut=bit15 ;  ; 1.0 ;  ; \n" +
            "phase.time = W22 ; Temps phase ; s ; 1.0 ; 0.0 ; 99999.0\n" +
            "phase.temp = W24 ; Temp\u00e9rature ; \u00b0C ; 1.0 ; 0.0 ; 600.0\n" +
            "phase.energy = W26 ; \u00c9nergie ; kWh ; 0.1 ; 0.0 ; 1000.0\n";

    private static final String C3 =
            "# Archivio ricette di B1 su SQL Server (come B2). Posizione: C:\\_tools\\Banbury1\\b1_recipes_sql.properties\n" +
            "# Crea prima le tabelle con sql/create_b1_recipes.sql (le esegue chi amministra SQL Server).\n" +
            "# enabled = false: le ricette restano in file nella cartella C:\\_tools\\Banbury1\\Recipes.\n" +
            "enabled = false\n" +
            "# Connessione centralizzata (consigliata): usa SVC.DBBamburyReader del progetto, nessuna credenziale in questo file.\n" +
            "# Con provider = svc gli altri parametri di connessione (jdbc.url, auth, jdbc.user, jdbc.password) sono ignorati.\n" +
            "provider = svc\n" +
            "jdbc.url = jdbc:sqlserver://srv-bambury:1433;databaseName=mondo;encrypt=true;trustServerCertificate=true;loginTimeout=5\n" +
            "# Modo consigliato: autenticazione Windows, nessuna password (vedi sql/grant_b1_windows_group.sql). Togli il commento alla riga seguente\n" +
            "# e lascia vuoti jdbc.user / jdbc.password:\n" +
            "# auth = windows\n" +
            "jdbc.user = b1_recipes\n" +
            "# Password: meglio nella variabile d'ambiente B1_RECIPES_SQL_PASSWORD (non nel file)\n" +
            "jdbc.password =\n";

    private static final String C4 =
            "# Invio delle ricette al PLC (DB201). Posizione: C:\\_tools\\Banbury1\\b1_recipe_transfer.properties\n" +
            "# La scrittura e' ABILITATA (enabled e allow_raw_write = false): \"Invia al PLC\" e \"Telecharger\" mostrano l'anteprima e,\n" +
            "# dopo la conferma scritta ENVOYER, scrivono nel PLC. Per tornare alla sola anteprima mettere entrambe le righe a false.\n" +
            "# Prima di ogni invio: macchina ferma (rotori 0), trasferimento abilitato dal PLC (DBW44 bit 12), copia di sicurezza del DB201.\n" +
            "# Provare con la macchina ferma e con l'operatore presente. La sequenza e' quella del WinCC originale (registro B1DbDump del 16/11/2026):\n" +
            "#   1) a riposo DB110.DBW44 = 0x100A (bit 12 = trasferimento abilitato)\n" +
            "#   2) Start transfert = DB201.DBX41.0 = 1; il PLC risponde DB110.DBW44 bit 2 = 1\n" +
            "#   3) scrittura delle parole della ricetta (numero ricetta DBW2 per ultimo)\n" +
            "#   4) fine = DB201.DBX383.0 = 1 (DBW382); dopo alcuni secondi il PLC azzera Start e Fine e riporta DBW44 a 0x100A\n" +
            "enabled = false\n" +
            "allow_raw_write = true\n" +
            "# true = usa lo scambio di sopra (consigliato); false = solo scrittura delle parole (solo per prove su un server di prova)\n" +
            "handshake = true\n" +
            "# Attesa massima della chiusura dello scambio da parte del PLC, in secondi (nel registro: circa 9 s)\n" +
            "wait_seconds = 60\n" +
            "# Copia di sicurezza del DB201 salvata prima di ogni invio\n" +
            "backup_dir = C:\\_tools\\Banbury1\\Recipes\\backup\n" +
            "# Download dalla pagina Production: scrive anche il totale miscele della produzione in DB110.DBW210 (\"Melanges total\").\n" +
            "# La scrittura avviene PRIMA dello Start, viene riletta e ricontrollata a trasferimento chiuso; a fine trasferimento il PLC\n" +
            "# azzera le miscele fatte (DBW212) e ricalcola quelle da fare (DBW214 = DBW210 - DBW212), come visto nel registro del 17/11/2026.\n" +
            "# Attivo di default (non ancora verificato con una quantita' diversa da 100: da provare in produzione). false = solo la ricetta.\n" +
            "# Come ogni scrittura, avviene solo se enabled e allow_raw_write sono true.\n" +
            "write_quantity = true\n";

    private static final String C5 =
            "# Scrittura dei dati del ticket nel database SQL di Banbury 1.\n" +
            "# Finche' enabled=false (o table vuota) il programma mostra l'SQL ma NON scrive.\n" +
            "# Password: meglio nella variabile d'ambiente B1_SQL_PASSWORD (non nel file).\n" +
            "enabled = false\n" +
            "jdbc.url = jdbc:sqlserver://srv-bambury:1433;databaseName=DATA_BAMBURY;encrypt=true;trustServerCertificate=true;loginTimeout=5\n" +
            "jdbc.user =\n" +
            "jdbc.password =\n" +
            "# Tabella di B1 (da confermare: es. dbo.Datas_B1) e modalita' insert|update\n" +
            "table =\n" +
            "mode = insert\n" +
            "# per mode=update: colonna e campo che identificano la riga da aggiornare\n" +
            "key.column =\n" +
            "key.field =\n" +
            "# Colonne: lasciare vuoto cio' che non va scritto. Esempio modellato su Datas_B2:\n" +
            "#   col.num_lot = Num_lot        col.suffixe = Suffixe       col.serie = Num_melange\n" +
            "#   col.couche = Type_melange    col.passage = Num_passage   col.couleur = Couleur\n" +
            "#   col.recipe_name = Nom_melange   col.nombre = Nbr_sequence\n" +
            "# Lotto completo AA/LOT (es. 26/0703)\n" +
            "col.num_lot =\n" +
            "# Anno (es. 26)\n" +
            "col.annee =\n" +
            "# Numero lotto (es. 0703)\n" +
            "col.lot =\n" +
            "# Suffisso\n" +
            "col.suffixe =\n" +
            "# Num melange / serie (A, A1...)\n" +
            "col.serie =\n" +
            "# Numero di miscele\n" +
            "col.nombre =\n" +
            "# Passaggio come testo WinCC (1er, 2eme...)\n" +
            "col.passage =\n" +
            "# Type (SUP, INF, STS, NET, MEL.MERE, PREMAST)\n" +
            "col.couche =\n" +
            "# Couleur (---, Clair, Fonc\u00e9, Medium)\n" +
            "col.couleur =\n" +
            "# Nome ricetta\n" +
            "col.recipe_name =\n" +
            "# RecipeUID\n" +
            "col.recipe_uid =\n" +
            "# Data (colonna data)\n" +
            "col.date =\n" +
            "# Ora (colonna ora)\n" +
            "col.time =\n" +
            "# Data e ora (colonna datetime)\n" +
            "col.timestamp =\n" +
            "# Utente Windows\n" +
            "col.user =\n";

    private static final String C6 =
            "# Silos di B1 dal sistema Govoni (sola lettura, via OpcManager del progetto, come B2).\n" +
            "# silos_node = NodeId OPC del campo \"Etat execution\" della produzione BY1 (bilancia W118): R2021 (BY2/W117 = R1021).\n" +
            "# Vuoto = usa il valore standard ns=2;s=Govoni.SILOS.R2021.\n" +
            "# Se OpcManager non e' nel classpath, le spie SILOS restano quelle del PLC.\n" +
            "# Regola: codice 2 (Descharge Vidage) = silos APERTO (spia Ouvert verde); ogni altro stato = CHIUSO (spia Ferme rossa).\n" +
            "silos_node = ns=2;s=Govoni.SILOS.R2021\n";
}
