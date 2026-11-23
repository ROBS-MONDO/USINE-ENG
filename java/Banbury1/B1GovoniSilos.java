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
 * Registro (dal programma Govoni, GovoniProductionFrame V3.58, bilancia W118 / BY1): R2021 = "Etat execution"
 * (per BY2 / W117 e' R1021, quello usato da PLC3_Main). Si puo' cambiare in b1_govoni.properties (silos_node).
 * Se OpcManager (package SVC, progetto Govoni/B2) non e' nel classpath, le spie restano quelle del PLC.
 */
final class B1GovoniSilos {

    private static final String APP_NAME = "Banbury 1 Silos";
    private static final String APP_URI = "urn:mondo:banbury1:silos";
    /** Etat execution di Production BY1 (W118): base 2000 + offset 21. */
    static final String DEFAULT_NODE = "ns=2;s=Govoni.SILOS.R2021";
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
        String cfg = p.getProperty("silos_node", "").trim();
        final String node = cfg.isEmpty() ? DEFAULT_NODE : cfg;
        System.out.println("Govoni silos: " + node);
        Thread t = new Thread(() -> loop(node), "b1-govoni-silos");
        t.setDaemon(true);
        t.start();
    }

    private static void loop(String nodeText) {
        try {
            Class<?> manager;
            try {
                manager = Class.forName("SVC.OpcManager");
            } catch (ClassNotFoundException e) {
                manager = Class.forName("OpcManager");
            }
            Class<?> nodeClass = Class.forName("org.eclipse.milo.opcua.stack.core.types.builtin.NodeId");
            Method read = manager.getMethod("readValues", String.class, String.class, List.class);
            Object node = nodeClass.getMethod("parse", String.class).invoke(null, nodeText);
            configured = true; // OPC disponibile: da qui le spie SILOS seguono Govoni
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
        Object status = dv.getClass().getMethod("getStatusCode").invoke(dv);
        if (status == null || !Boolean.TRUE.equals(status.getClass().getMethod("isGood").invoke(status))) {
            return null; // qualita' OPC non valida: nessuna variazione
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
