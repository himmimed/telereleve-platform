package ma.ceac.telereleve.simulator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides the instant at which the injected anomalies start, and keeps it the same across restarts.
 *
 * <p>An anomalous meter is a pure function of (meter, anomaly, time), so the readings it publishes for a given
 * moment are always the same, provided the anomaly starts at the same instant. A start computed from the
 * simulator's own start time ({@code SIM_ANOMALY_DELAY_MINUTES}) moves at every restart; replaying the history
 * on an existing database then contradicts the stored readings and raises false alerts. Hence:
 *
 * <ol>
 *   <li>{@code SIM_ANOMALY_START}, when set, is used as is;
 *   <li>otherwise the start is computed once from the delay, written to {@code SIM_STATE_DIR} and read back at
 *       the next start;
 *   <li>with neither, the start is computed from the delay every time (fine for a throwaway database).
 * </ol>
 */
final class AnomalyAnchor {

    static final String FILE_NAME = "anomaly-start.txt";

    private static final Logger log = LoggerFactory.getLogger(AnomalyAnchor.class);

    private AnomalyAnchor() {
    }

    static Instant resolve(SimulatorConfig config, Instant now) {
        if (config.anomalyStart() != null) {
            log.info("Anomalies start at {} (SIM_ANOMALY_START)", config.anomalyStart());
            return config.anomalyStart();
        }
        Instant computed = now.plus(config.anomalyDelay());
        Path stateDir = config.stateDir();
        if (stateDir == null) {
            log.info("Anomalies start at {} (relative to this start; set SIM_STATE_DIR or SIM_ANOMALY_START to keep it across restarts)", computed);
            return computed;
        }
        Path file = stateDir.resolve(FILE_NAME);
        try {
            if (Files.exists(file)) {
                try {
                    Instant saved = Instant.parse(Files.readString(file).trim());
                    log.info("Anomalies start at {} (kept from the first start, {})", saved, file);
                    return saved;
                } catch (DateTimeParseException e) {
                    log.warn("{} is unreadable, a new start is saved", file);
                }
            }
            Files.createDirectories(stateDir);
            Files.writeString(file, computed + System.lineSeparator());
            log.info("Anomalies start at {} (saved in {} for the next starts)", computed, file);
        } catch (IOException e) {
            log.warn("Cannot save the anomaly start in {} ({}): it will not survive a restart", file, e.toString());
        }
        return computed;
    }
}
