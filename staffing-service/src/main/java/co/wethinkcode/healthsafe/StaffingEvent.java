package co.wethinkcode.healthsafe;

import java.util.List;

/**
 * Broadcast (stage 3) sent over the {@code staffing-events-topic} whenever a
 * ward's on-call roster changes. A topic is used because this is a
 * fire-and-forget notification that any number of subscribers (currently
 * ward-service) may want, without staffing-service needing to know who they are.
 */
public record StaffingEvent(
        String type,
        String wardId,
        String wing,
        String department,
        int alertLevel,
        int requiredDoctors,
        List<OnCallSchedule.Slot> onCall,
        List<String> doctors,
        String timestamp) {

    public static final String ROSTER_UPDATED = "ROSTER_UPDATED";
}
