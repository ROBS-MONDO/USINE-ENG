package Banbury1;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lettura dei ticket di Banbury (stessa logica di PLC3_Recette di B2): QR "MQR2" in Base32, formato
 * "NOME;AA/LOT;SUFFISSO;SERIE;N;..;PASSAGGIO;COUCHE;COULEUR;RECIPEUID=..", con correzione degli errori
 * del lettore su tastiera francese (AZERTY) e dei ticket "compatti".
 *
 * Differenza voluta rispetto a B2: l'anno non e' fisso a "26" ma e' un elenco (anno corrente e precedente).
 */
public final class B1LabelParser {

    private B1LabelParser() {
    }

    /** Anni accettati nel lotto: anno corrente e precedente (due cifre). */
    public static List<String> defaultYears() {
        int yy = LocalDate.now().getYear() % 100;
        List<String> y = new ArrayList<>();
        y.add(String.format("%02d", yy));
        y.add(String.format("%02d", (yy + 99) % 100));
        return y;
    }

    public static B1Label parse(String scanned) {
        return parse(scanned, defaultYears());
    }

    /** Decodifica MQR2 (se presente) e interpreta il ticket. Lancia IllegalArgumentException con messaggio per l'operatore. */
    public static B1Label parse(String scanned, List<String> years) {
        String decoded = decodeMondoQr2(scanned, years);
        if (!decoded.trim().toUpperCase(Locale.ROOT).startsWith("MQR2")
                && !decoded.toUpperCase(Locale.ROOT).contains("RECIPEUID=")) {
            throw new IllegalArgumentException("Ancien QR sans RecipeUID : r\u00e9imprimer le ticket.");
        }
        B1Label l = parseBarcodeRecipe(decoded, years);
        if (l.recipeUid == null || l.recipeUid.trim().isEmpty()) {
            throw new IllegalArgumentException("RecipeUID absent : r\u00e9imprimer le ticket.");
        }
        return l;
    }

