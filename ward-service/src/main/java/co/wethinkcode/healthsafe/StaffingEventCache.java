package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the latest staffing event seen per ward.
 *
 * Extracted from {@link StaffingEventSubscriber} and kept free of JMS so the
 * per-ward "latest wins" behaviour can be unit-tested without a broker. Ward ids
 * are normalised to upper case so a lookup is case-insensitive.
 */
public final class StaffingEventCache {

    private static final Logger log = LoggerFactory.getLogger(StaffingEventCache.class);

    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final Map<String, StaffingEvent> latestByWard = new ConcurrentHashMap<>();

    /** Parses a topic payload and caches it; returns whether it was accepted. */
    public boolean accept(String payload) {
        try {
            return accept(mapper.readValue(payload, StaffingEvent.class));
        } catch (Exception e) {
            log.warn("could not process staffing event: {}", e.getMessage());
            return false;
        }
    }

    public boolean accept(StaffingEvent event) {
        if (event == null || event.wardId() == null) {
            log.warn("ignoring staffing event with no wardId");
            return false;
        }
        latestByWard.put(event.wardId().trim().toUpperCase(Locale.ROOT), event);
        log.info("received staffing event {} for ward {} (status {})",
                event.type(), event.wardId(), event.alertLevel());
        return true;
    }

    public Optional<StaffingEvent> latestFor(String wardId) {
        if (wardId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(latestByWard.get(wardId.trim().toUpperCase(Locale.ROOT)));
    }
}
