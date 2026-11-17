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
 *  - connessione centralizzata: {@code provider = svc} usa SVC.DBBamburyReader.openConnection() (database Mondo su srv-bambury), le credenziali
 *    restano solo in quella classe (nessun jdbc.url/utente/password nei file di B1). La classe viene cercata a runtime (reflection).
 *  - utente SQL: jdbc.user + password nella variabile d'ambiente (o, meno sicuro, jdbc.password).
 */
final class B1Jdbc {

    private B1Jdbc() {
    }

    static boolean svc(Properties cfg) {
        return "svc".equalsIgnoreCase(cfg.getProperty("provider", "").trim());
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
        if (svc(cfg)) {
            return true;
        }
        return !cfg.getProperty("jdbc.url", "").trim().isEmpty()
                && (windows(cfg) || !cfg.getProperty("jdbc.user", "").trim().isEmpty());
    }

    static Connection open(Properties cfg, String passwordEnv) throws SQLException {
        if (svc(cfg)) {
            return openSvc();
        }
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

    /** Connessione dalla classe centralizzata SVC.DBBamburyReader (stesso progetto Eclipse). */
    static Connection openSvc() throws SQLException {
        try {
            Class<?> c = Class.forName("SVC.DBBamburyReader");
            return (Connection) c.getMethod("openConnection").invoke(null);
        } catch (ClassNotFoundException e) {
            throw new SQLException("provider=svc : classe SVC.DBBamburyReader introuvable dans le classpath", e);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable t = e.getCause() != null ? e.getCause() : e;
            throw new SQLException("provider=svc: " + t.getMessage(), t);
        } catch (ReflectiveOperationException e) {
            throw new SQLException("provider=svc: " + e, e);
        }
    }

    /** Descrizione dell'autenticazione per i messaggi (mai la password). */
    static String describe(Properties cfg, String passwordEnv) {
        if (svc(cfg)) {
            return "connexion centralis\u00e9e SVC.DBBamburyReader (aucun identifiant dans les fichiers de B1)";
        }
        if (windows(cfg)) {
            return "authentification Windows (aucun mot de passe)";
        }
        String user = cfg.getProperty("jdbc.user", "").trim();
        String pwd = System.getenv(passwordEnv) != null ? "depuis " + passwordEnv
                : cfg.getProperty("jdbc.password", "").isEmpty() ? "manquant" : "depuis jdbc.password (mieux : authentification Windows)";
        return "utilisateur " + (user.isEmpty() ? "(vide)" : user) + ", mot de passe " + pwd;
    }
}
