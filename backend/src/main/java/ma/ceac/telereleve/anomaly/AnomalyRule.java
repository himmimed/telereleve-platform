package ma.ceac.telereleve.anomaly;

import java.util.Optional;
import ma.ceac.telereleve.meter.Meter;

/**
 * One anomaly detection rule (Strategy pattern): each rule is a Spring @Component, and AnomalyEngine
 * runs every enabled rule without knowing them individually. Adding a rule = adding one class.
 *
 * evaluate() returns the anomaly if the condition holds now, or empty if it does not; an empty result
 * lets the engine auto-resolve an open alert of this rule (unless autoResolve() is false).
 */
public interface AnomalyRule {

    /** Rule code, A1 to A5. */
    String code();

    Optional<AnomalyResult> evaluate(Meter meter, RuleContext ctx);

    /** When the rule runs. */
    default Trigger trigger() {
        return Trigger.ON_READING;
    }

    /** Whether an open alert is resolved automatically when the condition disappears. */
    default boolean autoResolve() {
        return true;
    }

    enum Trigger {
        /** After each stored reading of the meter. */
        ON_READING,
        /** Periodically, for every active meter (a missing reading cannot trigger anything by itself). */
        SCHEDULED
    }
}
