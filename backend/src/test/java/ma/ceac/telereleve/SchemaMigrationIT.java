package ma.ceac.telereleve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies that Flyway builds the expected schema on a real TimescaleDB, including the
 * database-level guarantees the business rules rely on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SchemaMigrationIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestRestTemplate http;

    @BeforeEach
    void cleanTables() {
        jdbc.execute("TRUNCATE alert, reading, meter, app_user, alert_rule_config CASCADE");
    }

    @Test
    void flywayAppliesV1Successfully() {
        Boolean success = jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);

        assertThat(success).isTrue();
    }

    @Test
    void readingTableIsATimescaleHypertable() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM timescaledb_information.hypertables WHERE hypertable_name = 'reading'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void duplicateReadingForSameMeterAndTimestampIsRejected() {
        UUID meterId = insertMeter("CEAC-E-000001");
        String insert = """
                INSERT INTO reading (meter_id, read_at, index_value, signal_strength)
                VALUES (?, '2026-10-01T10:15:00Z', 100.5, -70)""";
        jdbc.update(insert, meterId);

        assertThatThrownBy(() -> jdbc.update(insert, meterId)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void negativeIndexIsRejected() {
        UUID meterId = insertMeter("CEAC-E-000002");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO reading (meter_id, read_at, index_value, signal_strength)
                VALUES (?, now(), -1, -70)""", meterId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyOneOpenAlertPerMeterAndRuleIsAllowed() {
        UUID meterId = insertMeter("CEAC-E-000003");
        insertAlert(meterId, "A4", "NEW");

        assertThatThrownBy(() -> insertAlert(meterId, "A4", "IN_PROGRESS"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void newAlertIsAllowedOnceThePreviousOneIsResolved() {
        UUID meterId = insertMeter("CEAC-E-000004");
        insertAlert(meterId, "A3", "NEW");
        jdbc.update("UPDATE alert SET status = 'RESOLVED', resolved_at = now() WHERE meter_id = ?", meterId);

        insertAlert(meterId, "A3", "NEW");

        Integer total = jdbc.queryForObject("SELECT count(*) FROM alert WHERE meter_id = ?", Integer.class, meterId);
        assertThat(total).isEqualTo(2);
    }

    @Test
    void healthEndpointReportsUpWithDatabase() {
        ResponseEntity<String> response = http.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"").contains("\"db\"");
    }

    private UUID insertMeter(String serial) {
        return jdbc.queryForObject("""
                INSERT INTO meter (serial, type, client_name, address, latitude, longitude)
                VALUES (?, 'ELECTRICITY', 'Client Test', 'Fès', 34.0331, -5.0003)
                RETURNING id""", UUID.class, serial);
    }

    private void insertAlert(UUID meterId, String ruleCode, String status) {
        jdbc.update("""
                INSERT INTO alert (meter_id, rule_code, severity, status, message)
                VALUES (?, ?, 'CRITICAL', ?, 'test')""", meterId, ruleCode, status);
    }
}
