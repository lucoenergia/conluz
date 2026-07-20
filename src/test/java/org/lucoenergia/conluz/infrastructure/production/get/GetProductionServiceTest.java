package org.lucoenergia.conluz.infrastructure.production.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientResolver;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientSegment;
import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.domain.production.get.GetProductionRepository;
import org.lucoenergia.conluz.domain.production.get.GetProductionService;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetProductionServiceImpl}. Authorization is enforced at the controller layer
 * (via {@code @PreAuthorize}), so this service performs data access and coefficient-driven scaling
 * only. Per-supply behaviour is exercised here at the mocked-repository level (wiring, boundary
 * attribution, N+1 guards); real end-to-end numeric correctness against a live InfluxDB/Postgres
 * (transition, two-plants, monthly-across-transition, community-total unchanged) is covered by
 * {@link GetProductionServiceImplIntegrationTest}.
 */
class GetProductionServiceTest {

    private final GetProductionRepository getProductionRepository = Mockito.mock(GetProductionRepository.class);
    private final GetSupplyRepository getSupplyRepository = Mockito.mock(GetSupplyRepository.class);
    private final GetPlantRepository getPlantRepository = Mockito.mock(GetPlantRepository.class);
    private final CoefficientResolver coefficientResolver = Mockito.mock(CoefficientResolver.class);
    private final TimeConfiguration timeConfiguration = Mockito.mock(TimeConfiguration.class);

    private final GetProductionService service = new GetProductionServiceImpl(
            getProductionRepository, getSupplyRepository, getPlantRepository, coefficientResolver, timeConfiguration);

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID OTHER_PLANT_ID = UUID.randomUUID();
    private static final String STATION_CODE = "PLANT001";
    private static final String OTHER_STATION_CODE = "PLANT002";

    private final OffsetDateTime startDate = OffsetDateTime.parse("2023-09-01T00:00:00.000+02:00");
    private final OffsetDateTime endDate = OffsetDateTime.parse("2023-09-01T23:00:00.000+02:00");

    private void stubPlant(UUID plantId, String stationCode) {
        Plant plant = new Plant.Builder().withId(plantId).withProviderCode(stationCode).build();
        when(getPlantRepository.findById(PlantId.of(plantId))).thenReturn(Optional.of(plant));
    }

    private static Instant exclusiveTo(OffsetDateTime endDate) {
        return endDate.toInstant().plusNanos(1);
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    // --- Supply-scoped: not found / no coefficient rows ---

    @Test
    void getHourlyProductionByRangeOfDatesAndSupply_throwsWhenSupplyNotFound() {
        SupplyId supplyId = SupplyId.of(UUID.randomUUID());
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class,
                () -> service.getHourlyProductionByRangeOfDatesAndSupply(startDate, endDate, supplyId));
        verifyNoInteractions(coefficientResolver, getProductionRepository);
    }

