package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.production.get.GetEnergyStationRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionYearlyAggregationRepository;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiDisabledException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HuaweiProductionYearlyAggregationServiceTest {

    @Mock
    private GetEnergyStationRepository getEnergyStationRepository;

    @Mock
    private HuaweiProductionYearlyAggregationRepository aggregationRepository;

    @Mock
    private GetHuaweiConfigRepository getHuaweiConfigRepository;

    @Mock
    private GetPlantRepository getPlantRepository;

    @InjectMocks
    private HuaweiProductionYearlyAggregationServiceImpl service;

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
    void testAggregateYearlyForAllPlants() {
        Plant plant1 = PlantMother.random().build();
        Plant plant2 = PlantMother.random().build();
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(
                List.of(configForPlant(plant1.getId()), configForPlant(plant2.getId())));
        when(getEnergyStationRepository.findById(plant1.getId())).thenReturn(Optional.of(plant1));
        when(getEnergyStationRepository.findById(plant2.getId())).thenReturn(Optional.of(plant2));

        service.aggregateYearlyProductions(2024);

        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(plant1), eq(2024));
        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(plant2), eq(2024));
    }

    @Test
    void testAggregateYearlyHandlesRepositoryException() {
        Plant plant = PlantMother.random().build();
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(List.of(configForPlant(plant.getId())));
        when(getEnergyStationRepository.findById(plant.getId())).thenReturn(Optional.of(plant));
        doThrow(new RuntimeException("InfluxDB connection error"))
                .when(aggregationRepository)
                .aggregateYearlyProduction(any(Plant.class), anyInt());

        service.aggregateYearlyProductions(2024);

        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(plant), eq(2024));
    }

    @Test
    void testAggregateYearly_whenNoEnabledConfigs_thenSkip() {
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(Collections.emptyList());

        service.aggregateYearlyProductions(2024);

        verifyNoInteractions(getEnergyStationRepository);
        verifyNoInteractions(aggregationRepository);
    }

    @Test
    void testAggregateYearlyForAllPlants_whenPlantNotResolved_thenSkipsAggregation() {
        UUID missingPlantId = UUID.randomUUID();
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs())
                .thenReturn(List.of(configForPlant(missingPlantId)));
        when(getEnergyStationRepository.findById(missingPlantId)).thenReturn(Optional.empty());

        service.aggregateYearlyProductions(2024);

        verify(aggregationRepository, never()).aggregateYearlyProduction(any(Plant.class), anyInt());
    }

    @Test
    void testAggregateYearlyForAllPlants_whenConfigHasNoPlantId_thenSkipsAggregation() {
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs())
                .thenReturn(List.of(configForPlant(null)));

        service.aggregateYearlyProductions(2024);

        verify(getEnergyStationRepository, never()).findById(any(UUID.class));
        verify(aggregationRepository, never()).aggregateYearlyProduction(any(Plant.class), anyInt());
    }

    @Test
    void testAggregateYearlyForCommunity() {
        UUID communityId = UUID.randomUUID();
        Plant plant1 = PlantMother.random().build();
        Plant plant2 = PlantMother.random().build();
        when(getPlantRepository.findPlantCodesByCommunity(communityId))
                .thenReturn(List.of(plant1.getCode(), plant2.getCode()));
        when(getEnergyStationRepository.findByCode(plant1.getCode())).thenReturn(Optional.of(plant1));
        when(getEnergyStationRepository.findByCode(plant2.getCode())).thenReturn(Optional.of(plant2));

        service.aggregateYearlyProductions(communityId, 2024);

        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(plant1), eq(2024));
        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(plant2), eq(2024));
    }

    @Test
    void testAggregateYearlyForSpecificPlant() {
        UUID communityId = UUID.randomUUID();
        Plant plant = PlantMother.random().build();
        when(getEnergyStationRepository.findByCode(plant.getCode())).thenReturn(Optional.of(plant));
        when(getPlantRepository.findPlantCodesByCommunity(communityId)).thenReturn(List.of(plant.getCode()));

        service.aggregateYearlyProductions(communityId, plant.getCode(), 2024);

        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(plant), eq(2024));
    }

    @Test
    void testAggregateYearlyForSpecificPlant_whenPlantNotFound_thenThrows() {
        UUID communityId = UUID.randomUUID();
        String plantCode = "UNKNOWN";
        when(getEnergyStationRepository.findByCode(plantCode)).thenReturn(Optional.empty());

        assertThrows(PlantNotFoundException.class,
                () -> service.aggregateYearlyProductions(communityId, plantCode, 2024));

        verify(aggregationRepository, never()).aggregateYearlyProduction(any(Plant.class), anyInt());
    }

    @Test
    void testAggregateYearlyForSpecificPlant_whenPlantNotInCommunity_thenThrows() {
        UUID communityId = UUID.randomUUID();
        Plant plant = PlantMother.random().build();
        when(getEnergyStationRepository.findByCode(plant.getCode())).thenReturn(Optional.of(plant));
        when(getPlantRepository.findPlantCodesByCommunity(communityId)).thenReturn(Collections.emptyList());

        assertThrows(PlantNotFoundException.class,
                () -> service.aggregateYearlyProductions(communityId, plant.getCode(), 2024));

        verify(aggregationRepository, never()).aggregateYearlyProduction(any(Plant.class), anyInt());
    }

    // -----------------------------------------------------------------------
    // syncYearlyProductions: config gating + dispatch (moved out of the controller)
    // -----------------------------------------------------------------------

    @Test
    void testSyncYearlyThrowsWhenHuaweiDisabled() {
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(Collections.emptyList());

        assertThrows(HuaweiDisabledException.class,
                () -> service.syncYearlyProductions(UUID.randomUUID(), null, 2024));

        verifyNoInteractions(getEnergyStationRepository, getPlantRepository, aggregationRepository);
    }

    @Test
    void testSyncYearlyWithNoPlantAggregatesWholeCommunity() {
        UUID communityId = UUID.randomUUID();
        Plant plant = PlantMother.random().build();
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(List.of(configForPlant(UUID.randomUUID())));
        when(getPlantRepository.findPlantCodesByCommunity(communityId)).thenReturn(List.of(plant.getCode()));
        when(getEnergyStationRepository.findByCode(plant.getCode())).thenReturn(Optional.of(plant));

        service.syncYearlyProductions(communityId, null, 2024);

        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(plant), eq(2024));
    }

    @Test
    void testSyncYearlyWithPlantAggregatesThatPlant() {
        UUID communityId = UUID.randomUUID();
        Plant plant = PlantMother.random().build();
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(List.of(configForPlant(UUID.randomUUID())));
        when(getEnergyStationRepository.findByCode(plant.getCode())).thenReturn(Optional.of(plant));
        when(getPlantRepository.findPlantCodesByCommunity(communityId)).thenReturn(List.of(plant.getCode()));

        service.syncYearlyProductions(communityId, plant.getCode(), 2024);

        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(plant), eq(2024));
    }
}
