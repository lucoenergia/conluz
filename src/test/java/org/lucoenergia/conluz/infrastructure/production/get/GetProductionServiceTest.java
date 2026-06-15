package org.lucoenergia.conluz.infrastructure.production.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.domain.production.get.GetProductionRepository;
import org.lucoenergia.conluz.domain.production.get.GetProductionService;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetProductionServiceImpl}. Authorization is enforced at the controller layer
 * (via {@code @PreAuthorize}), so this service performs data access and community-scoping only.
 */
class GetProductionServiceTest {

    private final GetProductionRepository getProductionRepository = Mockito.mock(GetProductionRepository.class);
    private final GetSupplyRepository getSupplyRepository = Mockito.mock(GetSupplyRepository.class);
    private final GetPlantRepository getPlantRepository = Mockito.mock(GetPlantRepository.class);

    private final GetProductionService service = new GetProductionServiceImpl(
            getProductionRepository, getSupplyRepository, getPlantRepository);

    private final OffsetDateTime startDate = OffsetDateTime.parse("2023-09-01T00:00:00.000+02:00");
    private final OffsetDateTime endDate = OffsetDateTime.parse("2023-09-01T23:00:00.000+02:00");

    // --- Supply-scoped variants ---

    @Test
    void getHourlyProductionByRangeOfDatesAndSupply_appliesSupplyPartitionCoefficient() {
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        Supply supply = SupplyMother.random().withId(supplyUuid).withPartitionCoefficient(0.4f).build();
        List<ProductionByTime> expected = List.of(new ProductionByTime(startDate, 10d));

        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(getProductionRepository.getHourlyProductionByRangeOfDates(startDate, endDate, 0.4f))
                .thenReturn(expected);

        List<ProductionByTime> result = service.getHourlyProductionByRangeOfDatesAndSupply(startDate, endDate, supplyId);

        assertSame(expected, result);
        verify(getProductionRepository).getHourlyProductionByRangeOfDates(startDate, endDate, 0.4f);
    }

