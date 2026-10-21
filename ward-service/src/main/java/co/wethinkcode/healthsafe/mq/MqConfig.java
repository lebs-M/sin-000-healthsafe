package co.wethinkcode.healthsafe.mq;

/**
 * Shared by every producer/consumer service that talks to the ActiveMQ broker.
 * Duplicated into each participating service's own source tree, since these are
 * independent Maven projects with no shared parent pom.
 *
 * ward-service is a consumer of TOPIC (staffing updates) and a producer on QUEUE
 * (equipment failures detected on its wards).
 */
public final class MqConfig {

    public static final String BROKER_URL =
            env("MQ_BROKER_URL", "tcp://localhost:61616");
    public static final String TOPIC =
            env("MQ_TOPIC", "staffing-events-topic");
    public static final String QUEUE =
            env("MQ_QUEUE", "equipment-failure-queue");

    private MqConfig() {
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
