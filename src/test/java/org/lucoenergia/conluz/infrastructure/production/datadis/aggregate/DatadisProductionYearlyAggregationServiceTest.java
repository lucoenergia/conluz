package org.lucoenergia.conluz.infrastructure.production.datadis.aggregate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.distributor.SupplyDistributor;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.production.datadis.aggregate.DatadisProductionYearlyAggregationRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
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
class DatadisProductionYearlyAggregationServiceTest {

    @Mock
    private GetSupplyRepository getSupplyRepository;

    @Mock
    private DatadisProductionYearlyAggregationRepository aggregationRepository;

    @InjectMocks
    private DatadisProductionYearlyAggregationServiceImpl service;

    @Test
    void testAggregateYearlyForAllSupplies() {

        // Given
        Supply supply1 = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build()).build();
        Supply supply2 = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST002").build()).build();
        when(getSupplyRepository.findAll()).thenReturn(List.of(supply1, supply2));

        // When
        service.aggregateYearlyProductions(2024);

        // Then - one call per supply
        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(supply1), eq(2024));
        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(supply2), eq(2024));
    }

    @Test
    void testAggregateYearlySkipsSuppliesWithoutDistributorCode() {

        // Given
        Supply supplyWithCode = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build()).build();
        Supply supplyWithoutCode = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode(null).build()).build();
        when(getSupplyRepository.findAll()).thenReturn(List.of(supplyWithCode, supplyWithoutCode));

        // When
        service.aggregateYearlyProductions(2024);

        // Then - only supply with distributor code is processed
        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(supplyWithCode), eq(2024));
        verify(aggregationRepository, never()).aggregateYearlyProduction(eq(supplyWithoutCode), anyInt());
    }

    @Test
    void testAggregateYearlySkipsSuppliesWithBlankDistributorCode() {

        // Given
        Supply supplyWithBlankCode = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("   ").build()).build();
        when(getSupplyRepository.findAll()).thenReturn(List.of(supplyWithBlankCode));

        // When
        service.aggregateYearlyProductions(2024);

        // Then
        verify(aggregationRepository, never()).aggregateYearlyProduction(any(Supply.class), anyInt());
    }

    @Test
    void testAggregateYearlyHandlesRepositoryException() {

        // Given
        Supply supply = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST123").build()).build();
        when(getSupplyRepository.findAll()).thenReturn(List.of(supply));
        doThrow(new RuntimeException("InfluxDB connection error"))
                .when(aggregationRepository)
                .aggregateYearlyProduction(any(Supply.class), anyInt());

        // When - should not throw, just log error
        service.aggregateYearlyProductions(2024);

        // Then - attempted the call
        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(supply), eq(2024));
    }

    @Test
    void testAggregateYearlyWithEmptySupplyList() {

        // Given
        when(getSupplyRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        service.aggregateYearlyProductions(2024);

        // Then
        verify(aggregationRepository, never()).aggregateYearlyProduction(any(Supply.class), anyInt());
    }

    @Test
    void testAggregateYearlyForCommunityProcessesItsSupplies() {

        // Given
        UUID communityId = UUID.randomUUID();
        Supply supply1 = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build()).build();
        Supply supply2 = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST002").build()).build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supply1, supply2));

        // When
        service.aggregateYearlyProductions(communityId, 2024);

        // Then - one call per supply, using the community-scoped lookup
        verify(getSupplyRepository).findAllByCommunityId(communityId);
        verify(getSupplyRepository, never()).findAll();
        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(supply1), eq(2024));
        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(supply2), eq(2024));
    }

    @Test
    void testAggregateYearlyForCommunitySkipsSuppliesWithoutDistributorCode() {

        // Given
        UUID communityId = UUID.randomUUID();
        Supply supplyWithCode = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build()).build();
        Supply supplyWithoutCode = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode(null).build()).build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supplyWithCode, supplyWithoutCode));

        // When
        service.aggregateYearlyProductions(communityId, 2024);

        // Then - only the supply with a distributor code is processed
        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(supplyWithCode), eq(2024));
        verify(aggregationRepository, never()).aggregateYearlyProduction(eq(supplyWithoutCode), anyInt());
    }

    @Test
    void testAggregateYearlyForCommunityHandlesRepositoryException() {

        // Given
        UUID communityId = UUID.randomUUID();
        Supply supply = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST123").build()).build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supply));
        doThrow(new RuntimeException("InfluxDB connection error"))
                .when(aggregationRepository)
                .aggregateYearlyProduction(any(Supply.class), anyInt());

        // When - should not throw, just log the error
        service.aggregateYearlyProductions(communityId, 2024);

        // Then - attempted the call
        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(supply), eq(2024));
    }

    @Test
    void testAggregateYearlyForSupplyInCommunity() {

        // Given
        Community community = CommunityMother.random().build();
        SupplyCode supplyCode = SupplyCode.of("ES1234000000000001JN0F");
        Supply supply = SupplyMother.random()
                .withCommunity(community)
                .withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build())
                .build();
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.of(supply));

        // When
        service.aggregateYearlyProductions(community.getId(), supplyCode, 2024);

        // Then
        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(supply), eq(2024));
    }

    @Test
    void testAggregateYearlyForSupplyThrowsWhenSupplyNotFound() {

        // Given
        UUID communityId = UUID.randomUUID();
        SupplyCode supplyCode = SupplyCode.of("ES1234000000000001JN0F");
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.empty());

        // When / Then
        assertThrows(SupplyNotFoundException.class,
                () -> service.aggregateYearlyProductions(communityId, supplyCode, 2024));
        verify(aggregationRepository, never()).aggregateYearlyProduction(any(Supply.class), anyInt());
    }

    @Test
    void testAggregateYearlyForSupplyThrowsWhenSupplyBelongsToAnotherCommunity() {

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
                () -> service.aggregateYearlyProductions(UUID.randomUUID(), supplyCode, 2024));
        verify(aggregationRepository, never()).aggregateYearlyProduction(any(Supply.class), anyInt());
    }

    @Test
    void testAggregateYearlyForSupplyThrowsWhenSupplyHasNoCommunity() {

        // Given
        SupplyCode supplyCode = SupplyCode.of("ES1234000000000001JN0F");
        Supply supply = SupplyMother.random()
                .withCommunity(null)
                .withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build())
                .build();
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.of(supply));

        // When / Then
        assertThrows(SupplyNotFoundException.class,
                () -> service.aggregateYearlyProductions(UUID.randomUUID(), supplyCode, 2024));
        verify(aggregationRepository, never()).aggregateYearlyProduction(any(Supply.class), anyInt());
    }

    @Test
    void testAggregateYearlyForSupplyWithoutDistributorCodeDoesNothing() {

        // Given
        Community community = CommunityMother.random().build();
        SupplyCode supplyCode = SupplyCode.of("ES1234000000000001JN0F");
        Supply supply = SupplyMother.random()
                .withCommunity(community)
                .withDistributor(new SupplyDistributor.Builder().withCode(null).build())
                .build();
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.of(supply));

        // When - the supply belongs to the community but has no distributor code
        service.aggregateYearlyProductions(community.getId(), supplyCode, 2024);

        // Then - no aggregation is attempted and no exception is thrown
        verify(aggregationRepository, never()).aggregateYearlyProduction(any(Supply.class), anyInt());
    }
}
