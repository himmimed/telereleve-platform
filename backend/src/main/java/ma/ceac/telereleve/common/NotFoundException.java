package ma.ceac.telereleve.common;

/** A requested resource does not exist: mapped to 404 by ApiExceptionHandler. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
