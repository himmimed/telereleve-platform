package ma.ceac.telereleve.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import ma.ceac.telereleve.dashboard.dto.DashboardDtos.DashboardStats;
import ma.ceac.telereleve.dashboard.dto.DashboardDtos.MapMarker;
import ma.ceac.telereleve.dashboard.dto.DashboardDtos.TrendPoint;
import ma.ceac.telereleve.reading.ConsumptionStep;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Tableau de bord", description = "Carte des compteurs, indicateurs et consommation du réseau")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/api/meters/map")
    @Operation(summary = "Active meters with their position and state",
            description = "SILENT: no reading for more than the A3 multiple of the reading interval. "
                    + "ALERT: at least one open alert. Otherwise OK.")
    public List<MapMarker> map() {
        return dashboard.mapMarkers();
    }

    @GetMapping("/api/dashboard/stats")
    @Operation(summary = "Key figures of the network",
            description = "Active and silent meters, open alerts per severity, consumption since midnight (UTC+1) per energy.")
    public DashboardStats stats() {
        return dashboard.stats();
    }

    @GetMapping("/api/dashboard/consumption-trend")
    @Operation(summary = "Consumption of the whole network per hour, day or month",
            description = "Sum of every meter's consumption, computed as for one meter; kWh and m³ kept apart.")
    public List<TrendPoint> consumptionTrend(
            @Parameter(description = "Start (ISO-8601). Default: 24 h, 7 days or 12 months before `to`")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "End, excluded (ISO-8601). Default: now")
            @RequestParam(required = false) Instant to,
            @Parameter(description = "hour (31 days max), day (400 days max) or month (10 years max)")
            @RequestParam(defaultValue = "hour") ConsumptionStep step) {
        return dashboard.consumptionTrend(from, to, step);
    }
}
