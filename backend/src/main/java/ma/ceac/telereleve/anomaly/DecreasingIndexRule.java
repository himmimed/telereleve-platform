package ma.ceac.telereleve.anomaly;

import java.util.Optional;
import ma.ceac.telereleve.alert.Severity;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.reading.StoredReading;
import org.springframework.stereotype.Component;

/**
 * A4 DECREASING_INDEX (CRITICAL): the new index is lower than the previous one. An index only grows,
 * so this means fraud (tampered meter) or a meter replacement. Never resolved automatically: a person
 * must check on site and close the alert.
 * The previous reading is the latest one *by timestamp* before this reading, so a late message is
 * compared with the reading that really preceded it.
 */
@Component
public class DecreasingIndexRule implements AnomalyRule {

    @Override
    public String code() {
        return "A4";
    }

    @Override
    public boolean autoResolve() {
        return false;
    }

    @Override
    public Optional<AnomalyResult> evaluate(Meter meter, RuleContext ctx) {
        StoredReading current = ctx.reading();
        if (current == null) {
            return Optional.empty();
        }
        return ctx.history().latestBefore(current.readAt())
                .filter(previous -> current.index().compareTo(previous.index()) < 0)
                .map(previous -> {
                    String unit = Messages.unit(meter.getType());
                    return new AnomalyResult(Severity.CRITICAL,
                            "Index en baisse : %s %s puis %s %s. Fraude possible ou remplacement du compteur."
                                    .formatted(Messages.number(previous.index(), 3), unit,
                                            Messages.number(current.index(), 3), unit));
                });
    }
}
