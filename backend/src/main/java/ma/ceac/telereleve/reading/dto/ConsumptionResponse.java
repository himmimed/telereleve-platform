package ma.ceac.telereleve.reading.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import ma.ceac.telereleve.reading.ConsumptionCalculator.Point;
import ma.ceac.telereleve.reading.ConsumptionStep;

/**
 * @param unit   kWh or m3
 * @param points one per bucket of the period, empty buckets included (value 0)
 */
public record ConsumptionResponse(UUID meterId, String unit, ConsumptionStep step, BigDecimal total, List<Point> points) {
}
