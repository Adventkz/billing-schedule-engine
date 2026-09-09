package kz.yestai.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BillingScheduleEngineTest {

    private static final Currency KZT = Currency.getInstance("KZT");
    private static final Money PRICE = Money.of("30000.00", "KZT");

    private final BillingScheduleEngine engine = new BillingScheduleEngine();

    private static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    @Test
    void billsWholePeriodsAtFullPrice() {
        Subscription subscription = Subscription.monthly("sub-1", PRICE, d(2026, 1, 1));

        List<ChargeLine> lines = engine.schedule(subscription, d(2026, 4, 1));

        assertEquals(3, lines.size());
        assertTrue(lines.stream().noneMatch(ChargeLine::prorated));
        assertTrue(lines.stream().allMatch(l -> l.amount().equals(PRICE)));
        assertEquals(d(2026, 1, 1), lines.get(0).period().start());
        assertEquals(d(2026, 3, 1), lines.get(2).period().start());
        assertEquals(Money.of("90000.00", "KZT"), BillingScheduleEngine.total(lines, Money.zero(KZT)));
    }

    @Test
    @DisplayName("a mid-month start is prorated by day against a fixed invoicing day")
    void proratesTheFirstPartialPeriod() {
        Subscription subscription = new Subscription(
                "sub-2", PRICE, BillingCycle.MONTHLY, d(2026, 1, 1), d(2026, 1, 15), null, ProrationPolicy.DAILY);

        List<ChargeLine> lines = engine.schedule(subscription, d(2026, 4, 1));

        assertEquals(3, lines.size());
        // 17 of January's 31 days.
        assertEquals(Money.of("16451.61", "KZT"), lines.get(0).amount());
        assertTrue(lines.get(0).prorated());
        assertEquals(d(2026, 1, 15), lines.get(0).period().start());
        assertEquals(d(2026, 1, 1), lines.get(0).fullPeriod().start());
        assertEquals(PRICE, lines.get(1).amount());
        assertFalse(lines.get(1).prorated());
    }

    @Test
    void proratesTheFinalPeriodOnCancellation() {
        Subscription subscription =
                Subscription.monthly("sub-3", PRICE, d(2026, 1, 1)).endingOn(d(2026, 3, 10));

        List<ChargeLine> lines = engine.schedule(subscription, d(2026, 12, 1));

        assertEquals(3, lines.size());
        // 9 of March's 31 days.
        assertEquals(Money.of("8709.68", "KZT"), lines.get(2).amount());
        assertEquals(d(2026, 3, 9), lines.get(2).period().lastDay());
        assertEquals(Money.of("68709.68", "KZT"), BillingScheduleEngine.total(lines, Money.zero(KZT)));
    }

    @Test
    void fullPricePolicyChargesPartialPeriodsInFull() {
        Subscription subscription = new Subscription(
                "sub-4",
                PRICE,
                BillingCycle.MONTHLY,
                d(2026, 1, 1),
                d(2026, 1, 15),
                null,
                ProrationPolicy.FULL_PRICE);

        List<ChargeLine> lines = engine.schedule(subscription, d(2026, 3, 1));

        assertEquals(2, lines.size());
        assertEquals(PRICE, lines.get(0).amount());
        assertFalse(lines.get(0).prorated());
    }

    @Test
    void wholePeriodsOnlyPolicySkipsPartialPeriods() {
        Subscription subscription = new Subscription(
                "sub-5",
                PRICE,
                BillingCycle.MONTHLY,
                d(2026, 1, 1),
                d(2026, 1, 15),
                d(2026, 3, 20),
                ProrationPolicy.WHOLE_PERIODS_ONLY);

        List<ChargeLine> lines = engine.schedule(subscription, d(2026, 12, 1));

        assertEquals(1, lines.size());
        assertEquals(d(2026, 2, 1), lines.get(0).period().start());
    }

    @Test
    void monthEndAnchorKeepsBillingOnThe31st() {
        Subscription subscription = Subscription.monthly("sub-6", PRICE, d(2026, 1, 31));

        List<ChargeLine> lines = engine.schedule(subscription, d(2026, 4, 30));

        assertEquals(
                List.of(d(2026, 1, 31), d(2026, 2, 28), d(2026, 3, 31)),
                lines.stream().map(l -> l.period().start()).toList());
        assertTrue(lines.stream().noneMatch(ChargeLine::prorated));
    }

    @Test
    void yearlySubscription() {
        Subscription subscription = new Subscription(
                "sub-7",
                Money.of("300000.00", "KZT"),
                BillingCycle.YEARLY,
                d(2026, 2, 1),
                d(2026, 2, 1),
                null,
                ProrationPolicy.DAILY);

        List<ChargeLine> lines = engine.schedule(subscription, d(2029, 1, 1));

        assertEquals(3, lines.size());
        assertEquals(d(2028, 2, 1), lines.get(2).period().start());
    }

    @Test
    void returnsNothingWhenTheWindowEndsBeforeServiceStarts() {
        Subscription subscription = Subscription.monthly("sub-8", PRICE, d(2026, 5, 1));

        assertEquals(List.of(), engine.schedule(subscription, d(2026, 5, 1)));
        assertEquals(List.of(), engine.schedule(subscription, d(2026, 1, 1)));
    }

    @Test
    void windowClipsTheLastPeriod() {
        Subscription subscription = Subscription.monthly("sub-9", PRICE, d(2026, 1, 1));

        List<ChargeLine> lines = engine.schedule(subscription, d(2026, 3, 15));

        assertEquals(3, lines.size());
        assertEquals(d(2026, 3, 14), lines.get(2).period().lastDay());
        assertTrue(lines.get(2).prorated());
    }

    @Test
    void scheduleIsDeterministic() {
        Subscription subscription = Subscription.monthly("sub-10", PRICE, d(2026, 1, 17));

        assertEquals(
                engine.schedule(subscription, d(2027, 1, 1)), engine.schedule(subscription, d(2027, 1, 1)));
    }

    @Test
    void rejectsImpossibleSubscriptions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Subscription.monthly("bad", PRICE, d(2026, 1, 10)).endingOn(d(2026, 1, 5)));
        assertThrows(
                IllegalArgumentException.class,
                () -> Subscription.monthly("bad", Money.of("-1.00", "KZT"), d(2026, 1, 1)));
        assertThrows(NullPointerException.class, () -> Subscription.monthly("bad", PRICE, null));
    }

    @Test
    void periodsAreHalfOpenAndDoNotOverlap() {
        Subscription subscription = Subscription.monthly("sub-11", PRICE, d(2026, 1, 1));

        List<ChargeLine> lines = engine.schedule(subscription, d(2026, 6, 1));

        for (int i = 1; i < lines.size(); i++) {
            assertEquals(lines.get(i - 1).period().endExclusive(), lines.get(i).period().start());
        }
    }
}
