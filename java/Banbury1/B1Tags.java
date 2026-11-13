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

    private final Map<String, Tag> tags = new LinkedHashMap<>();

    // chiave, descrizione, indirizzo predefinito ("" = non collegato)
    private static final String[][] DEFAULTS = {
        // --- valori ---
        {"press_top",   "Pressione (barra alta)",                    "DB110.W70*0.1"},
        {"temp",        "Temperatura camera (barra alta e riquadro)", "DB110.W100"},
        {"speed_top",   "Velocita' rotori (barra alta)",             "DB110.W60"},
        {"cycle_time",  "Tempo ciclo, secondi (mm:ss)",              ""},
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
        {"piston_float",   "Pistone: flottante",        "DB110.W22.B5"},
        {"piston_low",     "Pistone: posizione bassa",  "DB110.W22.B6"},
        {"piston_blocked", "Pistone: bloccato",         "DB110.W22.B1"},
        {"rotors_running", "Rotori: running",           "DB110.W60"},
        {"lube_running",   "Lubrificazione: running",   ""},
        // --- silos / porte ---
        {"silos_open",      "Silos: aperto (ipotesi da PDL)",  "DB110.W6.B12"},
        {"silos_closed",    "Silos: chiuso (ipotesi da PDL)",  "DB110.W6.B13"},
        {"door_load_open",  "Porta carico: aperta",            "DB110.W26.B0"},
        {"door_load_closed","Porta carico: chiusa",            "DB110.W26.B1"},
        {"weight_open",     "Poids presseur: aperto",          "DB110.W24.B4"},
        {"weight_closed",   "Poids presseur: chiuso",          "DB110.W24.B3"},
        {"door_dis_open",   "Porta scarico: aperta",           "DB110.W24.B0"},
        {"door_dis_closed", "Porta scarico: chiusa",           "DB110.W24.B1"},
        // --- malaxeur pret ---
        {"rdy_temp",     "Pronto: temp. min. OK avviamento", "DB110.W54.B6"},
        {"rdy_discharge","Pronto: scarico OK",               "DB110.W54.B7"},
        {"rdy_motor",    "Pronto: motore principale OK",     "DB110.W54.B8"},
        {"rdy_door",     "Pronto: porta scarico chiusa",     "DB110.W54.B9"},
        {"rdy_mintime",  "Pronto: tempo min. tra cicli",     "DB110.W54.B10"},
        {"rdy_cycles",   "Pronto: n. cicli passi effettuati","DB110.W54.B11"},
        // --- cycle malaxeur ---
        {"cyc_auto",   "Ciclo: auto",       "DB110.W54.B14"},
        {"phase_no",   "Fase in corso (WinCC: DBW218, RunningPhaseActual)", "DB110.W218"},
        {"mode_auto",  "Modo automatico per il trend (ipotesi: = lampada Auto)", "DB110.W54.B14"},
        {"cyc_cont",   "Ciclo: continuo",   "DB110.W54.B12"},
        {"cyc_susp",   "Ciclo: sospeso",    "DB110.W54.B15"},
        {"cyc_cycle",  "Ciclo: ciclo",      "DB110.W44.B9"},
        // --- posizioni ---
        {"pos_load",     "Posizione carico",       "DB110.W140.B11"},
        {"decel_load",   "Decelerazione carico",   "DB110.W140.B13"},
        {"decel_dis",    "Decelerazione scarico",  "DB110.W140.B12"},
        {"pos_dis",      "Posizione scarico",      "DB110.W140.B14"},
        // --- produzione ---
        {"prod_ready",   "Pronto per produzione",   "DB110.W44.B0"},
        {"prod_auto_on", "Ciclo auto ON",           "DB110.W44.B11"},
        {"new_load",     "Caricamento nuova (ipotesi da PDL)", "DB201.X41.0"},
        {"prod_auto",    "Funzionamento produzione auto", "DB110.W44.B1"},
        {"cycle_stopped","Ciclo arrestato",         "DB110.W44.B2"},
        {"recipe_ok",    "Ricetta valida",          "DB110.W44.B12"},
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
