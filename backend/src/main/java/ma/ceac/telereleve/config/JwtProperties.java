package ma.ceac.telereleve.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT settings (app.security.jwt.*).
 *
 * @param secret base64 HMAC key, from JWT_SECRET; at least 256 bits, as required by HS256
 * @param ttl    lifetime of a token (one working shift)
 */
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(@NotBlank String secret, @NotNull Duration ttl, @NotBlank String issuer) {

    public JwtProperties {
        if (secret != null && !secret.isBlank() && decode(secret).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must be a base64 key of at least 32 bytes (256 bits)");
        }
    }

    public byte[] key() {
        return decode(secret);
    }

    private static byte[] decode(String value) {
        try {
            return Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("JWT_SECRET must be base64-encoded", e);
        }
    }
}
