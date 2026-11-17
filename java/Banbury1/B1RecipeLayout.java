package Banbury1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Descrizione del formato ricetta nel DB201 di Banbury 1 (CPU IM 151-8 F).
 *
 * Dal dump: testata di 52 byte (n. ricetta, nome STRING[32] a offset 4, parametri) seguita
 * da 11 fasi da 30 byte (come le 11 righe di 006_Recipes del WinCC). Quasi tutti i nomi
 * dei campi sono IPOTESI: si correggono nel file b1_recipe_layout.properties.
 *
 * Riga campo: chiave = W<off>|UW<off> ; Etichetta ; Unita' ; Scala ; Min ; Max
 *   header.*  -> offset assoluto nel DB      phase.* -> offset dall'inizio del blocco fase
 */
public final class B1RecipeLayout {

    public static final class Field {
        public final String key;
        public final int offset;
        public final boolean unsigned;
        public final String label;
        public final String unit;
        public final double scale;
        public final Double min;
        public final Double max;

        Field(String key, int offset, boolean unsigned, String label, String unit,
              double scale, Double min, Double max) {
            this.key = key;
            this.offset = offset;
            this.unsigned = unsigned;
            this.label = label;
            this.unit = unit;
            this.scale = scale;
            this.min = min;
            this.max = max;
        }

        public String title() {
            return unit == null || unit.isEmpty() ? label : label + " (" + unit + ")";
        }
    }

    public int db = 201;
    public int dbSize = 384;
    public int recipeNoOffset = 2;
    public int nameOffset = 4;
    public int nameMax = 32;
    public int phaseBase = 42;
    public int phaseSize = 30;
    public int phaseCount = 10;
    public int opsOffset = 0;
    public int dischargeBit = 15;

    public final List<Field> header = new ArrayList<>();
    public final List<Field> phase = new ArrayList<>();
    public final Map<Integer, String> opNames = new LinkedHashMap<>();

    private static final String[] DEFAULT_LINES = {
        "header.safety_temp  = W28 ; Temp. de s\u00e9curit\u00e9 ; \u00b0C ; 1 ; 0 ; 200",
        "header.drop_door    = W30 ; Porte de d\u00e9chargement ; s ; 1 ; 0 ; 3600",
        "header.safety_time  = W32 ; Temps de s\u00e9curit\u00e9 ; s ; 1 ; 0 ; 3600",
        "header.max_energy   = W34 ; \u00c9nergie max ; kWh ; 0.1 ; 0 ; 999.9",
        "header.restart_temp = W38 ; Temp. de red\u00e9marrage ; \u00b0C ; 1 ; 0 ; 200",
        // Confermato dal database ricette del WinCC (Q131014: A_RECIPE_PHASE_PARAMS_TAGS) e dai due invii registrati il 16/11/2026:
        // 10 fasi da 30 byte a partire da DBW42; per ogni fase (offset dall'inizio del blocco):
        //   +0 JOBS_word (operazioni, parola a bit)  +2 JOBS_timeout (s)  +10 SETS_word (bit Porte/Piston)
        //   +12 ROTOR1_speed (rpm)  +14 pressure (x10, bar)  +20 CONTROLS_word (et/ou/skip)
        //   +22 PHASE_timout (s)  +24 PHASE_temperature (C)  +26 PHASE_energy (x10)
        "phase.timeout   = W2   ; Timeout ; s ; 1 ; 0 ; 9999",
        "phase.set_bits  = UW10 ; Consigne : porte et piston (bits) ;  ; 1 ; ; ",
        "phase.rpm       = W12  ; Rotor ; rpm ; 1 ; 5 ; 50",
        "phase.press     = W14  ; Press. ; bar ; 0.1 ; 0 ; 500",
        "phase.ctrl_bits = UW20 ; Contr\u00f4le : et=bit0, ou=bit1, saut=bit15 ;  ; 1 ; ; ",
        "phase.time      = W22  ; Temps phase ; s ; 1 ; 0 ; 99999",
        "phase.temp      = W24  ; Temp\u00e9rature ; \u00b0C ; 1 ; 0 ; 600",
        "phase.energy    = W26  ; \u00c9nergie ; kWh ; 0.1 ; 0 ; 1000",
    };

    private B1RecipeLayout() {
    }

    public static B1RecipeLayout defaults() {
        B1RecipeLayout l = new B1RecipeLayout();
        for (String line : DEFAULT_LINES) {
            l.applyLine(line);
        }
        // bit delle operazioni, CONFERMATI dalla schermata 006_Recipes (btnBitJob): 0-6, poi 13-15
        String[] ops = {"Gomme", "Add.", "All Black", "Plastif.", "Cerac.", "Silano", "Half Black"};
        for (int i = 0; i < ops.length; i++) {
            l.opNames.put(i, ops[i]);
        }
        l.opNames.put(13, "Degas");
        l.opNames.put(14, "Net.");
        l.opNames.put(15, "D\u00e9ch. (d\u00e9chargement)");
        return l;
    }

