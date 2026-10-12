package ma.ceac.telereleve.anomaly;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.util.Locale;
import ma.ceac.telereleve.meter.MeterType;

/** French formatting of alert messages (numbers with a decimal comma, durations, units). */
final class Messages {

    private Messages() {
    }

    static String number(BigDecimal value, int maxDecimals) {
        DecimalFormat format = new DecimalFormat("#,##0." + "#".repeat(maxDecimals),
                DecimalFormatSymbols.getInstance(Locale.FRANCE));
        return format.format(value.setScale(maxDecimals, RoundingMode.HALF_UP));
    }

    static String unit(MeterType type) {
        return type == MeterType.ELECTRICITY ? "kWh" : "m³";
    }

    /** "45 min", "1 h 05", "3 j 2 h". */
    static String duration(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes < 60) {
            return minutes + " min";
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return "%d h %02d".formatted(hours, minutes % 60);
        }
        return "%d j %d h".formatted(hours / 24, hours % 24);
    }
}
