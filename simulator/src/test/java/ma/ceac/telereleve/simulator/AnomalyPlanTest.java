package ma.ceac.telereleve.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import ma.ceac.telereleve.simulator.InjectedAnomaly.Type;
import ma.ceac.telereleve.simulator.SimulatedMeter.MeterType;
import org.junit.jupiter.api.Test;

class AnomalyPlanTest {

    private static final Instant START = Instant.parse("2026-10-12T17:00:00Z");

    private static List<SimulatedMeter> park(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(n -> new SimulatedMeter("CEAC-%s-%06d".formatted(n % 3 == 0 ? "W" : "E", n),
                        n % 3 == 0 ? MeterType.WATER : MeterType.ELECTRICITY))
                .toList();
    }

    @Test
    void appliesExplicitAnomalies() {
        Map<String, InjectedAnomaly> plan = AnomalyPlan.build(park(10), "CEAC-E-000001:decreasing_index, CEAC-E-000002:SILENT", 0, START);

        assertThat(plan).containsOnlyKeys("CEAC-E-000001", "CEAC-E-000002");
        assertThat(plan.get("CEAC-E-000001")).isEqualTo(new InjectedAnomaly(Type.DECREASING_INDEX, START));
    }

    @Test
    void ignoresSerialsThatAreNotSimulated() {
        assertThat(AnomalyPlan.build(park(10), "CEAC-E-999999:ZERO", 0, START)).isEmpty();
    }

    @Test
    void neverInjectsAVoltageAnomalyOnAWaterMeter() {
        Map<String, InjectedAnomaly> plan = AnomalyPlan.build(park(10), "CEAC-W-000003:VOLTAGE", 0, START);

        assertThat(plan.get("CEAC-W-000003").type()).isEqualTo(Type.SPIKE);
    }

    @Test
    void picksAboutTheRequestedShareOfMetersDeterministically() {
        List<SimulatedMeter> park = park(1000);
        Map<String, InjectedAnomaly> first = AnomalyPlan.build(park, "", 10, START);
        Map<String, InjectedAnomaly> second = AnomalyPlan.build(park, "", 10, START);

        assertThat(first).hasSizeBetween(70, 130).isEqualTo(second);
        assertThat(first.values()).extracting(InjectedAnomaly::type).contains(Type.values());
    }

    @Test
    void rejectsTypos() {
        assertThatThrownBy(() -> AnomalyPlan.parse("CEAC-E-000001:FRAUD")).hasMessageContaining("DECREASING_INDEX");
        assertThatThrownBy(() -> AnomalyPlan.parse("CEAC-E-000001")).hasMessageContaining("SERIAL:TYPE");
    }
}
