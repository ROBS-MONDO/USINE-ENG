package Banbury1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Allarmi di Banbury 1 in stile WinCC Alarm Logging (messaggi "a bit"): ogni allarme e' un bit del PLC
 * con numero, "Point d'erreur" (Text1) e "Texte du message" (Text2). Il motore legge i bit (B1Source.raw),
 * rileva arrivo (come) e rientro (go), tiene l'elenco in memoria e lo registra su CSV.
 *
 * Le DEFINIZIONI (numeri, testi, bit) non sono nel progetto WinCC fornito (stanno nel database di
 * configurazione di Alarm Logging): si compilano in b1_alarms.properties.
 *   alarm.<numero> = DB110DBX22_3 ; Point d'erreur ; Testo del messaggio ; classe
 * (indirizzo come nel WinCC: DB110DBX22_3 = DBX 22.3; si accetta anche DB110.W22.B3)
 */
public final class B1AlarmEngine {

    public static final class Def {
        public final int number;
        public final String address;
        public final String point;
        public final String text;
        public final String cls;

        Def(int number, String address, String point, String text, String cls) {
            this.number = number;
            this.address = address;
            this.point = point;
            this.text = text;
            this.cls = cls;
        }
    }

    public static final class Event {
        public final Def def;
        public final LocalDateTime came;
        public volatile LocalDateTime went;

        Event(Def def, LocalDateTime came) {
            this.def = def;
            this.came = came;
        }

        public boolean active() {
            return went == null;
        }
    }

    private static final Pattern WINCC = Pattern.compile("^DB(\\d+)DBX(\\d+)_([0-7])$");
    private static final Pattern DOTTED = Pattern.compile("^DB(\\d+)\\.W(\\d+)\\.B(\\d+)$");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_EVENTS = 500;

    private final List<Def> defs = new ArrayList<>();
    private final Map<Integer, Event> activeByNumber = new LinkedHashMap<>();
    private final List<Event> events = new ArrayList<>();
    private final Path csv;
    private boolean primed;

    public B1AlarmEngine(List<Def> defs, Path csv) {
        this.defs.addAll(defs);
        this.csv = csv;
    }

    /** Legge b1_alarms.properties (assente = nessun allarme definito). */
    public static B1AlarmEngine load(Path file, Path csv) {
        List<Def> defs = new ArrayList<>();
        if (file != null && Files.isReadable(file)) {
            try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    Def d = parseLine(line);
                    if (d != null) {
                        defs.add(d);
                    } else {
                        System.err.println("b1_alarms: riga non valida: " + line);
                    }
                }
            } catch (IOException e) {
                System.err.println("b1_alarms: " + e.getMessage());
            }
        }
        return new B1AlarmEngine(defs, csv);
    }

    static Def parseLine(String line) {
        int eq = line.indexOf('=');
        if (eq < 0 || !line.substring(0, eq).trim().startsWith("alarm.")) {
            return null;
        }
        try {
            int number = Integer.parseInt(line.substring(0, eq).trim().substring(6));
            String[] p = line.substring(eq + 1).split(";", -1);
            String addr = normalizeAddress(p[0].trim());
            if (addr == null) {
                return null;
            }
            return new Def(number, addr, p.length > 1 ? p[1].trim() : "", p.length > 2 ? p[2].trim() : "",
                    p.length > 3 ? p[3].trim() : "");
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Converte nell'indirizzo WinCC DBnDBXb_i; null se non valido. */
    static String normalizeAddress(String a) {
        Matcher w = WINCC.matcher(a);
        if (w.matches()) {
            return a;
        }
        Matcher d = DOTTED.matcher(a);
        if (d.matches()) {
            int bit = Integer.parseInt(d.group(3));
            if (bit > 15) {
                return null;
            }
            int off = Integer.parseInt(d.group(2)) + (bit >= 8 ? 0 : 1); // DBW big-endian
            return "DB" + d.group(1) + "DBX" + off + "_" + (bit % 8);
        }
        return null;
    }

    public List<Def> definitions() {
        return defs;
    }

    /** DB da leggere dal PLC per gli allarmi. */
    public Set<Integer> dbs() {
        Set<Integer> s = new TreeSet<>();
        for (Def d : defs) {
            Matcher m = WINCC.matcher(d.address);
            if (m.matches()) {
                s.add(Integer.parseInt(m.group(1)));
            }
        }
        return s;
    }

    /**
     * Un ciclo di lettura. Al primo ciclo con PLC connesso lo stato attuale e' preso come base
     * (nessun allarme "nuovo" per bit gia' attivi: vengono mostrati come attivi dall'ora corrente).
     */
    public synchronized void poll(B1Source src, LocalDateTime now) {
        if (!src.connected()) {
            return;
        }
        for (Def d : defs) {
            Double v = src.raw(d.address);
            if (v == null) {
                continue;
            }
            boolean on = v != 0.0;
            Event act = activeByNumber.get(d.number);
            if (on && act == null) {
                Event e = new Event(d, now);
                activeByNumber.put(d.number, e);
                events.add(0, e);
                if (events.size() > MAX_EVENTS) {
                    events.remove(events.size() - 1);
                }
                log(e, "COME");
            } else if (!on && act != null) {
                act.went = now;
                activeByNumber.remove(d.number);
                log(act, "GO");
            }
        }
        primed = true;
    }

    public synchronized List<Event> events() {
        return new ArrayList<>(events);
    }

    public synchronized int activeCount() {
        return activeByNumber.size();
    }

    public boolean primed() {
        return primed;
    }

    private void log(Event e, String what) {
        if (csv == null) {
            return;
        }
        try {
            if (csv.getParent() != null) {
                Files.createDirectories(csv.getParent());
            }
            boolean header = !Files.exists(csv);
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
                if (header) {
                    w.println("evento;data_ora;numero;point_derreur;testo;classe;indirizzo");
                }
                LocalDateTime t = "GO".equals(what) ? e.went : e.came;
                w.println(what + ";" + t.format(TS) + ";" + e.def.number + ";" + e.def.point + ";" + e.def.text
                        + ";" + e.def.cls + ";" + e.def.address);
            }
        } catch (IOException ignored) {
            // il log e' accessorio
        }
    }

    public static String template() {
        return "# Allarmi di Banbury 1 (messaggi a bit come nel WinCC Alarm Logging).\n"
                + "# alarm.<numero> = <indirizzo bit> ; <Point d'erreur (Text1)> ; <Testo messaggio (Text2)> ; <classe>\n"
                + "# Indirizzo: DB110DBX22_3 (= DBX 22.3, formato WinCC) oppure DB110.W22.B3 (bit 3 della parola DBW22).\n"
                + "# Gli allarmi di B1 NON sono nel progetto WinCC fornito: servono l'elenco messaggi di Alarm Logging\n"
                + "# (numero, testo, tag/bit) o, in alternativa, gli screenshot dell'elenco.\n"
                + "#\n"
                + "# esempio (NON reale, da cancellare):\n"
                + "# alarm.1 = DB110DBX22_3 ; Pistone ; Pistone bloccato ; Errore\n";
    }
}
