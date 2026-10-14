package ma.ceac.telereleve.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import ma.ceac.telereleve.ApiClient;
import ma.ceac.telereleve.TestcontainersConfiguration;
import ma.ceac.telereleve.alert.AlertService;
import ma.ceac.telereleve.alert.Severity;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterRepository;
import ma.ceac.telereleve.meter.MeterStatus;
import ma.ceac.telereleve.meter.MeterType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** User administration and alert assignment through the API, with the role rules. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class UserApiIT {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private MeterRepository meters;
    @Autowired
    private AlertService alertService;
    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient admin;
    private ApiClient operator;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE alert, reading, meter, app_user CASCADE");
        admin = ApiClient.as(Role.ADMIN, rest, users, encoder);
        operator = ApiClient.as(Role.OPERATOR, rest, users, encoder);
    }

    private static Map<String, Object> user(String username, String role, String password) {
        Map<String, Object> body = new HashMap<>(Map.of("username", username, "fullName", "Hamza Tazi",
                "email", username + "@ceac.test", "role", role, "enabled", true));
        if (password != null) {
            body.put("password", password);
        }
        return body;
    }

    private UUID createTechnician(String username) {
        ResponseEntity<JsonNode> created = admin.post("/api/users", user(username, "TECHNICIAN", "mot-de-passe-1"), JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(created.getBody().get("id").asText());
    }

    @Test
    void anAdministratorManagesUsersAndTheNewUserCanSignIn() {
        UUID id = createTechnician("hamza");

        assertThat(admin.json("/api/users")).extracting(u -> u.get("username").asText()).contains("hamza");
        assertThat(admin.json("/api/users").toString()).doesNotContain("password");
        ApiClient hamza = ApiClient.signIn("hamza", "mot-de-passe-1", rest);
        assertThat(hamza.get("/api/alerts", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> disabled = user("hamza", "TECHNICIAN", null);
        disabled.put("enabled", false);
        assertThat(admin.put("/api/users/" + id, disabled, JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(hamza.get("/api/alerts", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(admin.delete("/api/users/" + id, Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void rejectsInvalidAndDuplicateUsers() {
        createTechnician("hamza");

        assertThat(admin.post("/api/users", user("hamza", "TECHNICIAN", "mot-de-passe-1"), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        ResponseEntity<JsonNode> invalid = admin.post("/api/users", user("Hamza Tazi!", "TECHNICIAN", "court"), JsonNode.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody().get("errors").has("username")).isTrue();
        assertThat(invalid.getBody().get("errors").has("password")).isTrue();
    }

    @Test
    void operatorsAssignAlertsAndTechniciansOnlySeeTheirOwn() {
        UUID hamzaId = createTechnician("hamza");
        createTechnician("nadia");
        Meter meter = meters.save(new Meter("CEAC-E-000005", MeterType.ELECTRICITY, "Foyer", "Fès", 34, -5, 15, MeterStatus.ACTIVE));
        alertService.raise(meter.getId(), "A4", Severity.CRITICAL, "Index en baisse", Instant.now());
        alertService.raise(meter.getId(), "A5", Severity.MEDIUM, "Tension", Instant.now());
        JsonNode alerts = operator.json("/api/alerts").get("content");
        String a4 = alerts.get(0).get("id").asText();

        // The operator sees the technicians and assigns the A4 alert to Hamza.
        assertThat(operator.json("/api/users/technicians")).hasSize(2);
        ResponseEntity<JsonNode> assigned = operator.patch("/api/alerts/" + a4, Map.of("assignedTo", hamzaId.toString()), JsonNode.class);
        assertThat(assigned.getBody().get("assignedTo").get("fullName").asText()).isEqualTo("Hamza Tazi");

        // Hamza sees only his alert, Nadia sees none and cannot touch it.
        ApiClient hamza = ApiClient.signIn("hamza", "mot-de-passe-1", rest);
        ApiClient nadia = ApiClient.signIn("nadia", "mot-de-passe-1", rest);
        assertThat(hamza.json("/api/alerts").get("totalElements").asInt()).isEqualTo(1);
        assertThat(nadia.json("/api/alerts").get("totalElements").asInt()).isZero();
        assertThat(nadia.patch("/api/alerts/" + a4, Map.of("status", "RESOLVED"), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Hamza takes charge and resolves; he may not reassign it.
        assertThat(hamza.patch("/api/alerts/" + a4, Map.of("status", "IN_PROGRESS"), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        Map<String, Object> unassign = new HashMap<>();
        unassign.put("assignedTo", null);
        assertThat(hamza.patch("/api/alerts/" + a4, unassign, JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(hamza.patch("/api/alerts/" + a4, Map.of("status", "RESOLVED"), JsonNode.class).getBody()
                .get("status").asText()).isEqualTo("RESOLVED");

        // Administrators read alerts but do not process them (specification).
        assertThat(admin.patch("/api/alerts/" + a4, Map.of("status", "NEW"), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Deleting Hamza unassigns his alerts.
        admin.delete("/api/users/" + hamzaId, Void.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alert WHERE assigned_to IS NOT NULL", Integer.class)).isZero();
    }
}
