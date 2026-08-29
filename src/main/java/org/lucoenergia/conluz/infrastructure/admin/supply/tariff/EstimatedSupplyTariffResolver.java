package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import org.lucoenergia.conluz.domain.admin.supply.tariff.*;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@link SupplyTariffResolver} implementation that produces an estimated tariff
 * when the real contracted tariff for a supply is not known.
 *
 * <p>It builds a {@link TariffSchedule} containing a single {@link TariffSegment}
 * that covers the whole requested {@link DateRange} with a configurable flat
 * price and VAT rate (injected from {@code conluz.supply.tariff.estimated.*}
 * properties) and marks it as {@link TariffSource#ESTIMATE} so downstream
 * consumers know the figures are approximate rather than contracted.
 */
@Service
public class EstimatedSupplyTariffResolver implements SupplyTariffResolver {

    private final BigDecimal basePrice;
    private final BigDecimal vatRate;

    public EstimatedSupplyTariffResolver(
            @Value("${conluz.supply.tariff.estimated.base-eur-per-kwh}") BigDecimal basePrice,
            @Value("${conluz.supply.tariff.estimated.vat-rate}") BigDecimal vatRate) {
        this.basePrice = basePrice;
        this.vatRate = vatRate;
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
