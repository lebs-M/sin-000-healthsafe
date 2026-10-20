package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlertLevelServiceAppTest {

    @Test
    void acceptsLevelsInRange() {
        assertEquals(0, AlertLevelServiceApp.parseLevel("{\"level\":0}"));
        assertEquals(5, AlertLevelServiceApp.parseLevel("{\"level\": 5}"));
        assertEquals(8, AlertLevelServiceApp.parseLevel("{\"level\":8}"));
    }

    @Test
    void rejectsOutOfRangeLevels() {
        assertThrows(IllegalArgumentException.class, () -> AlertLevelServiceApp.parseLevel("{\"level\":-1}"));
        assertThrows(IllegalArgumentException.class, () -> AlertLevelServiceApp.parseLevel("{\"level\":9}"));
        assertThrows(IllegalArgumentException.class, () -> AlertLevelServiceApp.parseLevel("{\"level\":999}"));
    }

    @Test
    void rejectsMissingOrNonIntegerLevels() {
        assertThrows(IllegalArgumentException.class, () -> AlertLevelServiceApp.parseLevel("{}"));
        assertThrows(IllegalArgumentException.class, () -> AlertLevelServiceApp.parseLevel("{\"level\":\"five\"}"));
        assertThrows(IllegalArgumentException.class, () -> AlertLevelServiceApp.parseLevel("{\"level\":4.5}"));
    }

    @Test
    void rejectsBlankOrMalformedBodies() {
        assertThrows(IllegalArgumentException.class, () -> AlertLevelServiceApp.parseLevel(null));
        assertThrows(IllegalArgumentException.class, () -> AlertLevelServiceApp.parseLevel(""));
        assertThrows(IllegalArgumentException.class, () -> AlertLevelServiceApp.parseLevel("not json"));
    }
}
