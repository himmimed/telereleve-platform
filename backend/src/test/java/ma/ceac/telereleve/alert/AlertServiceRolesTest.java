package ma.ceac.telereleve.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import ma.ceac.telereleve.alert.dto.AlertUpdateRequest;
import ma.ceac.telereleve.common.BadRequestException;
import ma.ceac.telereleve.meter.Meter;
import ma.ceac.telereleve.meter.MeterRepository;
import ma.ceac.telereleve.meter.MeterStatus;
import ma.ceac.telereleve.meter.MeterType;
import ma.ceac.telereleve.user.AppUser;
import ma.ceac.telereleve.user.AppUserRepository;
import ma.ceac.telereleve.user.AuthenticatedUser;
import ma.ceac.telereleve.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

/** Who may change what on an alert. */
@ExtendWith(MockitoExtension.class)
class AlertServiceRolesTest {

    @Mock
    private AlertRepository alerts;
    @Mock
    private MeterRepository meters;
    @Mock
    private AppUserRepository users;
    @Mock
    private ApplicationEventPublisher events;

    private AlertService service;
    private Alert alert;
    private final AppUser hamza = user("technicien", Role.TECHNICIAN);
    private final AppUser nadia = user("technicien2", Role.TECHNICIAN);

    @BeforeEach
    void setUp() {
        service = new AlertService(alerts, meters, users, events, Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC));
        Meter meter = new Meter("CEAC-E-000005", MeterType.ELECTRICITY, "Foyer", "Fès", 34, -5, 15, MeterStatus.ACTIVE);
        ReflectionTestUtils.setField(meter, "id", UUID.randomUUID());
        alert = new Alert(meter, "A4", Severity.CRITICAL, "Index en baisse", Instant.parse("2026-10-14T09:00:00Z"));
        ReflectionTestUtils.setField(alert, "id", UUID.randomUUID());
        // lenient: the empty-request test is refused before the alert is even loaded.
        lenient().when(alerts.findWithMeterById(alert.getId())).thenReturn(Optional.of(alert));
    }

    private static AppUser user(String username, Role role) {
        AppUser user = new AppUser(username, "hash", username, username + "@ceac.test", role, true);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private static AuthenticatedUser caller(AppUser user) {
        return AuthenticatedUser.of(user);
    }

    private static final AuthenticatedUser OPERATOR = new AuthenticatedUser(UUID.randomUUID(), "operateur", "Salma", Role.OPERATOR);

    @Test
    void anOperatorAssignsAnAlertToATechnician() {
        when(users.findById(hamza.getId())).thenReturn(Optional.of(hamza));

        service.update(alert.getId(), new AlertUpdateRequest(null, Optional.of(hamza.getId())), OPERATOR);

        assertThat(alert.getAssignee()).isSameAs(hamza);
    }

    @Test
    void anAlertCannotBeAssignedToANonTechnician() {
        AppUser operator = user("operateur", Role.OPERATOR);
        when(users.findById(operator.getId())).thenReturn(Optional.of(operator));

        assertThatThrownBy(() -> service.update(alert.getId(), new AlertUpdateRequest(null, Optional.of(operator.getId())), OPERATOR))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("technicien actif");
    }

    @Test
    void anOperatorUnassignsWithNull() {
        alert.assignTo(hamza);

        service.update(alert.getId(), new AlertUpdateRequest(null, Optional.empty()), OPERATOR);

        assertThat(alert.getAssignee()).isNull();
    }

    @Test
    void aTechnicianResolvesTheirOwnAlert() {
        alert.assignTo(hamza);

        service.update(alert.getId(), new AlertUpdateRequest(AlertStatus.RESOLVED, null), caller(hamza));

        assertThat(alert.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(alert.getResolvedAt()).isNotNull();
    }

    @Test
    void aTechnicianCannotTouchSomeoneElsesAlert() {
        alert.assignTo(nadia);

        assertThatThrownBy(() -> service.update(alert.getId(), new AlertUpdateRequest(AlertStatus.RESOLVED, null), caller(hamza)))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("pas assignée");
    }

    @Test
    void aTechnicianCannotChangeTheAssignment() {
        alert.assignTo(hamza);

        assertThatThrownBy(() -> service.update(alert.getId(), new AlertUpdateRequest(null, Optional.empty()), caller(hamza)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aTechnicianCannotReopenAResolvedAlert() {
        alert.assignTo(hamza);
        alert.changeStatus(AlertStatus.RESOLVED, Instant.parse("2026-10-14T09:30:00Z"));

        assertThatThrownBy(() -> service.update(alert.getId(), new AlertUpdateRequest(AlertStatus.NEW, null), caller(hamza)))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("rouvrir");
    }

    @Test
    void anEmptyRequestIsRefused() {
        assertThatThrownBy(() -> service.update(alert.getId(), new AlertUpdateRequest(null, null), OPERATOR))
                .isInstanceOf(BadRequestException.class);
    }
}
