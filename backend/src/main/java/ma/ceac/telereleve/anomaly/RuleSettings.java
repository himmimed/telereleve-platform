package ma.ceac.telereleve.anomaly;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Settings of one rule (a row of alert_rule_config).
 *
 * @param threshold meaning depends on the rule: A2 multiplier, A3 multiple of the reading interval,
 *                  A5 tolerance in % around 230 V; unused by A1 and A4
 * @param windowHours A1 window without consumption, A2 reference history; unused by the others
 */
public record RuleSettings(boolean enabled, BigDecimal threshold, Integer windowHours) {

    /** Specification defaults, used when a rule has no row in alert_rule_config. */
    public static final Map<String, RuleSettings> DEFAULTS = Map.of(
            "A1", new RuleSettings(true, null, 24),
            "A2", new RuleSettings(true, new BigDecimal("3"), 168),
            "A3", new RuleSettings(true, new BigDecimal("3"), null),
            "A4", new RuleSettings(true, null, null),
            "A5", new RuleSettings(true, new BigDecimal("10"), null));

    public BigDecimal thresholdOr(String fallback) {
        return threshold != null ? threshold : new BigDecimal(fallback);
    }

    public int windowHoursOr(int fallback) {
        return windowHours != null ? windowHours : fallback;
    }
}
