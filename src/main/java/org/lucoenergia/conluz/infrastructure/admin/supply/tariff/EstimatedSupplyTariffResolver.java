package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import org.lucoenergia.conluz.domain.admin.supply.tariff.*;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class EstimatedSupplyTariffResolver implements SupplyTariffResolver {

    private final BigDecimal basePrice;
    private final BigDecimal vatRate;

    public EstimatedSupplyTariffResolver(
            @Value("${supply.tariff.estimated.base-eur-per-kwh}") BigDecimal basePrice,
            @Value("${supply.tariff.estimated.vat-rate}") BigDecimal vatRate) {
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
