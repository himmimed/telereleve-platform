package ma.ceac.telereleve.simulator;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
 */
public class MeterSimulator implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MeterSimulator.class);
    static final int QOS = 1;

    private final SimulatorConfig config;
    private final List<SimulatedMeter> meters;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private MqttClient client;

    public MeterSimulator(SimulatorConfig config, List<SimulatedMeter> meters) {
        this.config = config;
        this.meters = meters;
    }

    public void start() throws MqttException {
        client = new MqttClient(config.mqttUrl(), config.clientId(), new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(config.mqttUsername());
        options.setPassword(config.mqttPassword().toCharArray());
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setMaxInflight(Math.max(100, meters.size()));
        client.connect(options);
        log.info("Connected to {} as {}", config.mqttUrl(), config.clientId());

        long intervalMs = config.interval().toMillis();
        for (SimulatedMeter meter : meters) {
            scheduler.scheduleAtFixedRate(() -> publish(meter), offsetOf(meter, intervalMs), intervalMs, TimeUnit.MILLISECONDS);
        }
        scheduler.scheduleAtFixedRate(this::report, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("Simulating {} meters, one reading every {}", meters.size(), config.interval());
    }

    /** Stable offset of a meter within the interval, derived from its serial. */
    static long offsetOf(SimulatedMeter meter, long intervalMs) {
        return Math.floorMod(meter.serial().hashCode() * 2_654_435_761L, intervalMs);
    }

    private void publish(SimulatedMeter meter) {
        try {
            Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            MqttMessage message = new MqttMessage(ReadingPayload.of(meter, now).toJson());
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
