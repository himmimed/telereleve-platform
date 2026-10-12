package ma.ceac.telereleve.reading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A row of the reading hypertable. voltage is null for water meters. */
public record StoredReading(UUID meterId, Instant readAt, BigDecimal index, BigDecimal voltage, int signalStrength) {
}
