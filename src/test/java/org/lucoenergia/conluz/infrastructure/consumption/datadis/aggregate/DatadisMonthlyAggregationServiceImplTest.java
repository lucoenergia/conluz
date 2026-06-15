package org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.distributor.SupplyDistributor;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    void testAggregateMonthlyForAllSuppliesSpecificMonth() {

        // Given
        Supply supply1 = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build()).build();
        Supply supply2 = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST002").build()).build();
        when(getSupplyRepository.findAll()).thenReturn(List.of(supply1, supply2));

        // When
        service.aggregateMonthlyConsumptions(Month.DECEMBER, 2024);

        // Then - 2 supplies × 1 month = 2 calls
        verify(aggregationRepository, times(2))
                .aggregateMonthlyConsumption(any(Supply.class), eq(Month.DECEMBER), eq(2024));
    }

    @Test
    void testAggregateMonthlySkipsSuppliesWithoutDistributorCode() {

        // Given
        Supply supplyWithCode = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build()).build();
        Supply supplyWithoutCode = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode(null).build()).build();
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
        Supply supplyWithBlankCode = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("   ").build()).build();
        when(getSupplyRepository.findAll()).thenReturn(List.of(supplyWithBlankCode));

        // When
        service.aggregateMonthlyConsumptions(Month.MARCH, 2024);

        // Then
        verify(aggregationRepository, never())
                .aggregateMonthlyConsumption(any(Supply.class), any(Month.class), anyInt());
    }

    @Test
    void testAggregateMonthlyHandlesRepositoryException() {

        // Given
        Supply supply = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST123").build()).build();
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
    void testAggregateMonthlyForCommunityWholeYearProcessesEveryMonth() {

        // Given
        UUID communityId = UUID.randomUUID();
        Supply supply1 = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build()).build();
        Supply supply2 = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST002").build()).build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supply1, supply2));

        // When
        service.aggregateMonthlyConsumptions(communityId, 2024);

        // Then - 2 supplies × 12 months
        verify(getSupplyRepository).findAllByCommunityId(communityId);
        verify(getSupplyRepository, never()).findAll();
        for (Month month : Month.values()) {
            verify(aggregationRepository).aggregateMonthlyConsumption(eq(supply1), eq(month), eq(2024));
            verify(aggregationRepository).aggregateMonthlyConsumption(eq(supply2), eq(month), eq(2024));
        }
        verify(aggregationRepository, times(24))
                .aggregateMonthlyConsumption(any(Supply.class), any(Month.class), eq(2024));
    }

    @Test
    void testAggregateMonthlyForCommunityWholeYearSkipsSuppliesWithoutDistributorCode() {

        // Given
        UUID communityId = UUID.randomUUID();
        Supply supplyWithCode = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build()).build();
        Supply supplyWithoutCode = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("   ").build()).build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supplyWithCode, supplyWithoutCode));

        // When
        service.aggregateMonthlyConsumptions(communityId, 2024);

        // Then - only the supply with a distributor code is processed, across all 12 months
        verify(aggregationRepository, times(12))
                .aggregateMonthlyConsumption(eq(supplyWithCode), any(Month.class), eq(2024));
        verify(aggregationRepository, never())
                .aggregateMonthlyConsumption(eq(supplyWithoutCode), any(Month.class), anyInt());
    }

    @Test
    void testAggregateMonthlyForCommunitySpecificMonthProcessesItsSupplies() {

        // Given
        UUID communityId = UUID.randomUUID();
        Supply supply1 = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build()).build();
        Supply supply2 = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST002").build()).build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supply1, supply2));

        // When
        service.aggregateMonthlyConsumptions(communityId, Month.APRIL, 2024);

        // Then - one call per supply for the requested month
        verify(getSupplyRepository).findAllByCommunityId(communityId);
        verify(getSupplyRepository, never()).findAll();
        verify(aggregationRepository, times(1)).aggregateMonthlyConsumption(eq(supply1), eq(Month.APRIL), eq(2024));
        verify(aggregationRepository, times(1)).aggregateMonthlyConsumption(eq(supply2), eq(Month.APRIL), eq(2024));
    }

    @Test
    void testAggregateMonthlyForCommunitySpecificMonthSkipsSuppliesWithoutDistributorCode() {

        // Given
        UUID communityId = UUID.randomUUID();
        Supply supplyWithCode = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build()).build();
        Supply supplyWithoutCode = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode(null).build()).build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supplyWithCode, supplyWithoutCode));

        // When
        service.aggregateMonthlyConsumptions(communityId, Month.JANUARY, 2024);

        // Then - only the supply with a distributor code is processed
        verify(aggregationRepository, times(1))
                .aggregateMonthlyConsumption(eq(supplyWithCode), eq(Month.JANUARY), eq(2024));
        verify(aggregationRepository, never())
                .aggregateMonthlyConsumption(eq(supplyWithoutCode), any(Month.class), anyInt());
    }

    @Test
    void testAggregateMonthlyForSupplyInCommunity() {

        // Given
        Community community = CommunityMother.random().build();
        SupplyCode supplyCode = SupplyCode.of("ES1234000000000001JN0F");
        Supply supply = SupplyMother.random()
                .withCommunity(community)
                .withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build())
                .build();
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.of(supply));

        // When
        service.aggregateMonthlyConsumptions(community.getId(), supplyCode, Month.MAY, 2024);

        // Then
        verify(aggregationRepository, times(1)).aggregateMonthlyConsumption(eq(supply), eq(Month.MAY), eq(2024));
    }

    @Test
    void testAggregateMonthlyForSupplyThrowsWhenSupplyNotFound() {

        // Given
        UUID communityId = UUID.randomUUID();
        SupplyCode supplyCode = SupplyCode.of("ES1234000000000001JN0F");
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.empty());

        // When / Then
        assertThrows(SupplyNotFoundException.class,
                () -> service.aggregateMonthlyConsumptions(communityId, supplyCode, Month.MAY, 2024));
        verify(aggregationRepository, never())
                .aggregateMonthlyConsumption(any(Supply.class), any(Month.class), anyInt());
    }

    @Test
    void testAggregateMonthlyForSupplyThrowsWhenSupplyBelongsToAnotherCommunity() {

        // Given
        Community community = CommunityMother.random().build();
        SupplyCode supplyCode = SupplyCode.of("ES1234000000000001JN0F");
        Supply supply = SupplyMother.random()
                .withCommunity(community)
                .withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build())
                .build();
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.of(supply));

        // When / Then - the requested community differs from the supply's community
        assertThrows(SupplyNotFoundException.class,
                () -> service.aggregateMonthlyConsumptions(UUID.randomUUID(), supplyCode, Month.MAY, 2024));
        verify(aggregationRepository, never())
                .aggregateMonthlyConsumption(any(Supply.class), any(Month.class), anyInt());
    }

    @Test
    void testAggregateMonthlyForSupplyThrowsWhenSupplyHasNoCommunity() {

        // Given
        SupplyCode supplyCode = SupplyCode.of("ES1234000000000001JN0F");
        Supply supply = SupplyMother.random()
                .withCommunity(null)
                .withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build())
                .build();
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.of(supply));

        // When / Then
        assertThrows(SupplyNotFoundException.class,
                () -> service.aggregateMonthlyConsumptions(UUID.randomUUID(), supplyCode, Month.MAY, 2024));
        verify(aggregationRepository, never())
                .aggregateMonthlyConsumption(any(Supply.class), any(Month.class), anyInt());
    }

    @Test
    void testAggregateMonthlyForSupplyWithoutDistributorCodeDoesNothing() {

        // Given
        Community community = CommunityMother.random().build();
        SupplyCode supplyCode = SupplyCode.of("ES1234000000000001JN0F");
        Supply supply = SupplyMother.random()
                .withCommunity(community)
                .withDistributor(new SupplyDistributor.Builder().withCode(null).build())
                .build();
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.of(supply));

        // When - the supply belongs to the community but has no distributor code
        service.aggregateMonthlyConsumptions(community.getId(), supplyCode, Month.MAY, 2024);

        // Then - no aggregation is attempted and no exception is thrown
        verify(aggregationRepository, never())
                .aggregateMonthlyConsumption(any(Supply.class), any(Month.class), anyInt());
    }
}
