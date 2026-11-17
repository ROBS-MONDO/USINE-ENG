package Banbury1;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Produzioni B1 su file (alternativa se SQL non e' disponibile): una riga per produzione, campi separati da tabulazione. */
public final class B1FileProductionRepository implements B1ProductionRepository {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path file;
    private final String note;

    public B1FileProductionRepository(Path file, String note) {
        this.file = file;
        this.note = note;
    }

    @Override
    public String location() {
        return file + (note == null || note.isEmpty() ? "" : "  " + note);
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }

    private List<B1Production> readAll() throws IOException {
        List<B1Production> out = new ArrayList<>();
        if (!Files.isReadable(file)) {
            return out;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String[] f = line.split("\t", -1);
            if (f.length < 10 || line.startsWith("#")) {
                continue;
            }
            try {
                B1Production p = new B1Production();
                p.id = Integer.parseInt(f[0]);
                p.name = f[1];
                p.description = f[2];
                p.recipeNo = Integer.parseInt(f[3]);
                p.quantity = Integer.parseInt(f[4]);
                p.priority = Integer.parseInt(f[5]);
                p.status = Integer.parseInt(f[6]);
                p.created = LocalDateTime.parse(f[7], TS);
                p.updated = LocalDateTime.parse(f[8], TS);
                p.customerDate = f[9];
                out.add(p);
            } catch (RuntimeException e) {
                // riga illeggibile: si salta
            }
        }
        return out;
    }

    private void writeAll(List<B1Production> all) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        List<String> lines = new ArrayList<>();
        lines.add("# id\tnome\tdescrizione\tricetta\tquantita\tpriorita\tstato\tcreazione\tmodifica\tdata_cliente");
        for (B1Production p : all) {
            lines.add(p.id + "\t" + clean(p.name) + "\t" + clean(p.description) + "\t" + p.recipeNo + "\t" + p.quantity + "\t"
                    + p.priority + "\t" + p.status + "\t" + p.created.format(TS) + "\t" + p.updated.format(TS) + "\t" + clean(p.customerDate));
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, lines, StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public synchronized List<B1Production> list() throws IOException {
        List<B1Production> out = new ArrayList<>();
        for (B1Production p : readAll()) {
            if (p.status <= B1Production.RUNNING) {
                out.add(p);
            }
        }
        out.sort(Comparator.comparingInt((B1Production p) -> p.priority).thenComparing(p -> p.name.toLowerCase()).thenComparingInt(p -> p.id));
        return out;
    }

    @Override
    public synchronized B1Production load(int id) throws IOException {
        for (B1Production p : readAll()) {
            if (p.id == id) {
                return p;
            }
        }
        return null;
    }

    @Override
    public synchronized B1Production save(B1Production p) throws IOException {
        List<String> err = p.validate();
        if (!err.isEmpty()) {
            throw new IOException(err.get(0));
        }
        List<B1Production> all = readAll();
        B1Production s = p.copy();
        s.name = s.name.trim();
        s.updated = LocalDateTime.now();
        int idx = -1;
        int max = 0;
        for (int i = 0; i < all.size(); i++) {
            max = Math.max(max, all.get(i).id);
            if (s.id > 0 && all.get(i).id == s.id) {
                idx = i;
            }
        }
        if (idx >= 0) {
            all.set(idx, s);
        } else {
            if (s.id <= 0) {
                s.id = max + 1;
                s.created = s.updated;
                s.status = B1Production.NEW;
                s.customerDate = s.updated.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            }
            all.add(s);
        }
        writeAll(all);
        return s;
    }

    @Override
    public synchronized boolean importRow(B1Production p) throws IOException {
        List<B1Production> all = readAll();
        for (B1Production q : all) {
            if (q.id == p.id) {
                return false;
            }
        }
        all.add(p.copy());
        writeAll(all);
        return true;
    }

    private void setStatus(int id, int status) throws IOException {
        List<B1Production> all = readAll();
        for (B1Production p : all) {
            if (p.id == id) {
                p.status = status;
                p.updated = LocalDateTime.now();
            }
        }
        writeAll(all);
    }

    @Override
    public synchronized void end(int id) throws IOException {
        setStatus(id, B1Production.ENDED);
    }

    @Override
    public synchronized void setRunning(int id) throws IOException {
        setStatus(id, B1Production.RUNNING);
    }

    @Override
    public synchronized boolean nameExists(String name, int exceptId) throws IOException {
        for (B1Production p : readAll()) {
            if (p.status <= B1Production.RUNNING && p.id != exceptId && p.name.trim().equalsIgnoreCase(name.trim())) {
                return true;
            }
        }
        return false;
    }
}
