package ma.ceac.telereleve.reading;

/**
 * Published after a new reading is stored (not for duplicates). The anomaly engine (phase 3) listens to
 * it, so ingestion does not depend on the anomaly package.
 */
public record ReadingSavedEvent(StoredReading reading, String serial) {
}
