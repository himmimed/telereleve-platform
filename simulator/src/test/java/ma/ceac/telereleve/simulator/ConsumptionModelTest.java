package ma.ceac.telereleve.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.IntStream;
import ma.ceac.telereleve.simulator.SimulatedMeter.MeterType;
import org.junit.jupiter.api.Test;

class ConsumptionModelTest {

    private static final SimulatedMeter ELECTRICITY = new SimulatedMeter("CEAC-E-000001", MeterType.ELECTRICITY);
    private static final SimulatedMeter WATER = new SimulatedMeter("CEAC-W-000003", MeterType.WATER);

    /** Midnight local time (UTC+1) on a weekday. */
    private static final Instant MONDAY = Instant.parse("2026-10-04T23:00:00Z");

    @Test
    void indexNeverDecreasesMinuteByMinute() {
        for (SimulatedMeter meter : new SimulatedMeter[] {ELECTRICITY, WATER}) {
            double previous = ConsumptionModel.indexAt(meter, MONDAY);
            for (int minute = 1; minute <= 3 * 24 * 60; minute++) {
                double index = ConsumptionModel.indexAt(meter, MONDAY.plusSeconds(60L * minute));
                assertThat(index).as("%s at minute %d", meter.serial(), minute).isGreaterThanOrEqualTo(previous);
                previous = index;
            }
        }
    }

    @Test
    void indexIsAPureFunctionOfSerialAndTime() {
        Instant t = Instant.parse("2026-10-11T14:37:12Z");
        assertThat(ConsumptionModel.indexAt(ELECTRICITY, t)).isEqualTo(ConsumptionModel.indexAt(ELECTRICITY, t));
        assertThat(ConsumptionModel.indexAt(ELECTRICITY, t))
                .isNotEqualTo(ConsumptionModel.indexAt(new SimulatedMeter("CEAC-E-000002", MeterType.ELECTRICITY), t));
    }

    @Test
    void dailyConsumptionStaysCloseToTheMeterAverage() {
        for (int n = 1; n <= 40; n++) {
            SimulatedMeter meter = new SimulatedMeter("CEAC-E-%06d".formatted(n), MeterType.ELECTRICITY);
            double average = ConsumptionModel.dailyBase(MeterType.ELECTRICITY, ConsumptionModel.seedOf(meter.serial()));
            double day = ConsumptionModel.indexAt(meter, MONDAY.plus(Duration.ofDays(1))) - ConsumptionModel.indexAt(meter, MONDAY);
            // +/-10 % daily noise, plus rounding to 0.01.
            assertThat(day).isBetween(average * 0.89, average * 1.11);
        }
    }

    @Test
    void consumesLessAtNightThanInTheEvening() {
        double night = 0;
        double evening = 0;
        for (int day = 0; day < 7; day++) {
            Instant midnight = MONDAY.plus(Duration.ofDays(day));
            night += hourly(ELECTRICITY, midnight.plus(Duration.ofHours(3)));
            evening += hourly(ELECTRICITY, midnight.plus(Duration.ofHours(19)));
        }
        assertThat(evening).isGreaterThan(night * 3);
    }

    @Test
    void waterUseIsAlmostNilAtNight() {
        Instant threeAm = MONDAY.plus(Duration.ofHours(3));
        Instant eightAm = MONDAY.plus(Duration.ofHours(7));
        assertThat(hourly(WATER, threeAm)).isLessThan(hourly(WATER, eightAm) / 10);
    }

    @Test
    void electricityVoltageStaysWithin2PercentOf230() {
        IntStream.range(0, 5000).forEach(minute -> {
            Double voltage = ConsumptionModel.voltageAt(ELECTRICITY, MONDAY.plusSeconds(60L * minute));
            assertThat(voltage).isBetween(225.4, 234.6);
        });
    }

    @Test
    void waterMetersHaveNoVoltage() {
        assertThat(ConsumptionModel.voltageAt(WATER, MONDAY)).isNull();
    }

    @Test
    void signalStrengthIsARealisticRssi() {
        IntStream.range(0, 500).forEach(minute ->
                assertThat(ConsumptionModel.signalAt(ELECTRICITY, MONDAY.plusSeconds(60L * minute))).isBetween(-92, -60));
    }

    private static double hourly(SimulatedMeter meter, Instant start) {
        return ConsumptionModel.indexAt(meter, start.plus(Duration.ofHours(1))) - ConsumptionModel.indexAt(meter, start);
    }
}
