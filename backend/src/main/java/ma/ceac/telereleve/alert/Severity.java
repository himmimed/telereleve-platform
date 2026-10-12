package ma.ceac.telereleve.alert;

/** Declared from the most to the least serious; HIGH and CRITICAL alerts are e-mailed. */
public enum Severity {
    CRITICAL,
    HIGH,
    MEDIUM;

    public boolean notifiedByMail() {
        return this == CRITICAL || this == HIGH;
    }
}
