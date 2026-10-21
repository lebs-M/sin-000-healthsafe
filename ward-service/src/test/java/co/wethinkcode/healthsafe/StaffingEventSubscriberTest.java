package co.wethinkcode.healthsafe;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test for the stage 3 flow: a roster published to the
 * {@code staffing-events-topic} has to end up being served by the ward
 * catalogue ({@code GET /wards/{id}/staffing}).
 *
 * Needs a real broker - {@code common/docker-compose.yml} provides one on
 * tcp://localhost:61616. The test is skipped when no broker is reachable, so
 * {@code mvn test} stays green on a machine without one.
 */
class StaffingEventSubscriberTest {

    private static final String BROKER_URL =
            System.getenv().getOrDefault("MQ_BROKER_URL", "tcp://localhost:61616");
    private static final String TOPIC =
            System.getenv().getOrDefault("MQ_TOPIC", "staffing-events-topic");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(20);

    private String clientId;
    private StaffingEventSubscriber subscriber;

    @BeforeAll
    static void aBrokerMustBeRunning() {
        assumeTrue(brokerIsUp(), "no ActiveMQ broker at " + BROKER_URL + " - skipping");
    }

    @BeforeEach
    void freshClientId() {
        clientId = "ward-service-test-" + Instant.now().toEpochMilli();
    }

    @AfterEach
    void stopSubscriber() {
        if (subscriber != null) {
            subscriber.close();
        }
    }

    @Test
    void aPublishedRosterIsServedByTheWardCatalogue() {
        startSubscriber();

        publish(roster("W-05", 3));

        StaffingEvent event = awaitEvent("W-05");
        assertEquals("ROSTER_UPDATED", event.type());
        assertEquals("W-05", event.wardId());
        assertEquals(3, event.alertLevel());
        assertEquals("08:00-20:00", event.onCall().get(0).shift());
    }

    @Test
    void aLaterRosterSupersedesTheEarlierOne() {
        startSubscriber();

        publish(roster("W-05", 1));
        publish(roster("W-05", 8));

        assertEquals(8, awaitEvent("W-05").alertLevel());
    }

    @Test
    void theWardCatalogueLooksUpWardsCaseInsensitively() {
        startSubscriber();
        publish(roster("W-05", 5));

        assertTrue(subscriber.latestFor("w-05").isPresent());
        assertTrue(subscriber.latestFor("  W-05 ").isPresent());
        assertTrue(subscriber.latestFor("W-99").isEmpty());
    }

    @Test
    void aMalformedPayloadIsIgnoredAndTheSubscriberKeepsRunning() {
        startSubscriber();

        publish("{not json");

        assertTrue(subscriber.isConnected(), "subscriber should survive a malformed payload");
        assertTrue(subscriber.latestFor("W-05").isEmpty());
    }

    @Test
    void aRosterPublishedWhileTheSubscriberIsDownIsDeliveredWhenItReturns() {
        StaffingEventSubscriber first = startSubscriber();
        first.close();

        publish(roster("W-07", 4));

        subscriber = new StaffingEventSubscriber(BROKER_URL, clientId, "ward-service-staffing");
        subscriber.start();
        assertTrue(subscriber.awaitConnected(CONNECT_TIMEOUT),
                "subscriber should reconnect to " + BROKER_URL);

        StaffingEvent event = awaitEvent("W-07");
        assertEquals(4, event.alertLevel());
    }

    private void startSubscriber() {
        subscriber = new StaffingEventSubscriber(BROKER_URL, clientId, "ward-service-staffing");
        subscriber.start();
        assertTrue(subscriber.awaitConnected(CONNECT_TIMEOUT),
                "subscriber should connect to " + BROKER_URL);
    }

    private StaffingEvent awaitEvent(String wardId) {
        long deadline = System.currentTimeMillis() + EVENT_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Optional<StaffingEvent> event = subscriber.latestFor(wardId);
            if (event.isPresent()) {
                return event.get();
            }
            sleep(100);
        }
        throw new AssertionError("no staffing event for " + wardId + " within " + EVENT_TIMEOUT);
    }

    private void publish(String payload) {
        try (Connection connection = new ActiveMQConnectionFactory(BROKER_URL).createConnection()) {
            connection.start();
            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                 MessageProducer producer = session.createProducer(session.createTopic(TOPIC))) {
                producer.setDeliveryMode(DeliveryMode.PERSISTENT);
                TextMessage message = session.createTextMessage(payload);
                producer.send(message);
            }
        } catch (JMSException e) {
            throw new AssertionError("could not publish to " + TOPIC, e);
        }
    }

    private static String roster(String wardId, int alertLevel) {
        return """
                {"type":"ROSTER_UPDATED","wardId":"%s","wing":"East Wing","department":"Paediatrics",
                 "alertLevel":%d,"requiredDoctors":2,
                 "onCall":[{"shift":"08:00-20:00","role":"Paediatrics registrar (on-call lead)"}],
                 "doctors":["Dr A"],"timestamp":"%s"}"""
                .formatted(wardId, alertLevel, Instant.now().toString());
    }

    private static boolean brokerIsUp() {
        try (Connection connection = new ActiveMQConnectionFactory(BROKER_URL).createConnection()) {
            connection.start();
            return true;
        } catch (JMSException e) {
            return false;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
