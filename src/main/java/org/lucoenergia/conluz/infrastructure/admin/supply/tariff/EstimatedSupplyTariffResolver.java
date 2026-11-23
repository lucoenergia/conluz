package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import org.lucoenergia.conluz.domain.admin.supply.tariff.*;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link SupplyTariffResolver} implementation that produces an estimated tariff
 * when the real contracted tariff for a supply is not known.
 *
 * <p>It prices every kWh at the configured flat price (bound from
 * {@code conluz.supply.tariff.estimated.*} into {@link EstimatedTariffProperties}), with the
 * electricity tax and VAT in force on the day it was consumed, and marks every segment as
 * {@link TariffSource#ESTIMATE} so downstream consumers know the figures are approximate rather
 * than contracted.
 *
 * <p>The requested range is split at every boundary of the VAT periods and of the electricity
 * tax periods inside it, one segment per sub-range, each carrying the rates in force on it
 * (TRF-002). A day no period of a tax covers is priced at that tax's default rate, and logged
 * at WARN (TRF-003).
 */
@Service
@Qualifier("estimatedSupplyTariffResolver")
public class EstimatedSupplyTariffResolver implements SupplyTariffResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(EstimatedSupplyTariffResolver.class);

    private final BigDecimal basePrice;
    private final TaxRatePeriods vat;
    private final TaxRatePeriods electricityTax;
    /**
     * The uncovered stretches of the configuration already logged. The hourly and daily series ask
     * for one civil day at a time, so logging every request would repeat the same warning once per
     * day of a long period; one line per stretch and tax is enough to make a missing period visible.
     */
    private final Set<String> loggedUncoveredStretches = ConcurrentHashMap.newKeySet();

    public EstimatedSupplyTariffResolver(EstimatedTariffProperties properties, EstimatedTaxRates taxRates) {
        this.basePrice = properties.getBaseEurPerKwh();
        this.vat = taxRates.vat();
        this.electricityTax = taxRates.electricityTax();
    }

    @Override
    public TariffSchedule scheduleFor(SupplyId supply, DateRange range) {
        SortedSet<LocalDate> ends = new TreeSet<>(vat.boundariesWithin(range));
        ends.addAll(electricityTax.boundariesWithin(range));
        ends.add(range.getEnd());

        List<TariffSegment> segments = new ArrayList<>();
        LocalDate start = range.getStart();
        for (LocalDate end : ends) {
            // No boundary of either tax falls strictly inside [start, end), so the rates in force on
            // its first day are the rates of every day of it.
            segments.add(new TariffSegment(
                    new DateRange(start, end),
                    TariffPlan.flat(basePrice),
                    vat.rateOn(start),
                    electricityTax.rateOn(start),
                    TariffSource.ESTIMATE
            ));
            start = end;
        }

        warnOnceIfUncovered("VAT", vat, range);
        warnOnceIfUncovered("electricity tax", electricityTax, range);
        return new TariffSchedule(segments);
    }

    private void warnOnceIfUncovered(String tax, TaxRatePeriods periods, DateRange range) {
        for (DateRange uncovered : periods.uncoveredWithin(range)) {
            String stretch = stretchOf(periods, uncovered);
            if (loggedUncoveredStretches.add(tax + " " + stretch)) {
                LOGGER.warn("No {} period is configured {}: [{}, {}) is priced at the default {} rate of {}. "
                                + "Configure the period under conluz.supply.tariff.estimated.taxes.",
                        tax, stretch, uncovered.getStart(), uncovered.getEnd(), tax, periods.getDefaultRate());
            }
        }
    }

    /**
     * The stretch of the calendar the configuration leaves uncovered that {@code uncovered} falls in:
     * before the first period, after a closed last one, or everywhere when no period is configured.
     */
    private static String stretchOf(TaxRatePeriods periods, DateRange uncovered) {
        List<TaxRatePeriod> configured = periods.getPeriods();
        if (configured.isEmpty()) {
            return "at all";
        }
        LocalDate firstFrom = configured.get(0).from();
        return uncovered.getStart().isBefore(firstFrom)
                ? "before " + firstFrom
                : "from " + configured.get(configured.size() - 1).to() + " on";
    }
}
