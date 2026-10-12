package ma.ceac.telereleve.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MeterSimulatorTest {

    @Test
    void spreadsMetersAcrossTheInterval() {
        long interval = 60_000;
        Set<Long> seconds = new HashSet<>();
        for (int n = 1; n <= 1000; n++) {
            long offset = MeterSimulator.offsetOf(
                    new SimulatedMeter("CEAC-E-%06d".formatted(n), SimulatedMeter.MeterType.ELECTRICITY), interval);
            assertThat(offset).isBetween(0L, interval - 1);
            seconds.add(offset / 1000);
        }
        // 1000 meters land on (almost) every second of the minute, not on a single burst.
        assertThat(seconds).hasSizeGreaterThan(55);
    }

    @Test
    void publishesWithQos1AsSpecified() {
        assertThat(MeterSimulator.QOS).isEqualTo(1);
    }
}
