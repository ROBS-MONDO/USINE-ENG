package Banbury1;

import java.awt.Component;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * "Mode modification" des recettes B1, comme dans MondoLuxRecipes (B2): authentification Active Directory obligatoire avant toute
 * modification, valable 15 minutes, journal de chaque changement. L'authentification est faite par la classe du projet
 * SVC.ActiveDirectorySetup (appelee par reflexion: aucun mot de passe n'est jamais lu, stocke ou ecrit par B1).
 * Si la classe n'est pas disponible, les modifications sont REFUSEES (securite par defaut).
 */
final class B1EditAuth {

    static final int DURATION_SECONDS = 15 * 60;
    private static final DateTimeFormatter LOG_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Path LOG_FILE = Files.isDirectory(Paths.get("C:\\_tools\\Banbury1"))
            ? Paths.get("C:\\_tools\\Banbury1\\b1_recipe_change_log.txt") : Paths.get("b1_recipe_change_log.txt");

    private static Object user;
    private static String userName = "";
    private static String principal = "";
    private static long expires;

    private B1EditAuth() {
    }

    static synchronized boolean active() {
        return user != null && System.currentTimeMillis() < expires;
    }

    static synchronized int remainingSeconds() {
        return active() ? (int) ((expires - System.currentTimeMillis()) / 1000) : 0;
    }

    static synchronized String userName() {
        return active() ? userName : "";
    }

    static synchronized void end() {
        user = null;
        expires = 0;
    }

    /** Vrai si le mode modification est actif ou si l'authentification vient d'etre validee. */
    static boolean ensure(Component parent) {
        if (active()) {
            return true;
        }
        try {
            Class<?> c;
            try {
                c = Class.forName("SVC.ActiveDirectorySetup");
            } catch (ClassNotFoundException e) {
                c = Class.forName("OT4US.ActiveDirectorySetup");
            }
            Method req = null;
            for (Method m : c.getMethods()) {
                if (m.getName().equals("requestAuthentication") && m.getParameterCount() == 1) {
                    req = m;
                }
            }
            if (req == null) {
                throw new NoSuchMethodException("requestAuthentication");
            }
            Object arg = null;
            java.awt.Window w = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
            Component cand = parent instanceof java.awt.Window ? parent : w;
            if (cand != null && req.getParameterTypes()[0].isInstance(cand)) {
                arg = cand;
            }
            Object u = req.invoke(null, arg);
            if (u == null) {
                return false; // annule ou refuse
            }
            synchronized (B1EditAuth.class) {
                user = u;
                userName = str(u, "getUsername");
                principal = str(u, "getPrincipal");
                expires = System.currentTimeMillis() + DURATION_SECONDS * 1000L;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Throwable t = e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null ? e.getCause() : e;
            JOptionPane.showMessageDialog(parent,
                    "Authentification Active Directory impossible (SVC.ActiveDirectorySetup) :\n" + t
                            + "\n\nLa modification est refus\u00e9e.", "Mode modification", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private static String str(Object o, String getter) {
        try {
            Object v = o.getClass().getMethod(getter).invoke(o);
            return v == null ? "" : v.toString();
        } catch (ReflectiveOperationException e) {
            return "";
        }
    }

    private static String safe(String s) {
        return s == null ? "-" : s.replace("\r", " ").replace("\n", " ").replace("|", "/").trim();
    }

    /** Journal des modifications (une ligne par changement). */
    static synchronized void log(String action, int recipeNo, String recipeName, String step, String param, String oldV, String newV) {
        String line = LOG_DATE.format(LocalDateTime.now()) + " | Action=" + safe(action) + " | Recette=" + recipeNo + " " + safe(recipeName)
                + " | Step=" + safe(step) + " | User=" + safe(userName) + " | Principal=" + safe(principal)
                + " | Champ=" + safe(param) + " | OldValue=" + safe(oldV) + " | NewValue=" + safe(newV) + System.lineSeparator();
        try {
            Files.write(LOG_FILE, line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Journal modifications illisible: " + e.getMessage());
        }
    }
}
