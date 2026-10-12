package ma.ceac.telereleve;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

/**
 * Real infrastructure for integration tests. Import it with {@code @Import(TestcontainersConfiguration.class)}.
 * Spring caches the test context, so all integration tests share one set of containers per JVM run.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Same images as docker-compose.yml, so tests run against the exact production engines. */
    static final DockerImageName TIMESCALEDB_IMAGE = DockerImageName
            .parse("timescale/timescaledb:2.30.2-pg16")
            .asCompatibleSubstituteFor("postgres");

    static final DockerImageName MOSQUITTO_IMAGE = DockerImageName.parse("eclipse-mosquitto:2.0.22");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> timescaleContainer() {
        return new PostgreSQLContainer<>(TIMESCALEDB_IMAGE);
    }

    /** Broker without authentication: authentication is covered by the docker compose checks. */
    @Bean
    GenericContainer<?> mosquittoContainer() {
        return new GenericContainer<>(MOSQUITTO_IMAGE)
                .withCopyToContainer(Transferable.of("listener 1883\nallow_anonymous true\n"),
                        "/mosquitto/config/mosquitto.conf")
                .withExposedPorts(1883)
                .waitingFor(Wait.forListeningPort());
    }

    @Bean
    DynamicPropertyRegistrar mqttProperties(GenericContainer<?> mosquittoContainer) {
        return registry -> {
            registry.add("app.mqtt.url",
                    () -> "tcp://%s:%d".formatted(mosquittoContainer.getHost(), mosquittoContainer.getMappedPort(1883)));
            registry.add("app.mqtt.username", () -> "");
            registry.add("app.mqtt.password", () -> "");
        };
    }

    /** URL for test clients publishing readings. */
    public static String brokerUrl(GenericContainer<?> mosquitto) {
        return "tcp://%s:%d".formatted(mosquitto.getHost(), mosquitto.getMappedPort(1883));
    }
}
