package Banbury1;

import java.util.Properties;

/** Prova la configurazione delle connessioni SQL (senza collegarsi): java Banbury1.B1JdbcSelfTest */
public final class B1JdbcSelfTest {

    private static int failures;

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "OK    " : "FALLITO ") + what);
        if (!ok) {
            failures++;
        }
    }

    public static void main(String[] a) {
        Properties p = new Properties();
        check("senza url: non configurato", !B1Jdbc.configured(p));
        p.setProperty("jdbc.url", "jdbc:sqlserver://srv:1433;databaseName=mondo");
        check("url senza utente e senza auth Windows: non configurato", !B1Jdbc.configured(p));
        p.setProperty("jdbc.user", "b1");
        check("url + utente: configurato", B1Jdbc.configured(p));
        check("utente SQL: l'url non cambia", B1Jdbc.url(p).equals("jdbc:sqlserver://srv:1433;databaseName=mondo"));
        p.setProperty("auth", "windows");
        p.remove("jdbc.user");
        check("auth Windows: configurato senza utente", B1Jdbc.configured(p));
        check("auth Windows: aggiunge integratedSecurity=true", B1Jdbc.url(p).endsWith(";integratedSecurity=true"));
        p.setProperty("jdbc.url", "jdbc:sqlserver://srv:1433;databaseName=mondo;integratedSecurity=true");
        check("integratedSecurity gia' presente: non duplicato", B1Jdbc.url(p).indexOf("integratedSecurity") == B1Jdbc.url(p).lastIndexOf("integratedSecurity"));
        check("descrizione senza password", B1Jdbc.describe(p, "NESSUNA_VARIABILE").contains("Windows")
                && !B1Jdbc.describe(p, "NESSUNA_VARIABILE").toLowerCase().contains("secret"));
        p.setProperty("jdbc.password", "secret");
        p.remove("auth");
        p.setProperty("jdbc.user", "b1");
        check("la password non compare mai nei messaggi", !B1Jdbc.describe(p, "NESSUNA_VARIABILE").contains("secret"));
        System.exit(failures == 0 ? 0 : 1);
    }
}
