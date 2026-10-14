package ma.ceac.telereleve.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import ma.ceac.telereleve.user.dto.UserDtos.UserRequest;
import ma.ceac.telereleve.user.dto.UserDtos.UserResponse;
import ma.ceac.telereleve.user.dto.UserDtos.UserSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** User administration (ADMIN), and the list of technicians for assignment (ADMIN, OPERATOR). */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Utilisateurs")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List users")
    public List<UserResponse> list() {
        return userService.list().stream().map(UserResponse::from).toList();
    }

    @GetMapping("/technicians")
    @Operation(summary = "Enabled technicians an alert can be assigned to")
    public List<UserSummary> technicians() {
        return userService.technicians().stream().map(UserSummary::from).toList();
    }

    @PostMapping
    @Operation(summary = "Create a user")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        AppUser user = userService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(user.getId()).toUri();
        return ResponseEntity.created(location).body(UserResponse.from(user));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user", description = "A blank password keeps the current one.")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UserRequest request,
                               @AuthenticationPrincipal AuthenticatedUser caller) {
        return UserResponse.from(userService.update(id, request, caller));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a user", description = "Their alerts become unassigned.")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
        userService.delete(id, caller);
        return ResponseEntity.noContent().build();
    }
}
