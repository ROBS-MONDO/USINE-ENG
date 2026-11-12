package Banbury1;

/** Dati letti da un ticket (QR/barcode) di Banbury: ticket 1 = primo passaggio, ticket 2 = secondo. */
public final class B1Label {

    public static final String[] PASSAGE = {"", "1ER", "2EME", "3EME", "4EME"};
    public static final String[] COUCHE = {"", "SUP", "INF", "STS", "NET", "MEL.MERE"};
    public static final String[] COULEUR = {"", "---", "CLAIR", "FONCE", "MEDIUM"};

    public String recipeName = "";
    public String recipeUid;
    public final String annee;
    public final String lot;
    public final String suffixe;
    /** "Num melange" (serie): A, A1, L1... */
    public final String serieMelange;
    public final int nombreMelanges;
    /** 1..4 (1er, 2eme, 3eme, 4eme). */
    public final int passage;
    /** TYPE: 1..5 (SUP, INF, STS, NET, MEL.MERE); null se premast. */
    public final Integer couche;
    public final boolean premast;
    /** COULEUR: 1..4 (---, CLAIR, FONCE, MEDIUM); null se non presente. */
    public final Integer couleur;

    public B1Label(String recipeName, String annee, String lot, String suffixe, String serieMelange,
                   int nombreMelanges, int passage, Integer couche, boolean premast, Integer couleur) {
        this.recipeName = recipeName == null ? "" : recipeName.trim();
        this.annee = annee;
        this.lot = lot;
        this.suffixe = suffixe;
        this.serieMelange = serieMelange;
        this.nombreMelanges = nombreMelanges;
        this.passage = passage;
        this.couche = couche;
        this.premast = premast;
        this.couleur = couleur;
    }

    // Testi come nei campi del WinCC e nella colonna dei log SQL (Datas_B2: "1er"; Datas_B1: "4ème").
    public static final String[] PASSAGE_SQL = {"", "1er", "2ème", "3ème", "4ème"}; // come in Datas_B1 (es. "4ème"): da confermare per 2ème/3ème
    public static final String[] COUCHE_SQL = {"", "SUP", "INF", "STS", "NET", "MEL.MERE"};
    public static final String[] COULEUR_SQL = {"", "---", "Clair", "Foncé", "Medium"};

    public String passageSql() {
        return passage >= 1 && passage < PASSAGE_SQL.length ? PASSAGE_SQL[passage] : String.valueOf(passage);
    }

    public String coucheSql() {
        return premast ? "PREMAST" : (couche != null && couche < COUCHE_SQL.length ? COUCHE_SQL[couche] : "");
    }

    public String couleurSql() {
        return couleur != null && couleur < COULEUR_SQL.length ? COULEUR_SQL[couleur] : "---";
    }

    /** "Ticket 1" (primo passaggio) o "Ticket 2" (secondo). */
    public String ticketKind() {
        return passage <= 1 ? "Ticket 1 (1er passage)" : "Ticket 2 (" + passage + "eme passage)";
    }

    public String passageText() {
        return passage >= 1 && passage < PASSAGE.length ? PASSAGE[passage] : String.valueOf(passage);
    }

    public String coucheText() {
        return premast ? "PREMAST" : (couche != null && couche < COUCHE.length ? COUCHE[couche] : "");
    }

    public String couleurText() {
        return couleur != null && couleur < COULEUR.length ? COULEUR[couleur] : "";
    }
}
