package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.production.datadis.get.DailyProductionAggregate;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
import org.lucoenergia.conluz.domain.production.plant.energy.GetPlantMonthlyEnergySeriesService;
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

class GetPlantMonthlyEnergySeriesServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final String SUPPLY_CODE = "ES0031000000000001AA";

    private final PlantEnergyScopeResolver scopeResolver = mock(PlantEnergyScopeResolver.class);
    private final GetDatadisProductionAggregateRepository aggregateRepository =
            mock(GetDatadisProductionAggregateRepository.class);

    private final GetPlantMonthlyEnergySeriesService service =
            new GetPlantMonthlyEnergySeriesServiceImpl(scopeResolver, aggregateRepository);

    @Test
    @DisplayName("PEM-003 AC8 the days are folded into local months, each with its possible hours and its hours with a record")
    void theDaysAreFoldedIntoLocalMonths() {
        // From mid-September to 10 November 10:00: September is not selected, November is clipped.
        OffsetDateTime start = OffsetDateTime.parse("2026-09-15T00:00:00+02:00");
        OffsetDateTime end = OffsetDateTime.parse("2026-11-10T10:00:00+01:00");
        when(scopeResolver.resolve(PLANT_ID, start, end, null, EnergyMetricsPeriodValidator.MAX_LENGTH))
                .thenReturn(new PlantEnergyScope(SUPPLY_CODE, ZONE, new EnergyMetricsPeriod(start, end)));
        when(aggregateRepository.aggregateByLocalDay(SUPPLY_CODE, start.toInstant(), end.toInstant(), ZONE))
                .thenReturn(List.of(
                        new DailyProductionAggregate(LocalDate.of(2026, 9, 20), 1000d, 24),
                        new DailyProductionAggregate(LocalDate.of(2026, 10, 1), 10d, 24),
                        new DailyProductionAggregate(LocalDate.of(2026, 10, 25), 12.5, 25),
                        new DailyProductionAggregate(LocalDate.of(2026, 10, 31), 0d, 24)));

        List<PlantEnergySeriesBucket> buckets = service.getMonthlySeries(PLANT_ID, start, end);

        assertEquals(List.of(
                OffsetDateTime.parse("2026-10-01T00:00:00+02:00"),
                OffsetDateTime.parse("2026-11-01T00:00:00+01:00")),
                buckets.stream().map(PlantEnergySeriesBucket::getStartsAt).toList());
        assertEquals(22.5, buckets.get(0).getProductionKWh());
        assertEquals(73, buckets.get(0).getHoursWithData());
        assertEquals(745, buckets.get(0).getExpectedHours());
        assertNull(buckets.get(1).getProductionKWh());
        assertEquals(0, buckets.get(1).getHoursWithData());
        assertEquals(9 * 24 + 10, buckets.get(1).getExpectedHours());
    }
}
