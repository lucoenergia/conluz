package org.lucoenergia.conluz.price;

import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.shared.time.InstantToOffsetDateTimeConverter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
public class PriceByHourInfluxMapper {

    private final InstantToOffsetDateTimeConverter converter;

    public PriceByHourInfluxMapper(InstantToOffsetDateTimeConverter converter) {
        this.converter = converter;
    }

    public PriceByHour map(PriceByHourPoint measurement) {
        return new PriceByHour(measurement.getPrice1(), converter.convert(measurement.getTime()));
    }

    public List<PriceByHour> mapList(List<PriceByHourPoint> measurements) {
        return measurements.stream()
                .map(new Function<PriceByHourPoint, PriceByHour>() {
                    @Override
                    public PriceByHour apply(PriceByHourPoint measurement) {
                        return map(measurement);
                    }
                })
                .toList();
    }
}
