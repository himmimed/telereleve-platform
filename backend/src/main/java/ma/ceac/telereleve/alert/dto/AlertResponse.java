package ma.ceac.telereleve.alert.dto;

import java.time.Instant;
import java.util.UUID;
import ma.ceac.telereleve.alert.Alert;
import ma.ceac.telereleve.alert.AlertStatus;
import ma.ceac.telereleve.alert.Severity;
import ma.ceac.telereleve.meter.MeterType;

/**
 * An alert with the identity of its meter, so a list of alerts needs no extra request.
 * assignedTo is null until assignment is implemented with users (phase 4).
 */
public record AlertResponse(
        UUID id,
        UUID meterId,
        String meterSerial,
        MeterType meterType,
        String ruleCode,
        Severity severity,
        AlertStatus status,
        String message,
        Instant detectedAt,
        Instant resolvedAt,
        Object assignedTo) {

    public static AlertResponse from(Alert alert) {
        return new AlertResponse(alert.getId(), alert.getMeter().getId(), alert.getMeter().getSerial(),
                alert.getMeter().getType(), alert.getRuleCode(), alert.getSeverity(), alert.getStatus(),
                alert.getMessage(), alert.getDetectedAt(), alert.getResolvedAt(), null);
    }
}
