package ma.ceac.telereleve.reading;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class IngestionMonitorTest {

    private static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");

    private static IngestionMonitor monitorAt(Instant now) {
        return new IngestionMonitor(Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void isNotCatchingUpBeforeAnyReading() {
        assertThat(monitorAt(NOW).isCatchingUp()).isFalse();
    }

    @Test
    void liveReadingsAreNotABacklog() {
        IngestionMonitor monitor = monitorAt(NOW);
        monitor.processed(NOW.minusSeconds(2));

        assertThat(monitor.isCatchingUp()).isFalse();
    }

    @Test
    void oldReadingsBeingProcessedMeanABacklog() {
        IngestionMonitor monitor = monitorAt(NOW);
        monitor.processed(NOW.minus(Duration.ofDays(3)));

        assertThat(monitor.isCatchingUp()).isTrue();
    }

    @Test
    void aBacklogProcessedLongAgoNoLongerCounts() {
        Instant earlier = NOW.minus(Duration.ofMinutes(5));
        IngestionMonitor processedEarlier = monitorAt(earlier);
        processedEarlier.processed(earlier.minus(Duration.ofHours(1)));

        // Same state, read 5 minutes later: the last message processed is older than 2 minutes.
        assertThat(processedEarlier.isCatchingUp()).isTrue();
        IngestionMonitor later = new IngestionMonitor(Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(later.isCatchingUp()).isFalse();
    }
}
