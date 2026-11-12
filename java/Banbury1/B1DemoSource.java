package Banbury1;

import java.util.HashMap;
import java.util.Map;

/** Valori fissi presi dallo screenshot WinCC di B1, per provare la grafica senza PLC. */
public final class B1DemoSource implements B1Source {

    private final Map<String, Double> nums = new HashMap<>();
    private final Map<String, String> strs = new HashMap<>();
    private final Map<String, Boolean> bools = new HashMap<>();
    private final long t0 = System.currentTimeMillis();

    public B1DemoSource() {
        nums.put("press_top", 0.0);
        nums.put("temp", 93.0);
        nums.put("speed_top", 33.0);
        nums.put("mix_total", 100.0);
        nums.put("mix_done", 25.0);
        nums.put("press_set", 0.0);
        nums.put("press_act", 5.4);
        nums.put("piston_pct1", 0.0);
        nums.put("piston_pct2", 80.0);
        nums.put("piston_pct3", 80.0);
        nums.put("rpm_set", 33.0);
        nums.put("rpm_act", 33.0);
        nums.put("current", 31.0);
        nums.put("steps", 30.0);
        nums.put("suffix", 1.0);
        nums.put("seq_a", 10.0);
        nums.put("seq_b", 21.0);
        strs.put("lot_year", "26");
        strs.put("lot_num", "9004");
        strs.put("type", "INF");
        strs.put("pass", "4e");
        strs.put("color", "---");
        strs.put("recipe_name", "gomma2");

        String[] on = {"piston_float", "piston_low", "rotors_running", "lube_running", "silos_closed",
                "door_load_closed", "weight_open", "door_dis_open", "rdy_discharge", "rdy_motor",
                "rdy_mintime", "rdy_cycles", "cyc_auto", "cyc_cont", "cyc_cycle", "prod_auto_on",
                "prod_auto", "recipe_ok"};
        String[] off = {"piston_high", "piston_blocked", "silos_open", "door_load_open", "weight_closed",
                "door_dis_closed", "rdy_temp", "rdy_door", "cyc_susp", "pos_load", "decel_load",
                "decel_dis", "pos_dis", "prod_ready", "new_load", "cycle_stopped"};
        for (String k : on) {
            bools.put(k, true);
        }
        for (String k : off) {
            bools.put(k, false);
        }
    }

    @Override
    public Double num(String key) {
        if ("cycle_time".equals(key)) {
            return 298.0 + ((System.currentTimeMillis() - t0) / 1000) % 600;
        }
        if ("mix_todo".equals(key)) {
            return nums.get("mix_total") - nums.get("mix_done");
        }
        return nums.get(key);
    }

    @Override
    public Boolean bool(String key) {
        return bools.get(key);
    }

    @Override
    public String str(String key) {
        if (strs.containsKey(key)) {
            return strs.get(key);
        }
        Double d = nums.get(key);
        return d == null ? null : String.valueOf(d.longValue());
    }

    @Override
    public String spec(String key) {
        return "demo";
    }

    @Override
    public boolean connected() {
        return true;
    }

    @Override
    public String status() {
        return "DEMO (valori dello screenshot)";
    }

    @Override
    public void close() {
    }
}
