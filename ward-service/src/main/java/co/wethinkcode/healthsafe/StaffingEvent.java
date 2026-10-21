package co.wethinkcode.healthsafe;

import java.util.List;

/**
 * A staffing update received from the {@code staffing-events-topic}. Mirrors
 * staffing-service's event shape; duplicated because each service is an
 * independent Maven project with no shared module.
 */
public record StaffingEvent(
        String type,
        String wardId,
        String wing,
        String department,
        int alertLevel,
        int requiredDoctors,
        List<Slot> onCall,
        List<String> doctors,
        String timestamp) {

    public record Slot(String shift, String role) {
    }
}
