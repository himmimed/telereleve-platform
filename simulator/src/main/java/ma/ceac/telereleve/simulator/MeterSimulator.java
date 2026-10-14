package ma.ceac.telereleve.simulator;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes one reading per meter and per interval, QoS 1, on ceac/meters/{serial}/readings.
 * Each meter has its own offset within the interval, so 1000 meters do not all publish in the same
 * second (real fleets spread their transmissions the same way).
 * Injected anomalies (AnomalyPlan) alter what some meters publish, or silence them.
 */
public class MeterSimulator implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MeterSimulator.class);
    static final int QOS = 1;
    /** Backfilled history: one reading every 15 minutes, the meters' real reading interval. */
    static final Duration BACKFILL_STEP = Duration.ofMinutes(15);
    /** Backfill pace: the backend evaluates the anomaly rules for each reading, so do not flood it. */
    private static final int BACKFILL_PER_SECOND = 400;

    private final SimulatorConfig config;
    private final List<SimulatedMeter> meters;
    private final Map<String, InjectedAnomaly> anomalies;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private MqttClient client;

    public MeterSimulator(SimulatorConfig config, List<SimulatedMeter> meters, Map<String, InjectedAnomaly> anomalies) {
        this.config = config;
        this.meters = meters;
        this.anomalies = anomalies;
    }

    public void connect() throws MqttException {
        client = new MqttClient(config.mqttUrl(), config.clientId(), new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(config.mqttUsername());
        options.setPassword(config.mqttPassword().toCharArray());
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setMaxInflight(Math.max(100, meters.size()));
        client.connect(options);
        log.info("Connected to {} as {}", config.mqttUrl(), config.clientId());
    }

    /**
     * Publishes the readings of the past `days` days, every 15 minutes, oldest first (all meters for one
     * time slot, then the next slot), so the backend sees each meter's history in order.
     */
    public void backfill(int days, Instant until) throws InterruptedException {
        if (days <= 0) {
            return;
        }
        Instant start = until.minus(Duration.ofDays(days)).truncatedTo(ChronoUnit.HOURS);
        long count = 0;
        long pace = System.nanoTime();
        for (Instant t = start; t.isBefore(until); t = t.plus(BACKFILL_STEP)) {
            for (SimulatedMeter meter : meters) {
                publish(meter, t);
                if (++count % BACKFILL_PER_SECOND == 0) {
                    long wait = 1_000_000_000L - (System.nanoTime() - pace);
                    if (wait > 0) {
                        TimeUnit.NANOSECONDS.sleep(wait);
                    }
                    pace = System.nanoTime();
                }
            }
        }
        log.info("Backfilled {} readings ({} days, {} meters)", count, days, meters.size());
    }

    public void start() {
        long intervalMs = config.interval().toMillis();
        for (SimulatedMeter meter : meters) {
            scheduler.scheduleAtFixedRate(() -> publish(meter, Instant.now().truncatedTo(ChronoUnit.SECONDS)),
                    offsetOf(meter, intervalMs), intervalMs, TimeUnit.MILLISECONDS);
        }
        scheduler.scheduleAtFixedRate(this::report, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("Simulating {} meters, one reading every {}", meters.size(), config.interval());
        anomalies.forEach((serial, anomaly) ->
                log.info("Anomaly {} injected on {} from {}", anomaly.type(), serial, anomaly.start()));
    }

    /** Stable offset of a meter within the interval, derived from its serial. */
    static long offsetOf(SimulatedMeter meter, long intervalMs) {
        return Math.floorMod(meter.serial().hashCode() * 2_654_435_761L, intervalMs);
    }

    private void publish(SimulatedMeter meter, Instant t) {
        InjectedAnomaly anomaly = anomalies.get(meter.serial());
        if (!MeterBehaviour.publishes(anomaly, t)) {
            return;
        }
        try {
            MqttMessage message = new MqttMessage(ReadingPayload.of(meter, anomaly, t).toJson());
            message.setQos(QOS);
            client.publish(meter.topic(), message);
            published.incrementAndGet();
        } catch (MqttException | RuntimeException e) {
            // Never let one failure cancel the meter's schedule: the next reading is retried as usual.
            failed.incrementAndGet();
            log.warn("Reading of {} not published: {}", meter.serial(), e.getMessage());
        }
    }

    private void report() {
        log.info("Published {} readings since start ({} failed)", published.get(), failed.get());
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
            // Final count, compared with the readings stored by the backend in the load test.
            log.info("Stopped after publishing {} readings ({} failed)", published.get(), failed.get());
            if (client != null && client.isConnected()) {
                client.disconnect(Duration.ofSeconds(5).toMillis());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (MqttException e) {
            log.warn("Clean disconnect failed: {}", e.getMessage());
        }
    }
}
