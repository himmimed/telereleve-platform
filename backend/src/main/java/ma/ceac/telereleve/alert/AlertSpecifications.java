package ma.ceac.telereleve.alert;

import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Filters of GET /api/alerts, and the default order: open first, most severe first, newest first. */
final class AlertSpecifications {

    private AlertSpecifications() {
    }

    static Specification<Alert> matching(AlertStatus status, Severity severity, String ruleCode, UUID meterId) {
        return Specification.allOf(
                (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status),
                (root, query, cb) -> severity == null ? null : cb.equal(root.get("severity"), severity),
                (root, query, cb) -> ruleCode == null ? null : cb.equal(root.get("ruleCode"), ruleCode),
                (root, query, cb) -> meterId == null ? null : cb.equal(root.get("meter").get("id"), meterId),
                ordered());
    }

    private static Specification<Alert> ordered() {
        return (root, query, cb) -> {
            // Not on the count query that Spring Data runs for pagination.
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(
                        cb.asc(cb.selectCase().when(cb.equal(root.get("status"), AlertStatus.RESOLVED), 1).otherwise(0)),
                        // Stored as text: CRITICAL < HIGH < MEDIUM alphabetically, which is also the severity order.
                        cb.asc(root.get("severity")),
                        cb.desc(root.get("detectedAt")));
            }
            return null;
        };
    }
}
