package ma.ceac.telereleve.anomaly;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import ma.ceac.telereleve.alert.Severity;
import ma.ceac.telereleve.meter.Meter;
import org.springframework.stereotype.Component;

/**
 * A2 SPIKE (MEDIUM): consumption of the last hour above `threshold` (3 by default) times the average
 * consumption of the same hour over the previous days (7 by default).
 * Comparing with the same hour of previous days, not with the daily average, avoids flagging the
 * normal evening peak.
 * Skipped with less than 3 days of history, and when the reference average is 0 (the ratio would be
 * meaningless, e.g. a water meter at night).
 */
@Component
public class SpikeRule implements AnomalyRule {

    static final int MIN_HISTORY_DAYS = 3;
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);

    @Override
    public String code() {
        return "A2";
    }

    @Override
    public Optional<AnomalyResult> evaluate(Meter meter, RuleContext ctx) {
        Optional<Instant> first = ctx.history().firstReadingAt();
        if (first.isEmpty()) {
            return Optional.empty();
        }
        Instant now = ctx.now();
        Optional<BigDecimal> lastHour = ctx.history().consumption(now.minus(HOUR), now);
        if (lastHour.isEmpty() || lastHour.get().signum() <= 0) {
            return Optional.empty();
        }

        int days = Math.max(1, ctx.settings().windowHoursOr(168) / 24);
        BigDecimal sum = BigDecimal.ZERO;
        int samples = 0;
        for (int d = 1; d <= days; d++) {
            Instant to = now.minus(DAY.multipliedBy(d));
            Instant from = to.minus(HOUR);
            if (first.get().isAfter(from)) {
                continue; // the meter did not exist yet at that hour
            }
            Optional<BigDecimal> consumed = ctx.history().consumption(from, to);
            if (consumed.isPresent()) {
                sum = sum.add(consumed.get().max(BigDecimal.ZERO));
                samples++;
            }
        }
        if (samples < MIN_HISTORY_DAYS || sum.signum() == 0) {
            return Optional.empty();
        }

        BigDecimal average = sum.divide(BigDecimal.valueOf(samples), MathContext.DECIMAL64);
        BigDecimal ratio = lastHour.get().divide(average, MathContext.DECIMAL64);
        if (ratio.compareTo(ctx.settings().thresholdOr("3")) <= 0) {
            return Optional.empty();
        }
        String unit = Messages.unit(meter.getType());
        return Optional.of(new AnomalyResult(Severity.MEDIUM,
                "Consommation de la dernière heure : %s %s, soit %s fois la moyenne de cette heure sur %d jours (%s %s)."
                        .formatted(Messages.number(lastHour.get(), 3), unit, Messages.number(ratio, 1), samples,
                                Messages.number(average, 3), unit)));
    }
}
