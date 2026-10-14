package ma.ceac.telereleve.user;

import java.util.UUID;

/**
 * The user behind the current request, as loaded from the database for every request (not trusted from
 * the token): controllers receive it with {@code @AuthenticationPrincipal}.
 */
public record AuthenticatedUser(UUID id, String username, String fullName, Role role) {

    public static AuthenticatedUser of(AppUser user) {
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getFullName(), user.getRole());
    }

    public boolean is(Role expected) {
        return role == expected;
    }
}
