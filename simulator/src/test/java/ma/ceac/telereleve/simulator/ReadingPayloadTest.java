package ma.ceac.telereleve.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import ma.ceac.telereleve.simulator.SimulatedMeter.MeterType;
import org.junit.jupiter.api.Test;

/** The payload is a contract with the backend: field names and formats must match the specification. */
class ReadingPayloadTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void electricityPayloadMatchesTheMqttContract() throws Exception {
        SimulatedMeter meter = new SimulatedMeter("CEAC-E-000123", MeterType.ELECTRICITY);

        JsonNode payload = json.readTree(ReadingPayload.of(meter, Instant.parse("2027-03-15T10:15:00Z")).toJson());

        assertThat(payload.fieldNames()).toIterable()
                .containsExactly("serial", "type", "timestamp", "index", "unit", "voltage", "signalStrength");
        assertThat(payload.get("serial").asText()).isEqualTo("CEAC-E-000123");
        assertThat(payload.get("type").asText()).isEqualTo("ELECTRICITY");
        assertThat(payload.get("timestamp").asText()).isEqualTo("2027-03-15T10:15:00Z");
        assertThat(payload.get("index").isNumber()).isTrue();
        assertThat(payload.get("unit").asText()).isEqualTo("kWh");
        assertThat(payload.get("voltage").asDouble()).isBetween(225.4, 234.6);
        assertThat(payload.get("signalStrength").isInt()).isTrue();
    }

    @Test
    void waterPayloadHasNoVoltage() throws Exception {
        SimulatedMeter meter = new SimulatedMeter("CEAC-W-000003", MeterType.WATER);

        JsonNode payload = json.readTree(ReadingPayload.of(meter, Instant.parse("2027-03-15T10:15:00Z")).toJson());

        assertThat(payload.has("voltage")).isFalse();
        assertThat(payload.get("unit").asText()).isEqualTo("m3");
    }

    @Test
    void topicFollowsTheSpecifiedPattern() {
        assertThat(new SimulatedMeter("CEAC-E-000123", MeterType.ELECTRICITY).topic())
                .isEqualTo("ceac/meters/CEAC-E-000123/readings");
    }
}
