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
 * Publishes equipment-failure alerts to {@code equipment-failure-queue}.
 *
 * A queue (point-to-point, single consumer, at-least-once) is the deliberate
 * choice here: an equipment failure must be delivered to exactly one alerting
 * consumer and not lost, even if that consumer is briefly down. That is the
 * opposite trade-off from the broadcast staffing-events-topic.
 *
 * Messages are PERSISTENT so they survive a broker restart and are redelivered
 * if the consumer fails to acknowledge them.
 */
public final class EquipmentFailurePublisher {

    private final ActiveMQConnectionFactory factory;
    private final ObjectMapper mapper = new ObjectMapper();

    public EquipmentFailurePublisher() {
        this(MqConfig.BROKER_URL);
    }

    EquipmentFailurePublisher(String brokerUrl) {
        this.factory = new ActiveMQConnectionFactory(brokerUrl);
    }

    public void publish(EquipmentFailureAlert alert) {
        try (Connection connection = factory.createConnection()) {
            connection.start();
            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                 MessageProducer producer = session.createProducer(session.createQueue(MqConfig.QUEUE))) {
                producer.setDeliveryMode(DeliveryMode.PERSISTENT);
                TextMessage message = session.createTextMessage(mapper.writeValueAsString(alert));
                producer.send(message);
            }
        } catch (JMSException | JsonProcessingException e) {
            throw new BrokerUnavailableException(
                    "could not publish to " + MqConfig.QUEUE + ": " + e.getMessage());
        }
    }
}
