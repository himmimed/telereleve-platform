package ma.ceac.telereleve.alert;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * E-mail notification of HIGH and CRITICAL alerts (app.alerts.mail.*).
 *
 * @param to recipients (the operations mailbox); operators' own addresses are added with users in phase 4
 */
@ConfigurationProperties(prefix = "app.alerts.mail")
public record AlertMailProperties(boolean enabled, String from, List<String> to, String platformUrl) {
}
