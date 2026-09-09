package kz.yestai.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Currency;
import java.util.List;
import kz.yestai.billing.RevenueSplitter.SplitLine;
import kz.yestai.billing.RevenueSplitter.SplitRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RevenueSplitterTest {

    private static final Currency KZT = Currency.getInstance("KZT");

    private final RevenueSplitter splitter = new RevenueSplitter();

    private static Money kzt(String amount) {
        return Money.of(amount, "KZT");
    }

    private static Money sum(List<SplitLine> lines) {
        return lines.stream().map(SplitLine::amount).reduce(Money.zero(KZT), Money::plus);
    }

    @Test
    @DisplayName("the parts always add back up to the invoice")
    void splitsWithoutLosingATiyn() {
        List<SplitLine> lines = splitter.split(
                kzt("100000.00"),
                List.of(
                        SplitRule.percent("vendor", "70"),
                        SplitRule.percent("reseller", "20"),
                        SplitRule.percent("platform", "10")));

        assertEquals(kzt("70000.00"), lines.get(0).amount());
        assertEquals(kzt("20000.00"), lines.get(1).amount());
        assertEquals(kzt("10000.00"), lines.get(2).amount());
        assertEquals(kzt("100000.00"), sum(lines));
    }

    @Test
    void anAwkwardAmountStillReconciles() {
        List<SplitLine> lines = splitter.split(
                kzt("1000.01"),
                List.of(
                        SplitRule.percent("a", "33.33"),
                        SplitRule.percent("b", "33.33"),
                        SplitRule.percent("c", "33.34")));

        assertEquals(kzt("1000.01"), sum(lines));
    }

    @Test
    void sharesNeedNotSumToOneHundred() {
        List<SplitLine> lines =
                splitter.split(kzt("900.00"), List.of(SplitRule.percent("a", "2"), SplitRule.percent("b", "1")));

        assertEquals(kzt("600.00"), lines.get(0).amount());
        assertEquals(kzt("300.00"), lines.get(1).amount());
    }

    @Test
    void keepsPartyOrderAndNames() {
        List<SplitLine> lines =
                splitter.split(kzt("10.00"), List.of(SplitRule.percent("first", "1"), SplitRule.percent("second", "1")));

        assertEquals(List.of("first", "second"), lines.stream().map(SplitLine::party).toList());
    }

    @Test
    void splitsARefund() {
        List<SplitLine> lines = splitter.split(
                kzt("-100.00"), List.of(SplitRule.percent("vendor", "70"), SplitRule.percent("platform", "30")));

        assertEquals(kzt("-70.00"), lines.get(0).amount());
        assertEquals(kzt("-100.00"), sum(lines));
    }

    @Test
    void rejectsEmptyOrNegativeRules() {
        assertThrows(IllegalArgumentException.class, () -> splitter.split(kzt("10.00"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> SplitRule.percent("bad", "-1"));
    }
}
