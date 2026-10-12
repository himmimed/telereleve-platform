package ma.ceac.telereleve.anomaly;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import ma.ceac.telereleve.common.BadRequestException;
import ma.ceac.telereleve.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule settings: the alert_rule_config row when it exists, the specification default otherwise, so
 * detection works even on a database where nobody has configured the rules yet.
 */
@Service
public class RuleConfigService {

    private final AlertRuleConfigRepository configs;

    public RuleConfigService(AlertRuleConfigRepository configs) {
        this.configs = configs;
    }

    /** Settings of every rule, keyed and sorted by code (A1..A5). */
    @Transactional(readOnly = true)
    public Map<String, RuleSettings> all() {
        Map<String, RuleSettings> stored = configs.findAll().stream()
                .collect(Collectors.toMap(AlertRuleConfig::getRuleCode, AlertRuleConfig::toSettings));
        Map<String, RuleSettings> result = new TreeMap<>(RuleSettings.DEFAULTS);
        result.putAll(stored);
        return result;
    }

    @Transactional
    public RuleSettings update(String code, RuleSettings settings) {
        if (!RuleSettings.DEFAULTS.containsKey(code)) {
            throw new NotFoundException("Cette règle n’existe pas.");
        }
        validate(code, settings);
        AlertRuleConfig config = configs.findById(code).orElseGet(() -> new AlertRuleConfig(code, settings));
        config.apply(settings);
        return configs.save(config).toSettings();
    }

    /** Inserts the default row of every rule that has none (dev seed). */
    @Transactional
    public int insertMissingDefaults() {
        Map<String, AlertRuleConfig> existing = configs.findAll().stream()
                .collect(Collectors.toMap(AlertRuleConfig::getRuleCode, Function.identity()));
        var missing = RuleSettings.DEFAULTS.entrySet().stream()
                .filter(e -> !existing.containsKey(e.getKey()))
                .map(e -> new AlertRuleConfig(e.getKey(), e.getValue()))
                .toList();
        configs.saveAll(missing);
        return missing.size();
    }

    private static void validate(String code, RuleSettings s) {
        switch (code) {
            case "A1" -> requireWindow(s);
            case "A2" -> {
                requireThreshold(s, "1", "Le multiplicateur doit être supérieur à 1.");
                requireWindow(s);
                if (s.windowHours() < 72) {
                    throw new BadRequestException("L’historique de référence doit couvrir au moins 72 h (3 jours).");
                }
            }
            case "A3" -> requireThreshold(s, "1", "Le multiple de l’intervalle doit être supérieur à 1.");
            case "A5" -> {
                requireThreshold(s, "0", "La tolérance doit être positive.");
                if (s.threshold().compareTo(new BigDecimal("50")) > 0) {
                    throw new BadRequestException("La tolérance ne peut pas dépasser 50 %.");
                }
            }
            default -> {
                // A4 has no setting besides enabled.
            }
        }
    }

    private static void requireThreshold(RuleSettings s, String exclusiveMin, String message) {
        if (s.threshold() == null || s.threshold().compareTo(new BigDecimal(exclusiveMin)) <= 0) {
            throw new BadRequestException(message);
        }
    }

    private static void requireWindow(RuleSettings s) {
        if (s.windowHours() == null || s.windowHours() < 1 || s.windowHours() > 720) {
            throw new BadRequestException("La fenêtre doit être comprise entre 1 et 720 heures.");
        }
    }
}
