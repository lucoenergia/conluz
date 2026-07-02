package org.lucoenergia.conluz.infrastructure.datadis.sync;

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
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncService;
import org.lucoenergia.conluz.domain.production.datadis.DatadisProduction;
import org.lucoenergia.conluz.domain.production.datadis.PersistDatadisProductionRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.admin.supply.DatadisSupplyConfigurationException;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionMapper;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.Month;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Unit tests for {@link DatadisSyncServiceImpl}. The service iterates over the supplies in scope,
 * derives production from surplus for plant supplies (zeroing the surplus on the persisted
 * consumption), and delegates retrieval/persistence to the Datadis repositories. Authorization is
 * enforced at the controller layer, so the service performs orchestration only.
 */
class DatadisSyncServiceImplTest {

    private final GetDatadisConsumptionRepository getDatadisConsumptionRepository =
            Mockito.mock(GetDatadisConsumptionRepository.class);
    private final GetSupplyRepository getSupplyRepository = Mockito.mock(GetSupplyRepository.class);
    private final PersistDatadisConsumptionRepository persistDatadisConsumptionRepository =
            Mockito.mock(PersistDatadisConsumptionRepository.class);
    private final GetPlantRepository getPlantRepository = Mockito.mock(GetPlantRepository.class);
    private final DatadisProductionMapper datadisProductionMapper = new DatadisProductionMapper();
    private final PersistDatadisProductionRepository persistDatadisProductionRepository =
            Mockito.mock(PersistDatadisProductionRepository.class);

    private final DatadisSyncService service = new DatadisSyncServiceImpl(
            getDatadisConsumptionRepository, getSupplyRepository, persistDatadisConsumptionRepository,
            getPlantRepository, datadisProductionMapper, persistDatadisProductionRepository);

    // ---------------------------------------------------------------------
    // synchronize(communityId, startDate, endDate)
    // ---------------------------------------------------------------------