    @Test
    void getDailyProductionByRangeOfDatesAndSupply_throwsWhenSupplyNotFound() {
        SupplyId supplyId = SupplyId.of(UUID.randomUUID());
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class,
                () -> service.getDailyProductionByRangeOfDatesAndSupply(startDate, endDate, supplyId));
        verifyNoInteractions(coefficientResolver, getProductionRepository);
    }

    @Test
    void getMonthlyProductionByRangeOfDatesAndSupply_throwsWhenSupplyNotFound() {
        SupplyId supplyId = SupplyId.of(UUID.randomUUID());
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class,
                () -> service.getMonthlyProductionByRangeOfDatesAndSupply(startDate, endDate, supplyId));
        verifyNoInteractions(coefficientResolver, getProductionRepository);
    }

    @Test
    void getHourlyProductionByRangeOfDatesAndSupply_noCoefficientRows_returnsEmptyWithoutError() {
        // A supply with zero supply_partition_coefficient rows must yield empty production, not an
        // error and not a fallback to any stale scalar -- this is the pre-flight-gate contract.
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(SupplyMother.random().withId(supplyUuid).build()));
        when(coefficientResolver.resolveSegmentsBySupply(supplyUuid, startDate.toInstant(), exclusiveTo(endDate)))
                .thenReturn(Map.of());

        List<ProductionByTime> result = service.getHourlyProductionByRangeOfDatesAndSupply(startDate, endDate, supplyId);

        assertTrue(result.isEmpty());
        verifyNoInteractions(getProductionRepository);
    }

    // --- Hourly: segment-driven scaling, boundary attribution, N+1 guard ---

    @Test
    void getHourlyProductionByRangeOfDatesAndSupply_appliesSegmentCoefficient() {
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(SupplyMother.random().withId(supplyUuid).build()));
        stubPlant(PLANT_ID, STATION_CODE);

        Instant exclusiveTo = exclusiveTo(endDate);
        CoefficientSegment segment = new CoefficientSegment(startDate.toInstant(), exclusiveTo, BigDecimal.valueOf(0.5));
        when(coefficientResolver.resolveSegmentsBySupply(supplyUuid, startDate.toInstant(), exclusiveTo))
                .thenReturn(Map.of(PLANT_ID, List.of(segment)));
        when(getProductionRepository.getHourlyProductionHalfOpen(startDate, toOffsetDateTime(exclusiveTo), List.of(STATION_CODE)))
                .thenReturn(List.of(new ProductionByTime(startDate, 10d)));

        List<ProductionByTime> result = service.getHourlyProductionByRangeOfDatesAndSupply(startDate, endDate, supplyId);

        assertEquals(1, result.size());
        assertEquals(5d, result.get(0).getPower(), 0.0001d);
    }

    @Test
    void getHourlyProductionByRangeOfDatesAndSupply_transitionAppliesOldBetaBeforeAndNewBetaAfter() {
        // Regression for the bug this phase fixes: a single static beta would scale every point in
        // the range by the same value, which is wrong the moment beta changes mid-range.
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(SupplyMother.random().withId(supplyUuid).build()));
        stubPlant(PLANT_ID, STATION_CODE);

        Instant exclusiveTo = exclusiveTo(endDate);
        Instant mid = startDate.toInstant().plusSeconds(3600 * 12);
        CoefficientSegment before = new CoefficientSegment(startDate.toInstant(), mid, BigDecimal.valueOf(0.3));
        CoefficientSegment after = new CoefficientSegment(mid, exclusiveTo, BigDecimal.valueOf(0.7));
        when(coefficientResolver.resolveSegmentsBySupply(supplyUuid, startDate.toInstant(), exclusiveTo))
                .thenReturn(Map.of(PLANT_ID, List.of(before, after)));

        OffsetDateTime pointBeforeMid = toOffsetDateTime(mid.minusSeconds(3600));
        OffsetDateTime pointAtMid = toOffsetDateTime(mid); // exactly on the transition boundary
        OffsetDateTime pointAfterMid = toOffsetDateTime(mid.plusSeconds(3600));
        when(getProductionRepository.getHourlyProductionHalfOpen(startDate, toOffsetDateTime(exclusiveTo), List.of(STATION_CODE)))
                .thenReturn(List.of(
                        new ProductionByTime(pointBeforeMid, 10d),
                        new ProductionByTime(pointAtMid, 10d),
                        new ProductionByTime(pointAfterMid, 10d)));

        List<ProductionByTime> result = service.getHourlyProductionByRangeOfDatesAndSupply(startDate, endDate, supplyId);

        assertEquals(3, result.size());
        assertEquals(3d, result.get(0).getPower(), 0.0001d); // 10 * 0.3 (before)
        // Half-open [from, to): a point exactly at the transition instant belongs to the segment that
        // STARTS there (0.7), not the one that ends there (0.3).
        assertEquals(7d, result.get(1).getPower(), 0.0001d);
        assertEquals(7d, result.get(2).getPower(), 0.0001d); // 10 * 0.7 (after)
    }

    @Test
    void getHourlyProductionByRangeOfDatesAndSupply_pointAtRequestEndDateIsIncludedNotZeroed() {
        // Outer-boundary regression: the public endDate is inclusive, but segments/fetches are
        // internally half-open. A point exactly at endDate must still be covered.
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(SupplyMother.random().withId(supplyUuid).build()));
        stubPlant(PLANT_ID, STATION_CODE);

        Instant exclusiveTo = exclusiveTo(endDate);
        CoefficientSegment segment = new CoefficientSegment(startDate.toInstant(), exclusiveTo, BigDecimal.valueOf(0.5));
        when(coefficientResolver.resolveSegmentsBySupply(supplyUuid, startDate.toInstant(), exclusiveTo))
                .thenReturn(Map.of(PLANT_ID, List.of(segment)));
        when(getProductionRepository.getHourlyProductionHalfOpen(startDate, toOffsetDateTime(exclusiveTo), List.of(STATION_CODE)))
                .thenReturn(List.of(new ProductionByTime(endDate, 8d)));

        List<ProductionByTime> result = service.getHourlyProductionByRangeOfDatesAndSupply(startDate, endDate, supplyId);

        assertEquals(1, result.size());
        assertEquals(4d, result.get(0).getPower(), 0.0001d); // 8 * 0.5, not zeroed
    }

    @Test
    void getHourlyProductionByRangeOfDatesAndSupply_twoPlantsMergeAtSameTimestamp() {
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(SupplyMother.random().withId(supplyUuid).build()));
        stubPlant(PLANT_ID, STATION_CODE);
        stubPlant(OTHER_PLANT_ID, OTHER_STATION_CODE);

        Instant exclusiveTo = exclusiveTo(endDate);
        CoefficientSegment segment1 = new CoefficientSegment(startDate.toInstant(), exclusiveTo, BigDecimal.valueOf(0.4));
        CoefficientSegment segment2 = new CoefficientSegment(startDate.toInstant(), exclusiveTo, BigDecimal.valueOf(0.6));
        when(coefficientResolver.resolveSegmentsBySupply(supplyUuid, startDate.toInstant(), exclusiveTo))
                .thenReturn(Map.of(PLANT_ID, List.of(segment1), OTHER_PLANT_ID, List.of(segment2)));
        when(getProductionRepository.getHourlyProductionHalfOpen(startDate, toOffsetDateTime(exclusiveTo), List.of(STATION_CODE)))
                .thenReturn(List.of(new ProductionByTime(startDate, 10d)));
        when(getProductionRepository.getHourlyProductionHalfOpen(startDate, toOffsetDateTime(exclusiveTo), List.of(OTHER_STATION_CODE)))
                .thenReturn(List.of(new ProductionByTime(startDate, 20d)));

        List<ProductionByTime> result = service.getHourlyProductionByRangeOfDatesAndSupply(startDate, endDate, supplyId);

        assertEquals(1, result.size());
        assertEquals(16d, result.get(0).getPower(), 0.0001d); // 10*0.4 + 20*0.6
    }

    @Test
    void getHourlyProductionByRangeOfDatesAndSupply_callsRepositoryAndResolverOnceEachPerPlant() {
        // N+1 guard: resolving segments and fetching raw points must each happen once per plant for
        // the whole range, never once per point.
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(SupplyMother.random().withId(supplyUuid).build()));
        stubPlant(PLANT_ID, STATION_CODE);

        Instant exclusiveTo = exclusiveTo(endDate);
        CoefficientSegment segment = new CoefficientSegment(startDate.toInstant(), exclusiveTo, BigDecimal.valueOf(1.0));
        when(coefficientResolver.resolveSegmentsBySupply(supplyUuid, startDate.toInstant(), exclusiveTo))
                .thenReturn(Map.of(PLANT_ID, List.of(segment)));
        when(getProductionRepository.getHourlyProductionHalfOpen(startDate, toOffsetDateTime(exclusiveTo), List.of(STATION_CODE)))
                .thenReturn(List.of(
                        new ProductionByTime(startDate, 1d),
                        new ProductionByTime(startDate.plusHours(1), 2d),
                        new ProductionByTime(startDate.plusHours(2), 3d)));

        service.getHourlyProductionByRangeOfDatesAndSupply(startDate, endDate, supplyId);

        verify(coefficientResolver, times(1)).resolveSegmentsBySupply(supplyUuid, startDate.toInstant(), exclusiveTo);
        verify(getProductionRepository, times(1))
                .getHourlyProductionHalfOpen(startDate, toOffsetDateTime(exclusiveTo), List.of(STATION_CODE));
    }

    // --- Daily: one query per segment, not per output bucket ---

    @Test
    void getDailyProductionByRangeOfDatesAndSupply_oneQueryPerSegmentMergesPartialDay() {
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(SupplyMother.random().withId(supplyUuid).build()));
        stubPlant(PLANT_ID, STATION_CODE);

        Instant exclusiveTo = exclusiveTo(endDate);
        Instant midDay = startDate.toInstant().plusSeconds(3600 * 12);
        CoefficientSegment before = new CoefficientSegment(startDate.toInstant(), midDay, BigDecimal.valueOf(0.3));
        CoefficientSegment after = new CoefficientSegment(midDay, exclusiveTo, BigDecimal.valueOf(0.7));
        when(coefficientResolver.resolveSegmentsBySupply(supplyUuid, startDate.toInstant(), exclusiveTo))
                .thenReturn(Map.of(PLANT_ID, List.of(before, after)));

        OffsetDateTime dayBucket = toOffsetDateTime(startDate.toInstant());
        // Same calendar day split across the two segment queries -- must merge into ONE bucket. Every
        // segment boundary is passed to the repository via toOffsetDateTime(Instant) (always
        // UTC-offset), including the first one -- so the stub/verify args below must match that, not
        // the original startDate parameter's own (differently-offset) representation of the same instant.
        OffsetDateTime rangeStart = toOffsetDateTime(startDate.toInstant());
        when(getProductionRepository.getDailyProductionHalfOpen(rangeStart, toOffsetDateTime(midDay), List.of(STATION_CODE)))
                .thenReturn(List.of(new ProductionByTime(dayBucket, 100d)));
        when(getProductionRepository.getDailyProductionHalfOpen(toOffsetDateTime(midDay), toOffsetDateTime(exclusiveTo), List.of(STATION_CODE)))
                .thenReturn(List.of(new ProductionByTime(dayBucket, 200d)));

        List<ProductionByTime> result = service.getDailyProductionByRangeOfDatesAndSupply(startDate, endDate, supplyId);

        assertEquals(1, result.size());
        assertEquals(170d, result.get(0).getPower(), 0.0001d); // 100*0.3 + 200*0.7

        verify(getProductionRepository, times(1))
                .getDailyProductionHalfOpen(rangeStart, toOffsetDateTime(midDay), List.of(STATION_CODE));
        verify(getProductionRepository, times(1))
                .getDailyProductionHalfOpen(toOffsetDateTime(midDay), toOffsetDateTime(exclusiveTo), List.of(STATION_CODE));
    }

    // --- Monthly: Madrid-aligned days folded into YearMonth, one query per segment ---

    @Test
    void getMonthlyProductionByRangeOfDatesAndSupply_foldsMadridDaysIntoYearMonth() {
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(SupplyMother.random().withId(supplyUuid).build()));
        stubPlant(PLANT_ID, STATION_CODE);
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("Europe/Madrid"));

        Instant exclusiveTo = exclusiveTo(endDate);
        CoefficientSegment segment = new CoefficientSegment(startDate.toInstant(), exclusiveTo, BigDecimal.valueOf(0.5));
        when(coefficientResolver.resolveSegmentsBySupply(supplyUuid, startDate.toInstant(), exclusiveTo))
                .thenReturn(Map.of(PLANT_ID, List.of(segment)));

        OffsetDateTime day1 = OffsetDateTime.parse("2023-09-01T00:00:00+02:00");
        OffsetDateTime day2 = OffsetDateTime.parse("2023-09-02T00:00:00+02:00");
        when(getProductionRepository.getMadridAlignedDailyProductionHalfOpen(
                toOffsetDateTime(startDate.toInstant()), toOffsetDateTime(exclusiveTo), List.of(STATION_CODE)))
                .thenReturn(List.of(new ProductionByTime(day1, 100d), new ProductionByTime(day2, 50d)));

        List<ProductionByTime> result = service.getMonthlyProductionByRangeOfDatesAndSupply(startDate, endDate, supplyId);

        assertEquals(1, result.size());
        assertEquals(75d, result.get(0).getPower(), 0.0001d); // (100 + 50) * 0.5, folded into one month
    }

    @Test
    void getMonthlyProductionByRangeOfDatesAndSupply_transitionMidMonthMergesPartialMonth() {
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(SupplyMother.random().withId(supplyUuid).build()));
        stubPlant(PLANT_ID, STATION_CODE);
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("Europe/Madrid"));

        Instant exclusiveTo = exclusiveTo(endDate);
        // Transition lands mid-day (not on a Madrid day boundary) -- exercises the partial-bucket
        // merge: two segment-scoped queries can return the SAME Madrid day with complementary sums.
        Instant midDay = startDate.toInstant().plusSeconds(3600 * 12);
        CoefficientSegment before = new CoefficientSegment(startDate.toInstant(), midDay, BigDecimal.valueOf(0.3));
        CoefficientSegment after = new CoefficientSegment(midDay, exclusiveTo, BigDecimal.valueOf(0.7));
        when(coefficientResolver.resolveSegmentsBySupply(supplyUuid, startDate.toInstant(), exclusiveTo))
                .thenReturn(Map.of(PLANT_ID, List.of(before, after)));

        OffsetDateTime sameMadridDay = OffsetDateTime.parse("2023-09-01T00:00:00+02:00");
        when(getProductionRepository.getMadridAlignedDailyProductionHalfOpen(
                toOffsetDateTime(startDate.toInstant()), toOffsetDateTime(midDay), List.of(STATION_CODE)))
                .thenReturn(List.of(new ProductionByTime(sameMadridDay, 100d)));
        when(getProductionRepository.getMadridAlignedDailyProductionHalfOpen(
                toOffsetDateTime(midDay), toOffsetDateTime(exclusiveTo), List.of(STATION_CODE)))
                .thenReturn(List.of(new ProductionByTime(sameMadridDay, 200d)));

        List<ProductionByTime> result = service.getMonthlyProductionByRangeOfDatesAndSupply(startDate, endDate, supplyId);

        assertEquals(1, result.size());
        assertEquals(170d, result.get(0).getPower(), 0.0001d); // 100*0.3 + 200*0.7, same month bucket
    }

    // --- Community-scoped variants (unchanged: raw, no coefficient) ---

    @Test
    void getInstantProductionByCommunity_queriesCommunityPlantCodes() {
        UUID communityId = UUID.randomUUID();
        List<String> stationCodes = List.of("PLANT001", "PLANT002");
        InstantProduction expected = new InstantProduction(42d);

        when(getPlantRepository.findPlantProviderCodesByCommunity(communityId)).thenReturn(stationCodes);
        when(getProductionRepository.getInstantProduction(stationCodes)).thenReturn(expected);

        InstantProduction result = service.getInstantProductionByCommunity(communityId);

        assertSame(expected, result);
        verify(getProductionRepository).getInstantProduction(stationCodes);
    }

    @Test
    void getHourlyProductionByRangeOfDatesAndCommunity_queriesCommunityPlantCodesRaw() {
        UUID communityId = UUID.randomUUID();
        List<String> stationCodes = List.of("PLANT001");
        List<ProductionByTime> expected = List.of(new ProductionByTime(startDate, 10d));

        when(getPlantRepository.findPlantProviderCodesByCommunity(communityId)).thenReturn(stationCodes);
        when(getProductionRepository.getHourlyProductionByRangeOfDates(startDate, endDate, stationCodes))
                .thenReturn(expected);

        List<ProductionByTime> result = service.getHourlyProductionByRangeOfDatesAndCommunity(startDate, endDate, communityId);

        assertSame(expected, result);
        verify(getProductionRepository).getHourlyProductionByRangeOfDates(startDate, endDate, stationCodes);
        verifyNoInteractions(coefficientResolver);
    }

    @Test
    void getDailyProductionByRangeOfDatesAndCommunity_queriesCommunityPlantCodesRaw() {
        UUID communityId = UUID.randomUUID();
        List<String> stationCodes = List.of("PLANT001");
        List<ProductionByTime> expected = List.of(new ProductionByTime(startDate, 11d));

        when(getPlantRepository.findPlantProviderCodesByCommunity(communityId)).thenReturn(stationCodes);
        when(getProductionRepository.getDailyProductionByRangeOfDates(startDate, endDate, stationCodes))
                .thenReturn(expected);

        List<ProductionByTime> result = service.getDailyProductionByRangeOfDatesAndCommunity(startDate, endDate, communityId);

        assertSame(expected, result);
        verify(getProductionRepository).getDailyProductionByRangeOfDates(startDate, endDate, stationCodes);
        verifyNoInteractions(coefficientResolver);
    }

    @Test
    void getMonthlyProductionByRangeOfDatesAndCommunity_queriesCommunityPlantCodesRaw() {
        UUID communityId = UUID.randomUUID();
        List<String> stationCodes = List.of("PLANT001");
        List<ProductionByTime> expected = List.of(new ProductionByTime(startDate, 12d));

        when(getPlantRepository.findPlantProviderCodesByCommunity(communityId)).thenReturn(stationCodes);
        when(getProductionRepository.getMonthlyProductionByRangeOfDates(startDate, endDate, stationCodes))
                .thenReturn(expected);

        List<ProductionByTime> result = service.getMonthlyProductionByRangeOfDatesAndCommunity(startDate, endDate, communityId);

        assertSame(expected, result);
        verify(getProductionRepository).getMonthlyProductionByRangeOfDates(startDate, endDate, stationCodes);
        verifyNoInteractions(coefficientResolver);
    }

    @Test
    void getYearlyProductionByRangeOfDatesAndCommunity_queriesCommunityPlantCodesRaw() {
        UUID communityId = UUID.randomUUID();
        List<String> stationCodes = List.of("PLANT001");
        List<ProductionByTime> expected = List.of(new ProductionByTime(startDate, 13d));

        when(getPlantRepository.findPlantProviderCodesByCommunity(communityId)).thenReturn(stationCodes);
        when(getProductionRepository.getYearlyProductionByRangeOfDates(startDate, endDate, stationCodes))
                .thenReturn(expected);

        List<ProductionByTime> result = service.getYearlyProductionByRangeOfDatesAndCommunity(startDate, endDate, communityId);

        assertSame(expected, result);
        verify(getProductionRepository).getYearlyProductionByRangeOfDates(startDate, endDate, stationCodes);
        verifyNoInteractions(coefficientResolver);
    }

    // --- Community + specific supply (resolver-driven) ---

    @Test
    void getInstantProductionByCommunityAndSupply_appliesResolverCoefficients() {
        UUID communityId = UUID.randomUUID();
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        Community community = CommunityMother.random().withId(communityId).build();
        Supply supply = SupplyMother.random().withId(supplyUuid).withCommunity(community).build();
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(timeConfiguration.now()).thenReturn(OffsetDateTime.parse("2025-01-01T00:00:00Z"));
        stubPlant(PLANT_ID, STATION_CODE);
        when(coefficientResolver.resolveCoefficientsBySupplyAtInstant(supplyUuid, Instant.parse("2025-01-01T00:00:00Z")))
                .thenReturn(Map.of(PLANT_ID, BigDecimal.valueOf(0.5)));
        when(getProductionRepository.getInstantProduction(List.of(STATION_CODE))).thenReturn(new InstantProduction(80d));

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
    void getHourlyProductionByRangeOfDatesAndCommunityAndSupply_delegatesToResolverDrivenPath() {
        UUID communityId = UUID.randomUUID();
        UUID supplyUuid = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyUuid);
        Community community = CommunityMother.random().withId(communityId).build();
        Supply supply = SupplyMother.random().withId(supplyUuid).withCommunity(community).build();
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));
        stubPlant(PLANT_ID, STATION_CODE);

        Instant exclusiveTo = exclusiveTo(endDate);
        CoefficientSegment segment = new CoefficientSegment(startDate.toInstant(), exclusiveTo, BigDecimal.valueOf(0.3));
        when(coefficientResolver.resolveSegmentsBySupply(supplyUuid, startDate.toInstant(), exclusiveTo))
                .thenReturn(Map.of(PLANT_ID, List.of(segment)));
        when(getProductionRepository.getHourlyProductionHalfOpen(startDate, toOffsetDateTime(exclusiveTo), List.of(STATION_CODE)))
                .thenReturn(List.of(new ProductionByTime(startDate, 10d)));

        List<ProductionByTime> result = service.getHourlyProductionByRangeOfDatesAndCommunityAndSupply(
                startDate, endDate, communityId, supplyId);

        assertEquals(1, result.size());
        assertEquals(3d, result.get(0).getPower(), 0.0001d);
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
