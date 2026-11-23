package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import org.lucoenergia.conluz.domain.admin.supply.tariff.InvalidTaxRatePeriodsException;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TaxRatePeriod;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TaxRatePeriods;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

/**
 * Builds the estimated tariff's tax rate periods from the configuration at startup, so a timeline that
 * breaks TRF-004 stops the application, with a message naming the property to fix.
 *
 * <p>The rules themselves belong to {@link TaxRatePeriods}; this class only knows where the values come
 * from, and so how to name the offending one.
 */
@Configuration
@EnableConfigurationProperties(EstimatedTariffProperties.class)
public class EstimatedTaxRatesConfiguration {

    static final String TAXES_PREFIX = "conluz.supply.tariff.estimated.taxes.";

    @Bean
    EstimatedTaxRates estimatedTaxRates(EstimatedTariffProperties properties) {
        EstimatedTariffProperties.Taxes taxes = properties.getTaxes();
        return new EstimatedTaxRates(
                periods(taxes.vat(), taxes.defaultVatRate(), "vat", "default-vat-rate"),
                periods(taxes.electricityTax(), taxes.defaultElectricityTaxRate(),
                        "electricity-tax", "default-electricity-tax-rate"));
    }

    private static TaxRatePeriods periods(List<EstimatedTariffProperties.Period> periods, BigDecimal defaultRate,
                                          String listProperty, String defaultRateProperty) {
        List<TaxRatePeriod> configured = periods.stream()
                .map(period -> new TaxRatePeriod(period.from(), period.to(), period.rate()))
                .toList();
        try {
            return new TaxRatePeriods(configured, defaultRate);
        } catch (InvalidTaxRatePeriodsException e) {
            throw new IllegalStateException("Invalid estimated tariff configuration: "
                    + propertyOf(e, listProperty, defaultRateProperty) + " " + describe(e.getReason()), e);
        }
    }

    /**
     * The property holding the value the violation was detected at: the start of a period that does not
     * follow its predecessor, the end of one that is empty or open-ended too early.
     */
    private static String propertyOf(InvalidTaxRatePeriodsException e, String listProperty,
                                     String defaultRateProperty) {
        String period = TAXES_PREFIX + listProperty + "[" + e.getPeriodIndex() + "].";
        return switch (e.getReason()) {
            case FROM_MISSING, UNORDERED, OVERLAP, GAP -> period + "from";
            case PERIOD_EMPTY, OPEN_ENDED_NOT_LAST -> period + "to";
            case RATE_MISSING, RATE_OUT_OF_RANGE -> period + "rate";
            case DEFAULT_RATE_MISSING, DEFAULT_RATE_OUT_OF_RANGE -> TAXES_PREFIX + defaultRateProperty;
        };
    }

    private static String describe(InvalidTaxRatePeriodsException.Reason reason) {
        return switch (reason) {
            case FROM_MISSING, RATE_MISSING, DEFAULT_RATE_MISSING -> "is missing";
            case PERIOD_EMPTY -> "must be after the period's start";
            case UNORDERED -> "is before the previous period's start: periods must be in order";
            case OVERLAP -> "is before the previous period's end: periods must not overlap";
            case GAP -> "is after the previous period's end: periods must leave no gap";
            case OPEN_ENDED_NOT_LAST -> "is missing, and only the last period may be open-ended";
            case RATE_OUT_OF_RANGE, DEFAULT_RATE_OUT_OF_RANGE ->
                    "must be a fraction of at least 0 and less than 1 (0.21 is 21%)";
        };
    }
}
