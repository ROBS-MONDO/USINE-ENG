package Banbury1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mappa chiave logica -> indirizzo S7 per il sinottico di Banbury 1.
 *
 * Sintassi indirizzo (file b1_tags.properties):
 *   DB110.W80          INT con segno (2 byte)
 *   DB110.UW80         INT senza segno
 *   DB110.W80*0.1      con fattore di scala
 *   DB110.W6.B12       bit 12 della parola DBW6 (0 = bit meno significativo)
 *   DB201.X41.0        bit 0 del byte 41
 *   DB150.S6           STRING S7 (byte max, byte lunghezza, caratteri)
 *   !DB110.W6.B3       il '!' davanti nega un bit
 *   (vuoto)            usa l'indirizzo predefinito del programma
 *   none               non collegato: il campo mostra "--"
 *
 * Gli indirizzi predefiniti sono IPOTESI dedotte dal dump del 12/11/2026 e dai
 * tag WinCC: vanno confermati sul campo e corretti nel file.
 */
public final class B1Tags {

    public enum Kind { INT, UINT, BIT, STRING }

    public static final class Tag {
        public final String key;
        public final String desc;
        public final String spec;
        public final Kind kind;
        public final int db;
        public final int off;
        public final int bit;
        public final double scale;
        public final boolean negate;
        public final boolean guess;

        Tag(String key, String desc, String spec, Kind kind, int db, int off, int bit,
            double scale, boolean negate, boolean guess) {
            this.key = key;
            this.desc = desc;
            this.spec = spec;
            this.kind = kind;
            this.db = db;
            this.off = off;
            this.bit = bit;
            this.scale = scale;
            this.negate = negate;
            this.guess = guess;
        }

        /** Byte necessari a partire da off. */
        public int length() {
            return kind == Kind.BIT ? 1 : (kind == Kind.STRING ? 2 : 2);
        }
    }

    private static final Pattern SPEC = Pattern.compile(
            "^(!?)DB(\\d+)\\.(?:(U?W)(\\d+)(?:\\.B(\\d+))?|X(\\d+)\\.([0-7])|S(\\d+))(?:\\*([0-9.]+))?$");

    /** Area merker (M, flag memory): M30.0 (bit), MW30 / UMW30 (parola), MW30.B3 (bit di una parola); db = 0 nei Tag. */
    private static final Pattern MSPEC = Pattern.compile(
            "^(!?)(?:M(U?W)(\\d+)(?:\\.B(\\d+))?|M(\\d+)\\.([0-7]))(?:\\*([0-9.]+))?$");

    private final Map<String, Tag> tags = new LinkedHashMap<>();

