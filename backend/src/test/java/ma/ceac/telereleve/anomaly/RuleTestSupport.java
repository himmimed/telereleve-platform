package ma.ceac.telereleve.anomaly;

import java.time.Instant;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterStatus;
import ma.ceac.telereleve.meter.MeterType;
import org.springframework.test.util.ReflectionTestUtils;

final class RuleTestSupport {

    static final Instant NOW = Instant.parse("2026-10-12T18:00:00Z");

    private RuleTestSupport() {
    }

    static Meter electricity() {
        return meter("CEAC-E-000001", MeterType.ELECTRICITY, MeterStatus.ACTIVE);
    }

    static Meter water() {
        return meter("CEAC-W-000002", MeterType.WATER, MeterStatus.ACTIVE);
    }

    static Meter meter(String serial, MeterType type, MeterStatus status) {
        Meter meter = new Meter(serial, type, "Foyer Test", "1, Rue Ibn Sina, Fès", 34.03, -5.0, 15, status);
        ReflectionTestUtils.setField(meter, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        return meter;
    }

    static RuleContext context(String code, InMemoryReadingHistory history, Instant now) {
        return new RuleContext(now, history.at(now), RuleSettings.DEFAULTS.get(code), history);
    }
}
