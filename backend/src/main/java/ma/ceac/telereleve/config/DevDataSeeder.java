package ma.ceac.telereleve.config;

import java.util.List;
import java.util.Set;
import ma.ceac.telereleve.anomaly.RuleConfigService;
import ma.ceac.telereleve.meter.FesMeterGenerator;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterRepository;
import ma.ceac.telereleve.meter.dto.MeterRequest;
import ma.ceac.telereleve.user.AppUser;
import ma.ceac.telereleve.user.AppUserRepository;
import ma.ceac.telereleve.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo data, created only with the "dev" profile (never in production). Idempotent: only what is
 * missing is added, so restarting is harmless and raising app.seed.meter-count from 50 to 1000 simply
 * adds meters 51 to 1000.
 * - meters around Fès;
 * - default settings of rules A1 to A5;
 * - demo accounts (admin, operateur, technicien, technicien2) and the simulator's technical account,
 *   with passwords taken from the environment, never from the code.
 */
@Component
@Profile("dev")
@EnableConfigurationProperties(SeedProperties.class)
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private record DemoUser(String username, String fullName, Role role) {
    }

    /** Fictitious people. */
    private static final List<DemoUser> DEMO_USERS = List.of(
            new DemoUser("admin", "Youssef El Amrani", Role.ADMIN),
            new DemoUser("operateur", "Salma Bennani", Role.OPERATOR),
            new DemoUser("technicien", "Hamza Tazi", Role.TECHNICIAN),
            new DemoUser("technicien2", "Nadia Alaoui", Role.TECHNICIAN));

    /**
     * Technical account of the simulator, which reads the meter list. OPERATOR is the least privileged of
     * the three roles of the specification that may read meters (D29).
     */
    public static final String SIMULATOR_USERNAME = "simulateur";

    private final MeterRepository meters;
    private final RuleConfigService ruleConfigs;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SeedProperties properties;

    public DevDataSeeder(MeterRepository meters, RuleConfigService ruleConfigs, AppUserRepository users,
                         PasswordEncoder passwordEncoder, SeedProperties properties) {
        this.meters = meters;
        this.ruleConfigs = ruleConfigs;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedMeters();
        int rules = ruleConfigs.insertMissingDefaults();
        log.info("Dev seed: {} default rule settings created", rules);
        seedUsers();
    }

    void seedMeters() {
        Set<String> existing = meters.findAllSerials();
        List<Meter> missing = FesMeterGenerator.generate(properties.meterCount(), properties.waterShare()).stream()
                .filter(request -> !existing.contains(request.serial()))
                .map(DevDataSeeder::toMeter)
                .toList();
        meters.saveAll(missing);
        log.info("Dev seed: {} meters created, {} already present", missing.size(), existing.size());
    }

    void seedUsers() {
        if (isBlank(properties.demoPassword())) {
            log.warn("Dev seed: APP_SEED_DEMO_PASSWORD is not set, no demo account created");
        } else {
            DEMO_USERS.forEach(u -> createIfMissing(u.username(), u.fullName(), u.role(), properties.demoPassword()));
        }
        if (isBlank(properties.simulatorPassword())) {
            log.warn("Dev seed: SIMULATOR_PASSWORD is not set, the simulator will not be able to sign in");
        } else {
            createIfMissing(SIMULATOR_USERNAME, "Simulateur de compteurs", Role.OPERATOR, properties.simulatorPassword());
        }
    }

    private void createIfMissing(String username, String fullName, Role role, String password) {
        if (!users.existsByUsername(username)) {
            users.save(new AppUser(username, passwordEncoder.encode(password), fullName, username + "@ceac.test", role, true));
            log.info("Dev seed: account '{}' ({}) created", username, role);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Meter toMeter(MeterRequest r) {
        return new Meter(r.serial(), r.type(), r.clientName(), r.address(),
                r.latitude(), r.longitude(), r.readingIntervalMin(), r.status());
    }
}
