package co.wethinkcode.healthsafe.mq;

/**
 * Shared by every producer/consumer service that talks to the "equipment-failure-queue"
 * ActiveMQ queue. Duplicated into each participating service's own source tree,
 * since these are independent Maven projects with no shared parent pom.
 */
public final class MqConfig {

    public static final String BROKER_URL =
            env("MQ_BROKER_URL", "tcp://localhost:61616");
    public static final String QUEUE =
            env("MQ_QUEUE", "equipment-failure-queue");

    private MqConfig() {
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
