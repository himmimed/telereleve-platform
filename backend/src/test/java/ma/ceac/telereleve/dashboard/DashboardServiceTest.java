package ma.ceac.telereleve.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import ma.ceac.telereleve.alert.AlertRepository;
import ma.ceac.telereleve.alert.AlertRepository.SeverityCount;
import ma.ceac.telereleve.alert.Severity;
import ma.ceac.telereleve.anomaly.RuleConfigService;
import ma.ceac.telereleve.anomaly.RuleSettings;
import ma.ceac.telereleve.dashboard.dto.DashboardDtos.DashboardStats;
import ma.ceac.telereleve.dashboard.dto.DashboardDtos.MapMarker;
import ma.ceac.telereleve.dashboard.dto.DashboardDtos.MapStatus;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterRepository;
import ma.ceac.telereleve.meter.MeterStatus;
import ma.ceac.telereleve.meter.MeterType;
import ma.ceac.telereleve.reading.ConsumptionService;
import ma.ceac.telereleve.reading.ConsumptionService.FleetPoint;
import ma.ceac.telereleve.reading.ConsumptionStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DashboardServiceTest {

    /** 18:00 UTC = 19:00 in Morocco (UTC+1); local midnight was 23:00 UTC the day before. */
    private static final Instant NOW = Instant.parse("2026-10-14T18:00:00Z");
    private static final Instant LOCAL_MIDNIGHT = Instant.parse("2026-10-13T23:00:00Z");

    private final MeterRepository meters = mock(MeterRepository.class);
    private final AlertRepository alerts = mock(AlertRepository.class);
    private final RuleConfigService ruleConfigs = mock(RuleConfigService.class);
    private final ConsumptionService consumption = mock(ConsumptionService.class);

    private DashboardService service(Instant now) {
        return new DashboardService(meters, alerts, ruleConfigs, consumption, Clock.fixed(now, ZoneOffset.UTC));
    }

    @BeforeEach
    void defaults() {
        when(ruleConfigs.all()).thenReturn(new TreeMap<>(RuleSettings.DEFAULTS));
        when(alerts.findMeterIdsWithOpenAlerts()).thenReturn(Set.of());
        when(alerts.countOpenBySeverity()).thenReturn(List.of());
    }

    /** Active electricity meter read every 15 min, registered long ago, last reading `silentFor` ago. */
    private static Meter meter(String serial, Duration silentFor) {
        Meter meter = new Meter(serial, MeterType.ELECTRICITY, "Foyer Test", "Fès", 34.03, -5.0, 15, MeterStatus.ACTIVE);
        ReflectionTestUtils.setField(meter, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(meter, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        meter.setLastReadingAt(silentFor == null ? null : NOW.minus(silentFor));
        return meter;
    }

    private static SeverityCount count(Severity severity, long count) {
        return new SeverityCount() {
            @Override
            public Severity getSeverity() {
                return severity;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }

    @Test
    void mapStatusIsSilentThenAlertThenOk() {
        Meter ok = meter("CEAC-E-000001", Duration.ofMinutes(5));
        Meter alerting = meter("CEAC-E-000002", Duration.ofMinutes(5));
        Meter silentAndAlerting = meter("CEAC-E-000003", Duration.ofHours(2));
        when(meters.findByStatusOrderBySerial(MeterStatus.ACTIVE)).thenReturn(List.of(ok, alerting, silentAndAlerting));
        when(alerts.findMeterIdsWithOpenAlerts()).thenReturn(Set.of(alerting.getId(), silentAndAlerting.getId()));

        List<MapMarker> markers = service(NOW).mapMarkers();

        assertThat(markers).extracting(MapMarker::status).containsExactly(MapStatus.OK, MapStatus.ALERT, MapStatus.SILENT);
        assertThat(markers.getFirst().lat()).isEqualTo(34.03);
        assertThat(markers.getFirst().clientName()).isEqualTo("Foyer Test");
    }

    @Test
    void silenceFollowsTheA3RuleAndItsSetting() {
        // Exactly 3 intervals (45 min) is tolerated, as in A3; one more second is silent.
        Meter limit = meter("CEAC-E-000001", Duration.ofMinutes(45));
        Meter over = meter("CEAC-E-000002", Duration.ofMinutes(45).plusSeconds(1));
        when(meters.findByStatusOrderBySerial(MeterStatus.ACTIVE)).thenReturn(List.of(limit, over));

        assertThat(service(NOW).mapMarkers()).extracting(MapMarker::status).containsExactly(MapStatus.OK, MapStatus.SILENT);

        // The administrator allows 4 intervals: the second meter is no longer silent.
        Map<String, RuleSettings> settings = new TreeMap<>(RuleSettings.DEFAULTS);
        settings.put("A3", new RuleSettings(true, new BigDecimal("4"), null));
        when(ruleConfigs.all()).thenReturn(settings);

        assertThat(service(NOW).mapMarkers()).extracting(MapMarker::status).containsExactly(MapStatus.OK, MapStatus.OK);
    }

    @Test
    void statsCountSilentMetersAndEverySeverity() {
        when(meters.findByStatusOrderBySerial(MeterStatus.ACTIVE)).thenReturn(List.of(
                meter("CEAC-E-000001", Duration.ofMinutes(5)), meter("CEAC-E-000002", Duration.ofHours(3))));
        when(alerts.countOpenBySeverity()).thenReturn(List.of(count(Severity.HIGH, 2)));
        when(consumption.fleetConsumption(LOCAL_MIDNIGHT, NOW, ConsumptionStep.DAY)).thenReturn(List.of(
                new FleetPoint(LOCAL_MIDNIGHT, new BigDecimal("812.500"), new BigDecimal("41.250"))));

        DashboardStats stats = service(NOW).stats();

        assertThat(stats.activeMeters()).isEqualTo(2);
        assertThat(stats.silentMeters()).isEqualTo(1);
        assertThat(stats.openAlerts()).containsExactly(
                Map.entry(Severity.CRITICAL, 0L), Map.entry(Severity.HIGH, 2L), Map.entry(Severity.MEDIUM, 0L));
        assertThat(stats.consumptionToday().electricityKwh()).isEqualByComparingTo("812.5");
        assertThat(stats.consumptionToday().waterM3()).isEqualByComparingTo("41.25");
    }

    @Test
    void consumptionTodayIsZeroAtLocalMidnight() {
        when(meters.findByStatusOrderBySerial(MeterStatus.ACTIVE)).thenReturn(List.of());

        DashboardStats stats = service(LOCAL_MIDNIGHT).stats();

        assertThat(stats.consumptionToday().electricityKwh()).isZero();
        assertThat(stats.consumptionToday().waterM3()).isZero();
        verify(consumption, never()).fleetConsumption(any(), any(), any());
    }
}
