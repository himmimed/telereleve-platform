package ma.ceac.telereleve.anomaly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules")
@Tag(name = "Règles", description = "Paramètres des règles de détection A1 à A5")
public class RuleConfigController {

    public record RuleConfigResponse(String ruleCode, boolean enabled, BigDecimal threshold, Integer windowHours) {
    }

    public record RuleConfigRequest(
            @NotNull(message = "Préciser si la règle est activée.") Boolean enabled,
            BigDecimal threshold,
            Integer windowHours) {
    }

    private final RuleConfigService ruleConfigs;

    public RuleConfigController(RuleConfigService ruleConfigs) {
        this.ruleConfigs = ruleConfigs;
    }

    @GetMapping
    @Operation(summary = "Settings of the five rules")
    public List<RuleConfigResponse> list() {
        return ruleConfigs.all().entrySet().stream()
                .map(e -> new RuleConfigResponse(e.getKey(), e.getValue().enabled(), e.getValue().threshold(),
                        e.getValue().windowHours()))
                .toList();
    }

    @PutMapping("/{code}")
    @Operation(summary = "Update the settings of a rule",
            description = "A1: windowHours. A2: threshold (multiplier) and windowHours (>= 72). "
                    + "A3: threshold (multiple of the reading interval). A5: threshold (tolerance in %).")
    public RuleConfigResponse update(@PathVariable String code, @Valid @RequestBody RuleConfigRequest request) {
        RuleSettings saved = ruleConfigs.update(code,
                new RuleSettings(request.enabled(), request.threshold(), request.windowHours()));
        return new RuleConfigResponse(code, saved.enabled(), saved.threshold(), saved.windowHours());
    }
}
