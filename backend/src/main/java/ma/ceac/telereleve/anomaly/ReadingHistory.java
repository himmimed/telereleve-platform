package ma.ceac.telereleve.anomaly;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import ma.ceac.telereleve.reading.StoredReading;

/**
 * Past readings of one meter, as needed by the rules. An interface so that rule tests can use an
 * in-memory history instead of a database.
 */
public interface ReadingHistory {

    /** Most recent reading at or before t. */
    Optional<StoredReading> latestAtOrBefore(Instant t);

    /**
     * latestAtOrBefore for several instants at once, keyed by instant; an instant without reading is
     * absent. One query in the database implementation (rule A2 needs 16 of them per reading).
     */
    default Map<Instant, StoredReading> latestAtOrBefore(Collection<Instant> instants) {
        Map<Instant, StoredReading> result = new HashMap<>();
        for (Instant t : instants) {
            latestAtOrBefore(t).ifPresent(r -> result.put(t, r));
        }
        return result;
    }

    /** Most recent reading strictly before t. */
    Optional<StoredReading> latestBefore(Instant t);

    /** Time of the meter's first reading, empty if it never reported. */
    Optional<Instant> firstReadingAt();

    /**
     * Consumption between two instants: index as of `to` minus index as of `from`. Empty when there is
     * no reading at or before `from` (the period is not covered by data). May be negative if the index
     * went down; callers decide what that means.
     */
    default Optional<BigDecimal> consumption(Instant from, Instant to) {
        Optional<StoredReading> start = latestAtOrBefore(from);
        Optional<StoredReading> end = latestAtOrBefore(to);
        if (start.isEmpty() || end.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(end.get().index().subtract(start.get().index()));
    }
}
