package ma.ceac.telereleve.anomaly;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import ma.ceac.telereleve.alert.Severity;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterStatus;
import org.springframework.stereotype.Component;

/**
 * A3 SILENT_METER (HIGH): no reading for more than `threshold` (3 by default) times the meter's
 * reading interval. A missing reading cannot trigger anything by itself, so this rule runs on a
 * schedule (AnomalyScheduler) instead of on ingestion. A meter that never reported is measured from
 * its registration date. Resolved automatically when the meter reports again.
 */
@Component
public class SilentMeterRule implements AnomalyRule {

    @Override
    public String code() {
        return "A3";
    }

    @Override
    public Trigger trigger() {
        return Trigger.SCHEDULED;
    }

    @Override
    public Optional<AnomalyResult> evaluate(Meter meter, RuleContext ctx) {
        if (meter.getStatus() != MeterStatus.ACTIVE) {
            return Optional.empty();
        }
        Instant reference = meter.getLastReadingAt() != null ? meter.getLastReadingAt() : meter.getCreatedAt();
        if (reference == null) {
            return Optional.empty();
        }
        long multiple = ctx.settings().thresholdOr("3").longValue();
        Duration allowed = Duration.ofMinutes(multiple * meter.getReadingIntervalMin());
        Duration silentFor = Duration.between(reference, ctx.now());
        if (silentFor.compareTo(allowed) <= 0) {
            return Optional.empty();
        }
        String since = meter.getLastReadingAt() != null ? "Aucune relève depuis " : "Aucune relève reçue depuis l’installation, il y a ";
        return Optional.of(new AnomalyResult(Severity.HIGH, "%s%s (intervalle attendu : %d min)."
                .formatted(since, Messages.duration(silentFor), meter.getReadingIntervalMin())));
    }
}
