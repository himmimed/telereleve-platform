package ma.ceac.telereleve.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, UUID id);

    List<AppUser> findAllByOrderByFullNameAsc();

    List<AppUser> findByRoleAndEnabledTrueOrderByFullNameAsc(Role role);

    long countByRoleAndEnabledTrue(Role role);
}
