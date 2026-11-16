package Banbury1;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/** Prova il registro degli invii (CSV e tabella SQL su H2): java -cp ...;h2.jar Banbury1.B1RecipeSendLogSelfTest */
public final class B1RecipeSendLogSelfTest {

    private static int failures;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "OK    " : "FALLITO ") + what);
        if (!ok) {
            failures++;
        }
    }

    public static void main(String[] a) throws Exception {
        B1RecipeSendLog.Entry e = new B1RecipeSendLog.Entry();
        e.recipeUid = "11111111-2222-3333-4444-555555555555";
        e.name = "gomma2";
        e.recipeNo = 24;
        e.revision = 3;
        e.user = "santi.roberto";
        e.host = "PC-TEST";
        e.ok = true;
        e.words = 5;
        e.backup = "C:\\x\\DB201.bin";
        e.message = "ok; con punto e virgola\nea capo";

        Path dir = Files.createTempDirectory("b1log");
        Path f = dir.resolve("invii.csv");
        B1RecipeSendLog.writeCsv(f, e);
        B1RecipeSendLog.Entry e2 = new B1RecipeSendLog.Entry();
        e2.name = "RFL8";
        e2.ok = false;
        e2.message = "Timeout";
        B1RecipeSendLog.writeCsv(f, e2);
        java.util.List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
        check("CSV: intestazione + 2 righe", lines.size() == 3 && lines.get(0).startsWith("DataOra;Esito"));
        check("CSV: esito, ricetta, utente, postazione, parole", lines.get(1).contains(";OK;gomma2;") && lines.get(1).contains("santi.roberto;PC-TEST;5;"));
        check("CSV: una sola riga per invio (punto e virgola e a capo ripuliti)",
                lines.get(1).split(";", -1).length == 11 && lines.get(2).contains(";ERRORE;RFL8;"));

        String url = "jdbc:h2:mem:b1log;MODE=MSSQLServer;DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS dbo");
            st.execute("CREATE TABLE dbo.B1RecipePLC (Id BIGINT IDENTITY(1,1) PRIMARY KEY, DataOra TIMESTAMP NOT NULL, RecipeUID NVARCHAR(36), "
                    + "Nome NVARCHAR(32) NOT NULL, NumRicetta INT NOT NULL DEFAULT 0, Revisione BIGINT NOT NULL DEFAULT 0, "
                    + "Utente NVARCHAR(100) NOT NULL DEFAULT '', Postazione NVARCHAR(100) NOT NULL DEFAULT '', Esito NVARCHAR(10) NOT NULL, "
                    + "ParoleScritte INT NOT NULL DEFAULT 0, Backup NVARCHAR(260), Messaggio NVARCHAR(400))");
            B1RecipeSendLog.insertSql(c, e);
            B1RecipeSendLog.insertSql(c, e2);
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*), MAX(Revisione), SUM(CASE WHEN Esito='OK' THEN 1 ELSE 0 END), "
                    + "MAX(Utente) FROM dbo.B1RecipePLC")) {
                rs.next();
                check("SQL: due righe, revisione 3, un solo OK", rs.getInt(1) == 2 && rs.getLong(2) == 3 && rs.getInt(3) == 1);
                check("SQL: utente registrato", "santi.roberto".equals(rs.getString(4)));
            }
        }
        System.exit(failures == 0 ? 0 : 1);
    }
}
