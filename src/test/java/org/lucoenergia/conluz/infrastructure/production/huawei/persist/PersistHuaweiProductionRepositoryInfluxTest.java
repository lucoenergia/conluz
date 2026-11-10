package org.lucoenergia.conluz.infrastructure.production.huawei.persist;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiProductionRepository;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterSnapshot;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

class PersistHuaweiProductionRepositoryInfluxTest extends BaseIntegrationTest {

    @Autowired
    private PersistHuaweiProductionRepositoryInflux persistHuaweiProductionRepositoryInflux;
    @Autowired
    private GetHuaweiProductionRepository getHuaweiProductionRepository;

    @Test
    void testPersistRealTimeProduction() {

        // Given
        OffsetDateTime currentTime = OffsetDateTime.now();
        RealTimeProduction realTimeProduction = new RealTimeProduction.Builder()
                .setTime(currentTime.toInstant())
                .setStationCode("SC")
                .setRealHealthState(1)
                .setDayPower(2.0)
                .setTotalPower(3.0)
                .setDayIncome(4.0)
                .setMonthPower(5.0)
                .setTotalIncome(6.0)
                .build();

        List<RealTimeProduction> realTimeProductions = Arrays.asList(realTimeProduction);

        // When
        persistHuaweiProductionRepositoryInflux.persistRealTimeProduction(realTimeProductions);

        // Then
        List<InverterSnapshot> result = getHuaweiProductionRepository.getProductionDay("SC",
                currentTime.minusHours(3).toInstant(), currentTime.minusHours(2).toInstant(),
                currentTime.plusHours(2).toInstant()).todaySnapshots();

        Assertions.assertEquals(
                List.of(new InverterSnapshot(currentTime.toInstant().truncatedTo(ChronoUnit.MILLIS), 2.0)), result);
    }
}
