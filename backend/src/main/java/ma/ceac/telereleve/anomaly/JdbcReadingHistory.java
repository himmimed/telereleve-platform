package ma.ceac.telereleve.anomaly;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import ma.ceac.telereleve.reading.ReadingRepository;
import ma.ceac.telereleve.reading.StoredReading;

/**
 * ReadingHistory backed by the reading hypertable, for one evaluation of the rules: lookups are indexed on
 * (meter_id, read_at) and remembered, so rules asking for the same instant cost one query. Ingestion is
 * limited by the number of round trips per reading, which this keeps low (D34).
 */
final class JdbcReadingHistory implements ReadingHistory {

    private final ReadingRepository readings;
    private final UUID meterId;
    private final Map<Instant, Optional<StoredReading>> latest = new HashMap<>();

    /** @param current the reading being judged, if any: it is the latest one at its own time */
    JdbcReadingHistory(ReadingRepository readings, UUID meterId, StoredReading current) {
        this.readings = readings;
        this.meterId = meterId;
        if (current != null) {
            latest.put(current.readAt(), Optional.of(current));
        }
    }

    @Override
    public Optional<StoredReading> latestAtOrBefore(Instant t) {
        return latest.computeIfAbsent(t, at -> readings.findLatestAtOrBefore(meterId, at));
    }

    @Override
    public Map<Instant, StoredReading> latestAtOrBefore(Collection<Instant> instants) {
        List<Instant> missing = instants.stream().filter(t -> !latest.containsKey(t)).distinct().toList();
        if (!missing.isEmpty()) {
            Map<Instant, StoredReading> found = readings.findLatestAtOrBefore(meterId, missing);
            missing.forEach(t -> latest.put(t, Optional.ofNullable(found.get(t))));
        }
        Map<Instant, StoredReading> result = new HashMap<>();
        for (Instant t : instants) {
            latest.get(t).ifPresent(r -> result.put(t, r));
        }
        return result;
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
