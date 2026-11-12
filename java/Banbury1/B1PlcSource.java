package Banbury1;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Legge i DB dal PLC (solo lettura) e li espone tramite la mappa tag. */
public final class B1PlcSource implements B1Source {

    private static final int MAX_DB = 1024;

    private final B1Tags tags;
    private final String host;
    private final int port;
    private final int rack;
    private final int slot;
    private final int periodMs;

    private volatile Map<Integer, byte[]> data = new HashMap<>();
    private volatile boolean connected;
    private volatile String status = "Connessione in corso...";
    private volatile boolean stop;
    private final Thread thread;

    public B1PlcSource(B1Tags tags, String host, int port, int rack, int slot, int periodMs) {
        this.tags = tags;
        this.host = host;
        this.port = port;
        this.rack = rack;
        this.slot = slot;
        this.periodMs = periodMs;
        thread = new Thread(this::run, "b1-plc-poll");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        while (!stop) {
            try (S7ReadClient plc = new S7ReadClient(host, port, rack, slot, 3000)) {
                plc.connect();
                Map<Integer, Integer> sizes = new HashMap<>();
                for (int db : tags.dbs()) {
                    sizes.put(db, plc.detectDbSize(db, MAX_DB));
                }
                connected = true;
                status = host + " rack " + rack + " slot " + slot + " (sola lettura)";
                while (!stop) {
                    Map<Integer, byte[]> next = new HashMap<>();
                    for (Map.Entry<Integer, Integer> e : sizes.entrySet()) {
                        if (e.getValue() > 0) {
                            next.put(e.getKey(), plc.readDb(e.getKey(), 0, e.getValue()));
                        }
                    }
                    data = next;
                    Thread.sleep(periodMs);
                }
            } catch (InterruptedException ie) {
                return;
            } catch (IOException | RuntimeException e) {
                connected = false;
                status = "PLC non raggiungibile: " + (e.getMessage() != null ? e.getMessage() : e.toString());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    private byte[] block(B1Tags.Tag t, int need) {
        byte[] d = data.get(t.db);
        if (d == null || t.off < 0 || t.off + need > d.length) {
            return null;
        }
        return d;
    }

    @Override
    public Double num(String key) {
        B1Tags.Tag t = tags.get(key);
        if (t == null || !connected) {
            return null;
        }
        switch (t.kind) {
            case INT: {
                byte[] d = block(t, 2);
                return d == null ? null : S7ReadClient.int16(d, t.off) * t.scale;
            }
            case UINT: {
                byte[] d = block(t, 2);
                return d == null ? null : (S7ReadClient.int16(d, t.off) & 0xFFFF) * t.scale;
            }
            case BIT: {
                Boolean b = bool(key);
                return b == null ? null : (b ? 1.0 : 0.0);
            }
            default: {
                String s = str(key);
                if (s == null) {
                    return null;
                }
                try {
                    return Double.parseDouble(s.trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
    }

    @Override
    public Boolean bool(String key) {
        B1Tags.Tag t = tags.get(key);
        if (t == null || !connected) {
            return null;
        }
        if (t.kind == B1Tags.Kind.BIT) {
            byte[] d = block(t, 1);
            if (d == null) {
                return null;
            }
            boolean v = S7ReadClient.bit(d, t.off, t.bit);
            return t.negate != v;
        }
        Double v = num(key);
        return v == null ? null : v != 0.0;
    }

    @Override
    public String str(String key) {
        B1Tags.Tag t = tags.get(key);
        if (t == null || !connected) {
            return null;
        }
        if (t.kind == B1Tags.Kind.STRING) {
            byte[] d = block(t, 2);
            if (d == null) {
                return null;
            }
            int max = d[t.off] & 0xFF;
            int len = Math.min(d[t.off + 1] & 0xFF, Math.min(max, d.length - t.off - 2));
            return new String(d, t.off + 2, Math.max(0, len), StandardCharsets.ISO_8859_1).trim();
        }
        Double v = num(key);
        if (v == null) {
            return null;
        }
        return v == Math.rint(v) ? String.valueOf(v.longValue()) : String.valueOf(v);
    }

    @Override
    public String spec(String key) {
        B1Tags.Tag t = tags.get(key);
        return t == null ? "?" : (t.guess ? t.spec + " (ipotesi)" : t.spec);
    }

    @Override
    public boolean connected() {
        return connected;
    }

    @Override
    public String status() {
        return status;
    }

    @Override
    public void close() {
        stop = true;
        thread.interrupt();
    }
}
