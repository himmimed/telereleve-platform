package ma.ceac.telereleve.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Against a tiny local HTTP server that mimics GET /api/meters. */
class MeterRegistryClientTest {

    private HttpServer server;
    private final List<String> queries = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private volatile int status = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/login", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean ok = body.contains("\"simulateur\"") && body.contains("\"bon-mot-de-passe\"");
            byte[] bytes = (ok ? "{\"token\":\"jwt-sim\",\"role\":\"OPERATOR\",\"fullName\":\"Simulateur\"}"
                    : "{\"title\":\"Authentification refusée\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(ok ? 200 : 401, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/api/meters", exchange -> {
            authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            String query = exchange.getRequestURI().getQuery();
            queries.add(query);
            String body = query.contains("page=0")
                    ? """
                      {"content":[{"serial":"CEAC-E-000001","type":"ELECTRICITY"},{"serial":"CEAC-W-000002","type":"WATER"}],
                       "page":0,"size":500,"totalElements":3,"totalPages":2}"""
                    : """
                      {"content":[{"serial":"CEAC-E-000004","type":"ELECTRICITY"}],"page":1,"size":500,"totalElements":3,"totalPages":2}""";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private MeterRegistryClient client() {
        return client("bon-mot-de-passe");
    }

    private MeterRegistryClient client(String password) {
        return new MeterRegistryClient("http://127.0.0.1:" + server.getAddress().getPort(), "simulateur", password);
    }

    @Test
    void fetchesEveryPageOfActiveMeters() throws Exception {
        List<SimulatedMeter> meters = client().fetchActiveMeters();

        assertThat(meters).extracting(SimulatedMeter::serial)
                .containsExactly("CEAC-E-000001", "CEAC-W-000002", "CEAC-E-000004");
        assertThat(meters.get(1).type()).isEqualTo(SimulatedMeter.MeterType.WATER);
        assertThat(queries).allMatch(q -> q.contains("status=ACTIVE")).hasSize(2);
        assertThat(authorizations).containsOnly("Bearer jwt-sim");
    }

    @Test
    void stopsAtOnceWhenTheAccountIsRefused() {
        assertThatThrownBy(() -> client("mauvais").fetchActiveMeters())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SIM_BACKEND_PASSWORD");
    }

    @Test
    void failsClearlyOnAnHttpError() {
        status = 503;

        assertThatThrownBy(() -> client().fetchActiveMeters()).isInstanceOf(IOException.class).hasMessageContaining("503");
    }
}
