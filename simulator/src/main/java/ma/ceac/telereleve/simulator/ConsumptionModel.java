package ma.ceac.telereleve.simulator;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import ma.ceac.telereleve.simulator.SimulatedMeter.MeterType;

/**
 * Realistic, stateless consumption model.
 *
 * The index of a meter is a pure function of its serial and of time: base index + everything consumed
 * since a fixed anchor date. Consequences:
 * - the index only ever increases, even if the simulator restarts (no state to lose);
 * - two simulator instances agree on the value;
 * - tests can check any instant without running the simulator.
 *
 * Daily consumption varies by +/-10 % (pseudo-random but reproducible) and is lower or higher at the
 * weekend; it is spread over the day with a profile that is low at night and peaks in the morning and
 * the evening, with +/-15 % noise per hour.
 */
public final class ConsumptionModel {

    /**
     * Local time of the daily profile: Moroccan standard time, UTC+1, as a fixed offset. Not the
     * Africa/Casablanca zone: Morocco moves clocks back during Ramadan, and a repeated local hour would
     * make the index go down for a moment, which the platform would flag as fraud (rule A4).
     */
    public static final ZoneOffset ZONE = ZoneOffset.ofHours(1);

    private static final LocalDate ANCHOR = LocalDate.of(2025, 1, 1);

    private static final double[] ELECTRICITY_PROFILE = {
            0.42, 0.36, 0.33, 0.32, 0.33, 0.40, 0.62, 0.95, 1.08, 0.90, 0.80, 0.82,
            0.92, 0.86, 0.78, 0.76, 0.84, 1.02, 1.34, 1.58, 1.55, 1.30, 0.95, 0.62};

    private static final double[] WATER_PROFILE = {
            0.12, 0.08, 0.06, 0.06, 0.08, 0.25, 1.10, 1.75, 1.50, 0.95, 0.80, 0.85,
            1.15, 1.05, 0.75, 0.65, 0.75, 0.95, 1.30, 1.60, 1.45, 1.05, 0.60, 0.30};

    private ConsumptionModel() {
    }

    /** Index at instant t, rounded to 0.01 kWh or 0.001 m3 (rounding keeps it non-decreasing). */
    public static double indexAt(SimulatedMeter meter, Instant t) {
        long seed = seedOf(meter.serial());
        ZonedDateTime local = t.atZone(ZONE);
        LocalDate today = local.toLocalDate();
        double index = baseIndex(meter.type(), seed);
        for (LocalDate day = ANCHOR; day.isBefore(today); day = day.plusDays(1)) {
            index += dayTotal(meter.type(), seed, day);
        }
        index += dayTotal(meter.type(), seed, today) * shareOfDayElapsed(meter.type(), seed, local);
        int decimals = meter.type() == MeterType.ELECTRICITY ? 2 : 3;
        double factor = Math.pow(10, decimals);
        return Math.round(index * factor) / factor;
    }

    /** Average consumption per day of this meter (kWh or m3), before day-to-day variation. */
    public static double dailyBase(MeterType type, long seed) {
        double u = unit(seed, 1);
        boolean business = unit(seed, 2) < 0.12;
        if (type == MeterType.ELECTRICITY) {
            return business ? 28 + 55 * u : 5.5 + 11 * u;
        }
        return business ? 1.6 + 2.8 * u : 0.28 + 0.55 * u;
    }

    /** Voltage of an electricity meter: 230 V +/- 2 %; null for water meters. */
    public static Double voltageAt(SimulatedMeter meter, Instant t) {
        if (meter.type() == MeterType.WATER) {
            return null;
        }
        double u = unit(seedOf(meter.serial()), 10_000_000L + t.getEpochSecond() / 60);
        return Math.round(230 * (0.98 + 0.04 * u) * 10) / 10.0;
    }

    /** Received signal strength in dBm: stable per meter, with a little jitter. */
    public static int signalAt(SimulatedMeter meter, Instant t) {
        long seed = seedOf(meter.serial());
        int base = -60 - (int) (unit(seed, 3) * 28);
        return base - (int) (unit(seed, 20_000_000L + t.getEpochSecond() / 60) * 4);
    }

    // --------------------------------------------------------------------------------------------

    private static double baseIndex(MeterType type, long seed) {
        return type == MeterType.ELECTRICITY ? 1000 + 8000 * unit(seed, 4) : 100 + 600 * unit(seed, 5);
    }

    private static double dayTotal(MeterType type, long seed, LocalDate day) {
        long dayNumber = ChronoUnit.DAYS.between(ANCHOR, day);
        boolean weekend = day.getDayOfWeek().getValue() >= 6;
        double weekendFactor = weekend ? 1.12 : 1.0;
        double noise = 0.9 + 0.2 * unit(seed, 1_000 + dayNumber);
        return dailyBase(type, seed) * weekendFactor * noise;
    }

    /** Fraction (0..1) of the day's consumption already used at this local time. */
    private static double shareOfDayElapsed(MeterType type, long seed, ZonedDateTime local) {
        double[] profile = type == MeterType.ELECTRICITY ? ELECTRICITY_PROFILE : WATER_PROFILE;
        long dayNumber = ChronoUnit.DAYS.between(ANCHOR, local.toLocalDate());
        double[] weights = new double[24];
        double total = 0;
        for (int h = 0; h < 24; h++) {
            weights[h] = profile[h] * (0.85 + 0.3 * unit(seed, 100_000 + dayNumber * 24 + h));
            total += weights[h];
        }
        int hour = local.getHour();
        double fractionOfHour = (local.getMinute() * 60 + local.getSecond()) / 3600.0;
        double elapsed = 0;
        for (int h = 0; h < hour; h++) {
            elapsed += weights[h];
        }
        elapsed += weights[hour] * fractionOfHour;
        return elapsed / total;
    }

    /** Stable seed for a serial (String.hashCode is specified by the Java language, so it never changes). */
    static long seedOf(String serial) {
        return serial.hashCode() * 0x9E3779B97F4A7C15L;
    }

    /** Pseudo-random number in [0, 1) for (seed, key), SplitMix64 mixing. */
    static double unit(long seed, long key) {
        long z = seed + key * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z >>> 11) * 0x1.0p-53;
    }
}
