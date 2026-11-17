package Banbury1;

import java.time.LocalDateTime;

/** Una produzione (come D_PRODUCTION di WinCC): nome, ricetta collegata e quantita' di miscele da fare. */
public final class B1Production {

    /** Stati come in WinCC: 1 nuova, 3 in uso, 4 terminata/cancellata (non piu' in elenco). */
    public static final int NEW = 1;
    public static final int RUNNING = 3;
    public static final int ENDED = 4;

    public int id;
    public String name = "";
    public String description = "";
    /** ID della ricetta WinCC (= numero ricetta in archivio). */
    public int recipeNo;
    /** Miscele da fare (NUM_DATA1). */
    public int quantity;
    public int priority = 1;
    public int status = NEW;
    public LocalDateTime created = LocalDateTime.now();
    public LocalDateTime updated = LocalDateTime.now();
    public String customerDate = "";

    public B1Production copy() {
        B1Production p = new B1Production();
        p.id = id;
        p.name = name;
        p.description = description;
        p.recipeNo = recipeNo;
        p.quantity = quantity;
        p.priority = priority;
        p.status = status;
        p.created = created;
        p.updated = updated;
        p.customerDate = customerDate;
        return p;
    }

    /** Controlli come WinCC: nome non vuoto (max 50), ricetta scelta, quantita' 0..999. Lista vuota = valida. */
    public java.util.List<String> validate() {
        java.util.List<String> e = new java.util.ArrayList<>();
        if (name == null || name.trim().isEmpty() || name.trim().length() > 50) {
            e.add("Nom de production non valide (1-50 caract\u00e8res)");
        }
        if (description != null && description.length() > 50) {
            e.add("Description trop longue (max 50 caract\u00e8res)");
        }
        if (recipeNo <= 0) {
            e.add("Recette pas s\u00e9lectionn\u00e9e");
        }
        if (quantity < 0 || quantity > 999) {
            e.add("Quantit\u00e9 de m\u00e9langes hors limites (0-999)");
        }
        return e;
    }
}
