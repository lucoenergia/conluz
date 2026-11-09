package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyMetrics;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScope;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScopeResolver;
import org.lucoenergia.conluz.domain.admin.community.energy.GetCommunityEnergyMetricsService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsReferencePeriod;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DatadisConsumptionAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.price.get.GetPriceRepository;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers what the service decides: which figures are summed, which supplies count as with data, and
 * which production belongs to which plant. The repositories are stubbed.
 */
class GetCommunityEnergyMetricsServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    /** September 2026: 720 hours. */
    private static final EnergyMetricsPeriod SEPTEMBER = new EnergyMetricsPeriod(
            OffsetDateTime.parse("2026-09-01T00:00:00+02:00"), OffsetDateTime.parse("2026-10-01T00:00:00+02:00"));
    private static final Instant FROM = SEPTEMBER.getStartDate().toInstant();
    private static final Instant TO = SEPTEMBER.getEndDate().toInstant();

    private final CommunityEnergyScopeResolver scopeResolver = mock(CommunityEnergyScopeResolver.class);
    private final GetPlantRepository getPlantRepository = mock(GetPlantRepository.class);
    private final GetDatadisConsumptionAggregateRepository consumptionRepository =
            mock(GetDatadisConsumptionAggregateRepository.class);
    private final GetDatadisProductionAggregateRepository productionRepository =
            mock(GetDatadisProductionAggregateRepository.class);
    private final GetPriceRepository priceRepository = mock(GetPriceRepository.class);

    private final GetCommunityEnergyMetricsService service = new GetCommunityEnergyMetricsServiceImpl(scopeResolver,
            getPlantRepository, consumptionRepository, productionRepository, priceRepository);

    private final Supply first = SupplyMother.random().withEnabled(true).build();
    private final Supply second = SupplyMother.random().withEnabled(true).build();
    private final Supply third = SupplyMother.random().withEnabled(true).build();
    private final Plant plant = PlantMother.random(first).withTotalPower(100.0).build();

    @Test
    @DisplayName("CEM-003 self-consumption and surplus are summed over every supply in scope")
    void selfConsumptionAndSurplusAreSummedOverEverySupplyInScope() {
        givenScope(SEPTEMBER, first, second, third);
        givenAggregate(first, 10, 1);
        givenAggregate(second, 20, 2);
        givenAggregate(third, 30, 3);

        CommunityEnergyMetrics metrics = getSeptember();

        assertEquals(60d, metrics.getSelfConsumptionKWh(), 1e-12);
        assertEquals(6d, metrics.getSurplusKWh(), 1e-12);
        assertEquals(3, metrics.getSupplyCount());
    }

    @Test
    @DisplayName("CEM-004 AC12 a supply has data with 648 of 720 hours published, not with 647")
    void aSupplyHasDataWithNinetyPercentOfThePossibleHours() {
        givenScope(SEPTEMBER, first, second, third);
        givenAggregate(first, 1, 0);
        givenAggregate(second, 1, 0);
        givenAggregate(third, 1, 0);
        when(consumptionRepository.countPublishedHours(first, FROM, TO)).thenReturn(648L);
        when(consumptionRepository.countPublishedHours(second, FROM, TO)).thenReturn(647L);
        when(consumptionRepository.countPublishedHours(third, FROM, TO)).thenReturn(720L);

        assertEquals(2, getSeptember().getSuppliesWithData());
    }

    @Test
    @DisplayName("CEM-003 self-consumption and surplus are null when no supply stored a record")
    void theFiguresAreNullWhenNoSupplyStoredARecord() {
        givenScope(SEPTEMBER, first, second);
        when(consumptionRepository.aggregateByRangeOfDates(any(), any(), any()))
                .thenReturn(DatadisConsumptionAggregate.empty());

        CommunityEnergyMetrics metrics = getSeptember();

        assertNull(metrics.getSelfConsumptionKWh());
        assertNull(metrics.getSurplusKWh());
        assertEquals(0, metrics.getSuppliesWithData());
    }

    @Test
    @DisplayName("CEM-005 the production of each plant is read through its own supply's code")
    void theProductionOfEachPlantIsReadThroughItsOwnSupplysCode() {
        givenScope(SEPTEMBER, first);
        givenAggregate(first, 1, 0);
        when(getPlantRepository.findAllByCommunityId(COMMUNITY_ID)).thenReturn(List.of(plant));
        when(productionRepository.sumProductionByCups(List.of(first.getCode()), FROM, TO))
                .thenReturn(Map.of(first.getCode(), 42d));

        CommunityEnergyMetrics metrics = getSeptember();

        assertEquals(42d, metrics.getProductionKWh(), 1e-12);
        assertEquals(100d, metrics.getInstalledPowerKWp(), 1e-12);
    }

    @Test
    @DisplayName("CEM-001 AC2 without a period nothing is read from the store")
    void withoutAPeriodNothingIsReadFromTheStore() {
        when(scopeResolver.resolve(eq(COMMUNITY_ID), any(), any(), any(), any()))
                .thenReturn(new CommunityEnergyScope(List.of(first, second), ZONE, null));
        when(getPlantRepository.findAllByCommunityId(COMMUNITY_ID)).thenReturn(List.of(plant));

        CommunityEnergyMetrics metrics = service.getEnergyMetrics(COMMUNITY_ID, null, null,
                EnergyMetricsReferencePeriod.LATEST_PUBLISHED_MONTH);

        assertNull(metrics.getStartDate());
        assertEquals(2, metrics.getSupplyCount());
        assertEquals(1, metrics.getPlants().size());
        verifyNoInteractions(consumptionRepository, productionRepository, priceRepository);
    }

    @Test
    @DisplayName("CEM-008 CEM-010 the hourly surplus is read over the supplies in scope and the period, and priced")
    void theHourlySurplusIsReadOverTheSuppliesInScopeAndPriced() {
        givenScope(SEPTEMBER, first, second, third);
        givenAggregate(first, 1, 2);
        givenAggregate(second, 1, 3);
        givenAggregate(third, 1, 0);
        when(consumptionRepository.sumSurplusByHour(List.of(first, second, third), FROM, TO, ZONE))
                .thenReturn(Map.of(FROM, 5d, FROM.plusSeconds(3600), 1.5));
        when(priceRepository.meanPriceByHour(FROM, TO, ZONE)).thenReturn(Map.of(FROM, 0.07));

        CommunityEnergyMetrics metrics = getSeptember();

        assertEquals(0, new BigDecimal("0.35").compareTo(metrics.getSurplusIncomeEur()),
                () -> "was " + metrics.getSurplusIncomeEur());
        assertEquals(1.5, metrics.getUnpricedSurplusKWh(), 1e-12);
    }

    /**
     * From 10:30 to 14:30 in Madrid: the prices are read from 10:00 to 15:00, so the hours the bounds
     * cut through are still priced at the mean of all four of their prices.
     */
    @Test
    @DisplayName("CEM-008 the prices are read over whole hours when the period's bounds cut through one")
    void thePricesAreReadOverWholeHours() {
        EnergyMetricsPeriod period = new EnergyMetricsPeriod(OffsetDateTime.parse("2026-09-01T10:30:00+02:00"),
                OffsetDateTime.parse("2026-09-01T14:30:00+02:00"));
        givenScope(period, first, second);
        when(consumptionRepository.aggregateByRangeOfDates(any(), any(), any()))
                .thenReturn(new DatadisConsumptionAggregate(1d, 1d, 1d, 1L));

        service.getEnergyMetrics(COMMUNITY_ID, period.getStartDate(), period.getEndDate(), null);

        verify(consumptionRepository).sumSurplusByHour(List.of(first, second), period.getStartDate().toInstant(),
                period.getEndDate().toInstant(), ZONE);
        verify(priceRepository).meanPriceByHour(Instant.parse("2026-09-01T08:00:00Z"),
                Instant.parse("2026-09-01T13:00:00Z"), ZONE);
    }

    @Test
    @DisplayName("CEM-010 AC6 no record means no surplus or price query and a null income")
    void noRecordMeansNoSurplusOrPriceQueryAndANullIncome() {
        givenScope(SEPTEMBER, first, second);
        when(consumptionRepository.aggregateByRangeOfDates(any(), any(), any()))
                .thenReturn(DatadisConsumptionAggregate.empty());

        CommunityEnergyMetrics metrics = getSeptember();

        assertNull(metrics.getSurplusIncomeEur());
        assertNull(metrics.getUnpricedSurplusKWh());
        verify(consumptionRepository, never()).sumSurplusByHour(any(), any(), any(), any());
        verifyNoInteractions(priceRepository);
    }

    private CommunityEnergyMetrics getSeptember() {
        return service.getEnergyMetrics(COMMUNITY_ID, SEPTEMBER.getStartDate(), SEPTEMBER.getEndDate(), null);
    }

    private void givenScope(EnergyMetricsPeriod period, Supply... supplies) {
        when(scopeResolver.resolve(COMMUNITY_ID, period.getStartDate(), period.getEndDate(), null,
                EnergyMetricsPeriodValidator.MAX_LENGTH))
                .thenReturn(new CommunityEnergyScope(List.of(supplies), ZONE, period));
    }

    private void givenAggregate(Supply supply, double selfConsumptionKWh, double surplusKWh) {
        when(consumptionRepository.aggregateByRangeOfDates(supply, SEPTEMBER.getStartDate(), SEPTEMBER.getEndDate()))
                .thenReturn(new DatadisConsumptionAggregate(5d, selfConsumptionKWh, surplusKWh, 1L));
    }
}
