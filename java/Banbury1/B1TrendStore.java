package Banbury1;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Memoria dei valori per i trend: un buffer circolare per serie con gli ultimi 2 giorni (un campione al
 * secondo) e salvataggio su file CSV, cosi' il grafico sopravvive alla chiusura della finestra e al riavvio.
 * File: una riga per campione "epochMs;valore1;valore2;..." (valore vuoto = non disponibile).
 */
public final class B1TrendStore {

    public static final long KEEP_MS = 2L * 24 * 3600 * 1000;
    /** 2 giorni a 1 campione/s = 172.800: capienza con margine. */
    static final int CAPACITY = 190_000;

    /** Buffer circolare di una serie (tempo crescente). */
    public static final class Buffer {
        private final long[] t = new long[CAPACITY];
        private final float[] v = new float[CAPACITY];
        private int head;
        private int size;

        synchronized void add(long time, double value, long keepMs) {
            if (size > 0 && time < t[idx(size - 1)]) {
                return; // campione fuori ordine: ignorato
            }
            while (size > 0 && t[head] < time - keepMs) {
                head = (head + 1) % CAPACITY;
                size--;
            }
            if (size == CAPACITY) {
                head = (head + 1) % CAPACITY;
                size--;
            }
            int i = idx(size);
            t[i] = time;
            v[i] = (float) value;
            size++;
        }

        private int idx(int logical) {
            return (head + logical) % CAPACITY;
        }

        public synchronized int size() {
            return size;
        }

        /** Primo indice logico con tempo >= x (ricerca binaria). */
        private int lowerBound(long x) {
            int lo = 0;
            int hi = size;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (t[idx(mid)] < x) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            return lo;
        }

        /** Come B1TrendChart.downsample: al massimo un minimo e un massimo per colonna di pixel. */
        public synchronized List<double[]> downsample(long from, long to, int columns) {
            List<double[]> out = new ArrayList<>();
            if (size == 0 || columns < 1 || to <= from) {
                return out;
            }
            double span = (double) (to - from) / columns;
            int curCol = Integer.MIN_VALUE;
            double[] lo = null;
            double[] hi = null;
            for (int i = lowerBound(from); i < size; i++) {
                int k = idx(i);
                if (t[k] > to) {
                    break;
                }
                double[] p = {t[k], v[k]};
                int col = (int) ((p[0] - from) / span);
                if (col != curCol) {
                    flush(out, lo, hi);
                    curCol = col;
                    lo = p;
                    hi = p;
                } else {
                    if (p[1] < lo[1]) {
                        lo = p;
                    }
                    if (p[1] > hi[1]) {
                        hi = p;
                    }
                }
            }
            flush(out, lo, hi);
            return out;
        }

        private static void flush(List<double[]> out, double[] lo, double[] hi) {
            if (lo == null) {
                return;
            }
            if (lo == hi) {
                out.add(lo);
            } else if (lo[0] <= hi[0]) {
                out.add(lo);
                out.add(hi);
            } else {
                out.add(hi);
                out.add(lo);
            }
        }

        /** Valore del campione piu' vicino a time, null se piu' lontano di maxGapMs. */
        public synchronized Double nearest(long time, long maxGapMs) {
            if (size == 0) {
                return null;
            }
            int i = lowerBound(time);
            double best = Double.NaN;
            long bestD = Long.MAX_VALUE;
            for (int k = Math.max(0, i - 1); k <= Math.min(size - 1, i); k++) {
                long d = Math.abs(t[idx(k)] - time);
                if (d < bestD) {
                    bestD = d;
                    best = v[idx(k)];
                }
            }
            return bestD <= maxGapMs ? best : null;
        }

        public synchronized long firstTime() {
            return size == 0 ? -1 : t[head];
        }
    }

    /** Cambio di stato nel tempo (es. ricetta in macchina, modo Auto/Manu). */
    public static final class Event {
        public final long time;
        public final String text;

        Event(long time, String text) {
            this.time = time;
            this.text = text;
        }
    }

    private static B1TrendStore shared;

    /** Archivio condiviso (finestra e registratore usano lo stesso). */
    public static synchronized B1TrendStore shared(List<String> ids) {
        if (shared == null) {
            shared = new B1TrendStore(ids);
        }
        return shared;
    }

    private final List<String> ids;
    private final Map<String, Buffer> buffers = new LinkedHashMap<>();
    private final Map<String, List<Event>> events = new LinkedHashMap<>();
    private BufferedWriter writer;
    private Path file;

