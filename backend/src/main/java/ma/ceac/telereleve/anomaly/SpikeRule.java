package ma.ceac.telereleve.anomaly;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ma.ceac.telereleve.alert.Severity;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.reading.StoredReading;
import org.springframework.stereotype.Component;

/**
 * A2 SPIKE (MEDIUM): consumption of the last hour above `threshold` (3 by default) times the average
 * consumption of the same hour over the previous days (7 by default).
 * - Compared with the same hour of previous days, not with the daily average, so the normal evening
 *   peak is not flagged.
 * - Skipped with fewer than 3 days of samples, and when that hour usually consumes nothing (the ratio
 *   would be meaningless, e.g. a water meter at night).
 * - Measured as an hourly rate between real readings close to both ends of the hour. After a gap in
 *   the data (meter or network down), the consumption of the whole gap must not be read as "the last
 *   hour": an hour without a reading near its start or end is not judged (D31).
 */
@Component
public class SpikeRule implements AnomalyRule {

    static final int MIN_HISTORY_DAYS = 3;
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);
    private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);

    @Override
    public String code() {
        return "A2";
    }

    @Override
    public Optional<AnomalyResult> evaluate(Meter meter, RuleContext ctx) {
        Instant now = ctx.now();
        // A reading must exist within two reading intervals of each end of the hour.
        Duration tolerance = Duration.ofMinutes(2L * meter.getReadingIntervalMin());
        int days = Math.max(1, ctx.settings().windowHoursOr(168) / 24);
        // Both ends of the last hour and of the same hour on each previous day, fetched at once.
        List<Instant> instants = new ArrayList<>(2 * (days + 1));
        for (int d = 0; d <= days; d++) {
            Instant to = now.minus(DAY.multipliedBy(d));
            instants.add(to.minus(HOUR));
            instants.add(to);
        }
        Map<Instant, StoredReading> readings = ctx.history().latestAtOrBefore(instants);

        Optional<BigDecimal> lastHour = hourlyRate(readings, now.minus(HOUR), now, tolerance);
        if (lastHour.isEmpty() || lastHour.get().signum() <= 0) {
            return Optional.empty();
        }

        BigDecimal sum = BigDecimal.ZERO;
        int samples = 0;
        for (int d = 1; d <= days; d++) {
            Instant to = now.minus(DAY.multipliedBy(d));
            Optional<BigDecimal> rate = hourlyRate(readings, to.minus(HOUR), to, tolerance);
            if (rate.isPresent()) {
                sum = sum.add(rate.get().max(BigDecimal.ZERO));
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

    /**
     * Consumption per hour between the readings that bracket [from, to] (`latest`: the reading at or
     * before each instant), or empty if either reading is
     * further than `tolerance` from its end of the interval (data missing around it).
     */
    static Optional<BigDecimal> hourlyRate(Map<Instant, StoredReading> latest, Instant from, Instant to,
                                           Duration tolerance) {
        Optional<StoredReading> start = Optional.ofNullable(latest.get(from));
        Optional<StoredReading> end = Optional.ofNullable(latest.get(to));
        if (start.isEmpty() || end.isEmpty()
                || start.get().readAt().isBefore(from.minus(tolerance))
                || end.get().readAt().isBefore(to.minus(tolerance))) {
            return Optional.empty();
        }
        long seconds = Duration.between(start.get().readAt(), end.get().readAt()).toSeconds();
        if (seconds <= 0) {
            return Optional.empty();
        }
        BigDecimal consumed = end.get().index().subtract(start.get().index());
        return Optional.of(consumed.multiply(SECONDS_PER_HOUR).divide(BigDecimal.valueOf(seconds), MathContext.DECIMAL64));
    }
}
