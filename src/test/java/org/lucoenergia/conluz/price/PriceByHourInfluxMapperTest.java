package org.lucoenergia.conluz.price;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@SpringBootTest
public class PriceByHourInfluxMapperTest {

    @Autowired
    private PriceByHourInfluxMapper mapper;

    @Test
    void testMap() {

        Instant time = Instant.parse("2023-10-26T12:25:33Z");
        PriceByHourPoint point = new PriceByHourPoint(time, 22.2d, 22.2d);

        PriceByHour result = mapper.map(point);

        Assertions.assertEquals(point.getTime(), result.getHour().toInstant());
        Assertions.assertEquals(point.getPrice1(), result.getPrice());
    }

    @Test
    void testMapList() {

        Instant timeOne = Instant.parse("2023-10-26T12:00:00Z");
        PriceByHourPoint pointOne = new PriceByHourPoint(timeOne, 22.2d, 22.2d);

        Instant timeTwo = Instant.parse("2023-10-26T13:00:00Z");
        PriceByHourPoint pointTwo = new PriceByHourPoint(timeTwo, 33.3d, 33.3d);

        List<PriceByHour> result = mapper.mapList(Arrays.asList(pointOne, pointTwo));

        Assertions.assertEquals(2, result.size());

        Assertions.assertEquals(pointOne.getTime(), result.get(0).getHour().toInstant());
        Assertions.assertEquals(pointOne.getPrice1(), result.get(0).getPrice());

        Assertions.assertEquals(pointTwo.getTime(), result.get(1).getHour().toInstant());
        Assertions.assertEquals(pointTwo.getPrice1(), result.get(1).getPrice());
    }
}
