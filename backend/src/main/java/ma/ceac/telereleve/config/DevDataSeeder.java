package ma.ceac.telereleve.config;

import java.util.List;
import java.util.Set;
import ma.ceac.telereleve.anomaly.RuleConfigService;
import ma.ceac.telereleve.meter.FesMeterGenerator;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterRepository;
import ma.ceac.telereleve.meter.dto.MeterRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo data, created only with the "dev" profile (never in production).
 * Idempotent: it adds the generated meters that are missing, so restarting is harmless and raising
 * app.seed.meter-count from 50 to 1000 simply adds meters 51 to 1000.
 * Also inserts the default settings of rules A1 to A5. Users are added in phase 4.
 */
@Component
@Profile("dev")
@EnableConfigurationProperties(SeedProperties.class)
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final MeterRepository meters;
    private final RuleConfigService ruleConfigs;
    private final SeedProperties properties;

    public DevDataSeeder(MeterRepository meters, RuleConfigService ruleConfigs, SeedProperties properties) {
        this.meters = meters;
        this.ruleConfigs = ruleConfigs;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedMeters();
        int rules = ruleConfigs.insertMissingDefaults();
        log.info("Dev seed: {} default rule settings created", rules);
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

    private static Meter toMeter(MeterRequest r) {
        return new Meter(r.serial(), r.type(), r.clientName(), r.address(),
                r.latitude(), r.longitude(), r.readingIntervalMin(), r.status());
    }
}
