package kz.yestai.billing;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns a subscription into the charge lines it produces over a window of time.
 *
 * <p>Pure and deterministic: no clock, no database, no side effects. Give it the
 * same subscription and the same window and it returns the same lines, which is
 * what makes recurring billing testable.
 */
public final class BillingScheduleEngine {

    /** Sanity bound so a mistaken window cannot spin forever. */
    private static final int MAX_PERIODS = 10_000;

    /**
     * All charge lines whose service period starts before {@code until}
     * (exclusive), clipped to the subscription's own start and end.
     */
    public List<ChargeLine> schedule(Subscription subscription, LocalDate until) {
        Objects.requireNonNull(subscription, "subscription");
        Objects.requireNonNull(until, "until");

        LocalDate serviceEnd = subscription.endDate().orElse(until);
        if (!serviceEnd.isAfter(subscription.start()) || !until.isAfter(subscription.start())) {
            return List.of();
        }
        BillingPeriod service = new BillingPeriod(subscription.start(), min(serviceEnd, until));

        List<ChargeLine> lines = new ArrayList<>();
        int index = subscription.cycle().indexOf(subscription.anchor(), service.start());

        for (int guard = 0; guard < MAX_PERIODS; guard++, index++) {
            BillingPeriod period = subscription.cycle().period(subscription.anchor(), index);
            if (!period.start().isBefore(service.endExclusive())) {
                break;
            }
            Optional<BillingPeriod> covered = period.intersect(service);
            if (covered.isEmpty()) {
                continue;
            }
            charge(subscription, period, covered.get()).ifPresent(lines::add);
        }
        return List.copyOf(lines);
    }

    /** The charge for a single period, or empty when nothing is billable. */
    public Optional<ChargeLine> charge(Subscription subscription, BillingPeriod period, BillingPeriod covered) {
        boolean partial = covered.days() < period.days();

        if (!partial) {
            return Optional.of(line(subscription, period, covered, subscription.price(), false));
        }
        return switch (subscription.proration()) {
            case FULL_PRICE -> Optional.of(line(subscription, period, covered, subscription.price(), false));
            case WHOLE_PERIODS_ONLY -> Optional.empty();
            case DAILY -> {
                Money amount = subscription.price().multipliedBy(covered.days(), period.days());
                yield amount.isZero()
                        ? Optional.empty()
                        : Optional.of(line(subscription, period, covered, amount, true));
            }
        };
    }

    /** Sum of a set of charge lines; empty input has no currency, so pass one. */
    public static Money total(List<ChargeLine> lines, Money zero) {
        return lines.stream().map(ChargeLine::amount).reduce(zero, Money::plus);
    }

    private static ChargeLine line(
            Subscription subscription, BillingPeriod period, BillingPeriod covered, Money amount, boolean prorated) {
        return new ChargeLine(subscription.id(), covered, period, amount, prorated);
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
