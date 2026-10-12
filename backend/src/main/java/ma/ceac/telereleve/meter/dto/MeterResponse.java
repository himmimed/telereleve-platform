package ma.ceac.telereleve.meter.dto;

import java.time.Instant;
import java.util.UUID;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterStatus;
import ma.ceac.telereleve.meter.MeterType;

public record MeterResponse(
        UUID id,
        String serial,
        MeterType type,
        String clientName,
        String address,
        double latitude,
        double longitude,
        int readingIntervalMin,
        MeterStatus status,
        Instant lastReadingAt,
        Instant createdAt) {

    public static MeterResponse from(Meter meter) {
        return new MeterResponse(
                meter.getId(),
                meter.getSerial(),
                meter.getType(),
                meter.getClientName(),
                meter.getAddress(),
                meter.getLatitude(),
                meter.getLongitude(),
                meter.getReadingIntervalMin(),
                meter.getStatus(),
                meter.getLastReadingAt(),
                meter.getCreatedAt());
    }
}
