package org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
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
class DatadisMonthlyAggregationServiceImplTest {

    @Mock
    private GetSupplyRepository getSupplyRepository;

    @Mock
    private DatadisMonthlyAggregationRepository aggregationRepository;

    @InjectMocks
    private DatadisMonthlyAggregationServiceImpl service;

    @Test
    void testAggregateMonthlyForAllSuppliesAllMonths() {

        // Given
        Supply supply1 = SupplyMother.random().withDistributorCode("DIST001").build();
        Supply supply2 = SupplyMother.random().withDistributorCode("DIST002").build();
        when(getSupplyRepository.findAll()).thenReturn(List.of(supply1, supply2));

        // When
        service.aggregateMonthlyConsumptions(2024);

        // Then - 2 supplies × 12 months = 24 calls
        verify(aggregationRepository, times(24))
                .aggregateMonthlyConsumption(any(Supply.class), any(Month.class), eq(2024));
    }

    @Test
    void testAggregateMonthlyForAllSuppliesSpecificMonth() {

        // Given
        Supply supply1 = SupplyMother.random().withDistributorCode("DIST001").build();
        Supply supply2 = SupplyMother.random().withDistributorCode("DIST002").build();
        when(getSupplyRepository.findAll()).thenReturn(List.of(supply1, supply2));

        // When
        service.aggregateMonthlyConsumptions(Month.DECEMBER, 2024);

        // Then - 2 supplies × 1 month = 2 calls
        verify(aggregationRepository, times(2))
                .aggregateMonthlyConsumption(any(Supply.class), eq(Month.DECEMBER), eq(2024));
    }

    @Test
    void testAggregateMonthlyForSpecificSupplyAndMonth() {

        // Given
        Supply supply = SupplyMother.random().withDistributorCode("DIST123").build();
        SupplyCode supplyCode = SupplyCode.of(supply.getCode());
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.of(supply));

        // When
        service.aggregateMonthlyConsumptions(supplyCode, Month.JUNE, 2024);

        // Then
        verify(aggregationRepository, times(1))
                .aggregateMonthlyConsumption(eq(supply), eq(Month.JUNE), eq(2024));
    }

    @Test
    void testAggregateMonthlySkipsSuppliesWithoutDistributorCode() {

        // Given
        Supply supplyWithCode = SupplyMother.random().withDistributorCode("DIST001").build();
        Supply supplyWithoutCode = SupplyMother.random().withDistributorCode(null).build();
        when(getSupplyRepository.findAll()).thenReturn(List.of(supplyWithCode, supplyWithoutCode));

        // When
        service.aggregateMonthlyConsumptions(Month.JANUARY, 2024);

        // Then - only the supply with a distributor code is processed
        verify(aggregationRepository, times(1))
                .aggregateMonthlyConsumption(eq(supplyWithCode), eq(Month.JANUARY), eq(2024));
        verify(aggregationRepository, never())
                .aggregateMonthlyConsumption(eq(supplyWithoutCode), any(Month.class), anyInt());
    }

    @Test
    void testAggregateMonthlySkipsSuppliesWithBlankDistributorCode() {

        // Given
        Supply supplyWithBlankCode = SupplyMother.random().withDistributorCode("   ").build();
        when(getSupplyRepository.findAll()).thenReturn(List.of(supplyWithBlankCode));

        // When
        service.aggregateMonthlyConsumptions(Month.MARCH, 2024);

        // Then
        verify(aggregationRepository, never())
                .aggregateMonthlyConsumption(any(Supply.class), any(Month.class), anyInt());
    }

    @Test
    void testAggregateMonthlyForSpecificSupplyWithoutDistributorCodeDoesNothing() {

        // Given
        Supply supply = SupplyMother.random().withDistributorCode(null).build();
        SupplyCode supplyCode = SupplyCode.of(supply.getCode());
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.of(supply));

        // When
        service.aggregateMonthlyConsumptions(supplyCode, Month.JUNE, 2024);

        // Then - skipped because no distributor code
        verify(aggregationRepository, never())
                .aggregateMonthlyConsumption(any(Supply.class), any(Month.class), anyInt());
    }

    @Test
    void testAggregateMonthlyWithSupplyNotFound() {

        // Given
        SupplyCode unknownCode = SupplyCode.of("UNKNOWN_CUPS");
        when(getSupplyRepository.findByCode(unknownCode)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(SupplyNotFoundException.class, () ->
                service.aggregateMonthlyConsumptions(unknownCode, Month.JUNE, 2024));

        verify(aggregationRepository, never())
                .aggregateMonthlyConsumption(any(Supply.class), any(Month.class), anyInt());
    }

    @Test
    void testAggregateMonthlyHandlesRepositoryException() {

        // Given
        Supply supply = SupplyMother.random().withDistributorCode("DIST123").build();
        when(getSupplyRepository.findAll()).thenReturn(List.of(supply));
        doThrow(new RuntimeException("InfluxDB connection error"))
                .when(aggregationRepository)
                .aggregateMonthlyConsumption(any(Supply.class), any(Month.class), anyInt());

        // When - should not throw, just log error
        service.aggregateMonthlyConsumptions(Month.JUNE, 2024);

        // Then - attempted the call
        verify(aggregationRepository, times(1))
                .aggregateMonthlyConsumption(eq(supply), eq(Month.JUNE), eq(2024));
    }

    @Test
    void testAggregateMonthlyWithEmptySupplyList() {

        // Given
        when(getSupplyRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        service.aggregateMonthlyConsumptions(2024);

        // Then
        verify(aggregationRepository, never())
                .aggregateMonthlyConsumption(any(Supply.class), any(Month.class), anyInt());
    }
}