    @Test
    void getHourlyProductionByRangeOfDatesAndSupply_throwsWhenSupplyNotFound() {
        SupplyId supplyId = SupplyId.of(UUID.randomUUID());
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class,
                () -> service.getHourlyProductionByRangeOfDatesAndSupply(startDate, endDate, supplyId));
        verifyNoInteractions(getProductionRepository);
    }

    @Test
    void getDailyProductionByRangeOfDatesAndSupply_appliesSupplyPartitionCoefficient() {
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        Supply supply = SupplyMother.random().withId(supplyUuid).withPartitionCoefficient(0.25f).build();
        List<ProductionByTime> expected = List.of(new ProductionByTime(startDate, 5d));

        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(getProductionRepository.getDailyProductionByRangeOfDates(startDate, endDate, 0.25f))
                .thenReturn(expected);

        List<ProductionByTime> result = service.getDailyProductionByRangeOfDatesAndSupply(startDate, endDate, supplyId);

        assertSame(expected, result);
        verify(getProductionRepository).getDailyProductionByRangeOfDates(startDate, endDate, 0.25f);
    }

    @Test
    void getDailyProductionByRangeOfDatesAndSupply_throwsWhenSupplyNotFound() {
        SupplyId supplyId = SupplyId.of(UUID.randomUUID());
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class,
                () -> service.getDailyProductionByRangeOfDatesAndSupply(startDate, endDate, supplyId));
        verifyNoInteractions(getProductionRepository);
    }

    @Test
    void getMonthlyProductionByRangeOfDatesAndSupply_appliesSupplyPartitionCoefficient() {
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        Supply supply = SupplyMother.random().withId(supplyUuid).withPartitionCoefficient(0.75f).build();
        List<ProductionByTime> expected = List.of(new ProductionByTime(startDate, 100d));

        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(getProductionRepository.getMonthlyProductionByRangeOfDates(startDate, endDate, 0.75f))
                .thenReturn(expected);

        List<ProductionByTime> result = service.getMonthlyProductionByRangeOfDatesAndSupply(startDate, endDate, supplyId);

        assertSame(expected, result);
        verify(getProductionRepository).getMonthlyProductionByRangeOfDates(startDate, endDate, 0.75f);
    }

    @Test
    void getMonthlyProductionByRangeOfDatesAndSupply_throwsWhenSupplyNotFound() {
        SupplyId supplyId = SupplyId.of(UUID.randomUUID());
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class,
                () -> service.getMonthlyProductionByRangeOfDatesAndSupply(startDate, endDate, supplyId));
        verifyNoInteractions(getProductionRepository);
    }

    // --- Community-scoped variants ---

    @Test
    void getInstantProductionByCommunity_queriesCommunityPlantCodes() {
        UUID communityId = UUID.randomUUID();
        List<String> stationCodes = List.of("PLANT001", "PLANT002");
        InstantProduction expected = new InstantProduction(42d);

        when(getPlantRepository.findPlantCodesByCommunity(communityId)).thenReturn(stationCodes);
        when(getProductionRepository.getInstantProduction(stationCodes)).thenReturn(expected);

        InstantProduction result = service.getInstantProductionByCommunity(communityId);

        assertSame(expected, result);
        verify(getProductionRepository).getInstantProduction(stationCodes);
    }

    @Test
    void getInstantProductionByCommunityAndSupply_appliesSupplyPartitionCoefficient() {
        UUID communityId = UUID.randomUUID();
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        Community community = CommunityMother.random().withId(communityId).build();
        Supply supply = SupplyMother.random().withId(supplyUuid)
                .withPartitionCoefficient(0.5f).withCommunity(community).build();
        List<String> stationCodes = List.of("PLANT001");

        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(getPlantRepository.findPlantCodesByCommunity(communityId)).thenReturn(stationCodes);
        when(getProductionRepository.getInstantProduction(stationCodes)).thenReturn(new InstantProduction(80d));

        InstantProduction result = service.getInstantProductionByCommunityAndSupply(communityId, supplyId);

        assertEquals(40d, result.getPower(), 0.0001d);
    }

    @Test
    void getInstantProductionByCommunityAndSupply_throwsWhenSupplyBelongsToAnotherCommunity() {
        UUID communityId = UUID.randomUUID();
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        Community otherCommunity = CommunityMother.random().withId(UUID.randomUUID()).build();
        Supply supply = SupplyMother.random().withId(supplyUuid).withCommunity(otherCommunity).build();

        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class,
                () -> service.getInstantProductionByCommunityAndSupply(communityId, supplyId));
        verify(getProductionRepository, never()).getInstantProduction(Mockito.anyCollection());
    }

    @Test
    void getInstantProductionByCommunityAndSupply_throwsWhenSupplyHasNoCommunity() {
        UUID communityId = UUID.randomUUID();
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        Supply supply = SupplyMother.random().withId(supplyUuid).withCommunity(null).build();

        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class,
                () -> service.getInstantProductionByCommunityAndSupply(communityId, supplyId));
    }

    @Test
    void getHourlyProductionByRangeOfDatesAndCommunity_usesCoefficientOneAndCommunityPlantCodes() {
        UUID communityId = UUID.randomUUID();
        List<String> stationCodes = List.of("PLANT001");
        List<ProductionByTime> expected = List.of(new ProductionByTime(startDate, 10d));

        when(getPlantRepository.findPlantCodesByCommunity(communityId)).thenReturn(stationCodes);
        when(getProductionRepository.getHourlyProductionByRangeOfDates(startDate, endDate, 1f, stationCodes))
                .thenReturn(expected);

        List<ProductionByTime> result = service.getHourlyProductionByRangeOfDatesAndCommunity(startDate, endDate, communityId);

        assertSame(expected, result);
        verify(getProductionRepository).getHourlyProductionByRangeOfDates(startDate, endDate, 1f, stationCodes);
    }

    @Test
    void getHourlyProductionByRangeOfDatesAndCommunityAndSupply_appliesSupplyCoefficientAndCommunityPlantCodes() {
        UUID communityId = UUID.randomUUID();
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        Community community = CommunityMother.random().withId(communityId).build();
        Supply supply = SupplyMother.random().withId(supplyUuid)
                .withPartitionCoefficient(0.3f).withCommunity(community).build();
        List<String> stationCodes = List.of("PLANT001");
        List<ProductionByTime> expected = List.of(new ProductionByTime(startDate, 3d));

        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(getPlantRepository.findPlantCodesByCommunity(communityId)).thenReturn(stationCodes);
        when(getProductionRepository.getHourlyProductionByRangeOfDates(startDate, endDate, 0.3f, stationCodes))
                .thenReturn(expected);

        List<ProductionByTime> result = service.getHourlyProductionByRangeOfDatesAndCommunityAndSupply(
                startDate, endDate, communityId, supplyId);

        assertSame(expected, result);
        verify(getProductionRepository).getHourlyProductionByRangeOfDates(startDate, endDate, 0.3f, stationCodes);
    }

    @Test
    void getDailyProductionByRangeOfDatesAndCommunity_usesCoefficientOneAndCommunityPlantCodes() {
        UUID communityId = UUID.randomUUID();
        List<String> stationCodes = List.of("PLANT001");
        List<ProductionByTime> expected = List.of(new ProductionByTime(startDate, 11d));

        when(getPlantRepository.findPlantCodesByCommunity(communityId)).thenReturn(stationCodes);
        when(getProductionRepository.getDailyProductionByRangeOfDates(startDate, endDate, 1f, stationCodes))
                .thenReturn(expected);

        List<ProductionByTime> result = service.getDailyProductionByRangeOfDatesAndCommunity(startDate, endDate, communityId);

        assertSame(expected, result);
        verify(getProductionRepository).getDailyProductionByRangeOfDates(startDate, endDate, 1f, stationCodes);
    }

    @Test
    void getDailyProductionByRangeOfDatesAndCommunityAndSupply_appliesSupplyCoefficientAndCommunityPlantCodes() {
        UUID communityId = UUID.randomUUID();
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        Community community = CommunityMother.random().withId(communityId).build();
        Supply supply = SupplyMother.random().withId(supplyUuid)
                .withPartitionCoefficient(0.6f).withCommunity(community).build();
        List<String> stationCodes = List.of("PLANT001");
        List<ProductionByTime> expected = List.of(new ProductionByTime(startDate, 6d));

        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(getPlantRepository.findPlantCodesByCommunity(communityId)).thenReturn(stationCodes);
        when(getProductionRepository.getDailyProductionByRangeOfDates(startDate, endDate, 0.6f, stationCodes))
                .thenReturn(expected);

        List<ProductionByTime> result = service.getDailyProductionByRangeOfDatesAndCommunityAndSupply(
                startDate, endDate, communityId, supplyId);

        assertSame(expected, result);
        verify(getProductionRepository).getDailyProductionByRangeOfDates(startDate, endDate, 0.6f, stationCodes);
    }

    @Test
    void getMonthlyProductionByRangeOfDatesAndCommunity_usesCoefficientOneAndCommunityPlantCodes() {
        UUID communityId = UUID.randomUUID();
        List<String> stationCodes = List.of("PLANT001");
        List<ProductionByTime> expected = List.of(new ProductionByTime(startDate, 12d));

        when(getPlantRepository.findPlantCodesByCommunity(communityId)).thenReturn(stationCodes);
        when(getProductionRepository.getMonthlyProductionByRangeOfDates(startDate, endDate, 1f, stationCodes))
                .thenReturn(expected);

        List<ProductionByTime> result = service.getMonthlyProductionByRangeOfDatesAndCommunity(startDate, endDate, communityId);

        assertSame(expected, result);
        verify(getProductionRepository).getMonthlyProductionByRangeOfDates(startDate, endDate, 1f, stationCodes);
    }

    @Test
    void getMonthlyProductionByRangeOfDatesAndCommunityAndSupply_appliesSupplyCoefficientAndCommunityPlantCodes() {
        UUID communityId = UUID.randomUUID();
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        Community community = CommunityMother.random().withId(communityId).build();
        Supply supply = SupplyMother.random().withId(supplyUuid)
                .withPartitionCoefficient(0.2f).withCommunity(community).build();
        List<String> stationCodes = List.of("PLANT001");
        List<ProductionByTime> expected = List.of(new ProductionByTime(startDate, 2d));

        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(getPlantRepository.findPlantCodesByCommunity(communityId)).thenReturn(stationCodes);
        when(getProductionRepository.getMonthlyProductionByRangeOfDates(startDate, endDate, 0.2f, stationCodes))
                .thenReturn(expected);

        List<ProductionByTime> result = service.getMonthlyProductionByRangeOfDatesAndCommunityAndSupply(
                startDate, endDate, communityId, supplyId);

        assertSame(expected, result);
        verify(getProductionRepository).getMonthlyProductionByRangeOfDates(startDate, endDate, 0.2f, stationCodes);
    }

    @Test
    void getYearlyProductionByRangeOfDatesAndCommunity_usesCoefficientOneAndCommunityPlantCodes() {
        UUID communityId = UUID.randomUUID();
        List<String> stationCodes = List.of("PLANT001");
        List<ProductionByTime> expected = List.of(new ProductionByTime(startDate, 13d));

        when(getPlantRepository.findPlantCodesByCommunity(communityId)).thenReturn(stationCodes);
        when(getProductionRepository.getYearlyProductionByRangeOfDates(startDate, endDate, 1f, stationCodes))
                .thenReturn(expected);

        List<ProductionByTime> result = service.getYearlyProductionByRangeOfDatesAndCommunity(startDate, endDate, communityId);

        assertSame(expected, result);
        verify(getProductionRepository).getYearlyProductionByRangeOfDates(startDate, endDate, 1f, stationCodes);
    }

    @Test
    void getYearlyProductionByRangeOfDatesAndCommunityAndSupply_appliesSupplyCoefficientAndCommunityPlantCodes() {
        UUID communityId = UUID.randomUUID();
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        Community community = CommunityMother.random().withId(communityId).build();
        Supply supply = SupplyMother.random().withId(supplyUuid)
                .withPartitionCoefficient(0.9f).withCommunity(community).build();
        List<String> stationCodes = List.of("PLANT001");
        List<ProductionByTime> expected = List.of(new ProductionByTime(startDate, 9d));

        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(getPlantRepository.findPlantCodesByCommunity(communityId)).thenReturn(stationCodes);
        when(getProductionRepository.getYearlyProductionByRangeOfDates(startDate, endDate, 0.9f, stationCodes))
                .thenReturn(expected);

        List<ProductionByTime> result = service.getYearlyProductionByRangeOfDatesAndCommunityAndSupply(
                startDate, endDate, communityId, supplyId);

        assertSame(expected, result);
        verify(getProductionRepository).getYearlyProductionByRangeOfDates(startDate, endDate, 0.9f, stationCodes);
    }

    @Test
    void communityAndSupplyVariant_throwsWhenSupplyBelongsToAnotherCommunity() {
        UUID communityId = UUID.randomUUID();
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        Community otherCommunity = CommunityMother.random().withId(UUID.randomUUID()).build();
        Supply supply = SupplyMother.random().withId(supplyUuid).withCommunity(otherCommunity).build();

        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class,
                () -> service.getHourlyProductionByRangeOfDatesAndCommunityAndSupply(startDate, endDate, communityId, supplyId));
    }
}
