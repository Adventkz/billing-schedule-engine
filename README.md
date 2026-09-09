# billing-schedule-engine

Recurring billing is one of those problems that looks like `price × months` for
about a week. Then a customer signs up on the 31st, another cancels on the 9th,
and finance asks why the three-way revenue split of 100 000 ₸ adds up to
99 999.99 ₸.

This is the part of a billing system that has to be right: a small, pure Java
library that turns a subscription into the charge lines it produces, and splits
those amounts between parties without losing a tiyn. No Spring, no database, no
clock — the same inputs always produce the same output, which is what makes it
testable.

```java
var subscription = new Subscription(
        "acme-monthly",
        Money.of("30000.00", "KZT"),
        BillingCycle.MONTHLY,
        LocalDate.of(2026, 1, 1),   // invoicing day the account is aligned to
        LocalDate.of(2026, 1, 15),  // service started mid-month
        null,                       // open-ended
        ProrationPolicy.DAILY);

new BillingScheduleEngine().schedule(subscription, LocalDate.of(2026, 4, 1));
// acme-monthly 2026-01-15..2026-01-31 16451.61 KZT (prorated)   17 of 31 days
// acme-monthly 2026-02-01..2026-02-28 30000.00 KZT
// acme-monthly 2026-03-01..2026-03-31 30000.00 KZT
```

## The three problems it actually solves

**Month ends do not drift.** A subscription anchored on the 31st bills on
Feb 28 and then goes back to Mar 31. Stepping month by month from the previous
boundary silently degrades it to the 28th forever; every boundary here is
computed from the original anchor, so it clamps only where the calendar forces
it to. Leap years included.

**Partial periods are explicit.** A period the subscription only partly covers
is charged by day (`DAILY`), in full (`FULL_PRICE`), or not at all
(`WHOLE_PERIODS_ONLY`) — a decision the caller makes, not a default buried in
the arithmetic. Each line reports both the period it covers and the full period
it belongs to, so an invoice can print "1–9 March, of 1–31 March".

**Money never rounds itself away.** Amounts are held in minor units, never in
`double`. Splitting 100.00 three ways gives 33.34 / 33.33 / 33.33 — the
remainder goes to the largest fractional share, deterministically, and the parts
always add back up to the whole.

```java
new RevenueSplitter().split(
        Money.of("100000.00", "KZT"),
        List.of(SplitRule.percent("vendor", "70"),
                SplitRule.percent("reseller", "20"),
                SplitRule.percent("platform", "10")));
// vendor 70000.00 KZT, reseller 20000.00 KZT, platform 10000.00 KZT
```

## API

| Type | Purpose |
| --- | --- |
| `Money` | exact amounts in minor units; `plus`, `minus`, `multipliedBy(n, d)`, `allocate` |
| `BillingCycle` | `WEEKLY`, `MONTHLY`, `QUARTERLY`, `YEARLY`; boundaries from an anchor |
| `BillingPeriod` | half-open `[start, endExclusive)` with `intersect` and `days` |
| `Subscription` | price, cycle, anchor, start, optional end, proration policy |
| `ProrationPolicy` | `DAILY`, `FULL_PRICE`, `WHOLE_PERIODS_ONLY` |
| `BillingScheduleEngine` | `schedule(subscription, until)` → `List<ChargeLine>` |
| `RevenueSplitter` | splits an amount across parties by share |

Everything is a record or an enum; nothing is mutable.

## Build

```bash
mvn verify
```

Java 21, JUnit 5, no runtime dependencies.

## Tests

The test suite is the specification: month-end anchors across February and leap
years, proration of a first and a last partial period, cancellation mid-period,
window clipping, negative amounts (refunds split too), and the allocation
invariant — the parts of any split always sum back to the original.

## Кратко по-русски

Библиотека для рекуррентного биллинга: расписание списаний по подписке
(включая корректную обработку 31-го числа, февраля и високосных лет),
пропорциональные начисления за неполные периоды и расщепление выручки между
участниками без потери копеек. Чистая доменная логика без Spring и БД.

## License

MIT — see [LICENSE](LICENSE).
