package org.lucoenergia.conluz.domain.admin.community.membership.payback;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Every expected value here is worked out by hand from the rules, not read off the implementation.
 * The month figures are shown with their arithmetic so a change in the constant or in the rounding
 * direction fails with a legible diff rather than an opaque one.
 */
class MembershipPaybackTest {

    private static final LocalDate START = LocalDate.parse("2025-01-01");
    /** 100 civil days after START: 30 + 28 + 31 + 11. */
    private static final LocalDate TODAY = LocalDate.parse("2025-04-11");

    // --- rule 5: remainingEur ---

    @Test
    void remainingIsWhatIsLeftToRecover() {
        MembershipPayback payback = payback("1000.00", "250.00", START, TODAY);

        assertEquals(0, new BigDecimal("750.00").compareTo(payback.getRemainingEur()));
    }

    /**
     * A recovered investment leaves nothing remaining. The surplus is visible in the ratio, which
     * is where an amount above the investment belongs.
     */
    @Test
    void remainingFloorsAtZeroOnceTheInvestmentIsRecovered() {
        MembershipPayback payback = payback("1000.00", "1250.00", START, TODAY);

        assertEquals(0, BigDecimal.ZERO.compareTo(payback.getRemainingEur()));
    }

    @Test
    void remainingIsExactlyZeroWhenSavingsEqualTheInvestment() {
        MembershipPayback payback = payback("1000.00", "1000.00", START, TODAY);

        assertEquals(0, BigDecimal.ZERO.compareTo(payback.getRemainingEur()));
    }

    // --- rule 6: progressRatio ---

    @Test
    void progressRatioIsSavingsOverInvestmentAtFourDecimals() {
        // 250 / 1000 = 0.25
        MembershipPayback payback = payback("1000.00", "250.00", START, TODAY);

        assertEquals(new BigDecimal("0.2500"), payback.getProgressRatio());
    }

    /**
     * The scale is part of the figure, so a non-terminating quotient is rounded half-up to four
     * decimals rather than overflowing or throwing.
     */
    @Test
    void progressRatioRoundsANonTerminatingQuotientHalfUp() {
        // 100 / 300 = 0.3333... -> 0.3333
        MembershipPayback payback = payback("300.00", "100.00", START, TODAY);

        assertEquals(new BigDecimal("0.3333"), payback.getProgressRatio());
    }

    @Test
    void progressRatioIsNotCappedAtOne() {
        // 1250 / 1000 = 1.25
        MembershipPayback payback = payback("1000.00", "1250.00", START, TODAY);

        assertEquals(new BigDecimal("1.2500"), payback.getProgressRatio());
    }

    // --- rule 7: estimatedRemainingMonths ---

    /**
     * 100 days elapsed, 250 saved, so 2.50/day; 750 left is 300 days, which is 300 / 30.4375 =
     * 9.856... months, rounded up to 10.
     */
    @Test
    void estimatedMonthsProjectsTheRemainderAtTheRateSoFar() {
        MembershipPayback payback = payback("1000.00", "250.00", START, TODAY);

        assertEquals(10, payback.getEstimatedRemainingMonths());
    }

    /**
     * Rounding up is asserted on a case whose exact value is only just above a whole month, so a
     * HALF_UP or a truncation would give a different answer: 100 days elapsed, 100 saved is
     * 1.00/day; 31 left is 31 days, and 31 / 30.4375 = 1.018... months. Up to 2, not 1.
     */
    @Test
    void estimatedMonthsRoundsAPartialMonthUp() {
        MembershipPayback payback = payback("131.00", "100.00", START, TODAY);

        assertEquals(2, payback.getEstimatedRemainingMonths());
    }

    /**
     * An exact number of months is not rounded up to the next one: 100 days elapsed, 100 saved is
     * 1.00/day, and 30.4375 left is exactly 1 month.
     */
    @Test
    void estimatedMonthsDoesNotRoundUpAnExactMonth() {
        MembershipPayback payback = payback("130.4375", "100.00", START, TODAY);

        assertEquals(1, payback.getEstimatedRemainingMonths());
    }

    /**
     * Zero rather than null: the member is not waiting, which is a definite answer. Checked before
     * the rate, so it holds however fast or slowly they got there.
     */
    @Test
    void estimatedMonthsIsZeroOnceTheInvestmentIsRecovered() {
        assertEquals(0, payback("1000.00", "1000.00", START, TODAY).getEstimatedRemainingMonths());
        assertEquals(0, payback("1000.00", "1250.00", START, TODAY).getEstimatedRemainingMonths());
    }

