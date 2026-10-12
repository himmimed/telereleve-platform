package ma.ceac.telereleve.anomaly;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Settings of one rule, editable by administrators (table alert_rule_config). */
@Entity
@Table(name = "alert_rule_config")
public class AlertRuleConfig {

    @Id
    @Column(name = "rule_code", length = 4)
    private String ruleCode;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(precision = 10, scale = 3)
    private BigDecimal threshold;

    @Column(name = "window_hours")
    private Integer windowHours;

    protected AlertRuleConfig() {
        // for JPA
    }

    public AlertRuleConfig(String ruleCode, RuleSettings settings) {
        this.ruleCode = ruleCode;
        apply(settings);
    }

    public void apply(RuleSettings settings) {
        this.enabled = settings.enabled();
        this.threshold = settings.threshold();
        this.windowHours = settings.windowHours();
    }

    public RuleSettings toSettings() {
        return new RuleSettings(enabled, threshold, windowHours);
    }

    public String getRuleCode() {
        return ruleCode;
    }
}
