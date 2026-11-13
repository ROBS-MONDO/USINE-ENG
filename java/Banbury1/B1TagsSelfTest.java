package Banbury1;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Test della mappa tag (java Banbury1.B1TagsSelfTest). */
public final class B1TagsSelfTest {

    private static int failures;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "OK    " : "FALLITO ") + what);
        if (!ok) {
            failures++;
        }
    }

    public static void main(String[] args) throws Exception {
        B1Tags def = B1Tags.load(null);
        check("predefinito: pressione = DB110.W70*0.1",
                def.get("press_set") != null && def.get("press_set").db == 110 && def.get("press_set").off == 70
                        && def.get("press_set").scale == 0.1);
        check("predefinito: miscele fatte = DBW212, passi = DBW234",
                def.get("mix_done").off == 212 && def.get("steps").off == 234);
        check("predefinito: SEQ in DB150", def.get("seq_a").db == 150 && def.get("seq_b").off == 2);

        Path f = Files.createTempFile("b1tags", ".properties");
        Files.write(f, ("# prova\n"
                + "press_set =\n"                       // vuota: deve restare il predefinito
                + "temp = DB110.W104\n"                 // sovrascrive
                + "rpm_act = none\n"                    // volutamente non collegato
                + "current = -\n"                       // idem
                + "mix_done = DB110.W999\n"             // sovrascrive
                + "steps = qualcosa_di_sbagliato\n"     // non valido: non collegato
                ).getBytes(StandardCharsets.UTF_8));
        B1Tags t = B1Tags.load(f);
        Files.delete(f);
        check("riga vuota nel file: resta l'indirizzo predefinito (vecchio file di esempio)",
                t.get("press_set") != null && t.get("press_set").off == 70);
        check("il file puo' sovrascrivere un indirizzo", t.get("temp").off == 104 && t.get("mix_done").off == 999);
        check("'none' e '-' scollegano il campo", t.get("rpm_act") == null && t.get("current") == null);
        check("indirizzo non valido: campo non collegato", t.get("steps") == null);
        check("le altre righe restano predefinite", t.get("rpm_set").off == 58);
        System.exit(failures == 0 ? 0 : 1);
    }
}
