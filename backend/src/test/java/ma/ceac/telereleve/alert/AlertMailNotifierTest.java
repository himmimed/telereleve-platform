package ma.ceac.telereleve.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.ceac.telereleve.meter.MeterType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class AlertMailNotifierTest {

    @Mock
    private JavaMailSender mailSender;

    private final AlertMailProperties properties = new AlertMailProperties(true, "telereleve@ceac.test",
            List.of("exploitation@ceac.test"), "http://localhost:4200");

    private static AlertRaisedEvent event(Severity severity) {
        return new AlertRaisedEvent(UUID.randomUUID(), "CEAC-E-000007", MeterType.ELECTRICITY, "Foyer El Idrissi",
                "11, Rue Abou Bakr Seddik, Fès", "A4", severity,
                "Index en baisse : 18 342,6 kWh puis 18 297,1 kWh. Fraude possible ou remplacement du compteur.",
                Instant.parse("2026-10-12T09:15:00Z"));
    }

    @Test
    void mailsACriticalAlertWithEverythingNeededToAct() {
        new AlertMailNotifier(mailSender, properties).onAlertRaised(event(Severity.CRITICAL));

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("exploitation@ceac.test");
        assertThat(mail.getValue().getSubject()).isEqualTo("[CRITIQUE] A4 Index décroissant - CEAC-E-000007");
        assertThat(mail.getValue().getText())
                .contains("Foyer El Idrissi")
                .contains("Fraude possible")
                .contains("12 octobre 2026 à 10:15")
                .contains("http://localhost:4200/alertes");
    }

    @Test
    void doesNotMailMediumAlerts() {
        new AlertMailNotifier(mailSender, properties).onAlertRaised(event(Severity.MEDIUM));

        verifyNoInteractions(mailSender);
    }

    @Test
    void canBeDisabled() {
        var disabled = new AlertMailProperties(false, "a@b.c", List.of("x@y.z"), "http://x");
        new AlertMailNotifier(mailSender, disabled).onAlertRaised(event(Severity.CRITICAL));

        verifyNoInteractions(mailSender);
    }

    @Test
    void anUnreachableMailServerIsLoggedNotThrown() {
        doThrow(new MailSendException("connection refused")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> new AlertMailNotifier(mailSender, properties).onAlertRaised(event(Severity.HIGH)))
                .doesNotThrowAnyException();
    }
}
