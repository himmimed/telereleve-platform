package ma.ceac.telereleve.reading;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Tracks how far behind real time ingestion is, from the last message processed.
 *
 * Why: after a backend restart the broker delivers the readings it queued, and the simulator may
 * publish days of history at startup. While that backlog drains, every meter's last_reading_at is
 * temporarily old, and the silent-meter rule (A3) would flag the whole park. The scheduler asks
 * {@link #isCatchingUp()} and waits for the backlog to be processed (docs/decisions.md, D25).
 */
@Component
public class IngestionMonitor {

    /** A reading processed with more delay than this is backlog, not live traffic. */
    static final Duration BACKLOG_LAG = Duration.ofMinutes(10);
    /** Only recent activity counts: an idle backend is not "catching up". */
    static final Duration RECENT = Duration.ofMinutes(2);

    private record Last(Instant processedAt, Duration lag) {
    }

    private final Clock clock;
    private final AtomicReference<Last> last = new AtomicReference<>();

    public IngestionMonitor(Clock clock) {
        this.clock = clock;
    }

    /** Called for every valid reading processed (stored or duplicate). */
    public void processed(Instant readingTimestamp) {
        Instant now = clock.instant();
        last.set(new Last(now, Duration.between(readingTimestamp, now)));
    }

    public boolean isCatchingUp() {
        Last current = last.get();
        return current != null
                && current.processedAt().isAfter(clock.instant().minus(RECENT))
                && current.lag().compareTo(BACKLOG_LAG) > 0;
    }
}
