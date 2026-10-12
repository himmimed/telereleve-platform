package ma.ceac.telereleve.meter;

/** Inactive meters are kept for history but ignored by ingestion and anomaly detection. */
public enum MeterStatus {
    ACTIVE,
    INACTIVE
}
