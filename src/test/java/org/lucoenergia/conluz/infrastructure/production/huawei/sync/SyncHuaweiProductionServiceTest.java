package org.lucoenergia.conluz.infrastructure.production.huawei.sync;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.get.GetEnergyStationRepository;
import org.lucoenergia.conluz.domain.production.huawei.HourlyProduction;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.huawei.persist.PersistHuaweiProductionRepository;
import org.lucoenergia.conluz.domain.production.huawei.sync.SyncHuaweiProductionService;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.infrastructure.production.huawei.get.GetHuaweiHourlyProductionRepositoryRest;
import org.lucoenergia.conluz.infrastructure.production.huawei.get.GetHuaweiRealTimeProductionRepositoryRest;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SyncHuaweiProductionServiceTest {

    private final PersistHuaweiProductionRepository persistHuaweiProductionRepository = Mockito.mock(PersistHuaweiProductionRepository.class);
    private final GetHuaweiHourlyProductionRepositoryRest getHuaweiHourlyProductionRepositoryRest = Mockito.mock(GetHuaweiHourlyProductionRepositoryRest.class);
    private final GetHuaweiRealTimeProductionRepositoryRest getHuaweiRealTimeProductionRepositoryRest = Mockito.mock(GetHuaweiRealTimeProductionRepositoryRest.class);
    private final GetEnergyStationRepository getEnergyStationRepository = Mockito.mock(GetEnergyStationRepository.class);
    private final GetHuaweiConfigRepository getHuaweiConfigRepository = Mockito.mock(GetHuaweiConfigRepository.class);
    private final SyncHuaweiProductionService syncHuaweiProductionService = new SyncHuaweiProductionServiceImpl(
            persistHuaweiProductionRepository, getHuaweiRealTimeProductionRepositoryRest,
            getHuaweiHourlyProductionRepositoryRest, getEnergyStationRepository, getHuaweiConfigRepository);

    private HuaweiConfig enabledConfigForPlant(Plant plant) {
        return new HuaweiConfig.Builder()
                .setUsername("u")
                .setPassword("p")
                .setBaseUrl(HuaweiConfig.DEFAULT_BASE_URL)
                .setEnabled(Boolean.TRUE)
                .setPlantId(plant.getId())
                .build();
    }

    @Test
    void givenValidDatesAndConfig_whenSyncHourlyProduction_thenPersistProductions() {
        OffsetDateTime startDate = OffsetDateTime.now().minusDays(1);
        OffsetDateTime endDate = OffsetDateTime.now();
        Plant plant = PlantMother.random().build();
        List<HourlyProduction> productions = new ArrayList<>();

        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(List.of(enabledConfigForPlant(plant)));
        when(getEnergyStationRepository.findById(plant.getId())).thenReturn(Optional.of(plant));
        when(getHuaweiHourlyProductionRepositoryRest.getHourlyProductionByDateInterval(
                anyList(), eq(startDate), eq(endDate), anyString(), anyString(), anyString())).thenReturn(productions);

        syncHuaweiProductionService.syncHourlyProduction(startDate, endDate);

        verify(persistHuaweiProductionRepository, times(1)).persistHourlyProduction(productions);
    }

    @Test
    void givenStartDateAfterEndDate_whenSyncHourlyProduction_thenDoNotPersist() {
        OffsetDateTime startDate = OffsetDateTime.now();
        OffsetDateTime endDate = OffsetDateTime.now().minusDays(1);

        syncHuaweiProductionService.syncHourlyProduction(startDate, endDate);

        verifyNoInteractions(persistHuaweiProductionRepository);
        verifyNoInteractions(getHuaweiHourlyProductionRepositoryRest);
    }

    @Test
    void givenNoEnabledConfigs_whenSyncHourlyProduction_thenDoNotPersist() {
        OffsetDateTime startDate = OffsetDateTime.now().minusDays(1);
        OffsetDateTime endDate = OffsetDateTime.now();

        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(Collections.emptyList());

        syncHuaweiProductionService.syncHourlyProduction(startDate, endDate);

        verifyNoInteractions(persistHuaweiProductionRepository);
        verifyNoInteractions(getHuaweiHourlyProductionRepositoryRest);
    }

    @Test
    void givenNoEnabledConfigs_whenSyncRealTimeProduction_thenDoNotPersist() {
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(Collections.emptyList());

        syncHuaweiProductionService.syncRealTimeProduction();

        verifyNoInteractions(persistHuaweiProductionRepository);
        verifyNoInteractions(getHuaweiRealTimeProductionRepositoryRest);
    }

    @Test
    void givenConfigWithNullPlantId_whenSyncHourlyProduction_thenSkipThatConfig() {
        OffsetDateTime startDate = OffsetDateTime.now().minusDays(1);
        OffsetDateTime endDate = OffsetDateTime.now();
        HuaweiConfig configWithNoPlant = new HuaweiConfig.Builder()
                .setUsername("u").setPassword("p")
                .setBaseUrl(HuaweiConfig.DEFAULT_BASE_URL)
                .setEnabled(Boolean.TRUE)
                .setPlantId(null)
                .build();

        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(List.of(configWithNoPlant));

        syncHuaweiProductionService.syncHourlyProduction(startDate, endDate);

        verifyNoInteractions(persistHuaweiProductionRepository);
        verifyNoInteractions(getHuaweiHourlyProductionRepositoryRest);
    }
}