    // ------------------------------------------------------------------ MQR2
    static String decodeMondoQr2(String input, List<String> years) {
        if (input == null) {
            return "";
        }
        String compact = input.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (!compact.startsWith("MQR2")) {
            return input;
        }
        if (!compact.matches("MQR2[A-Z2-7]+")) {
            throw new IllegalArgumentException("caract\u00e8res QR incorrects : v\u00e9rifier le lecteur");
        }
        String data = compact.substring(4);
        if (data.isEmpty()) {
            throw new IllegalArgumentException("QR vide");
        }
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);
            int v = c >= 'A' && c <= 'Z' ? c - 'A' : c >= '2' && c <= '7' ? c - '2' + 26 : -1;
            if (v < 0) {
                throw new IllegalArgumentException("Base32 invalide");
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                bytes.write((buffer >> bits) & 255);
            }
        }
        String payload = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        boolean yearOk = false;
        for (String y : years) {
            yearOk |= payload.contains(";" + y + "/");
        }
        if (!payload.contains(";RECIPEUID=") || !yearOk) {
            throw new IllegalArgumentException("donn\u00e9es ou identifiant manquants");
        }
        return payload;
    }

    // ------------------------------------------------------------------ parsing
    static B1Label parseBarcodeRecipe(String rawBarcode, List<String> years) {
        String raw = rawBarcode == null ? "" : rawBarcode.trim();
        List<String> candidates = new ArrayList<>();
        add(candidates, raw);
        add(candidates, normalizeBarcodeText(raw));
        add(candidates, fixFrenchAzertyBarcode(normalizeBarcodeText(raw)));
        add(candidates, normalizeFrenchKeyboardBarcode(raw));
        add(candidates, fixFrenchAzertyBarcode(normalizeFrenchKeyboardBarcode(raw)));
        for (String base : new String[]{raw, normalizeBarcodeText(raw),
                fixFrenchAzertyBarcode(normalizeBarcodeText(raw)), normalizeFrenchKeyboardBarcode(raw),
                fixFrenchAzertyBarcode(normalizeFrenchKeyboardBarcode(raw))}) {
            add(candidates, rebuildCompactTicketBarcode(base, years));
        }
        IllegalArgumentException last = null;
        for (String c : candidates) {
            try {
                return parseNormalized(c, years);
            } catch (IllegalArgumentException ex) {
                last = ex;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new IllegalArgumentException("Code-barres non valide");
    }

    private static void add(List<String> list, String v) {
        if (v == null) {
            return;
        }
        v = v.trim();
        if (!v.isEmpty() && !list.contains(v)) {
            list.add(v);
        }
    }

    private static int firstYearIndex(String raw, List<String> years) {
        int best = -1;
        for (String y : years) {
            int i = raw.indexOf(y + "/");
            if (i >= 0 && (best < 0 || i < best)) {
                best = i;
            }
        }
        return best;
    }

    static B1Label parseNormalized(String rawBarcode, List<String> years) {
        String raw = normalizeBarcodeText(rawBarcode);
        raw = normalizeBarcodeSeparators(raw);

        int firstLotIndex = firstYearIndex(raw, years);
        if (firstLotIndex > 0 && raw.substring(0, firstLotIndex).indexOf(';') < 0) {
            raw = raw.substring(firstLotIndex);
        }
        String[] parts = raw.split(";", -1);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Code-barres non valide");
        }

        int lotIndex = 0;
        String recipeName = "";
        if (parts.length >= 3 && clean(parts[1]).contains("/")) {
            recipeName = fixRecipeNameSpecialChars(clean(parts[0]));
            lotIndex = 1;
        }
        for (String p : parts) {
            String v = clean(p);
            if (v.startsWith("RECETTE=")) {
                if (recipeName.isEmpty()) {
                    recipeName = fixRecipeNameSpecialChars(v.substring("RECETTE=".length()));
                }
                break;
            }
        }

        String uid = null;
        Matcher um = Pattern.compile(
                "(?i)(?:^|;)\\s*RECIPEUID=([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?:;|$)")
                .matcher(rawBarcode == null ? "" : rawBarcode);
        if (um.find()) {
            uid = um.group(1);
        }
        for (String p : parts) {
            String v = clean(p).trim();
            if (v.startsWith("RECIPEUID=") && uid == null) {
                uid = v.substring("RECIPEUID=".length());
            }
        }
        if (uid != null) {
            try {
                uid = java.util.UUID.fromString(uid).toString();
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("RecipeUID non valide dans le QR");
            }
        }

        String lotComplete = clean(parts[lotIndex]);
        String[] lotParts = lotComplete.split("/");
        if (lotParts.length != 2) {
            throw new IllegalArgumentException("Lot non valide dans le code-barres");
        }
        String annee = onlyDigits(lotParts[0]);
        String lot = cleanLotValue(lotParts[1]);
        String suffixe = parts.length > lotIndex + 1 ? clean(parts[lotIndex + 1]) : "1";
        String serie = parts.length > lotIndex + 2 ? clean(parts[lotIndex + 2]) : "";
        int nombre = 0;
        if (parts.length > lotIndex + 3) {
            String n = onlyDigits(parts[lotIndex + 3]);
            if (!n.isEmpty()) {
                try {
                    nombre = Integer.parseInt(n);
                } catch (NumberFormatException ignored) {
                    // lasciato a 0: ricalcolato sotto
                }
            }
        }
        int passage = 1;
        Integer couche = 1;
        boolean premast = false;
        Integer couleur = null;

        for (String p : parts) {
            String v = clean(p);
            if (v.isEmpty() || v.startsWith("RECETTE=") || v.startsWith("RECIPEUID=")) {
                continue;
            }
            try {
                passage = parsePassageValue(v);
                continue;
            } catch (IllegalArgumentException ignored) {
                // non e' un passaggio
            }
            if (isPremastValue(v)) {
                premast = true;
                couche = null;
                continue;
            }
            try {
                couche = parseCoucheValue(v);
                premast = false;
                continue;
            } catch (IllegalArgumentException ignored) {
                // non e' una couche
            }
            try {
                couleur = parseCouleurValue(v);
            } catch (IllegalArgumentException ignored) {
                // campo non riconosciuto
            }
        }

        if (nombre == 0) {
            for (int i = lotIndex + 1; i < parts.length; i++) {
                if (clean(parts[i]).startsWith("RECETTE=") || clean(parts[i]).startsWith("RECIPEUID=")) {
                    continue;
                }
                String v = onlyDigits(parts[i]);
                if (!v.isEmpty()) {
                    try {
                        int n = Integer.parseInt(v);
                        if (n > 1 && n < 1000) {
                            nombre = n;
                            break;
                        }
                    } catch (NumberFormatException ignored) {
                        // numero troppo grande: ignorato
                    }
                }
            }
        }
        if (annee.isEmpty() || lot.isEmpty()) {
            throw new IllegalArgumentException("Donn\u00e9es manquantes dans le code-barres");
        }
        B1Label l = new B1Label(recipeName, annee, lot, suffixe, serie, nombre, passage, couche, premast, couleur);
        l.recipeUid = uid;
        return l;
    }

    /** Ricostruisce un ticket letto "tutto attaccato" (es. ROMANSOLID*260410171ERINF). */
    static String rebuildCompactTicketBarcode(String value, List<String> years) {
        if (value == null) {
            return "";
        }
        String s = normalizeBarcodeText(value);
        s = fixFrenchAzertyBarcode(s);
        s = s.toUpperCase(Locale.ROOT).replace(" ", "").replaceAll("[^A-Z0-9._*+%/-]", "");
        if (s.indexOf(';') >= 0 || s.indexOf('/') >= 0) {
            return "";
        }
        String yy = null;
        int yearIndex = -1;
        for (String y : years) {
            int i = s.indexOf(y);
            if (i > 0 && i + 2 < s.length() && (yearIndex < 0 || i < yearIndex)) {
                yearIndex = i;
                yy = y;
            }
        }
        if (yy == null) {
            return "";
        }
        String rawName = s.substring(0, yearIndex).replaceAll("[;|_-]+$", "");
        while (rawName.endsWith("*") && !rawName.endsWith("%*")) {
            rawName = rawName.substring(0, rawName.length() - 1);
        }
        String recipe = fixRecipeNameSpecialChars(rawName);
        String tail = s.substring(yearIndex + 2);

        String couleur = "";
        for (String c : new String[]{"MEDIUM", "FONCE", "CLAIRE", "CLAIR"}) {
            if (tail.endsWith(c)) {
                couleur = c;
                tail = tail.substring(0, tail.length() - c.length());
                break;
            }
        }
        String couche = "";
        for (String c : new String[]{"PREMAST", "MELMERE", "SUP", "INF", "STS", "NET"}) {
            if (tail.endsWith(c)) {
                couche = c;
                tail = tail.substring(0, tail.length() - c.length());
                break;
            }
        }
        if ("MELMERE".equals(couche)) {
            couche = "MEL.MERE";
        }
        String passage = "";
        for (String p : new String[]{"1ERE", "1ER", "2EME", "3EME", "4EME"}) {
            if (tail.endsWith(p)) {
                passage = p;
                tail = tail.substring(0, tail.length() - p.length());
                break;
            }
        }
        if (passage.isEmpty()) {
            passage = "1ER";
        }

        String compactData = cleanLotValue(tail);
        String lot = compactData;
        String suffixe = "1";
        String serie = "";
        String nombre = "1";
        Matcher withN = Pattern.compile("^(\\d{5,})([A-Z][A-Z0-9]*?)(\\d{1,2})$").matcher(compactData);
        Matcher noN = Pattern.compile("^(\\d{5,})([A-Z][A-Z0-9]*)$").matcher(compactData);
        if (withN.matches() || noN.matches()) {
            Matcher m = withN.matches() ? withN : noN;
            String lotSuffixe = m.group(1);
            lot = lotSuffixe.substring(0, lotSuffixe.length() - 1);
            suffixe = lotSuffixe.substring(lotSuffixe.length() - 1);
            serie = m.group(2);
            nombre = m.groupCount() >= 3 ? m.group(3) : "1";
        } else if (compactData.length() >= 5) {
            lot = compactData.substring(0, 4);
            suffixe = compactData.substring(4, 5);
            String rest = compactData.substring(5);
            if (rest.length() >= 2) {
                serie = rest.substring(0, rest.length() - 1);
                nombre = rest.substring(rest.length() - 1);
            } else if (rest.length() == 1) {
                serie = rest;
                nombre = "1";
            }
        }
        if (recipe.isEmpty() || lot.isEmpty()) {
            return "";
        }
        return recipe + ";" + yy + "/" + lot + ";" + suffixe + ";" + serie + ";" + nombre + ";;;" + passage
                + ";" + couche + ";" + couleur;
    }

    // ------------------------------------------------------------------ normalizzazione tastiera
    static String normalizeBarcodeText(String value) {
        if (value == null) {
            return "";
        }
        String n = value.trim().replace('\n', ';').replace('\r', ';');
        n = fixAccentedTicketKeywords(n);
        n = translateFrenchUnshiftKeyboard(n);
        n = n.replace('\u00a7', '/').replace('!', '/');
        return n.toUpperCase(Locale.ROOT);
    }

    static String fixAccentedTicketKeywords(String value) {
        return value == null ? "" : value
                .replaceAll("(?iu)FONC[\u00c9\u00c8\u00ca]", "FONCE")
                .replaceAll("(?iu)1[\u00c9\u00c8\u00ca]RE", "1ERE")
                .replaceAll("(?iu)([2-4])[\u00c9\u00c8\u00ca]ME", "$1EME")
                .replaceAll("(?iu)M[\u00c9\u00c8\u00ca]DIUM", "MEDIUM");
    }

    static String translateFrenchUnshiftKeyboard(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&': out.append('1'); break;
                case '\u00e9': case '\u00c9': out.append('2'); break;
                case '"': out.append('3'); break;
                case '\'': out.append('4'); break;
                case '(': out.append('5'); break;
                case '-': out.append('6'); break;
                case '\u00e8': case '\u00c8': out.append('7'); break;
                case '_': out.append('8'); break;
                case '\u00e7': case '\u00c7': out.append('9'); break;
                case '\u00e0': case '\u00c0': out.append('0'); break;
                case '!': out.append('/'); break;
                default: out.append(c);
            }
        }
        return out.toString();
    }

    static String fixFrenchAzertyBarcode(String value) {
        if (value == null) {
            return "";
        }
        String s = value.trim().toUpperCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case 'Q': out.append('A'); break;
                case 'A': out.append('Q'); break;
                case 'Z': out.append('W'); break;
                case 'W': out.append('Z'); break;
                default: out.append(c);
            }
        }
        return out.toString();
    }

    static String normalizeFrenchKeyboardBarcode(String value) {
        return value == null ? "" : normalizeBarcodeSeparators(normalizeBarcodeText(value));
    }

    static String normalizeBarcodeSeparators(String value) {
        if (value == null) {
            return "";
        }
        String n = value.trim().toUpperCase(Locale.ROOT);
        n = n.replace('\n', ';').replace('\r', ';').replace('\u00a7', '/').replace('!', '/');
        // Lettore FR: "26/0366;1;;4;1ER;SUP;CLAIR" puo arrivare come "26/0366M1MM4M1ERMSUPMCLQIR"
        if (n.indexOf(';') < 0 && n.indexOf('M') >= 0) {
            n = n.replace("MEL.MERE", "__MEL_DOT_MERE__").replace("MEL MERE", "__MEL_SPACE_MERE__")
                    .replace("MELMERE", "__MELMERE__").replace("MEDIUM", "__MEDIUM__");
            n = n.replace('M', ';');
            n = n.replace("__MEL_DOT_MERE__", "MEL.MERE").replace("__MEL_SPACE_MERE__", "MEL MERE")
                    .replace("__MELMERE__", "MELMERE").replace("__MEDIUM__", "MEDIUM");
        }
        return n;
    }

    // ------------------------------------------------------------------ campi
    static String clean(String v) {
        return v == null ? "" : v.trim().toUpperCase();
    }

    static String onlyDigits(String v) {
        return v == null ? "" : v.replaceAll("[^0-9]", "");
    }

    /** Mantiene anche le lettere del lotto (es. 26/0394L1 -> lotto 0394L1). */
    static String cleanLotValue(String v) {
        return v == null ? "" : v.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    static String fixRecipeNameSpecialChars(String name) {
        if (name == null) {
            return "";
        }
        String n = name.trim().toUpperCase(Locale.ROOT);
        if (n.matches(".*[A-Z]{2,}8$")) {
            n = n.substring(0, n.length() - 1) + "*";
        }
        if (n.matches(".*[A-Z]{2,}_$")) {
            n = n.substring(0, n.length() - 1) + "*";
        }
        return n;
    }

    static String normalizeBarcodeToken(String text) {
        return clean(text).replace("\u00c9", "E").replace("\u00c8", "E").replace("\u00ca", "E").replace("\u00c0", "A").replace("\u00c7", "C");
    }

    static boolean containsInEither(String value, String fixed, String expected) {
        return (value != null && value.contains(expected)) || (fixed != null && fixed.contains(expected));
    }

    static int parsePassageValue(String text) {
        String v = normalizeBarcodeToken(text).replace(" ", "");
        if (v.equals("1ER") || v.equals("1ERE")) return 1;
        if (v.equals("2EME")) return 2;
        if (v.equals("3EME")) return 3;
        if (v.equals("4EME")) return 4;
        throw new IllegalArgumentException("Passage non valide: " + v);
    }

    static boolean isPremastValue(String text) {
        String v = normalizeBarcodeToken(text);
        String f = fixFrenchAzertyBarcode(v);
        return "PRE".equals(v) || "PREMAST".equals(v) || "PRE".equals(f) || "PREMAST".equals(f);
    }

    static int parseCoucheValue(String text) {
        String v = normalizeBarcodeToken(text);
        String f = fixFrenchAzertyBarcode(v);
        if (containsInEither(v, f, "SUP")) return 1;
        if (containsInEither(v, f, "INF")) return 2;
        if (containsInEither(v, f, "STS")) return 3;
        if (containsInEither(v, f, "NET")) return 4;
        if (containsInEither(v, f, "MEL.MERE") || containsInEither(v, f, "MEL MERE")
                || containsInEither(v, f, "MELMERE")) return 5;
        throw new IllegalArgumentException("Couche non valide");
    }

    static int parseCouleurValue(String text) {
        String v = normalizeBarcodeToken(text);
        String f = fixFrenchAzertyBarcode(v);
        if (v.isEmpty() || v.contains("---") || f.contains("---") || containsInEither(v, f, "AUCUN")) return 1;
        if (containsInEither(v, f, "CLAIR")) return 2;
        if (containsInEither(v, f, "FONC")) return 3;
        if (containsInEither(v, f, "MEDIUM")) return 4;
        throw new IllegalArgumentException("Couleur non valide");
    }
}
