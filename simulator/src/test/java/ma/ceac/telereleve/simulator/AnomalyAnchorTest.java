package ma.ceac.telereleve.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnomalyAnchorTest {

    private static final Instant FIRST_START = Instant.parse("2026-10-21T10:00:00Z");
    private static final Instant RESTART = Instant.parse("2026-10-23T08:30:00Z");

    private static SimulatorConfig config(Map<String, String> extra) {
        Map<String, String> env = new HashMap<>(Map.of("MQTT_PASSWORD", "x", "SIM_BACKEND_PASSWORD", "pw",
                "SIM_BACKFILL_DAYS", "4", "SIM_ANOMALY_DELAY_MINUTES", "-1500"));
        env.putAll(extra);
        return SimulatorConfig.fromEnvironment(env);
    }

    @Test
    void aStartDerivedFromTheDelayMovesWithEveryRestartWhenNothingIsSaved() {
        SimulatorConfig config = config(Map.of());

        assertThat(AnomalyAnchor.resolve(config, FIRST_START)).isEqualTo(FIRST_START.minusSeconds(1500 * 60L));
        assertThat(AnomalyAnchor.resolve(config, RESTART)).isEqualTo(RESTART.minusSeconds(1500 * 60L));
    }

    @Test
    void anExplicitStartIsUsedAsIsAtEveryRestart(@TempDir Path state) throws IOException {
        SimulatorConfig config = config(Map.of("SIM_ANOMALY_START", "2026-10-20T09:00:00Z", "SIM_STATE_DIR", state.toString()));
        Instant expected = Instant.parse("2026-10-20T09:00:00Z");

        assertThat(AnomalyAnchor.resolve(config, FIRST_START)).isEqualTo(expected);
        assertThat(AnomalyAnchor.resolve(config, RESTART)).isEqualTo(expected);
        // An explicit start wins over the saved one and is never written.
        assertThat(state.resolve(AnomalyAnchor.FILE_NAME)).doesNotExist();
    }

    @Test
    void theFirstStartIsSavedAndKeptAtTheNextRestart(@TempDir Path state) throws IOException {
        SimulatorConfig config = config(Map.of("SIM_STATE_DIR", state.toString()));
        Instant first = AnomalyAnchor.resolve(config, FIRST_START);

        assertThat(first).isEqualTo(FIRST_START.minusSeconds(1500 * 60L));
        assertThat(Files.readString(state.resolve(AnomalyAnchor.FILE_NAME)).trim()).isEqualTo(first.toString());
        // Two days later: same instant, so the replayed history matches what is already stored.
        assertThat(AnomalyAnchor.resolve(config, RESTART)).isEqualTo(first);
    }

    @Test
    void anUnreadableSavedStartIsReplacedRatherThanBlockingTheStart(@TempDir Path state) throws IOException {
        Files.writeString(state.resolve(AnomalyAnchor.FILE_NAME), "not a date");
        SimulatorConfig config = config(Map.of("SIM_STATE_DIR", state.toString()));

        Instant resolved = AnomalyAnchor.resolve(config, FIRST_START);

        assertThat(resolved).isEqualTo(FIRST_START.minusSeconds(1500 * 60L));
        assertThat(AnomalyAnchor.resolve(config, RESTART)).isEqualTo(resolved);
    }

    @Test
    void aStateDirectoryThatCannotBeWrittenOnlyCostsTheMemory(@TempDir Path state) throws IOException {
        // A regular file where the directory should be: createDirectories fails.
        Path blocker = Files.writeString(state.resolve("blocker"), "x");
        SimulatorConfig config = config(Map.of("SIM_STATE_DIR", blocker.resolve("sub").toString()));

        assertThat(AnomalyAnchor.resolve(config, FIRST_START)).isEqualTo(FIRST_START.minusSeconds(1500 * 60L));
    }
}