    /**
     * No savings gives no rate to project from. Absent rather than infinite or an error: nothing
     * can be said yet, which is not the same as saying it will never happen.
     */
    @Test
    void estimatedMonthsIsAbsentWhenNothingHasBeenSavedYet() {
        MembershipPayback payback = payback("1000.00", "0.00", START, TODAY);

        assertNull(payback.getEstimatedRemainingMonths());
        // The other figures are still perfectly well defined.
        assertEquals(0, new BigDecimal("1000.00").compareTo(payback.getRemainingEur()));
        assertEquals(new BigDecimal("0.0000"), payback.getProgressRatio());
    }

    /**
     * Today being the start date is the community's first day of sharing, not an error, and it
     * gives no elapsed time to divide by.
     */
    @Test
    void estimatedMonthsIsAbsentOnTheFirstDay() {
        MembershipPayback payback = payback("1000.00", "5.00", START, START);

        assertNull(payback.getEstimatedRemainingMonths());
        assertEquals(0, new BigDecimal("995.00").compareTo(payback.getRemainingEur()));
    }

    // --- absent inputs propagate ---

    @Test
    void anAbsentInvestmentLeavesEveryDerivedFigureAbsent() {
        MembershipPayback payback = MembershipPayback.of(null, new BigDecimal("250.00"), START, TODAY,
                TariffSource.ESTIMATE);

        assertNull(payback.getInvestmentEur());
        assertNull(payback.getRemainingEur());
        assertNull(payback.getProgressRatio());
        assertNull(payback.getEstimatedRemainingMonths());
        // The savings themselves are still reported: they do not depend on the investment.
        assertEquals(0, new BigDecimal("250.00").compareTo(payback.getSavedEur()));
        assertEquals(START, payback.getStartDate());
    }

    @Test
    void absentSavingsLeaveEveryDerivedFigureAbsent() {
        MembershipPayback payback = MembershipPayback.of(new BigDecimal("1000.00"), null, null, TODAY,
                TariffSource.ESTIMATE);

        assertNull(payback.getSavedEur());
        assertNull(payback.getRemainingEur());
        assertNull(payback.getProgressRatio());
        assertNull(payback.getEstimatedRemainingMonths());
        assertNull(payback.getStartDate());
        // The investment is still reported: it is recorded independently of any sharing.
        assertEquals(0, new BigDecimal("1000.00").compareTo(payback.getInvestmentEur()));
    }

    /**
     * Without a start date there is no elapsed time, so no rate, even though both amounts are
     * known and the remainder is therefore perfectly well defined.
     */
    @Test
    void anAbsentStartDateLeavesOnlyTheMonthsAbsent() {
        MembershipPayback payback = MembershipPayback.of(new BigDecimal("1000.00"),
                new BigDecimal("250.00"), null, TODAY, TariffSource.ESTIMATE);

        assertNull(payback.getEstimatedRemainingMonths());
        assertNull(payback.getStartDate());
        assertEquals(0, new BigDecimal("750.00").compareTo(payback.getRemainingEur()));
        assertEquals(new BigDecimal("0.2500"), payback.getProgressRatio());
    }

    // --- tariff source ---

    @Test
    void theTariffSourceIsCarriedThroughUnchanged() {
        assertEquals(TariffSource.REAL_TARIFF,
                MembershipPayback.of(new BigDecimal("1000.00"), new BigDecimal("250.00"), START, TODAY,
                        TariffSource.REAL_TARIFF).getTariffSource());
        assertEquals(TariffSource.ESTIMATE,
                MembershipPayback.of(new BigDecimal("1000.00"), new BigDecimal("250.00"), START, TODAY,
                        TariffSource.ESTIMATE).getTariffSource());
    }

    /**
     * A money figure with no stated provenance is the one thing this must not be able to produce,
     * so it is rejected at construction rather than defaulted.
     */
    @Test
    void anAbsentTariffSourceIsRejected() {
        assertThrows(NullPointerException.class,
                () -> MembershipPayback.of(new BigDecimal("1000.00"), new BigDecimal("250.00"),
                        START, TODAY, null));
    }

    /**
     * The degenerate case: nothing recorded and nothing shared. Every figure is absent, but the
     * object is still constructible and still states where its (absent) money came from.
     */
    @Test
    void aMembershipWithNoInvestmentAndNoSharingIsEntirelyAbsentButValid() {
        MembershipPayback payback = MembershipPayback.of(null, null, null, TODAY, TariffSource.ESTIMATE);

        assertNull(payback.getInvestmentEur());
        assertNull(payback.getSavedEur());
        assertNull(payback.getRemainingEur());
        assertNull(payback.getProgressRatio());
        assertNull(payback.getEstimatedRemainingMonths());
        assertNull(payback.getStartDate());
        assertNotNull(payback.getTariffSource());
    }

    private static MembershipPayback payback(String investmentEur, String savedEur, LocalDate startDate,
                                             LocalDate today) {
        return MembershipPayback.of(new BigDecimal(investmentEur), new BigDecimal(savedEur), startDate,
                today, TariffSource.ESTIMATE);
    }
}
