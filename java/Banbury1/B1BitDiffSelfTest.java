package Banbury1;

import java.util.List;

/** Test del confronto bit (java Banbury1.B1BitDiffSelfTest). */
public final class B1BitDiffSelfTest {

    private static int failures;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "OK    " : "FALLITO ") + what);
        if (!ok) {
            failures++;
        }
    }

    public static void main(String[] args) {
        byte[] a = new byte[16];
        byte[] b = new byte[16];
        b[6] = 0x00;
        b[7] = 0x22;                  // DBW6: 0x0000 -> 0x0022 (bit 1 e 5 accesi)
        a[10] = 0x0C;                 // DBW10 = 0x0C00 (bit 10, 11)
        b[10] = 0x04;                 // DBW10 = 0x0400 (bit 11 spento)
        List<B1BitDiff.Change> d = B1BitDiff.diff(110, a, b);
        check("due parole cambiate", d.size() == 2);
        B1BitDiff.Change c0 = d.get(0);
        check("DBW6: bit 1 e 5 accesi", c0.off == 6 && c0.setBits.size() == 2 && c0.setBits.contains(1)
                && c0.setBits.contains(5) && c0.clearedBits.isEmpty());
        B1BitDiff.Change c1 = d.get(1);
        check("DBW10: bit 11 spento", c1.off == 10 && c1.clearedBits.size() == 1 && c1.clearedBits.contains(11)
                && c1.setBits.isEmpty());
        check("descrizione leggibile", c0.describe().contains("DB110.DBW6") && c0.describe().contains("acceso: bit1, bit5"));
        check("nessuna differenza = lista vuota", B1BitDiff.diff(110, a, a.clone()).isEmpty());
        check("lunghezze diverse: confronta la parte comune", B1BitDiff.diff(110, a, new byte[8]).isEmpty()
                || B1BitDiff.diff(110, a, new byte[8]).size() <= 4);
        check("valore nullo: nessun errore", B1BitDiff.diff(110, null, b).isEmpty());
        System.exit(failures == 0 ? 0 : 1);
    }
}