    public static B1RecipeLayout load(Path file) {
        B1RecipeLayout l = new B1RecipeLayout();
        List<String> lines = new ArrayList<>();
        boolean any = false;
        if (file != null && Files.isReadable(file)) {
            try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String s;
                while ((s = r.readLine()) != null) {
                    s = s.trim();
                    if (!s.isEmpty() && !s.startsWith("#")) {
                        lines.add(s);
                        any = true;
                    }
                }
            } catch (IOException e) {
                System.err.println("layout: " + e.getMessage());
            }
        }
        if (!any) {
            return defaults();
        }
        for (String s : lines) {
            l.applyLine(s);
        }
        return l;
    }

    private void applyLine(String line) {
        int eq = line.indexOf('=');
        if (eq < 0) {
            return;
        }
        String k = line.substring(0, eq).trim();
        String v = line.substring(eq + 1).trim();
        int hash = v.indexOf(" #");
        if (hash >= 0) {
            v = v.substring(0, hash).trim();
        }
        try {
            switch (k) {
                case "db": db = Integer.parseInt(v); return;
                case "db.size": dbSize = Integer.parseInt(v); return;
                case "recipeno.offset": recipeNoOffset = Integer.parseInt(v); return;
                case "name.offset": nameOffset = Integer.parseInt(v); return;
                case "name.max": nameMax = Integer.parseInt(v); return;
                case "phase.base": phaseBase = Integer.parseInt(v); return;
                case "phase.size": phaseSize = Integer.parseInt(v); return;
                case "phase.count": phaseCount = Integer.parseInt(v); return;
                case "ops.offset": opsOffset = Integer.parseInt(v); return;
                case "discharge.bit": dischargeBit = Integer.parseInt(v); return;
                default:
            }
            if (k.startsWith("ops.bit")) {
                opNames.put(Integer.parseInt(k.substring(7)), v);
                return;
            }
            boolean hdr = k.startsWith("header.");
            if (hdr || k.startsWith("phase.")) {
                String[] p = v.split(";", -1);
                String spec = p[0].trim();
                boolean uns = spec.startsWith("U");
                int off = Integer.parseInt(spec.replaceAll("[^0-9]", ""));
                String key = k.substring(k.indexOf('.') + 1);
                Field f = new Field(key, off, uns, p.length > 1 ? p[1].trim() : key,
                        p.length > 2 ? p[2].trim() : "", p.length > 3 && !p[3].trim().isEmpty()
                        ? Double.parseDouble(p[3].trim()) : 1.0,
                        p.length > 4 && !p[4].trim().isEmpty() ? Double.valueOf(p[4].trim()) : null,
                        p.length > 5 && !p[5].trim().isEmpty() ? Double.valueOf(p[5].trim()) : null);
                List<Field> list = hdr ? header : phase;
                list.removeIf(x -> x.key.equals(key));
                list.add(f);
            }
        } catch (RuntimeException e) {
            System.err.println("layout: riga non valida: " + line);
        }
    }

    public Field phaseField(String key) {
        for (Field f : phase) {
            if (f.key.equals(key)) {
                return f;
            }
        }
        return null;
    }

    public void writeTemplate(Path out) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            w.println("# Layout ricetta Banbury 1 (DB201), CONFERMATO dal database ricette del WinCC (tabelle A_RECIPE_PHASE_PARAMS/_TAGS).");
            w.println("# 10 fasi da 30 byte a partire da DBW42. Per ogni fase: +0 operazioni, +2 timeout, +10 set, +12 rotor, +14 pressione (x10), +20 controllo, +22 tempo, +24 temp., +26 energia (x10).");
            w.println("# Operazioni (bit della parola a offset 0 del blocco fase): CONFERMATE dal WinCC (btnBitJob): 0-6, 13, 14, 15 = Dech.");
        w.println("# Controlli (btnBitControl): et = bit 0, ou = bit 1, Skip = bit 15 (parola a offset 20, CONTROLS_word).");
        w.println("# Etichette confermate dal WinCC (002_Process): safety_temp, drop_door, safety_time, max_energy, restart_temp.");
            w.println("# Colonne WinCC (006_Recipes): Operazione = Gomme, Add., All Black, Plastif., Cerac., Silano, Half Black, Degas, Net., Dech.;");
            w.println("#   Set = Timeout, Rotor (rpm), Press. (bar), Porte, Piston; Controllo = Skip, Temps (s), Temp. (C), Ener. (kWh), et, ou.");
            w.println("# Campo: chiave = W<offset>|UW<offset> ; Etichetta ; Unita' ; Scala ; Min ; Max");
            w.println("# header.* = offset assoluto nel DB; phase.* = offset dall'inizio del blocco fase.");
            w.println("db = " + db);
            w.println("db.size = " + dbSize);
            w.println("recipeno.offset = " + recipeNoOffset);
            w.println("name.offset = " + nameOffset + "   # STRING S7 [max][len][caratteri]");
            w.println("name.max = " + nameMax);
            w.println("phase.base = " + phaseBase);
            w.println("phase.size = " + phaseSize);
            w.println("phase.count = " + phaseCount);
            w.println("ops.offset = " + opsOffset + "   # parola a bit delle operazioni (offset nel blocco fase)");
            w.println("discharge.bit = " + dischargeBit + "   # bit 'scarico' (ipotesi)");
            for (Map.Entry<Integer, String> e : opNames.entrySet()) {
                w.println("ops.bit" + e.getKey() + " = " + e.getValue());
            }
            w.println("# ops.bit12 = nome operazione   (aggiungere le righe che servono)");
            for (Field f : header) {
                w.println("header." + f.key + " = " + spec(f));
            }
            for (Field f : phase) {
                w.println("phase." + f.key + " = " + spec(f));
            }
        }
    }

    private static String spec(Field f) {
        return (f.unsigned ? "UW" : "W") + f.offset + " ; " + f.label + " ; " + f.unit + " ; " + f.scale
                + " ; " + (f.min == null ? "" : f.min) + " ; " + (f.max == null ? "" : f.max);
    }
}
