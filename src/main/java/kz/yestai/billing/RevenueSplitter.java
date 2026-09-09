package kz.yestai.billing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Splits an amount between parties — the reseller, the vendor, the platform —
 * so that the parts always add back up to the whole.
 *
 * <p>Rounding each share on its own is the classic way to lose a tiyn on every
 * invoice and spend a day reconciling it later. This delegates to
 * {@link Money#allocate(List)}, which distributes the remainder deterministically.
 */
public final class RevenueSplitter {

    /** One party's claim on the revenue, as a share of the total. */
    public record SplitRule(String party, BigDecimal share) {
        public SplitRule {
            Objects.requireNonNull(party, "party");
            Objects.requireNonNull(share, "share");
            if (share.signum() < 0) {
                throw new IllegalArgumentException("share must not be negative: " + party + "=" + share);
            }
        }

        public static SplitRule percent(String party, String percent) {
            return new SplitRule(party, new BigDecimal(percent));
        }
    }

    /** What one party actually receives. */
    public record SplitLine(String party, Money amount) {}

    /**
     * Distributes {@code total} across {@code rules} proportionally to their
     * shares. Shares need not sum to 1 or to 100 — only their ratios matter.
     */
    public List<SplitLine> split(Money total, List<SplitRule> rules) {
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(rules, "rules");
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("rules must not be empty");
        }

        List<Money> amounts = total.allocate(rules.stream().map(SplitRule::share).toList());
        List<SplitLine> lines = new ArrayList<>(rules.size());
        for (int i = 0; i < rules.size(); i++) {
            lines.add(new SplitLine(rules.get(i).party(), amounts.get(i)));
        }
        return List.copyOf(lines);
    }
}
