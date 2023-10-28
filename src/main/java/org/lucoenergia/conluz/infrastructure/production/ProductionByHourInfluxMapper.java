package org.lucoenergia.conluz.infrastructure.production;

import org.lucoenergia.conluz.domain.production.ProductionByHour;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.BasePointInfluxMapper;
import org.lucoenergia.conluz.infrastructure.shared.time.InstantToOffsetDateTimeConverter;
import org.springframework.stereotype.Component;

@Component
public class ProductionByHourInfluxMapper extends BasePointInfluxMapper<ProductionPoint, ProductionByHour> {

    private final InstantToOffsetDateTimeConverter converter;

    public ProductionByHourInfluxMapper(InstantToOffsetDateTimeConverter converter) {
        this.converter = converter;
    }

    public ProductionByHour map(ProductionPoint measurement) {
        return new ProductionByHour(converter.convert(measurement.getTime()), measurement.getInverterPower());
    }
}
