package ma.ceac.telereleve.meter;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface MeterRepository extends JpaRepository<Meter, UUID>, JpaSpecificationExecutor<Meter> {

    boolean existsBySerial(String serial);

    boolean existsBySerialAndIdNot(String serial, UUID id);

    Optional<Meter> findBySerial(String serial);

    @Query("select m.serial from Meter m")
    Set<String> findAllSerials();
}
