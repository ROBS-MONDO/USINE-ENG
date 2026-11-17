package Banbury1;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

/** Test dell'archivio produzioni (file e SQL con H2) e dell'importazione da WinCC: java -cp ...;h2.jar Banbury1.B1ProductionSelfTest */
public final class B1ProductionSelfTest {

    private static int failures;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "OK    " : "FALLITO ") + what);
        if (!ok) {
            failures++;
        }
    }

    private static B1Production prod(String name, int recipe, int qty) {
        B1Production p = new B1Production();
        p.name = name;
        p.description = "d";
        p.recipeNo = recipe;
        p.quantity = qty;
        return p;
    }

    private static void common(String title, B1ProductionRepository repo) throws Exception {
        check(title + ": archivio vuoto", repo.list().isEmpty());
        B1Production a = repo.save(prod("1 pass", 14, 5));
        B1Production b = repo.save(prod("2 acc", 8, 100));
        check(title + ": ID progressivi", a.id == 1 && b.id == 2 && a.status == B1Production.NEW);
        check(title + ": elenco ordinato per nome", repo.list().size() == 2 && repo.list().get(0).name.equals("1 pass"));
        check(title + ": nome gia' usato", repo.nameExists("1 PASS", 0) && !repo.nameExists("1 pass", a.id));
        B1Production m = repo.load(b.id);
        m.quantity = 7;
        repo.save(m);
        check(title + ": modifica", repo.load(b.id).quantity == 7 && repo.load(b.id).name.equals("2 acc"));
        repo.setRunning(a.id);
        check(title + ": in uso (stato 3) resta in elenco", repo.load(a.id).status == 3 && repo.list().size() == 2);
        repo.end(b.id);
        check(title + ": elimina = stato 4, esce dall'elenco", repo.load(b.id).status == 4 && repo.list().size() == 1);
        check(title + ": il nome di una terminata si puo' riusare", !repo.nameExists("2 acc", 0));
        boolean refused = false;
        try {
            repo.save(prod("", 14, 1));
        } catch (java.io.IOException e) {
            refused = true;
        }
        check(title + ": nome vuoto rifiutato", refused);
        refused = false;
        try {
            repo.save(prod("x", 0, 1));
        } catch (java.io.IOException e) {
            refused = true;
        }
        check(title + ": ricetta mancante rifiutata", refused);
        B1Production imp = prod("importata", 3, 9);
        imp.id = 50;
        imp.status = 3;
        check(title + ": importazione con ID originale", repo.importRow(imp) && repo.load(50).quantity == 9);
        check(title + ": importazione non sovrascrive", !repo.importRow(imp));
        B1Production n = repo.save(prod("nuova", 14, 1));
        check(title + ": dopo l'import il prossimo ID e' il massimo + 1", n.id == 51);
    }

    public static void main(String[] a) throws Exception {
        Path f = Files.createTempDirectory("b1prod").resolve("prod.csv");
        common("file", new B1FileProductionRepository(f, ""));
        check("file: riletto da un'altra istanza", new B1FileProductionRepository(f, "").load(50) != null);

        String url = "jdbc:h2:mem:b1prod;MODE=MSSQLServer;IGNORECASE=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS dbo");
            st.execute("CREATE TABLE dbo.B1Productions (Id INT PRIMARY KEY, Nome NVARCHAR(50) NOT NULL, Descrizione NVARCHAR(50) NOT NULL DEFAULT '', "
                    + "NumRicetta INT NOT NULL DEFAULT 0, Quantita INT NOT NULL DEFAULT 0, Priorita INT NOT NULL DEFAULT 1, Stato INT NOT NULL DEFAULT 1, "
                    + "Creazione TIMESTAMP NOT NULL, Modifica TIMESTAMP NOT NULL, DataCliente NVARCHAR(15) NOT NULL DEFAULT '')");
        }
        common("sql", new B1SqlProductionRepository(() -> DriverManager.getConnection(url), "H2 di prova"));

        // importazione dal CSV di SSMS (BOM, righe senza intestazione, NULL, virgolette)
        String csv = String.valueOf((char) 0xFEFF) + "14,1 pass,1 pass,2014-02-27 09:33:40.867,2014-04-04 12:01:31.290,14,BFT,NULL,NULL, , , , , ,5,0,0,0,0,0,0,0,0,0,1,3,27-02-2014\n"
                + "2,\"lando, due\",descr,2013-12-19 10:28:30.827,2013-12-19 10:28:30.827,2,BFT,NULL,NULL, , , , , ,10,0,0,0,0,0,0,0,0,0,1,4,19-12-2013\n"
                + "riga non valida\n";
        List<B1Production> l = B1WinccProductionImport.parse(csv);
        check("importazione: 2 righe valide (BOM e riga non valida gestiti)", l.size() == 2);
        check("importazione: campi", l.get(0).id == 14 && l.get(0).recipeNo == 14 && l.get(0).quantity == 5 && l.get(0).status == 3
                && l.get(0).updated.getYear() == 2014 && l.get(0).updated.getMonthValue() == 4 && l.get(0).customerDate.equals("27-02-2014"));
        check("importazione: virgola dentro le virgolette e stato 4", l.get(1).name.equals("lando, due") && l.get(1).status == 4);
        System.exit(failures == 0 ? 0 : 1);
    }
}
