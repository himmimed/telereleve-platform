package ma.ceac.telereleve;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import ma.ceac.telereleve.user.AppUser;
import ma.ceac.telereleve.user.AppUserRepository;
import ma.ceac.telereleve.user.Role;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * HTTP client for integration tests, signed in for real (POST /api/auth/login, then Bearer token), so
 * every API test also exercises the security chain.
 */
public final class ApiClient {

    public static final String PASSWORD = "mot-de-passe-test";

    private final TestRestTemplate http;
    private final String token;

    private ApiClient(TestRestTemplate http, String token) {
        this.http = http;
        this.token = token;
    }

    /** Creates (if needed) a user with this role, signs in and returns a client acting as that user. */
    public static ApiClient as(Role role, TestRestTemplate http, AppUserRepository users, PasswordEncoder encoder) {
        String username = "it-" + role.name().toLowerCase();
        if (!users.existsByUsername(username)) {
            users.save(new AppUser(username, encoder.encode(PASSWORD), "Test " + role.name(), username + "@ceac.test", role, true));
        }
        return signIn(username, PASSWORD, http);
    }

    public static ApiClient signIn(String username, String password, TestRestTemplate http) {
        // The JDK client supports PATCH, the default one does not.
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        ResponseEntity<JsonNode> login = http.postForEntity("/api/auth/login",
                Map.of("username", username, "password", password), JsonNode.class);
        if (!login.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Login failed for " + username + ": " + login.getBody());
        }
        return new ApiClient(http, login.getBody().get("token").asText());
    }

    public static ApiClient withToken(String token, TestRestTemplate http) {
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        return new ApiClient(http, token);
    }

    public String token() {
        return token;
    }

    public <T> ResponseEntity<T> get(String url, Class<T> type) {
        return exchange(HttpMethod.GET, url, null, type);
    }

    public <T> ResponseEntity<T> post(String url, Object body, Class<T> type) {
        return exchange(HttpMethod.POST, url, body, type);
    }

    public <T> ResponseEntity<T> put(String url, Object body, Class<T> type) {
        return exchange(HttpMethod.PUT, url, body, type);
    }

    public <T> ResponseEntity<T> patch(String url, Object body, Class<T> type) {
        return exchange(HttpMethod.PATCH, url, body, type);
    }

    public <T> ResponseEntity<T> delete(String url, Class<T> type) {
        return exchange(HttpMethod.DELETE, url, null, type);
    }

    // Same names as TestRestTemplate, so existing tests only swap the client.

    public <T> T getForObject(String url, Class<T> type) {
        return get(url, type).getBody();
    }

    public <T> ResponseEntity<T> getForEntity(String url, Class<T> type) {
        return get(url, type);
    }

    public <T> ResponseEntity<T> postForEntity(String url, Object body, Class<T> type) {
        return post(url, body, type);
    }

    public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> entity, Class<T> type) {
        return exchange(method, url, entity == null ? null : entity.getBody(), type);
    }

    /** GET returning JSON, for terse assertions. */
    public JsonNode json(String url) {
        return get(url, JsonNode.class).getBody();
    }

    private <T> ResponseEntity<T> exchange(HttpMethod method, String url, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(url, method, new HttpEntity<>(body, headers), type);
    }
}
