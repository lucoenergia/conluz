package org.lucoenergia.conluz.infrastructure.production.get;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.huawei.HourlyProduction;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.infrastructure.production.huawei.config.HuaweiConfigEntity;
import org.lucoenergia.conluz.infrastructure.production.huawei.config.HuaweiConfigRepository;
import org.lucoenergia.conluz.infrastructure.production.huawei.get.GetHuaweiHourlyProductionRepositoryRest;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("This test must be enabled just to do a test with real data")
@Transactional
class GetHuaweiHourlyProductionRepositoryRestIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GetHuaweiHourlyProductionRepositoryRest repository;
    @Autowired
    private HuaweiConfigRepository huaweiConfigRepository;

    @Test
    void getHourlyProduction_shouldReturnProductionByDateIntervalWhenStationCodesIsNotEmpty() {
        // Given
        String stationCode = "code";
        List<Plant> stationCodes = List.of(new Plant.Builder().withCode(stationCode).build());

        String username = "username";
        String password = "password";

        HuaweiConfigEntity huaweiConfigEntity = new HuaweiConfigEntity();
        huaweiConfigEntity.setId(UUID.randomUUID());
        huaweiConfigEntity.setUsername(username);
        huaweiConfigEntity.setPassword(password);
        huaweiConfigRepository.save(huaweiConfigEntity);

        // Calculate date interval to get data
        OffsetDateTime today = OffsetDateTime.now();
        OffsetDateTime todayMinusOneWeek = today.minusWeeks(1);

        // When
        List<HourlyProduction> realTimeProductions = repository.getHourlyProductionByDateInterval(stationCodes, today,
                todayMinusOneWeek);

        // Then
        assertEquals(1, realTimeProductions.size());

        assertEquals(stationCode, realTimeProductions.get(0).getStationCode());
//        assertEquals(0.000d, realTimeProductions.get(0).getDayIncome());
//        assertEquals(10000d, realTimeProductions.get(0).getDayPower());
//        assertEquals(900.000d, realTimeProductions.get(0).getMonthPower());
//        assertEquals(2088.000d, realTimeProductions.get(0).getTotalIncome());
//        assertEquals(900.000d, realTimeProductions.get(0).getTotalPower());
//        assertEquals(3, realTimeProductions.get(0).getRealHealthState());
    }
}