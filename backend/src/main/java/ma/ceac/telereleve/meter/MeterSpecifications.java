package ma.ceac.telereleve.meter;

import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Optional filters of GET /api/meters, combined with AND; a null argument means "no filter". */
final class MeterSpecifications {

    private MeterSpecifications() {
    }

    static Specification<Meter> matching(MeterType type, MeterStatus status, String search) {
        return Specification.allOf(hasType(type), hasStatus(status), contains(search));
    }

    private static Specification<Meter> hasType(MeterType type) {
        return (root, query, cb) -> type == null ? null : cb.equal(root.get("type"), type);
    }

    private static Specification<Meter> hasStatus(MeterStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    /** Case-insensitive search in serial, client name and address. */
    private static Specification<Meter> contains(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return null;
            }
            String pattern = "%" + escape(search.trim().toLowerCase(Locale.ROOT)) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("serial")), pattern, '\\'),
                    cb.like(cb.lower(root.get("clientName")), pattern, '\\'),
                    cb.like(cb.lower(root.get("address")), pattern, '\\'));
        };
    }

    /** A user typing "%" or "_" searches for that character, not for a wildcard. */
    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
