package ma.ceac.telereleve.simulator;

import java.time.Duration;
import java.time.Instant;
import ma.ceac.telereleve.simulator.SimulatedMeter.MeterType;

/**
 * What a meter publishes at time t: the normal ConsumptionModel, altered by an injected anomaly if any.
 * Still a pure function of (meter, anomaly, t), so it stays consistent across restarts and testable.
 */
public final class MeterBehaviour {

    static final Duration SPIKE_DURATION = Duration.ofHours(1);
    static final int SPIKE_FACTOR = 5;
    static final double DECREASE_SHARE = 0.005;

    private MeterBehaviour() {
    }

    public static boolean publishes(InjectedAnomaly anomaly, Instant t) {
        return anomaly == null || anomaly.type() != InjectedAnomaly.Type.SILENT || !anomaly.active(t);
    }

    public static double indexAt(SimulatedMeter meter, InjectedAnomaly anomaly, Instant t) {
        double normal = ConsumptionModel.indexAt(meter, t);
        if (anomaly == null || !anomaly.active(t)) {
            return normal;
        }
        Instant start = anomaly.start();
        return switch (anomaly.type()) {
            case ZERO -> ConsumptionModel.indexAt(meter, start);
            case SPIKE -> {
                // During the spike hour the meter consumes 5 times its normal amount; the extra stays
                // in the index afterwards (energy really consumed), so it keeps growing normally.
                Instant spikeEnd = t.isBefore(start.plus(SPIKE_DURATION)) ? t : start.plus(SPIKE_DURATION);
                double spikeHour = ConsumptionModel.indexAt(meter, spikeEnd) - ConsumptionModel.indexAt(meter, start);
                yield round(meter, normal + (SPIKE_FACTOR - 1) * spikeHour);
            }
            case DECREASING_INDEX -> {
                double drop = Math.max(1, ConsumptionModel.indexAt(meter, start) * DECREASE_SHARE);
                yield round(meter, Math.max(0, normal - drop));
            }
            case SILENT, VOLTAGE -> normal;
        };
    }

    public static Double voltageAt(SimulatedMeter meter, InjectedAnomaly anomaly, Instant t) {
        Double normal = ConsumptionModel.voltageAt(meter, t);
        if (normal == null || anomaly == null || anomaly.type() != InjectedAnomaly.Type.VOLTAGE || !anomaly.active(t)) {
            return normal;
        }
        return Math.round((258 + (normal - 225.4) / 9.2 * 4) * 10) / 10.0; // 258-262 V
    }

    private static double round(SimulatedMeter meter, double index) {
        double factor = meter.type() == MeterType.ELECTRICITY ? 100 : 1000;
        return Math.round(index * factor) / factor;
    }
}
