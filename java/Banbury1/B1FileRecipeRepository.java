package Banbury1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Una ricetta = un file testo {uid}.b1recipe in una cartella (anche condivisa di rete,
 * es. \\srv-bambury\Mondo\RecipesB1), cosi' piu' postazioni possono modificarle.
 * Salvataggio atomico e controllo di revisione contro modifiche concorrenti.
 */
public final class B1FileRecipeRepository implements B1RecipeRepository {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path dir;
    private final B1RecipeLayout layout;

    public B1FileRecipeRepository(Path dir, B1RecipeLayout layout) throws IOException {
        this.dir = dir;
        this.layout = layout;
        Files.createDirectories(dir);
    }

    @Override
    public String location() {
        return dir.toString();
    }

    private Path file(String uid) {
        if (!uid.matches("[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("UID non valido: " + uid);
        }
        return dir.resolve(uid + ".b1recipe");
    }

    @Override
    public List<Entry> list() throws IOException {
        List<Entry> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.b1recipe")) {
            for (Path p : ds) {
                try {
                    Map<String, String> m = read(p);
                    out.add(new Entry(m.getOrDefault("uid", ""), m.getOrDefault("name", "?"),
                            m.getOrDefault("modified", ""), m.getOrDefault("modifiedBy", "")));
                } catch (IOException e) {
                    // file illeggibile: lo ignora
                }
            }
        }
        out.sort(Comparator.comparing((Entry e) -> e.name.toLowerCase()));
        return out;
    }

    @Override
    public B1Recipe load(String uid) throws IOException {
        Map<String, String> m = read(file(uid));
        B1Recipe r = B1Recipe.blank(layout);
        r.uid = m.getOrDefault("uid", uid);
        r.name = m.getOrDefault("name", "");
        r.recipeNo = i(m.get("recipeNo"));
        r.revision = Long.parseLong(m.getOrDefault("rev", "0"));
        r.modified = m.getOrDefault("modified", "");
        r.modifiedBy = m.getOrDefault("modifiedBy", "");
        for (B1RecipeLayout.Field f : layout.header) {
            r.header.put(f.key, i(m.get("h." + f.key)));
        }
        for (int k = 0; k < layout.phaseCount; k++) {
            B1Recipe.Phase p = r.phases.get(k);
            for (B1RecipeLayout.Field f : layout.phase) {
                p.values.put(f.key, i(m.get("p" + k + "." + f.key)));
            }
            p.ops = i(m.get("p" + k + ".ops"));
        }
        return r;
    }

    @Override
    public synchronized void save(B1Recipe r, String user) throws IOException {
        Path f = file(r.uid);
        if (Files.exists(f)) {
            long onDisk = Long.parseLong(read(f).getOrDefault("rev", "0"));
            if (onDisk != r.revision) {
                throw new ConflictException("La ricetta e' stata modificata da un'altra postazione (revisione "
                        + onDisk + " invece di " + r.revision + "). Riaprirla per non perdere le modifiche.");
            }
        }
        long rev = r.revision + 1;
        String now = LocalDateTime.now().format(TS);
        Path tmp = dir.resolve(r.uid + ".tmp");
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(tmp, StandardCharsets.UTF_8))) {
            w.println("uid=" + r.uid);
            w.println("name=" + esc(r.name));
            w.println("recipeNo=" + r.recipeNo);
            w.println("rev=" + rev);
            w.println("modified=" + now);
            w.println("modifiedBy=" + esc(user));
            for (B1RecipeLayout.Field fl : layout.header) {
                w.println("h." + fl.key + "=" + r.header.getOrDefault(fl.key, 0));
            }
            for (int k = 0; k < layout.phaseCount && k < r.phases.size(); k++) {
                B1Recipe.Phase p = r.phases.get(k);
                for (B1RecipeLayout.Field fl : layout.phase) {
                    w.println("p" + k + "." + fl.key + "=" + p.values.getOrDefault(fl.key, 0));
                }
                w.println("p" + k + ".ops=" + p.ops);
            }
        }
        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        r.revision = rev;
        r.modified = now;
        r.modifiedBy = user;
    }

    @Override
    public void delete(String uid) throws IOException {
        Files.deleteIfExists(file(uid));
    }

    private static Map<String, String> read(Path p) throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        try (BufferedReader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String s;
            while ((s = r.readLine()) != null) {
                int eq = s.indexOf('=');
                if (eq > 0) {
                    m.put(s.substring(0, eq), unesc(s.substring(eq + 1)));
                }
            }
        }
        return m;
    }

    private static int i(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "");
    }

    private static String unesc(String s) {
        return s.replace("\\n", "\n").replace("\\\\", "\\");
    }
}
