package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import org.lucoenergia.conluz.domain.admin.supply.tariff.*;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@link SupplyTariffResolver} implementation that produces an estimated tariff
 * when the real contracted tariff for a supply is not known.
 *
 * <p>It builds a {@link TariffSchedule} containing a single {@link TariffSegment}
 * that covers the whole requested {@link DateRange} with a configurable flat
 * price and VAT rate (bound from {@code conluz.supply.tariff.estimated.*} into
 * {@link EstimatedTariffProperties}) and marks it as {@link TariffSource#ESTIMATE}
 * so downstream consumers know the figures are approximate rather than contracted.
 *
 * <p>{@code @EnableConfigurationProperties} sits on this service rather than on a
 * separate configuration class or on {@code ConLuzApplication}: {@link EstimatedTariffProperties}
 * is the first {@code @ConfigurationProperties} type in the repository, so there is no
 * {@code @ConfigurationPropertiesScan} to piggyback on, and declaring the registration on
 * the type's only consumer is the narrowest option -- it keeps the binding visible next to
 * the code that depends on it and changes nothing application-wide.
 */
@Service
@Qualifier("estimatedSupplyTariffResolver")
@EnableConfigurationProperties(EstimatedTariffProperties.class)
public class EstimatedSupplyTariffResolver implements SupplyTariffResolver {

    private final BigDecimal basePrice;
    private final BigDecimal vatRate;

    public EstimatedSupplyTariffResolver(EstimatedTariffProperties properties) {
        this.basePrice = properties.getBaseEurPerKwh();
        this.vatRate = properties.getVatRate();
    }

    @Override
    public TariffSchedule scheduleFor(SupplyId supply, DateRange range) {
        TariffSegment segment = new TariffSegment(
                range,
                TariffPlan.flat(basePrice),
                vatRate,
                TariffSource.ESTIMATE
        );
        return new TariffSchedule(List.of(segment));
    }
}
