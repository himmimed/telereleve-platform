package ma.ceac.telereleve.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import ma.ceac.telereleve.ApiClient;
import ma.ceac.telereleve.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

/**
 * Starts the application with the "dev" profile, as docker compose does, and checks the demo data.
 * Added after a configuration error broke only the dev profile, which no other test activated.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.seed.meter-count=12", "app.seed.demo-password=demo-test-1",
                "app.seed.simulator-password=simulateur-test-1"})
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class DevProfileIT {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void seedsTheDemoAccountsMetersAndRules() {
        ApiClient admin = ApiClient.signIn("admin", "demo-test-1", rest);
        ApiClient operator = ApiClient.signIn("operateur", "demo-test-1", rest);
        ApiClient technician = ApiClient.signIn("technicien", "demo-test-1", rest);
        ApiClient simulator = ApiClient.signIn("simulateur", "simulateur-test-1", rest);

        assertThat(simulator.json("/api/meters?status=ACTIVE&size=500").get("totalElements").asInt()).isGreaterThanOrEqualTo(12);
        assertThat(admin.json("/api/rules")).hasSize(5);
        assertThat(admin.json("/api/users")).extracting(u -> u.get("username").asText())
                .contains("admin", "operateur", "technicien", "technicien2", "simulateur");
        assertThat(operator.json("/api/users/technicians")).hasSizeGreaterThanOrEqualTo(2);
        assertThat(technician.get("/api/meters", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
