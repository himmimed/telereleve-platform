package ma.ceac.telereleve.anomaly;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import ma.ceac.telereleve.reading.StoredReading;

/** ReadingHistory for rule tests: readings are kept in memory, sorted by time. */
final class InMemoryReadingHistory implements ReadingHistory {

    private final TreeMap<Instant, StoredReading> readings = new TreeMap<>(Comparator.naturalOrder());
    private final UUID meterId = UUID.randomUUID();

    InMemoryReadingHistory add(Instant at, String index) {
        return add(at, index, null);
    }

    InMemoryReadingHistory add(Instant at, String index, String voltage) {
        readings.put(at, new StoredReading(meterId, at, new BigDecimal(index),
                voltage == null ? null : new BigDecimal(voltage), -70));
        return this;
    }

    /**
     * Regular readings every `step` from `from` to `to` (inclusive), the index growing by `perStep`.
     * Returns the last index written.
     */
    BigDecimal addSeries(Instant from, Instant to, Duration step, BigDecimal startIndex, BigDecimal perStep) {
        BigDecimal index = startIndex;
        for (Instant t = from; !t.isAfter(to); t = t.plus(step)) {
            add(t, index.toPlainString());
            index = index.add(perStep);
        }
        return index.subtract(perStep);
    }

    StoredReading at(Instant t) {
        return readings.get(t);
    }

    @Override
    public Optional<StoredReading> latestAtOrBefore(Instant t) {
        return Optional.ofNullable(readings.floorEntry(t)).map(e -> e.getValue());
    }

    @Override
    public Optional<StoredReading> latestBefore(Instant t) {
        return Optional.ofNullable(readings.lowerEntry(t)).map(e -> e.getValue());
    }

    @Override
    public Optional<Instant> firstReadingAt() {
        return readings.isEmpty() ? Optional.empty() : Optional.of(readings.firstKey());
    }
}
