package ma.ceac.telereleve.anomaly;

import ma.ceac.telereleve.alert.Severity;

/** A detected anomaly. The message is shown to operators as is, hence in French. */
public record AnomalyResult(Severity severity, String message) {
}
