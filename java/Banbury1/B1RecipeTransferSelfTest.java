package Banbury1;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test dell'invio ricetta. Senza argomenti prova le parti pure (anteprima, blocchi, scrittura rifiutata se disabilitata).
 * Con "host porta" prova anche la scrittura reale su un server S7 di prova (mai sul PLC della macchina!).
 */
public final class B1RecipeTransferSelfTest {

    private static int failures;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "OK    " : "FALLITO ") + what);
        if (!ok) {
            failures++;
        }
    }

    public static void main(String[] a) throws Exception {
        B1RecipeLayout l = B1RecipeLayout.defaults();
        byte[] cur = new byte[l.dbSize];
        for (int[] w : B1DemoSource.DB201_DUMP) {
            B1RecipeCodec.putWord(cur, w[0], w[1]);
        }
        B1Recipe r = B1RecipeCodec.decode(cur, l);
        byte[] idle = new byte[256];
        B1RecipeCodec.putWord(idle, 44, 0x100A); // a riposo: trasferimento abilitato (bit 12)
        B1RecipeTransfer.Plan same = B1RecipeTransfer.plan(r, l, cur, idle);
        check("ricetta uguale al PLC: nessuna parola da scrivere", same.changes.isEmpty() && same.blockers.isEmpty());

        B1Recipe r2 = r.copy();
        r2.phases.get(0).values.put("rpm", 40);
        r2.header.put("safety_time", r.header.get("safety_time") + 5);
        B1RecipeTransfer.Plan p = B1RecipeTransfer.plan(r2, l, cur, idle);
        check("2 parole cambiate (rpm fase 1, tempo sicurezza)", p.changes.size() == 2 && p.blockers.isEmpty());
        check("descrizioni dei campi", p.changes.stream().anyMatch(c -> c.what.contains("Phase 1"))
                && p.changes.stream().anyMatch(c -> c.what.startsWith("En-t\u00eate")));

        byte[] running = idle.clone();
        B1RecipeCodec.putWord(running, 60, 33);
        B1RecipeTransfer.Plan pr = B1RecipeTransfer.plan(r2, l, cur, running);
        check("rotori in movimento: invio bloccato", !pr.blockers.isEmpty());

        byte[] notEnabled = new byte[256];
        check("PLC che non abilita il trasferimento (DBW44 bit 12 = 0): invio bloccato",
                !B1RecipeTransfer.plan(r2, l, cur, notEnabled).blockers.isEmpty());
        byte[] busy = idle.clone();
        B1RecipeCodec.putWord(busy, 44, 0x1006);
        check("trasferimento gia' in corso (bit 2): invio bloccato", !B1RecipeTransfer.plan(r2, l, cur, busy).blockers.isEmpty());

        B1Recipe longName = r2.copy();
        longName.name = "0123456789012345678901234567890";
        B1RecipeTransfer.Plan pl2 = B1RecipeTransfer.plan(longName, l, cur, idle);
        check("nome troppo lungo (sovrascriverebbe la testata): bloccato", !pl2.blockers.isEmpty());

        B1Recipe bad = r2.copy();
        bad.name = "";
        check("ricetta non valida: invio bloccato", !B1RecipeTransfer.plan(bad, l, cur, idle).blockers.isEmpty());
        check("DB non letto: invio bloccato", !B1RecipeTransfer.plan(r2, l, null, null).blockers.isEmpty());

        B1RecipeTransfer.Config off = new B1RecipeTransfer.Config();
        check("scrittura disabilitata di default", !off.writeAllowed());
        boolean refused = false;
        try {
            B1RecipeTransfer.execute(p, off, "127.0.0.1", 1, l.dbSize);
        } catch (java.io.IOException e) {
            refused = e.getMessage().contains("non activ\u00e9e");
        }
        check("execute rifiuta se disabilitata", refused);
        B1RecipeTransfer.Config half = new B1RecipeTransfer.Config();
        half.enabled = true;
        check("enabled da solo non basta", !half.writeAllowed());

        if (a.length >= 2) {
            String host = a[0];
            int port = Integer.parseInt(a[1]);
            Path bk = Files.createTempDirectory("b1backup");
            B1RecipeTransfer.Config on = new B1RecipeTransfer.Config();
            on.enabled = true;
            on.allowRawWrite = true;
            on.backupDir = bk;
            on.handshake = a.length < 3 || !a[2].equals("raw"); // "raw": senza lo scambio con il PLC (server senza logica)
            on.waitSeconds = 20;
            byte[] live;
            try (S7ReadClient c = new S7ReadClient(host, port, 0, 2, 3000)) {
                c.connect();
                live = c.readDb(201, 0, l.dbSize);
            }
            B1RecipeTransfer.Plan pl = B1RecipeTransfer.plan(r2, l, live, idle);
            Path backup = B1RecipeTransfer.execute(pl, on, host, port, l.dbSize);
            byte[] after;
            try (S7ReadClient c = new S7ReadClient(host, port, 0, 2, 3000)) {
                c.connect();
                after = c.readDb(201, 0, l.dbSize);
            }
            check("scrittura reale su server di prova: DB uguale all'immagine prevista", java.util.Arrays.equals(after, pl.image));
            check("copia di sicurezza = DB prima dell'invio", java.util.Arrays.equals(Files.readAllBytes(backup), live));
            boolean changedMeanwhile = false;
            try {
                B1RecipeTransfer.execute(pl, on, host, port, l.dbSize); // il DB ora e' diverso dall'anteprima
            } catch (java.io.IOException e) {
                changedMeanwhile = e.getMessage().contains("cambiato");
            }
            check("DB cambiato dopo l'anteprima: invio annullato", changedMeanwhile);
        }
        System.exit(failures == 0 ? 0 : 1);
    }
}
