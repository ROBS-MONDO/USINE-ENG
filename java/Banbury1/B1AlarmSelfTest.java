package Banbury1;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** Test del motore allarmi (java Banbury1.B1AlarmSelfTest). */
public final class B1AlarmSelfTest {

    private static int failures;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "OK    " : "FALLITO ") + what);
        if (!ok) {
            failures++;
        }
    }

    /** Sorgente finta: valori di bit impostati dal test. */
    private static final class FakeSource implements B1Source {
        final Map<String, Double> vals = new HashMap<>();
        boolean connected = true;

        public Double num(String key) { return null; }
        public Boolean bool(String key) { return null; }
        public String str(String key) { return null; }
        public String spec(String key) { return "fake"; }
        public Double raw(String tag) { return vals.get(tag); }
        public boolean connected() { return connected; }
        public String status() { return "fake"; }
        public void close() { }
    }

    public static void main(String[] args) {
        check("indirizzo WinCC valido", "DB110DBX22_3".equals(B1AlarmEngine.normalizeAddress("DB110DBX22_3")));
        check("DB110.W22.B3 -> DBX23_3 (parola big-endian)", "DB110DBX23_3".equals(B1AlarmEngine.normalizeAddress("DB110.W22.B3")));
        check("DB110.W22.B12 -> DBX22_4", "DB110DBX22_4".equals(B1AlarmEngine.normalizeAddress("DB110.W22.B12")));
        check("indirizzo non valido rifiutato", B1AlarmEngine.normalizeAddress("pippo") == null);

        B1AlarmEngine.Def d = B1AlarmEngine.parseLine("alarm.7 = DB110DBX22_3 ; Pistone ; Pistone bloccato ; Errore");
        check("riga di definizione", d != null && d.number == 7 && "Pistone".equals(d.point)
                && "Pistone bloccato".equals(d.text) && "Errore".equals(d.cls));
        check("riga senza 'alarm.' scartata", B1AlarmEngine.parseLine("pippo = DB110DBX22_3") == null);

        B1AlarmEngine eng = new B1AlarmEngine(java.util.Arrays.asList(
                B1AlarmEngine.parseLine("alarm.1 = DB110DBX22_3 ; A ; Allarme uno ; E"),
                B1AlarmEngine.parseLine("alarm.2 = DB110DBX24_0 ; B ; Allarme due ; E")), null);
        FakeSource s = new FakeSource();
        LocalDateTime t0 = LocalDateTime.of(2026, 11, 12, 10, 0, 0);

        s.connected = false;
        eng.poll(s, t0);
        check("PLC non connesso: nessun evento", eng.events().isEmpty() && !eng.primed());

        s.connected = true;
        s.vals.put("DB110DBX22_3", 0.0);
        s.vals.put("DB110DBX24_0", 0.0);
        eng.poll(s, t0);
        check("tutto a riposo: nessun allarme", eng.events().isEmpty() && eng.primed());

        s.vals.put("DB110DBX22_3", 1.0);
        eng.poll(s, t0.plusSeconds(5));
        check("arrivo allarme 1", eng.events().size() == 1 && eng.activeCount() == 1
                && eng.events().get(0).active() && eng.events().get(0).def.number == 1);

        eng.poll(s, t0.plusSeconds(6));
        check("lo stesso allarme non si duplica", eng.events().size() == 1);

        s.vals.put("DB110DBX24_0", 1.0);
        eng.poll(s, t0.plusSeconds(10));
        check("secondo allarme in testa all'elenco", eng.events().size() == 2 && eng.events().get(0).def.number == 2
                && eng.activeCount() == 2);

        s.vals.put("DB110DBX22_3", 0.0);
        eng.poll(s, t0.plusSeconds(20));
        B1AlarmEngine.Event e1 = null;
        for (B1AlarmEngine.Event e : eng.events()) {
            if (e.def.number == 1) {
                e1 = e;
            }
        }
        check("rientro allarme 1 con ora", e1 != null && !e1.active() && e1.went.equals(t0.plusSeconds(20))
                && eng.activeCount() == 1);

        s.vals.remove("DB110DBX24_0");
        eng.poll(s, t0.plusSeconds(30));
        check("valore non leggibile: lo stato non cambia", eng.activeCount() == 1);
        check("DB da leggere", eng.dbs().size() == 1 && eng.dbs().contains(110));

        persistence();
        System.exit(failures == 0 ? 0 : 1);
    }

    /** Gli ultimi 10 allarmi sopravvivono al riavvio (riletti dal CSV). */
    private static void persistence() {
        try {
            java.nio.file.Path csv = java.nio.file.Files.createTempDirectory("b1alg").resolve("log.csv");
            java.util.List<B1AlarmEngine.Def> defs = new java.util.ArrayList<>();
            for (int i = 1; i <= 12; i++) {
                defs.add(B1AlarmEngine.parseLine("alarm." + i + " = DB110DBX" + (30 + i) + "_0 ; P" + i + " ; Testo " + i + " ; Errore"));
            }
            LocalDateTime t0 = LocalDateTime.of(2026, 11, 17, 8, 0, 0);
            FakeSource s = new FakeSource();
            for (int i = 1; i <= 12; i++) {
                s.vals.put("DB110DBX" + (30 + i) + "_0", 0.0);
            }
            B1AlarmEngine a = new B1AlarmEngine(defs, csv);
            a.poll(s, t0);
            for (int i = 1; i <= 12; i++) { // 12 allarmi arrivano e rientrano uno dopo l'altro, l'ultimo resta attivo
                s.vals.put("DB110DBX" + (30 + i) + "_0", 1.0);
                a.poll(s, t0.plusMinutes(i));
                if (i < 12) {
                    s.vals.put("DB110DBX" + (30 + i) + "_0", 0.0);
                    a.poll(s, t0.plusMinutes(i).plusSeconds(30));
                }
            }
            check("al massimo 10 allarmi in lista", a.events().size() == 10);

            B1AlarmEngine b = new B1AlarmEngine(defs, csv); // riavvio di B1
            check("riavvio: ricordati gli ultimi 10", b.events().size() == 10 && b.events().get(0).def.number == 12
                    && b.events().get(9).def.number == 3);
            check("riavvio: i rientrati hanno l'ora di rientro", !b.events().get(1).active()
                    && b.events().get(1).went.equals(t0.plusMinutes(11).plusSeconds(30)));
            check("riavvio: l'ultimo risulta ancora attivo", b.events().get(0).active());
            long lines = java.nio.file.Files.readAllLines(csv).size();
            b.poll(s, t0.plusHours(1)); // il bit 12 e' ancora attivo: stesso evento, niente nuovo arrivo
            check("riavvio: allarme ancora attivo = stesso evento", b.events().size() == 10 && b.activeCount() == 1
                    && b.events().get(0).came.equals(t0.plusMinutes(12))
                    && java.nio.file.Files.readAllLines(csv).size() == lines);

            B1AlarmEngine c = new B1AlarmEngine(defs, csv);
            s.vals.put("DB110DBX42_0", 0.0); // rientrato mentre B1 era chiuso
            c.poll(s, t0.plusHours(2));
            check("riavvio: rientrato a B1 chiuso, chiuso al primo ciclo", !c.events().get(0).active()
                    && c.events().get(0).went.equals(t0.plusHours(2)) && c.activeCount() == 0);
        } catch (java.io.IOException e) {
            check("persistenza: " + e, false);
        }
    }
}
