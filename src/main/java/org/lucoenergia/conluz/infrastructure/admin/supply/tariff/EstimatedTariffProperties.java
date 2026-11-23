package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Binds the estimated tariff configuration at startup.
 *
 * <p>These values are multiplied by metered kWh to produce the monetary figures the savings feature
 * reports, so an absent, unparseable or out-of-range value must stop the application from starting
 * rather than silently yield a plausible-looking wrong amount. Constructor binding plus
 * {@code @Validated} turns each of those into a startup failure naming the offending property for the
 * base price; the tax periods are only bound here, and validated as a whole by
 * {@link EstimatedTaxRatesConfiguration}, which names the offending property in the same way.
 */
@ConfigurationProperties(prefix = "conluz.supply.tariff.estimated")
@Validated
public class EstimatedTariffProperties {

    /**
     * Energy-term price per kWh, excluding taxes. Must be strictly positive: a zero or
     * negative estimate is never a meaningful tariff, only a misconfiguration.
     */
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private final BigDecimal baseEurPerKwh;

    /**
     * The electricity tax and VAT applied on top of {@link #baseEurPerKwh}, by consumption date.
     */
    private final Taxes taxes;

    public EstimatedTariffProperties(BigDecimal baseEurPerKwh, @DefaultValue Taxes taxes) {
        this.baseEurPerKwh = baseEurPerKwh;
        this.taxes = taxes;
    }

    public BigDecimal getBaseEurPerKwh() {
        return baseEurPerKwh;
    }

    public Taxes getTaxes() {
        return taxes;
    }

    /**
     * The dated periods of each tax, as two independent lists, and the rate of each tax on a date no
     * period of its list covers. Rates are fractions: {@code 0.21} is 21%.
     */
    public record Taxes(BigDecimal defaultVatRate,
                        BigDecimal defaultElectricityTaxRate,
                        @DefaultValue List<Period> vat,
                        @DefaultValue List<Period> electricityTax) {
    }

    /**
     * One period of a tax rate: {@code from} is inclusive and {@code to} exclusive, civil dates; an
     * omitted {@code to} means the period is open-ended.
     */
    public record Period(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         BigDecimal rate) {
    }
}
