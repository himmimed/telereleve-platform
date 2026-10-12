package ma.ceac.telereleve.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import ma.ceac.telereleve.TestcontainersConfiguration;
import ma.ceac.telereleve.anomaly.AnomalyEngine;
import ma.ceac.telereleve.anomaly.RuleConfigService;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterRepository;
import ma.ceac.telereleve.meter.MeterStatus;
import ma.ceac.telereleve.meter.MeterType;
import ma.ceac.telereleve.reading.ReadingRepository;
import ma.ceac.telereleve.reading.StoredReading;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/** Alert lifecycle on a real database: creation, no duplicates, auto-resolution, manual changes, API. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AlertLifecycleIT {

    private static final Instant T0 = Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(Duration.ofHours(2));

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private MeterRepository meters;
    @Autowired
    private ReadingRepository readings;
    @Autowired
    private AnomalyEngine engine;
    @Autowired
    private RuleConfigService ruleConfigs;
    @Autowired
    private JdbcTemplate jdbc;

    private Meter meter;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE alert, reading, meter, alert_rule_config CASCADE");
        meter = meters.save(new Meter("CEAC-E-000100", MeterType.ELECTRICITY, "Foyer Tazi", "Fès", 34, -5, 15, MeterStatus.ACTIVE));
        // PATCH needs an HTTP client that supports it (the JDK one does).
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    /** Same path as ingestion: store, move last_reading_at, run the engine. */
    private void read(int minute, String index, String voltage) {
        StoredReading reading = new StoredReading(meter.getId(), T0.plus(Duration.ofMinutes(minute)),
                new BigDecimal(index), voltage == null ? null : new BigDecimal(voltage), -70);
        readings.insertIfAbsent(reading);
        meters.advanceLastReadingAt(meter.getId(), reading.readAt());
        engine.onReading(meters.findById(meter.getId()).orElseThrow(), reading);
    }

    private int count(String ruleCode, String condition) {
        return jdbc.queryForObject("SELECT count(*) FROM alert WHERE meter_id = ? AND rule_code = ? AND " + condition,
                Integer.class, meter.getId(), ruleCode);
    }

    private ResponseEntity<JsonNode> patch(UUID alertId, String status) {
        return http.exchange("/api/alerts/" + alertId, HttpMethod.PATCH, new HttpEntity<>(Map.of("status", status)), JsonNode.class);
    }

    private UUID openAlertId(String ruleCode) {
        return jdbc.queryForObject("SELECT id FROM alert WHERE meter_id = ? AND rule_code = ? AND status <> 'RESOLVED'",
                UUID.class, meter.getId(), ruleCode);
    }

    @Test
    void aDecreasingIndexOpensOneCriticalAlertThatStaysOpen() {
        read(0, "100", "230");
        read(1, "101", "230");
        read(2, "99", "230");    // decrease: A4
        read(3, "98", "230");    // decreases again: same alert, no duplicate
        read(4, "105", "230");   // back to normal: A4 is never resolved automatically

        assertThat(count("A4", "true")).isEqualTo(1);
        assertThat(count("A4", "status = 'NEW'")).isEqualTo(1);
        String message = jdbc.queryForObject("SELECT message FROM alert WHERE rule_code = 'A4'", String.class);
        assertThat(message).contains("101").contains("99").contains("Fraude possible");
    }

    @Test
    void aVoltageAlertIsResolvedAutomaticallyAndCanOpenAgain() {
        read(0, "100", "260");   // A5 opens
        read(1, "100.1", "261"); // still out of range: no duplicate
        assertThat(count("A5", "status = 'NEW'")).isEqualTo(1);

        read(2, "100.2", "230"); // normal: resolved
        assertThat(count("A5", "status = 'RESOLVED' AND resolved_at IS NOT NULL")).isEqualTo(1);

        read(3, "100.3", "200"); // new anomaly: a new alert
        assertThat(count("A5", "true")).isEqualTo(2);
        assertThat(count("A5", "status <> 'RESOLVED'")).isEqualTo(1);
    }

    @Test
    void aSilentMeterIsFlaggedByTheScheduleAndClearedWhenItReportsAgain() {
        read(0, "100", "230");
        Instant later = T0.plus(Duration.ofMinutes(50)); // > 3 x 15 min without reading
        engine.onSchedule(meters.findById(meter.getId()).orElseThrow(), later, ruleConfigs.all());
        engine.onSchedule(meters.findById(meter.getId()).orElseThrow(), later.plusSeconds(300), ruleConfigs.all());
        assertThat(count("A3", "status = 'NEW' AND severity = 'HIGH'")).isEqualTo(1);

        read(55, "101", "230");
        engine.onSchedule(meters.findById(meter.getId()).orElseThrow(), T0.plus(Duration.ofMinutes(56)), ruleConfigs.all());
        assertThat(count("A3", "status = 'RESOLVED'")).isEqualTo(1);
    }

    @Test
    void aDisabledRuleRaisesNothing() {
        ResponseEntity<JsonNode> disabled = http.exchange("/api/rules/A5", HttpMethod.PUT,
                new HttpEntity<>(Map.of("enabled", false, "threshold", 10)), JsonNode.class);
        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.OK);

        read(0, "100", "300");

        assertThat(count("A5", "true")).isZero();
        JsonNode rules = http.getForObject("/api/rules", JsonNode.class);
        assertThat(rules).hasSize(5);
        assertThat(rules.get(4).get("ruleCode").asText()).isEqualTo("A5");
        assertThat(rules.get(4).get("enabled").asBoolean()).isFalse();
    }

    @Test
    void operatorsChangeTheStatusThroughTheApi() {
        read(0, "100", "230");
        read(1, "99", "260");    // A4 (critical) and A5 (medium)
        UUID a4 = openAlertId("A4");

        // Open alerts first, most severe first.
        JsonNode list = http.getForObject("/api/alerts?status=NEW", JsonNode.class);
        assertThat(list.get("totalElements").asInt()).isEqualTo(2);
        assertThat(list.get("content").get(0).get("ruleCode").asText()).isEqualTo("A4");
        assertThat(list.get("content").get(0).get("meterSerial").asText()).isEqualTo("CEAC-E-000100");

        assertThat(patch(a4, "IN_PROGRESS").getBody().get("status").asText()).isEqualTo("IN_PROGRESS");
        ResponseEntity<JsonNode> backToNew = patch(a4, "NEW");
        assertThat(backToNew.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(backToNew.getBody().get("detail").asText()).contains("Impossible");

        ResponseEntity<JsonNode> resolved = patch(a4, "RESOLVED");
        assertThat(resolved.getBody().get("resolvedAt").isNull()).isFalse();

        // A new decrease opens a new A4; reopening the old one would make two open: refused.
        read(2, "98", "230");
        ResponseEntity<JsonNode> reopen = patch(a4, "NEW");
        assertThat(reopen.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        JsonNode details = http.getForObject("/api/meters/" + meter.getId(), JsonNode.class);
        assertThat(details.get("openAlerts")).hasSize(1);
        assertThat(details.get("openAlerts").get(0).get("ruleCode").asText()).isEqualTo("A4");
    }

    @Test
    void filtersAlertsByRuleSeverityAndMeter() {
        read(0, "100", "260");

        assertThat(http.getForObject("/api/alerts?ruleCode=A5", JsonNode.class).get("totalElements").asInt()).isEqualTo(1);
        assertThat(http.getForObject("/api/alerts?severity=CRITICAL", JsonNode.class).get("totalElements").asInt()).isZero();
        assertThat(http.getForObject("/api/alerts?meterId=" + UUID.randomUUID(), JsonNode.class).get("totalElements").asInt()).isZero();
        assertThat(http.getForEntity("/api/alerts?ruleCode=A9", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
