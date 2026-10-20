package co.wethinkcode.healthsafe;

/** Thrown when ward-service reports that a ward id does not exist (maps to HTTP 404). */
public class UnknownWardException extends RuntimeException {

    public UnknownWardException(String message) {
        super(message);
    }
}
