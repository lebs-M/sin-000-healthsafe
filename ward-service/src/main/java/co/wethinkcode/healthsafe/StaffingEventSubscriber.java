package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.Optional;

/**
 * Subscribes to the {@code staffing-events-topic} and caches the latest event
 * per ward in a {@link StaffingEventCache}, so ward-service can react to
 * staffing changes asynchronously rather than polling staffing-service.
 *
 * Uses a durable subscription (stable client id + subscription name) over
 * PERSISTENT messages: a subscriber that is briefly disconnected still receives
 * the events it missed. Connection is established on a background thread that
 * keeps retrying, so ward-service starts fine even if the broker is down.
 */
public final class StaffingEventSubscriber implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(StaffingEventSubscriber.class);
    private static final String CLIENT_ID = "ward-service";
    private static final String SUBSCRIPTION_NAME = "ward-service-staffing";
    private static final long RETRY_MILLIS = 10_000;

    private final ActiveMQConnectionFactory factory;
    private final StaffingEventCache cache = new StaffingEventCache();

    private volatile boolean closed;

    public StaffingEventSubscriber() {
        this(MqConfig.BROKER_URL);
    }

    StaffingEventSubscriber(String brokerUrl) {
        this.factory = new ActiveMQConnectionFactory(brokerUrl);
    }

    public void start() {
        Thread subscriber = new Thread(this::run, "staffing-topic-subscriber");
        subscriber.setDaemon(true);
        subscriber.start();
    }

    private void run() {
        while (!closed) {
            try {
                subscribeAndBlock();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (JMSException e) {
                log.warn("staffing topic subscriber cannot reach {} ({}); retrying in {}s",
                        MqConfig.BROKER_URL, e.getMessage(), RETRY_MILLIS / 1000);
            }
            if (!closed) {
                sleep(RETRY_MILLIS);
            }
        }
    }

    private void subscribeAndBlock() throws JMSException, InterruptedException {
        try (Connection connection = factory.createConnection()) {
            connection.setClientID(CLIENT_ID);
            connection.start();
            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                Topic topic = session.createTopic(MqConfig.TOPIC);
                try (MessageConsumer consumer = session.createDurableConsumer(topic, SUBSCRIPTION_NAME)) {
                    consumer.setMessageListener(this::onMessage);
                    log.info("subscribed to topic {} (durable) at {}", MqConfig.TOPIC, MqConfig.BROKER_URL);
                    while (!closed) {
                        Thread.sleep(500);
                    }
                }
            }
        }
    }

    private void onMessage(Message message) {
        try {
            if (message instanceof TextMessage text) {
                cache.accept(text.getText());
            }
        } catch (JMSException e) {
            log.warn("could not read staffing message: {}", e.getMessage());
        }
    }

    public Optional<StaffingEvent> latestFor(String wardId) {
        return cache.latestFor(wardId);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        closed = true;
    }
}
