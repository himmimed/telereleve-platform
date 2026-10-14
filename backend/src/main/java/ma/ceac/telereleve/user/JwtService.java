package ma.ceac.telereleve.user;

import java.time.Clock;
import java.time.Instant;
import ma.ceac.telereleve.config.JwtProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues the access tokens (HS256, signed with JWT_SECRET).
 * Claims: sub = username, uid = user id, role and name (for the client's convenience). The server does
 * not trust role and name from the token: it reloads the user on every request (SecurityConfig).
 */
@Service
public class JwtService {

    public static final String USER_ID_CLAIM = "uid";

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(AppUser user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(properties.ttl()))
                .claim(USER_ID_CLAIM, user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("name", user.getFullName())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
