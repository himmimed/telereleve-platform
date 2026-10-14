package ma.ceac.telereleve.alert;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface AlertRepository extends JpaRepository<Alert, UUID>, JpaSpecificationExecutor<Alert> {

    /** The open alert of a rule on a meter, if any (at most one, guaranteed by a partial unique index). */
    @Query("""
            select a from Alert a
            where a.meter.id = :meterId and a.ruleCode = :ruleCode
              and a.status in (ma.ceac.telereleve.alert.AlertStatus.NEW, ma.ceac.telereleve.alert.AlertStatus.IN_PROGRESS)""")
    Optional<Alert> findOpen(UUID meterId, String ruleCode);

    @Query("""
            select a from Alert a join fetch a.meter left join fetch a.assignee
            where a.meter.id = :meterId
              and a.status in (ma.ceac.telereleve.alert.AlertStatus.NEW, ma.ceac.telereleve.alert.AlertStatus.IN_PROGRESS)
            order by a.severity, a.detectedAt desc""")
    List<Alert> findOpenByMeter(UUID meterId);

    /** The alert with its meter and assignee, so the response can be built after the transaction. */
    @EntityGraph(attributePaths = {"meter", "assignee"})
    Optional<Alert> findWithMeterById(UUID id);

    /** Meter and assignee loaded in the same query: every alert row shows them. */
    @Override
    @EntityGraph(attributePaths = {"meter", "assignee"})
    Page<Alert> findAll(Specification<Alert> spec, Pageable pageable);

    /** Called when a user is deleted, disabled or stops being a technician. */
    @Transactional
    @Modifying
    @Query("update Alert a set a.assignee = null where a.assignee.id = :userId")
    int unassignAll(UUID userId);

    /** Meters with at least one open alert (map status). */
    @Query("""
            select distinct a.meter.id from Alert a
            where a.status in (ma.ceac.telereleve.alert.AlertStatus.NEW, ma.ceac.telereleve.alert.AlertStatus.IN_PROGRESS)""")
    Set<UUID> findMeterIdsWithOpenAlerts();

    /** Number of open alerts per severity; a severity without open alert is absent. */
    @Query("""
            select a.severity as severity, count(a) as count from Alert a
            where a.status in (ma.ceac.telereleve.alert.AlertStatus.NEW, ma.ceac.telereleve.alert.AlertStatus.IN_PROGRESS)
            group by a.severity""")
    List<SeverityCount> countOpenBySeverity();

    interface SeverityCount {
        Severity getSeverity();

        long getCount();
    }
}
