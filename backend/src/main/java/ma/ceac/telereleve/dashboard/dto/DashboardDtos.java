package ma.ceac.telereleve.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import ma.ceac.telereleve.alert.Severity;
import ma.ceac.telereleve.meter.MeterType;
import ma.ceac.telereleve.reading.ConsumptionService.FleetPoint;

/** Responses of the map and dashboard endpoints (extensions of the specification, docs/decisions.md D12). */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    /** SILENT wins over ALERT: a meter that stopped reporting is the first thing to check. */
    public enum MapStatus {
        OK, ALERT, SILENT
    }

    /** One active meter on the map; type and clientName let the popup describe it without another call. */
    public record MapMarker(UUID id, String serial, MeterType type, String clientName, double lat, double lng,
                            MapStatus status) {
    }

    /**
     * @param openAlerts       open alerts (NEW or IN_PROGRESS) per severity, every severity present
     * @param consumptionToday consumption since midnight (Moroccan calendar), per energy
     */
    public record DashboardStats(long activeMeters, long silentMeters, Map<Severity, Long> openAlerts,
                                 ConsumptionToday consumptionToday) {
    }

    public record ConsumptionToday(BigDecimal electricityKwh, BigDecimal waterM3) {
    }

    public record TrendPoint(Instant bucket, BigDecimal electricityKwh, BigDecimal waterM3) {
        public static TrendPoint from(FleetPoint point) {
            return new TrendPoint(point.bucket(), point.electricityKwh(), point.waterM3());
        }
    }
}
