package ma.ceac.telereleve.anomaly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import ma.ceac.telereleve.common.BadRequestException;
import ma.ceac.telereleve.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuleConfigServiceTest {

    @Mock
    private AlertRuleConfigRepository configs;

    @InjectMocks
    private RuleConfigService service;

    @Test
    void fallsBackToTheSpecificationDefaults() {
        when(configs.findAll()).thenReturn(List.of(new AlertRuleConfig("A2", new RuleSettings(true, new BigDecimal("4"), 168))));

        var all = service.all();

        assertThat(all).containsOnlyKeys("A1", "A2", "A3", "A4", "A5");
        assertThat(all.get("A2").threshold()).isEqualByComparingTo("4");
        assertThat(all.get("A5").threshold()).isEqualByComparingTo("10");
        assertThat(all.get("A1").windowHours()).isEqualTo(24);
    }

    @Test
    void storesValidSettings() {
        when(configs.findById("A5")).thenReturn(Optional.empty());
        when(configs.save(any())).thenAnswer(i -> i.getArgument(0));

        RuleSettings saved = service.update("A5", new RuleSettings(true, new BigDecimal("8"), null));

        assertThat(saved.threshold()).isEqualByComparingTo("8");
    }

    @Test
    void rejectsInvalidSettings() {
        assertThatThrownBy(() -> service.update("A2", new RuleSettings(true, new BigDecimal("0.5"), 168)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update("A2", new RuleSettings(true, new BigDecimal("3"), 48)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("72 h");
        assertThatThrownBy(() -> service.update("A1", new RuleSettings(true, null, 0)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update("A5", new RuleSettings(true, new BigDecimal("60"), null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update("A9", new RuleSettings(true, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void insertsOnlyTheMissingDefaults() {
        when(configs.findAll()).thenReturn(List.of(new AlertRuleConfig("A1", RuleSettings.DEFAULTS.get("A1"))));

        int created = service.insertMissingDefaults();

        assertThat(created).isEqualTo(4);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AlertRuleConfig>> saved = ArgumentCaptor.forClass(List.class);
        verify(configs).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(AlertRuleConfig::getRuleCode).containsExactlyInAnyOrder("A2", "A3", "A4", "A5");
    }
}
