package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleCalculatorTest {

    private static final WardServiceClient.WardSummary WARD =
            new WardServiceClient.WardSummary("W-05", "East Wing", "Paediatrics", 5);

    private final ScheduleCalculator calculator = new ScheduleCalculator();

    @Test
    void headcountGrowsWithEmergencyStatus() {
        assertEquals(1, ScheduleCalculator.headcountFor(0));
        assertEquals(2, ScheduleCalculator.headcountFor(1));
        assertEquals(3, ScheduleCalculator.headcountFor(3));
        assertEquals(4, ScheduleCalculator.headcountFor(6));
        assertEquals(5, ScheduleCalculator.headcountFor(8));
    }

    @Test
    void countIsMonotonicAcrossTheFullRange() {
        int previous = 0;
        for (int level = 0; level <= 8; level++) {
            int headcount = ScheduleCalculator.headcountFor(level);
            assertTrue(headcount >= previous, "headcount shrank at level " + level);
            assertTrue(headcount >= 1);
            previous = headcount;
        }
    }

    @Test
    void scheduleCarriesWardAndStatusAndOneSlotPerRequiredDoctor() {
        OnCallSchedule schedule = calculator.compute(WARD, 8, "2026-10-19T00:00:00Z");

        assertEquals("W-05", schedule.wardId());
        assertEquals("East Wing", schedule.wing());
        assertEquals("Paediatrics", schedule.department());
        assertEquals(8, schedule.alertLevel());
        assertEquals(5, schedule.requiredDoctors());
        assertEquals(5, schedule.onCall().size());
        assertEquals("2026-10-19T00:00:00Z", schedule.computedAt());
    }

    @Test
    void slotsAlternateDayAndNightShiftsAndNameTheDepartment() {
        OnCallSchedule schedule = calculator.compute(WARD, 3, "now");

        assertEquals("08:00-20:00", schedule.onCall().get(0).shift());
        assertEquals("20:00-08:00", schedule.onCall().get(1).shift());
        assertTrue(schedule.onCall().get(0).role().contains("Paediatrics"));
        assertTrue(schedule.onCall().get(2).role().contains("ICU"));
    }

    @Test
    void handlesMissingDepartmentWithoutCrashing() {
        WardServiceClient.WardSummary noDept =
                new WardServiceClient.WardSummary("W-08", null, null, null);

        OnCallSchedule schedule = calculator.compute(noDept, 0, "now");

        assertEquals("General registrar (on-call lead)", schedule.onCall().get(0).role());
    }
}
