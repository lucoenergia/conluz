package org.lucoenergia.conluz.infrastructure.production.huawei.persist;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;
import org.lucoenergia.conluz.infrastructure.production.get.GetHuaweiProductionRepositoryInflux;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

class PersistHuaweiProductionRepositoryInfluxTest extends BaseIntegrationTest {

    @Autowired
    private PersistHuaweiProductionRepositoryInflux persistHuaweiProductionRepositoryInflux;
    @Autowired
    private GetHuaweiProductionRepositoryInflux getHuaweiProductionRepositoryInflux;

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
        List<RealTimeProduction> result = getHuaweiProductionRepositoryInflux.getRealTimeProductionByRangeOfDates(
                currentTime.minusHours(2).toInstant(), currentTime.plusHours(2).toInstant());

        Assertions.assertEquals(1, result.size());
    }
}
