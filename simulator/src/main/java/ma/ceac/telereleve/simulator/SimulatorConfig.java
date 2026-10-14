package ma.ceac.telereleve.simulator;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration read from environment variables (see .env.example).
 *
 * @param mqttUrl        broker URL, e.g. tcp://mosquitto:1883
 * @param backendUrl     base URL of the backend, used to fetch the meter list
 * @param interval       time between two readings of the same meter (1 minute in demo mode)
 * @param maxMeters      simulate at most this many meters (0 = all active meters)
 * @param anomalies      explicit anomalies, "SERIAL:TYPE,..." (SIM_ANOMALIES)
 * @param anomalyPercent share of the other meters given a random anomaly (SIM_ANOMALY_PERCENT, 0-100)
 * @param anomalyDelay   anomalies start this long after the simulator (SIM_ANOMALY_DELAY_MINUTES);
 *                       negative = in the past, within the backfilled history
 * @param backfillDays   readings of the past N days published at startup, every 15 min (SIM_BACKFILL_DAYS)
 * @param backendUsername technical account used to read the meter list (SIM_BACKEND_USERNAME)
 * @param backendPassword its password (SIM_BACKEND_PASSWORD)
 */
public record SimulatorConfig(
        String mqttUrl,
        String mqttUsername,
        String mqttPassword,
        String backendUrl,
        Duration interval,
        int maxMeters,
        String clientId,
        String anomalies,
        int anomalyPercent,
        Duration anomalyDelay,
        int backfillDays,
        String backendUsername,
        String backendPassword) {

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
        int percent = Integer.parseInt(env.getOrDefault("SIM_ANOMALY_PERCENT", "0"));
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("SIM_ANOMALY_PERCENT must be between 0 and 100");
        }
        long delayMinutes = Long.parseLong(env.getOrDefault("SIM_ANOMALY_DELAY_MINUTES", "2"));
        int backfillDays = Integer.parseInt(env.getOrDefault("SIM_BACKFILL_DAYS", "0"));
        if (backfillDays < 0 || backfillDays > 31) {
            throw new IllegalArgumentException("SIM_BACKFILL_DAYS must be between 0 and 31");
        }
        // A negative delay starts the anomalies in the past, inside the backfilled history, so every
        // rule (A1 needs 24 h without consumption) can be demonstrated within minutes.
        if (delayMinutes < -backfillDays * 1440L) {
            throw new IllegalArgumentException("SIM_ANOMALY_DELAY_MINUTES cannot start the anomalies before the backfilled history");
        }
        String backendPassword = env.get("SIM_BACKEND_PASSWORD");
        if (backendPassword == null || backendPassword.isBlank()) {
            throw new IllegalArgumentException("SIM_BACKEND_PASSWORD must be set: the meter list requires authentication");
        }
        String anomalies = env.getOrDefault("SIM_ANOMALIES", "");
        AnomalyPlan.parse(anomalies); // fail fast on a typo
        return new SimulatorConfig(
                env.getOrDefault("MQTT_URL", "tcp://localhost:1883"),
                env.getOrDefault("MQTT_USERNAME", "telereleve"),
                password,
                stripTrailingSlash(env.getOrDefault("BACKEND_URL", "http://localhost:8080")),
                Duration.ofSeconds(intervalSeconds),
                maxMeters,
                env.getOrDefault("SIM_CLIENT_ID", "ceac-simulator"),
                anomalies,
                percent,
                Duration.ofMinutes(delayMinutes),
                backfillDays,
                env.getOrDefault("SIM_BACKEND_USERNAME", "simulateur"),
                backendPassword);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Never print the password. */
    @Override
    public String toString() {
        return ("SimulatorConfig[mqttUrl=%s, mqttUsername=%s, backendUrl=%s, interval=%s, maxMeters=%d, clientId=%s, "
                + "anomalies=%s, anomalyPercent=%d, anomalyDelay=%s, backfillDays=%d, backendUsername=%s]")
                .formatted(mqttUrl, mqttUsername, backendUrl, interval, maxMeters, clientId,
                        anomalies, anomalyPercent, anomalyDelay, backfillDays, backendUsername);
    }
}
