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
    void testAggregateYearlyForSpecificPlant() {
        Plant plant = PlantMother.random().build();
        when(getEnergyStationRepository.findByCode(plant.getCode())).thenReturn(Optional.of(plant));

        service.aggregateYearlyProductions(plant.getCode(), 2024);

        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(plant), eq(2024));
    }

    @Test
    void testAggregateYearlyWithPlantNotFound() {
        String unknownCode = "UNKNOWN_PLANT";
        when(getEnergyStationRepository.findByCode(unknownCode)).thenReturn(Optional.empty());

        assertThrows(PlantNotFoundException.class, () ->
                service.aggregateYearlyProductions(unknownCode, 2024));

        verify(aggregationRepository, never()).aggregateYearlyProduction(any(Plant.class), anyInt());
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
    void testAggregateYearlyWithNoEnabledConfigs() {
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(Collections.emptyList());

        service.aggregateYearlyProductions(2024);

        verifyNoInteractions(getEnergyStationRepository);
        verifyNoInteractions(aggregationRepository);
    }

    @Test
    void testAggregateYearly_whenNoEnabledConfigs_thenSkip() {
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(Collections.emptyList());

        service.aggregateYearlyProductions(2024);

        verifyNoInteractions(getEnergyStationRepository);
        verifyNoInteractions(aggregationRepository);
    }
}
