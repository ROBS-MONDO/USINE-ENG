package Banbury1;

import java.io.IOException;
import java.util.List;
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
                        return new Status(State.SAME, name, "= archivio (rev. " + archived.revision + ")");
                    }
                    lastRev = " (rev. " + archived.revision + ")";
                }
            }
            if (found == 0) {
                return new Status(State.NOT_ARCHIVED, name, "non in archivio");
            }
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
