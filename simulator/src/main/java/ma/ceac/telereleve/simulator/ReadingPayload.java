package ma.ceac.telereleve.simulator;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;

/**
 * MQTT payload, exactly as specified:
 * {"serial":"CEAC-E-000123","type":"ELECTRICITY","timestamp":"2027-03-15T10:15:00Z","index":15234.75,
 *  "unit":"kWh","voltage":229.8,"signalStrength":-71}
 * voltage is omitted for water meters.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReadingPayload(
        String serial,
        SimulatedMeter.MeterType type,
        Instant timestamp,
        double index,
        String unit,
        Double voltage,
        int signalStrength) {

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static ReadingPayload of(SimulatedMeter meter, Instant timestamp) {
        return new ReadingPayload(
                meter.serial(),
                meter.type(),
                timestamp,
                ConsumptionModel.indexAt(meter, timestamp),
                meter.type().unit(),
                ConsumptionModel.voltageAt(meter, timestamp),
                ConsumptionModel.signalAt(meter, timestamp));
    }

    public byte[] toJson() {
        try {
            return JSON.writeValueAsBytes(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise reading of " + serial, e);
        }
    }
}
