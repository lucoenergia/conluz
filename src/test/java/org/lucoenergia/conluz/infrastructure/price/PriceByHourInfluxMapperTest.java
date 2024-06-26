package org.lucoenergia.conluz.infrastructure.price;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceByHourInfluxMapperTest {

    @InjectMocks
    private PriceByHourInfluxMapper mapper;
    @Mock
    private DateConverter converter;

    @Test
    void testMap() {

        Instant time = Instant.parse("2023-10-26T12:25:33Z");
        when(converter.convertInstantToOffsetDateTime(time)).thenReturn(time.atOffset(ZoneOffset.ofHours(1)));

        PriceByHourPoint point = new PriceByHourPoint(time, 22.2d);

        PriceByHour result = mapper.map(point);

        Assertions.assertEquals(point.getTime(), result.getHour().toInstant());
        Assertions.assertEquals(point.getPrice(), result.getPrice());
    }

    @Test
    void testMapList() {

        Instant timeOne = Instant.parse("2023-10-26T12:00:00Z");
        when(converter.convertInstantToOffsetDateTime(timeOne)).thenReturn(timeOne.atOffset(ZoneOffset.ofHours(1)));
        PriceByHourPoint pointOne = new PriceByHourPoint(timeOne, 22.2d);

        Instant timeTwo = Instant.parse("2023-10-26T13:00:00Z");
        when(converter.convertInstantToOffsetDateTime(timeTwo)).thenReturn(timeTwo.atOffset(ZoneOffset.ofHours(1)));
        PriceByHourPoint pointTwo = new PriceByHourPoint(timeTwo, 33.3d);

        List<PriceByHour> result = mapper.mapList(Arrays.asList(pointOne, pointTwo));

        Assertions.assertEquals(2, result.size());

        Assertions.assertEquals(pointOne.getTime(), result.get(0).getHour().toInstant());
        Assertions.assertEquals(pointOne.getPrice(), result.get(0).getPrice());

        Assertions.assertEquals(pointTwo.getTime(), result.get(1).getHour().toInstant());
        Assertions.assertEquals(pointTwo.getPrice(), result.get(1).getPrice());
    }
}
