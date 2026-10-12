package ma.ceac.telereleve.alert;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.ceac.telereleve.common.BadRequestException;
import ma.ceac.telereleve.common.ConflictException;
import ma.ceac.telereleve.common.NotFoundException;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alert lifecycle.
 * - raise: creates an alert only if none is open for (meter, rule);
 * - resolve: closes the open alert when the anomaly has disappeared;
 * - changeStatus: manual changes from the UI (take charge, resolve, reopen).
 * raise / resolve each run in their own transaction (REQUIRES_NEW): if the database refuses a
 * duplicate (two evaluations racing), only that operation is rolled back, not the other rules.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    public enum Outcome { CREATED, ALREADY_OPEN, RESOLVED, NOTHING_TO_DO }

    private final AlertRepository alerts;
    private final MeterRepository meters;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AlertService(AlertRepository alerts, MeterRepository meters, ApplicationEventPublisher events, Clock clock) {
        this.alerts = alerts;
        this.meters = meters;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome raise(UUID meterId, String ruleCode, Severity severity, String message, Instant detectedAt) {
        if (alerts.findOpen(meterId, ruleCode).isPresent()) {
            return Outcome.ALREADY_OPEN;
        }
        Meter meter = meters.findById(meterId).orElseThrow(() -> new NotFoundException("Ce compteur n’existe pas."));
        try {
            Alert alert = alerts.saveAndFlush(new Alert(meter, ruleCode, severity, message, detectedAt));
            events.publishEvent(new AlertRaisedEvent(alert.getId(), meter.getSerial(), meter.getType(),
                    meter.getClientName(), meter.getAddress(), ruleCode, severity, message, detectedAt));
            log.info("Alert {} raised on {}: {}", ruleCode, meter.getSerial(), message);
            return Outcome.CREATED;
        } catch (DataIntegrityViolationException duplicate) {
            // Another evaluation opened the same alert in the meantime: the unique index kept one.
            return Outcome.ALREADY_OPEN;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome resolve(UUID meterId, String ruleCode, Instant at) {
        Optional<Alert> open = alerts.findOpen(meterId, ruleCode);
        if (open.isEmpty()) {
            return Outcome.NOTHING_TO_DO;
        }
        // A replayed old reading must not produce resolved_at before detected_at.
        Alert alert = open.get();
        alert.changeStatus(AlertStatus.RESOLVED, at.isBefore(alert.getDetectedAt()) ? alert.getDetectedAt() : at);
        log.info("Alert {} on meter {} resolved automatically", ruleCode, meterId);
        return Outcome.RESOLVED;
    }

    @Transactional(readOnly = true)
    public Page<Alert> search(AlertStatus status, Severity severity, String ruleCode, UUID meterId, Pageable pageable) {
        return alerts.findAll(AlertSpecifications.matching(status, severity, ruleCode, meterId), pageable);
    }

    @Transactional(readOnly = true)
    public List<Alert> openAlerts(UUID meterId) {
        return alerts.findOpenByMeter(meterId);
    }

    @Transactional
    public Alert changeStatus(UUID alertId, AlertStatus target) {
        Alert alert = alerts.findWithMeterById(alertId).orElseThrow(() -> new NotFoundException("Cette alerte n’existe pas."));
        if (alert.getStatus() == target) {
            return alert;
        }
        if (!alert.getStatus().canChangeTo(target)) {
            throw new BadRequestException("Impossible de passer une alerte de « %s » à « %s »."
                    .formatted(label(alert.getStatus()), label(target)));
        }
        // Reopening a resolved alert: refused if a newer alert of the same rule is already open.
        boolean reopening = !alert.getStatus().isOpen() && target.isOpen();
        if (reopening && alerts.findOpen(alert.getMeter().getId(), alert.getRuleCode()).isPresent()) {
            throw new ConflictException("Une alerte de cette règle est déjà ouverte pour ce compteur.");
        }
        alert.changeStatus(target, clock.instant());
        return alert;
    }

    private static String label(AlertStatus status) {
        return switch (status) {
            case NEW -> "Nouvelle";
            case IN_PROGRESS -> "En cours";
            case RESOLVED -> "Résolue";
        };
    }
}
