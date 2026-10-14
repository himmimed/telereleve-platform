package ma.ceac.telereleve.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetches the active meters from the backend (GET /api/meters?status=ACTIVE), page by page, after
 * signing in with the simulator's technical account.
 * The backend is the single source of truth for serials: the simulator only publishes for meters
 * that exist, exactly as real meters are registered before they are installed.
 */
public class MeterRegistryClient {

    private static final Logger log = LoggerFactory.getLogger(MeterRegistryClient.class);
    private static final int PAGE_SIZE = 500;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String backendUrl;
    private final String username;
    private final String password;

    public MeterRegistryClient(String backendUrl, String username, String password) {
        this.backendUrl = backendUrl;
        this.username = username;
        this.password = password;
    }

    /** Signs in with the technical account (POST /api/auth/login) and returns the JWT. */
    String login() throws IOException, InterruptedException {
        String body = json.writeValueAsString(Map.of("username", username, "password", password));
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(backendUrl + "/api/auth/login")).timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401) {
            // Wrong credentials will not fix themselves: fail at once instead of retrying.
            throw new IllegalStateException("The backend refused the simulator account '" + username
                    + "' (check SIM_BACKEND_USERNAME / SIM_BACKEND_PASSWORD)");
        }
        if (response.statusCode() != 200) {
            throw new IOException("Login returned HTTP " + response.statusCode());
        }
        return json.readTree(response.body()).get("token").asText();
    }

    public List<SimulatedMeter> fetchActiveMeters() throws IOException, InterruptedException {
        String token = login();
        List<SimulatedMeter> meters = new ArrayList<>();
        int page = 0;
        int totalPages;
        do {
            URI uri = URI.create("%s/api/meters?status=ACTIVE&page=%d&size=%d".formatted(backendUrl, page, PAGE_SIZE));
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                            .header("Authorization", "Bearer " + token).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("GET " + uri + " returned HTTP " + response.statusCode());
            }
            JsonNode body = json.readTree(response.body());
            for (JsonNode meter : body.get("content")) {
                meters.add(new SimulatedMeter(meter.get("serial").asText(),
                        SimulatedMeter.MeterType.valueOf(meter.get("type").asText())));
            }
            totalPages = body.get("totalPages").asInt();
            page++;
        } while (page < totalPages);
        return meters;
    }

    /** Waits for the backend at startup (it may still be applying migrations), retrying every 5 s. */
    public List<SimulatedMeter> fetchActiveMetersWithRetry(Duration maxWait) throws InterruptedException {
        long deadline = System.nanoTime() + maxWait.toNanos();
        while (true) {
            try {
                return fetchActiveMeters();
            } catch (IOException e) {
                if (System.nanoTime() > deadline) {
                    throw new IllegalStateException("Backend unreachable at " + backendUrl, e);
                }
                log.info("Backend not ready ({}), retrying in 5 s", e.getMessage());
                Thread.sleep(5_000);
            }
        }
    }
}
