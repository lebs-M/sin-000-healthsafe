package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffingEventCacheTest {

    private final StaffingEventCache cache = new StaffingEventCache();

    private static String payload(String wardId, int alertLevel) {
        return """
                {"type":"ROSTER_UPDATED","wardId":"%s","wing":"East Wing","department":"Paediatrics",
                 "alertLevel":%d,"requiredDoctors":2,
                 "onCall":[{"shift":"08:00-20:00","role":"Paediatrics registrar (on-call lead)"}],
                 "doctors":["Dr A"],"timestamp":"2026-10-19T08:00:00Z"}""".formatted(wardId, alertLevel);
    }

    @Test
    void parsesAndCachesAValidEvent() {
        assertTrue(cache.accept(payload("W-05", 3)));

        Optional<StaffingEvent> event = cache.latestFor("W-05");
        assertTrue(event.isPresent());
        assertEquals("ROSTER_UPDATED", event.get().type());
        assertEquals(3, event.get().alertLevel());
        assertEquals("08:00-20:00", event.get().onCall().get(0).shift());
    }

    @Test
    void lookupIsCaseInsensitiveAndTrimsWhitespace() {
        cache.accept(payload("W-05", 5));

        assertTrue(cache.latestFor("w-05").isPresent());
        assertTrue(cache.latestFor("  W-05 ").isPresent());
        assertTrue(cache.latestFor("W-99").isEmpty());
        assertTrue(cache.latestFor(null).isEmpty());
    }

    @Test
    void aLaterEventForTheSameWardReplacesTheEarlierOne() {
        cache.accept(payload("W-05", 1));
        cache.accept(payload("W-05", 8));

        assertEquals(8, cache.latestFor("W-05").orElseThrow().alertLevel());
    }

    @Test
    void malformedPayloadIsRejectedAndNotCached() {
        assertFalse(cache.accept("{not json"));
        assertTrue(cache.latestFor("W-05").isEmpty());
    }

    @Test
    void eventWithoutAWardIdIsRejected() {
        StaffingEvent noWard = new StaffingEvent(
                "ROSTER_UPDATED", null, "East Wing", "Paediatrics", 3, 2, List.of(), List.of(), "now");
        assertFalse(cache.accept(noWard));
    }
}
