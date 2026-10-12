package ma.ceac.telereleve.common;

/** The request clashes with existing data (duplicate serial, ...): mapped to 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
