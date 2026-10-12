package ma.ceac.telereleve.reading;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * MQTT message published by a meter (specification, "MQTT CONTRACT"). Every field is nullable here:
 * the payload comes from outside and ReadingValidator decides what is acceptable.
 * Unknown fields are ignored so meters with a newer firmware do not break ingestion.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReadingPayload(
        String serial,
        String type,
        Instant timestamp,
        BigDecimal index,
        String unit,
        BigDecimal voltage,
        Integer signalStrength) {
}
