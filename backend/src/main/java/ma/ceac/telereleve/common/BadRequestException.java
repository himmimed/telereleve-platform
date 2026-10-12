package ma.ceac.telereleve.common;

/** The request is understood but its values are not acceptable (e.g. a period too long): mapped to 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
