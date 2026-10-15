package ma.ceac.telereleve.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import ma.ceac.telereleve.ApiClient;
import ma.ceac.telereleve.TestcontainersConfiguration;
import ma.ceac.telereleve.alert.AlertService;
import ma.ceac.telereleve.alert.Severity;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterRepository;
import ma.ceac.telereleve.meter.MeterStatus;
import ma.ceac.telereleve.meter.MeterType;
import ma.ceac.telereleve.reading.ConsumptionService;
import ma.ceac.telereleve.reading.ConsumptionStep;
import ma.ceac.telereleve.reading.ReadingRepository;
import ma.ceac.telereleve.reading.StoredReading;
import ma.ceac.telereleve.user.AppUserRepository;
import ma.ceac.telereleve.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Map, key figures and network consumption on real data (TimescaleDB). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DashboardApiIT {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MeterRepository meters;
    @Autowired
    private ReadingRepository readings;
    @Autowired
    private AlertService alerts;
    @Autowired
    private JdbcTemplate jdbc;

    /** Signed in as OPERATOR. */
    private ApiClient http;

    @BeforeEach
    void setUp() {
        http = ApiClient.as(Role.OPERATOR, rest, users, passwordEncoder);
        jdbc.execute("TRUNCATE alert, reading, meter CASCADE");
    }

    private Meter meter(String serial, MeterType type, MeterStatus status) {
        return meters.save(new Meter(serial, type, "Foyer " + serial, "Fès", 34.03, -5.0, 15, status));
    }

    private void insert(Meter meter, Instant at, String index) {
        readings.insertIfAbsent(new StoredReading(meter.getId(), at, new BigDecimal(index), null, -70));
        meters.advanceLastReadingAt(meter.getId(), at);
    }

    private void insert(Meter meter, String at, String index) {
        insert(meter, Instant.parse(at), index);
    }

    @Test
    void networkConsumptionIsTheSumOfEveryMeterCurve() {
        // Same readings as ConsumptionApiIT: hourly 3, 0, 5, 1 (a gap in hour 11, a decrease in hour 12).
        Meter first = meter("CEAC-E-000001", MeterType.ELECTRICITY, MeterStatus.ACTIVE);
        insert(first, "2026-10-12T09:50:00Z", "100");
        insert(first, "2026-10-12T10:10:00Z", "101");
        insert(first, "2026-10-12T10:50:00Z", "103");
        insert(first, "2026-10-12T12:20:00Z", "110");
        insert(first, "2026-10-12T12:40:00Z", "108");
        insert(first, "2026-10-12T13:10:00Z", "109");
        // No reading before the period: its first index is the reference, so hour 10 is 0, hour 13 is 2.
        Meter second = meter("CEAC-E-000002", MeterType.ELECTRICITY, MeterStatus.ACTIVE);
        insert(second, "2026-10-12T10:05:00Z", "50");
        insert(second, "2026-10-12T13:30:00Z", "52");
        // Decommissioned since: its past consumption stays in the history of the network.
        Meter retired = meter("CEAC-E-000003", MeterType.ELECTRICITY, MeterStatus.INACTIVE);
        insert(retired, "2026-10-12T09:00:00Z", "1000");
        insert(retired, "2026-10-12T10:30:00Z", "1010");
        Meter water = meter("CEAC-W-000004", MeterType.WATER, MeterStatus.ACTIVE);
        insert(water, "2026-10-12T09:55:00Z", "5.000");
        insert(water, "2026-10-12T12:10:00Z", "5.250");

        String period = "from=2026-10-12T10:00:00Z&to=2026-10-12T14:00:00Z&step=hour";
        JsonNode trend = http.json("/api/dashboard/consumption-trend?" + period);

        assertThat(trend).hasSize(4);
        assertThat(trend.get(0).get("bucket").asText()).isEqualTo("2026-10-12T10:00:00Z");
        assertThat(values(trend, "electricityKwh")).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("13"), BigDecimal.ZERO, new BigDecimal("5"), new BigDecimal("3"));
        assertThat(values(trend, "waterM3")).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.25"), BigDecimal.ZERO);

        // Same rules as the per-meter endpoint: the curves add up exactly.
        BigDecimal[] sum = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (Meter m : List.of(first, second, retired)) {
            List<BigDecimal> curve = values(http.json("/api/meters/" + m.getId() + "/consumption?" + period).get("points"), "value");
            for (int i = 0; i < 4; i++) {
                sum[i] = sum[i].add(curve.get(i));
            }
        }
        assertThat(values(trend, "electricityKwh")).usingElementComparator(BigDecimal::compareTo).containsExactly(sum);
    }

    @Test
    void networkConsumptionHandlesLateReadingsAndAPeriodEndingWithinAnHour() {
        // Hour 10: 10:10 -> 100, 10:50 -> 104, then 10:05 -> 99 arrives late (replayed after an outage) and
        // becomes the first reading of the hour. Hour 11: 11:20 -> 106, and 11:45 -> 110 after the period.
        Meter meter = meter("CEAC-E-000021", MeterType.ELECTRICITY, MeterStatus.ACTIVE);
        insert(meter, "2026-10-12T10:10:00Z", "100");
        insert(meter, "2026-10-12T10:50:00Z", "104");
        insert(meter, "2026-10-12T11:20:00Z", "106");
        insert(meter, "2026-10-12T11:45:00Z", "110");
        insert(meter, "2026-10-12T10:05:00Z", "99");

        assertThat(jdbc.queryForMap("""
                SELECT first_at, first_index, last_at, last_index FROM reading_hourly
                WHERE meter_id = ? AND hour = '2026-10-12T10:00:00Z'""", meter.getId()))
                .containsEntry("first_index", new BigDecimal("99.000"))
                .containsEntry("last_index", new BigDecimal("104.000"));

        String period = "from=2026-10-12T10:00:00Z&to=2026-10-12T11:30:00Z&step=hour";
        JsonNode trend = http.json("/api/dashboard/consumption-trend?" + period);
        // Hour 10: 104 - 99 (first reading, nothing before). Hour 11 up to 11:30: 106 - 104, not 110.
        assertThat(values(trend, "electricityKwh")).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("5"), new BigDecimal("2"));
        assertThat(values(http.json("/api/meters/" + meter.getId() + "/consumption?" + period).get("points"), "value"))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("5"), new BigDecimal("2"));
    }

    @Test
    void aDuplicateReadingChangesNeitherTheReadingsNorTheHourlySummary() {
        Meter meter = meter("CEAC-E-000022", MeterType.ELECTRICITY, MeterStatus.ACTIVE);
        insert(meter, "2026-10-12T10:10:00Z", "100");

        boolean stored = readings.insertIfAbsent(new StoredReading(meter.getId(), Instant.parse("2026-10-12T10:10:00Z"),
                new BigDecimal("999"), null, -70));

        assertThat(stored).isFalse();
        assertThat(jdbc.queryForObject("SELECT last_index FROM reading_hourly WHERE meter_id = ?", BigDecimal.class,
                meter.getId())).isEqualByComparingTo("100");
    }

    @Test
    void trendRejectsInvalidPeriods() {
        assertThat(http.getForEntity("/api/dashboard/consumption-trend?step=week", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.getForEntity("/api/dashboard/consumption-trend?step=hour&from=2026-08-01T00:00:00Z"
                + "&to=2026-10-01T00:00:00Z", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void statsAndMapDescribeTheNetworkForEveryRole() {
        Instant now = Instant.now();
        Meter reporting = meter("CEAC-E-000011", MeterType.ELECTRICITY, MeterStatus.ACTIVE);
        insert(reporting, now.minus(Duration.ofDays(2)), "100");
        insert(reporting, now.minusSeconds(60), "104.5");
        Meter silent = meter("CEAC-E-000012", MeterType.ELECTRICITY, MeterStatus.ACTIVE);
        insert(silent, now.minus(Duration.ofHours(2)), "7");
        Meter newWater = meter("CEAC-W-000013", MeterType.WATER, MeterStatus.ACTIVE); // never reported, just installed
        meter("CEAC-E-000014", MeterType.ELECTRICITY, MeterStatus.INACTIVE);
        alerts.raise(reporting.getId(), "A5", Severity.MEDIUM, "Tension", now);
        alerts.raise(reporting.getId(), "A4", Severity.CRITICAL, "Index", now);
        alerts.raise(silent.getId(), "A3", Severity.HIGH, "Silence", now);
        alerts.raise(newWater.getId(), "A2", Severity.MEDIUM, "Pic", now);
        alerts.resolve(newWater.getId(), "A2", now);

        ApiClient technician = ApiClient.as(Role.TECHNICIAN, rest, users, passwordEncoder);
        JsonNode stats = technician.json("/api/dashboard/stats");

        assertThat(stats.get("activeMeters").asInt()).isEqualTo(3);
        assertThat(stats.get("silentMeters").asInt()).isEqualTo(1);
        assertThat(stats.get("openAlerts").get("CRITICAL").asInt()).isEqualTo(1);
        assertThat(stats.get("openAlerts").get("HIGH").asInt()).isEqualTo(1);
        assertThat(stats.get("openAlerts").get("MEDIUM").asInt()).isEqualTo(1);
        // 4.5 kWh between the reading of two days ago and the last one, unless the test runs in the
        // first minute of the local day (the last reading then belongs to yesterday).
        boolean lastReadingToday = now.minusSeconds(60).isAfter(ConsumptionStep.DAY.align(now, ConsumptionService.ZONE));
        assertThat(stats.get("consumptionToday").get("electricityKwh").decimalValue())
                .isEqualByComparingTo(lastReadingToday ? "4.5" : "0");
        assertThat(stats.get("consumptionToday").get("waterM3").decimalValue()).isZero();

        JsonNode map = technician.json("/api/meters/map");
        assertThat(map).hasSize(3);
        assertThat(map).extracting(m -> m.get("serial").asText() + " " + m.get("status").asText())
                .containsExactly("CEAC-E-000011 ALERT", "CEAC-E-000012 SILENT", "CEAC-W-000013 OK");
        assertThat(map.get(2).get("type").asText()).isEqualTo("WATER");
        assertThat(map.get(2).get("lat").asDouble()).isEqualTo(34.03);
        assertThat(map.get(2).get("lng").asDouble()).isEqualTo(-5.0);
    }

    private static List<BigDecimal> values(JsonNode points, String field) {
        List<BigDecimal> values = new ArrayList<>();
        points.forEach(p -> values.add(p.get(field).decimalValue()));
        return values;
    }
}
