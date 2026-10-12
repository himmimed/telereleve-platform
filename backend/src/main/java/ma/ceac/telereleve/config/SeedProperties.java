package ma.ceac.telereleve.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Size of the demo meter park created by DevDataSeeder (dev profile only).
 *
 * @param meterCount number of meters, 50 by default; 1000 for the load test (APP_SEED_METER_COUNT)
 * @param waterShare share of water meters, the others are electricity meters
 */
@Validated
@ConfigurationProperties(prefix = "app.seed")
public record SeedProperties(
        @Min(0) @Max(100_000) int meterCount,
        @DecimalMin("0.0") @DecimalMax("1.0") double waterShare) {
}
