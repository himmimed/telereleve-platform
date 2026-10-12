package ma.ceac.telereleve.simulator;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import ma.ceac.telereleve.simulator.InjectedAnomaly.Type;
import ma.ceac.telereleve.simulator.SimulatedMeter.MeterType;

/**
 * Which meters misbehave, and how.
 * - SIM_ANOMALIES="CEAC-E-000007:DECREASING_INDEX,CEAC-W-000003:ZERO": explicit meters, for demos;
 * - SIM_ANOMALY_PERCENT=5: about 5 % of the other meters, chosen and typed deterministically from
 *   their serial (the same meters on every run).
 * A VOLTAGE anomaly is meaningless on a water meter (no voltage), so those get a SPIKE instead.
 */
public final class AnomalyPlan {

    private static final Type[] RANDOM_TYPES = Type.values();

    private AnomalyPlan() {
    }

    public static Map<String, InjectedAnomaly> build(List<SimulatedMeter> meters, String explicit, int percent, Instant start) {
        Map<String, InjectedAnomaly> plan = new HashMap<>();
        if (percent > 0) {
            for (SimulatedMeter meter : meters) {
                long hash = Math.abs(ConsumptionModel.seedOf(meter.serial()) >>> 7);
                if (hash % 100 < percent) {
                    plan.put(meter.serial(), new InjectedAnomaly(adapt(meter, RANDOM_TYPES[(int) (hash / 100 % RANDOM_TYPES.length)]), start));
                }
            }
        }
        for (Map.Entry<String, Type> entry : parse(explicit).entrySet()) {
            meters.stream().filter(m -> m.serial().equals(entry.getKey())).findFirst()
                    .ifPresent(m -> plan.put(m.serial(), new InjectedAnomaly(adapt(m, entry.getValue()), start)));
        }
        return plan;
    }

    /** "SERIAL:TYPE,SERIAL:TYPE" -> map; rejects unknown types with a clear message. */
    static Map<String, Type> parse(String spec) {
        Map<String, Type> result = new HashMap<>();
        if (spec == null || spec.isBlank()) {
            return result;
        }
        for (String item : spec.split(",")) {
            String[] parts = item.trim().split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("SIM_ANOMALIES: expected SERIAL:TYPE, got '" + item.trim() + "'");
            }
            try {
                result.put(parts[0].trim(), Type.valueOf(parts[1].trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("SIM_ANOMALIES: unknown anomaly '" + parts[1].trim()
                        + "', expected one of ZERO, SPIKE, SILENT, DECREASING_INDEX, VOLTAGE");
            }
        }
        return result;
    }

    private static Type adapt(SimulatedMeter meter, Type type) {
        return type == Type.VOLTAGE && meter.type() == MeterType.WATER ? Type.SPIKE : type;
    }
}
