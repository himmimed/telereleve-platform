package ma.ceac.telereleve.simulator;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of the smart meter simulator.
 * 1. reads its configuration from environment variables;
 * 2. fetches the active meters from the backend (waiting for it if needed);
 * 3. publishes readings over MQTT until stopped (Ctrl+C or docker stop).
 */
public final class SimulatorApplication {

    private static final Logger log = LoggerFactory.getLogger(SimulatorApplication.class);

    private SimulatorApplication() {
    }

    public static void main(String[] args) throws Exception {
        SimulatorConfig config = SimulatorConfig.fromEnvironment(System.getenv());
        log.info("Starting with {}", config);

        List<SimulatedMeter> meters = new MeterRegistryClient(config.backendUrl())
                .fetchActiveMetersWithRetry(Duration.ofMinutes(5));
        if (config.maxMeters() > 0 && meters.size() > config.maxMeters()) {
            meters = meters.subList(0, config.maxMeters());
        }
        if (meters.isEmpty()) {
            log.warn("No active meter in the backend: nothing to simulate");
            return;
        }

        MeterSimulator simulator = new MeterSimulator(config, meters);
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Stopping");
            simulator.close();
            stopped.countDown();
        }));
        simulator.start();
        stopped.await();
    }
}
