package Banbury1;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Collegamento sinottico <-> editor ricette: confronta la ricetta presente nel DB201 della
 * macchina con l'archivio (sola lettura) e apre l'editor sulla ricetta in macchina.
 */
public final class B1RecipeBridge {

    public enum State { UNKNOWN, SAME, DIFFERENT, NOT_ARCHIVED }

    public static final class Status {
        public final State state;
        public final String name;
        public final String text;

        Status(State state, String name, String text) {
            this.state = state;
            this.name = name;
            this.text = text;
        }
    }

    private static final long PERIOD_MS = 5000;

    /** Nome nel PLC (minuscolo) -> nome in archivio, per le ricette riconosciute dai valori e non dal nome. */
    private static final Map<String, String> ALIASES = new ConcurrentHashMap<>();

    /** Nome da mostrare: quello dell'archivio se la ricetta in macchina e' stata riconosciuta dai valori (es. 'gomma2' -> '4* pass 100'). */
    public static String displayName(String plcName) {
        if (plcName == null) {
            return "";
        }
        String a = ALIASES.get(plcName.trim().toLowerCase());
        return pretty(a != null ? a : plcName);
    }

    /** Nome in archivio da aprire nell'editor (come salvato, ASCII); se non c'e' alias il nome del PLC. */
    public static String archiveName(String plcName) {
        if (plcName == null) {
            return "";
        }
        String a = ALIASES.get(plcName.trim().toLowerCase());
        return a != null ? a : plcName;
    }

    /** I nomi in archivio sono ASCII (PLC): '100o' -> '100\u00B0' come nell'originale WinCC. */
    static String pretty(String name) {
        return name.replaceAll("(?<=\\d)o\\b", "\u00B0");
    }

    private final B1RecipeRepository repo;
    private final B1RecipeLayout layout;
    private final String plcIp;
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "b1-recipe-compare");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile long lastRun;
    private volatile Status status = new Status(State.UNKNOWN, "", "in attesa");

    public B1RecipeBridge(B1RecipeRepository repo, B1RecipeLayout layout, String plcIp) {
        this.repo = repo;
        this.layout = layout;
        this.plcIp = plcIp;
    }

    public B1RecipeRepository repo() {
        return repo;
    }

    public B1RecipeLayout layout() {
        return layout;
    }

    public Status status() {
        return status;
    }

    /** Da chiamare spesso: ricalcola al massimo ogni 5 s, fuori dal thread grafico. */
    public void offer(byte[] db) {
        long now = System.currentTimeMillis();
        if (db == null) {
            status = new Status(State.UNKNOWN, "", "ricetta non letta");
            return;
        }
        if (now - lastRun < PERIOD_MS || !running.compareAndSet(false, true)) {
            return;
        }
        lastRun = now;
        exec.execute(() -> {
            try {
                status = compare(db, repo, layout);
            } finally {
                running.set(false);
            }
        });
    }

    public static Status compare(byte[] db, B1RecipeRepository repo, B1RecipeLayout layout) {
        B1Recipe live = B1RecipeCodec.decode(db, layout);
        String name = live.name == null ? "" : live.name.trim();
        if (name.isEmpty()) {
            return new Status(State.UNKNOWN, "", "nessuna ricetta in macchina");
        }
        try {
            List<B1RecipeRepository.Entry> entries = repo.list();
            int found = 0;
            String lastRev = "";
            for (B1RecipeRepository.Entry e : entries) {
                if (e.name.trim().equalsIgnoreCase(name)) {
                    found++;
                    B1Recipe archived = repo.load(e.uid);
                    if (B1RecipeCodec.sameExecutable(live, archived, layout)) {
                        ALIASES.remove(name.toLowerCase());
                        return new Status(State.SAME, name, "= archivio (rev. " + archived.revision + ")");
                    }
                    lastRev = " (rev. " + archived.revision + ")";
                }
            }
            String key = name.toLowerCase();
            if (found == 0) {
                // nome non in archivio: si cerca una ricetta con gli stessi valori (nome nel PLC diverso da quello di WinCC)
                B1RecipeRepository.Entry same = null;
                B1Recipe sameRecipe = null;
                int equal = 0;
                for (B1RecipeRepository.Entry e : entries) {
                    B1Recipe archived = repo.load(e.uid);
                    if (B1RecipeCodec.sameContent(live, archived, layout)) {
                        if (same == null) {
                            same = e;
                            sameRecipe = archived;
                        }
                        equal++;
                    }
                }
                if (same != null) {
                    ALIASES.put(key, same.name.trim());
                    return new Status(State.SAME, pretty(same.name.trim()), "= archivio (rev. " + sameRecipe.revision + ")"
                            + (equal > 1 ? " +" + (equal - 1) + " uguali" : ""));
                }
                ALIASES.remove(key);
                return new Status(State.NOT_ARCHIVED, name, "non in archivio");
            }
            ALIASES.remove(key);
            return new Status(State.DIFFERENT, name, "DIVERSA dall'archivio" + lastRev);
        } catch (IOException e) {
            return new Status(State.UNKNOWN, name, "archivio non leggibile");
        }
    }

    /** Apre (o porta in primo piano) l'editor; con name seleziona/importa la ricetta in macchina. */
    public void openEditor(String name) {
        B1RecipeEditor.openShared(repo, layout, plcIp, name);
    }
}
