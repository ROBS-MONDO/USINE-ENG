package Banbury1;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Stato del SILOS di B1 letto da Govoni (come in PLC3_Main per B2): "Etat execution" della produzione BY1.
 * Regola: codice 2 (= "Descharge Vidage") = silos APERTO (spia Ouvert verde); qualsiasi altro stato = silos CHIUSO (spia Ferme rossa);
 * lettura non valida = nessuna variazione. Sola lettura: usa OpcManager del progetto (stesso client OPC di B2) via reflection.
 * Il registro si configura in b1_govoni.properties: silos_node = ns=2;s=Govoni.SILOS.R.... (vuoto = funzione spenta).
 */
final class B1GovoniSilos {

    private static final String APP_NAME = "Banbury 1";
    private static final String APP_URI = "urn:banbury1:main";
    private static volatile Boolean state; // TRUE aperto, FALSE chiuso, null sconosciuto
    private static volatile boolean configured;
    private static volatile String lastError = "";

    private B1GovoniSilos() {
    }

    /** true se il registro Govoni e' configurato (altrimenti le spie restano quelle del PLC). */
    static boolean configured() {
        return configured;
    }

    /** TRUE = silos aperto, FALSE = chiuso, null = non ancora letto. */
    static Boolean open() {
        return state;
    }

    static String lastError() {
        return lastError;
    }

    /** Avvia la lettura in background se b1_govoni.properties indica il registro. */
    static void start() {
        Properties p = new Properties();
        for (Path f : new Path[]{Paths.get("C:\\_tools\\Banbury1\\b1_govoni.properties"), Paths.get("b1_govoni.properties")}) {
            if (Files.isReadable(f)) {
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    p.load(r);
                } catch (IOException e) {
                    System.err.println("Govoni silos : configurazione illeggibile (" + e.getMessage() + ")");
                }
                break;
            }
        }
        final String node = p.getProperty("silos_node", "").trim();
        if (node.isEmpty()) {
            System.out.println("Govoni silos: silos_node non configurato: spie SILOS dal PLC");
            return;
        }
        configured = true;
        Thread t = new Thread(() -> loop(node), "b1-govoni-silos");
        t.setDaemon(true);
        t.start();
    }

    private static void loop(String nodeText) {
        try {
            Class<?> manager = Class.forName("OpcManager");
            Class<?> nodeClass = Class.forName("org.eclipse.milo.opcua.stack.core.types.builtin.NodeId");
            Method read = manager.getMethod("readValues", String.class, String.class, List.class);
            Object node = nodeClass.getMethod("parse", String.class).invoke(null, nodeText);
            while (true) {
                try {
                    Object res = read.invoke(null, APP_NAME, APP_URI, Collections.singletonList(node));
                    Object raw = firstValue(res);
                    if (raw != null) {
                        apply(raw);
                        lastError = "";
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    lastError = String.valueOf(e.getCause() != null ? e.getCause() : e);
                }
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            lastError = "OpcManager/Milo non disponibili: " + e;
            System.err.println("Govoni silos: " + lastError);
        }
    }

    /** Primo valore della lista di DataValue (DataValue.getValue().getValue()). */
    private static Object firstValue(Object list) throws ReflectiveOperationException {
        if (!(list instanceof List) || ((List<?>) list).isEmpty()) {
            return null;
        }
        Object dv = ((List<?>) list).get(0);
        if (dv == null) {
            return null;
        }
        Object variant = dv.getClass().getMethod("getValue").invoke(dv);
        return variant == null ? null : variant.getClass().getMethod("getValue").invoke(variant);
    }

    /** Regola PLC3_Main: solo il codice 2 ("Descharge Vidage") = aperto; ogni altro valore valido = chiuso. */
    static void apply(Object raw) {
        String v = String.valueOf(raw).trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (v.isEmpty()) {
            return; // lettura non valida: nessuna variazione
        }
        try {
            int code = (int) Math.round(Double.parseDouble(v.replace(",", ".")));
            state = code == 2;
            return;
        } catch (NumberFormatException ignored) {
            // non numerico: prova con il testo
        }
        state = v.startsWith("descharge vidage");
    }

    /** Solo per le prove: imposta lo stato senza OPC. */
    static void setForTest(Boolean open) {
        state = open;
        configured = open != null;
    }
}
