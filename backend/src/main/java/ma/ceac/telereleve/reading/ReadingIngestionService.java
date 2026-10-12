package ma.ceac.telereleve.reading;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Optional;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterRepository;
import ma.ceac.telereleve.reading.ReadingValidator.Accepted;
import ma.ceac.telereleve.reading.ReadingValidator.Rejected;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns an MQTT message into a stored reading: parse, validate, insert idempotently, move the meter's
 * last_reading_at forward, and announce the new reading (anomaly engine, phase 3).
 * Bad messages are logged at WARN and dropped: one faulty meter must never stop ingestion for the others.
 */
@Service
public class ReadingIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ReadingIngestionService.class);
    private static final String TOPIC_PREFIX = "ceac/meters/";
    private static final String TOPIC_SUFFIX = "/readings";

    public enum Outcome { STORED, DUPLICATE, REJECTED }

    private final MeterRepository meters;
    private final ReadingRepository readings;
    private final ApplicationEventPublisher events;
    private final ObjectMapper json;
    private final Clock clock;

    public ReadingIngestionService(MeterRepository meters, ReadingRepository readings,
                                   ApplicationEventPublisher events, ObjectMapper json, Clock clock) {
        this.meters = meters;
        this.readings = readings;
        this.events = events;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public Outcome ingest(String topic, String message) {
        String topicSerial = serialFromTopic(topic);
        ReadingPayload payload;
        try {
            payload = json.readValue(message, ReadingPayload.class);
        } catch (JsonProcessingException e) {
            return reject(topic, "invalid JSON");
        }
        if (payload == null) {
            return reject(topic, "empty message");
        }

        var verdict = ReadingValidator.validate(topicSerial, payload,
                payload.serial() == null ? Optional.empty() : meters.findBySerial(payload.serial()),
                clock.instant());
        if (verdict instanceof Rejected rejected) {
            return reject(topic, rejected.reason());
        }
        Meter meter = ((Accepted) verdict).meter();

        StoredReading reading = new StoredReading(meter.getId(), payload.timestamp(), payload.index(),
                payload.voltage(), payload.signalStrength());
        if (!readings.insertIfAbsent(reading)) {
            // Re-published message (QoS 1 redelivery, meter retry): already stored, nothing to do.
            log.debug("Duplicate reading ignored: {} at {}", meter.getSerial(), payload.timestamp());
            return Outcome.DUPLICATE;
        }
        meters.advanceLastReadingAt(meter.getId(), payload.timestamp());
        events.publishEvent(new ReadingSavedEvent(reading, meter.getSerial()));
        return Outcome.STORED;
    }

    private static Outcome reject(String topic, String reason) {
        log.warn("Reading dropped (topic={}): {}", sanitize(topic), reason);
        return Outcome.REJECTED;
    }

    static String serialFromTopic(String topic) {
        if (topic != null && topic.startsWith(TOPIC_PREFIX) && topic.endsWith(TOPIC_SUFFIX)) {
            return topic.substring(TOPIC_PREFIX.length(), topic.length() - TOPIC_SUFFIX.length());
        }
        return "";
    }

    /** Topics come from outside: keep log lines on one line and of bounded length. */
    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String clean = text.replaceAll("[\\r\\n\\t]", "_");
        return clean.length() > 100 ? clean.substring(0, 100) + "..." : clean;
    }
}
