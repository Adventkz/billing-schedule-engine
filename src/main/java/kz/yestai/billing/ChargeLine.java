package kz.yestai.billing;

import java.util.Objects;

/**
 * One line on an invoice.
 *
 * @param subscriptionId which subscription produced it
 * @param period         the service period actually covered (already clipped to
 *                       the subscription's own start and end)
 * @param fullPeriod     the untrimmed billing period it belongs to
 * @param amount         what to charge
 * @param prorated       true when {@code amount} is less than the full price
 */
public record ChargeLine(
        String subscriptionId,
        BillingPeriod period,
        BillingPeriod fullPeriod,
        Money amount,
        boolean prorated) {

    public ChargeLine {
        Objects.requireNonNull(subscriptionId, "subscriptionId");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(fullPeriod, "fullPeriod");
        Objects.requireNonNull(amount, "amount");
    }

    @Override
    public String toString() {
        return subscriptionId + " " + period + " " + amount + (prorated ? " (prorated)" : "");
    }
}
