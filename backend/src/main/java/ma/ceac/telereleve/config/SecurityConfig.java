package ma.ceac.telereleve.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import ma.ceac.telereleve.user.AppUser;
import ma.ceac.telereleve.user.AppUserRepository;
import ma.ceac.telereleve.user.AuthenticatedUser;
import ma.ceac.telereleve.user.JwtService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless JWT security.
 *
 * Route permissions mirror the REST API table of the specification. Finer rules that depend on the data
 * (a technician only sees and updates the alerts assigned to them) are enforced in AlertService.
 * The token only identifies the user: on every request the user is reloaded from the database, so a
 * disabled account or a changed role takes effect immediately, not when the token expires (D29).
 * CSRF protection is off: there is no session cookie, the token travels in the Authorization header.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String OPERATOR = "OPERATOR";
    private static final String TECHNICIAN = "TECHNICIAN";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Converter<Jwt, AbstractAuthenticationToken> jwtToUser,
                                            ObjectMapper json) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Meters
                        .requestMatchers(HttpMethod.GET, "/api/meters/map").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/meters", "/api/meters/**").hasAnyRole(ADMIN, OPERATOR)
                        .requestMatchers(HttpMethod.POST, "/api/meters").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/meters/**").hasRole(ADMIN)
                        // Alerts (technicians: own alerts only, checked in AlertService)
                        .requestMatchers(HttpMethod.GET, "/api/alerts").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/alerts/**").hasAnyRole(OPERATOR, TECHNICIAN)
                        // Dashboard
                        .requestMatchers(HttpMethod.GET, "/api/dashboard/**").authenticated()
                        // Administration
                        .requestMatchers("/api/rules", "/api/rules/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/users/technicians").hasAnyRole(ADMIN, OPERATOR)
                        .requestMatchers("/api/users", "/api/users/**").hasRole(ADMIN)
                        // Anything else: deny by default
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtToUser))
                        .authenticationEntryPoint((request, response, e) ->
                                writeProblem(response, json, HttpStatus.UNAUTHORIZED, "Non authentifié",
                                        "Authentification requise ou session expirée.")))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeProblem(response, json, HttpStatus.UNAUTHORIZED, "Non authentifié",
                                        "Authentification requise ou session expirée."))
                        .accessDeniedHandler((request, response, e) ->
                                writeProblem(response, json, HttpStatus.FORBIDDEN, "Accès refusé",
                                        "Vous n’avez pas les droits nécessaires pour cette action.")));
        return http.build();
    }

    /** Token -> user loaded from the database; unknown or disabled user -> 401. */
    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtToUser(AppUserRepository users) {
        return jwt -> {
            AppUser user;
            try {
                user = users.findById(UUID.fromString(jwt.getClaimAsString(JwtService.USER_ID_CLAIM))).orElse(null);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new InvalidBearerTokenException("Jeton invalide.");
            }
            if (user == null || !user.isEnabled()) {
                throw new InvalidBearerTokenException("Compte inexistant ou désactivé.");
            }
            AuthenticatedUser principal = AuthenticatedUser.of(user);
            return new UsernamePasswordAuthenticationToken(principal, jwt,
                    List.of(new SimpleGrantedAuthority(user.getRole().authority())));
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256).build();
        // Checks the signature, the expiry (with Spring's default 60 s clock skew) and the issuer.
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    private static SecretKey secretKey(JwtProperties properties) {
        return new SecretKeySpec(properties.key(), "HmacSHA256");
    }

    private static void writeProblem(HttpServletResponse response, ObjectMapper json, HttpStatus status,
                                     String title, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), problem);
    }
}
