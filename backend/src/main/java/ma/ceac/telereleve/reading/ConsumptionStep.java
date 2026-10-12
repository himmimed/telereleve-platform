package ma.ceac.telereleve.reading;

import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Width of consumption buckets. Written in lower case in URLs and JSON (step=hour|day|month). */
public enum ConsumptionStep {

    HOUR("1 hour", Duration.ofHours(24), Duration.ofDays(31)),
    DAY("1 day", Duration.ofDays(7), Duration.ofDays(400)),
    MONTH("1 month", Duration.ofDays(365), Duration.ofDays(3660));

    private final String sqlInterval;
    private final Duration defaultRange;
    private final Duration maxRange;

    ConsumptionStep(String sqlInterval, Duration defaultRange, Duration maxRange) {
        this.sqlInterval = sqlInterval;
        this.defaultRange = defaultRange;
        this.maxRange = maxRange;
    }

    /** Interval literal for TimescaleDB time_bucket. */
    public String sqlInterval() {
        return sqlInterval;
    }

    public Duration defaultRange() {
        return defaultRange;
    }

    /** Longest period accepted, which bounds the number of points (hour: 744, day: 400, month: 120). */
    public Duration maxRange() {
        return maxRange;
    }

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ConsumptionStep fromCode(String code) {
        return valueOf(code.trim().toUpperCase(Locale.ROOT));
    }

    /** Start of the bucket containing t, on the local calendar (same alignment as time_bucket with a time zone). */
    public Instant align(Instant t, ZoneId zone) {
        ZonedDateTime local = t.atZone(zone);
        return switch (this) {
            case HOUR -> local.truncatedTo(ChronoUnit.HOURS).toInstant();
            case DAY -> local.toLocalDate().atStartOfDay(zone).toInstant();
            case MONTH -> LocalDate.of(local.getYear(), local.getMonth(), 1).atStartOfDay(zone).toInstant();
        };
    }

    public Instant next(Instant bucketStart, ZoneId zone) {
        ZonedDateTime local = bucketStart.atZone(zone);
        return switch (this) {
            case HOUR -> bucketStart.plus(Duration.ofHours(1));
            case DAY -> local.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
            case MONTH -> local.toLocalDate().plusMonths(1).atStartOfDay(zone).toInstant();
        };
    }
}
