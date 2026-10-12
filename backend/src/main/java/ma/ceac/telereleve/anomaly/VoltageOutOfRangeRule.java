package ma.ceac.telereleve.anomaly;

import java.math.BigDecimal;
import java.util.Optional;
import ma.ceac.telereleve.alert.Severity;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterType;
import org.springframework.stereotype.Component;

/**
 * A5 VOLTAGE_OUT_OF_RANGE (MEDIUM, electricity only): voltage outside 230 V +/- threshold %
 * (10 % by default, i.e. below 207 V or above 253 V; 207 and 253 themselves are acceptable).
 * Resolved automatically by the next reading with a normal voltage.
 */
@Component
public class VoltageOutOfRangeRule implements AnomalyRule {

    static final BigDecimal NOMINAL = new BigDecimal("230");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Override
    public String code() {
        return "A5";
    }

    @Override
    public Optional<AnomalyResult> evaluate(Meter meter, RuleContext ctx) {
        if (meter.getType() != MeterType.ELECTRICITY || ctx.reading() == null || ctx.reading().voltage() == null) {
            return Optional.empty();
        }
        BigDecimal tolerance = NOMINAL.multiply(ctx.settings().thresholdOr("10")).divide(HUNDRED);
        BigDecimal min = NOMINAL.subtract(tolerance);
        BigDecimal max = NOMINAL.add(tolerance);
        BigDecimal voltage = ctx.reading().voltage();
        if (voltage.compareTo(min) >= 0 && voltage.compareTo(max) <= 0) {
            return Optional.empty();
        }
        return Optional.of(new AnomalyResult(Severity.MEDIUM,
                "Tension mesurée à %s V, hors de la plage autorisée (%s à %s V)."
                        .formatted(Messages.number(voltage, 1), Messages.number(min, 1), Messages.number(max, 1))));
    }
}
