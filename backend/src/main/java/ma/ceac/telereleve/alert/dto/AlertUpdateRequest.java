package ma.ceac.telereleve.alert.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.Optional;
import java.util.UUID;
import ma.ceac.telereleve.alert.AlertStatus;

/**
 * Body of PATCH /api/alerts/{id}; send only what changes.
 * - status: new status, or absent;
 * - assignedTo: absent = unchanged, null = unassign, an id = assign to that technician.
 *
 * A class rather than a record on purpose: Jackson gives a record's Optional component the same value
 * (empty) whether the field is absent or explicitly null, so a status-only update would have unassigned
 * the alert. Here the setter is only called when the field is present in the JSON (D30).
 */
public final class AlertUpdateRequest {

    private AlertStatus status;
    private UUID assignedTo;
    private boolean assignedToPresent;

    public AlertUpdateRequest() {
        // for Jackson
    }

    /** @param assignedTo null = assignment unchanged, empty = unassign, value = assign */
    public AlertUpdateRequest(AlertStatus status, Optional<UUID> assignedTo) {
        this.status = status;
        if (assignedTo != null) {
            this.assignedTo = assignedTo.orElse(null);
            this.assignedToPresent = true;
        }
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
    }

    @JsonSetter("assignedTo")
    public void setAssignedTo(UUID assignedTo) {
        this.assignedTo = assignedTo;
        this.assignedToPresent = true;
    }

    public AlertStatus status() {
        return status;
    }

    public boolean changesAssignment() {
        return assignedToPresent;
    }

    /** Meaningful only if changesAssignment(): empty means "unassign". */
    public Optional<UUID> assignedTo() {
        return Optional.ofNullable(assignedTo);
    }
}
