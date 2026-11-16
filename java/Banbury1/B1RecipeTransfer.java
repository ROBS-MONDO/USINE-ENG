package Banbury1;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Invio di una ricetta al PLC (DB201): anteprima, controlli, copia di sicurezza, scrittura e verifica.
 * La scrittura e' DISABILITATA di default: servono sia {@code enabled=true} sia {@code allow_raw_write=true} in
 * b1_recipe_transfer.properties. La sequenza e' quella del WinCC originale, ricavata dal registro B1DbDump di due invii
 * (Start = DB201.DBX41.0, parole della ricetta, Fine = DB201.DBX383.0, risposta del PLC in DB110.DBW44).
 */
public final class B1RecipeTransfer {

    private B1RecipeTransfer() {
    }

    // Sequenza di trasferimento osservata sul WinCC originale (registro B1DbDump del 16/11/2026, due invii):
    //  1. a riposo DB110.DBW44 = 0x100A (bit 12 = trasferimento abilitato, bit 3 = ricetta valida);
    //  2. il WinCC pone DB201.DBX41.0 = 1 (Start transfert): il PLC porta DBW44 a 0x0006 (bit 2 = trasferimento in corso);
    //  3. il WinCC scrive le parole cambiate della ricetta (testata, fasi), poi il numero ricetta (DBW2);
    //  4. il WinCC pone DBX383.0 = 1 (parola DB201.DBW382 = 1, fine trasferimento);
    //  5. dopo circa 9 s il PLC riporta DBW44 a 0x100A e azzera DBW40 e DBW382.
    static final int W44 = 44;
    static final int BIT_ENABLED = 12;
    static final int BIT_BUSY = 2;
    static final int START_WORD = 40; // DBX41.0 = bit 0 della parola
    static final int END_WORD = 382;  // DBX383.0 = bit 0 della parola

    /** Impostazioni (file b1_recipe_transfer.properties). */
    public static final class Config {
        public boolean enabled;
        public boolean allowRawWrite;
        /** Sequenza di trasferimento osservata dal WinCC (consigliata). false = solo scrittura delle parole (prove). */
        public boolean handshake = true;
        /** Attesa massima della risposta del PLC, in secondi. */
        public int waitSeconds = 60;
        public Path backupDir = Paths.get("C:\\_tools\\Banbury1\\Recipes\\backup");

        public static Config load() {
            Config c = new Config();
            Path[] cands = {Paths.get("C:\\_tools\\Banbury1\\b1_recipe_transfer.properties"), Paths.get("b1_recipe_transfer.properties")};
            for (Path f : cands) {
                if (Files.isReadable(f)) {
                    Properties p = new Properties();
                    try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                        p.load(r);
                        c.enabled = "true".equalsIgnoreCase(p.getProperty("enabled", "false").trim());
                        c.allowRawWrite = "true".equalsIgnoreCase(p.getProperty("allow_raw_write", "false").trim());
                        c.handshake = !"false".equalsIgnoreCase(p.getProperty("handshake", "true").trim());
                        try {
                            c.waitSeconds = Integer.parseInt(p.getProperty("wait_seconds", "60").trim());
                        } catch (NumberFormatException ignored) {
                            // resta 60
                        }
                        String bd = p.getProperty("backup_dir", "").trim();
                        if (!bd.isEmpty()) {
                            c.backupDir = Paths.get(bd);
                        }
                    } catch (IOException e) {
                        System.err.println("Trasferimento ricetta: configurazione illeggibile (" + e.getMessage() + ")");
                    }
                    break;
                }
            }
            return c;
        }

