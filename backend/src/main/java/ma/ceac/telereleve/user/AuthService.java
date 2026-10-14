package ma.ceac.telereleve.user;

import java.util.Optional;
import ma.ceac.telereleve.user.dto.UserDtos.LoginResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Checks credentials and issues a token. */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    /** Hash compared when the username is unknown, so both cases take the same time (no user enumeration). */
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("timing-equaliser");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(String username, String password) {
        Optional<AppUser> user = users.findByUsername(username.trim());
        boolean matches = passwordEncoder.matches(password, user.map(AppUser::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !matches) {
            log.info("Failed login for username '{}'", username.replaceAll("[\\r\\n]", "_"));
            throw new BadCredentialsException("Identifiant ou mot de passe incorrect.");
        }
        if (!user.get().isEnabled()) {
            throw new DisabledException("Ce compte est désactivé.");
        }
        return new LoginResponse(jwtService.issue(user.get()), user.get().getRole(), user.get().getFullName());
    }
}
