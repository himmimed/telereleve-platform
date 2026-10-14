package ma.ceac.telereleve.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import ma.ceac.telereleve.user.AppUser;
import ma.ceac.telereleve.user.Role;

/** DTOs of the user API, grouped: they are small and only make sense together. */
public final class UserDtos {

    private UserDtos() {
    }

    public record UserResponse(UUID id, String username, String fullName, String email, Role role, boolean enabled) {

        public static UserResponse from(AppUser user) {
            return new UserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                    user.getRole(), user.isEnabled());
        }
    }

    /** id + name: enough to show who an alert is assigned to. */
    public record UserSummary(UUID id, String fullName) {

        public static UserSummary from(AppUser user) {
            return user == null ? null : new UserSummary(user.getId(), user.getFullName());
        }
    }

    /**
     * Body of POST and PUT /api/users. password is required on creation; on update, null or blank keeps
     * the current one.
     */
    public record UserRequest(
            @NotBlank(message = "L’identifiant est obligatoire.")
            @Pattern(regexp = "^[a-z0-9.-]{3,50}$",
                    message = "3 à 50 caractères : lettres minuscules, chiffres, point, tiret.")
            String username,

            @NotBlank(message = "Le nom est obligatoire.")
            @Size(max = 120, message = "120 caractères au maximum.")
            String fullName,

            @NotBlank(message = "L’adresse e-mail est obligatoire.")
            @Email(message = "Adresse e-mail invalide.")
            @Size(max = 254, message = "254 caractères au maximum.")
            String email,

            @NotNull(message = "Le rôle est obligatoire.")
            Role role,

            @NotNull(message = "Préciser si le compte est actif.")
            Boolean enabled,

            @Size(min = 8, max = 72, message = "Le mot de passe doit contenir entre 8 et 72 caractères.")
            String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "Saisissez votre identifiant.") String username,
            @NotBlank(message = "Saisissez votre mot de passe.") String password) {
    }

    public record LoginResponse(String token, Role role, String fullName) {
    }
}
