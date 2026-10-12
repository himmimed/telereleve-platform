package ma.ceac.telereleve.reading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterStatus;
import ma.ceac.telereleve.meter.MeterType;
import ma.ceac.telereleve.reading.ReadingValidator.Accepted;
import ma.ceac.telereleve.reading.ReadingValidator.Rejected;
import ma.ceac.telereleve.reading.ReadingValidator.Verdict;
import org.junit.jupiter.api.Test;

class ReadingValidatorTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
    private static final String SERIAL = "CEAC-E-000123";
    private static final Meter ACTIVE = new Meter(SERIAL, MeterType.ELECTRICITY, "Foyer", "Fès", 34, -5, 15, MeterStatus.ACTIVE);

    private static ReadingPayload payload(Instant timestamp, String index) {
        return new ReadingPayload(SERIAL, "ELECTRICITY", timestamp, index == null ? null : new BigDecimal(index),
                "kWh", new BigDecimal("229.8"), -71);
    }

    private static Verdict validate(ReadingPayload payload) {
        return ReadingValidator.validate(SERIAL, payload, Optional.of(ACTIVE), NOW);
    }

    private static String reason(Verdict verdict) {
        assertThat(verdict).isInstanceOf(Rejected.class);
        return ((Rejected) verdict).reason();
    }

    @Test
    void acceptsAValidReading() {
        Verdict verdict = validate(payload(NOW, "15234.75"));

        assertThat(verdict).isInstanceOf(Accepted.class);
        assertThat(((Accepted) verdict).meter()).isSameAs(ACTIVE);
    }

    @Test
    void acceptsATimestampExactly5MinutesAhead() {
        assertThat(validate(payload(NOW.plus(Duration.ofMinutes(5)), "1"))).isInstanceOf(Accepted.class);
    }

    @Test
    void rejectsATimestampMoreThan5MinutesAhead() {
        assertThat(reason(validate(payload(NOW.plus(Duration.ofMinutes(5)).plusSeconds(1), "1"))))
                .contains("future");
    }

    @Test
    void acceptsOldReadings() {
        // A meter catching up after a network outage sends readings from hours ago.
        assertThat(validate(payload(NOW.minus(Duration.ofDays(2)), "1"))).isInstanceOf(Accepted.class);
    }

    @Test
    void acceptsAZeroIndex() {
        assertThat(validate(payload(NOW, "0"))).isInstanceOf(Accepted.class);
    }

    @Test
    void rejectsANegativeIndex() {
        assertThat(reason(validate(payload(NOW, "-0.01")))).isEqualTo("negative index");
    }

    @Test
    void rejectsAnUnknownSerial() {
        assertThat(reason(ReadingValidator.validate(SERIAL, payload(NOW, "1"), Optional.empty(), NOW)))
                .isEqualTo("unknown serial");
    }

    @Test
    void rejectsAnInactiveMeter() {
        Meter inactive = new Meter(SERIAL, MeterType.ELECTRICITY, "Foyer", "Fès", 34, -5, 15, MeterStatus.INACTIVE);
        assertThat(reason(ReadingValidator.validate(SERIAL, payload(NOW, "1"), Optional.of(inactive), NOW)))
                .isEqualTo("meter is not active");
    }

    @Test
    void rejectsAMeterPublishingOnAnotherMetersTopic() {
        assertThat(reason(ReadingValidator.validate("CEAC-E-000999", payload(NOW, "1"), Optional.of(ACTIVE), NOW)))
                .isEqualTo("serial does not match the topic");
    }

    @Test
    void rejectsATypeDifferentFromTheRegisteredMeter() {
        ReadingPayload water = new ReadingPayload(SERIAL, "WATER", NOW, BigDecimal.ONE, "m3", null, -70);
        assertThat(reason(validate(water))).contains("does not match");
    }

    @Test
    void rejectsMissingMandatoryFields() {
        assertThat(reason(validate(payload(null, "1")))).isEqualTo("missing timestamp");
        assertThat(reason(validate(payload(NOW, null)))).isEqualTo("missing index");
        assertThat(reason(validate(new ReadingPayload(SERIAL, "ELECTRICITY", NOW, BigDecimal.ONE, "kWh", null, null))))
                .isEqualTo("missing signalStrength");
        assertThat(reason(validate(new ReadingPayload(null, null, NOW, BigDecimal.ONE, null, null, -70))))
                .isEqualTo("missing serial");
    }
}
