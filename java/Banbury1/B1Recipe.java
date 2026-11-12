package Banbury1;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ricetta B1: nome, parametri di testata e fasi. I valori sono INT grezzi (come nel PLC). */
public final class B1Recipe {

    public static final class Phase {
        public final Map<String, Integer> values = new LinkedHashMap<>();
        public int ops;

        public Phase copy() {
            Phase p = new Phase();
            p.values.putAll(values);
            p.ops = ops;
            return p;
        }

        /** Fase vuota (tutti zero): non e' usata. */
        public boolean isEmpty() {
            if (ops != 0) {
                return false;
            }
            for (int v : values.values()) {
                if (v != 0) {
                    return false;
                }
            }
            return true;
        }
    }

    public String uid = UUID.randomUUID().toString();
    public String name = "";
    public int recipeNo;
    public long revision;
    public String modified = "";
    public String modifiedBy = "";
    public final Map<String, Integer> header = new LinkedHashMap<>();
    public final List<Phase> phases = new ArrayList<>();

    public static B1Recipe blank(B1RecipeLayout l) {
        B1Recipe r = new B1Recipe();
        for (B1RecipeLayout.Field f : l.header) {
            r.header.put(f.key, 0);
        }
        for (int i = 0; i < l.phaseCount; i++) {
            Phase p = new Phase();
            for (B1RecipeLayout.Field f : l.phase) {
                p.values.put(f.key, 0);
            }
            r.phases.add(p);
        }
        return r;
    }

    public B1Recipe copy() {
        B1Recipe r = new B1Recipe();
        r.uid = uid;
        r.name = name;
        r.recipeNo = recipeNo;
        r.revision = revision;
        r.modified = modified;
        r.modifiedBy = modifiedBy;
        r.header.putAll(header);
        for (Phase p : phases) {
            r.phases.add(p.copy());
        }
        return r;
    }
}
