package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertProcessorTest {

    private static final String PAYLOAD = """
            {"wardId":"W-05","wing":"East Wing","equipment":"Ventilator-3",
             "detail":"not powering on","detectedAt":"2026-10-19T08:00:00Z"}""";

    private final AlertProcessor processor = new AlertProcessor();

    @Test
    void validPayloadIsRecordedAndReportedAsHandled() {
        assertTrue(processor.process(PAYLOAD));

        assertEquals(1, processor.processed().size());
        EquipmentFailureAlert alert = processor.processed().get(0);
        assertEquals("W-05", alert.wardId());
        assertEquals("East Wing", alert.wing());
        assertEquals("Ventilator-3", alert.equipment());
        assertEquals("not powering on", alert.detail());
    }

    @Test
    void unknownFieldsInThePayloadAreIgnored() {
        assertTrue(processor.process(PAYLOAD.replaceFirst("\\{", "{\"extra\":123,")));
        assertEquals(1, processor.processed().size());
    }

    @Test
    void malformedPayloadIsNotHandledAndNotRecorded() {
        assertFalse(processor.process("{not json"));
        assertTrue(processor.processed().isEmpty());
    }

    @Test
    void returnsACopySoCallersCannotMutateTheRecordedAlerts() {
        processor.process(PAYLOAD);
        processor.processed().clear();
        assertEquals(1, processor.processed().size());
    }
}
