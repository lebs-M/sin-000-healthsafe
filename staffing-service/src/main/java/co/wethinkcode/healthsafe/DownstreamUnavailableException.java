package co.wethinkcode.healthsafe;

/**
 * Thrown when a service this one depends on cannot be reached or answers with
 * an error (maps to HTTP 503, so the caller can tell "try again" from "no such ward").
 */
public class DownstreamUnavailableException extends RuntimeException {

    public DownstreamUnavailableException(String message) {
        super(message);
    }
}
