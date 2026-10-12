package ma.ceac.telereleve.anomaly;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterRepository;
import ma.ceac.telereleve.reading.IngestionMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class AnomalySchedulerTest {

    private static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");

    @Mock
    private AnomalyEngine engine;
    @Mock
    private MeterRepository meters;
    @Mock
    private RuleConfigService ruleConfigs;
    @Mock
    private IngestionMonitor ingestion;

    private AnomalyScheduler scheduler() {
        return new AnomalyScheduler(engine, meters, ruleConfigs, ingestion, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void checksEveryActiveMeter() {
        Meter a = RuleTestSupport.electricity();
        Meter b = RuleTestSupport.water();
        when(ruleConfigs.all()).thenReturn(RuleSettings.DEFAULTS);
        when(meters.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a, b), PageRequest.of(0, 500), 2));

        scheduler().checkSilentMeters();

        verify(engine).onSchedule(eq(a), eq(NOW), any());
        verify(engine).onSchedule(eq(b), eq(NOW), any());
        verify(engine, times(2)).onSchedule(any(), any(), any());
    }

    @Test
    void waitsWhileIngestionIsCatchingUp() {
        when(ingestion.isCatchingUp()).thenReturn(true);

        scheduler().checkSilentMeters();

        verifyNoInteractions(engine, meters);
    }
}
