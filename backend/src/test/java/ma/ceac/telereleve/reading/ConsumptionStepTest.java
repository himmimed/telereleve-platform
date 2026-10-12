package ma.ceac.telereleve.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ConsumptionStepTest {

    private static final ZoneId FES = ConsumptionService.ZONE;

    @Test
    void alignsHoursDaysAndMonthsOnTheLocalCalendar() {
        Instant t = Instant.parse("2026-10-12T14:37:00Z"); // 15:37 in Morocco (UTC+1)

        assertThat(ConsumptionStep.HOUR.align(t, FES)).isEqualTo(Instant.parse("2026-10-12T14:00:00Z"));
        assertThat(ConsumptionStep.DAY.align(t, FES)).isEqualTo(Instant.parse("2026-10-11T23:00:00Z"));
        assertThat(ConsumptionStep.MONTH.align(t, FES)).isEqualTo(Instant.parse("2026-09-30T23:00:00Z"));
    }

    @Test
    void stepsToTheNextBucketWithCalendarMonths() {
        Instant october = Instant.parse("2026-09-30T23:00:00Z");
        Instant november = ConsumptionStep.MONTH.next(october, FES);

        assertThat(november).isEqualTo(Instant.parse("2026-10-31T23:00:00Z"));
        assertThat(ConsumptionStep.DAY.next(november, FES)).isEqualTo(Instant.parse("2026-11-01T23:00:00Z"));
    }

    @Test
    void readsLowerCaseCodesAndWritesThem() {
        assertThat(ConsumptionStep.fromCode("hour")).isEqualTo(ConsumptionStep.HOUR);
        assertThat(ConsumptionStep.fromCode(" Month ")).isEqualTo(ConsumptionStep.MONTH);
        assertThat(ConsumptionStep.DAY.code()).isEqualTo("day");
        assertThatThrownBy(() -> ConsumptionStep.fromCode("week")).isInstanceOf(IllegalArgumentException.class);
    }
}
