package ma.ceac.telereleve.anomaly;

import ma.ceac.telereleve.meter.MeterRepository;
import ma.ceac.telereleve.reading.ReadingSavedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs the anomaly engine for each newly stored reading, once the reading is committed: the reading is
 * kept even if detection fails, and detection sees it in the database (rule A4 compares it with the
 * previous one). Runs on the same thread as ingestion, so each meter's readings are judged in order.
 */
@Component
public class ReadingAnomalyListener {

    private static final Logger log = LoggerFactory.getLogger(ReadingAnomalyListener.class);

    private final AnomalyEngine engine;
    private final MeterRepository meters;

    public ReadingAnomalyListener(AnomalyEngine engine, MeterRepository meters) {
        this.engine = engine;
        this.meters = meters;
    }

    @TransactionalEventListener
    public void onReadingSaved(ReadingSavedEvent event) {
        try {
            meters.findById(event.reading().meterId())
                    .ifPresent(meter -> engine.onReading(meter, event.reading()));
        } catch (RuntimeException e) {
            // Never let detection make ingestion fail (the reading is already committed).
            log.error("Anomaly detection failed for {}", event.serial(), e);
        }
    }
}
