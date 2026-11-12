package Banbury1;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Test senza framework: java Banbury1.B1RecipeSelfTest (esce con codice 1 se fallisce). */
public final class B1RecipeSelfTest {

    private static int failures;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "OK    " : "FALLITO ") + what);
        if (!ok) {
            failures++;
        }
    }

    /** Parole non nulle del DB201 reale (dump del 12/11/2026, ricetta "gomma2"). */
    private static final int[][] DB201_DUMP = {
        {2, 24}, {4, 8198}, {6, 26479}, {8, 28013}, {10, 24882}, {12, 25441}, {14, 29289}, {16, 25455},
        {18, 28528}, {20, 29042}, {28, 115}, {30, 80}, {32, 600}, {34, 300}, {38, 80}, {42, 1}, {44, 60},
        {52, 18}, {54, 36}, {56, 900}, {62, 1}, {66, 80}, {72, 12288}, {74, 2},
        {82, 18}, {84, 33}, {86, 900}, {92, 1}, {96, 90}, {102, 12288}, {104, 2},
        {112, 18}, {114, 33}, {116, 700}, {122, 1}, {126, 105}, {132, -32768}, {134, 10},
        {144, 33}, {152, 8},
    };

    public static void main(String[] a) throws Exception {
        B1RecipeLayout l = B1RecipeLayout.defaults();
        byte[] db = new byte[l.dbSize];
        for (int[] w : DB201_DUMP) {
            B1RecipeCodec.putWord(db, w[0], w[1]);
        }
        Path lf = Files.createTempFile("b1layout", ".properties");
        l.writeTemplate(lf);
        B1RecipeLayout l2 = B1RecipeLayout.load(lf);
        check("layout riletto dal file = predefinito",
                l2.phaseBase == 52 && l2.phaseSize == 30 && l2.phaseCount == 11 && l2.nameOffset == 4
                        && l2.opsOffset == 20 && l2.dischargeBit == 15 && l2.db == 201
                        && l2.header.size() == l.header.size() && l2.phase.size() == l.phase.size()
                        && l2.phaseField("rpm") != null && l2.phaseField("rpm").offset == 2
                        && "Scarico (?)".equals(l2.opNames.get(15)));
        Files.delete(lf);
        B1Recipe r = B1RecipeCodec.decode(db, l);
        check("nome ricetta = gomma2", "gomma2".equals(r.name));
        check("n. ricetta = 24", r.recipeNo == 24);
        check("fase 1: rpm 36, temp 80, ops 0x3000",
                r.phases.get(0).values.get("rpm") == 36 && r.phases.get(0).values.get("temp") == 80
                        && r.phases.get(0).ops == 0x3000);
        check("fase 3: temp 105, ops 0x8000 (scarico)",
                r.phases.get(2).values.get("temp") == 105 && r.phases.get(2).ops == 0x8000);
        check("validazione ricetta reale: nessun errore", B1RecipeCodec.validate(r, l).isEmpty());

        byte[] again = B1RecipeCodec.encode(r, l, db);
        boolean same = true;
        for (int i = 0; i < 52 + 11 * 30; i++) {
            if (i >= 6 && i < 38) {
                continue; // area nome: i residui oltre la lunghezza vengono azzerati
            }
            same &= again[i] == db[i];
        }
        check("codifica = byte originali (fuori dall'area nome)", same);
        check("nome riletto dopo codifica", "gomma2".equals(B1RecipeCodec.decode(again, l).name));

        B1Recipe bad = r.copy();
        bad.name = "";
        bad.phases.get(2).ops = 0x1000;
        List<String> e = B1RecipeCodec.validate(bad, l);
        System.out.println("      messaggi: " + e);
        check("nome vuoto segnalato", e.stream().anyMatch(m -> m.startsWith("Nome ricetta")));
        check("assenza fase di scarico segnalata", e.stream().anyMatch(m -> m.contains("scarico")));

        Path dir = Files.createTempDirectory("b1rec");
        B1FileRecipeRepository repo = new B1FileRecipeRepository(dir, l);
        r.uid = java.util.UUID.randomUUID().toString();
        repo.save(r, "test");
        check("salvataggio file e rev 1", r.revision == 1 && repo.list().size() == 1);
        B1Recipe back = repo.load(r.uid);
        check("rilettura da file uguale", back.name.equals("gomma2")
                && back.phases.get(1).values.get("rpm") == 33 && back.phases.get(2).ops == 0x8000);
        B1Recipe other = repo.load(r.uid);
        repo.save(back, "utente A");
        boolean conflict = false;
        try {
            repo.save(other, "utente B");
        } catch (B1RecipeRepository.ConflictException ex) {
            conflict = true;
        }
        check("modifica concorrente rilevata", conflict);
        repo.delete(r.uid);
        check("cancellazione", repo.list().isEmpty());

        System.exit(failures == 0 ? 0 : 1);
    }
}
