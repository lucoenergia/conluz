package org.lucoenergia.conluz.infrastructure.consumption.datadis.sync;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.persist.PersistDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.sync.DatadisConsumptionSyncService;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.admin.supply.DatadisSupplyConfigurationException;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.Month;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DatadisConsumptionSyncServiceImpl}. The service iterates over the supplies
 * in scope and delegates retrieval/persistence to the Datadis repositories. Authorization is
 * enforced at the controller layer, so the service performs orchestration only.
 */
class DatadisConsumptionSyncServiceTest {

    private final GetDatadisConsumptionRepository getDatadisConsumptionRepository =
            Mockito.mock(GetDatadisConsumptionRepository.class);
    private final GetSupplyRepository getSupplyRepository = Mockito.mock(GetSupplyRepository.class);
    private final PersistDatadisConsumptionRepository persistDatadisConsumptionRepository =
            Mockito.mock(PersistDatadisConsumptionRepository.class);

    private final DatadisConsumptionSyncService service = new DatadisConsumptionSyncServiceImpl(
            getDatadisConsumptionRepository, getSupplyRepository, persistDatadisConsumptionRepository);

    // ---------------------------------------------------------------------
    // synchronizeConsumptions(communityId, startDate, endDate)
    // ---------------------------------------------------------------------

    @Test
    void synchronizeConsumptionsByCommunity_retrievesAndPersistsConsumptionsForEachSupply() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);

        Supply supplyOne = SupplyMother.random().build();
        Supply supplyTwo = SupplyMother.random().build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supplyOne, supplyTwo));

        List<DatadisConsumption> consumptions = List.of(Mockito.mock(DatadisConsumption.class));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt()))
                .thenReturn(consumptions);

        service.synchronizeConsumptions(communityId, date, date);

        verify(getDatadisConsumptionRepository).getHourlyConsumptionsByMonth(supplyOne, Month.JANUARY, 2024);
        verify(getDatadisConsumptionRepository).getHourlyConsumptionsByMonth(supplyTwo, Month.JANUARY, 2024);
        verify(persistDatadisConsumptionRepository, times(2)).persistHourlyConsumptions(consumptions);
    }

    @Test
    void synchronizeConsumptionsByCommunity_iteratesOverEachMonthInTheRange() {
        UUID communityId = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2024, 1, 10);
        LocalDate endDate = LocalDate.of(2024, 3, 10);

        Supply supply = SupplyMother.random().build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supply));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt()))
                .thenReturn(Collections.emptyList());

        service.synchronizeConsumptions(communityId, startDate, endDate);

        verify(getDatadisConsumptionRepository).getHourlyConsumptionsByMonth(supply, Month.JANUARY, 2024);
        verify(getDatadisConsumptionRepository).getHourlyConsumptionsByMonth(supply, Month.FEBRUARY, 2024);
        verify(getDatadisConsumptionRepository).getHourlyConsumptionsByMonth(supply, Month.MARCH, 2024);
    }

    @Test
    void synchronizeConsumptionsByCommunity_doesNotPersistWhenConsumptionsAreEmpty() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);

        Supply supply = SupplyMother.random().build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supply));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt()))
                .thenReturn(Collections.emptyList());

        service.synchronizeConsumptions(communityId, date, date);

        verify(persistDatadisConsumptionRepository, never()).persistHourlyConsumptions(any());
    }

    @Test
    void synchronizeConsumptionsByCommunity_skipsSuppliesWithoutDistributorCode() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);

        Supply supplyWithoutDistributor = SupplyMother.random().withDistributor(null).build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supplyWithoutDistributor));

        service.synchronizeConsumptions(communityId, date, date);

        verifyNoInteractions(getDatadisConsumptionRepository);
        verifyNoInteractions(persistDatadisConsumptionRepository);
    }

    @Test
    void synchronizeConsumptionsByCommunity_doesNothingWhenCommunityHasNoSupplies() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);

        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(Collections.emptyList());

        service.synchronizeConsumptions(communityId, date, date);

        verifyNoInteractions(getDatadisConsumptionRepository);
        verifyNoInteractions(persistDatadisConsumptionRepository);
    }

    @Test
    void synchronizeConsumptionsByCommunity_continuesWhenRetrievalFailsForAMonth() {
        UUID communityId = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2024, 1, 10);
        LocalDate endDate = LocalDate.of(2024, 2, 10);

        Supply supply = SupplyMother.random().build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supply));

        List<DatadisConsumption> consumptions = List.of(Mockito.mock(DatadisConsumption.class));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(supply, Month.JANUARY, 2024))
                .thenThrow(new DatadisSupplyConfigurationException("boom"));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(supply, Month.FEBRUARY, 2024))
                .thenReturn(consumptions);

        service.synchronizeConsumptions(communityId, startDate, endDate);

        // The failure for January must not stop February from being processed and persisted.
        verify(persistDatadisConsumptionRepository, times(1)).persistHourlyConsumptions(consumptions);
    }

    // ---------------------------------------------------------------------
    // synchronizeConsumptions(communityId, startDate, endDate, supplyCode)
    // ---------------------------------------------------------------------

    @Test
    void synchronizeConsumptionsBySupplyCode_processesSupplyWhenItBelongsToTheCommunity() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);
        SupplyCode supplyCode = SupplyCode.of("ES001");

        Community community = CommunityMother.random().withId(communityId).build();
        Supply supply = SupplyMother.random().withCommunity(community).build();
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.of(supply));

        List<DatadisConsumption> consumptions = List.of(Mockito.mock(DatadisConsumption.class));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(supply), any(Month.class), anyInt()))
                .thenReturn(consumptions);

        service.synchronizeConsumptions(communityId, date, date, supplyCode);

        verify(getDatadisConsumptionRepository).getHourlyConsumptionsByMonth(supply, Month.JANUARY, 2024);
        verify(persistDatadisConsumptionRepository).persistHourlyConsumptions(consumptions);
    }

    @Test
    void synchronizeConsumptionsBySupplyCode_throwsWhenSupplyDoesNotExist() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);
        SupplyCode supplyCode = SupplyCode.of("UNKNOWN");

        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class,
                () -> service.synchronizeConsumptions(communityId, date, date, supplyCode));

        verifyNoInteractions(getDatadisConsumptionRepository);
        verifyNoInteractions(persistDatadisConsumptionRepository);
    }

    @Test
    void synchronizeConsumptionsBySupplyCode_throwsWhenSupplyHasNoCommunity() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);
        SupplyCode supplyCode = SupplyCode.of("ES001");

        Supply supply = SupplyMother.random().withCommunity(null).build();
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class,
                () -> service.synchronizeConsumptions(communityId, date, date, supplyCode));

        verifyNoInteractions(getDatadisConsumptionRepository);
        verifyNoInteractions(persistDatadisConsumptionRepository);
    }

    @Test
    void synchronizeConsumptionsBySupplyCode_throwsWhenSupplyBelongsToAnotherCommunity() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);
        SupplyCode supplyCode = SupplyCode.of("ES001");

        Community otherCommunity = CommunityMother.random().withId(UUID.randomUUID()).build();
        Supply supply = SupplyMother.random().withCommunity(otherCommunity).build();
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class,
                () -> service.synchronizeConsumptions(communityId, date, date, supplyCode));

        verifyNoInteractions(getDatadisConsumptionRepository);
        verifyNoInteractions(persistDatadisConsumptionRepository);
    }
}
