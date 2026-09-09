package kz.yestai.billing;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * A subscription to bill.
 *
 * @param id            caller's identifier, echoed onto every charge line
 * @param price         the full price of one billing period
 * @param cycle         how often it renews
 * @param anchor        the date the billing grid is aligned to; usually the
 *                      signup date, but kept separate so a customer can be
 *                      moved to a common invoicing day without re-dating them
 * @param start         first day of service
 * @param end           day service stops, exclusive; {@code null} = open-ended
 * @param proration     what to do with periods the subscription only partly covers
 */
public record Subscription(
        String id,
        Money price,
        BillingCycle cycle,
        LocalDate anchor,
        LocalDate start,
        LocalDate end,
        ProrationPolicy proration) {

    public Subscription {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(cycle, "cycle");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(proration, "proration");
        if (price.isNegative()) {
            throw new IllegalArgumentException("price must not be negative: " + price);
        }
        if (end != null && !end.isAfter(start)) {
            throw new IllegalArgumentException("end must be after start: " + start + ".." + end);
        }
    }

    /** Open-ended monthly subscription anchored on its own start date. */
    public static Subscription monthly(String id, Money price, LocalDate start) {
        return new Subscription(id, price, BillingCycle.MONTHLY, start, start, null, ProrationPolicy.DAILY);
    }

    public Subscription endingOn(LocalDate newEnd) {
        return new Subscription(id, price, cycle, anchor, start, newEnd, proration);
    }

    public Subscription withProration(ProrationPolicy policy) {
        return new Subscription(id, price, cycle, anchor, start, end, policy);
    }

    public Optional<LocalDate> endDate() {
        return Optional.ofNullable(end);
    }
}
