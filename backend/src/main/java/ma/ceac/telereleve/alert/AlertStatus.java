package ma.ceac.telereleve.alert;

import java.util.Map;
import java.util.Set;

/** NEW and IN_PROGRESS are "open": at most one open alert per (meter, rule). */
public enum AlertStatus {
    NEW,
    IN_PROGRESS,
    RESOLVED;

    /**
     * Allowed manual changes: take charge (NEW -> IN_PROGRESS), resolve (NEW / IN_PROGRESS -> RESOLVED),
     * reopen (RESOLVED -> NEW). Going back from IN_PROGRESS to NEW has no business meaning.
     */
    private static final Map<AlertStatus, Set<AlertStatus>> TRANSITIONS = Map.of(
            NEW, Set.of(IN_PROGRESS, RESOLVED),
            IN_PROGRESS, Set.of(RESOLVED),
            RESOLVED, Set.of(NEW));

    public boolean isOpen() {
        return this != RESOLVED;
    }

    public boolean canChangeTo(AlertStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }
}
