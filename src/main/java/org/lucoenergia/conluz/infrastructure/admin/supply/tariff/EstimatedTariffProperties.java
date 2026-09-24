package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Binds and validates the estimated tariff configuration at startup.
 *
 * <p>These two values are multiplied by metered kWh to produce the monetary figures the
 * savings feature reports, so an absent, unparseable or out-of-range value must stop the
 * application from starting rather than silently yield a plausible-looking wrong amount.
 * Constructor binding plus {@code @Validated} turns each of those into a startup failure
 * naming the offending property.
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
     * VAT rate applied on top of {@link #baseEurPerKwh}, expressed as a fraction rather than
     * a percentage. {@code 0} means no VAT; the upper bound is exclusive because a rate of
     * 1 or above would mean tax at or beyond 100%, which in practice only happens when a
     * percentage was written where a fraction was expected.
     */
    @NotNull
    @DecimalMin("0")
    @DecimalMax(value = "1", inclusive = false)
    private final BigDecimal vatRate;

    public EstimatedTariffProperties(BigDecimal baseEurPerKwh, BigDecimal vatRate) {
        this.baseEurPerKwh = baseEurPerKwh;
        this.vatRate = vatRate;
    }

    public BigDecimal getBaseEurPerKwh() {
        return baseEurPerKwh;
    }

    public BigDecimal getVatRate() {
        return vatRate;
    }
}
