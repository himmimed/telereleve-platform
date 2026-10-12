package ma.ceac.telereleve.reading;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    public long countByMeter(UUID meterId) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM reading WHERE meter_id = ?", Long.class, meterId);
        return count == null ? 0 : count;
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
}
