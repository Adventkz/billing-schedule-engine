package kz.yestai.billing;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * How often a subscription renews.
 *
 * <p>Every boundary is computed from the original anchor date rather than by
 * stepping from the previous boundary. That difference is the whole reason this
 * enum exists: a subscription anchored on the 31st stepped month by month
 * degrades to the 28th after February and never comes back, while
 * {@code anchor.plusMonths(n)} clamps only where it must and returns to the 31st
 * in March.
 */
public enum BillingCycle {

    WEEKLY {
        @Override
        public LocalDate boundary(LocalDate anchor, int index) {
            return anchor.plusWeeks(index);
        }

        @Override
        int estimateIndex(LocalDate anchor, LocalDate date) {
            return (int) (ChronoUnit.DAYS.between(anchor, date) / 7);
        }
    },

    MONTHLY {
        @Override
        public LocalDate boundary(LocalDate anchor, int index) {
            return anchor.plusMonths(index);
        }

        @Override
        int estimateIndex(LocalDate anchor, LocalDate date) {
            return (int) ChronoUnit.MONTHS.between(anchor, date);
        }
    },

    QUARTERLY {
        @Override
        public LocalDate boundary(LocalDate anchor, int index) {
            return anchor.plusMonths(3L * index);
        }

        @Override
        int estimateIndex(LocalDate anchor, LocalDate date) {
            return (int) (ChronoUnit.MONTHS.between(anchor, date) / 3);
        }
    },

    YEARLY {
        @Override
        public LocalDate boundary(LocalDate anchor, int index) {
            return anchor.plusYears(index);
        }

        @Override
        int estimateIndex(LocalDate anchor, LocalDate date) {
            return (int) ChronoUnit.YEARS.between(anchor, date);
        }
    };

    /** The start of the {@code index}-th period counted from {@code anchor}. */
    public abstract LocalDate boundary(LocalDate anchor, int index);

    /** The period containing {@code date}: {@code [boundary(n), boundary(n + 1))}. */
    public BillingPeriod periodContaining(LocalDate anchor, LocalDate date) {
        return period(anchor, indexOf(anchor, date));
    }

    public BillingPeriod period(LocalDate anchor, int index) {
        return new BillingPeriod(boundary(anchor, index), boundary(anchor, index + 1));
    }

    /** Index of the period containing {@code date}; negative before the anchor. */
    public int indexOf(LocalDate anchor, LocalDate date) {
        int index = estimateIndex(anchor, date);
        // The estimate can be off by one around clamped month ends; correct it.
        while (boundary(anchor, index).isAfter(date)) {
            index--;
        }
        while (!boundary(anchor, index + 1).isAfter(date)) {
            index++;
        }
        return index;
    }

    abstract int estimateIndex(LocalDate anchor, LocalDate date);
}
