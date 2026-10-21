package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import co.wethinkcode.healthsafe.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

/**
 * Publishes staffing updates to the {@code staffing-events-topic}.
 *
 * A topic (broadcast, fire-and-forget) is the deliberate choice here: staffing
 * changes are events of interest to any number of subscribers, and
 * staffing-service should not need to know who they are or block if one is
 * slow/down. Contrast with the equipment-failure-queue, where each alert must
 * be delivered to exactly one consumer and processed at least once.
 *
 * Messages are PERSISTENT so a subscriber that is briefly disconnected still
 * receives them (the consumer here is a durable subscription).
 */
public final class StaffingPublisher {

    private final ActiveMQConnectionFactory factory;
    private final ObjectMapper mapper = new ObjectMapper();

    public StaffingPublisher() {
        this(MqConfig.BROKER_URL);
    }

    StaffingPublisher(String brokerUrl) {
        this.factory = new ActiveMQConnectionFactory(brokerUrl);
    }

    public void publish(StaffingEvent event) {
        try (Connection connection = factory.createConnection()) {
            connection.start();
            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                 MessageProducer producer = session.createProducer(session.createTopic(MqConfig.TOPIC))) {
                producer.setDeliveryMode(DeliveryMode.PERSISTENT);
                TextMessage message = session.createTextMessage(mapper.writeValueAsString(event));
                producer.send(message);
            }
        } catch (JMSException | JsonProcessingException e) {
            throw new DownstreamUnavailableException(
                    "could not publish staffing event to " + MqConfig.TOPIC + ": " + e.getMessage());
        }
    }
}
