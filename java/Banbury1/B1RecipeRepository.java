package Banbury1;

import java.io.IOException;
import java.util.List;

/** Archivio ricette B1 (file in cartella condivisa oggi; SQL Server in futuro). */
public interface B1RecipeRepository {

    final class Entry {
        public final String uid;
        public final String name;
        public final String modified;
        public final String modifiedBy;

        public Entry(String uid, String name, String modified, String modifiedBy) {
            this.uid = uid;
            this.name = name;
            this.modified = modified;
            this.modifiedBy = modifiedBy;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** Un altro utente ha salvato la ricetta dopo che e' stata aperta. */
    class ConflictException extends IOException {
        private static final long serialVersionUID = 1L;

        public ConflictException(String msg) {
            super(msg);
        }
    }

    List<Entry> list() throws IOException;

    B1Recipe load(String uid) throws IOException;

    /** Salva (nuova revisione). Rilancia ConflictException se la revisione sul disco e' piu' recente. */
    void save(B1Recipe r, String user) throws IOException;

    void delete(String uid) throws IOException;

    String location();
}
