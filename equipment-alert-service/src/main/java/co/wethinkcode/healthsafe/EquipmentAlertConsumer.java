package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.List;

/**
 * Guaranteed-delivery consumer for {@code equipment-failure-queue}.
 *
 * Uses CLIENT_ACKNOWLEDGE: a message is only acknowledged after the
 * {@link AlertProcessor} reports it was fully processed, so a crash or failure
 * mid-processing leaves it on the queue for redelivery rather than silently
 * dropping a critical alert (at-least-once). Combined with the publisher's
 * PERSISTENT delivery, alerts survive broker restarts and a consumer that is
 * briefly down. The connection is established on a background thread that
 * retries, so the service starts even if the broker is not yet up.
 *
 * A dropped broker connection is only reported to the connection's exception
 * listener, not thrown at the thread parked in the consume loop, so the
 * listener flags it and the loop reconnects instead of waiting forever on a
 * dead connection.
 */
public final class EquipmentAlertConsumer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EquipmentAlertConsumer.class);
    private static final long RETRY_MILLIS = 10_000;

    private final ActiveMQConnectionFactory factory;
    private final AlertProcessor processor = new AlertProcessor();

    private volatile boolean closed;
    private volatile boolean connectionLost;

    public EquipmentAlertConsumer() {
        this(MqConfig.BROKER_URL);
    }

    EquipmentAlertConsumer(String brokerUrl) {
        this.factory = new ActiveMQConnectionFactory(brokerUrl);
    }

    public void start() {
        Thread consumer = new Thread(this::run, "equipment-alert-consumer");
        consumer.setDaemon(true);
        consumer.start();
    }

    private void run() {
        while (!closed) {
            try {
                consumeAndBlock();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (JMSException e) {
                log.warn("equipment alert consumer cannot reach {} ({}); retrying in {}s",
                        MqConfig.BROKER_URL, e.getMessage(), RETRY_MILLIS / 1000);
            }
            if (!closed) {
                sleep(RETRY_MILLIS);
            }
        }
    }

    private void consumeAndBlock() throws JMSException, InterruptedException {
        connectionLost = false;
        try (Connection connection = factory.createConnection()) {
            connection.setExceptionListener(e -> {
                log.warn("equipment alert consumer lost {} ({}); reconnecting",
                        MqConfig.BROKER_URL, e.getMessage());
                connectionLost = true;
            });
            connection.start();
            try (Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)) {
                Queue queue = session.createQueue(MqConfig.QUEUE);
                try (MessageConsumer consumer = session.createConsumer(queue)) {
                    consumer.setMessageListener(this::onMessage);
                    log.info("consuming queue {} (client-ack) at {}", MqConfig.QUEUE, MqConfig.BROKER_URL);
                    while (!closed && !connectionLost) {
                        Thread.sleep(500);
                    }
                }
            }
        }
    }

    private void onMessage(Message message) {
        try {
            if (message instanceof TextMessage text && !processor.process(text.getText())) {
                // Processing failed: fall through without acking so the broker redelivers.
                return;
            }
            message.acknowledge();
        } catch (JMSException e) {
            log.warn("could not acknowledge message: {}", e.getMessage());
        }
    }

    public List<EquipmentFailureAlert> processed() {
        return processor.processed();
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
