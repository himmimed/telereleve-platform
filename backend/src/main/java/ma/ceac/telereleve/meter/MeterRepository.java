package ma.ceac.telereleve.meter;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MeterRepository extends JpaRepository<Meter, UUID>, JpaSpecificationExecutor<Meter> {

    boolean existsBySerial(String serial);

    boolean existsBySerialAndIdNot(String serial, UUID id);

    Optional<Meter> findBySerial(String serial);

    @Query("select m.serial from Meter m")
    Set<String> findAllSerials();

    /**
     * Moves last_reading_at forward only: a late message (older timestamp, e.g. replayed after a network
     * outage) must not make the meter look silent. One SQL statement, so concurrent updates are safe.
     */
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE meter SET last_reading_at = GREATEST(COALESCE(last_reading_at, :readAt), :readAt)
            WHERE id = :id""")
    int advanceLastReadingAt(@Param("id") UUID id, @Param("readAt") Instant readAt);
}
