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

        System.exit(failures == 0 ? 0 : 1);
    }
}
