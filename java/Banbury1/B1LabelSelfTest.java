package Banbury1;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/** Test della lettura ticket (java Banbury1.B1LabelSelfTest): esce con codice 1 se qualcosa fallisce. */
public final class B1LabelSelfTest {

    private static int failures;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "OK    " : "FALLITO ") + what);
        if (!ok) {
            failures++;
        }
    }

    private static String base32(String s) {
        String a = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder out = new StringBuilder("MQR2");
        int buffer = 0;
        int bits = 0;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                out.append(a.charAt((buffer >> bits) & 31));
            }
        }
        if (bits > 0) {
            out.append(a.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    private static boolean fails(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException | IllegalStateException e) {
            return true;
        }
    }

    public static void main(String[] args) {
        final List<String> y26 = Arrays.asList("26");
        final String uid = "123e4567-e89b-12d3-a456-426614174000";

        B1Label a = B1LabelParser.parse("26/0366;1;;4;1ER;SUP;CLAIR;RECIPEUID=" + uid, y26);
        check("ticket semplice: lotto 26/0366, suffisso 1, 4 miscele",
                "26".equals(a.annee) && "0366".equals(a.lot) && "1".equals(a.suffixe) && a.nombreMelanges == 4);
        check("ticket semplice: 1er passaggio, SUP, CLAIR, UID",
                a.passage == 1 && a.couche == 1 && a.couleur == 2 && uid.equals(a.recipeUid));
        check("ticket 1 = primo passaggio", a.ticketKind().startsWith("Ticket 1"));

        String payload = "ROMAN SOLID;26/0410;1;A;12;;;2EME;INF;FONCE;RECIPEUID=" + uid;
        B1Label b = B1LabelParser.parse(base32(payload), y26);
        check("QR MQR2: nome, lotto, serie A, 12 miscele",
                "ROMAN SOLID".equals(b.recipeName) && "0410".equals(b.lot) && "A".equals(b.serieMelange)
                        && b.nombreMelanges == 12);
        check("QR MQR2: 2eme passaggio, INF, FONCE", b.passage == 2 && b.couche == 2 && b.couleur == 3);
        check("ticket 2 = secondo passaggio", b.ticketKind().startsWith("Ticket 2"));
        check("testi SQL come Datas_B1 (2\u00e8me, INF, Fonc\u00e9)",
                "2\u00e8me".equals(b.passageSql()) && "INF".equals(b.coucheSql()) && "Fonc\u00e9".equals(b.couleurSql()));

        B1Label c = B1LabelParser.parse("RFL8 V05 +14%*;26/0394L1;1;A1;3;;;1ER;SUP;;RECIPEUID=" + uid, y26);
        check("lotto con lettere (0394L1) e nome con % e *",
                "0394L1".equals(c.lot) && "A1".equals(c.serieMelange) && c.recipeName.endsWith("14%*"));

        B1Label d = B1LabelParser.parseBarcodeRecipe("26/0366M1MM4M1ERMSUPMCLQIR", y26);
        check("lettore AZERTY: 26/0366M1MM4M1ERMSUPMCLQIR",
                "0366".equals(d.lot) && d.nombreMelanges == 4 && d.passage == 1 && d.couche == 1 && d.couleur == 2);

        // Come in B2 la correzione AZERTY scambia A<->Q anche nel nome (il nome vero arriva poi dal RecipeUID).
        String compact = B1LabelParser.rebuildCompactTicketBarcode("ROMANSOLID*260410171ERINF", y26);
        check("ticket compatto ricostruito (lotto 0410, suffisso 1, serie 7, 1er, INF)",
                compact.startsWith("ROM") && compact.endsWith(";26/0410;1;7;1;;;1ER;INF;"));

        B1Label e = B1LabelParser.parseBarcodeRecipe("ROMAN SOLID;26/0410;1;A;5;;;3EME;PREMAST;MEDIUM", y26);
        check("PREMAST: nessuna couche; 3eme; MEDIUM", e.premast && e.couche == null && e.passage == 3 && e.couleur == 4);

        check("anno diverso rifiutato (solo 26 accettato)",
                fails(() -> B1LabelParser.parse(base32("X;27/0001;1;;1;;;1ER;SUP;;RECIPEUID=" + uid), y26)));
        B1Label f = B1LabelParser.parse(base32("X;27/0001;1;;2;;;1ER;SUP;;RECIPEUID=" + uid),
                Arrays.asList("27", "26"));
        check("anno corrente+precedente accettati (27/0001)", "27".equals(f.annee) && "0001".equals(f.lot));
        check("senza RecipeUID rifiutato", fails(() -> B1LabelParser.parse("26/0366;1;;4;1ER;SUP;CLAIR", y26)));
        check("MQR2 con caratteri non validi rifiutato", fails(() -> B1LabelParser.parse("MQR2AB1CD", y26)));

        // --- SQL ---
        Properties p = new Properties();
        p.setProperty("table", "dbo.Datas_B1");
        p.setProperty("col.num_lot", "Num_lot");
        p.setProperty("col.suffixe", "Suffixe");
        p.setProperty("col.serie", "Num_melange");
        p.setProperty("col.couche", "Type_melange");
        p.setProperty("col.passage", "Num_passage");
        p.setProperty("col.couleur", "Couleur");
        B1SqlLabelStore st = B1SqlLabelStore.fromProperties(p);
        check("senza enabled resta in prova (nessuna scrittura)", st.dryRun());
        List<Object> params = new ArrayList<>();
        String sql = st.buildSql(b, LocalDateTime.now(), params);
        check("INSERT parametrico", ("INSERT INTO dbo.Datas_B1 (Num_lot, Suffixe, Num_melange, Num_passage, "
                + "Type_melange, Couleur) VALUES (?, ?, ?, ?, ?, ?)").equals(sql));
        check("parametri: 26/0410, 1, A, 2\u00e8me, INF, Fonc\u00e9",
                params.equals(Arrays.<Object>asList("26/0410", "1", "A", "2\u00e8me", "INF", "Fonc\u00e9")));
        check("store() rifiutato in modalita' prova", fails(() -> {
            try {
                st.store(b);
            } catch (IllegalStateException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalArgumentException(ex);
            }
        }));
        Properties bad = new Properties(p);
        bad.setProperty("table", "dbo.Datas_B1; DROP TABLE x");
        check("nome tabella con ';' rifiutato", fails(() -> B1SqlLabelStore.fromProperties(bad)));
        Properties upd = new Properties();
        upd.putAll(p);
        upd.setProperty("mode", "update");
        upd.setProperty("enabled", "true");
        check("UPDATE senza chiave rifiutato", fails(() -> B1SqlLabelStore.fromProperties(upd)));
        upd.setProperty("key.column", "Id");
        upd.setProperty("key.field", "recipe_uid");
        List<Object> pu = new ArrayList<>();
        String su = B1SqlLabelStore.fromProperties(upd).buildSql(b, LocalDateTime.now(), pu);
        check("UPDATE con chiave", su.startsWith("UPDATE dbo.Datas_B1 SET Num_lot = ?") && su.endsWith("WHERE Id = ?")
                && pu.size() == 7);

        System.exit(failures == 0 ? 0 : 1);
    }
}
