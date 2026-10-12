package ma.ceac.telereleve.anomaly;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ma.ceac.telereleve.alert.AlertService;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterStatus;
import ma.ceac.telereleve.reading.ReadingRepository;
import ma.ceac.telereleve.reading.StoredReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs the rules and turns their results into alerts.
 * For each enabled rule of the requested trigger:
 * - anomaly found -> AlertService.raise (no-op if an alert of this rule is already open);
 * - no anomaly    -> AlertService.resolve, if the rule allows automatic resolution.
 * One rule failing (bug, database hiccup) is logged and never prevents the other rules from running.
 */
@Service
public class AnomalyEngine {

    private static final Logger log = LoggerFactory.getLogger(AnomalyEngine.class);

    private final List<AnomalyRule> rules;
    private final RuleConfigService ruleConfigs;
    private final AlertService alerts;
    private final ReadingRepository readings;

    public AnomalyEngine(List<AnomalyRule> rules, RuleConfigService ruleConfigs, AlertService alerts,
                         ReadingRepository readings) {
        this.rules = rules;
        this.ruleConfigs = ruleConfigs;
        this.alerts = alerts;
        this.readings = readings;
    }

    /** ON_READING rules, judged at the reading's own time. */
    public void onReading(Meter meter, StoredReading reading) {
        run(meter, AnomalyRule.Trigger.ON_READING, reading.readAt(), reading, ruleConfigs.all());
    }

    /** SCHEDULED rules for one meter (A3). */
    public void onSchedule(Meter meter, Instant now, Map<String, RuleSettings> settings) {
        run(meter, AnomalyRule.Trigger.SCHEDULED, now, null, settings);
    }

    private void run(Meter meter, AnomalyRule.Trigger trigger, Instant now, StoredReading reading,
                     Map<String, RuleSettings> settings) {
        if (meter.getStatus() != MeterStatus.ACTIVE) {
            return;
        }
        ReadingHistory history = new JdbcReadingHistory(readings, meter.getId());
        for (AnomalyRule rule : rules) {
            RuleSettings ruleSettings = settings.getOrDefault(rule.code(), RuleSettings.DEFAULTS.get(rule.code()));
            if (rule.trigger() != trigger || ruleSettings == null || !ruleSettings.enabled()) {
                continue;
            }
            try {
                Optional<AnomalyResult> result = rule.evaluate(meter, new RuleContext(now, reading, ruleSettings, history));
                if (result.isPresent()) {
                    alerts.raise(meter.getId(), rule.code(), result.get().severity(), result.get().message(), now);
                } else if (rule.autoResolve()) {
                    alerts.resolve(meter.getId(), rule.code(), now);
                }
            } catch (RuntimeException e) {
                log.error("Rule {} failed on meter {}", rule.code(), meter.getSerial(), e);
            }
        }
    }
}
