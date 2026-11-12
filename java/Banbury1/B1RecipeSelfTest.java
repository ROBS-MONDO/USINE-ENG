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

    public static void main(String[] a) throws Exception {
        B1RecipeLayout l = B1RecipeLayout.defaults();
        byte[] db = new byte[l.dbSize];
        for (int[] w : B1DemoSource.DB201_DUMP) {
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
                        && "Déch. (scarico)".equals(l2.opNames.get(15)));
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

        // --- collegamento sinottico <-> archivio ---
        Path dir2 = Files.createTempDirectory("b1bridge");
        B1FileRecipeRepository repo2 = new B1FileRecipeRepository(dir2, l);
        byte[] plc = B1DemoSource.sampleDb201(384);
        check("senza archivio: 'non in archivio'",
                B1RecipeBridge.compare(plc, repo2, l).state == B1RecipeBridge.State.NOT_ARCHIVED);
        B1Recipe arch = B1RecipeCodec.decode(plc, l);
        arch.uid = java.util.UUID.randomUUID().toString();
        arch.revision = 0;
        repo2.save(arch, "test");
        check("uguale all'archivio",
                B1RecipeBridge.compare(plc, repo2, l).state == B1RecipeBridge.State.SAME);
        byte[] resid = plc.clone();
        B1RecipeCodec.putWord(resid, 52 + 3 * 30 + 2, 99); // fase 4 = residuo dopo lo scarico
        check("residui dopo lo scarico ignorati",
                B1RecipeBridge.compare(resid, repo2, l).state == B1RecipeBridge.State.SAME);
        byte[] changed = plc.clone();
        B1RecipeCodec.putWord(changed, 52 + 2, 40); // rpm fase 1: 36 -> 40
        check("fase diversa rilevata",
                B1RecipeBridge.compare(changed, repo2, l).state == B1RecipeBridge.State.DIFFERENT);
        byte[] renamed = plc.clone();
        B1RecipeCodec.writeString(renamed, l.nameOffset, l.nameMax, "ALTRA");
        check("altro nome: non in archivio",
                B1RecipeBridge.compare(renamed, repo2, l).state == B1RecipeBridge.State.NOT_ARCHIVED);
        byte[] none = new byte[384];
        check("DB vuoto: nessuna ricetta",
                B1RecipeBridge.compare(none, repo2, l).state == B1RecipeBridge.State.UNKNOWN);
        System.exit(failures == 0 ? 0 : 1);
    }
}
