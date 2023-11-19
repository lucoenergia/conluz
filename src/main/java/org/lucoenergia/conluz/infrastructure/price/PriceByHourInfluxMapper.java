package org.lucoenergia.conluz.infrastructure.price;

import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.shared.BaseMapper;
import org.lucoenergia.conluz.infrastructure.shared.time.InstantToOffsetDateTimeConverter;
import org.springframework.stereotype.Component;

@Component
public class PriceByHourInfluxMapper extends BaseMapper<PriceByHourPoint, PriceByHour> {

    private final InstantToOffsetDateTimeConverter converter;

    public PriceByHourInfluxMapper(InstantToOffsetDateTimeConverter converter) {
        this.converter = converter;
    }

    public PriceByHour map(PriceByHourPoint point) {
        return new PriceByHour(point.getPrice1(), converter.convert(point.getTime()));
    }
}
