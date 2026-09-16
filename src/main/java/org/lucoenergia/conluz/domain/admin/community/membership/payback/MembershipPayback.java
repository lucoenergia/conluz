package org.lucoenergia.conluz.domain.admin.community.membership.payback;

import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * How much of a member's investment their share of the community's energy has recovered so far,
 * and how long the rest would take at the rate observed until now.
 *
 * <p>Every derived figure is computed in {@link #of}, from the four inputs, with no I/O. Amounts
 * are unrounded here: rounding is presentational and happens once, where the response is built, so
 * a figure summed over several supplies is not rounded once per supply.
 *
 * <p>Absence is distinguished from zero throughout. A null investment means none has been recorded,
 * not that the member contributed nothing; a null {@code savedEur} means there was no period to
 * price, not that the period was worth nothing. Anything derived from a missing input is itself
 * null rather than being given a made-up value.
 */
public class MembershipPayback {

    /**
     * Mean length of a Gregorian month in days, 365.25 / 12. The estimate is a projection over
     * months that have not happened yet, so a calendar-exact month count would be false precision:
     * it would depend on which months those turn out to be.
     */
    private static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30.4375");

    /**
     * The ratio's scale is part of its definition rather than a display choice, so it is applied
     * here: four decimals resolves a tenth of a percent of progress, which is finer than any
     * caller can act on.
     */
    private static final int PROGRESS_RATIO_SCALE = 4;

    private final BigDecimal investmentEur;
    private final BigDecimal savedEur;
    private final BigDecimal remainingEur;
    private final BigDecimal progressRatio;
    private final LocalDate startDate;
    private final Integer estimatedRemainingMonths;
    private final TariffSource tariffSource;

    private MembershipPayback(BigDecimal investmentEur, BigDecimal savedEur, BigDecimal remainingEur,
                              BigDecimal progressRatio, LocalDate startDate,
                              Integer estimatedRemainingMonths, TariffSource tariffSource) {
        this.investmentEur = investmentEur;
        this.savedEur = savedEur;
        this.remainingEur = remainingEur;
        this.progressRatio = progressRatio;
        this.startDate = startDate;
        this.estimatedRemainingMonths = estimatedRemainingMonths;
        this.tariffSource = tariffSource;
    }

    /**
     * @param investmentEur what the member contributed, or null when none has been recorded
     * @param savedEur      what their supplies have saved since {@code startDate}, or null when
     *                      there was no period to price
     * @param startDate     the community's first activation date, or null when it has never shared
     * @param today         the civil date "now" falls on, in the community's zone
     * @param tariffSource  where the prices behind {@code savedEur} came from; never null
     */
    public static MembershipPayback of(BigDecimal investmentEur, BigDecimal savedEur, LocalDate startDate,
                                       LocalDate today, TariffSource tariffSource) {
        BigDecimal remainingEur = remainingEur(investmentEur, savedEur);
        return new MembershipPayback(
                investmentEur,
                savedEur,
                remainingEur,
                progressRatio(investmentEur, savedEur),
                startDate,
                estimatedRemainingMonths(investmentEur, savedEur, remainingEur, startDate, today),
                Objects.requireNonNull(tariffSource));
    }

    /**
     * What is left to recover, floored at zero: once the investment is recovered there is nothing
     * remaining, and a negative "remaining" would be a surplus reported in the wrong field.
     */
    private static BigDecimal remainingEur(BigDecimal investmentEur, BigDecimal savedEur) {
        if (investmentEur == null || savedEur == null) {
            return null;
        }
        return investmentEur.subtract(savedEur).max(BigDecimal.ZERO);
    }

    /**
     * Progress towards the investment, deliberately uncapped: a member who has recovered more than
     * they put in has a ratio above 1, and flattening that to 1 would hide the return rather than
     * report it. {@code remainingEur} floors at zero because "remaining" is a debt; this does not,
     * because it is a measurement.
     */
    private static BigDecimal progressRatio(BigDecimal investmentEur, BigDecimal savedEur) {
        if (investmentEur == null || savedEur == null) {
            return null;
        }
        return savedEur.divide(investmentEur, PROGRESS_RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Whole months to recover the rest, at the average daily rate observed so far, rounded up.
     *
     * <p>The order of the guards is the contract, and each rules out a case the arithmetic below
     * could not survive:
     * <ol>
     *   <li>a missing input makes the estimate meaningless, so it is absent;</li>
     *   <li>an investment already recovered needs no more time, which is 0 rather than null --
     *       checked before the rate, since a recovered investment is an answer regardless of pace;</li>
     *   <li>nothing saved yet gives no rate to project from. Absent rather than infinite: the
     *       member is not waiting forever, it is that nothing can yet be said;</li>
     *   <li>no elapsed days gives no rate either. Today being the start date is the first day of
     *       sharing, not an error.</li>
     * </ol>
     *
     * <p>{@code DECIMAL128} governs the two non-terminating divisions. It is not the rounding the
     * caller sees -- the result is a whole number of months -- but 34 significant digits, which no
     * euro amount comes close to exhausting.
     */
    private static Integer estimatedRemainingMonths(BigDecimal investmentEur, BigDecimal savedEur,
                                                    BigDecimal remainingEur, LocalDate startDate,
                                                    LocalDate today) {
        if (investmentEur == null || savedEur == null || startDate == null) {
            return null;
        }
        if (savedEur.compareTo(investmentEur) >= 0) {
            return 0;
        }
        if (savedEur.signum() == 0) {
            return null;
        }
        long elapsedDays = ChronoUnit.DAYS.between(startDate, today);
        if (elapsedDays == 0) {
            return null;
        }

        BigDecimal dailyRate = savedEur.divide(BigDecimal.valueOf(elapsedDays), MathContext.DECIMAL128);
        BigDecimal months = remainingEur
                .divide(dailyRate, MathContext.DECIMAL128)
                .divide(DAYS_PER_MONTH, MathContext.DECIMAL128);

        // Rounded up: a partial month is still a month the member has to wait through.
        return months.setScale(0, RoundingMode.CEILING).intValueExact();
    }

    /**
     * What the member contributed, or null when none has been recorded. Unrounded.
     */
    public BigDecimal getInvestmentEur() {
        return investmentEur;
    }

    /**
     * What their supplies have saved since {@link #getStartDate()}, or null when there was no
     * period to price. Unrounded.
     */
    public BigDecimal getSavedEur() {
        return savedEur;
    }

    /**
     * What is left to recover, floored at zero, or null when either amount above is absent.
     * Unrounded.
     */
    public BigDecimal getRemainingEur() {
        return remainingEur;
    }

    /**
     * Saved over invested, at four decimals, or null when either amount is absent. May exceed 1.
     */
    public BigDecimal getProgressRatio() {
        return progressRatio;
    }

    /**
     * The community's first activation date, as a civil date in its zone, or null when it has
     * never shared energy.
     */
    public LocalDate getStartDate() {
        return startDate;
    }

    /**
     * Whole months remaining at the rate so far, 0 when already recovered, or null when no rate
     * can be established.
     */
    public Integer getEstimatedRemainingMonths() {
        return estimatedRemainingMonths;
    }

    /**
     * Where the prices behind {@link #getSavedEur()} came from. Never null: a monetary figure
     * derived from an estimate and one derived from a contracted tariff are not interchangeable,
     * and a caller cannot tell them apart unless the figure says so.
     */
    public TariffSource getTariffSource() {
        return tariffSource;
    }
}
