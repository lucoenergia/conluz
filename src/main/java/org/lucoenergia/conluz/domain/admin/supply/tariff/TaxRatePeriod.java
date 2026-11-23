package org.lucoenergia.conluz.domain.admin.supply.tariff;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One period of a tax rate, as configured: the rate in force from {@code from}, inclusive, until
 * {@code to}, exclusive, both civil dates with the same half-open convention as {@link DateRange}.
 *
 * <p>A null {@code to} means the period is open-ended: the rate stays in force until a new period is
 * configured. Nothing is checked here; a period is only meaningful as part of {@link TaxRatePeriods},
 * which validates the whole timeline at once.
 *
 * @param from the first civil date the rate applies to
 * @param to   the first civil date the rate no longer applies to, or null when open-ended
 * @param rate the rate as a fraction rather than a percentage: {@code 0.21} is 21%
 */
public record TaxRatePeriod(LocalDate from, LocalDate to, BigDecimal rate) {
}
