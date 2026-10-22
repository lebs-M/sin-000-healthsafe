package co.wethinkcode.healthsafe;

/** Thrown when the ActiveMQ broker cannot be reached (maps to HTTP 503). */
public class BrokerUnavailableException extends RuntimeException {

    public BrokerUnavailableException(String message) {
        super(message);
    }
}
