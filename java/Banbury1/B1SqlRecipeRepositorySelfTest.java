package Banbury1;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

/** Prova l'archivio SQL con H2 (modalita' SQL Server): java -cp ...;h2.jar Banbury1.B1SqlRecipeRepositorySelfTest */
public final class B1SqlRecipeRepositorySelfTest {

    private static int failures;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "OK    " : "FALLITO ") + what);
        if (!ok) {
            failures++;
        }
    }

    public static void main(String[] a) throws Exception {
        String url = "jdbc:h2:mem:b1recipes;MODE=MSSQLServer;IGNORECASE=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS dbo");
            st.execute("CREATE TABLE dbo.B1Recipes (Id INT IDENTITY(1,1) PRIMARY KEY, RecipeUID NVARCHAR(36) NOT NULL UNIQUE, "
                    + "Nome NVARCHAR(32) NOT NULL UNIQUE, NumRicetta INT NOT NULL DEFAULT 0, Revisione BIGINT NOT NULL, "
                    + "Creazione TIMESTAMP NOT NULL, Modifica TIMESTAMP NOT NULL, ModificatoDa NVARCHAR(100) NOT NULL DEFAULT '')");
            st.execute("CREATE TABLE dbo.B1RecipeValues (RecipeId INT NOT NULL REFERENCES dbo.B1Recipes(Id), Fase SMALLINT NOT NULL, "
                    + "Chiave NVARCHAR(40) NOT NULL, Valore INT NOT NULL, PRIMARY KEY (RecipeId, Fase, Chiave))");
        }
        B1RecipeLayout l = B1RecipeLayout.defaults();
        B1SqlRecipeRepository repo = new B1SqlRecipeRepository(() -> DriverManager.getConnection(url), l, "H2 di prova");
        B1SqlRecipeRepository altra = new B1SqlRecipeRepository(() -> DriverManager.getConnection(url), l, "altra postazione");

        check("archivio vuoto all'inizio", repo.list().isEmpty());

        B1Recipe r = B1Recipe.blank(l);
        r.name = "gomma2";
        r.recipeNo = 24;
        r.header.put("safety_temp", 115);
        r.header.put("max_energy", 300);
        r.phases.get(0).values.put("rpm", 36);
        r.phases.get(0).values.put("timeout", 900);
        r.phases.get(0).ops = 0x3000;
        r.phases.get(2).ops = 0x8000;
        repo.save(r, "santi");
        check("nuova ricetta salvata: revisione 1", r.revision == 1 && !r.modified.isEmpty());

        List<B1RecipeRepository.Entry> list = repo.list();
        check("elenco: una ricetta, nome e utente", list.size() == 1 && list.get(0).name.equals("gomma2")
                && list.get(0).modifiedBy.equals("santi"));

        B1Recipe back = repo.load(r.uid);
        check("rilettura identica (testata, fase 1, operazioni)", back.name.equals("gomma2") && back.recipeNo == 24
                && back.revision == 1 && back.header.get("safety_temp") == 115 && back.header.get("max_energy") == 300
                && back.phases.get(0).values.get("rpm") == 36 && back.phases.get(0).values.get("timeout") == 900
                && back.phases.get(0).ops == 0x3000 && back.phases.get(2).ops == 0x8000
                && back.phases.size() == l.phaseCount);

        B1Recipe other = altra.load(r.uid);        // un'altra postazione la apre
        back.phases.get(0).values.put("rpm", 40);
        repo.save(back, "santi");
        check("modifica: revisione 2 e valore nuovo", back.revision == 2 && repo.load(r.uid).phases.get(0).values.get("rpm") == 40);

        other.phases.get(1).values.put("rpm", 50);
        boolean conflict = false;
        try {
            altra.save(other, "rossi");               // ha ancora la revisione 1
        } catch (B1RecipeRepository.ConflictException e) {
            conflict = true;
        }
        check("salvataggio con revisione vecchia: conflitto, niente sovrascrittura",
                conflict && repo.load(r.uid).phases.get(1).values.get("rpm") == 0);

        B1Recipe dup = B1Recipe.blank(l);
        dup.name = "GOMMA2";
        boolean duplicate = false;
        try {
            repo.save(dup, "rossi");
        } catch (java.io.IOException e) {
            duplicate = !(e instanceof B1RecipeRepository.ConflictException) && e.getMessage().contains("gia'");
        }
        check("nome gia' presente: rifiutato", duplicate);

        B1Recipe second = B1Recipe.blank(l);
        second.name = "RFL8 V05";
        repo.save(second, "rossi");
        check("seconda ricetta: elenco ordinato per nome", repo.list().size() == 2 && repo.list().get(0).name.equals("gomma2"));

        repo.delete(r.uid);
        check("eliminazione: resta una ricetta e i valori sono spariti", repo.list().size() == 1
                && repo.list().get(0).name.equals("RFL8 V05"));
        boolean missing = false;
        try {
            repo.load(r.uid);
        } catch (java.io.IOException e) {
            missing = true;
        }
        check("ricetta eliminata: non si carica piu'", missing);
        System.exit(failures == 0 ? 0 : 1);
    }
}
