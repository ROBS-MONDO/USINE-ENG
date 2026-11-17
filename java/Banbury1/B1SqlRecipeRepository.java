package Banbury1;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Archivio ricette B1 su SQL Server (come B2): tabelle dbo.B1Recipes (una riga per ricetta) e dbo.B1RecipeValues
 * (valori: Fase 0 = testata, 1..N = fasi; chiave "ops" = bit delle operazioni). Piu' postazioni lavorano sullo stesso
 * archivio; il salvataggio controlla la revisione (se un'altra postazione ha salvato prima: ConflictException).
 * SQL "neutro" (solo istruzioni preparate, nessun costrutto specifico di T-SQL). Non scrive mai nel PLC.
 */
public final class B1SqlRecipeRepository implements B1RecipeRepository {

    /** Apre una connessione al database (credenziali non nel codice). */
    public interface Connections {
        Connection open() throws SQLException;
    }

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String OPS = "ops";

    private final Connections connections;
    private final B1RecipeLayout layout;
    private final String where;

    public B1SqlRecipeRepository(Connections connections, B1RecipeLayout layout, String where) {
        this.connections = connections;
        this.layout = layout;
        this.where = where;
    }

    @Override
    public String location() {
        return where;
    }

    @Override
    public List<Entry> list() throws IOException {
        List<Entry> out = new ArrayList<>();
        try (Connection c = connections.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT RecipeUID, Nome, Modifica, ModificatoDa, NumRicetta FROM dbo.B1Recipes ORDER BY Nome");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Timestamp t = rs.getTimestamp(3);
                out.add(new Entry(rs.getString(1), rs.getString(2),
                        t == null ? "" : t.toLocalDateTime().format(TS), rs.getString(4), rs.getInt(5)));
            }
        } catch (SQLException e) {
            throw new IOException("Archive SQL illisible : " + e.getMessage(), e);
        }
        return out;
    }

    @Override
    public B1Recipe load(String uid) throws IOException {
        try (Connection c = connections.open()) {
            long id;
            B1Recipe r = B1Recipe.blank(layout);
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT Id, Nome, NumRicetta, Revisione, Modifica, ModificatoDa FROM dbo.B1Recipes WHERE RecipeUID = ?")) {
                ps.setString(1, uid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IOException("Recette introuvable dans l'archive SQL : " + uid);
                    }
                    id = rs.getLong(1);
                    r.uid = uid;
                    r.name = rs.getString(2);
                    r.recipeNo = rs.getInt(3);
                    r.revision = rs.getLong(4);
                    Timestamp t = rs.getTimestamp(5);
                    r.modified = t == null ? "" : t.toLocalDateTime().format(TS);
                    r.modifiedBy = rs.getString(6);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT Fase, Chiave, Valore FROM dbo.B1RecipeValues WHERE RecipeId = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int fase = rs.getInt(1);
                        String key = rs.getString(2);
                        int val = rs.getInt(3);
                        if (fase == 0) {
                            if (r.header.containsKey(key)) {
                                r.header.put(key, val);
                            }
                        } else if (fase - 1 < r.phases.size()) {
                            B1Recipe.Phase p = r.phases.get(fase - 1);
                            if (OPS.equals(key)) {
                                p.ops = val;
                            } else if (p.values.containsKey(key)) {
                                p.values.put(key, val);
                            }
                        }
                    }
                }
            }
            return r;
        } catch (SQLException e) {
            throw new IOException("Archive SQL illisible : " + e.getMessage(), e);
        }
    }

    @Override
    public void save(B1Recipe r, String user) throws IOException {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        String who = user == null ? "" : user;
        long newRev = r.revision + 1;
        try (Connection c = connections.open()) {
            c.setAutoCommit(false);
            try {
                long id;
                if (r.revision > 0) {
                    int n;
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE dbo.B1Recipes SET Nome = ?, NumRicetta = ?, Revisione = ?, Modifica = ?, ModificatoDa = ? "
                                    + "WHERE RecipeUID = ? AND Revisione = ?")) {
                        ps.setString(1, r.name);
                        ps.setInt(2, r.recipeNo);
                        ps.setLong(3, newRev);
                        ps.setTimestamp(4, Timestamp.valueOf(now));
                        ps.setString(5, who);
                        ps.setString(6, r.uid);
                        ps.setLong(7, r.revision);
                        n = ps.executeUpdate();
                    }
                    if (n == 0) {
                        long onDb = currentRevision(c, r.uid);
                        c.rollback();
                        throw new ConflictException(onDb < 0
                                ? "La recette a \u00e9t\u00e9 supprim\u00e9e depuis un autre poste."
                                : "La recette a \u00e9t\u00e9 modifi\u00e9e depuis un autre poste (r\u00e9vision " + onDb
                                + " invece di " + r.revision + "). La rouvrir pour ne pas perdre les modifications.");
                    }
                    id = idOf(c, r.uid);
                } else {
                    if (currentRevision(c, r.uid) >= 0) {
                        c.rollback();
                        throw new ConflictException("Cette recette existe d\u00e9j\u00e0 dans l'archive : la rouvrir depuis la liste.");
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO dbo.B1Recipes (RecipeUID, Nome, NumRicetta, Revisione, Creazione, Modifica, ModificatoDa) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                        ps.setString(1, r.uid);
                        ps.setString(2, r.name);
                        ps.setInt(3, r.recipeNo);
                        ps.setLong(4, newRev);
                        ps.setTimestamp(5, Timestamp.valueOf(now));
                        ps.setTimestamp(6, Timestamp.valueOf(now));
                        ps.setString(7, who);
                        ps.executeUpdate();
                    }
                    id = idOf(c, r.uid);
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM dbo.B1RecipeValues WHERE RecipeId = ?")) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO dbo.B1RecipeValues (RecipeId, Fase, Chiave, Valore) VALUES (?, ?, ?, ?)")) {
                    for (B1RecipeLayout.Field f : layout.header) {
                        addValue(ps, id, 0, f.key, r.header.getOrDefault(f.key, 0));
                    }
                    for (int k = 0; k < layout.phaseCount && k < r.phases.size(); k++) {
                        B1Recipe.Phase p = r.phases.get(k);
                        for (B1RecipeLayout.Field f : layout.phase) {
                            addValue(ps, id, k + 1, f.key, p.values.getOrDefault(f.key, 0));
                        }
                        addValue(ps, id, k + 1, OPS, p.ops);
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException | RuntimeException | ConflictException e) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                    // la connessione si chiude comunque
                }
                throw e;
            }
        } catch (ConflictException e) {
            throw e;
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                throw new IOException("Il existe d\u00e9j\u00e0 une recette nomm\u00e9e '" + r.name + "' dans l'archive.", e);
            }
            throw new IOException("\u00c9chec de l'enregistrement dans l'archive SQL : " + e.getMessage(), e);
        }
        r.revision = newRev;
        r.modified = now.format(TS);
        r.modifiedBy = who;
    }

    @Override
    public void delete(String uid) throws IOException {
        try (Connection c = connections.open()) {
            c.setAutoCommit(false);
            try {
                long id = idOf(c, uid);
                if (id < 0) {
                    c.rollback();
                    return;
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM dbo.B1RecipeValues WHERE RecipeId = ?")) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM dbo.B1Recipes WHERE Id = ?")) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IOException("\u00c9chec de la suppression dans l'archive SQL : " + e.getMessage(), e);
        }
    }

    private static void addValue(PreparedStatement ps, long id, int fase, String key, int value) throws SQLException {
        ps.setLong(1, id);
        ps.setInt(2, fase);
        ps.setString(3, key);
        ps.setInt(4, value);
        ps.addBatch();
    }

    private static long idOf(Connection c, String uid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT Id FROM dbo.B1Recipes WHERE RecipeUID = ?")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    private static long currentRevision(Connection c, String uid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT Revisione FROM dbo.B1Recipes WHERE RecipeUID = ?")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }
}
