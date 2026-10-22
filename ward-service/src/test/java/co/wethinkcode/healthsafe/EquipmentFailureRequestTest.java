package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EquipmentFailureRequestTest {

    @Test
    void parsesEquipmentAndDetail() {
        EquipmentFailureRequest request = EquipmentFailureRequest.parse(
                "{\"equipment\":\"Ventilator-3\",\"detail\":\"not powering on\"}");

        assertEquals("Ventilator-3", request.equipment());
        assertEquals("not powering on", request.detail());
    }

    @Test
    void detailIsOptionalAndDefaultsToEmpty() {
        assertEquals("", EquipmentFailureRequest.parse("{\"equipment\":\"Pump\"}").detail());
    }

    @Test
    void trimsSurroundingWhitespaceOnTheEquipmentName() {
        assertEquals("Pump", EquipmentFailureRequest.parse("{\"equipment\":\"  Pump  \"}").equipment());
    }

    @Test
    void rejectsMissingOrBlankEquipment() {
        assertThrows(IllegalArgumentException.class,
                () -> EquipmentFailureRequest.parse("{\"detail\":\"x\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> EquipmentFailureRequest.parse("{\"equipment\":\"   \"}"));
    }

    @Test
    void rejectsNonTextualEquipment() {
        assertThrows(IllegalArgumentException.class,
                () -> EquipmentFailureRequest.parse("{\"equipment\":42}"));
    }

    @Test
    void rejectsBlankOrMalformedBodies() {
        assertThrows(IllegalArgumentException.class, () -> EquipmentFailureRequest.parse(null));
        assertThrows(IllegalArgumentException.class, () -> EquipmentFailureRequest.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> EquipmentFailureRequest.parse("{not json"));
    }
}
