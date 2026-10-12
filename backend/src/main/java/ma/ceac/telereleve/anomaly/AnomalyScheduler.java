package ma.ceac.telereleve.anomaly;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterRepository;
import ma.ceac.telereleve.meter.MeterStatus;
import ma.ceac.telereleve.reading.IngestionMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every 5 minutes (app.anomaly.schedule-interval), evaluates the SCHEDULED rules (A3, silent meter) for
 * every active meter, 500 meters at a time to keep memory flat with a large park. Skipped while
 * ingestion is draining a backlog (see IngestionMonitor).
 */
@Component
@ConditionalOnProperty(prefix = "app.anomaly", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class AnomalyScheduler {

    private static final Logger log = LoggerFactory.getLogger(AnomalyScheduler.class);
    private static final int BATCH = 500;

    private final AnomalyEngine engine;
    private final MeterRepository meters;
    private final RuleConfigService ruleConfigs;
    private final IngestionMonitor ingestion;
    private final Clock clock;

    public AnomalyScheduler(AnomalyEngine engine, MeterRepository meters, RuleConfigService ruleConfigs,
                            IngestionMonitor ingestion, Clock clock) {
        this.engine = engine;
        this.meters = meters;
        this.ruleConfigs = ruleConfigs;
        this.ingestion = ingestion;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.anomaly.schedule-interval:PT5M}",
            initialDelayString = "${app.anomaly.schedule-initial-delay:PT1M}")
    public void checkSilentMeters() {
        if (ingestion.isCatchingUp()) {
            // Readings are being replayed: last_reading_at is not up to date yet, the check would flag
            // healthy meters as silent. The next run (5 minutes later) checks again.
            log.info("Silent-meter check postponed: ingestion is processing a backlog of readings");
            return;
        }
        Instant now = clock.instant();
        Map<String, RuleSettings> settings = ruleConfigs.all();
        Specification<Meter> active = (root, query, cb) -> cb.equal(root.get("status"), MeterStatus.ACTIVE);
        int checked = 0;
        Page<Meter> page;
        int pageIndex = 0;
        do {
            page = meters.findAll(active, PageRequest.of(pageIndex++, BATCH, Sort.by("serial")));
            for (Meter meter : page) {
                engine.onSchedule(meter, now, settings);
            }
            checked += page.getNumberOfElements();
        } while (page.hasNext());
        log.debug("Scheduled anomaly check done for {} meters", checked);
    }
}
