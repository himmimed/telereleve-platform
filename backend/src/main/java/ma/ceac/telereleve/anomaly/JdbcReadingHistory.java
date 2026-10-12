package ma.ceac.telereleve.anomaly;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import ma.ceac.telereleve.reading.ReadingRepository;
import ma.ceac.telereleve.reading.StoredReading;

/** ReadingHistory backed by the reading hypertable. Each call is one indexed lookup on (meter_id, read_at). */
final class JdbcReadingHistory implements ReadingHistory {

    private final ReadingRepository readings;
    private final UUID meterId;

    JdbcReadingHistory(ReadingRepository readings, UUID meterId) {
        this.readings = readings;
        this.meterId = meterId;
    }

    @Override
    public Optional<StoredReading> latestAtOrBefore(Instant t) {
        return readings.findLatestAtOrBefore(meterId, t);
    }

    @Override
    public Optional<StoredReading> latestBefore(Instant t) {
        return readings.findPrevious(meterId, t);
    }

    @Override
    public Optional<Instant> firstReadingAt() {
        return readings.findFirstReadingAt(meterId);
    }
}
