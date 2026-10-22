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
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    private static final long STOP_TIMEOUT_MILLIS = 2_000;

    private final ActiveMQConnectionFactory factory;
    private final String clientId;
    private final String subscriptionName;
    private final StaffingEventCache cache = new StaffingEventCache();

    private final CountDownLatch connected = new CountDownLatch(1);
    private final CountDownLatch stopped = new CountDownLatch(1);

    private volatile boolean closed;
    private volatile boolean running;
    private volatile boolean started;
    private volatile Connection connection;

    public StaffingEventSubscriber() {
        this(MqConfig.BROKER_URL);
    }

    StaffingEventSubscriber(String brokerUrl) {
        this(brokerUrl, CLIENT_ID, SUBSCRIPTION_NAME);
    }

    StaffingEventSubscriber(String brokerUrl, String clientId, String subscriptionName) {
        this.factory = new ActiveMQConnectionFactory(brokerUrl);
        this.clientId = clientId;
        this.subscriptionName = subscriptionName;
    }

    public void start() {
        started = true;
        Thread subscriber = new Thread(this::run, "staffing-topic-subscriber");
        subscriber.setDaemon(true);
        subscriber.start();
    }

    /** Whether the subscription is currently established. */
    public boolean isConnected() {
        return running;
    }

    /**
     * Waits until the subscription is established, so a caller can be sure the
     * topic will not drop an event published before this subscriber existed.
     *
     * @return whether the subscriber connected within the timeout
     */
    public boolean awaitConnected(Duration timeout) {
        try {
            return connected.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void run() {
        try {
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
        } finally {
            running = false;
            stopped.countDown();
        }
    }

    private void subscribeAndBlock() throws JMSException, InterruptedException {
        try (Connection conn = factory.createConnection()) {
            connection = conn;
            conn.setClientID(clientId);
            conn.start();
            if (closed) {
                return;
            }
            try (Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                Topic topic = session.createTopic(MqConfig.TOPIC);
                try (MessageConsumer consumer = session.createDurableConsumer(topic, subscriptionName)) {
                    consumer.setMessageListener(this::onMessage);
                    running = true;
                    connected.countDown();
                    log.info("subscribed to topic {} (durable, client {}) at {}",
                            MqConfig.TOPIC, clientId, MqConfig.BROKER_URL);
                    while (!closed) {
                        Thread.sleep(500);
                    }
                }
            }
        } finally {
            running = false;
            connection = null;
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

    /**
     * Stops the subscriber and releases the broker connection, so the client id
     * is free for an immediate reconnect and a durable subscription keeps the
     * events published while nobody was listening.
     */
    @Override
    public void close() {
        closed = true;
        Connection open = connection;
        if (open != null) {
            try {
                open.close();
            } catch (JMSException e) {
                log.warn("could not close the staffing topic connection: {}", e.getMessage());
            }
        }
        if (started) {
            try {
                if (!stopped.await(STOP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    log.warn("staffing topic subscriber did not stop within {}ms", STOP_TIMEOUT_MILLIS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
