package ma.ceac.telereleve.alert;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import ma.ceac.telereleve.meter.MeterType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends an e-mail for every new HIGH or CRITICAL alert.
 * After commit (no mail for an alert that was rolled back) and asynchronously (a slow or unreachable
 * mail server never delays ingestion). A failed mail is logged; the alert itself is safe in the database.
 */
@Component
@EnableConfigurationProperties(AlertMailProperties.class)
public class AlertMailNotifier {

    private static final Logger log = LoggerFactory.getLogger(AlertMailNotifier.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter
            .ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRANCE).withZone(ZoneId.of("Etc/GMT-1"));

    private final JavaMailSender mailSender;
    private final AlertMailProperties properties;

    public AlertMailNotifier(JavaMailSender mailSender, AlertMailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener
    public void onAlertRaised(AlertRaisedEvent event) {
        if (!properties.enabled() || !event.severity().notifiedByMail() || properties.to() == null
                || properties.to().isEmpty()) {
            return;
        }
        try {
            mailSender.send(compose(event));
        } catch (MailException e) {
            log.warn("Alert e-mail not sent for {} on {}: {}", event.ruleCode(), event.meterSerial(), e.getMessage());
        }
    }

    SimpleMailMessage compose(AlertRaisedEvent event) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.from());
        mail.setTo(properties.to().toArray(String[]::new));
        mail.setSubject("[%s] %s %s - %s".formatted(severityLabel(event.severity()), event.ruleCode(),
                ruleLabel(event.ruleCode()), event.meterSerial()));
        mail.setText("""
                Une alerte %s a été détectée par la plateforme de télérelève.

                Compteur : %s (%s)
                Client : %s
                Adresse : %s
                Règle : %s, %s
                Détectée le : %s

                %s

                Consulter les alertes : %s/alertes

                Message automatique, merci de ne pas y répondre.
                """.formatted(severityLabel(event.severity()).toLowerCase(Locale.FRANCE), event.meterSerial(),
                event.meterType() == MeterType.ELECTRICITY ? "électricité" : "eau",
                event.clientName(), event.address(), event.ruleCode(), ruleLabel(event.ruleCode()),
                TIME.format(event.detectedAt()), event.message(), properties.platformUrl()));
        return mail;
    }

    static String severityLabel(Severity severity) {
        return switch (severity) {
            case CRITICAL -> "CRITIQUE";
            case HIGH -> "HAUTE";
            case MEDIUM -> "MOYENNE";
        };
    }

    static String ruleLabel(String ruleCode) {
        return switch (ruleCode) {
            case "A1" -> "Consommation nulle";
            case "A2" -> "Pic de consommation";
            case "A3" -> "Compteur silencieux";
            case "A4" -> "Index décroissant";
            case "A5" -> "Tension hors plage";
            default -> ruleCode;
        };
    }
}
