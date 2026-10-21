package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RosterRequestTest {

    @Test
    void parsesTheDoctorList() {
        assertEquals(List.of("Dr A", "Dr B"),
                RosterRequest.parseDoctors("{\"doctors\":[\"Dr A\",\"Dr B\"]}"));
    }

    @Test
    void trimsWhitespaceAroundDoctorNames() {
        assertEquals(List.of("Dr A"),
                RosterRequest.parseDoctors("{\"doctors\":[\"  Dr A  \"]}"));
    }

    @Test
    void rejectsBlankOrMissingBody() {
        assertThrows(IllegalArgumentException.class, () -> RosterRequest.parseDoctors(null));
        assertThrows(IllegalArgumentException.class, () -> RosterRequest.parseDoctors("   "));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> RosterRequest.parseDoctors("{not json"));
    }

    @Test
    void rejectsMissingOrEmptyDoctorArray() {
        assertThrows(IllegalArgumentException.class, () -> RosterRequest.parseDoctors("{}"));
        assertThrows(IllegalArgumentException.class, () -> RosterRequest.parseDoctors("{\"doctors\":[]}"));
    }

    @Test
    void rejectsBlankOrNonTextualNames() {
        assertThrows(IllegalArgumentException.class,
                () -> RosterRequest.parseDoctors("{\"doctors\":[\"Dr A\",\"  \"]}"));
        assertThrows(IllegalArgumentException.class,
                () -> RosterRequest.parseDoctors("{\"doctors\":[42]}"));
    }
}
