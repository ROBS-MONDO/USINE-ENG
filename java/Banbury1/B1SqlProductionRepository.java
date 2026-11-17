package Banbury1;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Produzioni B1 su SQL Server: tabella dbo.B1Productions (vedi sql/create_b1_productions.sql). SQL "neutro". */
public final class B1SqlProductionRepository implements B1ProductionRepository {

    private static final String COLS = "Id, Nome, Descrizione, NumRicetta, Quantita, Priorita, Stato, Creazione, Modifica, DataCliente";

    private final B1SqlRecipeRepository.Connections connections;
    private final String where;

    public B1SqlProductionRepository(B1SqlRecipeRepository.Connections connections, String where) {
        this.connections = connections;
        this.where = where;
    }

    @Override
    public String location() {
        return where;
    }

    private static B1Production read(ResultSet rs) throws SQLException {
        B1Production p = new B1Production();
        p.id = rs.getInt(1);
        p.name = rs.getString(2);
        p.description = rs.getString(3) == null ? "" : rs.getString(3);
        p.recipeNo = rs.getInt(4);
        p.quantity = rs.getInt(5);
        p.priority = rs.getInt(6);
        p.status = rs.getInt(7);
        Timestamp c = rs.getTimestamp(8);
        Timestamp m = rs.getTimestamp(9);
        p.created = c == null ? LocalDateTime.now() : c.toLocalDateTime();
        p.updated = m == null ? p.created : m.toLocalDateTime();
        p.customerDate = rs.getString(10) == null ? "" : rs.getString(10);
        return p;
    }

    @Override
    public List<B1Production> list() throws IOException {
        List<B1Production> out = new ArrayList<>();
        try (Connection c = connections.open();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLS
                     + " FROM dbo.B1Productions WHERE Stato <= 3 ORDER BY Priorita, Nome, Id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(read(rs));
            }
        } catch (SQLException e) {
            throw new IOException("Archive SQL des productions illisible : " + e.getMessage(), e);
        }
        return out;
    }

    @Override
    public B1Production load(int id) throws IOException {
        try (Connection c = connections.open();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLS + " FROM dbo.B1Productions WHERE Id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        } catch (SQLException e) {
            throw new IOException("Archive SQL des productions illisible : " + e.getMessage(), e);
        }
    }

    @Override
    public B1Production save(B1Production p) throws IOException {
        List<String> err = p.validate();
        if (!err.isEmpty()) {
            throw new IOException(err.get(0));
        }
        B1Production s = p.copy();
        s.updated = LocalDateTime.now();
        try (Connection c = connections.open()) {
            if (s.id > 0 && exists(c, s.id)) {
                try (PreparedStatement ps = c.prepareStatement("UPDATE dbo.B1Productions SET Nome = ?, Descrizione = ?, NumRicetta = ?, "
                        + "Quantita = ?, Priorita = ?, Stato = ?, Modifica = ?, DataCliente = ? WHERE Id = ?")) {
                    ps.setString(1, s.name.trim());
                    ps.setString(2, s.description == null ? "" : s.description);
                    ps.setInt(3, s.recipeNo);
                    ps.setInt(4, s.quantity);
                    ps.setInt(5, s.priority);
                    ps.setInt(6, s.status);
                    ps.setTimestamp(7, Timestamp.valueOf(s.updated));
                    ps.setString(8, s.customerDate == null ? "" : s.customerDate);
                    ps.setInt(9, s.id);
                    ps.executeUpdate();
                }
            } else {
                if (s.id <= 0) {
                    try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(MAX(Id), 0) + 1 FROM dbo.B1Productions");
                         ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        s.id = rs.getInt(1);
                    }
                    s.created = s.updated;
                    s.status = B1Production.NEW;
                    s.customerDate = s.updated.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO dbo.B1Productions (" + COLS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setInt(1, s.id);
                    ps.setString(2, s.name.trim());
                    ps.setString(3, s.description == null ? "" : s.description);
                    ps.setInt(4, s.recipeNo);
                    ps.setInt(5, s.quantity);
                    ps.setInt(6, s.priority);
                    ps.setInt(7, s.status);
                    ps.setTimestamp(8, Timestamp.valueOf(s.created));
                    ps.setTimestamp(9, Timestamp.valueOf(s.updated));
                    ps.setString(10, s.customerDate == null ? "" : s.customerDate);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new IOException("\u00c9chec de l'enregistrement de la production : " + e.getMessage(), e);
        }
        s.name = s.name.trim();
        return s;
    }

    @Override
    public boolean importRow(B1Production p) throws IOException {
        try (Connection c = connections.open()) {
            if (exists(c, p.id)) {
                return false;
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO dbo.B1Productions (" + COLS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, p.id);
                ps.setString(2, p.name.trim());
                ps.setString(3, p.description == null ? "" : p.description);
                ps.setInt(4, p.recipeNo);
                ps.setInt(5, p.quantity);
                ps.setInt(6, p.priority);
                ps.setInt(7, p.status);
                ps.setTimestamp(8, Timestamp.valueOf(p.created));
                ps.setTimestamp(9, Timestamp.valueOf(p.updated));
                ps.setString(10, p.customerDate == null ? "" : p.customerDate);
                ps.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            throw new IOException("\u00c9chec de l'importation de la production " + p.id + " : " + e.getMessage(), e);
        }
    }

    private static boolean exists(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM dbo.B1Productions WHERE Id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void setStatus(int id, int status) throws IOException {
        try (Connection c = connections.open();
             PreparedStatement ps = c.prepareStatement("UPDATE dbo.B1Productions SET Stato = ?, Modifica = ? WHERE Id = ?")) {
            ps.setInt(1, status);
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("\u00c9chec de la mise \u00e0 jour de la production : " + e.getMessage(), e);
        }
    }

    @Override
    public void end(int id) throws IOException {
        setStatus(id, B1Production.ENDED);
    }

    @Override
    public void setRunning(int id) throws IOException {
        setStatus(id, B1Production.RUNNING);
    }

    @Override
    public boolean nameExists(String name, int exceptId) throws IOException {
        try (Connection c = connections.open();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM dbo.B1Productions WHERE Nome = ? AND Stato <= 3 AND Id <> ?")) {
            ps.setString(1, name.trim());
            ps.setInt(2, exceptId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IOException("Archive SQL des productions illisible : " + e.getMessage(), e);
        }
    }
}
