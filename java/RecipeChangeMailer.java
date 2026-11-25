import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Invio email di notifica per le modifiche alle ricette.
 *
 * - SMTP: relay Exchange Online Protection (porta 25, senza autenticazione, senza cifratura).
 * - Invio asincrono su thread dedicato: un SMTP lento/non raggiungibile NON blocca l'interfaccia
 *   e NON impedisce il salvataggio della modifica.
 * - Layout HTML con tabelle e stili inline (compatibile con Outlook).
 *
 * Dipendenza Maven: org.eclipse.angus:angus-mail (o com.sun.mail:jakarta.mail) + jakarta.mail-api.
 * Se il progetto usa javax.mail, sostituire gli import "jakarta.mail" con "javax.mail".
 */
public final class RecipeChangeMailer {

    /**
     * File di configurazione (UTF-8). Riletto a ogni invio: le modifiche ai destinatari
     * sono attive subito, senza riavviare l'applicazione. Chiavi:
     *   mail.enabled=true
     *   mail.from=banbury-recette@mondo.lu
     *   mail.recipients=nome1@mondo.lu,nome2@mondo.lu
     *   mail.smtp.host=mondo-lu.mail.protection.outlook.com   (opzionale)
     *   mail.smtp.port=25                                    (opzionale)
     */
    private static final String CONFIG_FILE =
            "\\\\neo\\x-service\\banbury\\recette\\recipe_mail.properties";

