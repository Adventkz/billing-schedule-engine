package kz.yestai.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BillingCycleTest {

    private static final LocalDate JAN_31 = LocalDate.of(2026, 1, 31);

    @Test
    @DisplayName("a 31st anchor clamps in February and returns to the 31st in March")
    void monthEndAnchorDoesNotDegrade() {
        assertEquals(LocalDate.of(2026, 1, 31), BillingCycle.MONTHLY.boundary(JAN_31, 0));
        assertEquals(LocalDate.of(2026, 2, 28), BillingCycle.MONTHLY.boundary(JAN_31, 1));
        assertEquals(LocalDate.of(2026, 3, 31), BillingCycle.MONTHLY.boundary(JAN_31, 2));
        assertEquals(LocalDate.of(2026, 4, 30), BillingCycle.MONTHLY.boundary(JAN_31, 3));
        assertEquals(LocalDate.of(2026, 5, 31), BillingCycle.MONTHLY.boundary(JAN_31, 4));
    }

    @Test
    void clampsToFebruary29InALeapYear() {
        LocalDate anchor = LocalDate.of(2028, 1, 31);

        assertEquals(LocalDate.of(2028, 2, 29), BillingCycle.MONTHLY.boundary(anchor, 1));
    }

    @Test
    void yearlyClampsLeapDay() {
        LocalDate leapDay = LocalDate.of(2028, 2, 29);

        assertEquals(LocalDate.of(2029, 2, 28), BillingCycle.YEARLY.boundary(leapDay, 1));
        assertEquals(LocalDate.of(2032, 2, 29), BillingCycle.YEARLY.boundary(leapDay, 4));
    }

    @Test
    void weeklyAndQuarterlyBoundaries() {
        LocalDate anchor = LocalDate.of(2026, 1, 1);

        assertEquals(LocalDate.of(2026, 1, 22), BillingCycle.WEEKLY.boundary(anchor, 3));
        assertEquals(LocalDate.of(2026, 7, 1), BillingCycle.QUARTERLY.boundary(anchor, 2));
    }

    @Test
    void indexOfIsCorrectAroundClampedMonthEnds() {
        assertEquals(0, BillingCycle.MONTHLY.indexOf(JAN_31, LocalDate.of(2026, 2, 27)));
        assertEquals(1, BillingCycle.MONTHLY.indexOf(JAN_31, LocalDate.of(2026, 2, 28)));
        assertEquals(1, BillingCycle.MONTHLY.indexOf(JAN_31, LocalDate.of(2026, 3, 30)));
        assertEquals(2, BillingCycle.MONTHLY.indexOf(JAN_31, LocalDate.of(2026, 3, 31)));
    }

    @Test
    void indexOfIsNegativeBeforeTheAnchor() {
        assertEquals(-1, BillingCycle.MONTHLY.indexOf(JAN_31, LocalDate.of(2026, 1, 1)));
        assertEquals(-1, BillingCycle.MONTHLY.indexOf(JAN_31, LocalDate.of(2025, 12, 31)));
        assertEquals(-2, BillingCycle.MONTHLY.indexOf(JAN_31, LocalDate.of(2025, 12, 30)));
    }

    @Test
    void periodContainingIsHalfOpen() {
        BillingPeriod period = BillingCycle.MONTHLY.periodContaining(JAN_31, LocalDate.of(2026, 3, 15));

        assertEquals(LocalDate.of(2026, 2, 28), period.start());
        assertEquals(LocalDate.of(2026, 3, 31), period.endExclusive());
        assertEquals(LocalDate.of(2026, 3, 30), period.lastDay());
        assertEquals(31, period.days());
    }
}
