package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.production.get.GetEnergyStationRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionMonthlyAggregationRepository;
import org.lucoenergia.conluz.domain.production.huawei.config.GetHuaweiConfigurationService;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HuaweiProductionMonthlyAggregationServiceTest {

    @Mock
    private GetEnergyStationRepository getEnergyStationRepository;

    @Mock
    private HuaweiProductionMonthlyAggregationRepository aggregationRepository;

    @Mock
    private GetHuaweiConfigurationService getHuaweiConfigurationService;

    @InjectMocks
    private HuaweiProductionMonthlyAggregationServiceImpl service;

    private HuaweiConfig enabledConfig() {
        return new HuaweiConfig.Builder()
                .setUsername("u")
                .setPassword("p")
                .setBaseUrl(HuaweiConfig.DEFAULT_BASE_URL)
                .setEnabled(Boolean.TRUE)
                .build();
    }

    @Test
    void testAggregateMonthlyForAllPlantsAllMonths() {

        // Given
        Plant plant1 = PlantMother.random().build();
        Plant plant2 = PlantMother.random().build();
        when(getHuaweiConfigurationService.isDisabled()).thenReturn(false);
        when(getEnergyStationRepository.findAll()).thenReturn(List.of(plant1, plant2));

        // When
        service.aggregateMonthlyProductions(2024);

        // Then - 2 plants × 12 months = 24 calls
        verify(aggregationRepository, times(24))
                .aggregateMonthlyProduction(any(Plant.class), any(Month.class), eq(2024));
    }

    @Test
    void testAggregateMonthlyForAllPlantsSpecificMonth() {

        // Given
        Plant plant1 = PlantMother.random().build();
        Plant plant2 = PlantMother.random().build();
        when(getHuaweiConfigurationService.isDisabled()).thenReturn(false);
        when(getEnergyStationRepository.findAll()).thenReturn(List.of(plant1, plant2));

        // When
        service.aggregateMonthlyProductions(Month.DECEMBER, 2024);

        // Then - 2 plants × 1 month = 2 calls
        verify(aggregationRepository, times(2))
                .aggregateMonthlyProduction(any(Plant.class), eq(Month.DECEMBER), eq(2024));
    }

    @Test
    void testAggregateMonthlyForSpecificPlantAndMonth() {

        // Given
        Plant plant = PlantMother.random().build();
        when(getHuaweiConfigurationService.isDisabled()).thenReturn(false);
        when(getEnergyStationRepository.findByCode(plant.getCode())).thenReturn(Optional.of(plant));

        // When
        service.aggregateMonthlyProductions(plant.getCode(), Month.JUNE, 2024);

        // Then
        verify(aggregationRepository, times(1))
                .aggregateMonthlyProduction(eq(plant), eq(Month.JUNE), eq(2024));
    }

    @Test
    void testAggregateMonthlyWithPlantNotFound() {

        // Given
        String unknownCode = "UNKNOWN_PLANT";
        when(getHuaweiConfigurationService.isDisabled()).thenReturn(false);
        when(getEnergyStationRepository.findByCode(unknownCode)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(PlantNotFoundException.class, () ->
                service.aggregateMonthlyProductions(unknownCode, Month.JUNE, 2024));

        verify(aggregationRepository, never())
                .aggregateMonthlyProduction(any(Plant.class), any(Month.class), anyInt());
    }

    @Test
    void testAggregateMonthlyHandlesRepositoryException() {

        // Given
        Plant plant = PlantMother.random().build();
        when(getHuaweiConfigurationService.isDisabled()).thenReturn(false);
        when(getEnergyStationRepository.findAll()).thenReturn(List.of(plant));
        doThrow(new RuntimeException("InfluxDB connection error"))
                .when(aggregationRepository)
                .aggregateMonthlyProduction(any(Plant.class), any(Month.class), anyInt());

        // When - should not throw, just log error
        service.aggregateMonthlyProductions(Month.JUNE, 2024);

        // Then - attempted the call
        verify(aggregationRepository, times(1))
                .aggregateMonthlyProduction(eq(plant), eq(Month.JUNE), eq(2024));
    }

    @Test
    void testAggregateMonthlyWithEmptyPlantList() {

        // Given
        when(getHuaweiConfigurationService.isDisabled()).thenReturn(false);
        when(getEnergyStationRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        service.aggregateMonthlyProductions(2024);

        // Then
        verify(aggregationRepository, never())
                .aggregateMonthlyProduction(any(Plant.class), any(Month.class), anyInt());
    }

    @Test
    void testAggregateMonthly_whenDisabled_thenSkip() {

        // Given
        HuaweiConfig disabledConfig = new HuaweiConfig.Builder()
                .setUsername("u").setPassword("p").setEnabled(Boolean.FALSE).build();
        when(getHuaweiConfigurationService.isDisabled()).thenReturn(true);

        // When
        service.aggregateMonthlyProductions(2024);

        // Then
        verifyNoInteractions(getEnergyStationRepository);
        verifyNoInteractions(aggregationRepository);
    }

    @Test
    void testAggregateMonthly_whenNoConfig_thenSkip() {

        // Given
        when(getHuaweiConfigurationService.isDisabled()).thenReturn(true);

        // When
        service.aggregateMonthlyProductions(2024);

        // Then
        verifyNoInteractions(getEnergyStationRepository);
        verifyNoInteractions(aggregationRepository);
    }
}
