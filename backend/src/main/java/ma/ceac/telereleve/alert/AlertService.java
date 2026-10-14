package ma.ceac.telereleve.alert;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import ma.ceac.telereleve.alert.dto.AlertUpdateRequest;
import ma.ceac.telereleve.common.BadRequestException;
import ma.ceac.telereleve.common.ConflictException;
import ma.ceac.telereleve.common.NotFoundException;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterRepository;
import ma.ceac.telereleve.user.AppUser;
import ma.ceac.telereleve.user.AppUserRepository;
import ma.ceac.telereleve.user.AuthenticatedUser;
import ma.ceac.telereleve.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alert lifecycle.
 * - raise: creates an alert only if none is open for (meter, rule);
 * - resolve: closes the open alert when the anomaly has disappeared;
 * - update: manual changes from the UI (take charge, resolve, reopen, assign), with role rules.
 * raise / resolve each run in their own transaction (REQUIRES_NEW): if the database refuses a
 * duplicate (two evaluations racing), only that operation is rolled back, not the other rules.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    public enum Outcome { CREATED, ALREADY_OPEN, RESOLVED, NOTHING_TO_DO }

    private final AlertRepository alerts;
    private final MeterRepository meters;
    private final AppUserRepository users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AlertService(AlertRepository alerts, MeterRepository meters, AppUserRepository users,
                        ApplicationEventPublisher events, Clock clock) {
        this.alerts = alerts;
        this.meters = meters;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    /** Codes of the rules with an open alert (NEW or IN_PROGRESS) on the meter. */
    @Transactional(readOnly = true)
    public Set<String> openRuleCodes(UUID meterId) {
        return alerts.findOpenRuleCodes(meterId);
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

    /** Technicians only ever see the alerts assigned to them. */
    @Transactional(readOnly = true)
    public Page<Alert> search(AlertStatus status, Severity severity, String ruleCode, UUID meterId,
                              AuthenticatedUser caller, Pageable pageable) {
        UUID assignee = caller.is(Role.TECHNICIAN) ? caller.id() : null;
        return alerts.findAll(AlertSpecifications.matching(status, severity, ruleCode, meterId, assignee), pageable);
    }

    @Transactional(readOnly = true)
    public List<Alert> openAlerts(UUID meterId) {
        return alerts.findOpenByMeter(meterId);
    }

    /**
     * PATCH /api/alerts/{id}. Operators change the status and the assignment of any alert; technicians
     * change only the status of alerts assigned to them, and cannot reopen one (the route itself is
     * closed to administrators, as in the specification).
     */
    @Transactional
    public Alert update(UUID alertId, AlertUpdateRequest request, AuthenticatedUser caller) {
        if (request.status() == null && !request.changesAssignment()) {
            throw new BadRequestException("Indiquez le nouveau statut ou l’assignation.");
        }
        Alert alert = alerts.findWithMeterById(alertId)
                .orElseThrow(() -> new NotFoundException("Cette alerte n’existe pas."));
        if (caller.is(Role.TECHNICIAN)) {
            checkTechnicianMayUpdate(alert, request, caller);
        }
        if (request.changesAssignment()) {
            alert.assignTo(request.assignedTo().map(this::assignableTechnician).orElse(null));
        }
        if (request.status() != null && request.status() != alert.getStatus()) {
            changeStatus(alert, request.status());
        }
        return alert;
    }

    private static void checkTechnicianMayUpdate(Alert alert, AlertUpdateRequest request, AuthenticatedUser caller) {
        if (alert.getAssignee() == null || !alert.getAssignee().getId().equals(caller.id())) {
            throw new AccessDeniedException("Cette alerte ne vous est pas assignée.");
        }
        if (request.changesAssignment()) {
            throw new AccessDeniedException("Seul un opérateur peut modifier l’assignation.");
        }
        if (request.status() != null && request.status().isOpen() && !alert.getStatus().isOpen()) {
            throw new AccessDeniedException("Seul un opérateur peut rouvrir une alerte.");
        }
    }

    private AppUser assignableTechnician(UUID userId) {
        return users.findById(userId)
                .filter(user -> user.getRole() == Role.TECHNICIAN && user.isEnabled())
                .orElseThrow(() -> new BadRequestException("Une alerte ne peut être assignée qu’à un technicien actif."));
    }

    private void changeStatus(Alert alert, AlertStatus target) {
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
    }

    private static String label(AlertStatus status) {
        return switch (status) {
            case NEW -> "Nouvelle";
            case IN_PROGRESS -> "En cours";
            case RESOLVED -> "Résolue";
        };
    }
}
