package Banbury1;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Importa nell'archivio ricette di B1 le ricette del database WinCC (Q131014), a partire dal file prodotto da
 * sql/export_wincc_recipes.sql. Senza --apply mostra soltanto cosa importerebbe (non scrive niente).
 * Uso: java Banbury1.B1WinccRecipeImport recipes_out.txt [--apply] [--update-numbers] [--dir cartella] [--available]
 * Corrispondenze: D_RECIPES.NUM_DATA1..5 = tempo sicurezza (s), temp. sicurezza, energia max (kWh), porte de dechargement (s),
 * temp. riavvio; NUM_DATA6 = tempo ciclo (s, non presente nel DB201: solo riportato). Valori di fase (D_RECIPE_PHASE_PARAMS, gia' grezzi
 * come nel PLC): parametro 1 operazioni, 2 timeout, 3 rotor, 4 pressione, 5 bit set, 6 bit controllo, 7 tempo fase, 8 temperatura, 9 energia.
 */
public final class B1WinccRecipeImport {

    private B1WinccRecipeImport() {
    }

    /** Una tabella del file di esportazione: intestazione + righe di valori (separatore ';'). */
    static Map<String, List<Map<String, String>>> parse(List<String> lines) {
        Map<String, List<Map<String, String>>> out = new LinkedHashMap<>();
        String section = null;
        String[] head = null;
        boolean afterHead = false;
        for (String l : lines) {
            if (l.startsWith("### ")) {
                section = l.substring(4).trim();
                String key = section.split("[ (]")[0].replaceAll("[:;]+$", "");
                out.put(key, new ArrayList<>());
                section = key;
                head = null;
                continue;
            }
            if (section == null || l.trim().isEmpty() || l.startsWith("(") || l.contains(" rows affected")) {
                continue;
            }
            if (head == null) {
                head = l.split(";", -1);
                afterHead = true;
                continue;
            }
            if (afterHead) { // riga dei trattini
                afterHead = false;
                if (l.startsWith("-")) {
                    continue;
                }
            }
            String[] v = l.split(";", -1);
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < head.length && i < v.length; i++) {
                row.put(head[i].trim(), v[i].trim());
            }
            out.get(section).add(row);
        }
        return out;
    }

    private static int num(String s) {
        if (s == null || s.isEmpty() || s.equals("NULL")) {
            return 0;
        }
        try {
            return (int) Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Nome adatto all'editor: solo ASCII, massimo 22 caratteri. */
    static String cleanName(String n) {
        StringBuilder sb = new StringBuilder();
        for (char c : n.trim().toCharArray()) {
            if (c == '\u00b0' || c == '\u00ba') {
                sb.append('o');
            } else if (c >= 32 && c < 127) {
                sb.append(c);
            } else {
                sb.append('?');
            }
        }
        String s = sb.toString().trim();
        return s.length() > 22 ? s.substring(0, 22).trim() : s;
    }

    static List<B1Recipe> convert(Map<String, List<Map<String, String>>> t, B1RecipeLayout l, boolean onlyAvailable,
                                  List<String> notes) {
        Map<Integer, List<Map<String, String>>> values = new LinkedHashMap<>();
        for (Map<String, String> r : t.getOrDefault("D_RECIPE_PHASE_PARAMS", new ArrayList<>())) {
            values.computeIfAbsent(num(r.get("ID_RECIPE")), k -> new ArrayList<>()).add(r);
        }
        String[] phaseKeys = {null, "ops", "timeout", "rpm", "press", "set_bits", "ctrl_bits", "time", "temp", "energy"};
        List<B1Recipe> out = new ArrayList<>();
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Map<String, String> d : t.getOrDefault("D_RECIPES", new ArrayList<>())) {
            if (onlyAvailable && num(d.get("FLAG_AVAILABLE")) == 0) {
                continue;
            }
            B1Recipe r = B1Recipe.blank(l);
            r.name = cleanName(d.getOrDefault("NAME", ""));
            if (r.name.isEmpty() || !names.add(r.name.toLowerCase())) {
                notes.add("saltata (nome vuoto o duplicato): '" + d.get("NAME") + "' (ID " + d.get("ID") + ")");
                continue;
            }
            if (!r.name.equals(d.get("NAME").trim())) {
                notes.add("nome adattato: '" + d.get("NAME") + "' -> '" + r.name + "'");
            }
            r.recipeNo = num(d.get("ID")); // il PLC riceve l'ID della ricetta WinCC (DBW2)
            r.header.put("safety_time", num(d.get("NUM_DATA1")));
            r.header.put("safety_temp", num(d.get("NUM_DATA2")));
            r.header.put("max_energy", num(d.get("NUM_DATA3")) * 10); // il DB tiene kWh, il PLC x10
            r.header.put("drop_door", num(d.get("NUM_DATA4")));
            r.header.put("restart_temp", num(d.get("NUM_DATA5")));
            for (Map<String, String> v : values.getOrDefault(num(d.get("ID")), new ArrayList<>())) {
                int fase = num(v.get("ID"));
                int param = num(v.get("ID_PARAM"));
                if (fase < 1 || fase > l.phaseCount || param < 1 || param >= phaseKeys.length) {
                    continue;
                }
                B1Recipe.Phase p = r.phases.get(fase - 1);
                int val = num(v.get("VALUE_PARAM"));
                if (param == 1) {
                    p.ops = val;
                } else {
                    p.values.put(phaseKeys[param], val);
                }
            }
            out.add(r);
        }
        return out;
    }

    public static void main(String[] a) throws Exception {
        if (a.length < 1) {
            System.err.println("Uso: java Banbury1.B1WinccRecipeImport recipes_out.txt [--apply] [--update-numbers] [--dir cartella] [--available]");
            System.exit(2);
        }
        Path file = Paths.get(a[0]);
        boolean apply = false;
        boolean updateNumbers = false;
        boolean available = false;
        Path dir = B1RecipeEditor.DEFAULT_DIR;
        for (int i = 1; i < a.length; i++) {
            switch (a[i]) {
                case "--apply": apply = true; break;
                case "--update-numbers": updateNumbers = true; break;
                case "--available": available = true; break;
                case "--dir": dir = Paths.get(a[++i]); break;
                default:
            }
        }
        List<String> lines = Files.readAllLines(file, Charset.forName("windows-1252"));
        B1RecipeLayout layout = B1RecipeLayout.defaults();
        List<String> notes = new ArrayList<>();
        List<B1Recipe> recipes = convert(parse(lines), layout, available, notes);
        int invalid = 0;
        for (B1Recipe r : recipes) {
            List<String> err = B1RecipeCodec.validate(r, layout);
            int used = 0;
            for (B1Recipe.Phase p : r.phases) {
                used += p.isEmpty() ? 0 : 1;
            }
            System.out.printf("%-24s fasi usate %2d  sicurezza %ds/%d C  %s%n", r.name, used, r.header.get("safety_time"),
                    r.header.get("safety_temp"), err.isEmpty() ? "ok" : "NON VALIDA: " + err.get(0));
            if (!err.isEmpty()) {
                invalid++;
            }
        }
        notes.forEach(n -> System.out.println("nota: " + n));
        System.out.println(recipes.size() + " ricette convertite, " + invalid + " con avvisi di validita'");
        if (!apply) {
            System.out.println("Anteprima: non e' stato scritto nulla (usa --apply per importare nell'archivio ricette).");
            return;
        }
        B1RecipeRepository repo = B1RecipeRepositories.open(layout, dir);
        int saved = 0;
        List<String> existing = new ArrayList<>();
        Map<String, String> uidByName = new LinkedHashMap<>();
        for (B1RecipeRepository.Entry e : repo.list()) {
            existing.add(e.name.toLowerCase());
            uidByName.put(e.name.toLowerCase(), e.uid);
        }
        int renumbered = 0;
        for (B1Recipe r : recipes) {
            if (existing.contains(r.name.toLowerCase())) {
                if (updateNumbers) {
                    B1Recipe old = repo.load(uidByName.get(r.name.toLowerCase()));
                    if (old.recipeNo != r.recipeNo) {
                        old.recipeNo = r.recipeNo;
                        repo.save(old, "import WinCC (numero)");
                        renumbered++;
                        System.out.println("numero aggiornato: " + r.name + " -> " + r.recipeNo);
                    }
                    continue;
                }
                System.out.println("gia' presente, saltata: " + r.name);
                continue;
            }
            repo.save(r, "import WinCC");
            saved++;
        }
        System.out.println("Importate " + saved + " ricette in " + repo.location()
                + (updateNumbers ? "; numeri ricetta aggiornati: " + renumbered : ""));
    }
}
