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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetches the active meters from the backend (GET /api/meters?status=ACTIVE), page by page.
 * The backend is the single source of truth for serials: the simulator only publishes for meters
 * that exist, exactly as real meters are registered before they are installed.
 */
public class MeterRegistryClient {

    private static final Logger log = LoggerFactory.getLogger(MeterRegistryClient.class);
    private static final int PAGE_SIZE = 500;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String backendUrl;

    public MeterRegistryClient(String backendUrl) {
        this.backendUrl = backendUrl;
    }

    public List<SimulatedMeter> fetchActiveMeters() throws IOException, InterruptedException {
        List<SimulatedMeter> meters = new ArrayList<>();
        int page = 0;
        int totalPages;
        do {
            URI uri = URI.create("%s/api/meters?status=ACTIVE&page=%d&size=%d".formatted(backendUrl, page, PAGE_SIZE));
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
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
