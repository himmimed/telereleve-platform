package ma.ceac.telereleve.anomaly;

import static ma.ceac.telereleve.anomaly.RuleTestSupport.NOW;
import static ma.ceac.telereleve.anomaly.RuleTestSupport.context;
import static ma.ceac.telereleve.anomaly.RuleTestSupport.electricity;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import ma.ceac.telereleve.alert.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VoltageOutOfRangeRuleTest {

    private final VoltageOutOfRangeRule rule = new VoltageOutOfRangeRule();

    private static InMemoryReadingHistory reading(String voltage) {
        return new InMemoryReadingHistory().add(NOW, "100", voltage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"207", "207.0", "230", "253", "253.0"})
    void acceptsTheRangeIncludingItsBounds(String voltage) {
        assertThat(rule.evaluate(electricity(), context("A5", reading(voltage), NOW))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"206.9", "253.1", "0", "300"})
    void raisesOutsideTheRange(String voltage) {
        assertThat(rule.evaluate(electricity(), context("A5", reading(voltage), NOW))).hasValueSatisfying(r -> {
            assertThat(r.severity()).isEqualTo(Severity.MEDIUM);
            assertThat(r.message()).contains("207 à 253 V");
        });
    }

    @Test
    void ignoresWaterMeters() {
        assertThat(rule.evaluate(RuleTestSupport.water(), context("A5", reading("300"), NOW))).isEmpty();
    }

    @Test
    void ignoresReadingsWithoutVoltage() {
        assertThat(rule.evaluate(electricity(), context("A5", reading(null), NOW))).isEmpty();
    }

    @Test
    void usesTheConfiguredTolerance() {
        // 230 V +/- 5 % = 218.5 to 241.5 V: 245 V is now out of range.
        InMemoryReadingHistory history = reading("245");
        RuleContext fivePercent = new RuleContext(NOW, history.at(NOW), new RuleSettings(true, new BigDecimal("5"), null), history);

        assertThat(rule.evaluate(electricity(), fivePercent))
                .hasValueSatisfying(r -> assertThat(r.message()).contains("218,5 à 241,5 V"));
    }
}
