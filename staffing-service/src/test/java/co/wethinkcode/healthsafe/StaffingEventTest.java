package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffingEventTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundTripsThroughJsonAsSentOnTheTopic() throws Exception {
        StaffingEvent event = new StaffingEvent(
                StaffingEvent.ROSTER_UPDATED,
                "W-05", "East Wing", "Paediatrics",
                5, 3,
                List.of(new OnCallSchedule.Slot("08:00-20:00", "Paediatrics registrar (on-call lead)")),
                List.of("Dr A", "Dr B"),
                "2026-10-19T08:00:00Z");

        String json = mapper.writeValueAsString(event);
        StaffingEvent back = mapper.readValue(json, StaffingEvent.class);

        assertEquals(event, back);
        assertTrue(json.contains("\"type\":\"ROSTER_UPDATED\""));
        assertTrue(json.contains("\"wardId\":\"W-05\""));
    }
}
