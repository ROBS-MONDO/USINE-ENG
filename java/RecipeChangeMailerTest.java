/**
 * Prova invio email senza avviare l'applicazione.
 * Uso:  java -cp ".;lib/*" RecipeChangeMailerTest
 * Legge destinatari e mittente da recipe_mail.properties (vedi CONFIG_FILE in RecipeChangeMailer).
 */
public class RecipeChangeMailerTest {
    public static void main(String[] args) throws Exception {
        RecipeChangeMailer.sendStepChange(16, "RFL16 (TEST)", 6, "Vitesse rotors",
                "36 rpm", "38 rpm", "test.user", "test.user@mondo.lu");
        RecipeChangeMailer.sendSetupChange("MODIFICATION_RECETTE (TEST)",
                "ID 16 - RFL16", "ID 16 - RFL16 bis", "test.user", "test.user@mondo.lu");
        Thread.sleep(8000); // attende l'invio asincrono; eventuali errori compaiono su stderr
        System.out.println("Fatto: controllare la casella e l'eventuale output di errore.");
    }
}
