package org.lucoenergia.conluz.infrastructure.production.huawei.aggregate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.production.get.GetEnergyStationRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.aggregate.HuaweiProductionYearlyAggregationRepository;
import org.lucoenergia.conluz.domain.production.huawei.config.GetHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HuaweiProductionYearlyAggregationServiceTest {

    @Mock
    private GetEnergyStationRepository getEnergyStationRepository;

    @Mock
    private HuaweiProductionYearlyAggregationRepository aggregationRepository;

    @Mock
    private GetHuaweiConfigurationService getHuaweiConfigurationService;

    @InjectMocks
    private HuaweiProductionYearlyAggregationServiceImpl service;

    private HuaweiConfig enabledConfig() {
        return new HuaweiConfig.Builder()
                .setUsername("u")
                .setPassword("p")
                .setBaseUrl(HuaweiConfig.DEFAULT_BASE_URL)
                .setEnabled(Boolean.TRUE)
                .build();
    }

    @Test
    void testAggregateYearlyForAllPlants() {

        // Given
        Plant plant1 = PlantMother.random().build();
        Plant plant2 = PlantMother.random().build();
        when(getHuaweiConfigurationService.isDisabled()).thenReturn(false);
        when(getEnergyStationRepository.findAll()).thenReturn(List.of(plant1, plant2));

        // When
        service.aggregateYearlyProductions(2024);

        // Then - one call per plant
        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(plant1), eq(2024));
        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(plant2), eq(2024));
    }

    @Test
    void testAggregateYearlyForSpecificPlant() {

        // Given
        Plant plant = PlantMother.random().build();
        when(getHuaweiConfigurationService.isDisabled()).thenReturn(false);
        when(getEnergyStationRepository.findByCode(plant.getCode())).thenReturn(Optional.of(plant));

        // When
        service.aggregateYearlyProductions(plant.getCode(), 2024);

        // Then
        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(plant), eq(2024));
    }

    @Test
    void testAggregateYearlyWithPlantNotFound() {

        // Given
        String unknownCode = "UNKNOWN_PLANT";
        when(getHuaweiConfigurationService.isDisabled()).thenReturn(false);
        when(getEnergyStationRepository.findByCode(unknownCode)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(PlantNotFoundException.class, () ->
                service.aggregateYearlyProductions(unknownCode, 2024));

        verify(aggregationRepository, never()).aggregateYearlyProduction(any(Plant.class), anyInt());
    }

    @Test
    void testAggregateYearlyHandlesRepositoryException() {

        // Given
        Plant plant = PlantMother.random().build();
        when(getHuaweiConfigurationService.isDisabled()).thenReturn(false);
        when(getEnergyStationRepository.findAll()).thenReturn(List.of(plant));
        doThrow(new RuntimeException("InfluxDB connection error"))
                .when(aggregationRepository)
                .aggregateYearlyProduction(any(Plant.class), anyInt());

        // When - should not throw, just log error
        service.aggregateYearlyProductions(2024);

        // Then - attempted the call
        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(plant), eq(2024));
    }

    @Test
    void testAggregateYearlyWithEmptyPlantList() {

        // Given
        when(getHuaweiConfigurationService.isDisabled()).thenReturn(false);
        when(getEnergyStationRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        service.aggregateYearlyProductions(2024);

        // Then
        verify(aggregationRepository, never()).aggregateYearlyProduction(any(Plant.class), anyInt());
    }

    @Test
    void testAggregateYearly_whenDisabled_thenSkip() {

        // Given
        HuaweiConfig disabledConfig = new HuaweiConfig.Builder()
                .setUsername("u").setPassword("p").setEnabled(Boolean.FALSE).build();
        when(getHuaweiConfigurationService.isDisabled()).thenReturn(true);

        // When
        service.aggregateYearlyProductions(2024);

        // Then
        verifyNoInteractions(getEnergyStationRepository);
        verifyNoInteractions(aggregationRepository);
    }

    @Test
    void testAggregateYearly_whenNoConfig_thenSkip() {

        // Given
        when(getHuaweiConfigurationService.isDisabled()).thenReturn(true);

        // When
        service.aggregateYearlyProductions(2024);

        // Then
        verifyNoInteractions(getEnergyStationRepository);
        verifyNoInteractions(aggregationRepository);
    }
}