    private static final String DEFAULT_SMTP_HOST = "mondo-lu.mail.protection.outlook.com";
    private static final String DEFAULT_SMTP_PORT = "25";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RecipeChangeMailer");
        t.setDaemon(true);
        return t;
    });

    private RecipeChangeMailer() {
    }

    /** Notifica la modifica di un parametro di uno step. Non lancia mai eccezioni. */
    public static void sendStepChange(int recipeId,
                                      String recipeName,
                                      int step,
                                      String parameterName,
                                      String oldValue,
                                      String newValue,
                                      String username,
                                      String principal) {
        String subject = "Modifica ricetta " + recipeId + " - STEP " + step + " - " + parameterName;
        String html = buildStepChangeHtml(recipeId, recipeName, step, parameterName,
                oldValue, newValue, username, principal);
        sendAsync(subject, html);
    }

    /** Notifica un'operazione eseguita in SETUP (creazione, copia, rinomina, spostamento, eliminazione). */
    public static void sendSetupChange(String action,
                                       String oldInfo,
                                       String newInfo,
                                       String username,
                                       String principal) {
        String subject = "SETUP ricette - " + action;
        String html = buildSetupChangeHtml(action, oldInfo, newInfo, username, principal);
        sendAsync(subject, html);
    }

    private static void sendAsync(String subject, String html) {
        EXECUTOR.submit(() -> {
            try {
                Properties cfg = new Properties();
                try (Reader r = new InputStreamReader(new FileInputStream(new File(CONFIG_FILE)), StandardCharsets.UTF_8)) {
                    cfg.load(r);
                }
                if (!"true".equalsIgnoreCase(cfg.getProperty("mail.enabled", "true").trim())) {
                    return;
                }
                String from = cfg.getProperty("mail.from", "").trim();
                String recipientsRaw = cfg.getProperty("mail.recipients", "").trim();
                if (from.isEmpty() || recipientsRaw.isEmpty()) {
                    return;
                }

                Properties props = new Properties();
                props.put("mail.smtp.host", cfg.getProperty("mail.smtp.host", DEFAULT_SMTP_HOST).trim());
                props.put("mail.smtp.port", cfg.getProperty("mail.smtp.port", DEFAULT_SMTP_PORT).trim());
                props.put("mail.smtp.auth", "false");
                props.put("mail.smtp.starttls.enable", "false");
                props.put("mail.smtp.connectiontimeout", "5000");
                props.put("mail.smtp.timeout", "10000");
                props.put("mail.smtp.writetimeout", "10000");

                Session session = Session.getInstance(props);
                MimeMessage msg = new MimeMessage(session);
                msg.setFrom(new InternetAddress(from));
                for (String addr : recipientsRaw.split("[,;]")) {
                    if (!addr.trim().isEmpty()) {
                        msg.addRecipient(Message.RecipientType.TO, new InternetAddress(addr.trim()));
                    }
                }
                msg.setSubject(subject, "UTF-8");
                msg.setContent(html, "text/html; charset=UTF-8");
                Transport.send(msg);
            } catch (Exception ex) {
                // Mai bloccare il flusso operativo: errore solo su stderr.
                System.err.println("[RecipeChangeMailer] Invio email fallito: " + ex);
            }
        });
    }

    // ------------------------------------------------------------------ HTML

    private static String buildStepChangeHtml(int recipeId, String recipeName, int step,
                                              String parameterName, String oldValue, String newValue,
                                              String username, String principal) {
        String when = DATE_FORMAT.format(LocalDateTime.now());
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style=\"margin:0;padding:16px;background:#f3f4f6;font-family:Segoe UI,Arial,sans-serif;\">");
        sb.append("<table role=\"presentation\" width=\"640\" cellpadding=\"0\" cellspacing=\"0\" ")
          .append("style=\"background:#ffffff;border:1px solid #d9dce1;border-radius:8px;\"><tr><td style=\"padding:20px 24px;\">");

        sb.append("<div style=\"font-size:12px;letter-spacing:.5px;color:#6b7280;text-transform:uppercase;\">Audit delle ricette</div>");
        sb.append("<div style=\"font-size:18px;font-weight:600;color:#111827;margin:10px 0 4px 0;\">Ricetta ")
          .append(esc(String.valueOf(recipeId)));
        if (recipeName != null && !recipeName.trim().isEmpty()) {
            sb.append(" (").append(esc(recipeName.trim())).append(")");
        }
        sb.append(" &mdash; STEP ").append(step).append("</div>");
        sb.append("<div style=\"font-size:13px;color:#4b5563;margin-bottom:16px;\">Parametro: ")
          .append(esc(parameterName)).append("</div>");

        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>");
        sb.append("<td align=\"center\" width=\"42%\"><div style=\"font-size:13px;color:#4b5563;\">Valore precedente</div>")
          .append("<div style=\"font-size:38px;font-weight:700;color:#dc2626;\">").append(esc(oldValue)).append("</div></td>");
        sb.append("<td align=\"center\" width=\"16%\" style=\"font-size:24px;color:#111827;\">&rarr;</td>");
        sb.append("<td align=\"center\" width=\"42%\"><div style=\"font-size:13px;color:#4b5563;\">Nuovo valore</div>")
          .append("<div style=\"font-size:38px;font-weight:700;color:#15803d;\">").append(esc(newValue)).append("</div></td>");
        sb.append("</tr></table>");

        sb.append("<hr style=\"border:0;border-top:1px solid #e5e7eb;margin:20px 0 14px 0;\">");
        sb.append(infoBlock(username, principal, when));
        sb.append("</td></tr></table></body></html>");
        return sb.toString();
    }

    private static String buildSetupChangeHtml(String action, String oldInfo, String newInfo,
                                               String username, String principal) {
        String when = DATE_FORMAT.format(LocalDateTime.now());
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style=\"margin:0;padding:16px;background:#f3f4f6;font-family:Segoe UI,Arial,sans-serif;\">");
        sb.append("<table role=\"presentation\" width=\"640\" cellpadding=\"0\" cellspacing=\"0\" ")
          .append("style=\"background:#ffffff;border:1px solid #d9dce1;border-radius:8px;\"><tr><td style=\"padding:20px 24px;\">");
        sb.append("<div style=\"font-size:12px;letter-spacing:.5px;color:#6b7280;text-transform:uppercase;\">Audit delle ricette - SETUP</div>");
        sb.append("<div style=\"font-size:18px;font-weight:600;color:#111827;margin:10px 0 16px 0;\">")
          .append(esc(action)).append("</div>");
        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>");
        sb.append("<td align=\"center\" width=\"46%\"><div style=\"font-size:13px;color:#4b5563;\">Prima</div>")
          .append("<div style=\"font-size:20px;font-weight:700;color:#dc2626;\">").append(esc(oldInfo)).append("</div></td>");
        sb.append("<td align=\"center\" width=\"8%\" style=\"font-size:24px;color:#111827;\">&rarr;</td>");
        sb.append("<td align=\"center\" width=\"46%\"><div style=\"font-size:13px;color:#4b5563;\">Dopo</div>")
          .append("<div style=\"font-size:20px;font-weight:700;color:#15803d;\">").append(esc(newInfo)).append("</div></td>");
        sb.append("</tr></table>");
        sb.append("<hr style=\"border:0;border-top:1px solid #e5e7eb;margin:20px 0 14px 0;\">");
        sb.append(infoBlock(username, principal, when));
        sb.append("</td></tr></table></body></html>");
        return sb.toString();
    }

    private static String infoBlock(String username, String principal, String when) {
        return "<div style=\"font-size:14px;color:#111827;font-weight:600;\">&#128100; Utente identificato tramite Active Directory</div>"
                + "<div style=\"font-size:13px;color:#4b5563;margin:2px 0 10px 22px;\">"
                + esc(username) + " (" + esc(principal) + ")<br>Data e ora: " + esc(when) + "</div>"
                + "<div style=\"font-size:13px;color:#111827;\">&#128196; Registrato nel file di log centralizzato</div>";
    }

    private static String esc(String s) {
        if (s == null || s.isEmpty()) {
            return "-";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
