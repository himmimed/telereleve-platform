package ma.ceac.telereleve.reading.dto;

import java.math.BigDecimal;
import java.time.Instant;
import ma.ceac.telereleve.reading.StoredReading;

/** A reading as shown to users. voltage is null for water meters. */
public record ReadingResponse(Instant timestamp, BigDecimal index, BigDecimal voltage, int signalStrength) {

    public static ReadingResponse from(StoredReading reading) {
        return new ReadingResponse(reading.readAt(), reading.index(), reading.voltage(), reading.signalStrength());
    }
}
