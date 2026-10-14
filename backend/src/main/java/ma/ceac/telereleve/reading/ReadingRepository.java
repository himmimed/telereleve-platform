package ma.ceac.telereleve.reading;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import ma.ceac.telereleve.meter.MeterType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

/**
 * Readings are accessed with plain SQL rather than JPA: inserts must be idempotent
 * (ON CONFLICT DO NOTHING) and queries use TimescaleDB functions (time_bucket, first, last),
 * neither of which JPQL can express.
 */
@Repository
public class ReadingRepository {

    private final JdbcTemplate jdbc;

    public ReadingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return true if the reading was stored, false if (meter, timestamp) already existed */
    public boolean insertIfAbsent(StoredReading reading) {
        int inserted = jdbc.update("""
                INSERT INTO reading (meter_id, read_at, index_value, voltage, signal_strength)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (meter_id, read_at) DO NOTHING""",
                reading.meterId(), Timestamp.from(reading.readAt()), reading.index(), reading.voltage(),
                reading.signalStrength());
        return inserted == 1;
    }

    public Optional<StoredReading> findLatest(UUID meterId) {
        return jdbc.query("""
                SELECT meter_id, read_at, index_value, voltage, signal_strength
                FROM reading WHERE meter_id = ? ORDER BY read_at DESC LIMIT 1""",
                ReadingRepository::map, meterId).stream().findFirst();
    }

    /** Most recent reading strictly before `before` (the previous index, for rule A4). */
    public Optional<StoredReading> findPrevious(UUID meterId, Instant before) {
        return jdbc.query("""
                SELECT meter_id, read_at, index_value, voltage, signal_strength
                FROM reading WHERE meter_id = ? AND read_at < ? ORDER BY read_at DESC LIMIT 1""",
                ReadingRepository::map, meterId, Timestamp.from(before)).stream().findFirst();
    }

    /** Most recent reading at or before t: the index "as of" t. */
    public Optional<StoredReading> findLatestAtOrBefore(UUID meterId, Instant t) {
        return jdbc.query("""
                SELECT meter_id, read_at, index_value, voltage, signal_strength
                FROM reading WHERE meter_id = ? AND read_at <= ? ORDER BY read_at DESC LIMIT 1""",
                ReadingRepository::map, meterId, Timestamp.from(t)).stream().findFirst();
    }

    /** Time of the meter's first reading: how much history exists (rules A1 and A2). */
    public Optional<Instant> findFirstReadingAt(UUID meterId) {
        return jdbc.query("SELECT min(read_at) AS first_at FROM reading WHERE meter_id = ?",
                (rs, n) -> rs.getTimestamp("first_at"), meterId).stream()
                .filter(Objects::nonNull).map(Timestamp::toInstant).findFirst();
    }

