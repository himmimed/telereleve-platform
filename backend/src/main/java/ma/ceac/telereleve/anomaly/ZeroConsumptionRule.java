package ma.ceac.telereleve.anomaly;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import ma.ceac.telereleve.alert.Severity;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterStatus;
import org.springframework.stereotype.Component;

/**
 * A1 ZERO_CONSUMPTION (MEDIUM): an active meter that keeps reporting but whose index has not moved
 * over the window (24 h by default). Typical causes: blocked meter, disconnected supply, bypass.
 * Not raised when the meter has less history than the window: there is nothing to judge yet.
 */
@Component
public class ZeroConsumptionRule implements AnomalyRule {

    static final int DEFAULT_WINDOW_HOURS = 24;

    @Override
    public String code() {
        return "A1";
    }

    @Override
    public Optional<AnomalyResult> evaluate(Meter meter, RuleContext ctx) {
        if (meter.getStatus() != MeterStatus.ACTIVE) {
            return Optional.empty();
        }
        int windowHours = ctx.settings().windowHoursOr(DEFAULT_WINDOW_HOURS);
        Instant windowStart = ctx.now().minus(Duration.ofHours(windowHours));
        Optional<Instant> first = ctx.history().firstReadingAt();
        if (first.isEmpty() || first.get().isAfter(windowStart)) {
            return Optional.empty();
        }
        return ctx.history().consumption(windowStart, ctx.now())
                .filter(consumed -> consumed.compareTo(BigDecimal.ZERO) == 0)
                .map(zero -> new AnomalyResult(Severity.MEDIUM,
                        "Aucune consommation enregistrée depuis %d h sur un compteur actif.".formatted(windowHours)));
    }
}
