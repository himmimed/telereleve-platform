package ma.ceac.telereleve.reading;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import ma.ceac.telereleve.meter.MeterService;
import ma.ceac.telereleve.reading.dto.ConsumptionResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meters/{id}")
@Tag(name = "Consommation", description = "Consommation agrégée et export des relevés")
public class ReadingController {

    private final ConsumptionService consumptionService;
    private final MeterService meterService;

    public ReadingController(ConsumptionService consumptionService, MeterService meterService) {
        this.consumptionService = consumptionService;
        this.meterService = meterService;
    }

    @GetMapping("/consumption")
    @Operation(summary = "Consumption per hour, day or month",
            description = "Difference between successive indexes, aggregated with TimescaleDB time_bucket on the "
                    + "Moroccan calendar (UTC+1). Empty buckets are returned with 0; never negative.")
    public ConsumptionResponse consumption(
            @PathVariable UUID id,
            @Parameter(description = "Start (ISO-8601). Default: 24 h, 7 days or 12 months before `to`")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "End, excluded (ISO-8601). Default: now")
            @RequestParam(required = false) Instant to,
            @Parameter(description = "hour (31 days max), day (400 days max) or month (10 years max)")
            @RequestParam(defaultValue = "day") ConsumptionStep step) {
        return consumptionService.consumption(id, from, to, step);
    }

    @GetMapping("/export.csv")
    @Operation(summary = "Export readings as CSV",
            description = "';' separator, decimal comma, UTF-8 with BOM (Excel in French). Default: last 30 days, 400 days max.")
    public void exportCsv(@PathVariable UUID id,
                          @RequestParam(required = false) Instant from,
                          @RequestParam(required = false) Instant to,
                          HttpServletResponse response) throws IOException {
        // Validate first: once the CSV headers are set, an error could no longer be sent as JSON.
        ConsumptionService.ExportPeriod period = consumptionService.exportPeriod(id, from, to);
        String serial = meterService.get(id).getSerial();
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(serial + "_releves.csv").build().toString());
        Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
        consumptionService.exportCsv(id, period, writer);
        writer.flush();
    }
}