        public boolean writeAllowed() {
            return enabled && allowRawWrite;
        }
    }

    /** Una parola (16 bit) che cambierebbe nel DB. */
    public static final class Change {
        public final int offset;
        public final int oldValue;
        public final int newValue;
        public final String what;

        Change(int offset, int oldValue, int newValue, String what) {
            this.offset = offset;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.what = what;
        }
    }

    /** Risultato dell'analisi: cosa cambierebbe e perche' l'invio e' (o non e') possibile. */
    public static final class Plan {
        public final List<Change> changes = new ArrayList<>();
        public final List<String> blockers = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
        public byte[] current;
        public byte[] image;
        public int db;
        // ricetta inviata (per il registro degli invii)
        public String recipeUid = "";
        public String recipeName = "";
        public int recipeNo;
        public long recipeRevision;
    }

    /**
     * Prepara l'invio. {@code currentDb} = immagine attuale del DB201 nel PLC, {@code db110} = DB110 (stato macchina,
     * puo' essere null). Non scrive niente.
     */
    public static Plan plan(B1Recipe r, B1RecipeLayout l, byte[] currentDb, byte[] db110) {
        Plan p = new Plan();
        p.db = l.db;
        p.recipeUid = r.uid;
        p.recipeName = r.name == null ? "" : r.name.trim();
        p.recipeNo = r.recipeNo;
        p.recipeRevision = r.revision;
        p.current = currentDb;
        for (String e : B1RecipeCodec.validate(r, l)) {
            p.blockers.add("Ricetta non valida: " + e);
        }
        if (currentDb == null || currentDb.length < l.phaseBase + l.phaseCount * l.phaseSize) {
            p.blockers.add("DB" + l.db + " del PLC non letto o troppo corto (" + (currentDb == null ? 0 : currentDb.length)
                    + " byte, attesi almeno " + (l.phaseBase + l.phaseCount * l.phaseSize) + ")");
            return p;
        }
        // spazio reale per il nome: i caratteri partono da nameOffset+2 e si fermano prima del primo campo di testata
        int firstHeader = l.phaseBase;
        for (B1RecipeLayout.Field f : l.header) {
            firstHeader = Math.min(firstHeader, f.offset);
        }
        int nameSpace = Math.min(l.nameMax, firstHeader - (l.nameOffset + 2));
        String nm = r.name == null ? "" : r.name.trim();
        if (nm.length() > nameSpace) {
            p.blockers.add("Nome ricetta di " + nm.length() + " caratteri: nel DB" + l.db + " ce n'e' spazio solo per " + nameSpace
                    + " (oltre si sovrascriverebbe la testata a DBW" + firstHeader + ")");
            return p;
        }
        p.image = B1RecipeCodec.encode(r, l, currentDb);
        // stringa S7 [max][lunghezza][caratteri]: i byte dopo la lunghezza non contano e restano come sono nel PLC
        int strLen = p.image[l.nameOffset + 1] & 0xFF;
        for (int i = l.nameOffset + 2 + strLen; i < l.nameOffset + 2 + nameSpace && i < p.image.length; i++) {
            p.image[i] = currentDb[i];
        }
        for (int off = 0; off + 1 < p.image.length; off += 2) {
            int o = S7ReadClient.int16(currentDb, off) & 0xFFFF;
            int n = S7ReadClient.int16(p.image, off) & 0xFFFF;
            if (o != n) {
                p.changes.add(new Change(off, o, n, describe(off, l)));
            }
        }
        if (p.changes.isEmpty()) {
            p.warnings.add("La ricetta nel PLC e' gia' uguale: non c'e' niente da scrivere.");
        }
        if (db110 != null && db110.length >= 62) {
            int rpm = S7ReadClient.int16(db110, 60);
            if (rpm != 0) {
                p.blockers.add("Rotori in movimento (" + rpm + " rpm): invio consentito solo a macchina ferma");
            }
            int w44 = S7ReadClient.int16(db110, W44);
            if (((w44 >> BIT_ENABLED) & 1) == 0) {
                p.blockers.add("Il PLC non abilita il trasferimento (DB110.DBW44 bit 12 = 0, DBW44 = 0x"
                        + Integer.toHexString(w44 & 0xFFFF).toUpperCase() + ")");
            }
            if (((w44 >> BIT_BUSY) & 1) != 0) {
                p.blockers.add("Trasferimento ricetta gia' in corso nel PLC (DB110.DBW44 bit 2 = 1)");
            }
        } else {
            p.warnings.add("Stato macchina (DB110) non letto: controllo rotori fermi non eseguito");
        }
        return p;
    }

    /** Descrizione leggibile del campo che contiene l'offset indicato. */
    static String describe(int off, B1RecipeLayout l) {
        if (off == l.recipeNoOffset) {
            return "N. ricetta";
        }
        for (B1RecipeLayout.Field f : l.header) {
            if (f.offset == off) {
                return "Testata: " + f.label;
            }
        }
        if (off >= l.nameOffset && off < l.nameOffset + l.nameMax + 2) {
            return "Nome ricetta";
        }
        if (off >= l.phaseBase) {
            int rel = off - l.phaseBase;
            int ph = rel / l.phaseSize;
            int in = rel % l.phaseSize;
            if (ph < l.phaseCount) {
                if (in == l.opsOffset) {
                    return "Fase " + (ph + 1) + ": operazioni";
                }
                for (B1RecipeLayout.Field f : l.phase) {
                    if (f.offset == in) {
                        return "Fase " + (ph + 1) + ": " + f.label;
                    }
                }
                return "Fase " + (ph + 1) + ": parola +" + in;
            }
        }
        return "(non descritto)";
    }

    /** Testo dell'anteprima mostrato all'operatore. */
    public static String describe(Plan p, Config cfg, String host) {
        StringBuilder sb = new StringBuilder();
        sb.append("PLC ").append(host).append("  -  DB").append(p.db).append("\n\n");
        if (!p.blockers.isEmpty()) {
            sb.append("INVIO NON POSSIBILE:\n");
            for (String b : p.blockers) {
                sb.append("  - ").append(b).append("\n");
            }
            sb.append("\n");
        }
        for (String w : p.warnings) {
            sb.append("Attenzione: ").append(w).append("\n");
        }
        if (!p.warnings.isEmpty()) {
            sb.append("\n");
        }
        sb.append(p.changes.size()).append(" parole da modificare:\n");
        for (Change c : p.changes) {
            sb.append(String.format("  DB%d.DBW%-4d %-34s 0x%04X (%d)  ->  0x%04X (%d)%n", p.db, c.offset, c.what,
                    c.oldValue, (short) c.oldValue, c.newValue, (short) c.newValue));
        }
        sb.append("\n");
        if (cfg.writeAllowed()) {
            sb.append("Scrittura ABILITATA. Sequenza: copia di sicurezza di DB").append(p.db).append(" in ").append(cfg.backupDir)
                    .append("\n  1) Start transfert (DB201.DBX41.0) e attesa della presa in carico (DB110.DBW44 bit 2)")
                    .append("\n  2) scrittura delle parole cambiate, numero ricetta per ultimo")
                    .append("\n  3) fine trasferimento (DB201.DBX383.0) e attesa che il PLC chiuda lo scambio (fino a ")
                    .append(cfg.waitSeconds).append(" s)\n  4) rilettura e confronto del DB.\n");
        } else {
            sb.append("Scrittura DISABILITATA (modalita' anteprima): per abilitarla servono enabled=true e allow_raw_write=true\n")
                    .append("in b1_recipe_transfer.properties. La sequenza di trasferimento e' quella del WinCC (registro del 16/11/2026).\n");
        }
        return sb.toString();
    }

    /**
     * Esegue l'invio con la sequenza del WinCC: controlla, salva la copia di sicurezza, pone Start, scrive le parole cambiate
     * (numero ricetta per ultimo), pone la fine, attende che il PLC chiuda lo scambio e rilegge il DB per confrontarlo.
     * Ritorna il percorso della copia di sicurezza.
     */
    public static Path execute(Plan p, Config cfg, String host, int port, int sizeLimit) throws IOException {
        if (!cfg.writeAllowed()) {
            throw new IOException("Scrittura non abilitata (b1_recipe_transfer.properties)");
        }
        if (!p.blockers.isEmpty() || p.image == null) {
            throw new IOException("Invio bloccato: " + String.join("; ", p.blockers));
        }
        if (p.changes.isEmpty()) {
            throw new IOException("Niente da scrivere");
        }
        try (S7WriteClient plc = new S7WriteClient(host, port, 0, 2, 3000)) {
            plc.connect();
            byte[] now = plc.readDb(p.db, 0, p.current.length);
            if (!java.util.Arrays.equals(now, p.current)) {
                throw new IOException("Il DB" + p.db + " nel PLC e' cambiato dopo l'anteprima: annullato, rifare l'anteprima");
            }
            if (cfg.handshake) {
                byte[] st = plc.readDb(110, 0, 64);
                int w44 = S7ReadClient.int16(st, W44);
                if (((w44 >> BIT_ENABLED) & 1) == 0 || ((w44 >> BIT_BUSY) & 1) != 0) {
                    throw new IOException("Il PLC non e' pronto al trasferimento (DB110.DBW44 = 0x"
                            + Integer.toHexString(w44 & 0xFFFF).toUpperCase() + ")");
                }
                if (S7ReadClient.int16(st, 60) != 0) {
                    throw new IOException("Rotori in movimento: invio annullato");
                }
            }
            Files.createDirectories(cfg.backupDir);
            Path backup = cfg.backupDir.resolve("DB" + p.db + "_" + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".bin");
            Files.write(backup, now);

            if (cfg.handshake) {
                setBit0(plc, p.db, START_WORD, now); // Start transfert
                waitFor(plc, cfg, () -> ((S7ReadClient.int16(plc.readDb(110, 0, 64), W44) >> BIT_BUSY) & 1) != 0,
                        "il PLC non ha preso in carico il trasferimento (DB110.DBW44 bit 2)", 8);
            }
            writeChanges(plc, p, sizeLimit);
            if (cfg.handshake) {
                setBit0(plc, p.db, END_WORD, plc.readDb(p.db, 0, p.current.length)); // Fine transfert
                waitFor(plc, cfg, () -> {
                    int w44 = S7ReadClient.int16(plc.readDb(110, 0, 64), W44);
                    byte[] d = plc.readDb(p.db, 0, p.current.length);
                    boolean closed = (S7ReadClient.int16(d, START_WORD) & 1) == 0 && (S7ReadClient.int16(d, END_WORD) & 1) == 0;
                    return ((w44 >> BIT_ENABLED) & 1) != 0 && ((w44 >> BIT_BUSY) & 1) == 0 && closed;
                }, "il PLC non ha chiuso il trasferimento (DB110.DBW44 / DB201.DBW40 / DBW382): la copia di sicurezza e' "
                        + backup, cfg.waitSeconds);
            }
            byte[] after = plc.readDb(p.db, 0, p.image.length);
            // le due parole di scambio (Start, Fine) tornano a 0 e non fanno parte della ricetta
            byte[] expect = p.image.clone();
            for (int w : new int[]{START_WORD, END_WORD}) {
                if (w + 1 < expect.length) {
                    expect[w] = after[w];
                    expect[w + 1] = after[w + 1];
                }
            }
            if (!java.util.Arrays.equals(after, expect)) {
                throw new IOException("Verifica fallita: il DB" + p.db + " dopo l'invio non coincide con la ricetta. "
                        + "Copia di sicurezza: " + backup);
            }
            return backup;
        }
    }

    /** Scrive le parole cambiate (unite quando adiacenti); il numero ricetta (DBW2) per ultimo, come fa il WinCC. */
    private static void writeChanges(S7WriteClient plc, Plan p, int sizeLimit) throws IOException {
        List<int[]> ranges = new ArrayList<>();
        int i = 0;
        while (i < p.changes.size()) {
            int start = p.changes.get(i).offset;
            int end = start + 2;
            int j = i + 1;
            while (j < p.changes.size() && p.changes.get(j).offset == end) {
                end += 2;
                j++;
            }
            if (end > sizeLimit) {
                throw new IOException("Scrittura fuori dal DB (" + end + " > " + sizeLimit + ")");
            }
            ranges.add(new int[]{start, end});
            i = j;
        }
        ranges.sort((x, y) -> {
            boolean ax = x[0] <= 3 && x[1] <= 4;
            boolean ay = y[0] <= 3 && y[1] <= 4;
            if (ax != ay) {
                return ax ? 1 : -1; // l'intervallo del solo numero ricetta (DBW2) va in coda
            }
            return Integer.compare(x[0], y[0]);
        });
        for (int[] r : ranges) {
            byte[] part = new byte[r[1] - r[0]];
            System.arraycopy(p.image, r[0], part, 0, part.length);
            plc.writeDb(p.db, r[0], part);
        }
    }

    /** Pone a 1 il bit 0 del byte basso della parola (DBX(w+1).0), lasciando invariato il resto. */
    private static void setBit0(S7WriteClient plc, int db, int word, byte[] image) throws IOException {
        byte[] two = {image[word], (byte) (image[word + 1] | 1)};
        plc.writeDb(db, word, two);
    }

    private interface Cond {
        boolean ok() throws IOException;
    }

    private static void waitFor(S7WriteClient plc, Config cfg, Cond c, String error, int seconds) throws IOException {
        long end = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < end) {
            if (c.ok()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrotto");
            }
        }
        throw new IOException("Timeout: " + error);
    }
}
