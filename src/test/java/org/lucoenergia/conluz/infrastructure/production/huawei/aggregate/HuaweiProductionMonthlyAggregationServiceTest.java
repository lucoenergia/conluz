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
    void testAggregateMonthlyForAllPlantsAllMonths() {
        Plant plant1 = PlantMother.random().build();
        Plant plant2 = PlantMother.random().build();
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(
                List.of(configForPlant(plant1.getId()), configForPlant(plant2.getId())));
        when(getEnergyStationRepository.findById(plant1.getId())).thenReturn(Optional.of(plant1));
        when(getEnergyStationRepository.findById(plant2.getId())).thenReturn(Optional.of(plant2));

        service.aggregateMonthlyProductions(2024);

        // 2 plants × 12 months = 24 calls
        verify(aggregationRepository, times(24))
                .aggregateMonthlyProduction(any(Plant.class), any(Month.class), eq(2024));
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
    void testAggregateMonthlyForSpecificPlantAndMonth() {
        Plant plant = PlantMother.random().build();
        when(getEnergyStationRepository.findByCode(plant.getCode())).thenReturn(Optional.of(plant));

        service.aggregateMonthlyProductions(plant.getCode(), Month.JUNE, 2024);

        verify(aggregationRepository, times(1))
                .aggregateMonthlyProduction(eq(plant), eq(Month.JUNE), eq(2024));
    }

    @Test
    void testAggregateMonthlyWithPlantNotFound() {
        String unknownCode = "UNKNOWN_PLANT";
        when(getEnergyStationRepository.findByCode(unknownCode)).thenReturn(Optional.empty());

        assertThrows(PlantNotFoundException.class, () ->
                service.aggregateMonthlyProductions(unknownCode, Month.JUNE, 2024));

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
    void testAggregateMonthlyWithNoEnabledConfigs() {
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(Collections.emptyList());

        service.aggregateMonthlyProductions(2024);

        verifyNoInteractions(getEnergyStationRepository);
        verifyNoInteractions(aggregationRepository);
    }

    @Test
    void testAggregateMonthly_whenNoEnabledConfigs_thenSkip() {
        when(getHuaweiConfigRepository.getEnabledHuaweiConfigs()).thenReturn(Collections.emptyList());

        service.aggregateMonthlyProductions(2024);

        verifyNoInteractions(getEnergyStationRepository);
        verifyNoInteractions(aggregationRepository);
    }
}
