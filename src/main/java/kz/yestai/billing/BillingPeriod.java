package kz.yestai.billing;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/** A half-open date range {@code [start, endExclusive)}. */
public record BillingPeriod(LocalDate start, LocalDate endExclusive) {

    public BillingPeriod {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(endExclusive, "endExclusive");
        if (!endExclusive.isAfter(start)) {
            throw new IllegalArgumentException("endExclusive must be after start: " + start + ".." + endExclusive);
        }
    }

    public long days() {
        return ChronoUnit.DAYS.between(start, endExclusive);
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && date.isBefore(endExclusive);
    }

    /** The overlap with {@code other}, or empty when they do not overlap. */
    public Optional<BillingPeriod> intersect(BillingPeriod other) {
        LocalDate from = start.isAfter(other.start) ? start : other.start;
        LocalDate to = endExclusive.isBefore(other.endExclusive) ? endExclusive : other.endExclusive;
        return to.isAfter(from) ? Optional.of(new BillingPeriod(from, to)) : Optional.empty();
    }

    /** The last day actually covered — what an invoice line prints. */
    public LocalDate lastDay() {
        return endExclusive.minusDays(1);
    }

    @Override
    public String toString() {
        return start + ".." + lastDay();
    }
}
