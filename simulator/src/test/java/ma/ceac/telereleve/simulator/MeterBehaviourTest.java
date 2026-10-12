package ma.ceac.telereleve.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import ma.ceac.telereleve.simulator.InjectedAnomaly.Type;
import ma.ceac.telereleve.simulator.SimulatedMeter.MeterType;
import org.junit.jupiter.api.Test;

/** Each injected anomaly must produce exactly the symptom its rule looks for. */
class MeterBehaviourTest {

    private static final SimulatedMeter METER = new SimulatedMeter("CEAC-E-000007", MeterType.ELECTRICITY);
    private static final Instant START = Instant.parse("2026-10-12T17:00:00Z");

    private static InjectedAnomaly anomaly(Type type) {
        return new InjectedAnomaly(type, START);
    }

    private static double index(Type type, Duration afterStart) {
        return MeterBehaviour.indexAt(METER, anomaly(type), START.plus(afterStart));
    }

    @Test
    void behavesNormallyBeforeTheAnomalyStarts() {
        Instant before = START.minus(Duration.ofMinutes(1));
        for (Type type : Type.values()) {
            assertThat(MeterBehaviour.indexAt(METER, anomaly(type), before)).isEqualTo(ConsumptionModel.indexAt(METER, before));
            assertThat(MeterBehaviour.publishes(anomaly(type), before)).isTrue();
        }
    }

    @Test
    void zeroFreezesTheIndex() {
        assertThat(index(Type.ZERO, Duration.ofHours(30))).isEqualTo(index(Type.ZERO, Duration.ZERO));
    }

    @Test
    void spikeMultipliesTheConsumptionOfOneHourBy5() {
        double normalHour = ConsumptionModel.indexAt(METER, START.plus(Duration.ofHours(1))) - ConsumptionModel.indexAt(METER, START);
        double spikeHour = index(Type.SPIKE, Duration.ofHours(1)) - index(Type.SPIKE, Duration.ZERO);

        assertThat(spikeHour).isCloseTo(5 * normalHour, org.assertj.core.data.Offset.offset(0.02));
        // Afterwards the meter consumes normally again, from the higher index.
        double nextHour = index(Type.SPIKE, Duration.ofHours(2)) - index(Type.SPIKE, Duration.ofHours(1));
        double normalNextHour = ConsumptionModel.indexAt(METER, START.plus(Duration.ofHours(2)))
                - ConsumptionModel.indexAt(METER, START.plus(Duration.ofHours(1)));
        assertThat(nextHour).isCloseTo(normalNextHour, org.assertj.core.data.Offset.offset(0.02));
    }

    @Test
    void decreasingIndexGoesDownOnceThenGrowsAgain() {
        double before = MeterBehaviour.indexAt(METER, anomaly(Type.DECREASING_INDEX), START.minusSeconds(60));
        double after = index(Type.DECREASING_INDEX, Duration.ZERO);
        double later = index(Type.DECREASING_INDEX, Duration.ofHours(2));

        assertThat(after).isLessThan(before);
        assertThat(later).isGreaterThan(after);
    }

    @Test
    void silentStopsPublishing() {
        assertThat(MeterBehaviour.publishes(anomaly(Type.SILENT), START)).isFalse();
        assertThat(MeterBehaviour.publishes(null, START)).isTrue();
    }

    @Test
    void voltageGoesAbove253Volts() {
        for (int minute = 0; minute < 120; minute++) {
            assertThat(MeterBehaviour.voltageAt(METER, anomaly(Type.VOLTAGE), START.plusSeconds(60L * minute)))
                    .isBetween(258.0, 262.0);
        }
    }
}
