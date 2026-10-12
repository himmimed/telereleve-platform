package ma.ceac.telereleve.alert.dto;

import jakarta.validation.constraints.NotNull;
import ma.ceac.telereleve.alert.AlertStatus;

/** Body of PATCH /api/alerts/{id}. Assignment (assignedTo) is added with users in phase 4. */
public record AlertUpdateRequest(@NotNull(message = "Le statut est obligatoire.") AlertStatus status) {
}
