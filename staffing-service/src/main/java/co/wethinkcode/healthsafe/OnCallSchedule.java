package co.wethinkcode.healthsafe;

import java.util.List;

/** A computed on-call roster for one ward, sized by the current Emergency Status. */
public record OnCallSchedule(
        String wardId,
        String wing,
        String department,
        int alertLevel,
        int requiredDoctors,
        List<Slot> onCall,
        String computedAt) {

    public record Slot(String shift, String role) {
    }
}