    // chiave, descrizione, indirizzo predefinito ("" = non collegato)
    private static final String[][] DEFAULTS = {
        // --- valori ---
        {"press_top",   "Pressione (barra alta)",                    "DB110.W70*0.1"},
        {"temp",        "Temperatura camera (barra alta e riquadro)", "DB110.W100"},
        {"speed_top",   "Velocita' rotori (barra alta)",             "DB110.W60"},
        {"mix_time",    "Tempo di mescola (fase attuale), secondi (mm:ss)", "DB110.UW194"},
        {"cycle_time",  "Tempo ciclo, secondi (mm:ss)",              "DB110.UW202"},
        {"mix_total",   "Miscele totali (WinCC: DBW210)",         "DB110.W210"},
        {"mix_done",    "Miscele fatte (WinCC: DBW212)",          "DB110.W212"},
        {"mix_todo",    "Miscele da fare (vuoto = totali - fatte)",  ""},
        {"press_set",   "Pistone: pressione consegna",               "DB110.W70*0.1"},
        {"press_act",   "Pistone: pressione attuale",                "DB110.W72*0.1"},
        {"piston_pct1", "Pistone: % 1",                              "DB110.W86"},
        {"piston_pct2", "Pistone: % 2 (ipotesi: velocita' discesa)", "DB110.W106"},
        {"piston_pct3", "Pistone: % 3 (ipotesi: velocita' salita)",  "DB110.W108"},
        {"rpm_set",     "Rotori: giri consegna",                     "DB110.W58"},
        {"rpm_act",     "Rotori: giri attuali",                      "DB110.W60"},
        {"current",     "Rotori: corrente (A)",                      "DB110.W62"},
        {"steps",       "Cycle malaxeur: passi (ipotesi)",           "DB110.W234"},
        {"suffix",      "Suffisso",                                  ""},
        {"seq_a",       "SEQ prima cifra (ipotesi dal dump)",        "DB150.W0"},
        {"seq_b",       "SEQ seconda cifra (ipotesi dal dump)",      "DB150.W2"},
        {"lot_year",    "LOT anno (stringa)",                        "DB150.S6"},
        {"lot_num",     "LOT numero (stringa)",                      "DB150.S12"},
        {"type",        "TYPE",                                      ""},
        {"pass",        "PASS",                                      ""},
        {"color",       "COULEUR",                                   ""},
        {"num_melange", "NUM MELANGE",                               ""},
        {"recipe_name", "Nome ricetta (stringa)",                    "DB201.S4"},
        // --- lampade piston ---
        {"piston_high",    "Pistone: posizione alta",   "DB110.W22.B0"},
        {"piston_float",   "Pistone: flottante",        "DB110.W22.B2"},
        {"piston_low",     "Pistone: posizione bassa",  "DB110.W22.B1"},
        {"piston_blocked", "Pistone: bloccato",         "none"},
        {"piston_move",    "Pistone: in movimento (osservato: si accende in salita e in discesa)", "DB110.W22.B5"},
        {"rotors_running", "Rotori: running (merker M20.3, tag WinCC RotorRunning; prima: velocita' DB110.W60 diversa da 0)",           "M20.3"},
        {"lube_running",   "Lubrificazione: running (merker M46.5, tag WinCC LubricationRunning)",   "M46.5"},
        // --- silos / porte ---
        {"silos_open",      "Silos: aperto (merker M30.0, tag WinCC SilosOpen)",  "M30.0"},
        {"silos_closed",    "Silos: chiuso (merker M30.1, tag WinCC SilosClose; prima: DB110.W6.B11)",  "M30.1"},
        {"door_load_open",  "Porta carico: aperta",            "DB110.W26.B0"},
        {"door_load_closed","Porta carico: chiusa",            "DB110.W26.B1"},
        {"weight_open",     "Poids presseur: aperto",          "DB110.W24.B2"},
        {"weight_closed",   "Poids presseur: chiuso",          "DB110.W24.B3"},
        {"door_dis_open",   "Porta scarico: aperta",           "DB110.W24.B0"},
        {"door_dis_closed", "Porta scarico: chiusa",           "DB110.W24.B1"},
        // --- malaxeur pret ---
        {"rdy_temp",     "Pronto: temp. min. OK avviamento", "DB110.W54.B0"},
        {"rdy_discharge","Pronto: scarico OK",               "DB110.W54.B6"},
        {"rdy_motor",    "Pronto: motore principale OK",     "DB110.W54.B7"},
        {"rdy_door",     "Pronto: porta scarico chiusa",     "DB110.W54.B8"},
        {"rdy_mintime",  "Pronto: tempo min. tra cicli",     "DB110.W54.B9"},
        {"rdy_cycles",   "Pronto: n. cicli passi effettuati","DB110.W54.B10"},
        // --- cycle malaxeur ---
        {"liq_ceraclore",    "Liquido CERACLORE attivo (da collegare)", "none"},
        {"liq_silano", "Liquido SILANO attivo (da collegare)", "none"},
        {"liq_huile",  "Liquido HUILE PLASTIFIANTE attivo (da collegare)", "none"},
        {"liq_consigne", "Consigna liquidi (da collegare)", "none"},
        {"cyc_auto",   "Ciclo: auto (CONFERMATO in produzione 20/11: = modo automatico, W44.B3)", "DB110.W44.B3"},
        {"phase_no",   "Fase in corso (WinCC: DBW218, RunningPhaseActual)", "DB110.W218"},
        {"mode_auto",  "Modo automatico (spie AUTO/MANUAL e trend) (CONFERMATO in produzione 20/11: W44.B3)", "DB110.W44.B3"},
        {"cyc_cont",   "Ciclo: continuo",   "DB110.W54.B12"},
        {"cyc_susp",   "Ciclo: sospeso",    "DB110.W54.B15"},
        {"cyc_cycle",  "Ciclo: ciclo (CONFERMATO in produzione 20/11: W44.B4)",      "DB110.W44.B4"},
        // --- pagina Process: fase attuale. Valori impostati (dalla ricetta) DB110.142..171, valori attuali DB110.172..201 ---
        {"ph_set_ops",    "Fase attuale, impostato: operazioni (bit)", "DB110.UW142"},
        {"ph_set_tmo",    "Fase attuale, impostato: timeout s",        "DB110.W144"},
        {"ph_set_bits",   "Fase attuale, impostato: porta/pistone",    "DB110.UW152"},
        {"ph_set_rpm",    "Fase attuale, impostato: rotor rpm",        "DB110.W154"},
        {"ph_set_press",  "Fase attuale, impostato: pressione bar",    "DB110.W156*0.1"},
        {"ph_set_ctrl",   "Fase attuale, impostato: controllo (bit)",  "DB110.UW162"},
        {"ph_set_time",   "Fase attuale, impostato: tempo s",          "DB110.W164"},
        {"ph_set_temp",   "Fase attuale, impostato: temperatura",      "DB110.W166"},
        {"ph_set_energy", "Fase attuale, impostato: energia kWh",      "DB110.W168*0.1"},
        {"ph_act_ops",    "Fase attuale, attuale: operazioni (bit)",   "DB110.UW172"},
        {"ph_act_tmo",    "Fase attuale, attuale: timeout s",          "DB110.W174"},
        {"ph_act_bits",   "Fase attuale, attuale: porta/pistone",      "DB110.UW182"},
        {"ph_act_rpm",    "Fase attuale, attuale: rotor rpm",          "DB110.W184"},
        {"ph_act_press",  "Fase attuale, attuale: pressione bar",      "DB110.W186*0.1"},
        {"ph_act_ctrl",   "Fase attuale, attuale: controllo (bit)",    "DB110.UW192"},
        {"ph_act_time",   "Fase attuale, attuale: tempo s",            "DB110.W194"},
        {"ph_act_temp",   "Fase attuale, attuale: temperatura",        "DB110.W196"},
        {"ph_act_energy", "Fase attuale, attuale: energia kWh",        "DB110.W198*0.1"},
        // --- pagina Production: scambio ricetta (log del WinCC del 16/11/2026) ---
        {"transfer_enabled", "Trasferimento ricetta abilitato (DBW44 bit 12 a riposo)", "DB110.W44.B12"},
        {"transfer_end",     "Fine trasferimento (DB201.DBX383.0)",                      "DB201.X383.0"},
        // --- posizioni ---
        {"pos_load",     "Posizione carico",       "DB110.W140.B11"},
        {"decel_load",   "Decelerazione carico",   "DB110.W140.B13"},
        {"decel_dis",    "Decelerazione scarico",  "DB110.W140.B12"},
        {"pos_dis",      "Posizione scarico",      "DB110.W140.B14"},
        // --- produzione ---
        {"prod_ready",   "Pronto per produzione",   "DB110.W44.B0"},
        {"prod_auto_on", "Ciclo auto ON (CONFERMATO in produzione 20/11: W44.B5)",           "DB110.W44.B5"},
        {"new_load",     "Caricamento nuova (ipotesi da PDL)", "DB201.X41.0"},
        {"prod_auto",    "Funzionamento produzione auto (CONFERMATO in produzione 20/11: W44.B3)", "DB110.W44.B3"},
        {"cycle_stopped","Ciclo arrestato",         "DB110.W44.B2"},
        {"recipe_ok",    "Ricetta valida (CONFERMATO in produzione 20/11: W44.B1)",          "DB110.W44.B1"},
    };

