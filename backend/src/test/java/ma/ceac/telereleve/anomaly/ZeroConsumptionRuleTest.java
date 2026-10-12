package ma.ceac.telereleve.anomaly;

import static ma.ceac.telereleve.anomaly.RuleTestSupport.NOW;
import static ma.ceac.telereleve.anomaly.RuleTestSupport.context;
import static ma.ceac.telereleve.anomaly.RuleTestSupport.electricity;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import ma.ceac.telereleve.alert.Severity;
import ma.ceac.telereleve.meter.MeterStatus;
import ma.ceac.telereleve.meter.MeterType;
import org.junit.jupiter.api.Test;

class ZeroConsumptionRuleTest {

    private final ZeroConsumptionRule rule = new ZeroConsumptionRule();

    @Test
    void raisesWhenTheIndexDidNotMoveFor24Hours() {
        InMemoryReadingHistory history = new InMemoryReadingHistory();
        history.addSeries(NOW.minus(Duration.ofHours(30)), NOW, Duration.ofMinutes(15), new BigDecimal("500"), BigDecimal.ZERO);

        Optional<AnomalyResult> result = rule.evaluate(electricity(), context("A1", history, NOW));

        assertThat(result).hasValueSatisfying(r -> {
            assertThat(r.severity()).isEqualTo(Severity.MEDIUM);
            assertThat(r.message()).contains("24 h");
        });
    }

    @Test
    void staysQuietWhenTheMeterConsumedEvenALittle() {
        InMemoryReadingHistory history = new InMemoryReadingHistory();
        history.addSeries(NOW.minus(Duration.ofHours(30)), NOW.minus(Duration.ofMinutes(15)), Duration.ofMinutes(15),
                new BigDecimal("500"), BigDecimal.ZERO);
        history.add(NOW, "500.001");

        assertThat(rule.evaluate(electricity(), context("A1", history, NOW))).isEmpty();
    }

    @Test
    void doesNotJudgeAMeterWithLessHistoryThanTheWindow() {
        InMemoryReadingHistory history = new InMemoryReadingHistory();
        history.addSeries(NOW.minus(Duration.ofHours(10)), NOW, Duration.ofMinutes(15), new BigDecimal("500"), BigDecimal.ZERO);

        assertThat(rule.evaluate(electricity(), context("A1", history, NOW))).isEmpty();
    }

    @Test
    void ignoresInactiveMeters() {
        InMemoryReadingHistory history = new InMemoryReadingHistory();
        history.addSeries(NOW.minus(Duration.ofHours(30)), NOW, Duration.ofMinutes(15), new BigDecimal("500"), BigDecimal.ZERO);

        var inactive = RuleTestSupport.meter("CEAC-E-000009", MeterType.ELECTRICITY, MeterStatus.INACTIVE);
        assertThat(rule.evaluate(inactive, context("A1", history, NOW))).isEmpty();
    }

    @Test
    void usesTheConfiguredWindow() {
        InMemoryReadingHistory history = new InMemoryReadingHistory();
        history.addSeries(NOW.minus(Duration.ofHours(3)), NOW, Duration.ofMinutes(15), new BigDecimal("500"), BigDecimal.ZERO);
        RuleContext twoHours = new RuleContext(NOW, history.at(NOW), new RuleSettings(true, null, 2), history);

        assertThat(rule.evaluate(electricity(), twoHours)).hasValueSatisfying(r -> assertThat(r.message()).contains("2 h"));
    }
}
