package Banbury1;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Importa le produzioni del WinCC (tabella D_PRODUCTION, risultato di SELECT * salvato come CSV, senza intestazione) nell'archivio produzioni B1.
 * Uso: java Banbury1.B1WinccProductionImport produzioni.csv [--apply] [--file b1_productions.csv]
 * Senza --apply mostra soltanto cosa importerebbe. Le produzioni gia' presenti (stesso ID) non vengono toccate.
 * Colonne: ID, NAME, DESCRIPTION, DATE_CREATION, DATE_LAST_UPDATE, ID_RECIPE, ID_MILL, ID_WEIGH_PROGRAM, BATCH_WEIGH_PROGRAM,
 * STR_DATA1..5, NUM_DATA1..10 (NUM_DATA1 = miscele da fare), PRIORITY, STATUS, DATE_CUSTOMER.
 */
public final class B1WinccProductionImport {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private B1WinccProductionImport() {
    }

    /** Legge il file come UTF-8 se valido, altrimenti come windows-1252. */
    static String readText(Path file) throws IOException {
        byte[] b = Files.readAllBytes(file);
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();
        } catch (CharacterCodingException e) {
            return new String(b, Charset.forName("windows-1252"));
        }
    }

    /** Divide una riga CSV (virgole, campi tra virgolette doppie ammessi). */
    static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (q) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    q = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                q = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static LocalDateTime date(String s) {
        try {
            return LocalDateTime.parse(s.trim().substring(0, 19), TS);
        } catch (RuntimeException e) {
            return LocalDateTime.now();
        }
    }

    /** Converte le righe del CSV (le righe vuote o non valide si saltano). */
    static List<B1Production> parse(String text) {
        List<B1Production> out = new ArrayList<>();
        text = text.replace(String.valueOf((char) 0xFEFF), ""); // BOM del file salvato da SSMS
        for (String line : text.split("\\r?\\n")) {
            List<String> f = splitCsv(line);
            if (f.size() < 27 || !f.get(0).trim().matches("\\d+")) {
                continue;
            }
            B1Production p = new B1Production();
            p.id = num(f.get(0));
            p.name = f.get(1).trim();
            p.description = f.get(2).trim();
            p.created = date(f.get(3));
            p.updated = date(f.get(4));
            p.recipeNo = num(f.get(5));
            p.quantity = num(f.get(14));
            p.priority = num(f.get(24));
            p.status = num(f.get(25));
            p.customerDate = "NULL".equals(f.get(26).trim()) ? "" : f.get(26).trim();
            out.add(p);
        }
        return out;
    }

    public static void main(String[] a) throws Exception {
        if (a.length < 1) {
            System.err.println("Uso : java Banbury1.B1WinccProductionImport produzioni.csv [--apply] [--file b1_productions.csv]");
            return;
        }
        Path csv = Paths.get(a[0]);
        boolean apply = false;
        Path file = B1ProductionRepositories.DEFAULT_FILE;
        for (int i = 1; i < a.length; i++) {
            if ("--apply".equals(a[i])) {
                apply = true;
            } else if ("--file".equals(a[i])) {
                file = Paths.get(a[++i]);
            }
        }
        List<B1Production> list = parse(readText(csv));
        int active = 0;
        for (B1Production p : list) {
            if (p.status <= B1Production.RUNNING) {
                active++;
            }
            System.out.printf("%3d  %-34s ricetta %-3d miscele %-4d stato %d%s%n", p.id, p.name, p.recipeNo, p.quantity, p.status,
                    p.status > B1Production.RUNNING ? "  (terminata: non in elenco)" : "");
        }
        System.out.println(list.size() + " produzioni lette, " + active + " attive (stato 1-3)");
        if (!apply) {
            System.out.println("Anteprima: non e' stato scritto nulla (usa --apply per importare).");
            return;
        }
        B1ProductionRepository repo = B1ProductionRepositories.open(file);
        int added = 0;
        for (B1Production p : list) {
            if (repo.importRow(p)) {
                added++;
            }
        }
        System.out.println("Importate " + added + " produzioni in " + repo.location() + " (le altre erano gia' presenti)");
    }
}
