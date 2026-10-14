package ma.ceac.telereleve.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import ma.ceac.telereleve.alert.AlertRepository;
import ma.ceac.telereleve.common.BadRequestException;
import ma.ceac.telereleve.common.ConflictException;
import ma.ceac.telereleve.user.dto.UserDtos.UserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserRepository users;
    @Mock
    private AlertRepository alerts;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserService service;

    private final AppUser admin = user("admin", Role.ADMIN);
    private final AuthenticatedUser adminCaller = AuthenticatedUser.of(admin);

    @BeforeEach
    void setUp() {
        service = new UserService(users, alerts, encoder);
    }

    private static AppUser user(String username, Role role) {
        AppUser user = new AppUser(username, "hash", "Nom " + username, username + "@ceac.test", role, true);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private static UserRequest request(String username, Role role, boolean enabled, String password) {
        return new UserRequest(username, "Nom", username + "@ceac.test", role, enabled, password);
    }

    @Test
    void createsAUserWithAHashedPassword() {
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));

        AppUser created = service.create(request("salma", Role.OPERATOR, true, "secret-123"));

        assertThat(created.getPasswordHash()).isNotEqualTo("secret-123").startsWith("$2");
        assertThat(encoder.matches("secret-123", created.getPasswordHash())).isTrue();
    }

    @Test
    void refusesADuplicateUsername() {
        when(users.existsByUsername("salma")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("salma", Role.OPERATOR, true, "secret-123")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void requiresAPasswordOnCreation() {
        assertThatThrownBy(() -> service.create(request("salma", Role.OPERATOR, true, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void keepsThePasswordWhenUpdatingWithoutOne() {
        AppUser operator = user("salma", Role.OPERATOR);
        when(users.findById(operator.getId())).thenReturn(Optional.of(operator));

        service.update(operator.getId(), request("salma", Role.OPERATOR, true, null), adminCaller);

        assertThat(operator.getPasswordHash()).isEqualTo("hash");
    }

    @Test
    void anAdministratorCannotDemoteOrDisableThemself() {
        when(users.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.update(admin.getId(), request("admin", Role.OPERATOR, true, null), adminCaller))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("propres droits");
        assertThatThrownBy(() -> service.update(admin.getId(), request("admin", Role.ADMIN, false, null), adminCaller))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void theLastActiveAdministratorCannotBeRemoved() {
        AppUser otherAdmin = user("admin2", Role.ADMIN);
        when(users.findById(otherAdmin.getId())).thenReturn(Optional.of(otherAdmin));
        when(users.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(otherAdmin.getId(), adminCaller))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("au moins un administrateur");
        verify(users, never()).delete(any());
    }

    @Test
    void anAdministratorCannotDeleteThemself() {
        when(users.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.delete(admin.getId(), adminCaller)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void deletingAUserUnassignsTheirAlerts() {
        AppUser technician = user("hamza", Role.TECHNICIAN);
        when(users.findById(technician.getId())).thenReturn(Optional.of(technician));

        service.delete(technician.getId(), adminCaller);

        verify(alerts).unassignAll(technician.getId());
        verify(users).delete(technician);
    }

    @Test
    void aTechnicianWhoChangesRoleLosesTheirAlerts() {
        AppUser technician = user("hamza", Role.TECHNICIAN);
        when(users.findById(technician.getId())).thenReturn(Optional.of(technician));

        service.update(technician.getId(), request("hamza", Role.OPERATOR, true, null), adminCaller);

        verify(alerts).unassignAll(technician.getId());
    }
}
