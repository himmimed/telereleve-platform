package ma.ceac.telereleve.reading;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterStatus;

/**
 * Acceptance rules of an incoming reading (specification, "Ingestion rules"). Pure function, no I/O:
 * the caller looks the meter up and passes the current time, which keeps every rule unit-testable.
 */
public final class ReadingValidator {

    /** Meter clocks drift: a timestamp up to 5 minutes ahead of the server is still accepted. */
    public static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);

    private ReadingValidator() {
    }

    public sealed interface Verdict permits Accepted, Rejected {
    }

    public record Accepted(Meter meter) implements Verdict {
    }

    public record Rejected(String reason) implements Verdict {
    }

    /**
     * @param topicSerial serial taken from the topic ceac/meters/{serial}/readings
     * @param meter       the meter registered under the payload serial, if any
     */
    public static Verdict validate(String topicSerial, ReadingPayload payload, Optional<Meter> meter, Instant now) {
        if (payload.serial() == null || payload.serial().isBlank()) {
            return new Rejected("missing serial");
        }
        if (!payload.serial().equals(topicSerial)) {
            // A meter may only publish on its own topic.
            return new Rejected("serial does not match the topic");
        }
        if (payload.timestamp() == null) {
            return new Rejected("missing timestamp");
        }
        if (payload.index() == null) {
            return new Rejected("missing index");
        }
        if (payload.signalStrength() == null) {
            return new Rejected("missing signalStrength");
        }
        if (meter.isEmpty()) {
            return new Rejected("unknown serial");
        }
        Meter known = meter.get();
        if (known.getStatus() != MeterStatus.ACTIVE) {
            return new Rejected("meter is not active");
        }
        if (payload.type() != null && !payload.type().equals(known.getType().name())) {
            return new Rejected("type %s does not match the registered %s meter".formatted(payload.type(), known.getType()));
        }
        if (payload.timestamp().isAfter(now.plus(FUTURE_TOLERANCE))) {
            return new Rejected("timestamp more than 5 minutes in the future");
        }
        if (payload.index().signum() < 0) {
            return new Rejected("negative index");
        }
        return new Accepted(known);
    }
}
