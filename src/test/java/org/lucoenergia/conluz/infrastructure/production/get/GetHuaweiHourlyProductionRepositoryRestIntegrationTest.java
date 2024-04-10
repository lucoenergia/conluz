package org.lucoenergia.conluz.infrastructure.production.get;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.EnergyStation;
import org.lucoenergia.conluz.domain.production.huawei.HourlyProduction;
import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;
import org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiConfigEntity;
import org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiConfigRepository;
import org.lucoenergia.conluz.infrastructure.production.huawei.get.GetHuaweiHourlyProductionRepositoryRest;
import org.lucoenergia.conluz.infrastructure.production.huawei.get.GetHuaweiRealTimeProductionRepositoryRest;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

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
    void getHourlyProduction_shouldReturnProductionWhenStationCodesIsNotEmpty() {
        // Given
        String stationCode = "code";
        List<EnergyStation> stationCodes = List.of(new EnergyStation.Builder().withCode(stationCode).build());

        String username = "username";
        String password = "password";

        HuaweiConfigEntity huaweiConfigEntity = new HuaweiConfigEntity();
        huaweiConfigEntity.setId(UUID.randomUUID());
        huaweiConfigEntity.setUsername(username);
        huaweiConfigEntity.setPassword(password);
        huaweiConfigRepository.save(huaweiConfigEntity);

        // When
        List<HourlyProduction> realTimeProductions = repository.getHourlyProduction(stationCodes);

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