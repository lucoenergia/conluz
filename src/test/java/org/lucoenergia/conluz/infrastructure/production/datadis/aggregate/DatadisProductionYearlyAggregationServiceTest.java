package org.lucoenergia.conluz.infrastructure.production.datadis.aggregate;

import org.junit.jupiter.api.DisplayName;
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
import org.lucoenergia.conluz.domain.production.datadis.aggregate.DatadisProductionYearlyAggregationRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisDisabledException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatadisProductionYearlyAggregationServiceTest {

    @Mock
    private GetSupplyRepository getSupplyRepository;

    @Mock
    private DatadisProductionYearlyAggregationRepository aggregationRepository;

    @Mock
    private GetDatadisConfigRepository getDatadisConfigRepository;

    @Mock
    private GetPlantRepository getPlantRepository;

    @InjectMocks
    private DatadisProductionYearlyAggregationServiceImpl service;

    private static DatadisConfig config(boolean enabled) {
        return new DatadisConfig.Builder()
                .setUsername("u")
                .setPassword("p")
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .setEnabled(enabled)
                .build();
    }

    /** Links the given supplies, and only them, to plants of the community. */
    private void givenPlantSupplies(UUID communityId, Supply... supplies) {
        Set<String> codes = Stream.of(supplies).map(Supply::getCode).collect(Collectors.toSet());
        when(getPlantRepository.findSupplyCodesByCommunity(communityId)).thenReturn(codes);
    }

    @Test
    void testAggregateYearlyForCommunityProcessesItsSupplies() {

        // Given
        UUID communityId = UUID.randomUUID();
        Supply supply1 = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build()).build();
        Supply supply2 = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST002").build()).build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supply1, supply2));

        givenPlantSupplies(communityId, supply1, supply2);
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

        givenPlantSupplies(communityId, supplyWithCode, supplyWithoutCode);
        // When
        service.aggregateYearlyProductions(communityId, 2024);

        // Then - only the supply with a distributor code is processed
        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(supplyWithCode), eq(2024));
        verify(aggregationRepository, never()).aggregateYearlyProduction(eq(supplyWithoutCode), anyInt());
    }

    @Test
    void testAggregateYearlyForCommunitySkipsSuppliesWithBlankDistributorCode() {

        // Given
        UUID communityId = UUID.randomUUID();
        Supply supplyWithBlankCode = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("   ").build()).build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supplyWithBlankCode));

        givenPlantSupplies(communityId, supplyWithBlankCode);
        // When
        service.aggregateYearlyProductions(communityId, 2024);

        // Then
        verify(aggregationRepository, never()).aggregateYearlyProduction(any(Supply.class), anyInt());
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

        givenPlantSupplies(communityId, supply);
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

        givenPlantSupplies(community.getId(), supply);
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

        givenPlantSupplies(community.getId(), supply);
        // When - the supply belongs to the community but has no distributor code
        service.aggregateYearlyProductions(community.getId(), supplyCode, 2024);

        // Then - no aggregation is attempted and no exception is thrown
        verify(aggregationRepository, never()).aggregateYearlyProduction(any(Supply.class), anyInt());
    }

    // -----------------------------------------------------------------------
    // syncYearlyProductions: config gating + dispatch (moved out of the controller)
    // -----------------------------------------------------------------------

    @Test
    void testSyncYearlyThrowsWhenDatadisConfigIsMissing() {

        // Given
        UUID communityId = UUID.randomUUID();
        when(getDatadisConfigRepository.findByCommunityId(communityId)).thenReturn(Optional.empty());

        // When / Then
        assertThrows(DatadisDisabledException.class,
                () -> service.syncYearlyProductions(communityId, null, 2024));
        verifyNoInteractions(getSupplyRepository, aggregationRepository);
    }

    @Test
    void testSyncYearlyThrowsWhenDatadisConfigIsDisabled() {

        // Given
        UUID communityId = UUID.randomUUID();
        when(getDatadisConfigRepository.findByCommunityId(communityId)).thenReturn(Optional.of(config(false)));

        // When / Then
        assertThrows(DatadisDisabledException.class,
                () -> service.syncYearlyProductions(communityId, null, 2024));
        verifyNoInteractions(getSupplyRepository, aggregationRepository);
    }

    @Test
    void testSyncYearlyWithNoSupplyAggregatesWholeCommunity() {

        // Given
        UUID communityId = UUID.randomUUID();
        Supply supply = SupplyMother.random().withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build()).build();
        when(getDatadisConfigRepository.findByCommunityId(communityId)).thenReturn(Optional.of(config(true)));
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supply));

        givenPlantSupplies(communityId, supply);
        // When
        service.syncYearlyProductions(communityId, null, 2024);

        // Then - community path, no single-supply lookup
        verify(getSupplyRepository, never()).findByCode(any(SupplyCode.class));
        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(supply), eq(2024));
    }

    @Test
    void testSyncYearlyWithSupplyAggregatesThatSupply() {

        // Given
        Community community = CommunityMother.random().build();
        Supply supply = SupplyMother.random()
                .withCommunity(community)
                .withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build())
                .build();
        when(getDatadisConfigRepository.findByCommunityId(community.getId())).thenReturn(Optional.of(config(true)));
        when(getSupplyRepository.findByCode(SupplyCode.of("CUPS001"))).thenReturn(Optional.of(supply));

        givenPlantSupplies(community.getId(), supply);
        // When
        service.syncYearlyProductions(community.getId(), "CUPS001", 2024);

        // Then - single supply path, no community-wide lookup
        verify(getSupplyRepository, never()).findAllByCommunityId(any(UUID.class));
        verify(aggregationRepository, times(1)).aggregateYearlyProduction(eq(supply), eq(2024));
    }

    // -----------------------------------------------------------------------
    // DCA-004: only the community's supplies linked to a plant are aggregated
    // -----------------------------------------------------------------------

    private static Supply supplyWithDistributorCode(String distributorCode) {
        return SupplyMother.random()
                .withDistributor(new SupplyDistributor.Builder().withCode(distributorCode).build())
                .build();
    }

    @Test
    @DisplayName("DCA-004 a community run aggregates only the community's plant supplies, and still skips one without distributor code")
    void communityRunAggregatesOnlyPlantSupplies() {

        // Given
        UUID communityId = UUID.randomUUID();
        Supply plantA = supplyWithDistributorCode("DIST001");
        Supply plantB = supplyWithDistributorCode("DIST002");
        Supply plantWithoutDistributorCode = supplyWithDistributorCode(null);
        Supply nonPlantA = supplyWithDistributorCode("DIST003");
        Supply nonPlantB = supplyWithDistributorCode("DIST004");
        when(getSupplyRepository.findAllByCommunityId(communityId))
                .thenReturn(List.of(nonPlantA, plantA, plantWithoutDistributorCode, nonPlantB, plantB));
        givenPlantSupplies(communityId, plantA, plantB, plantWithoutDistributorCode);

        // When
        service.aggregateYearlyProductions(communityId, 2024);

        // Then
        verify(aggregationRepository).aggregateYearlyProduction(plantA, 2024);
        verify(aggregationRepository).aggregateYearlyProduction(plantB, 2024);
        verify(aggregationRepository, times(2)).aggregateYearlyProduction(any(Supply.class), anyInt());
    }

    @Test
    @DisplayName("DCA-004 a community without plant supplies is not aggregated at all")
    void communityWithoutPlantSuppliesIsNotAggregated() {

        // Given
        UUID communityId = UUID.randomUUID();
        givenPlantSupplies(communityId);

        // When
        service.aggregateYearlyProductions(communityId, 2024);

        // Then - neither the supplies are read nor the time series queried
        verify(getSupplyRepository, never()).findAllByCommunityId(any(UUID.class));
        verifyNoInteractions(aggregationRepository);
    }

    @Test
    @DisplayName("DCA-004 a supply of the community that is not linked to a plant is not found")
    void supplyNotLinkedToAPlantIsNotFound() {

        // Given
        Community community = CommunityMother.random().build();
        Supply nonPlant = SupplyMother.random()
                .withCommunity(community)
                .withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build())
                .build();
        Supply plantA = supplyWithDistributorCode("DIST002");
        Supply plantB = supplyWithDistributorCode("DIST003");
        SupplyCode supplyCode = SupplyCode.of(nonPlant.getCode());
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.of(nonPlant));
        givenPlantSupplies(community.getId(), plantA, plantB);

        // When / Then
        assertThrows(SupplyNotFoundException.class,
                () -> service.aggregateYearlyProductions(community.getId(), supplyCode, 2024));
        verifyNoInteractions(aggregationRepository);
    }

    @Test
    @DisplayName("DCA-004 a supply of the community linked to a plant is aggregated")
    void supplyLinkedToAPlantIsAggregated() {

        // Given
        Community community = CommunityMother.random().build();
        Supply plant = SupplyMother.random()
                .withCommunity(community)
                .withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build())
                .build();
        Supply otherPlant = supplyWithDistributorCode("DIST002");
        SupplyCode supplyCode = SupplyCode.of(plant.getCode());
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.of(plant));
        givenPlantSupplies(community.getId(), otherPlant, plant);

        // When
        service.aggregateYearlyProductions(community.getId(), supplyCode, 2024);

        // Then
        verify(aggregationRepository).aggregateYearlyProduction(plant, 2024);
        verify(aggregationRepository, times(1)).aggregateYearlyProduction(any(Supply.class), anyInt());
    }
}
