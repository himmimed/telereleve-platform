package ma.ceac.telereleve.alert;

import java.time.Instant;
import java.util.UUID;
import ma.ceac.telereleve.meter.MeterType;

/** A new alert was created. Carries plain values: listeners run after the transaction is closed. */
public record AlertRaisedEvent(UUID alertId, String meterSerial, MeterType meterType, String clientName,
                               String address, String ruleCode, Severity severity, String message, Instant detectedAt) {
}
