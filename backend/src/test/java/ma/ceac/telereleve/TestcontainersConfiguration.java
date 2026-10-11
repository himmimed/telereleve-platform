package ma.ceac.telereleve;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real infrastructure for integration tests. Import it with {@code @Import(TestcontainersConfiguration.class)}.
 * Spring caches the test context, so all integration tests share one container per JVM run.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Same image as docker-compose.yml, so tests run against the exact production database engine. */
    static final DockerImageName TIMESCALEDB_IMAGE = DockerImageName
            .parse("timescale/timescaledb:2.30.2-pg16")
            .asCompatibleSubstituteFor("postgres");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> timescaleContainer() {
        return new PostgreSQLContainer<>(TIMESCALEDB_IMAGE);
    }
}
