package ma.ceac.telereleve.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MQTT ingestion settings (app.mqtt.*).
 *
 * @param url      broker URL, e.g. tcp://mosquitto:1883
 * @param username empty for an anonymous broker (tests only)
 * @param clientId must be stable: the broker keeps this client's persistent session and queues QoS 1
 *                 readings for it while the backend is down
 * @param topic    subscription, ceac/meters/+/readings
 */
@Validated
@ConfigurationProperties(prefix = "app.mqtt")
public record MqttProperties(
        boolean enabled,
        @NotBlank String url,
        String username,
        String password,
        @NotBlank String clientId,
        @NotBlank String topic) {
}
