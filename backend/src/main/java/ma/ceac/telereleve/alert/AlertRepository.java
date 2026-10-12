package ma.ceac.telereleve.alert;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AlertRepository extends JpaRepository<Alert, UUID>, JpaSpecificationExecutor<Alert> {

    /** The open alert of a rule on a meter, if any (at most one, guaranteed by a partial unique index). */
    @Query("""
            select a from Alert a
            where a.meter.id = :meterId and a.ruleCode = :ruleCode
              and a.status in (ma.ceac.telereleve.alert.AlertStatus.NEW, ma.ceac.telereleve.alert.AlertStatus.IN_PROGRESS)""")
    Optional<Alert> findOpen(UUID meterId, String ruleCode);

    @Query("""
            select a from Alert a join fetch a.meter
            where a.meter.id = :meterId
              and a.status in (ma.ceac.telereleve.alert.AlertStatus.NEW, ma.ceac.telereleve.alert.AlertStatus.IN_PROGRESS)
            order by a.severity, a.detectedAt desc""")
    List<Alert> findOpenByMeter(UUID meterId);

    /** The alert with its meter, so the response can be built after the transaction. */
    @EntityGraph(attributePaths = "meter")
    Optional<Alert> findWithMeterById(UUID id);

    /** Meter loaded in the same query: every alert row shows its meter's serial. */
    @Override
    @EntityGraph(attributePaths = "meter")
    Page<Alert> findAll(Specification<Alert> spec, Pageable pageable);
}
