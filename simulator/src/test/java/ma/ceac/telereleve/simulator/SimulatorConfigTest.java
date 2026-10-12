package ma.ceac.telereleve.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimulatorConfigTest {

    @Test
    void usesDemoDefaults() {
        SimulatorConfig config = SimulatorConfig.fromEnvironment(Map.of("MQTT_PASSWORD", "secret"));

        assertThat(config.mqttUrl()).isEqualTo("tcp://localhost:1883");
        assertThat(config.backendUrl()).isEqualTo("http://localhost:8080");
        assertThat(config.interval()).isEqualTo(Duration.ofMinutes(1));
        assertThat(config.maxMeters()).isZero();
    }

    @Test
    void readsEnvironmentVariables() {
        SimulatorConfig config = SimulatorConfig.fromEnvironment(Map.of(
                "MQTT_PASSWORD", "secret", "MQTT_URL", "tcp://mosquitto:1883", "BACKEND_URL", "http://backend:8080/",
                "SIM_INTERVAL_SECONDS", "15", "SIM_MAX_METERS", "1000"));

        assertThat(config.mqttUrl()).isEqualTo("tcp://mosquitto:1883");
        assertThat(config.backendUrl()).isEqualTo("http://backend:8080");
        assertThat(config.interval()).isEqualTo(Duration.ofSeconds(15));
        assertThat(config.maxMeters()).isEqualTo(1000);
    }

    @Test
    void requiresTheMqttPassword() {
        assertThatThrownBy(() -> SimulatorConfig.fromEnvironment(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MQTT_PASSWORD");
    }

    @Test
    void rejectsANonPositiveInterval() {
        assertThatThrownBy(() -> SimulatorConfig.fromEnvironment(Map.of("MQTT_PASSWORD", "x", "SIM_INTERVAL_SECONDS", "0")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neverPrintsThePassword() {
        assertThat(SimulatorConfig.fromEnvironment(Map.of("MQTT_PASSWORD", "s3cr3t")).toString()).doesNotContain("s3cr3t");
    }

    @Test
    void allowsAnomaliesInThePastOnlyWithinTheBackfilledHistory() {
        SimulatorConfig demo = SimulatorConfig.fromEnvironment(Map.of("MQTT_PASSWORD", "x",
                "SIM_BACKFILL_DAYS", "4", "SIM_ANOMALY_DELAY_MINUTES", "-1500"));
        assertThat(demo.anomalyDelay()).isEqualTo(Duration.ofMinutes(-1500));

        assertThatThrownBy(() -> SimulatorConfig.fromEnvironment(Map.of("MQTT_PASSWORD", "x",
                "SIM_BACKFILL_DAYS", "1", "SIM_ANOMALY_DELAY_MINUTES", "-1500")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsFastOnAnUnknownAnomalyType() {
        assertThatThrownBy(() -> SimulatorConfig.fromEnvironment(Map.of("MQTT_PASSWORD", "x", "SIM_ANOMALIES", "CEAC-E-000001:FIRE")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
