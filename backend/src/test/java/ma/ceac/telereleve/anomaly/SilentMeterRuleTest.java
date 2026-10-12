package ma.ceac.telereleve.anomaly;

import static ma.ceac.telereleve.anomaly.RuleTestSupport.NOW;
import static ma.ceac.telereleve.anomaly.RuleTestSupport.electricity;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import ma.ceac.telereleve.alert.Severity;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterStatus;
import ma.ceac.telereleve.meter.MeterType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SilentMeterRuleTest {

    private final SilentMeterRule rule = new SilentMeterRule();

    private static RuleContext ctx() {
        return new RuleContext(NOW, null, RuleSettings.DEFAULTS.get("A3"), new InMemoryReadingHistory());
    }

    private static Meter lastReading(Duration ago) {
        Meter meter = electricity();
        meter.setLastReadingAt(NOW.minus(ago));
        return meter;
    }

    @Test
    void runsOnTheSchedulerNotOnIngestion() {
        assertThat(rule.trigger()).isEqualTo(AnomalyRule.Trigger.SCHEDULED);
    }

    @Test
    void toleratesExactly3Intervals() {
        assertThat(rule.evaluate(lastReading(Duration.ofMinutes(45)), ctx())).isEmpty();
    }

    @Test
    void raisesAfter3IntervalsWithoutReading() {
        assertThat(rule.evaluate(lastReading(Duration.ofMinutes(45).plusSeconds(1)), ctx()))
                .hasValueSatisfying(r -> {
                    assertThat(r.severity()).isEqualTo(Severity.HIGH);
                    assertThat(r.message()).contains("45 min").contains("15 min");
                });
    }

    @Test
    void formatsLongSilences() {
        assertThat(rule.evaluate(lastReading(Duration.ofMinutes(85)), ctx()))
                .hasValueSatisfying(r -> assertThat(r.message()).startsWith("Aucune relève depuis 1 h 25"));
    }

    @Test
    void measuresANeverReportingMeterFromItsRegistration() {
        Meter meter = electricity();
        ReflectionTestUtils.setField(meter, "createdAt", NOW.minus(Duration.ofHours(2)));

        assertThat(rule.evaluate(meter, ctx())).hasValueSatisfying(r -> assertThat(r.message()).contains("installation"));
    }

    @Test
    void doesNotFlagAMeterJustRegistered() {
        Meter meter = electricity();
        ReflectionTestUtils.setField(meter, "createdAt", NOW.minus(Duration.ofMinutes(10)));

        assertThat(rule.evaluate(meter, ctx())).isEmpty();
    }

    @Test
    void ignoresInactiveMeters() {
        Meter meter = RuleTestSupport.meter("CEAC-E-000003", MeterType.ELECTRICITY, MeterStatus.INACTIVE);
        meter.setLastReadingAt(Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(rule.evaluate(meter, ctx())).isEmpty();
    }

    @Test
    void usesTheConfiguredMultiple() {
        RuleContext five = new RuleContext(NOW, null, new RuleSettings(true, new BigDecimal("5"), null), new InMemoryReadingHistory());

        assertThat(rule.evaluate(lastReading(Duration.ofMinutes(60)), five)).isEmpty();
        assertThat(rule.evaluate(lastReading(Duration.ofMinutes(76)), five)).isPresent();
    }
}
