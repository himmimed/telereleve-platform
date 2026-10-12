package ma.ceac.telereleve.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import ma.ceac.telereleve.TestcontainersConfiguration;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterRepository;
import ma.ceac.telereleve.meter.MeterStatus;
import ma.ceac.telereleve.meter.MeterType;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;

/**
 * End to end through a real Mosquitto broker and TimescaleDB: what a meter publishes ends up in the
 * reading hypertable, exactly once, and bad messages are dropped without stopping ingestion.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MqttIngestionIT {

    private static final Instant T0 = Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(Duration.ofHours(1));

    @Autowired
    private GenericContainer<?> mosquittoContainer;
    @Autowired
    private MeterRepository meters;
    @Autowired
    private ReadingRepository readings;
    @Autowired
    private JdbcTemplate jdbc;

    private MqttClient publisher;
    private Meter meter;
    private Meter sentinel;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE alert, reading, meter, alert_rule_config CASCADE");
        meter = meters.save(new Meter("CEAC-E-000777", MeterType.ELECTRICITY, "Foyer Test", "Fès", 34, -5, 15, MeterStatus.ACTIVE));
        sentinel = meters.save(new Meter("CEAC-W-000778", MeterType.WATER, "Témoin", "Fès", 34, -5, 15, MeterStatus.ACTIVE));
        meters.save(new Meter("CEAC-E-000779", MeterType.ELECTRICITY, "Inactif", "Fès", 34, -5, 15, MeterStatus.INACTIVE));

        publisher = new MqttClient(TestcontainersConfiguration.brokerUrl(mosquittoContainer), "it-" + UUID.randomUUID(),
                new MemoryPersistence());
        publisher.connect(new MqttConnectOptions());
    }

    @AfterEach
    void tearDown() throws Exception {
        publisher.disconnect();
        publisher.close();
    }

    private void publish(String serial, String json) throws Exception {
        publisher.publish("ceac/meters/" + serial + "/readings", json.getBytes(), 1, false);
    }

    private static String electricity(String serial, Instant at, String index) {
        return """
               {"serial":"%s","type":"ELECTRICITY","timestamp":"%s","index":%s,"unit":"kWh","voltage":229.8,"signalStrength":-71}"""
                .formatted(serial, at, index);
    }

    /** A valid water reading on another meter; once it is stored, every earlier message has been processed. */
    private void publishSentinelAndWait(Instant at) throws Exception {
        publish(sentinel.getSerial(), """
                {"serial":"%s","type":"WATER","timestamp":"%s","index":12.5,"unit":"m3","signalStrength":-66}"""
                .formatted(sentinel.getSerial(), at));
        await().atMost(Duration.ofSeconds(20)).until(() -> readings.findLatest(sentinel.getId())
                .map(r -> r.readAt().equals(at)).orElse(false));
    }

    @Test
    void storesTenReadingsOnceEvenWhenRepublished() throws Exception {
        // Ten readings one minute apart; the 6th has a lower index (possible fraud, rule A4):
        // it is still a valid reading and must be stored, and it must open exactly one A4 alert.
        String[] indexes = {"100.10", "100.25", "100.41", "100.60", "100.72", "99.80", "100.95", "101.10", "101.30", "101.42"};
        for (int i = 0; i < indexes.length; i++) {
            publish(meter.getSerial(), electricity(meter.getSerial(), T0.plus(Duration.ofMinutes(i)), indexes[i]));
        }
        await().atMost(Duration.ofSeconds(20)).until(() -> readings.countByMeter(meter.getId()) == 10);

        // Same ten messages again (QoS 1 redelivery, meter retry...).
        for (int i = 0; i < indexes.length; i++) {
            publish(meter.getSerial(), electricity(meter.getSerial(), T0.plus(Duration.ofMinutes(i)), indexes[i]));
        }
        publishSentinelAndWait(T0);

        assertThat(readings.countByMeter(meter.getId())).isEqualTo(10);
        assertThat(meters.findById(meter.getId()).orElseThrow().getLastReadingAt())
                .isEqualTo(T0.plus(Duration.ofMinutes(9)));
        assertThat(readings.findLatest(meter.getId()).orElseThrow().index()).isEqualByComparingTo("101.42");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM alert WHERE meter_id = ? AND rule_code = 'A4' AND status IN ('NEW', 'IN_PROGRESS')",
                Long.class, meter.getId())).isEqualTo(1);
    }

    @Test
    void dropsInvalidMessagesAndKeepsIngesting() throws Exception {
        String serial = meter.getSerial();
        publish(serial, "{not json");
        publish(serial, electricity(serial, Instant.now().plus(Duration.ofMinutes(10)), "100"));   // future
        publish(serial, electricity(serial, T0, "-5"));                                              // negative index
        publish("CEAC-E-000999", electricity("CEAC-E-000999", T0, "100"));                           // unknown meter
        publish("CEAC-E-000779", electricity("CEAC-E-000779", T0, "100"));                           // inactive meter
        publish(serial, electricity("CEAC-E-000779", T0, "100"));                                    // serial != topic
        publish(serial, """
                {"serial":"%s","type":"WATER","timestamp":"%s","index":1,"unit":"m3","signalStrength":-60}"""
                .formatted(serial, T0));                                                             // wrong type

        publishSentinelAndWait(T0.plus(Duration.ofMinutes(1)));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM reading", Long.class)).isEqualTo(1);
        assertThat(meters.findById(meter.getId()).orElseThrow().getLastReadingAt()).isNull();
    }

    @Test
    void aLateReadingDoesNotMoveLastReadingAtBackwards() throws Exception {
        String serial = meter.getSerial();
        publish(serial, electricity(serial, T0.plus(Duration.ofMinutes(30)), "120"));
        publish(serial, electricity(serial, T0, "100"));   // older reading, received after
        publishSentinelAndWait(T0.plus(Duration.ofMinutes(2)));

        assertThat(readings.countByMeter(meter.getId())).isEqualTo(2);
        assertThat(meters.findById(meter.getId()).orElseThrow().getLastReadingAt())
                .isEqualTo(T0.plus(Duration.ofMinutes(30)));
    }
}
