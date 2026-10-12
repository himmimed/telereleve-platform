package ma.ceac.telereleve.anomaly;

import static ma.ceac.telereleve.anomaly.RuleTestSupport.NOW;
import static ma.ceac.telereleve.anomaly.RuleTestSupport.context;
import static ma.ceac.telereleve.anomaly.RuleTestSupport.electricity;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import ma.ceac.telereleve.alert.Severity;
import org.junit.jupiter.api.Test;

class DecreasingIndexRuleTest {

    private final DecreasingIndexRule rule = new DecreasingIndexRule();

    @Test
    void raisesACriticalAlertWhenTheIndexGoesDown() {
        InMemoryReadingHistory history = new InMemoryReadingHistory()
                .add(NOW.minus(Duration.ofMinutes(15)), "18342.6")
                .add(NOW, "18297.1");

        assertThat(rule.evaluate(electricity(), context("A4", history, NOW))).hasValueSatisfying(r -> {
            assertThat(r.severity()).isEqualTo(Severity.CRITICAL);
            assertThat(r.message()).contains("18 342,6").contains("18 297,1").contains("Fraude possible");
        });
    }

    @Test
    void staysQuietWhenTheIndexGrowsOrStaysTheSame() {
        InMemoryReadingHistory history = new InMemoryReadingHistory()
                .add(NOW.minus(Duration.ofMinutes(30)), "100")
                .add(NOW.minus(Duration.ofMinutes(15)), "100")
                .add(NOW, "100.5");

        assertThat(rule.evaluate(electricity(), context("A4", history, NOW))).isEmpty();
        assertThat(rule.evaluate(electricity(), context("A4", history, NOW.minus(Duration.ofMinutes(15))))).isEmpty();
    }

    @Test
    void staysQuietForTheFirstReading() {
        InMemoryReadingHistory history = new InMemoryReadingHistory().add(NOW, "5");

        assertThat(rule.evaluate(electricity(), context("A4", history, NOW))).isEmpty();
    }

    @Test
    void comparesALateReadingWithTheReadingThatPrecededItInTime() {
        // 10:00 -> 100, 10:30 -> 110 arrive first; 10:15 -> 105 arrives late: no decrease.
        InMemoryReadingHistory history = new InMemoryReadingHistory()
                .add(NOW.minus(Duration.ofMinutes(30)), "100")
                .add(NOW, "110")
                .add(NOW.minus(Duration.ofMinutes(15)), "105");

        assertThat(rule.evaluate(electricity(), context("A4", history, NOW.minus(Duration.ofMinutes(15))))).isEmpty();
    }

    @Test
    void isNeverResolvedAutomatically() {
        assertThat(rule.autoResolve()).isFalse();
    }
}