    public B1TrendStore(List<String> ids) {
        this.ids = new ArrayList<>(ids);
        for (String id : ids) {
            buffers.put(id, new Buffer());
        }
    }

    /** Registra un cambio di stato (solo se diverso dall'ultimo registrato per quel tipo). */
    public synchronized void addEvent(String kind, long time, String text) {
        List<Event> l = events.computeIfAbsent(kind, k -> new ArrayList<>());
        String clean = text == null ? "" : text.replace(';', ',').replace('\n', ' ').trim();
        if (!l.isEmpty()) {
            Event last = l.get(l.size() - 1);
            if (last.text.equals(clean) || time < last.time) {
                return;
            }
        }
        l.add(new Event(time, clean));
        while (!l.isEmpty() && l.size() > 1 && l.get(1).time < time - KEEP_MS) {
            l.remove(0); // si tiene un evento di partenza prima della finestra
        }
        if (writer != null) {
            try {
                writer.write("E;" + kind + ";" + time + ";" + clean);
                writer.newLine();
            } catch (IOException e) {
                closeQuietly();
            }
        }
    }

    /** Copia degli eventi di un tipo, in ordine di tempo. */
    public synchronized List<Event> events(String kind) {
        List<Event> l = events.get(kind);
        return l == null ? new ArrayList<>() : new ArrayList<>(l);
    }

    public Buffer buffer(String id) {
        Buffer b = buffers.get(id);
        if (b == null) {
            throw new IllegalArgumentException("Serie sconosciuta: " + id);
        }
        return b;
    }

    /** Aggiunge un campione per ogni serie (NaN = non disponibile) e lo accoda al file, se aperto. */
    public synchronized void add(long time, double[] values) {
        boolean any = false;
        StringBuilder line = new StringBuilder().append(time);
        for (int i = 0; i < ids.size(); i++) {
            double v = i < values.length ? values[i] : Double.NaN;
            line.append(';');
            if (!Double.isNaN(v)) {
                buffers.get(ids.get(i)).add(time, v, KEEP_MS);
                line.append(v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v));
                any = true;
            }
        }
        if (any && writer != null) {
            try {
                writer.write(line.toString());
                writer.newLine();
            } catch (IOException e) {
                closeQuietly();
            }
        }
    }

    public synchronized void flush() {
        if (writer != null) {
            try {
                writer.flush();
            } catch (IOException e) {
                closeQuietly();
            }
        }
    }

    /**
     * Carica il file (solo gli ultimi 2 giorni), lo riscrive senza le righe vecchie e lo apre in aggiunta.
     * Ritorna il numero di campioni caricati.
     */
    public synchronized int attachFile(Path f, long now) throws IOException {
        closeQuietly();
        this.file = f;
        int loaded = 0;
        List<String> keep = new ArrayList<>();
        if (Files.isReadable(f)) {
            long min = now - KEEP_MS;
            try (BufferedReader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] p = line.split(";", -1);
                    if (p.length >= 4 && "E".equals(p[0])) {
                        try {
                            long te = Long.parseLong(p[2]);
                            if (te >= min && te <= now + 60_000) {
                                keep.add(line);
                                List<Event> l = events.computeIfAbsent(p[1], k -> new ArrayList<>());
                                if (l.isEmpty() || (!l.get(l.size() - 1).text.equals(p[3]) && te >= l.get(l.size() - 1).time)) {
                                    l.add(new Event(te, p[3]));
                                }
                            }
                        } catch (NumberFormatException ignored) {
                            // riga evento illeggibile
                        }
                        continue;
                    }
                    long t;
                    try {
                        t = Long.parseLong(p[0]);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    if (t < min || t > now + 60_000) {
                        continue;
                    }
                    keep.add(line);
                    for (int i = 0; i < ids.size() && i + 1 < p.length; i++) {
                        if (!p[i + 1].isEmpty()) {
                            try {
                                buffers.get(ids.get(i)).add(t, Double.parseDouble(p[i + 1]), KEEP_MS);
                            } catch (NumberFormatException ignored) {
                                // campo illeggibile: saltato
                            }
                        }
                    }
                    loaded++;
                }
            }
        }
        if (f.getParent() != null) {
            Files.createDirectories(f.getParent());
        }
        Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
        Files.write(tmp, keep, StandardCharsets.UTF_8);
        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        writer = Files.newBufferedWriter(f, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        return loaded;
    }

    public synchronized void close() {
        flush();
        closeQuietly();
    }

    private void closeQuietly() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
                // niente da fare
            }
            writer = null;
        }
    }
}
