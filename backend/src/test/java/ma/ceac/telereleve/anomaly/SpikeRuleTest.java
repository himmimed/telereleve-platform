package ma.ceac.telereleve.anomaly;

import static ma.ceac.telereleve.anomaly.RuleTestSupport.NOW;
import static ma.ceac.telereleve.anomaly.RuleTestSupport.electricity;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import ma.ceac.telereleve.alert.Severity;
import org.junit.jupiter.api.Test;

/**
 * Builds a history where the same hour (17:00-18:00) consumed exactly 1 kWh on each previous day,
 * then a last hour consuming the given amount. The reference average is therefore 1 kWh.
 */
class SpikeRuleTest {

    private final SpikeRule rule = new SpikeRule();

    private static InMemoryReadingHistory history(int previousDays, String lastHourConsumption) {
        InMemoryReadingHistory history = new InMemoryReadingHistory();
        BigDecimal index = new BigDecimal("1000");
        for (int d = previousDays; d >= 1; d--) {
            Instant end = NOW.minus(Duration.ofDays(d));
            history.add(end.minus(Duration.ofHours(1)), index.toPlainString());
            index = index.add(BigDecimal.ONE);
            history.add(end, index.toPlainString());
        }
        history.add(NOW.minus(Duration.ofHours(1)), index.toPlainString());
        history.add(NOW, index.add(new BigDecimal(lastHourConsumption)).toPlainString());
        return history;
    }

    private Optional<AnomalyResult> evaluate(InMemoryReadingHistory history) {
        return rule.evaluate(electricity(), RuleTestSupport.context("A2", history, NOW));
    }

    @Test
    void raisesAt3Point1TimesTheUsualConsumption() {
        Optional<AnomalyResult> result = evaluate(history(7, "3.1"));

        assertThat(result).hasValueSatisfying(r -> {
            assertThat(r.severity()).isEqualTo(Severity.MEDIUM);
            assertThat(r.message()).contains("3,1 fois").contains("7 jours");
        });
    }

    @Test
    void staysQuietAt2Point9Times() {
        assertThat(evaluate(history(7, "2.9"))).isEmpty();
    }

    @Test
    void staysQuietAtExactly3Times() {
        // "more than 3 times": 3.0 itself is not a spike.
        assertThat(evaluate(history(7, "3"))).isEmpty();
    }

    @Test
    void isSkippedWithLessThan3DaysOfHistory() {
        assertThat(evaluate(history(2, "10"))).isEmpty();
    }

    @Test
    void worksWith3DaysOfHistory() {
        assertThat(evaluate(history(3, "10"))).hasValueSatisfying(r -> assertThat(r.message()).contains("3 jours"));
    }

    @Test
    void isSkippedWhenTheUsualConsumptionOfThatHourIsZero() {
        InMemoryReadingHistory history = new InMemoryReadingHistory();
        history.addSeries(NOW.minus(Duration.ofDays(8)), NOW.minus(Duration.ofHours(1)), Duration.ofHours(1),
                new BigDecimal("50"), BigDecimal.ZERO);
        history.add(NOW, "52");

        assertThat(evaluate(history)).isEmpty();
    }

    @Test
    void usesTheConfiguredMultiplier() {
        RuleContext ctx = new RuleContext(NOW, null, new RuleSettings(true, new BigDecimal("2"), 168), history(7, "2.5"));

        assertThat(rule.evaluate(electricity(), ctx)).isPresent();
    }
}
