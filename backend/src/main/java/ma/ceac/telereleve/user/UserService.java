package ma.ceac.telereleve.user;

import java.util.List;
import java.util.UUID;
import ma.ceac.telereleve.alert.AlertRepository;
import ma.ceac.telereleve.common.BadRequestException;
import ma.ceac.telereleve.common.ConflictException;
import ma.ceac.telereleve.common.NotFoundException;
import ma.ceac.telereleve.user.dto.UserDtos.UserRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User administration. Safeguards so the platform can never be locked out:
 * an administrator cannot delete, disable or demote their own account, and the last active
 * administrator cannot be removed.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final AppUserRepository users;
    private final AlertRepository alerts;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository users, AlertRepository alerts, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.alerts = alerts;
        this.passwordEncoder = passwordEncoder;
    }

    public List<AppUser> list() {
        return users.findAllByOrderByFullNameAsc();
    }

    /** Enabled technicians, the people an alert can be assigned to. */
    public List<AppUser> technicians() {
        return users.findByRoleAndEnabledTrueOrderByFullNameAsc(Role.TECHNICIAN);
    }

    public AppUser get(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Cet utilisateur n’existe pas."));
    }

    @Transactional
    public AppUser create(UserRequest request) {
        if (users.existsByUsername(request.username())) {
            throw duplicate(request.username());
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new BadRequestException("Le mot de passe est obligatoire pour un nouveau compte.");
        }
        return users.save(new AppUser(request.username(), passwordEncoder.encode(request.password()),
                request.fullName().trim(), request.email().trim(), request.role(), request.enabled()));
    }

    @Transactional
    public AppUser update(UUID id, UserRequest request, AuthenticatedUser caller) {
        AppUser user = get(id);
        if (users.existsByUsernameAndIdNot(request.username(), id)) {
            throw duplicate(request.username());
        }
        boolean losesAdmin = user.getRole() == Role.ADMIN && user.isEnabled()
                && (request.role() != Role.ADMIN || !request.enabled());
        if (losesAdmin && user.getId().equals(caller.id())) {
            throw new BadRequestException("Vous ne pouvez pas retirer vos propres droits d’administration.");
        }
        if (losesAdmin && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new BadRequestException("Il doit rester au moins un administrateur actif.");
        }
        user.update(request.username(), request.fullName().trim(), request.email().trim(), request.role(), request.enabled());
        if (request.password() != null && !request.password().isBlank()) {
            user.changePasswordHash(passwordEncoder.encode(request.password()));
        }
        if (request.role() != Role.TECHNICIAN || !request.enabled()) {
            // Only enabled technicians can hold alerts.
            alerts.unassignAll(user.getId());
        }
        return user;
    }

    @Transactional
    public void delete(UUID id, AuthenticatedUser caller) {
        AppUser user = get(id);
        if (user.getId().equals(caller.id())) {
            throw new BadRequestException("Vous ne pouvez pas supprimer votre propre compte.");
        }
        if (user.getRole() == Role.ADMIN && user.isEnabled() && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new BadRequestException("Il doit rester au moins un administrateur actif.");
        }
        alerts.unassignAll(user.getId());
        users.delete(user);
    }

    private static ConflictException duplicate(String username) {
        return new ConflictException("L’identifiant " + username + " est déjà utilisé.");
    }
}
