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
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.get.GetDatadisConfigRepository;
import org.lucoenergia.conluz.domain.production.datadis.aggregate.DatadisProductionMonthlyAggregationRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisDisabledException;
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
class DatadisProductionMonthlyAggregationServiceTest {

    @Mock
    private GetSupplyRepository getSupplyRepository;

    @Mock
    private DatadisProductionMonthlyAggregationRepository aggregationRepository;

    @Mock
    private GetDatadisConfigRepository getDatadisConfigRepository;

    @InjectMocks
    private DatadisProductionMonthlyAggregationServiceImpl service;

    private static DatadisConfig config(boolean enabled) {
        return new DatadisConfig.Builder()
                .setUsername("u")
                .setPassword("p")
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .setEnabled(enabled)
                .build();
    }

    @Test
    void testAggregateMonthlyForAllSuppliesSpecificMonth() {

        // Given
        Supply supply1 = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build()).build();
        Supply supply2 = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST002").build()).build();
        when(getSupplyRepository.findAll()).thenReturn(List.of(supply1, supply2));

        // When
        service.aggregateMonthlyProductions(Month.DECEMBER, 2024);

        // Then - 2 supplies × 1 month = 2 calls
        verify(aggregationRepository, times(2))
                .aggregateMonthlyProduction(any(Supply.class), eq(Month.DECEMBER), eq(2024));
    }

    @Test
    void testAggregateMonthlySkipsSuppliesWithoutDistributorCode() {

        // Given
        Supply supplyWithCode = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build()).build();
        Supply supplyWithoutCode = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode(null).build()).build();
        when(getSupplyRepository.findAll()).thenReturn(List.of(supplyWithCode, supplyWithoutCode));

        // When
        service.aggregateMonthlyProductions(Month.JANUARY, 2024);

        // Then - only the supply with a distributor code is processed
        verify(aggregationRepository, times(1))
                .aggregateMonthlyProduction(eq(supplyWithCode), eq(Month.JANUARY), eq(2024));
        verify(aggregationRepository, never())
                .aggregateMonthlyProduction(eq(supplyWithoutCode), any(Month.class), anyInt());
    }

    @Test
    void testAggregateMonthlySkipsSuppliesWithBlankDistributorCode() {

        // Given
        Supply supplyWithBlankCode = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("   ").build()).build();
        when(getSupplyRepository.findAll()).thenReturn(List.of(supplyWithBlankCode));

        // When
        service.aggregateMonthlyProductions(Month.MARCH, 2024);

        // Then
        verify(aggregationRepository, never())
                .aggregateMonthlyProduction(any(Supply.class), any(Month.class), anyInt());
    }

    @Test
    void testAggregateMonthlyHandlesRepositoryException() {

        // Given
        Supply supply = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST123").build()).build();
        when(getSupplyRepository.findAll()).thenReturn(List.of(supply));
        doThrow(new RuntimeException("InfluxDB connection error"))
                .when(aggregationRepository)
                .aggregateMonthlyProduction(any(Supply.class), any(Month.class), anyInt());

        // When - should not throw, just log error
        service.aggregateMonthlyProductions(Month.JUNE, 2024);

        // Then - attempted the call
        verify(aggregationRepository, times(1))
                .aggregateMonthlyProduction(eq(supply), eq(Month.JUNE), eq(2024));
    }

    @Test
    void testAggregateMonthlyForCommunityWholeYearProcessesEveryMonth() {

        // Given
        UUID communityId = UUID.randomUUID();
        Supply supply1 = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build()).build();
        Supply supply2 = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST002").build()).build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supply1, supply2));

        // When
        service.aggregateMonthlyProductions(communityId, 2024);

        // Then - 2 supplies × 12 months
        verify(getSupplyRepository).findAllByCommunityId(communityId);
        verify(getSupplyRepository, never()).findAll();
        for (Month month : Month.values()) {
            verify(aggregationRepository).aggregateMonthlyProduction(eq(supply1), eq(month), eq(2024));
            verify(aggregationRepository).aggregateMonthlyProduction(eq(supply2), eq(month), eq(2024));
        }
        verify(aggregationRepository, times(24))
                .aggregateMonthlyProduction(any(Supply.class), any(Month.class), eq(2024));
    }

    @Test
    void testAggregateMonthlyForCommunityWholeYearSkipsSuppliesWithoutDistributorCode() {

        // Given
        UUID communityId = UUID.randomUUID();
        Supply supplyWithCode = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build()).build();
        Supply supplyWithoutCode = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("   ").build()).build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supplyWithCode, supplyWithoutCode));

        // When
        service.aggregateMonthlyProductions(communityId, 2024);

        // Then - only the supply with a distributor code is processed, across all 12 months
        verify(aggregationRepository, times(12))
                .aggregateMonthlyProduction(eq(supplyWithCode), any(Month.class), eq(2024));
        verify(aggregationRepository, never())
                .aggregateMonthlyProduction(eq(supplyWithoutCode), any(Month.class), anyInt());
    }

    @Test
    void testAggregateMonthlyForCommunitySpecificMonthProcessesItsSupplies() {

        // Given
        UUID communityId = UUID.randomUUID();
        Supply supply1 = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build()).build();
        Supply supply2 = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST002").build()).build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supply1, supply2));

        // When
        service.aggregateMonthlyProductions(communityId, Month.APRIL, 2024);

        // Then - one call per supply for the requested month
        verify(getSupplyRepository).findAllByCommunityId(communityId);
        verify(getSupplyRepository, never()).findAll();
        verify(aggregationRepository, times(1)).aggregateMonthlyProduction(eq(supply1), eq(Month.APRIL), eq(2024));
        verify(aggregationRepository, times(1)).aggregateMonthlyProduction(eq(supply2), eq(Month.APRIL), eq(2024));
    }

    @Test
    void testAggregateMonthlyForCommunitySpecificMonthSkipsSuppliesWithoutDistributorCode() {

        // Given
        UUID communityId = UUID.randomUUID();
        Supply supplyWithCode = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build()).build();
        Supply supplyWithoutCode = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode(null).build()).build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supplyWithCode, supplyWithoutCode));

        // When
        service.aggregateMonthlyProductions(communityId, Month.JANUARY, 2024);

        // Then - only the supply with a distributor code is processed
        verify(aggregationRepository, times(1))
                .aggregateMonthlyProduction(eq(supplyWithCode), eq(Month.JANUARY), eq(2024));
        verify(aggregationRepository, never())
                .aggregateMonthlyProduction(eq(supplyWithoutCode), any(Month.class), anyInt());
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
        service.aggregateMonthlyProductions(community.getId(), supplyCode, Month.MAY, 2024);

        // Then
        verify(aggregationRepository, times(1)).aggregateMonthlyProduction(eq(supply), eq(Month.MAY), eq(2024));
    }

    @Test
    void testAggregateMonthlyForSupplyThrowsWhenSupplyNotFound() {

        // Given
        UUID communityId = UUID.randomUUID();
        SupplyCode supplyCode = SupplyCode.of("ES1234000000000001JN0F");
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.empty());

        // When / Then
        assertThrows(SupplyNotFoundException.class,
                () -> service.aggregateMonthlyProductions(communityId, supplyCode, Month.MAY, 2024));
        verify(aggregationRepository, never())
                .aggregateMonthlyProduction(any(Supply.class), any(Month.class), anyInt());
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
                () -> service.aggregateMonthlyProductions(UUID.randomUUID(), supplyCode, Month.MAY, 2024));
        verify(aggregationRepository, never())
                .aggregateMonthlyProduction(any(Supply.class), any(Month.class), anyInt());
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
                () -> service.aggregateMonthlyProductions(UUID.randomUUID(), supplyCode, Month.MAY, 2024));
        verify(aggregationRepository, never())
                .aggregateMonthlyProduction(any(Supply.class), any(Month.class), anyInt());
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
        service.aggregateMonthlyProductions(community.getId(), supplyCode, Month.MAY, 2024);

        // Then - no aggregation is attempted and no exception is thrown
        verify(aggregationRepository, never())
                .aggregateMonthlyProduction(any(Supply.class), any(Month.class), anyInt());
    }

    // -----------------------------------------------------------------------
    // syncMonthlyProductions: config gating + dispatch (moved out of the controller)
    // -----------------------------------------------------------------------

    @Test
    void testSyncMonthlyThrowsWhenDatadisConfigIsMissing() {

        // Given
        UUID communityId = UUID.randomUUID();
        when(getDatadisConfigRepository.findByCommunityId(communityId)).thenReturn(Optional.empty());

        // When / Then
        assertThrows(DatadisDisabledException.class,
                () -> service.syncMonthlyProductions(communityId, null, null, 2024));
        verifyNoInteractions(getSupplyRepository, aggregationRepository);
    }

    @Test
    void testSyncMonthlyThrowsWhenDatadisConfigIsDisabled() {

        // Given
        UUID communityId = UUID.randomUUID();
        when(getDatadisConfigRepository.findByCommunityId(communityId)).thenReturn(Optional.of(config(false)));

        // When / Then
        assertThrows(DatadisDisabledException.class,
                () -> service.syncMonthlyProductions(communityId, null, null, 2024));
        verifyNoInteractions(getSupplyRepository, aggregationRepository);
    }

    @Test
    void testSyncMonthlyWithNoSupplyNoMonthAggregatesWholeCommunityYear() {

        // Given
        UUID communityId = UUID.randomUUID();
        Supply supply = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build()).build();
        when(getDatadisConfigRepository.findByCommunityId(communityId)).thenReturn(Optional.of(config(true)));
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supply));

        // When
        service.syncMonthlyProductions(communityId, null, null, 2024);

        // Then - community whole-year path: all 12 months, no single-supply lookup
        verify(getSupplyRepository, never()).findByCode(any(SupplyCode.class));
        verify(aggregationRepository, times(12))
                .aggregateMonthlyProduction(eq(supply), any(Month.class), eq(2024));
    }

    @Test
    void testSyncMonthlyWithNoSupplySpecificMonthAggregatesCommunityForThatMonth() {

        // Given
        UUID communityId = UUID.randomUUID();
        Supply supply = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build()).build();
        when(getDatadisConfigRepository.findByCommunityId(communityId)).thenReturn(Optional.of(config(true)));
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supply));

        // When
        service.syncMonthlyProductions(communityId, null, 4, 2024);

        // Then - only the requested month
        verify(aggregationRepository, times(1)).aggregateMonthlyProduction(any(Supply.class), any(Month.class), anyInt());
        verify(aggregationRepository, times(1)).aggregateMonthlyProduction(eq(supply), eq(Month.APRIL), eq(2024));
    }

    @Test
    void testSyncMonthlyWithSupplyNoMonthAggregatesThatSupplyAllMonths() {

        // Given
        Community community = CommunityMother.random().build();
        Supply supply = SupplyMother.random()
                .withCommunity(community)
                .withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build())
                .build();
        when(getDatadisConfigRepository.findByCommunityId(community.getId())).thenReturn(Optional.of(config(true)));
        when(getSupplyRepository.findByCode(SupplyCode.of("CUPS001"))).thenReturn(Optional.of(supply));

        // When
        service.syncMonthlyProductions(community.getId(), "CUPS001", null, 2024);

        // Then - single supply across all 12 months, no community-wide lookup
        verify(getSupplyRepository, never()).findAllByCommunityId(any(UUID.class));
        verify(aggregationRepository, times(12))
                .aggregateMonthlyProduction(eq(supply), any(Month.class), eq(2024));
    }

    @Test
    void testSyncMonthlyWithSupplyAndMonthAggregatesThatSupplyForThatMonth() {

        // Given
        Community community = CommunityMother.random().build();
        Supply supply = SupplyMother.random()
                .withCommunity(community)
                .withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build())
                .build();
        when(getDatadisConfigRepository.findByCommunityId(community.getId())).thenReturn(Optional.of(config(true)));
        when(getSupplyRepository.findByCode(SupplyCode.of("CUPS001"))).thenReturn(Optional.of(supply));

        // When
        service.syncMonthlyProductions(community.getId(), "CUPS001", 6, 2024);

        // Then
        verify(aggregationRepository, times(1)).aggregateMonthlyProduction(any(Supply.class), any(Month.class), anyInt());
        verify(aggregationRepository, times(1)).aggregateMonthlyProduction(eq(supply), eq(Month.JUNE), eq(2024));
    }
}
