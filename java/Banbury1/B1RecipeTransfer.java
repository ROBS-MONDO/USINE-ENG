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
 * b1_recipe_transfer.properties. Prima di abilitarla va confermata la sequenza di trasferimento del WinCC
 * (lampade "Transfert recette habilite", "Start transfert" = DB201.DBX41.0, "Fin transfert", "Recette valide").
 */
public final class B1RecipeTransfer {

    private B1RecipeTransfer() {
    }

    /** Impostazioni (file b1_recipe_transfer.properties). */
    public static final class Config {
        public boolean enabled;
        public boolean allowRawWrite;
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
    }

    /**
     * Prepara l'invio. {@code currentDb} = immagine attuale del DB201 nel PLC, {@code db110} = DB110 (stato macchina,
     * puo' essere null). Non scrive niente.
     */
    public static Plan plan(B1Recipe r, B1RecipeLayout l, byte[] currentDb, byte[] db110) {
        Plan p = new Plan();
        p.db = l.db;
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
            boolean enabledBit = ((S7ReadClient.int16(db110, 44) >> 4) & 1) != 0;
            if (!enabledBit) {
                p.warnings.add("Il PLC non segnala 'Transfert recette habilite' (DB110.DBW44 bit 4, ipotesi dal WinCC)");
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
            sb.append("Scrittura ABILITATA. Prima dell'invio il DB attuale viene salvato in ").append(cfg.backupDir)
                    .append(" e dopo l'invio viene riletto e confrontato.\n");
        } else {
            sb.append("Scrittura DISABILITATA (modalita' anteprima): per abilitarla servono enabled=true e allow_raw_write=true\n")
                    .append("in b1_recipe_transfer.properties, dopo aver confermato la sequenza di trasferimento del WinCC.\n");
        }
        return sb.toString();
    }

    /**
     * Esegue l'invio: rilegge il DB e rifiuta se e' cambiato dopo l'anteprima, salva la copia di sicurezza, scrive solo
     * le parole cambiate, rilegge e confronta (se diverso prova a ripristinare la copia). Ritorna il percorso del backup.
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
            Files.createDirectories(cfg.backupDir);
            Path backup = cfg.backupDir.resolve("DB" + p.db + "_" + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".bin");
            Files.write(backup, now);
            // si scrivono solo le parole cambiate, unite quando adiacenti
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
                byte[] part = new byte[end - start];
                System.arraycopy(p.image, start, part, 0, part.length);
                plc.writeDb(p.db, start, part);
                i = j;
            }
            byte[] after = plc.readDb(p.db, 0, p.image.length);
            if (!java.util.Arrays.equals(after, p.image)) {
                try {
                    plc.writeDb(p.db, 0, now);
                } catch (IOException ignored) {
                    // ripristino fallito: l'errore sotto lo segnala
                }
                throw new IOException("Verifica fallita dopo la scrittura: ripristinata la copia di sicurezza " + backup);
            }
            return backup;
        }
    }
}
