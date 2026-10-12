package ma.ceac.telereleve.anomaly;

import java.time.Instant;
import ma.ceac.telereleve.reading.StoredReading;

/**
 * Everything a rule may look at.
 *
 * @param now      evaluation time: the reading's own timestamp for ON_READING rules (so replayed or
 *                 back-filled readings are judged at their time), the clock for SCHEDULED rules
 * @param reading  the reading that triggered the evaluation, null for SCHEDULED rules
 * @param settings threshold and window of the rule (alert_rule_config)
 * @param history  read access to the meter's past readings
 */
public record RuleContext(Instant now, StoredReading reading, RuleSettings settings, ReadingHistory history) {
}
