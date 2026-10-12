package ma.ceac.telereleve.meter.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;
import ma.ceac.telereleve.alert.dto.AlertResponse;
import ma.ceac.telereleve.reading.dto.ReadingResponse;

/**
 * GET /api/meters/{id}: the meter's fields (flattened at the top level), its last reading (null if it
 * never reported) and its open alerts.
 */
public record MeterDetailsResponse(@JsonUnwrapped MeterResponse meter, ReadingResponse lastReading,
                                   List<AlertResponse> openAlerts) {
}
