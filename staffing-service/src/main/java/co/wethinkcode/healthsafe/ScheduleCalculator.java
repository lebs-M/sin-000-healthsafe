package co.wethinkcode.healthsafe;

import java.util.ArrayList;
import java.util.List;

/**
 * Sizes an on-call roster from the ward's department and the current Emergency
 * Status. Until real doctor/personnel data exists this names the required
 * positions rather than individuals; the roster is deterministic so it is easy
 * to unit-test and to explain to ward staff.
 */
public final class ScheduleCalculator {

    private static final String DAY_SHIFT = "08:00-20:00";
    private static final String NIGHT_SHIFT = "20:00-08:00";

    public OnCallSchedule compute(WardServiceClient.WardSummary ward, int alertLevel, String computedAt) {
        int headcount = headcountFor(alertLevel);
        List<OnCallSchedule.Slot> slots = new ArrayList<>();
        for (int i = 0; i < headcount; i++) {
            slots.add(new OnCallSchedule.Slot(
                    i % 2 == 0 ? DAY_SHIFT : NIGHT_SHIFT,
                    roleFor(i, ward.department())));
        }
        return new OnCallSchedule(
                ward.wardId(),
                ward.wing(),
                ward.department(),
                alertLevel,
                headcount,
                slots,
                computedAt);
    }

    /** Higher Emergency Status means more doctors pulled onto the ward. */
    static int headcountFor(int alertLevel) {
        if (alertLevel >= 8) {
            return 5; // full Code Blue — all available hands
        }
        if (alertLevel >= 6) {
            return 4;
        }
        if (alertLevel >= 3) {
            return 3;
        }
        if (alertLevel >= 1) {
            return 2;
        }
        return 1;
    }

    private static String roleFor(int index, String department) {
        String specialty = department == null ? "General" : department;
        return switch (index) {
            case 0 -> specialty + " registrar (on-call lead)";
            case 1 -> specialty + " resident";
            case 2 -> "ICU outreach nurse";
            case 3 -> "Anaesthetics registrar";
            default -> specialty + " consultant";
        };
    }
}
