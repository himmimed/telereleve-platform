package ma.ceac.telereleve.meter.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import ma.ceac.telereleve.reading.dto.ReadingResponse;

/**
 * GET /api/meters/{id}: the meter's fields (flattened at the top level) plus its last reading
 * (null if it never reported). The open alerts are added in phase 3.
 */
public record MeterDetailsResponse(@JsonUnwrapped MeterResponse meter, ReadingResponse lastReading) {
}
