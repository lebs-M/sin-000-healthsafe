package co.wethinkcode.healthsafe.mq;

/**
 * Shared by every producer/consumer service that talks to the "staffing-events-topic"
 * ActiveMQ topic. Duplicated into each participating service's own source tree,
 * since these are independent Maven projects with no shared parent pom.
 *
 * staffing-service is the producer of TOPIC (roster updates).
 */
public final class MqConfig {

    public static final String BROKER_URL =
            env("MQ_BROKER_URL", "tcp://localhost:61616");
    public static final String TOPIC =
            env("MQ_TOPIC", "staffing-events-topic");

    private MqConfig() {
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