    public long countByMeter(UUID meterId) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM reading WHERE meter_id = ?", Long.class, meterId);
        return count == null ? 0 : count;
    }

    /**
     * Most recent reading at or before each instant, in one query (one indexed lookup per instant on the
     * server). Instants without reading are absent from the result.
     */
    public Map<Instant, StoredReading> findLatestAtOrBefore(UUID meterId, List<Instant> instants) {
        if (instants.isEmpty()) {
            return Map.of();
        }
        String array = instants.stream().map(Instant::toString).collect(Collectors.joining(",", "{", "}"));
        Map<Instant, StoredReading> result = new HashMap<>();
        jdbc.query("""
                SELECT t.at, r.meter_id, r.read_at, r.index_value, r.voltage, r.signal_strength
                FROM unnest(?::timestamptz[]) AS t(at)
                CROSS JOIN LATERAL (
                    SELECT meter_id, read_at, index_value, voltage, signal_strength
                    FROM reading WHERE meter_id = ? AND read_at <= t.at
                    ORDER BY read_at DESC LIMIT 1) r""",
                (RowCallbackHandler) rs -> result.put(rs.getTimestamp("at").toInstant(), map(rs, 0)),
                array, meterId);
        return result;
    }

    /** First and last index of each non-empty time bucket in [from, to), buckets aligned on the local calendar. */
    public List<BucketIndexes> bucketIndexes(UUID meterId, Instant from, Instant to, String bucketWidth, ZoneId zone) {
        return jdbc.query("""
                SELECT time_bucket(?::interval, read_at, ?) AS bucket,
                       first(index_value, read_at) AS first_index,
                       last(index_value, read_at) AS last_index
                FROM reading
                WHERE meter_id = ? AND read_at >= ? AND read_at < ?
                GROUP BY bucket
                ORDER BY bucket""",
                (rs, n) -> new BucketIndexes(rs.getTimestamp("bucket").toInstant(),
                        rs.getBigDecimal("first_index"), rs.getBigDecimal("last_index")),
                bucketWidth, zone.getId(), meterId, Timestamp.from(from), Timestamp.from(to));
    }

    /**
     * Consumption of all meters per bucket and energy type in [from, to), with the same rules as one
     * meter's consumption (ConsumptionCalculator): each bucket's last index minus the previous reference
     * (last index of the meter's previous non-empty bucket, else its last reading before `from`, else
     * the bucket's first index), never negative. Empty buckets are absent.
     */
    public List<FleetBucket> fleetConsumption(Instant from, Instant to, String bucketWidth, ZoneId zone) {
        return jdbc.query("""
                WITH buckets AS (
                    SELECT meter_id, time_bucket(?::interval, read_at, ?) AS bucket,
                           first(index_value, read_at) AS first_index,
                           last(index_value, read_at) AS last_index
                    FROM reading
                    WHERE read_at >= ? AND read_at < ?
                    GROUP BY meter_id, bucket
                ), consumed AS (
                    SELECT b.meter_id, b.bucket,
                           GREATEST(b.last_index - COALESCE(
                               lag(b.last_index) OVER (PARTITION BY b.meter_id ORDER BY b.bucket),
                               (SELECT r.index_value FROM reading r
                                WHERE r.meter_id = b.meter_id AND r.read_at < ?
                                ORDER BY r.read_at DESC LIMIT 1),
                               b.first_index), 0) AS value
                    FROM buckets b
                )
                SELECT c.bucket, m.type, sum(c.value) AS value
                FROM consumed c JOIN meter m ON m.id = c.meter_id
                GROUP BY c.bucket, m.type
                ORDER BY c.bucket""",
                (rs, n) -> new FleetBucket(rs.getTimestamp("bucket").toInstant(),
                        MeterType.valueOf(rs.getString("type")), rs.getBigDecimal("value")),
                bucketWidth, zone.getId(), Timestamp.from(from), Timestamp.from(to), Timestamp.from(from));
    }

    /** Readings in [from, to), oldest first, streamed row by row (CSV export of long periods). */
    public void forEachReading(UUID meterId, Instant from, Instant to, RowCallbackHandler handler) {
        jdbc.query("""
                SELECT meter_id, read_at, index_value, voltage, signal_strength
                FROM reading WHERE meter_id = ? AND read_at >= ? AND read_at < ? ORDER BY read_at""",
                handler, meterId, Timestamp.from(from), Timestamp.from(to));
    }

    public static StoredReading map(ResultSet rs, int rowNum) throws SQLException {
        return new StoredReading(
                rs.getObject("meter_id", UUID.class),
                rs.getTimestamp("read_at").toInstant(),
                rs.getBigDecimal("index_value"),
                rs.getBigDecimal("voltage"),
                rs.getInt("signal_strength"));
    }

    public record BucketIndexes(Instant bucket, BigDecimal firstIndex, BigDecimal lastIndex) {
    }

    public record FleetBucket(Instant bucket, MeterType type, BigDecimal value) {
    }
}
