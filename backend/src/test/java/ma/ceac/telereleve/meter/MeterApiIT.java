package ma.ceac.telereleve.meter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import ma.ceac.telereleve.ApiClient;
import ma.ceac.telereleve.TestcontainersConfiguration;
import ma.ceac.telereleve.anomaly.RuleConfigService;
import ma.ceac.telereleve.config.DevDataSeeder;
import ma.ceac.telereleve.config.SeedProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ma.ceac.telereleve.user.AppUserRepository;
import ma.ceac.telereleve.user.Role;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Meter API end to end on a real TimescaleDB: persistence, filters, uniqueness, dev seed. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class MeterApiIT {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Signed in as ADMIN (real login, Bearer token). */
    private ApiClient http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MeterRepository meters;

    @Autowired
    private RuleConfigService ruleConfigs;

    @BeforeEach
    void cleanTables() {
        http = ApiClient.as(Role.ADMIN, rest, users, passwordEncoder);
        jdbc.execute("TRUNCATE alert, reading, meter, alert_rule_config CASCADE");
    }

    private static Map<String, Object> body(String serial, String type, String client, String status) {
        return Map.of("serial", serial, "type", type, "clientName", client, "address", "1, Rue Ibn Sina, Fès",
                "latitude", 34.03, "longitude", -5.0, "readingIntervalMin", 15, "status", status);
    }

    private ResponseEntity<JsonNode> create(Map<String, Object> body) {
        return http.postForEntity("/api/meters", body, JsonNode.class);
    }

    @Test
    void createsReadsAndUpdatesAMeter() {
        ResponseEntity<JsonNode> created = create(body("CEAC-E-000001", "ELECTRICITY", "Foyer Tazi", "ACTIVE"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = created.getBody().get("id").asText();
        assertThat(created.getBody().get("createdAt").asText()).isNotBlank();

        JsonNode fetched = http.getForObject("/api/meters/" + id, JsonNode.class);
        assertThat(fetched.get("clientName").asText()).isEqualTo("Foyer Tazi");

        ResponseEntity<JsonNode> updated = http.exchange("/api/meters/" + id, HttpMethod.PUT,
                new HttpEntity<>(body("CEAC-E-000001", "ELECTRICITY", "Foyer Tazi", "INACTIVE")), JsonNode.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("status").asText()).isEqualTo("INACTIVE");
    }

    @Test
    void rejectsADuplicateSerialWith409() {
        create(body("CEAC-E-000001", "ELECTRICITY", "Foyer Tazi", "ACTIVE"));

        ResponseEntity<JsonNode> duplicate = create(body("CEAC-E-000001", "ELECTRICITY", "Autre", "ACTIVE"));

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().get("detail").asText()).contains("CEAC-E-000001");
    }

    @Test
    void filtersByTypeStatusAndText() {
        create(body("CEAC-E-000001", "ELECTRICITY", "Foyer Tazi", "ACTIVE"));
        create(body("CEAC-W-000002", "WATER", "Boulangerie Al Baraka", "ACTIVE"));
        create(body("CEAC-W-000003", "WATER", "Foyer Bennis", "INACTIVE"));

        JsonNode water = http.getForObject("/api/meters?type=WATER", JsonNode.class);
        JsonNode activeWater = http.getForObject("/api/meters?type=WATER&status=ACTIVE", JsonNode.class);
        JsonNode search = http.getForObject("/api/meters?search=BARAKA", JsonNode.class);
        JsonNode bySerial = http.getForObject("/api/meters?search=000003", JsonNode.class);

        assertThat(water.get("totalElements").asInt()).isEqualTo(2);
        assertThat(activeWater.get("totalElements").asInt()).isEqualTo(1);
        assertThat(search.get("content").get(0).get("serial").asText()).isEqualTo("CEAC-W-000002");
        assertThat(bySerial.get("content").get(0).get("clientName").asText()).isEqualTo("Foyer Bennis");
    }

    @Test
    void treatsWildcardCharactersLiterally() {
        create(body("CEAC-E-000001", "ELECTRICITY", "Foyer Tazi", "ACTIVE"));

        JsonNode result = http.getForObject("/api/meters?search=%25", JsonNode.class);

        assertThat(result.get("totalElements").asInt()).isZero();
    }

    @Test
    void paginatesSortedBySerial() {
        create(body("CEAC-E-000002", "ELECTRICITY", "B", "ACTIVE"));
        create(body("CEAC-E-000001", "ELECTRICITY", "A", "ACTIVE"));
        create(body("CEAC-E-000003", "ELECTRICITY", "C", "ACTIVE"));

        JsonNode page = http.getForObject("/api/meters?page=1&size=2", JsonNode.class);

        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("content").get(0).get("serial").asText()).isEqualTo("CEAC-E-000003");
    }

    @Test
    void devSeedCreatesTheParkOnceAndGrowsIt() {
        new DevDataSeeder(meters, ruleConfigs, users, passwordEncoder, new SeedProperties(50, 0.3, null, null)).run(null);
        new DevDataSeeder(meters, ruleConfigs, users, passwordEncoder, new SeedProperties(50, 0.3, null, null)).run(null);
        assertThat(meters.count()).isEqualTo(50);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meter WHERE type = 'WATER'", Integer.class)).isEqualTo(15);

        new DevDataSeeder(meters, ruleConfigs, users, passwordEncoder, new SeedProperties(120, 0.3, null, null)).run(null);
        assertThat(meters.count()).isEqualTo(120);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alert_rule_config", Integer.class)).isEqualTo(5);
    }
}
