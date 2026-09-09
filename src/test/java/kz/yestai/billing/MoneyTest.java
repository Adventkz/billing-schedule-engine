package kz.yestai.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTest {

    private static final Currency KZT = Currency.getInstance("KZT");

    private static Money kzt(String amount) {
        return Money.of(amount, "KZT");
    }

    @Test
    void parsesIntoMinorUnits() {
        assertEquals(2500000L, kzt("25000.00").minorUnits());
        assertEquals("25000.00 KZT", kzt("25000.00").toString());
    }

    @Test
    void roundsHalfUpToTheMinorUnit() {
        assertEquals(1235L, Money.of(new BigDecimal("12.345"), KZT).minorUnits());
    }

    @Test
    void addsAndSubtracts() {
        assertEquals(kzt("85000.00"), kzt("25000.00").plus(kzt("60000.00")));
        assertEquals(kzt("35000.00"), kzt("60000.00").minus(kzt("25000.00")));
        assertTrue(kzt("25000.00").minus(kzt("60000.00")).isNegative());
    }

    @Test
    void refusesToMixCurrencies() {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> kzt("100.00").plus(Money.of("100.00", "USD")));
        assertTrue(error.getMessage().contains("currency mismatch"));
    }

    @Test
    @DisplayName("splitting 100.00 three ways loses nothing")
    void allocateDistributesTheRemainder() {
        List<Money> parts = kzt("100.00").allocate(3);

        assertEquals(List.of(kzt("33.34"), kzt("33.33"), kzt("33.33")), parts);
        assertEquals(kzt("100.00"), parts.stream().reduce(Money.zero(KZT), Money::plus));
    }

    @Test
    void allocateHonoursWeights() {
        List<Money> parts = kzt("100.00")
                .allocate(List.of(new BigDecimal("70"), new BigDecimal("30")));

        assertEquals(List.of(kzt("70.00"), kzt("30.00")), parts);
    }

    @Test
    void allocateGivesRemainderToTheLargestFraction() {
        // 10.00 split 1:1:1:1:1:1 => 1.666… each; four shares round up to 1.67.
        List<Money> parts = kzt("10.00").allocate(6);

        assertEquals(kzt("10.00"), parts.stream().reduce(Money.zero(KZT), Money::plus));
        assertEquals(kzt("1.67"), parts.get(0));
        assertEquals(kzt("1.66"), parts.get(5));
    }

    @Test
    void allocateWorksForNegativeAmounts() {
        List<Money> parts = kzt("-100.00").allocate(3);

        assertEquals(kzt("-100.00"), parts.stream().reduce(Money.zero(KZT), Money::plus));
    }

    @Test
    void allocateRejectsNonsense() {
        assertThrows(IllegalArgumentException.class, () -> kzt("10.00").allocate(0));
        assertThrows(IllegalArgumentException.class, () -> kzt("10.00").allocate(List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> kzt("10.00").allocate(List.of(BigDecimal.ZERO, BigDecimal.ZERO)));
        assertThrows(
                IllegalArgumentException.class,
                () -> kzt("10.00").allocate(List.of(BigDecimal.ONE, new BigDecimal("-1"))));
    }

    @Test
    void proratesByDays() {
        // 30 000 ₸ for 17 of 31 days.
        assertEquals(kzt("16451.61"), kzt("30000.00").multipliedBy(17, 31));
    }

    @Test
    void multiplyRejectsZeroDenominator() {
        assertThrows(IllegalArgumentException.class, () -> kzt("30000.00").multipliedBy(1, 0));
    }

    @Test
    void comparesAndConverts() {
        assertTrue(kzt("60000.00").compareTo(kzt("25000.00")) > 0);
        assertEquals(new BigDecimal("25000.00"), kzt("25000.00").toBigDecimal());
        assertTrue(Money.zero(KZT).isZero());
    }
}
