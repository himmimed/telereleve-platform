package ma.ceac.telereleve.simulator;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration read from environment variables (see .env.example).
 *
 * @param mqttUrl          broker URL, e.g. tcp://mosquitto:1883
 * @param backendUrl       base URL of the backend, used to fetch the meter list
 * @param interval         time between two readings of the same meter (1 minute in demo mode)
 * @param maxMeters        simulate at most this many meters (0 = all active meters)
 */
public record SimulatorConfig(
        String mqttUrl,
        String mqttUsername,
        String mqttPassword,
        String backendUrl,
        Duration interval,
        int maxMeters,
        String clientId) {

    public static SimulatorConfig fromEnvironment(Map<String, String> env) {
        String password = env.get("MQTT_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("MQTT_PASSWORD must be set (see .env.example)");
        }
        long intervalSeconds = Long.parseLong(env.getOrDefault("SIM_INTERVAL_SECONDS", "60"));
        if (intervalSeconds < 1) {
            throw new IllegalArgumentException("SIM_INTERVAL_SECONDS must be at least 1");
        }
        int maxMeters = Integer.parseInt(env.getOrDefault("SIM_MAX_METERS", "0"));
        if (maxMeters < 0) {
            throw new IllegalArgumentException("SIM_MAX_METERS must be 0 (all) or positive");
        }
        return new SimulatorConfig(
                env.getOrDefault("MQTT_URL", "tcp://localhost:1883"),
                env.getOrDefault("MQTT_USERNAME", "telereleve"),
                password,
                stripTrailingSlash(env.getOrDefault("BACKEND_URL", "http://localhost:8080")),
                Duration.ofSeconds(intervalSeconds),
                maxMeters,
                env.getOrDefault("SIM_CLIENT_ID", "ceac-simulator"));
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Never print the password. */
    @Override
    public String toString() {
        return "SimulatorConfig[mqttUrl=%s, mqttUsername=%s, backendUrl=%s, interval=%s, maxMeters=%d, clientId=%s]"
                .formatted(mqttUrl, mqttUsername, backendUrl, interval, maxMeters, clientId);
    }
}
