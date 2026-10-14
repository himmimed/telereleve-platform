package ma.ceac.telereleve.alert;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import ma.ceac.telereleve.alert.dto.AlertResponse;
import ma.ceac.telereleve.alert.dto.AlertUpdateRequest;
import ma.ceac.telereleve.common.PageResponse;
import ma.ceac.telereleve.user.AuthenticatedUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
@Tag(name = "Alertes", description = "Anomalies détectées et leur traitement")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    @Operation(summary = "List alerts",
            description = "Open alerts first, then by severity, newest first. Technicians only get their own alerts.")
    public PageResponse<AlertResponse> search(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) @Pattern(regexp = "A[1-5]") String ruleCode,
            @RequestParam(required = false) UUID meterId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int size) {
        return PageResponse.of(alertService.search(status, severity, ruleCode, meterId, caller, PageRequest.of(page, size)),
                AlertResponse::from);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Change the status or the assignment of an alert",
            description = "status: take charge (NEW -> IN_PROGRESS), resolve (-> RESOLVED), reopen (RESOLVED -> NEW, "
                    + "operators only). assignedTo: technician id, or null to unassign (operators only). "
                    + "Technicians may only update the alerts assigned to them.")
    public AlertResponse update(@PathVariable UUID id, @Valid @RequestBody AlertUpdateRequest request,
                                @AuthenticationPrincipal AuthenticatedUser caller) {
        return AlertResponse.from(alertService.update(id, request, caller));
    }
}
