package ma.ceac.telereleve.reading;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import ma.ceac.telereleve.ApiClient;
import ma.ceac.telereleve.TestcontainersConfiguration;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterRepository;
import ma.ceac.telereleve.meter.MeterStatus;
import ma.ceac.telereleve.meter.MeterType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ma.ceac.telereleve.user.AppUserRepository;
import ma.ceac.telereleve.user.Role;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Consumption aggregation (TimescaleDB time_bucket) and CSV export on real data. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ConsumptionApiIT {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Signed in as OPERATOR (real login, Bearer token). */
    private ApiClient http;
    @Autowired
    private MeterRepository meters;
    @Autowired
    private ReadingRepository readings;
    @Autowired
    private JdbcTemplate jdbc;

    private Meter meter;

    @BeforeEach
    void setUp() {
        http = ApiClient.as(Role.OPERATOR, rest, users, passwordEncoder);
        jdbc.execute("TRUNCATE alert, reading, meter CASCADE");
        meter = meters.save(new Meter("CEAC-E-000500", MeterType.ELECTRICITY, "Foyer", "Fès", 34, -5, 15, MeterStatus.ACTIVE));
        // 09:50 (before the period) 100 | 10:10 101 | 10:50 103 | no reading in hour 11 |
        // 12:20 110 | 12:40 108 (index goes down) | 13:10 109
        insert("2026-10-12T09:50:00Z", "100");
        insert("2026-10-12T10:10:00Z", "101");
        insert("2026-10-12T10:50:00Z", "103");
        insert("2026-10-12T12:20:00Z", "110");
        insert("2026-10-12T12:40:00Z", "108");
        insert("2026-10-12T13:10:00Z", "109");
    }

    private void insert(String at, String index) {
        readings.insertIfAbsent(new StoredReading(meter.getId(), Instant.parse(at), new BigDecimal(index),
                new BigDecimal("230.4"), -70));
    }

    private JsonNode consumption(String query) {
        return http.getForObject("/api/meters/" + meter.getId() + "/consumption?" + query, JsonNode.class);
    }

    @Test
    void hourlyConsumptionHandlesGapsAndDecreasesWithoutNegativeValues() {
        JsonNode result = consumption("from=2026-10-12T10:00:00Z&to=2026-10-12T14:00:00Z&step=hour");

        assertThat(result.get("unit").asText()).isEqualTo("kWh");
        assertThat(result.get("step").asText()).isEqualTo("hour");
        JsonNode points = result.get("points");
        assertThat(points).hasSize(4);
        assertThat(points.get(0).get("bucket").asText()).isEqualTo("2026-10-12T10:00:00Z");
        // hour 10: 103 - 100 | hour 11: no reading | hour 12: 108 - 103 | hour 13: 109 - 108
        assertThat(points.get(0).get("value").decimalValue()).isEqualByComparingTo("3");
        assertThat(points.get(1).get("value").decimalValue()).isEqualByComparingTo("0");
        assertThat(points.get(2).get("value").decimalValue()).isEqualByComparingTo("5");
        assertThat(points.get(3).get("value").decimalValue()).isEqualByComparingTo("1");
        assertThat(result.get("total").decimalValue()).isEqualByComparingTo("9");
    }

    @Test
    void dailyBucketsFollowTheMoroccanCalendar() {
        // 12 October, local midnight = 23:00 UTC the day before.
        JsonNode result = consumption("from=2026-10-11T23:00:00Z&to=2026-10-12T23:00:00Z&step=day");

        assertThat(result.get("points")).hasSize(1);
        assertThat(result.get("points").get(0).get("bucket").asText()).isEqualTo("2026-10-11T23:00:00Z");
        // One bucket for the whole day: last index of the day (109) minus the last index before it (100).
        assertThat(result.get("total").decimalValue()).isEqualByComparingTo("9");
    }

    @Test
    void findsTheLatestReadingAtOrBeforeSeveralInstantsInOneQuery() {
        // Used by rule A2 (16 instants per reading): exact match, between readings, after the last one,
        // and before the first one (absent).
        Map<Instant, StoredReading> latest = readings.findLatestAtOrBefore(meter.getId(), List.of(
                Instant.parse("2026-10-12T09:00:00Z"), Instant.parse("2026-10-12T10:10:00Z"),
                Instant.parse("2026-10-12T11:30:00Z"), Instant.parse("2026-10-12T14:00:00Z")));

        assertThat(latest).hasSize(3).doesNotContainKey(Instant.parse("2026-10-12T09:00:00Z"));
        assertThat(latest.get(Instant.parse("2026-10-12T10:10:00Z")).index()).isEqualByComparingTo("101");
        assertThat(latest.get(Instant.parse("2026-10-12T11:30:00Z")).index()).isEqualByComparingTo("103");
        assertThat(latest.get(Instant.parse("2026-10-12T14:00:00Z")).readAt()).isEqualTo(Instant.parse("2026-10-12T13:10:00Z"));
    }

    @Test
    void rejectsInvalidPeriodsAndSteps() {
        String base = "/api/meters/" + meter.getId() + "/consumption?";

        assertThat(http.getForEntity(base + "step=week", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.getForEntity(base + "from=2026-10-12T10:00:00Z&to=2026-10-12T09:00:00Z", JsonNode.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<JsonNode> tooLong = http.getForEntity(
                base + "step=hour&from=2026-08-01T00:00:00Z&to=2026-10-01T00:00:00Z", JsonNode.class);
        assertThat(tooLong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tooLong.getBody().get("detail").asText()).contains("31 jours");
    }

    @Test
    void exportsReadingsAsFrenchCsv() {
        ResponseEntity<byte[]> response = http.getForEntity("/api/meters/" + meter.getId()
                + "/export.csv?from=2026-10-12T10:00:00Z&to=2026-10-12T12:00:00Z", byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("text/csv");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("CEAC-E-000500_releves.csv");
        String csv = new String(response.getBody(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿horodatage;index;tension_v;signal_dbm\r\n");
        assertThat(csv.split("\r\n")).containsExactly(
                "﻿horodatage;index;tension_v;signal_dbm",
                "2026-10-12T11:10:00+01:00;101;230,4;-70",
                "2026-10-12T11:50:00+01:00;103;230,4;-70");
    }

    @Test
    void refusesAnExportPeriodTooLongAsJson() {
        ResponseEntity<JsonNode> response = http.getForEntity("/api/meters/" + meter.getId()
                + "/export.csv?from=2020-01-01T00:00:00Z&to=2026-01-01T00:00:00Z", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").asText()).contains("400 jours");
    }

    @Test
    void meterDetailsIncludeTheLastReading() {
        JsonNode details = http.getForObject("/api/meters/" + meter.getId(), JsonNode.class);

        assertThat(details.get("serial").asText()).isEqualTo("CEAC-E-000500");
        assertThat(details.get("lastReading").get("index").decimalValue()).isEqualByComparingTo("109");
        assertThat(details.get("lastReading").get("timestamp").asText()).isEqualTo("2026-10-12T13:10:00Z");
    }
}
