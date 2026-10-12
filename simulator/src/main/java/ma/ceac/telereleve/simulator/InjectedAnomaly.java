package ma.ceac.telereleve.simulator;

import java.time.Instant;

/**
 * An anomaly injected on one meter from `start` on, to demonstrate and test the detection rules.
 *
 * @param type ZERO -> rule A1, SPIKE -> A2, SILENT -> A3, DECREASING_INDEX -> A4, VOLTAGE -> A5
 */
public record InjectedAnomaly(Type type, Instant start) {

    public enum Type {
        /** Consumption stops: the index freezes. */
        ZERO,
        /** Consumption multiplied by 5 for one hour. */
        SPIKE,
        /** The meter stops publishing. */
        SILENT,
        /** The index goes down once (by 0.5 %), then grows normally from the lower value. */
        DECREASING_INDEX,
        /** Voltage around 260 V, outside 207-253 V (electricity meters only). */
        VOLTAGE
    }

    public boolean active(Instant t) {
        return !t.isBefore(start);
    }
}
