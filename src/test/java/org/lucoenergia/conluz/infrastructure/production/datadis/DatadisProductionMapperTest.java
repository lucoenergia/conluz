package org.lucoenergia.conluz.infrastructure.production.datadis;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.production.datadis.DatadisProduction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatadisProductionMapperTest {

    private final DatadisProductionMapper mapper = new DatadisProductionMapper();

    @Test
    void map_setsProductionFromSurplusAndCopiesTheOtherFields() {
        DatadisConsumption consumption = consumption("ES001", "2024/01/10", "10:00", "Real", 2.5f);

        DatadisProduction production = mapper.map(consumption);

        assertEquals("ES001", production.getCups());
        assertEquals("2024/01/10", production.getDate());
        assertEquals("10:00", production.getTime());
        assertEquals("Real", production.getObtainMethod());
        assertEquals(2.5f, production.getProductionKWh());
    }

    @Test
    void map_convertsNullSurplusToZero() {
        DatadisConsumption consumption = consumption("ES001", "2024/01/10", "10:00", "Real", null);

        DatadisProduction production = mapper.map(consumption);

        assertEquals(0f, production.getProductionKWh());
    }

    private DatadisConsumption consumption(String cups, String date, String time, String obtainMethod, Float surplus) {
        DatadisConsumption consumption = new DatadisConsumption();
        consumption.setCups(cups);
        consumption.setDate(date);
        consumption.setTime(time);
        consumption.setObtainMethod(obtainMethod);
        consumption.setSurplusEnergyKWh(surplus);
        return consumption;
    }
}
