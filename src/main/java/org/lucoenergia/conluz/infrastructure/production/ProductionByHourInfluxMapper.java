package org.lucoenergia.conluz.infrastructure.production;

import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.domain.shared.BaseMapper;
import org.lucoenergia.conluz.infrastructure.shared.time.InstantToOffsetDateTimeConverter;
import org.springframework.stereotype.Component;

@Component
public class ProductionByHourInfluxMapper extends BaseMapper<ProductionPoint, ProductionByTime> {

    private final InstantToOffsetDateTimeConverter converter;

    public ProductionByHourInfluxMapper(InstantToOffsetDateTimeConverter converter) {
        this.converter = converter;
    }

    public ProductionByTime map(ProductionPoint measurement) {
        return new ProductionByTime(converter.convert(measurement.getTime()), measurement.getInverterPower());
    }
}
