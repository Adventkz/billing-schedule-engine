package kz.yestai.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * An exact monetary amount, held in minor units (tiyn, cents) so that no
 * arithmetic in this library is ever done in binary floating point.
 *
 * <p>The interesting method is {@link #allocate(List)}: splitting 100.00 three
 * ways cannot be done by rounding each share independently — you lose or invent
 * a tiyn. It distributes the remainder by largest fractional part, so the parts
 * always add back up to the whole.
 */
public record Money(long minorUnits, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of(BigDecimal amount, Currency currency) {
        Objects.requireNonNull(amount, "amount");
        int scale = Math.max(currency.getDefaultFractionDigits(), 0);
        return new Money(amount.setScale(scale, RoundingMode.HALF_UP).movePointRight(scale).longValueExact(), currency);
    }

    public static Money of(String amount, String currencyCode) {
        return of(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(0L, currency);
    }

    public boolean isZero() {
        return minorUnits == 0L;
    }

    public boolean isNegative() {
        return minorUnits < 0L;
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minorUnits, other.minorUnits), currency);
    }

    public Money negated() {
        return new Money(Math.negateExact(minorUnits), currency);
    }

    /** Multiplies by a ratio, rounding half up to the currency's minor unit. */
    public Money multipliedBy(BigDecimal ratio) {
        Objects.requireNonNull(ratio, "ratio");
        return new Money(
                BigDecimal.valueOf(minorUnits).multiply(ratio).setScale(0, RoundingMode.HALF_UP).longValueExact(),
                currency);
    }

    /** Convenience for day-based proration: {@code amount * numerator / denominator}. */
    public Money multipliedBy(long numerator, long denominator) {
        if (denominator == 0L) {
            throw new IllegalArgumentException("denominator must not be zero");
        }
        return multipliedBy(BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 12, RoundingMode.HALF_UP));
    }

    /** Splits into {@code parts} equal shares, giving the remainder to the earliest shares. */
    public List<Money> allocate(int parts) {
        if (parts <= 0) {
            throw new IllegalArgumentException("parts must be positive, was " + parts);
        }
        List<BigDecimal> weights = new ArrayList<>(parts);
        for (int i = 0; i < parts; i++) {
            weights.add(BigDecimal.ONE);
        }
        return allocate(weights);
    }

    /**
     * Splits proportionally to {@code weights} without losing or inventing a
     * minor unit. Remainder units go to the shares with the largest truncated
     * fraction; ties are broken by position, so the result is deterministic.
     */
    public List<Money> allocate(List<BigDecimal> weights) {
        Objects.requireNonNull(weights, "weights");
        if (weights.isEmpty()) {
            throw new IllegalArgumentException("weights must not be empty");
        }
        BigDecimal total = weights.stream()
                .peek(w -> {
                    if (w.signum() < 0) {
                        throw new IllegalArgumentException("weights must not be negative: " + w);
                    }
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) {
            throw new IllegalArgumentException("weights must not sum to zero");
        }

        long[] shares = new long[weights.size()];
        BigDecimal[] remainders = new BigDecimal[weights.size()];
        long distributed = 0L;
        for (int i = 0; i < weights.size(); i++) {
            BigDecimal exact = BigDecimal.valueOf(minorUnits)
                    .multiply(weights.get(i))
                    .divide(total, 12, RoundingMode.HALF_UP);
            BigDecimal floor = exact.setScale(0, RoundingMode.FLOOR);
            shares[i] = floor.longValueExact();
            remainders[i] = exact.subtract(floor);
            distributed += shares[i];
        }

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < shares.length; i++) {
            order.add(i);
        }
        order.sort(Comparator.<Integer, BigDecimal>comparing(i -> remainders[i]).reversed()
                .thenComparing(Comparator.naturalOrder()));

        long leftover = minorUnits - distributed;
        for (int i = 0; leftover != 0; i = (i + 1) % order.size()) {
            int index = order.get(i);
            shares[index] += leftover > 0 ? 1 : -1;
            leftover += leftover > 0 ? -1 : 1;
        }

        List<Money> result = new ArrayList<>(shares.length);
        for (long share : shares) {
            result.add(new Money(share, currency));
        }
        return List.copyOf(result);
    }

    public BigDecimal toBigDecimal() {
        return BigDecimal.valueOf(minorUnits, Math.max(currency.getDefaultFractionDigits(), 0));
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(minorUnits, other.minorUnits);
    }

    @Override
    public String toString() {
        return toBigDecimal().toPlainString() + " " + currency.getCurrencyCode();
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "currency mismatch: " + currency.getCurrencyCode() + " vs " + other.currency.getCurrencyCode());
        }
    }
}
