package kz.yestai.billing;

/** What to charge for a period the subscription only partly covers. */
public enum ProrationPolicy {

    /** Charge the full price even for a partial period. */
    FULL_PRICE,

    /** Charge {@code price * coveredDays / daysInPeriod}. */
    DAILY,

    /** Skip partial periods entirely — bill only whole ones. */
    WHOLE_PERIODS_ONLY
}
