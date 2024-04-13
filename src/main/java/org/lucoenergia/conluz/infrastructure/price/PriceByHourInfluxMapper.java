package org.lucoenergia.conluz.infrastructure.price;

import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.shared.BaseMapper;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Component;

@Component
public class PriceByHourInfluxMapper extends BaseMapper<PriceByHourPoint, PriceByHour> {

    private final DateConverter converter;

    public PriceByHourInfluxMapper(DateConverter converter) {
        this.converter = converter;
    }

    public PriceByHour map(PriceByHourPoint point) {
        return new PriceByHour(point.getPrice(), converter.convertInstantToOffsetDateTime(point.getTime()));
    }
}
