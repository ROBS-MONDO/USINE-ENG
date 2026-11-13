package Banbury1;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Layout di una schermata WinCC di B1, estratto dal file .Pdl del progetto (screens/*.b1s).
 * Coordinate = pixel del picture WinCC (1024x768).
 */
public final class B1Screen {

    public static final class Obj {
        public final String kind;
        public final String name;
        public final int l, t, r, b;
        public final String text;
        public final String tag;
        public final int bit;
        public final String fmt;
        public final String bg;
        public final String lamp;
        public final String img;

        Obj(String[] f) {
            kind = f[1];
            name = f[2];
            l = Integer.parseInt(f[3]);
            t = Integer.parseInt(f[4]);
            r = Integer.parseInt(f[5]);
            b = Integer.parseInt(f[6]);
            text = f[7];
            tag = f[8];
            bit = Integer.parseInt(f[9]);
            fmt = f[10];
            bg = f[11];
            lamp = f[12];
            img = f.length > 13 ? f[13] : "";
        }

        public int w() {
            return r - l;
        }

        public int h() {
            return b - t;
        }
    }

    /** Fattori di scala noti (valore PLC grezzo -> unita' ingegneristica). Ipotesi da confermare. */
    static final Map<String, Double> SCALE = new HashMap<>();

    static {
        SCALE.put("DB110DBW70", 0.1);
        SCALE.put("DB110DBW72", 0.1);
        SCALE.put("DB201DBW34", 0.1);
    }

    public final String name;
    public final List<Obj> objects = new ArrayList<>();

    private B1Screen(String name) {
        this.name = name;
    }

    public static B1Screen load(String name) {
        String rel = name + ".b1s";
        try (InputStream in = open(rel)) {
            if (in == null) {
                return null;
            }
            B1Screen s = new B1Screen(name);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("obj|")) {
                        String[] f = line.split("\\|", -1);
                        if (f.length >= 13) {
                            try {
                                s.objects.add(new Obj(f));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
            }
            return s;
        } catch (IOException e) {
            return null;
        }
    }

    private static InputStream open(String rel) throws IOException {
        InputStream in = B1Screen.class.getResourceAsStream("/Banbury1/screens/" + rel);
        if (in != null) {
            return in;
        }
        String[] dirs = {"screens", "java/Banbury1/screens", "src/main/java/Banbury1/screens",
                "C:\\_tools\\Banbury1\\screens"};
        for (String d : dirs) {
            File f = new File(d, rel);
            if (f.isFile()) {
                return new java.io.FileInputStream(f);
            }
        }
        return null;
    }

    /** DB citati dai tag degli oggetti (per farli leggere dal PLC). */
    public Set<Integer> dbs() {
        Set<Integer> out = new TreeSet<>();
        for (Obj o : objects) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^DB(\\d+)DB").matcher(o.tag);
            if (m.find()) {
                out.add(Integer.parseInt(m.group(1)));
            }
        }
        return out;
    }
}
