package ma.ceac.telereleve.anomaly;

import static ma.ceac.telereleve.anomaly.RuleTestSupport.NOW;
import static ma.ceac.telereleve.anomaly.RuleTestSupport.electricity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import ma.ceac.telereleve.alert.AlertService;
import ma.ceac.telereleve.alert.Severity;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterStatus;
import ma.ceac.telereleve.meter.MeterType;
import ma.ceac.telereleve.reading.ReadingRepository;
import ma.ceac.telereleve.reading.StoredReading;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnomalyEngineTest {

    @Mock
    private RuleConfigService ruleConfigs;
    @Mock
    private AlertService alerts;
    @Mock
    private ReadingRepository readings;

    private final StoredReading reading = new StoredReading(UUID.randomUUID(), NOW, BigDecimal.TEN, null, -70);

    /** A rule with a fixed answer. */
    private static AnomalyRule rule(String code, AnomalyRule.Trigger trigger, boolean autoResolve, Optional<AnomalyResult> answer) {
        return new AnomalyRule() {
            public String code() { return code; }
            public Optional<AnomalyResult> evaluate(Meter meter, RuleContext ctx) { return answer; }
            public Trigger trigger() { return trigger; }
            public boolean autoResolve() { return autoResolve; }
        };
    }

    private static final Optional<AnomalyResult> FOUND = Optional.of(new AnomalyResult(Severity.CRITICAL, "anomalie"));

    private AnomalyEngine engine(AnomalyRule... rules) {
        return new AnomalyEngine(List.of(rules), ruleConfigs, alerts, readings);
    }

    @Test
    void raisesAnAlertWhenARuleFindsAnAnomaly() {
        when(ruleConfigs.all()).thenReturn(RuleSettings.DEFAULTS);
        Meter meter = electricity();

        engine(rule("A4", AnomalyRule.Trigger.ON_READING, false, FOUND)).onReading(meter, reading);

        verify(alerts).raise(meter.getId(), "A4", Severity.CRITICAL, "anomalie", NOW);
    }

    @Test
    void resolvesWhenTheAnomalyIsGoneIfTheRuleAllowsIt() {
        when(ruleConfigs.all()).thenReturn(RuleSettings.DEFAULTS);

        engine(rule("A5", AnomalyRule.Trigger.ON_READING, true, Optional.empty()),
                rule("A4", AnomalyRule.Trigger.ON_READING, false, Optional.empty())).onReading(electricity(), reading);

        verify(alerts).resolve(any(), eq("A5"), eq(NOW));
        verify(alerts, never()).resolve(any(), eq("A4"), any());
    }

    @Test
    void skipsDisabledRules() {
        Map<String, RuleSettings> settings = new HashMap<>(RuleSettings.DEFAULTS);
        settings.put("A4", new RuleSettings(false, null, null));
        when(ruleConfigs.all()).thenReturn(settings);

        engine(rule("A4", AnomalyRule.Trigger.ON_READING, false, FOUND)).onReading(electricity(), reading);

        verifyNoInteractions(alerts);
    }

    @Test
    void runsOnlyTheRulesOfTheRequestedTrigger() {
        engine(rule("A3", AnomalyRule.Trigger.SCHEDULED, true, FOUND),
                rule("A4", AnomalyRule.Trigger.ON_READING, false, FOUND))
                .onSchedule(electricity(), NOW, RuleSettings.DEFAULTS);

        verify(alerts).raise(any(), eq("A3"), any(), anyString(), eq(NOW));
        verify(alerts, never()).raise(any(), eq("A4"), any(), anyString(), any());
    }

    @Test
    void ignoresInactiveMeters() {
        Meter inactive = RuleTestSupport.meter("CEAC-E-000005", MeterType.ELECTRICITY, MeterStatus.INACTIVE);

        engine(rule("A3", AnomalyRule.Trigger.SCHEDULED, true, FOUND)).onSchedule(inactive, NOW, RuleSettings.DEFAULTS);

        verifyNoInteractions(alerts);
    }

    @Test
    void aFailingRuleDoesNotStopTheOthers() {
        when(ruleConfigs.all()).thenReturn(RuleSettings.DEFAULTS);
        AnomalyRule broken = new AnomalyRule() {
            public String code() { return "A1"; }
            public Optional<AnomalyResult> evaluate(Meter meter, RuleContext ctx) { throw new IllegalStateException("bug"); }
        };

        engine(broken, rule("A4", AnomalyRule.Trigger.ON_READING, false, FOUND)).onReading(electricity(), reading);

        verify(alerts).raise(any(), eq("A4"), any(), anyString(), any());
    }
}
