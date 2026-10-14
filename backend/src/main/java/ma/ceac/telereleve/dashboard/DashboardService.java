package ma.ceac.telereleve.dashboard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.ceac.telereleve.alert.AlertRepository;
import ma.ceac.telereleve.alert.Severity;
import ma.ceac.telereleve.anomaly.RuleConfigService;
import ma.ceac.telereleve.anomaly.SilentMeterRule;
import ma.ceac.telereleve.dashboard.dto.DashboardDtos.ConsumptionToday;
import ma.ceac.telereleve.dashboard.dto.DashboardDtos.DashboardStats;
import ma.ceac.telereleve.dashboard.dto.DashboardDtos.MapMarker;
import ma.ceac.telereleve.dashboard.dto.DashboardDtos.MapStatus;
import ma.ceac.telereleve.dashboard.dto.DashboardDtos.TrendPoint;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterRepository;
import ma.ceac.telereleve.meter.MeterStatus;
import ma.ceac.telereleve.reading.ConsumptionService;
import ma.ceac.telereleve.reading.ConsumptionService.FleetPoint;
import ma.ceac.telereleve.reading.ConsumptionStep;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Network-wide views: map markers, key figures and consumption curve.
 * "Silent" uses the same test and setting as rule A3, so the map and the figures agree with the alerts,
 * and are already up to date between two runs of the A3 check.
 */
@Service
public class DashboardService {

    private final MeterRepository meters;
    private final AlertRepository alerts;
    private final RuleConfigService ruleConfigs;
    private final ConsumptionService consumption;
    private final Clock clock;

    public DashboardService(MeterRepository meters, AlertRepository alerts, RuleConfigService ruleConfigs,
                            ConsumptionService consumption, Clock clock) {
        this.meters = meters;
        this.alerts = alerts;
        this.ruleConfigs = ruleConfigs;
        this.consumption = consumption;
        this.clock = clock;
    }

    /** Active meters, sorted by serial. */
    @Transactional(readOnly = true)
    public List<MapMarker> mapMarkers() {
        Instant now = clock.instant();
        long multiple = silentMultiple();
        Set<UUID> alerting = alerts.findMeterIdsWithOpenAlerts();
        return meters.findByStatusOrderBySerial(MeterStatus.ACTIVE).stream()
                .map(m -> new MapMarker(m.getId(), m.getSerial(), m.getType(), m.getClientName(), m.getLatitude(),
                        m.getLongitude(), status(m, now, multiple, alerting)))
                .toList();
    }

    @Transactional(readOnly = true)
    public DashboardStats stats() {
        Instant now = clock.instant();
        long multiple = silentMultiple();
        List<Meter> active = meters.findByStatusOrderBySerial(MeterStatus.ACTIVE);
        long silent = active.stream().filter(m -> SilentMeterRule.isSilent(m, now, multiple)).count();

        Map<Severity, Long> open = new EnumMap<>(Severity.class);
        for (Severity severity : Severity.values()) {
            open.put(severity, 0L);
        }
        alerts.countOpenBySeverity().forEach(c -> open.put(c.getSeverity(), c.getCount()));

        Instant midnight = ConsumptionStep.DAY.align(now, ConsumptionService.ZONE);
        ConsumptionToday today = new ConsumptionToday(BigDecimal.ZERO.setScale(3), BigDecimal.ZERO.setScale(3));
        if (midnight.isBefore(now)) {
            FleetPoint point = consumption.fleetConsumption(midnight, now, ConsumptionStep.DAY).getFirst();
            today = new ConsumptionToday(point.electricityKwh(), point.waterM3());
        }
        return new DashboardStats(active.size(), silent, open, today);
    }

    public List<TrendPoint> consumptionTrend(Instant from, Instant to, ConsumptionStep step) {
        return consumption.fleetConsumption(from, to, step).stream().map(TrendPoint::from).toList();
    }

    private static MapStatus status(Meter meter, Instant now, long multiple, Set<UUID> alerting) {
        if (SilentMeterRule.isSilent(meter, now, multiple)) {
            return MapStatus.SILENT;
        }
        return alerting.contains(meter.getId()) ? MapStatus.ALERT : MapStatus.OK;
    }

    private long silentMultiple() {
        return ruleConfigs.all().get("A3").thresholdOr(SilentMeterRule.DEFAULT_MULTIPLE).longValue();
    }
}
