package ma.ceac.telereleve.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import ma.ceac.telereleve.ApiClient;
import ma.ceac.telereleve.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** Authentication end to end: real login, real JWT, real database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuthIT {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private JdbcTemplate jdbc;

    private AppUser salma;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE alert, app_user CASCADE");
        salma = users.save(new AppUser("operateur", encoder.encode("bon-mot-de-passe"), "Salma Bennani",
                "s.bennani@ceac.test", Role.OPERATOR, true));
    }

    private ResponseEntity<JsonNode> login(String username, String password) {
        return rest.postForEntity("/api/auth/login", Map.of("username", username, "password", password), JsonNode.class);
    }

    @Test
    void returnsATokenWithTheRoleAndName() {
        ResponseEntity<JsonNode> response = login("operateur", "bon-mot-de-passe");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("role").asText()).isEqualTo("OPERATOR");
        assertThat(response.getBody().get("fullName").asText()).isEqualTo("Salma Bennani");
        assertThat(response.getBody().get("token").asText()).matches("[\\w-]+\\.[\\w-]+\\.[\\w-]+");
    }

    @Test
    void theTokenOpensTheApi() {
        ApiClient api = ApiClient.signIn("operateur", "bon-mot-de-passe", rest);

        assertThat(api.get("/api/meters", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameAnswer() {
        ResponseEntity<JsonNode> wrongPassword = login("operateur", "mauvais");
        ResponseEntity<JsonNode> unknownUser = login("inconnu", "mauvais");

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownUser.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(wrongPassword.getBody().get("detail").asText()).isEqualTo("Identifiant ou mot de passe incorrect.")
                .isEqualTo(unknownUser.getBody().get("detail").asText());
    }

    @Test
    void aDisabledAccountCannotSignIn() {
        users.save(new AppUser("ancien", encoder.encode("bon-mot-de-passe"), "Ancien", "a@ceac.test", Role.OPERATOR, false));

        ResponseEntity<JsonNode> response = login("ancien", "bon-mot-de-passe");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("detail").asText()).contains("désactivé");
    }

    @Test
    void aTamperedTokenIsRejected() {
        String token = ApiClient.signIn("operateur", "bon-mot-de-passe", rest).token();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");

        assertThat(ApiClient.withToken(tampered, rest).get("/api/meters", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anExpiredTokenIsRejected() {
        Instant past = Instant.now().minus(Duration.ofHours(10));
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("ceac-telereleve").subject("operateur")
                        .issuedAt(past).expiresAt(past.plus(Duration.ofHours(8)))
                        .claim(JwtService.USER_ID_CLAIM, salma.getId().toString()).build())).getTokenValue();

        assertThat(ApiClient.withToken(expired, rest).get("/api/meters", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void disablingAUserCutsTheirAccessAtOnce() {
        ApiClient api = ApiClient.signIn("operateur", "bon-mot-de-passe", rest);
        jdbc.update("UPDATE app_user SET enabled = false WHERE username = 'operateur'");

        assertThat(api.get("/api/meters", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aRoleChangeAppliesAtOnce() {
        ApiClient api = ApiClient.signIn("operateur", "bon-mot-de-passe", rest);
        jdbc.update("UPDATE app_user SET role = 'TECHNICIAN' WHERE username = 'operateur'");

        // The token still says OPERATOR, but the server uses the role in the database.
        assertThat(api.get("/api/meters", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void healthAndApiDocsStayPublic() {
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/v3/api-docs", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