    @Test
    void synchronizeByCommunity_retrievesAndPersistsConsumptionsForEachSupply() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);

        Supply supplyOne = SupplyMother.random().build();
        Supply supplyTwo = SupplyMother.random().build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supplyOne, supplyTwo));

        List<DatadisConsumption> consumptions = List.of(Mockito.mock(DatadisConsumption.class));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt()))
                .thenReturn(consumptions);

        service.synchronize(communityId, date, date);

        verify(getDatadisConsumptionRepository).getHourlyConsumptionsByMonth(supplyOne, Month.JANUARY, 2024);
        verify(getDatadisConsumptionRepository).getHourlyConsumptionsByMonth(supplyTwo, Month.JANUARY, 2024);
        verify(persistDatadisConsumptionRepository, times(2)).persistHourlyConsumptions(consumptions);
        verifyNoInteractions(persistDatadisProductionRepository);
    }

    @Test
    void synchronizeByCommunity_iteratesOverEachMonthInTheRange() {
        UUID communityId = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2024, 1, 10);
        LocalDate endDate = LocalDate.of(2024, 3, 10);

        Supply supply = SupplyMother.random().build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supply));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt()))
                .thenReturn(Collections.emptyList());

        service.synchronize(communityId, startDate, endDate);

        verify(getDatadisConsumptionRepository).getHourlyConsumptionsByMonth(supply, Month.JANUARY, 2024);
        verify(getDatadisConsumptionRepository).getHourlyConsumptionsByMonth(supply, Month.FEBRUARY, 2024);
        verify(getDatadisConsumptionRepository).getHourlyConsumptionsByMonth(supply, Month.MARCH, 2024);
    }

    @Test
    void synchronizeByCommunity_doesNotPersistWhenConsumptionsAreEmpty() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);

        Supply supply = SupplyMother.random().build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supply));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt()))
                .thenReturn(Collections.emptyList());

        service.synchronize(communityId, date, date);

        verify(persistDatadisConsumptionRepository, never()).persistHourlyConsumptions(any());
        verifyNoInteractions(persistDatadisProductionRepository);
    }

    @Test
    void synchronizeByCommunity_skipsSuppliesWithoutDistributorCode() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);

        Supply supplyWithoutDistributor = SupplyMother.random().withDistributor(null).build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supplyWithoutDistributor));

        service.synchronize(communityId, date, date);

        verifyNoInteractions(getDatadisConsumptionRepository);
        verifyNoInteractions(persistDatadisConsumptionRepository);
        verifyNoInteractions(persistDatadisProductionRepository);
    }

    @Test
    void synchronizeByCommunity_doesNothingWhenCommunityHasNoSupplies() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);

        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(Collections.emptyList());

        service.synchronize(communityId, date, date);

        verifyNoInteractions(getDatadisConsumptionRepository);
        verifyNoInteractions(persistDatadisConsumptionRepository);
        verifyNoInteractions(persistDatadisProductionRepository);
    }

    @Test
    void synchronizeByCommunity_continuesWhenRetrievalFailsForAMonth() {
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

        service.synchronize(communityId, startDate, endDate);

        // The failure for January must not stop February from being processed and persisted.
        verify(persistDatadisConsumptionRepository, times(1)).persistHourlyConsumptions(consumptions);
    }

    // ---------------------------------------------------------------------
    // Plant supplies: production derivation + zeroed consumption
    // ---------------------------------------------------------------------

    @Test
    void synchronizeByCommunity_forPlantSupply_persistsProductionFromSurplusAndZeroesConsumptionSurplus() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);

        Supply plantSupply = SupplyMother.random().build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(plantSupply));
        when(getPlantRepository.findSupplyCodesByCommunity(communityId)).thenReturn(Set.of(plantSupply.getCode()));

        DatadisConsumption original = consumption(plantSupply.getCode(), 3.5f);
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(plantSupply), any(Month.class), anyInt()))
                .thenReturn(List.of(original));

        service.synchronize(communityId, date, date);

        // Production is derived from the original surplus.
        ArgumentCaptor<List<DatadisProduction>> productionCaptor = ArgumentCaptor.forClass(List.class);
        verify(persistDatadisProductionRepository).persistHourlyProductions(productionCaptor.capture());
        List<DatadisProduction> productions = productionCaptor.getValue();
        assertEquals(1, productions.size());
        assertEquals(3.5f, productions.get(0).getProductionKWh());
        assertEquals(plantSupply.getCode(), productions.get(0).getCups());

        // Consumption is persisted from a copy whose surplus is zeroed.
        ArgumentCaptor<List<DatadisConsumption>> consumptionCaptor = ArgumentCaptor.forClass(List.class);
        verify(persistDatadisConsumptionRepository).persistHourlyConsumptions(consumptionCaptor.capture());
        List<DatadisConsumption> persisted = consumptionCaptor.getValue();
        assertEquals(1, persisted.size());
        assertEquals(0f, persisted.get(0).getSurplusEnergyKWh());

        // The original DTO must not be mutated.
        assertEquals(3.5f, original.getSurplusEnergyKWh());
    }

    @Test
    void synchronizeByCommunity_forNonPlantSupply_neverTouchesProductionAndKeepsSurplus() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);

        Supply supply = SupplyMother.random().build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supply));
        // Community has a plant, but for a different supply code.
        when(getPlantRepository.findSupplyCodesByCommunity(communityId)).thenReturn(Set.of("OTHER_CUPS"));

        DatadisConsumption original = consumption(supply.getCode(), 2.0f);
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(supply), any(Month.class), anyInt()))
                .thenReturn(List.of(original));

        service.synchronize(communityId, date, date);

        verify(persistDatadisConsumptionRepository).persistHourlyConsumptions(List.of(original));
        verifyNoInteractions(persistDatadisProductionRepository);
        // Surplus is untouched.
        assertEquals(2.0f, original.getSurplusEnergyKWh());
    }

    // ---------------------------------------------------------------------
    // synchronize(communityId, startDate, endDate, supplyCode)
    // ---------------------------------------------------------------------

    @Test
    void synchronizeBySupplyCode_processesSupplyWhenItBelongsToTheCommunity() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);
        SupplyCode supplyCode = SupplyCode.of("ES001");

        Community community = CommunityMother.random().withId(communityId).build();
        Supply supply = SupplyMother.random().withCommunity(community).build();
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.of(supply));

        List<DatadisConsumption> consumptions = List.of(Mockito.mock(DatadisConsumption.class));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(supply), any(Month.class), anyInt()))
                .thenReturn(consumptions);

        service.synchronize(communityId, date, date, supplyCode);

        verify(getDatadisConsumptionRepository).getHourlyConsumptionsByMonth(supply, Month.JANUARY, 2024);
        verify(persistDatadisConsumptionRepository).persistHourlyConsumptions(consumptions);
        verifyNoInteractions(persistDatadisProductionRepository);
    }

    @Test
    void synchronizeBySupplyCode_forPlantSupply_persistsProductionAndZeroedConsumption() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);
        SupplyCode supplyCode = SupplyCode.of("ES001");

        Community community = CommunityMother.random().withId(communityId).build();
        Supply plantSupply = SupplyMother.random().withCommunity(community).build();
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.of(plantSupply));
        when(getPlantRepository.findSupplyCodesByCommunity(communityId)).thenReturn(Set.of(plantSupply.getCode()));

        DatadisConsumption original = consumption(plantSupply.getCode(), 4.0f);
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(plantSupply), any(Month.class), anyInt()))
                .thenReturn(List.of(original));

        service.synchronize(communityId, date, date, supplyCode);

        ArgumentCaptor<List<DatadisProduction>> productionCaptor = ArgumentCaptor.forClass(List.class);
        verify(persistDatadisProductionRepository).persistHourlyProductions(productionCaptor.capture());
        assertEquals(4.0f, productionCaptor.getValue().get(0).getProductionKWh());

        ArgumentCaptor<List<DatadisConsumption>> consumptionCaptor = ArgumentCaptor.forClass(List.class);
        verify(persistDatadisConsumptionRepository).persistHourlyConsumptions(consumptionCaptor.capture());
        assertEquals(0f, consumptionCaptor.getValue().get(0).getSurplusEnergyKWh());
        assertEquals(4.0f, original.getSurplusEnergyKWh());
    }

    @Test
    void synchronizeBySupplyCode_throwsWhenSupplyDoesNotExist() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);
        SupplyCode supplyCode = SupplyCode.of("UNKNOWN");

        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class,
                () -> service.synchronize(communityId, date, date, supplyCode));

        verifyNoInteractions(getDatadisConsumptionRepository);
        verifyNoInteractions(persistDatadisConsumptionRepository);
        verifyNoInteractions(persistDatadisProductionRepository);
    }

    @Test
    void synchronizeBySupplyCode_throwsWhenSupplyHasNoCommunity() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);
        SupplyCode supplyCode = SupplyCode.of("ES001");

        Supply supply = SupplyMother.random().withCommunity(null).build();
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class,
                () -> service.synchronize(communityId, date, date, supplyCode));

        verifyNoInteractions(getDatadisConsumptionRepository);
        verifyNoInteractions(persistDatadisConsumptionRepository);
        verifyNoInteractions(persistDatadisProductionRepository);
    }

    @Test
    void synchronizeBySupplyCode_throwsWhenSupplyBelongsToAnotherCommunity() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);
        SupplyCode supplyCode = SupplyCode.of("ES001");

        Community otherCommunity = CommunityMother.random().withId(UUID.randomUUID()).build();
        Supply supply = SupplyMother.random().withCommunity(otherCommunity).build();
        when(getSupplyRepository.findByCode(supplyCode)).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class,
                () -> service.synchronize(communityId, date, date, supplyCode));

        verifyNoInteractions(getDatadisConsumptionRepository);
        verifyNoInteractions(persistDatadisConsumptionRepository);
        verifyNoInteractions(persistDatadisProductionRepository);
    }

    private DatadisConsumption consumption(String cups, Float surplus) {
        DatadisConsumption consumption = new DatadisConsumption();
        consumption.setCups(cups);
        consumption.setDate("2024/01/10");
        consumption.setTime("10:00");
        consumption.setConsumptionKWh(1.0f);
        consumption.setObtainMethod("Real");
        consumption.setSurplusEnergyKWh(surplus);
        return consumption;
    }
}
