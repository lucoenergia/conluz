package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.production.get.GetEnergyStationRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionMonthlyAggregationRepository;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiDisabledException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Month;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HuaweiProductionMonthlyAggregationServiceTest {

    @Mock
    private GetEnergyStationRepository getEnergyStationRepository;

    @Mock
    private HuaweiProductionMonthlyAggregationRepository aggregationRepository;

    @Mock
    private GetHuaweiConfigRepository getHuaweiConfigRepository;

    @Mock
    private GetPlantRepository getPlantRepository;

    @InjectMocks
    private HuaweiProductionMonthlyAggregationServiceImpl service;

    private HuaweiConfig configForPlant(UUID plantId) {
        return new HuaweiConfig.Builder()
                .setUsername("u")
                .setPassword("p")
                .setBaseUrl(HuaweiConfig.DEFAULT_BASE_URL)
                .setEnabled(Boolean.TRUE)
                .setPlantId(plantId)
                .build();
    }

    @Test
    void testAggregateMonthlyForAllPlantsSpecificMonth() {
        Plant plant1 = PlantMother.random().build();
        Plant plant2 = PlantMother.random().build();
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(
                List.of(configForPlant(plant1.getId()), configForPlant(plant2.getId())));
        when(getEnergyStationRepository.findById(plant1.getId())).thenReturn(Optional.of(plant1));
        when(getEnergyStationRepository.findById(plant2.getId())).thenReturn(Optional.of(plant2));

        service.aggregateMonthlyProductions(Month.DECEMBER, 2024);

        verify(aggregationRepository, times(2))
                .aggregateMonthlyProduction(any(Plant.class), eq(Month.DECEMBER), eq(2024));
    }

    @Test
    void testAggregateMonthlyForAllPlants_whenNoEnabledConfigs_thenSkip() {
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(Collections.emptyList());

        service.aggregateMonthlyProductions(Month.MARCH, 2024);

        verifyNoInteractions(getEnergyStationRepository);
        verifyNoInteractions(aggregationRepository);
    }

    @Test
    void testAggregateMonthlyForAllPlants_whenPlantNotResolved_thenSkipsAggregation() {
        UUID missingPlantId = UUID.randomUUID();
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs())
                .thenReturn(List.of(configForPlant(missingPlantId)));
        when(getEnergyStationRepository.findById(missingPlantId)).thenReturn(Optional.empty());

        service.aggregateMonthlyProductions(Month.MAY, 2024);

        verify(aggregationRepository, never())
                .aggregateMonthlyProduction(any(Plant.class), any(Month.class), anyInt());
    }

    @Test
    void testAggregateMonthlyForAllPlants_whenConfigHasNoPlantId_thenSkipsAggregation() {
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs())
                .thenReturn(List.of(configForPlant(null)));

        service.aggregateMonthlyProductions(Month.MAY, 2024);

        verify(getEnergyStationRepository, never()).findById(any(UUID.class));
        verify(aggregationRepository, never())
                .aggregateMonthlyProduction(any(Plant.class), any(Month.class), anyInt());
    }

    @Test
    void testAggregateMonthlyHandlesRepositoryException() {
        Plant plant = PlantMother.random().build();
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(List.of(configForPlant(plant.getId())));
        when(getEnergyStationRepository.findById(plant.getId())).thenReturn(Optional.of(plant));
        doThrow(new RuntimeException("InfluxDB connection error"))
                .when(aggregationRepository)
                .aggregateMonthlyProduction(any(Plant.class), any(Month.class), anyInt());

        service.aggregateMonthlyProductions(Month.JUNE, 2024);

        verify(aggregationRepository, times(1))
                .aggregateMonthlyProduction(eq(plant), eq(Month.JUNE), eq(2024));
    }

    @Test
    void testAggregateMonthlyForCommunityWholeYear() {
        UUID communityId = UUID.randomUUID();
        Plant plant1 = PlantMother.random().build();
        Plant plant2 = PlantMother.random().build();
        when(getPlantRepository.findPlantCodesByCommunity(communityId))
                .thenReturn(List.of(plant1.getCode(), plant2.getCode()));
        when(getEnergyStationRepository.findByCode(plant1.getCode())).thenReturn(Optional.of(plant1));
        when(getEnergyStationRepository.findByCode(plant2.getCode())).thenReturn(Optional.of(plant2));

        service.aggregateMonthlyProductions(communityId, 2024);

        // 2 plants × 12 months = 24 calls
        verify(aggregationRepository, times(12))
                .aggregateMonthlyProduction(eq(plant1), any(Month.class), eq(2024));
        verify(aggregationRepository, times(12))
                .aggregateMonthlyProduction(eq(plant2), any(Month.class), eq(2024));
    }

    @Test
    void testAggregateMonthlyForCommunitySpecificMonth() {
        UUID communityId = UUID.randomUUID();
        Plant plant1 = PlantMother.random().build();
        Plant plant2 = PlantMother.random().build();
        when(getPlantRepository.findPlantCodesByCommunity(communityId))
                .thenReturn(List.of(plant1.getCode(), plant2.getCode()));
        when(getEnergyStationRepository.findByCode(plant1.getCode())).thenReturn(Optional.of(plant1));
        when(getEnergyStationRepository.findByCode(plant2.getCode())).thenReturn(Optional.of(plant2));

        service.aggregateMonthlyProductions(communityId, Month.JULY, 2024);

        verify(aggregationRepository, times(1))
                .aggregateMonthlyProduction(eq(plant1), eq(Month.JULY), eq(2024));
        verify(aggregationRepository, times(1))
                .aggregateMonthlyProduction(eq(plant2), eq(Month.JULY), eq(2024));
    }

    @Test
    void testAggregateMonthlyForSpecificPlant() {
        UUID communityId = UUID.randomUUID();
        Plant plant = PlantMother.random().build();
        when(getEnergyStationRepository.findByCode(plant.getCode())).thenReturn(Optional.of(plant));
        when(getPlantRepository.findPlantCodesByCommunity(communityId)).thenReturn(List.of(plant.getCode()));

        service.aggregateMonthlyProductions(communityId, plant.getCode(), Month.AUGUST, 2024);

        verify(aggregationRepository, times(1))
                .aggregateMonthlyProduction(eq(plant), eq(Month.AUGUST), eq(2024));
    }

    @Test
    void testAggregateMonthlyForSpecificPlant_whenPlantNotFound_thenThrows() {
        UUID communityId = UUID.randomUUID();
        String plantCode = "UNKNOWN";
        when(getEnergyStationRepository.findByCode(plantCode)).thenReturn(Optional.empty());

        assertThrows(PlantNotFoundException.class,
                () -> service.aggregateMonthlyProductions(communityId, plantCode, Month.AUGUST, 2024));

        verify(aggregationRepository, never())
                .aggregateMonthlyProduction(any(Plant.class), any(Month.class), anyInt());
    }

    @Test
    void testAggregateMonthlyForSpecificPlant_whenPlantNotInCommunity_thenThrows() {
        UUID communityId = UUID.randomUUID();
        Plant plant = PlantMother.random().build();
        when(getEnergyStationRepository.findByCode(plant.getCode())).thenReturn(Optional.of(plant));
        when(getPlantRepository.findPlantCodesByCommunity(communityId)).thenReturn(Collections.emptyList());

        assertThrows(PlantNotFoundException.class,
                () -> service.aggregateMonthlyProductions(communityId, plant.getCode(), Month.AUGUST, 2024));

        verify(aggregationRepository, never())
                .aggregateMonthlyProduction(any(Plant.class), any(Month.class), anyInt());
    }

    // -----------------------------------------------------------------------
    // syncMonthlyProductions: config gating + dispatch (moved out of the controller)
    // -----------------------------------------------------------------------

    @Test
    void testSyncMonthlyThrowsWhenHuaweiDisabled() {
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(Collections.emptyList());

        assertThrows(HuaweiDisabledException.class,
                () -> service.syncMonthlyProductions(UUID.randomUUID(), null, null, 2024));

        verifyNoInteractions(getEnergyStationRepository, getPlantRepository, aggregationRepository);
    }

    @Test
    void testSyncMonthlyWithNoPlantNoMonthAggregatesWholeCommunityYear() {
        UUID communityId = UUID.randomUUID();
        Plant plant = PlantMother.random().build();
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(List.of(configForPlant(UUID.randomUUID())));
        when(getPlantRepository.findPlantCodesByCommunity(communityId)).thenReturn(List.of(plant.getCode()));
        when(getEnergyStationRepository.findByCode(plant.getCode())).thenReturn(Optional.of(plant));

        service.syncMonthlyProductions(communityId, null, null, 2024);

        verify(aggregationRepository, times(12)).aggregateMonthlyProduction(eq(plant), any(Month.class), eq(2024));
    }

    @Test
    void testSyncMonthlyWithPlantAndMonthAggregatesThatPlantForThatMonth() {
        UUID communityId = UUID.randomUUID();
        Plant plant = PlantMother.random().build();
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(List.of(configForPlant(UUID.randomUUID())));
        when(getEnergyStationRepository.findByCode(plant.getCode())).thenReturn(Optional.of(plant));
        when(getPlantRepository.findPlantCodesByCommunity(communityId)).thenReturn(List.of(plant.getCode()));

        service.syncMonthlyProductions(communityId, plant.getCode(), 6, 2024);

        verify(aggregationRepository, times(1)).aggregateMonthlyProduction(eq(plant), eq(Month.JUNE), eq(2024));
    }
}
