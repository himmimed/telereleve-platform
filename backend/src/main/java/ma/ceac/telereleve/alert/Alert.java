package ma.ceac.telereleve.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import ma.ceac.telereleve.meter.Meter;

@Entity
@Table(name = "alert")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meter_id", nullable = false)
    private Meter meter;

    @Column(name = "rule_code", nullable = false, length = 4)
    private String ruleCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AlertStatus status = AlertStatus.NEW;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** User the alert is assigned to (users and assignment arrive in phase 4). */
    @Column(name = "assigned_to")
    private UUID assignedTo;

    protected Alert() {
        // for JPA
    }

    public Alert(Meter meter, String ruleCode, Severity severity, String message, Instant detectedAt) {
        this.meter = meter;
        this.ruleCode = ruleCode;
        this.severity = severity;
        this.message = truncate(message);
        this.detectedAt = detectedAt;
    }

    /** Applies a status change; resolved_at is set exactly when the alert is resolved (DB constraint). */
    public void changeStatus(AlertStatus target, Instant now) {
        this.status = target;
        this.resolvedAt = target == AlertStatus.RESOLVED ? now : null;
    }

    private static String truncate(String message) {
        return message.length() <= 500 ? message : message.substring(0, 497) + "...";
    }

    public UUID getId() {
        return id;
    }

    public Meter getMeter() {
        return meter;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public Severity getSeverity() {
        return severity;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public UUID getAssignedTo() {
        return assignedTo;
    }
}
