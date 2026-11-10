package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.production.datadis.get.DailyProductionAggregate;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
import org.lucoenergia.conluz.domain.production.plant.energy.GetPlantDailyEnergySeriesService;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyScope;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyScopeResolver;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergySeriesBucket;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetPlantDailyEnergySeriesServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final String SUPPLY_CODE = "ES0031000000000001AA";

    private final PlantEnergyScopeResolver scopeResolver = mock(PlantEnergyScopeResolver.class);
    private final GetDatadisProductionAggregateRepository aggregateRepository =
            mock(GetDatadisProductionAggregateRepository.class);

    private final GetPlantDailyEnergySeriesService service =
            new GetPlantDailyEnergySeriesServiceImpl(scopeResolver, aggregateRepository);

    @Test
    @DisplayName("PEM-003 AC6 AC9 every day is emitted with its possible hours inside the period and its hours with a record")
    void everyDayIsEmittedWithItsCoverage() {
        // From 24 October to 27 October 10:00, the clocks going back on the 25th.
        OffsetDateTime start = OffsetDateTime.parse("2026-10-24T00:00:00+02:00");
        OffsetDateTime end = OffsetDateTime.parse("2026-10-27T10:00:00+01:00");
        when(scopeResolver.resolve(PLANT_ID, start, end, null, EnergyMetricsPeriodValidator.MAX_LENGTH))
                .thenReturn(new PlantEnergyScope(SUPPLY_CODE, ZONE, new EnergyMetricsPeriod(start, end)));
        when(aggregateRepository.aggregateByLocalDay(SUPPLY_CODE, start.toInstant(), end.toInstant(), ZONE))
                .thenReturn(List.of(
                        new DailyProductionAggregate(LocalDate.of(2026, 10, 24), 18.5, 12),
                        new DailyProductionAggregate(LocalDate.of(2026, 10, 25), 30d, 25),
                        new DailyProductionAggregate(LocalDate.of(2026, 10, 27), 0d, 10)));

        List<PlantEnergySeriesBucket> buckets = service.getDailySeries(PLANT_ID, start, end);

        assertEquals(List.of(
                OffsetDateTime.parse("2026-10-24T00:00:00+02:00"),
                OffsetDateTime.parse("2026-10-25T00:00:00+02:00"),
                OffsetDateTime.parse("2026-10-26T00:00:00+01:00"),
                OffsetDateTime.parse("2026-10-27T00:00:00+01:00")),
                buckets.stream().map(PlantEnergySeriesBucket::getStartsAt).toList());
        assertEquals(List.of(24L, 25L, 24L, 10L), buckets.stream().map(PlantEnergySeriesBucket::getExpectedHours).toList());
        assertEquals(List.of(12L, 25L, 0L, 10L), buckets.stream().map(PlantEnergySeriesBucket::getHoursWithData).toList());
        assertEquals(18.5, buckets.get(0).getProductionKWh());
        assertEquals(30d, buckets.get(1).getProductionKWh());
        assertNull(buckets.get(2).getProductionKWh());
        assertEquals(0d, buckets.get(3).getProductionKWh());
    }
}
