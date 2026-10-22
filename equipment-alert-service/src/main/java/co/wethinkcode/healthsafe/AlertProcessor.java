package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses and records equipment-failure alert payloads.
 *
 * Deliberately free of JMS so the "process, then acknowledge" decision can be
 * unit-tested without a broker: {@link #process(String)} reports whether the
 * payload was handled, and {@link EquipmentAlertConsumer} only acknowledges the
 * message when it returns {@code true}. Anything else is left unacknowledged so
 * the broker redelivers it (at-least-once delivery).
 */
public final class AlertProcessor {

    private static final Logger log = LoggerFactory.getLogger(AlertProcessor.class);

    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final List<EquipmentFailureAlert> processed = Collections.synchronizedList(new ArrayList<>());

    public boolean process(String payload) {
        try {
            EquipmentFailureAlert alert = mapper.readValue(payload, EquipmentFailureAlert.class);
            processed.add(alert);
            log.error("EQUIPMENT FAILURE ALERT: ward {} ({}) — {} [{}]",
                    alert.wardId(), alert.wing(), alert.equipment(), alert.detail());
            return true;
        } catch (Exception e) {
            log.warn("could not parse alert payload, leaving it for redelivery: {}", e.getMessage());
            return false;
        }
    }

    public List<EquipmentFailureAlert> processed() {
        synchronized (processed) {
            return new ArrayList<>(processed);
        }
    }
}
