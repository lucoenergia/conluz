package org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisYearlyAggregationRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatadisYearlyAggregationServiceImplTest {

    @Mock
    private GetSupplyRepository getSupplyRepository;

    @Mock
    private DatadisYearlyAggregationRepository aggregationRepository;

    @InjectMocks
    private DatadisYearlyAggregationServiceImpl service;

    @Test
    void testAggregateYearlyForAllSupplies() {

        // Given
        Supply supply1 = SupplyMother.random().withDistributorCode("DIST001").build();
        Supply supply2 = SupplyMother.random().withDistributorCode("DIST002").build();
        when(getSupplyRepository.findAll()).thenReturn(List.of(supply1, supply2));

        // When
        service.aggregateYearlyConsumptions(2024);

        // Then - one call per supply
        verify(aggregationRepository, times(1)).aggregateYearlyConsumption(eq(supply1), eq(2024));
        verify(aggregationRepository, times(1)).aggregateYearlyConsumption(eq(supply2), eq(2024));
    }

    @Test
    void testAggregateYearlyForSpecificSupply() {

        // Given
        Supply supply = SupplyMother.random().withDistributorCode("DIST123").build();
        SupplyCode supplyCode = SupplyCode.of(supply.getCode());
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.of(supply));

        // When
        service.aggregateYearlyConsumptions(supplyCode, 2024);

        // Then
        verify(aggregationRepository, times(1)).aggregateYearlyConsumption(eq(supply), eq(2024));
    }

    @Test
    void testAggregateYearlySkipsSuppliesWithoutDistributorCode() {

        // Given
        Supply supplyWithCode = SupplyMother.random().withDistributorCode("DIST001").build();
        Supply supplyWithoutCode = SupplyMother.random().withDistributorCode(null).build();
        when(getSupplyRepository.findAll()).thenReturn(List.of(supplyWithCode, supplyWithoutCode));

        // When
        service.aggregateYearlyConsumptions(2024);

        // Then - only supply with distributor code is processed
        verify(aggregationRepository, times(1)).aggregateYearlyConsumption(eq(supplyWithCode), eq(2024));
        verify(aggregationRepository, never()).aggregateYearlyConsumption(eq(supplyWithoutCode), anyInt());
    }

    @Test
    void testAggregateYearlySkipsSuppliesWithBlankDistributorCode() {

        // Given
        Supply supplyWithBlankCode = SupplyMother.random().withDistributorCode("   ").build();
        when(getSupplyRepository.findAll()).thenReturn(List.of(supplyWithBlankCode));

        // When
        service.aggregateYearlyConsumptions(2024);

        // Then
        verify(aggregationRepository, never()).aggregateYearlyConsumption(any(Supply.class), anyInt());
    }

    @Test
    void testAggregateYearlyForSpecificSupplyWithoutDistributorCodeDoesNothing() {

        // Given
        Supply supply = SupplyMother.random().withDistributorCode(null).build();
        SupplyCode supplyCode = SupplyCode.of(supply.getCode());
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.of(supply));

        // When
        service.aggregateYearlyConsumptions(supplyCode, 2024);

        // Then - skipped because no distributor code
        verify(aggregationRepository, never()).aggregateYearlyConsumption(any(Supply.class), anyInt());
    }

    @Test
    void testAggregateYearlyWithSupplyNotFound() {

        // Given
        SupplyCode unknownCode = SupplyCode.of("UNKNOWN_CUPS");
        when(getSupplyRepository.findByCode(unknownCode)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(SupplyNotFoundException.class, () ->
                service.aggregateYearlyConsumptions(unknownCode, 2024));

        verify(aggregationRepository, never()).aggregateYearlyConsumption(any(Supply.class), anyInt());
    }

    @Test
    void testAggregateYearlyHandlesRepositoryException() {

        // Given
        Supply supply = SupplyMother.random().withDistributorCode("DIST123").build();
        when(getSupplyRepository.findAll()).thenReturn(List.of(supply));
        doThrow(new RuntimeException("InfluxDB connection error"))
                .when(aggregationRepository)
                .aggregateYearlyConsumption(any(Supply.class), anyInt());

        // When - should not throw, just log error
        service.aggregateYearlyConsumptions(2024);

        // Then - attempted the call
        verify(aggregationRepository, times(1)).aggregateYearlyConsumption(eq(supply), eq(2024));
    }

    @Test
    void testAggregateYearlyWithEmptySupplyList() {

        // Given
        when(getSupplyRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        service.aggregateYearlyConsumptions(2024);

        // Then
        verify(aggregationRepository, never()).aggregateYearlyConsumption(any(Supply.class), anyInt());
    }
}
