package org.lucoenergia.conluz.infrastructure.production.datadis.get;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.production.datadis.DatadisProduction;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionRepository;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionService;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetDatadisProductionServiceTest {

    @Mock
    private GetDatadisProductionRepository getDatadisProductionRepository;
    @Mock
    private GetPlantRepository getPlantRepository;
    @Mock
    private GetSupplyRepository getSupplyRepository;

    private GetDatadisProductionService service() {
        return new GetDatadisProductionServiceImpl(getDatadisProductionRepository, getPlantRepository,
                getSupplyRepository);
    }

    private static final OffsetDateTime START = OffsetDateTime.parse("2023-04-01T00:00:00Z");
    private static final OffsetDateTime END = OffsetDateTime.parse("2023-04-30T23:59:59Z");

    // --- community-wide reads (supplyId == null) delegate to the matching repository method ---

    @Test
    void getHourlyProduction_queriesHourlyRepository_withAllCommunityCups() {
        UUID communityId = UUID.randomUUID();
        Set<String> communityCups = Set.of("CUPS_A", "CUPS_B");
        List<DatadisProduction> expected = List.of(production("CUPS_A"));
        when(getPlantRepository.findSupplyCodesByCommunity(communityId)).thenReturn(communityCups);
        when(getDatadisProductionRepository.getHourlyProductionByRangeOfDates(anyCollection(), eq(START), eq(END)))
                .thenReturn(expected);

        List<DatadisProduction> result = service().getHourlyProduction(communityId, null, START, END);

        assertSame(expected, result);
        assertEquals(communityCups, capturedHourlyCups());
    }

    @Test
    void getDailyProduction_queriesDailyRepository_withAllCommunityCups() {
        UUID communityId = UUID.randomUUID();
        Set<String> communityCups = Set.of("CUPS_A", "CUPS_B");
        List<DatadisProduction> expected = List.of(production("CUPS_A"));
        when(getPlantRepository.findSupplyCodesByCommunity(communityId)).thenReturn(communityCups);
        when(getDatadisProductionRepository.getDailyProductionByRangeOfDates(anyCollection(), eq(START), eq(END)))
                .thenReturn(expected);

        List<DatadisProduction> result = service().getDailyProduction(communityId, null, START, END);

        assertSame(expected, result);
        ArgumentCaptor<Collection<String>> captor = collectionCaptor();
        verify(getDatadisProductionRepository).getDailyProductionByRangeOfDates(captor.capture(), eq(START), eq(END));
        assertEquals(communityCups, Set.copyOf(captor.getValue()));
    }

    @Test
    void getMonthlyProduction_queriesMonthlyRepository_withAllCommunityCups() {
        UUID communityId = UUID.randomUUID();
        Set<String> communityCups = Set.of("CUPS_A");
        List<DatadisProduction> expected = List.of(production("CUPS_A"));
        when(getPlantRepository.findSupplyCodesByCommunity(communityId)).thenReturn(communityCups);
        when(getDatadisProductionRepository.getMonthlyProductionByRangeOfDates(anyCollection(), eq(START), eq(END)))
                .thenReturn(expected);

        List<DatadisProduction> result = service().getMonthlyProduction(communityId, null, START, END);

        assertSame(expected, result);
        ArgumentCaptor<Collection<String>> captor = collectionCaptor();
        verify(getDatadisProductionRepository).getMonthlyProductionByRangeOfDates(captor.capture(), eq(START), eq(END));
        assertEquals(communityCups, Set.copyOf(captor.getValue()));
    }

    @Test
    void getYearlyProduction_queriesYearlyRepository_withAllCommunityCups() {
        UUID communityId = UUID.randomUUID();
        Set<String> communityCups = Set.of("CUPS_A");
        List<DatadisProduction> expected = List.of(production("CUPS_A"));
        when(getPlantRepository.findSupplyCodesByCommunity(communityId)).thenReturn(communityCups);
        when(getDatadisProductionRepository.getYearlyProductionByRangeOfDates(anyCollection(), eq(START), eq(END)))
                .thenReturn(expected);

        List<DatadisProduction> result = service().getYearlyProduction(communityId, null, START, END);

        assertSame(expected, result);
        ArgumentCaptor<Collection<String>> captor = collectionCaptor();
        verify(getDatadisProductionRepository).getYearlyProductionByRangeOfDates(captor.capture(), eq(START), eq(END));
        assertEquals(communityCups, Set.copyOf(captor.getValue()));
    }

    // --- community with no plant CUPS returns empty without touching the repository ---

    @Test
    void getHourlyProduction_returnsEmpty_whenCommunityHasNoPlantCupsAndNoSupplyGiven() {
        UUID communityId = UUID.randomUUID();
        when(getPlantRepository.findSupplyCodesByCommunity(communityId)).thenReturn(Set.of());

        List<DatadisProduction> result = service().getHourlyProduction(communityId, null, START, END);

        assertTrue(result.isEmpty());
        verifyNoInteractions(getDatadisProductionRepository);
        verifyNoInteractions(getSupplyRepository);
    }

    // --- single-supply reads narrow the query to that supply's CUPS ---

    @Test
    void getHourlyProduction_narrowsToSingleCups_whenSupplyBacksAPlantOfCommunity() {
        UUID communityId = UUID.randomUUID();
        Supply supply = SupplyMother.random().withCode("CUPS_A").build();
        SupplyId supplyId = SupplyId.of(supply.getId());
        List<DatadisProduction> expected = List.of(production("CUPS_A"));
        when(getPlantRepository.findSupplyCodesByCommunity(communityId)).thenReturn(Set.of("CUPS_A", "CUPS_B"));
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(getDatadisProductionRepository.getHourlyProductionByRangeOfDates(anyCollection(), eq(START), eq(END)))
                .thenReturn(expected);

        List<DatadisProduction> result = service().getHourlyProduction(communityId, supplyId, START, END);

        assertSame(expected, result);
        // Narrowed to exactly the requested supply's CUPS, not the whole community.
        assertEquals(List.of("CUPS_A"), List.copyOf(capturedHourlyCups()));
    }

    // --- out-of-community / unknown supply is rejected as 404 (SupplyNotFoundException) ---

    @Test
    void getHourlyProduction_throwsSupplyNotFound_whenSupplyDoesNotExist() {
        UUID communityId = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(UUID.randomUUID());
        when(getPlantRepository.findSupplyCodesByCommunity(communityId)).thenReturn(Set.of("CUPS_A"));
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class,
                () -> service().getHourlyProduction(communityId, supplyId, START, END));
        verify(getDatadisProductionRepository, never())
                .getHourlyProductionByRangeOfDates(anyCollection(), any(), any());
    }

    @Test
    void getHourlyProduction_throwsSupplyNotFound_whenSupplyDoesNotBackAPlantOfCommunity() {
        UUID communityId = UUID.randomUUID();
        Supply supply = SupplyMother.random().withCode("CUPS_OTHER").build();
        SupplyId supplyId = SupplyId.of(supply.getId());
        // The supply exists but its CUPS is not among the community's plant CUPS -> 404, no leak.
        when(getPlantRepository.findSupplyCodesByCommunity(communityId)).thenReturn(Set.of("CUPS_A", "CUPS_B"));
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class,
                () -> service().getHourlyProduction(communityId, supplyId, START, END));
        verify(getDatadisProductionRepository, never())
                .getHourlyProductionByRangeOfDates(anyCollection(), any(), any());
    }

    @Test
    void singleSupplyResolution_appliesToEveryGranularity() {
        // The supply-scoping / 404 logic is shared by all four reads; assert each rejects the unknown supply.
        UUID communityId = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(UUID.randomUUID());
        when(getPlantRepository.findSupplyCodesByCommunity(communityId)).thenReturn(Set.of("CUPS_A"));
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.empty());

        GetDatadisProductionService service = service();
        assertThrows(SupplyNotFoundException.class, () -> service.getHourlyProduction(communityId, supplyId, START, END));
        assertThrows(SupplyNotFoundException.class, () -> service.getDailyProduction(communityId, supplyId, START, END));
        assertThrows(SupplyNotFoundException.class, () -> service.getMonthlyProduction(communityId, supplyId, START, END));
        assertThrows(SupplyNotFoundException.class, () -> service.getYearlyProduction(communityId, supplyId, START, END));
        verifyNoInteractions(getDatadisProductionRepository);
    }

    // --- helpers ---

    private Collection<String> capturedHourlyCups() {
        ArgumentCaptor<Collection<String>> captor = collectionCaptor();
        verify(getDatadisProductionRepository).getHourlyProductionByRangeOfDates(captor.capture(), eq(START), eq(END));
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Collection<String>> collectionCaptor() {
        return ArgumentCaptor.forClass(Collection.class);
    }

    private static DatadisProduction production(String cups) {
        DatadisProduction production = new DatadisProduction();
        production.setCups(cups);
        return production;
    }
}
