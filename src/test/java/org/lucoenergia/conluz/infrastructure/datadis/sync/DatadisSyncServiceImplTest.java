package org.lucoenergia.conluz.infrastructure.datadis.sync;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.DisplayName;
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
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.DatadisHourlyRecord;
import org.lucoenergia.conluz.domain.datadis.get.GetDatadisConfigRepository;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncService;
import org.lucoenergia.conluz.domain.production.datadis.DatadisProduction;
import org.lucoenergia.conluz.domain.production.datadis.PersistDatadisProductionRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.admin.supply.DatadisSupplyConfigurationException;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisDisabledException;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisHourlyInstantResolver;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionMapper;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private final GetDatadisConfigRepository getDatadisConfigRepository =
            Mockito.mock(GetDatadisConfigRepository.class);

    private final DatadisHourlyInstantResolver datadisHourlyInstantResolver =
            new DatadisHourlyInstantResolver(madridTimeConfiguration());

    private final DatadisSyncService service = new DatadisSyncServiceImpl(
            getDatadisConsumptionRepository, getSupplyRepository, persistDatadisConsumptionRepository,
            getPlantRepository, datadisProductionMapper, persistDatadisProductionRepository,
            getDatadisConfigRepository, datadisHourlyInstantResolver);

    private static TimeConfiguration madridTimeConfiguration() {
        TimeConfiguration timeConfiguration = Mockito.mock(TimeConfiguration.class);
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("Europe/Madrid"));
        return timeConfiguration;
    }

    private static DatadisConfig config(boolean enabled) {
        return new DatadisConfig.Builder()
                .setUsername("u")
                .setPassword("p")
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .setEnabled(enabled)
                .build();
    }

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

        List<DatadisConsumption> consumptions = List.of(consumption("ES001", 1.0f));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt()))
                .thenReturn(consumptions);

        service.synchronize(communityId, date, date);

        verify(getDatadisConsumptionRepository).getHourlyConsumptionsByMonth(supplyOne, Month.JANUARY, 2024);
        verify(getDatadisConsumptionRepository).getHourlyConsumptionsByMonth(supplyTwo, Month.JANUARY, 2024);
        verify(persistDatadisConsumptionRepository, times(2))
                .persistHourlyConsumptions(datadisHourlyInstantResolver.resolve(consumptions).records());
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

        List<DatadisConsumption> consumptions = List.of(consumption("ES001", 1.0f));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(supply, Month.JANUARY, 2024))
                .thenThrow(new DatadisSupplyConfigurationException("boom"));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(supply, Month.FEBRUARY, 2024))
                .thenReturn(consumptions);

        service.synchronize(communityId, startDate, endDate);

        // The failure for January must not stop February from being processed and persisted.
        verify(persistDatadisConsumptionRepository, times(1))
                .persistHourlyConsumptions(datadisHourlyInstantResolver.resolve(consumptions).records());
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
        ArgumentCaptor<List<DatadisHourlyRecord<DatadisProduction>>> productionCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(persistDatadisProductionRepository).persistHourlyProductions(productionCaptor.capture());
        List<DatadisHourlyRecord<DatadisProduction>> productions = productionCaptor.getValue();
        assertEquals(1, productions.size());
        assertEquals(3.5f, productions.get(0).record().getProductionKWh());
        assertEquals(plantSupply.getCode(), productions.get(0).record().getCups());

        // Consumption is persisted from a copy whose surplus is zeroed.
        ArgumentCaptor<List<DatadisHourlyRecord<DatadisConsumption>>> consumptionCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(persistDatadisConsumptionRepository).persistHourlyConsumptions(consumptionCaptor.capture());
        List<DatadisHourlyRecord<DatadisConsumption>> persisted = consumptionCaptor.getValue();
        assertEquals(1, persisted.size());
        assertEquals(0f, persisted.get(0).record().getSurplusEnergyKWh());

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

        verify(persistDatadisConsumptionRepository)
                .persistHourlyConsumptions(datadisHourlyInstantResolver.resolve(List.of(original)).records());
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

        List<DatadisConsumption> consumptions = List.of(consumption("ES001", 1.0f));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(supply), any(Month.class), anyInt()))
                .thenReturn(consumptions);

        service.synchronize(communityId, date, date, supplyCode);

        verify(getDatadisConsumptionRepository).getHourlyConsumptionsByMonth(supply, Month.JANUARY, 2024);
        verify(persistDatadisConsumptionRepository)
                .persistHourlyConsumptions(datadisHourlyInstantResolver.resolve(consumptions).records());
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

        ArgumentCaptor<List<DatadisHourlyRecord<DatadisProduction>>> productionCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(persistDatadisProductionRepository).persistHourlyProductions(productionCaptor.capture());
        assertEquals(4.0f, productionCaptor.getValue().get(0).record().getProductionKWh());

        ArgumentCaptor<List<DatadisHourlyRecord<DatadisConsumption>>> consumptionCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(persistDatadisConsumptionRepository).persistHourlyConsumptions(consumptionCaptor.capture());
        assertEquals(0f, consumptionCaptor.getValue().get(0).record().getSurplusEnergyKWh());
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

    // ---------------------------------------------------------------------
    // synchronize(communityId, startDate, endDate, String supplyCode): config gating + dispatch
    // (moved out of the controller)
    // ---------------------------------------------------------------------

    @Test
    void synchronizeManual_throwsWhenDatadisConfigIsMissing() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);
        when(getDatadisConfigRepository.findByCommunityId(communityId)).thenReturn(Optional.empty());

        assertThrows(DatadisDisabledException.class,
                () -> service.synchronize(communityId, date, date, (String) null));

        verifyNoInteractions(getSupplyRepository, getDatadisConsumptionRepository,
                persistDatadisConsumptionRepository, persistDatadisProductionRepository);
    }

    @Test
    void synchronizeManual_throwsWhenDatadisConfigIsDisabled() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);
        when(getDatadisConfigRepository.findByCommunityId(communityId)).thenReturn(Optional.of(config(false)));

        assertThrows(DatadisDisabledException.class,
                () -> service.synchronize(communityId, date, date, "ES001"));

        verifyNoInteractions(getSupplyRepository, getDatadisConsumptionRepository,
                persistDatadisConsumptionRepository, persistDatadisProductionRepository);
    }

    @Test
    void synchronizeManual_withNoSupplyCode_dispatchesToWholeCommunity() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);
        when(getDatadisConfigRepository.findByCommunityId(communityId)).thenReturn(Optional.of(config(true)));
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(Collections.emptyList());

        service.synchronize(communityId, date, date, (String) null);

        verify(getSupplyRepository).findAllByCommunityId(communityId);
        verify(getSupplyRepository, never()).findByCode(any(SupplyCode.class));
    }

    @Test
    void synchronizeManual_withSupplyCode_dispatchesToSingleSupply() {
        UUID communityId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2024, 1, 10);
        Community community = CommunityMother.random().withId(communityId).build();
        Supply supply = SupplyMother.random().withCommunity(community).build();
        when(getDatadisConfigRepository.findByCommunityId(communityId)).thenReturn(Optional.of(config(true)));
        when(getSupplyRepository.findByCode(SupplyCode.of("ES001"))).thenReturn(Optional.of(supply));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(supply), any(Month.class), anyInt()))
                .thenReturn(Collections.emptyList());

        service.synchronize(communityId, date, date, "ES001");

        verify(getSupplyRepository).findByCode(SupplyCode.of("ES001"));
        verify(getSupplyRepository, never()).findAllByCommunityId(any(UUID.class));
    }

    // ---------------------------------------------------------------------
    // Instants of the hourly records (#322)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("DHI-002 AC4 a day placed by label is logged once, with the supply id, the date and both counts, never the CUPS")
    void aDayPlacedByLabelIsLoggedOnceWithoutTheCups() {
        UUID communityId = UUID.randomUUID();
        Supply supply = SupplyMother.random().build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(supply));

        List<String> withoutTwoPm = hourLabels();
        withoutTwoPm.remove("14:00");
        List<DatadisConsumption> records = new ArrayList<>(day(supply.getCode(), "2025/11/04", hourLabels(), 0f));
        records.addAll(day(supply.getCode(), "2025/11/05", withoutTwoPm, 100f));
        records.addAll(day(supply.getCode(), "2025/11/06", hourLabels(), 200f));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(supply), any(Month.class), anyInt()))
                .thenReturn(records);

        try (LogCapture logs = LogCapture.of(DatadisSyncServiceImpl.class)) {
            service.synchronize(communityId, LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 1));

            List<ILoggingEvent> warnings = logs.warningsAndAbove();
            assertEquals(1, warnings.size(), () -> "warnings: " + warnings);
            ILoggingEvent warning = warnings.get(0);
            assertArrayEquals(new Object[]{23, supply.getId(), LocalDate.of(2025, 11, 5), 24},
                    warning.getArgumentArray());
            assertNull(warning.getThrowableProxy());
            for (ILoggingEvent event : logs.all()) {
                assertFalse(event.getFormattedMessage().contains(supply.getCode()),
                        () -> "the CUPS is logged: " + event.getFormattedMessage());
            }
        }
    }

    @Test
    @DisplayName("DHI-003 AC5 a plant supply's hourly production is stored at the same instants as its hourly consumption")
    void aPlantSuppliesProductionIsStoredAtTheInstantsOfItsConsumption() {
        UUID communityId = UUID.randomUUID();
        Supply plantSupply = SupplyMother.random().build();
        when(getSupplyRepository.findAllByCommunityId(communityId)).thenReturn(List.of(plantSupply));
        when(getPlantRepository.findSupplyCodesByCommunity(communityId)).thenReturn(Set.of(plantSupply.getCode()));

        String cups = plantSupply.getCode();
        List<String> withoutTwoPm = hourLabels();
        withoutTwoPm.remove("14:00");
        // An ordinary day, the day the clocks go back, and a day missing an hour.
        List<DatadisConsumption> october = new ArrayList<>(day(cups, "2025/10/25", hourLabels(), 0f));
        october.addAll(day(cups, "2025/10/26", fallBackLabels(), 100f));
        october.addAll(day(cups, "2025/10/27", withoutTwoPm, 200f));
        // The day the clocks go forward.
        List<DatadisConsumption> march = new ArrayList<>(day(cups, "2026/03/29", springForwardLabels(), 300f));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(plantSupply, Month.OCTOBER, 2025))
                .thenReturn(october);
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(plantSupply, Month.MARCH, 2026))
                .thenReturn(march);

        service.synchronize(communityId, LocalDate.of(2025, 10, 1), LocalDate.of(2026, 3, 1));

        ArgumentCaptor<List<DatadisHourlyRecord<DatadisProduction>>> productionCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(persistDatadisProductionRepository, times(2)).persistHourlyProductions(productionCaptor.capture());
        ArgumentCaptor<List<DatadisHourlyRecord<DatadisConsumption>>> consumptionCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(persistDatadisConsumptionRepository, times(2)).persistHourlyConsumptions(consumptionCaptor.capture());

        List<DatadisHourlyRecord<DatadisProduction>> productions =
                productionCaptor.getAllValues().stream().flatMap(List::stream).toList();
        List<DatadisHourlyRecord<DatadisConsumption>> consumptions =
                consumptionCaptor.getAllValues().stream().flatMap(List::stream).toList();
        assertEquals(24 + 25 + 23 + 23, productions.size());
        assertEquals(consumptions.stream().map(DatadisHourlyRecord::instant).toList(),
                productions.stream().map(DatadisHourlyRecord::instant).toList());
        // Each production point carries the surplus of the consumption record stored at its instant.
        List<DatadisHourlyRecord<DatadisConsumption>> resolved = new ArrayList<>(
                datadisHourlyInstantResolver.resolve(october).records());
        resolved.addAll(datadisHourlyInstantResolver.resolve(march).records());
        for (int i = 0; i < productions.size(); i++) {
            assertEquals(resolved.get(i).instant(), productions.get(i).instant());
            assertEquals(resolved.get(i).record().getSurplusEnergyKWh(), productions.get(i).record().getProductionKWh());
            assertEquals(0f, consumptions.get(i).record().getSurplusEnergyKWh());
        }
        assertEquals(Instant.parse("2025-10-26T00:00:00Z"), productions.get(24 + 2).instant());
        assertEquals(Instant.parse("2025-10-26T01:00:00Z"), productions.get(24 + 3).instant());
    }

    private static List<String> hourLabels() {
        List<String> labels = new ArrayList<>();
        for (int hour = 1; hour <= 24; hour++) {
            labels.add(String.format("%02d:00", hour));
        }
        return labels;
    }

    /** As Datadis labels 2025/10/26: 03:00 twice. */
    private static List<String> fallBackLabels() {
        List<String> labels = hourLabels();
        labels.add(2, "03:00");
        return labels;
    }

    /** As Datadis labels 2026/03/29: no 03:00. */
    private static List<String> springForwardLabels() {
        List<String> labels = hourLabels();
        labels.remove("03:00");
        return labels;
    }

    /** One record per label, in order, the n-th with a consumption and a surplus of {@code base + n}. */
    private static List<DatadisConsumption> day(String cups, String date, List<String> labels, float base) {
        List<DatadisConsumption> records = new ArrayList<>();
        for (int position = 0; position < labels.size(); position++) {
            DatadisConsumption record = new DatadisConsumption();
            record.setCups(cups);
            record.setDate(date);
            record.setTime(labels.get(position));
            record.setConsumptionKWh(base + position + 1);
            record.setSurplusEnergyKWh(base + position + 1);
            record.setObtainMethod("Real");
            records.add(record);
        }
        return records;
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
