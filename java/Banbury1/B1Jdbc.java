package Banbury1;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Connessione a SQL Server per le configurazioni di B1. Due modi:
 *  - autenticazione Windows (consigliata): {@code auth = windows} nel file di configurazione. Nessuna password in nessun file e in nessuna
 *    variabile d'ambiente: si usa l'account Windows/dominio di chi lancia il programma, e i permessi li assegna il server (gruppo di dominio).
 *    Serve il driver con la libreria nativa mssql-jdbc_auth (dll) raggiungibile da PATH / java.library.path.
 *  - utente SQL: jdbc.user + password nella variabile d'ambiente (o, meno sicuro, jdbc.password).
 */
final class B1Jdbc {

    private B1Jdbc() {
    }

    static boolean windows(Properties cfg) {
        return "windows".equalsIgnoreCase(cfg.getProperty("auth", "").trim());
    }

    /** URL pronto all'uso (con integratedSecurity=true se l'autenticazione e' Windows). */
    static String url(Properties cfg) {
        String url = cfg.getProperty("jdbc.url", "").trim();
        if (windows(cfg) && !url.isEmpty() && !url.toLowerCase().contains("integratedsecurity")) {
            url = url + (url.endsWith(";") ? "" : ";") + "integratedSecurity=true";
        }
        return url;
    }

    /** Connessione configurata: URL presente e (autenticazione Windows oppure utente). */
    static boolean configured(Properties cfg) {
        return !cfg.getProperty("jdbc.url", "").trim().isEmpty()
                && (windows(cfg) || !cfg.getProperty("jdbc.user", "").trim().isEmpty());
    }

    static Connection open(Properties cfg, String passwordEnv) throws SQLException {
        String url = url(cfg);
        if (url.isEmpty()) {
            throw new SQLException("jdbc.url mancante");
        }
        if (windows(cfg)) {
            return DriverManager.getConnection(url);
        }
        String pwd = System.getenv(passwordEnv);
        if (pwd == null) {
            pwd = cfg.getProperty("jdbc.password", "");
        }
        return DriverManager.getConnection(url, cfg.getProperty("jdbc.user", "").trim(), pwd);
    }

    /** Descrizione dell'autenticazione per i messaggi (mai la password). */
    static String describe(Properties cfg, String passwordEnv) {
        if (windows(cfg)) {
            return "autenticazione Windows (nessuna password)";
        }
        String user = cfg.getProperty("jdbc.user", "").trim();
        String pwd = System.getenv(passwordEnv) != null ? "da " + passwordEnv
                : cfg.getProperty("jdbc.password", "").isEmpty() ? "mancante" : "da jdbc.password (meglio autenticazione Windows)";
        return "utente " + (user.isEmpty() ? "(vuoto)" : user) + ", password " + pwd;
    }
}
