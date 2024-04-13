package org.lucoenergia.conluz.infrastructure.production.get;

import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.domain.shared.BaseMapper;
import org.lucoenergia.conluz.infrastructure.production.ProductionPoint;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Component;

@Component
public class ProductionByHourInfluxMapper extends BaseMapper<ProductionPoint, ProductionByTime> {

    private final DateConverter converter;

    public ProductionByHourInfluxMapper(DateConverter converter) {
        this.converter = converter;
    }

    public ProductionByTime map(ProductionPoint measurement) {
        return new ProductionByTime(converter.convertInstantToOffsetDateTime(measurement.getTime()), measurement.getInverterPower());
    }
}
