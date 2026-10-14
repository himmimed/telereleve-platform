package ma.ceac.telereleve.reading;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import ma.ceac.telereleve.common.BadRequestException;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterService;
import ma.ceac.telereleve.meter.MeterType;
import ma.ceac.telereleve.reading.ConsumptionCalculator.Point;
import ma.ceac.telereleve.reading.dto.ConsumptionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConsumptionService {

    /**
     * Calendar used for days and months: Moroccan standard time as a fixed UTC+1 offset ("Etc/GMT-1",
     * the sign is inverted in POSIX zone names). Not "Africa/Casablanca": the time-zone databases of Java
     * and PostgreSQL disagree on Morocco's offsets (Ramadan changes), which shifted day buckets by one
     * hour between the two. A fixed offset is identical everywhere (docs/decisions.md, D21).
     */
    public static final ZoneId ZONE = ZoneId.of("Etc/GMT-1");

    /** Longest period for a CSV export (about 576 000 rows for a meter read every minute). */
    static final Duration MAX_EXPORT_RANGE = Duration.ofDays(400);

    /** CSV line ending (RFC 4180). */
    private static final String CRLF = "\r\n";

    private final MeterService meterService;
    private final ReadingRepository readings;
    private final Clock clock;

    public ConsumptionService(MeterService meterService, ReadingRepository readings, Clock clock) {
        this.meterService = meterService;
        this.readings = readings;
        this.clock = clock;
    }

    public ConsumptionResponse consumption(UUID meterId, Instant from, Instant to, ConsumptionStep step) {
        Meter meter = meterService.get(meterId);
        Instant end = to != null ? to : clock.instant();
        Instant start = from != null ? from : end.minus(step.defaultRange());
        checkRange(start, end, step.maxRange());

        // Whole buckets: the first one starts at the aligned bucket boundary.
        Instant firstBucket = step.align(start, ZONE);
        List<Instant> buckets = new ArrayList<>();
        for (Instant b = firstBucket; b.isBefore(end); b = step.next(b, ZONE)) {
            buckets.add(b);
        }
        BigDecimal opening = readings.findPrevious(meterId, firstBucket).map(StoredReading::index).orElse(null);
        List<Point> points = ConsumptionCalculator.compute(opening,
                readings.bucketIndexes(meterId, firstBucket, end, step.sqlInterval(), ZONE), buckets);

        List<Point> rounded = points.stream()
                .map(p -> new Point(p.bucket(), p.value().setScale(3, RoundingMode.HALF_UP))).toList();
        BigDecimal total = rounded.stream().map(Point::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ConsumptionResponse(meterId, meter.getType().unit(), step, total, rounded);
    }

    /** Consumption of all meters in one bucket, split by energy: kWh and m³ cannot be added. */
    public record FleetPoint(Instant bucket, BigDecimal electricityKwh, BigDecimal waterM3) {
    }

    /**
     * Consumption of the whole network per bucket, same rules and periods as one meter's consumption:
     * the sum of every meter's consumption curve. Empty buckets are returned with 0.
     */
    public List<FleetPoint> fleetConsumption(Instant from, Instant to, ConsumptionStep step) {
        Instant end = to != null ? to : clock.instant();
        Instant start = from != null ? from : end.minus(step.defaultRange());
        checkRange(start, end, step.maxRange());

        Instant firstBucket = step.align(start, ZONE);
        Map<Instant, BigDecimal[]> byBucket = new LinkedHashMap<>();
        for (Instant b = firstBucket; b.isBefore(end); b = step.next(b, ZONE)) {
            byBucket.put(b, new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (ReadingRepository.FleetBucket row : readings.fleetConsumption(firstBucket, end, step.sqlInterval(), ZONE)) {
            BigDecimal[] values = byBucket.get(row.bucket());
            if (values != null) {
                values[row.type() == MeterType.ELECTRICITY ? 0 : 1] = row.value();
            }
        }
        return byBucket.entrySet().stream()
                .map(e -> new FleetPoint(e.getKey(), e.getValue()[0].setScale(3, RoundingMode.HALF_UP),
                        e.getValue()[1].setScale(3, RoundingMode.HALF_UP)))
                .toList();
    }

    /** A validated export period. */
    public record ExportPeriod(Instant from, Instant to) {
    }

    /** Checks the meter and the period before anything is written to the response. */
    public ExportPeriod exportPeriod(UUID meterId, Instant from, Instant to) {
        meterService.get(meterId);
        Instant end = to != null ? to : clock.instant();
        Instant start = from != null ? from : end.minus(Duration.ofDays(30));
        checkRange(start, end, MAX_EXPORT_RANGE);
        return new ExportPeriod(start, end);
    }

    /**
     * Writes the readings of the period as CSV for Excel in French: ';' separator, decimal comma,
     * local time with offset, UTF-8 with BOM so accents and the encoding are detected.
     */
    public void exportCsv(UUID meterId, ExportPeriod period, Writer out) throws IOException {
        DateTimeFormatter time = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZONE);
        DecimalFormat number = new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.FRANCE));
        out.write('\uFEFF');
        out.write("horodatage;index;tension_v;signal_dbm" + CRLF);
        IOException[] failure = new IOException[1];
        readings.forEachReading(meterId, period.from(), period.to(), rs -> {
            if (failure[0] != null) {
                return;
            }
            try {
                StoredReading r = ReadingRepository.map(rs, 0);
                out.write(time.format(r.readAt()));
                out.write(';');
                out.write(number.format(r.index()));
                out.write(';');
                out.write(r.voltage() == null ? "" : number.format(r.voltage()));
                out.write(';');
                out.write(Integer.toString(r.signalStrength()));
                out.write(CRLF);
            } catch (IOException e) {
                failure[0] = e; // client disconnected: stop writing, report after the query
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    private static void checkRange(Instant from, Instant to, Duration max) {
        if (!from.isBefore(to)) {
            throw new BadRequestException("La date de début doit précéder la date de fin.");
        }
        if (Duration.between(from, to).compareTo(max) > 0) {
            throw new BadRequestException("Période trop longue : %d jours au maximum pour ce pas."
                    .formatted(max.toDays()));
        }
    }
}