    private B1Tags() {
    }

    /** Predefiniti, poi sovrascritti dal file (se esiste). */
    public static B1Tags load(Path file) {
        B1Tags t = new B1Tags();
        Map<String, String> override = new LinkedHashMap<>();
        if (file != null && Files.isReadable(file)) {
            try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                        continue;
                    }
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        String spec = line.substring(eq + 1).trim();
                        int hash = spec.indexOf(" #");
                        if (hash >= 0) {
                            spec = spec.substring(0, hash).trim();
                        }
                        override.put(line.substring(0, eq).trim(), spec);
                    }
                }
            } catch (IOException e) {
                System.err.println("b1_tags: " + e.getMessage());
            }
        }
        for (String[] d : DEFAULTS) {
            // riga assente o VUOTA = indirizzo predefinito; "none" (o "-") = volutamente non collegato
            String fileSpec = override.get(d[0]);
            boolean fromFile = fileSpec != null && !fileSpec.isEmpty();
            String spec = fromFile ? fileSpec : d[2];
            if ("none".equalsIgnoreCase(spec) || "-".equals(spec)) {
                spec = "";
                fromFile = true;
            }
            Tag tag = parse(d[0], d[1], spec, !fromFile && !spec.isEmpty());
            if (tag != null) {
                t.tags.put(d[0], tag);
            } else {
                t.tags.put(d[0], new Tag(d[0], d[1], "", Kind.INT, 0, 0, 0, 1, false, false));
            }
        }
        return t;
    }

    static Tag parse(String key, String desc, String spec, boolean guess) {
        if (spec == null || spec.isEmpty()) {
            return null;
        }
        Matcher mm = MSPEC.matcher(spec);
        if (mm.matches()) {
            boolean negM = !mm.group(1).isEmpty();
            double scaleM = mm.group(7) != null ? Double.parseDouble(mm.group(7)) : 1.0;
            if (mm.group(2) != null) {
                int off = Integer.parseInt(mm.group(3));
                if (mm.group(4) != null) {
                    int b = Integer.parseInt(mm.group(4));
                    if (b > 15) {
                        return null;
                    }
                    return new Tag(key, desc, spec, Kind.BIT, 0, b >= 8 ? off : off + 1, b % 8, 1, negM, guess);
                }
                return new Tag(key, desc, spec, mm.group(2).startsWith("U") ? Kind.UINT : Kind.INT, 0, off, 0, scaleM, false, guess);
            }
            return new Tag(key, desc, spec, Kind.BIT, 0, Integer.parseInt(mm.group(5)), Integer.parseInt(mm.group(6)), 1, negM, guess);
        }
        Matcher m = SPEC.matcher(spec);
        if (!m.matches()) {
            System.err.println("b1_tags: indirizzo non valido per " + key + ": " + spec);
            return null;
        }
        boolean neg = !m.group(1).isEmpty();
        int db = Integer.parseInt(m.group(2));
        double scale = m.group(9) != null ? Double.parseDouble(m.group(9)) : 1.0;
        if (m.group(3) != null) {
            boolean unsigned = m.group(3).startsWith("U");
            int off = Integer.parseInt(m.group(4));
            if (m.group(5) != null) {
                int b = Integer.parseInt(m.group(5));
                if (b > 15) {
                    return null;
                }
                // DBW big-endian: bit 8..15 nel byte "off", bit 0..7 in "off+1"
                int byteOff = b >= 8 ? off : off + 1;
                return new Tag(key, desc, spec, Kind.BIT, db, byteOff, b % 8, 1, neg, guess);
            }
            return new Tag(key, desc, spec, unsigned ? Kind.UINT : Kind.INT, db, off, 0, scale, false, guess);
        }
        if (m.group(6) != null) {
            return new Tag(key, desc, spec, Kind.BIT, db, Integer.parseInt(m.group(6)),
                    Integer.parseInt(m.group(7)), 1, neg, guess);
        }
        return new Tag(key, desc, spec, Kind.STRING, db, Integer.parseInt(m.group(8)), 0, 1, false, guess);
    }

    /** Chiavi note (per elenchi/diagnostica). */
    public static java.util.Set<String> keysOf(B1Tags t) {
        return t.tags.keySet();
    }

    public Tag get(String key) {
        Tag t = tags.get(key);
        return (t == null || t.spec.isEmpty()) ? null : t;
    }

    public String desc(String key) {
        Tag t = tags.get(key);
        return t == null ? key : t.desc;
    }

    /** Primo byte dopo l'ultimo usato dai tag del DB indicato (db 0 = area M); 0 se nessun tag. */
    public int usedLength(int db) {
        int max = 0;
        for (Tag t : tags.values()) {
            if (!t.spec.isEmpty() && t.db == db) {
                max = Math.max(max, t.off + t.length());
            }
        }
        return max;
    }

    /** DB usati dai tag; 0 = area merker (M). */
    public Set<Integer> dbs() {
        Set<Integer> s = new TreeSet<>();
        for (Tag t : tags.values()) {
            if (!t.spec.isEmpty()) {
                s.add(t.db);
            }
        }
        return s;
    }

    /** Scrive un file b1_tags.properties modificabile con tutti i campi. */
    public static void writeTemplate(Path out) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            w.println("# Mappa tag Banbury 1 (CPU IM 151-8 F-CPU, 192.168.10.100, rack 0 slot 2)");
            w.println("# Sintassi: chiave = DB110.W80 | DB110.UW80 | DB110.W80*0.1 | DB110.W6.B12 | DB201.X41.0 | DB150.S6 | !DB110.W6.B3");
            w.println("# Area merker (M): M30.0 | MW30 | UMW30 | MW30.B3 (sola lettura)");
            w.println("# Riga vuota = indirizzo predefinito del programma. 'none' = volutamente non collegato (mostra '--').");
            w.println("# Le righe preimpostate possono essere IPOTESI da confermare (vedi DB_MAP.md).");
            w.println("# Premere F2 nel sinottico per vedere la chiave di ogni campo.");
            w.println();
            for (String[] d : DEFAULTS) {
                w.println("# " + d[1]);
                w.println(d[0] + " = " + d[2]);
            }
        }
    }
}
