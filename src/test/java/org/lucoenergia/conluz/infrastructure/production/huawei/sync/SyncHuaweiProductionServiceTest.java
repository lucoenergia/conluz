package org.lucoenergia.conluz.infrastructure.production.huawei.sync;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.InverterProvider;
import org.lucoenergia.conluz.domain.production.get.GetEnergyStationRepository;
import org.lucoenergia.conluz.domain.production.huawei.HourlyProduction;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.config.GetHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.huawei.persist.PersistHuaweiProductionRepository;
import org.lucoenergia.conluz.domain.production.huawei.sync.SyncHuaweiProductionService;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.infrastructure.production.huawei.get.GetHuaweiHourlyProductionRepositoryRest;
import org.lucoenergia.conluz.infrastructure.production.huawei.get.GetHuaweiRealTimeProductionRepositoryRest;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SyncHuaweiProductionServiceTest {

    private final PersistHuaweiProductionRepository persistHuaweiProductionRepository = Mockito.mock(PersistHuaweiProductionRepository.class);
    private final GetHuaweiHourlyProductionRepositoryRest getHuaweiHourlyProductionRepositoryRest = Mockito.mock(GetHuaweiHourlyProductionRepositoryRest.class);
    private final GetHuaweiRealTimeProductionRepositoryRest getHuaweiRealTimeProductionRepositoryRest = Mockito.mock(GetHuaweiRealTimeProductionRepositoryRest.class);
    private final GetEnergyStationRepository getEnergyStationRepository = Mockito.mock(GetEnergyStationRepository.class);
    private final GetHuaweiConfigRepository getHuaweiConfigRepository = Mockito.mock(GetHuaweiConfigRepository.class);
    private final GetHuaweiConfigurationService getHuaweiConfigurationService = Mockito.mock(GetHuaweiConfigurationService.class);
    private final SyncHuaweiProductionService syncHuaweiProductionService = new SyncHuaweiProductionServiceImpl(
            persistHuaweiProductionRepository, getHuaweiRealTimeProductionRepositoryRest,
            getHuaweiHourlyProductionRepositoryRest, getEnergyStationRepository, getHuaweiConfigRepository,
            getHuaweiConfigurationService);

    private HuaweiConfig enabledConfig() {
        return new HuaweiConfig.Builder()
                .setUsername("u")
                .setPassword("p")
                .setBaseUrl(HuaweiConfig.DEFAULT_BASE_URL)
                .setEnabled(Boolean.TRUE)
                .build();
    }

    @Test
    void givenValidDatesAndConfig_whenSyncHourlyProduction_thenPersistProductions() {
        OffsetDateTime startDate = OffsetDateTime.now().minusDays(1);
        OffsetDateTime endDate = OffsetDateTime.now();
        List<Plant> plants = List.of(new Plant());
        List<HourlyProduction> productions = new ArrayList<>();

        when(getHuaweiConfigRepository.getHuaweiConfig()).thenReturn(Optional.of(enabledConfig()));
        when(getEnergyStationRepository.findAllByInverterProvider(InverterProvider.HUAWEI)).thenReturn(plants);
        when(getHuaweiHourlyProductionRepositoryRest.getHourlyProductionByDateInterval(
                eq(plants), eq(startDate), eq(endDate), anyString())).thenReturn(productions);

        syncHuaweiProductionService.syncHourlyProduction(startDate, endDate);

        verify(persistHuaweiProductionRepository, times(1)).persistHourlyProduction(productions);
    }

    @Test
    void givenStartDateAfterEndDate_whenSyncHourlyProduction_thenDoNotPersist() {
        OffsetDateTime startDate = OffsetDateTime.now();
        OffsetDateTime endDate = OffsetDateTime.now().minusDays(1);

        when(getHuaweiConfigRepository.getHuaweiConfig()).thenReturn(Optional.of(enabledConfig()));

        syncHuaweiProductionService.syncHourlyProduction(startDate, endDate);

        verifyNoInteractions(persistHuaweiProductionRepository);
        verifyNoInteractions(getHuaweiHourlyProductionRepositoryRest);
    }

    @Test
    void givenDisabledConfig_whenSyncHourlyProduction_thenDoNotPersist() {
        OffsetDateTime startDate = OffsetDateTime.now().minusDays(1);
        OffsetDateTime endDate = OffsetDateTime.now();
        HuaweiConfig disabledConfig = new HuaweiConfig.Builder()
                .setUsername("u").setPassword("p").setEnabled(Boolean.FALSE).build();

        when(getHuaweiConfigRepository.getHuaweiConfig()).thenReturn(Optional.of(disabledConfig));

        syncHuaweiProductionService.syncHourlyProduction(startDate, endDate);

        verifyNoInteractions(persistHuaweiProductionRepository);
        verifyNoInteractions(getHuaweiHourlyProductionRepositoryRest);
    }

    @Test
    void givenDisabledConfig_whenSyncRealTimeProduction_thenDoNotPersist() {
        HuaweiConfig disabledConfig = new HuaweiConfig.Builder()
                .setUsername("u").setPassword("p").setEnabled(Boolean.FALSE).build();

        when(getHuaweiConfigRepository.getHuaweiConfig()).thenReturn(Optional.of(disabledConfig));

        syncHuaweiProductionService.syncRealTimeProduction();

        verifyNoInteractions(persistHuaweiProductionRepository);
        verifyNoInteractions(getHuaweiRealTimeProductionRepositoryRest);
    }

    @Test
    void givenEmptyPlants_whenSyncHourlyProduction_thenDoNotPersist() {
        OffsetDateTime startDate = OffsetDateTime.now().minusDays(1);
        OffsetDateTime endDate = OffsetDateTime.now();

        when(getHuaweiConfigRepository.getHuaweiConfig()).thenReturn(Optional.of(enabledConfig()));
        when(getEnergyStationRepository.findAllByInverterProvider(InverterProvider.HUAWEI)).thenReturn(List.of());

        syncHuaweiProductionService.syncHourlyProduction(startDate, endDate);

        verifyNoInteractions(persistHuaweiProductionRepository);
        verifyNoInteractions(getHuaweiHourlyProductionRepositoryRest);
    }
}
