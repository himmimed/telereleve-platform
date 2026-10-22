package ma.ceac.telereleve.simulator;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of the smart meter simulator.
 * 1. reads its configuration from environment variables;
 * 2. fetches the active meters from the backend (waiting for it if needed);
 * 3. optionally publishes the past days of history (SIM_BACKFILL_DAYS);
 * 4. publishes live readings over MQTT until stopped, with the injected anomalies.
 */
public final class SimulatorApplication {

    private static final Logger log = LoggerFactory.getLogger(SimulatorApplication.class);

    private SimulatorApplication() {
    }

    public static void main(String[] args) throws Exception {
        SimulatorConfig config = SimulatorConfig.fromEnvironment(System.getenv());
        log.info("Starting with {}", config);

        List<SimulatedMeter> meters = new MeterRegistryClient(config.backendUrl(), config.backendUsername(), config.backendPassword())
                .fetchActiveMetersWithRetry(Duration.ofMinutes(5));
        if (config.maxMeters() > 0 && meters.size() > config.maxMeters()) {
            meters = meters.subList(0, config.maxMeters());
        }
        if (meters.isEmpty()) {
            log.warn("No active meter in the backend: nothing to simulate");
            return;
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Map<String, InjectedAnomaly> anomalies = AnomalyPlan.build(meters, config.anomalies(),
                config.anomalyPercent(), AnomalyAnchor.resolve(config, now));

        MeterSimulator simulator = new MeterSimulator(config, meters, anomalies);
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Stopping");
            simulator.close();
            stopped.countDown();
        }));
        simulator.connect();
        simulator.backfill(config.backfillDays(), now);
        simulator.start();
        stopped.await();
    }
}
