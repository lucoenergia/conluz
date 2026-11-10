package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
import org.lucoenergia.conluz.domain.production.plant.energy.GetPlantHourlyEnergySeriesService;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyScope;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyScopeResolver;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergySeriesBucket;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetPlantHourlyEnergySeriesServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final String SUPPLY_CODE = "ES0031000000000001AA";
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-10-25T01:00:00+02:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-10-25T04:00:00+01:00");

    private final PlantEnergyScopeResolver scopeResolver = mock(PlantEnergyScopeResolver.class);
    private final GetDatadisProductionAggregateRepository aggregateRepository =
            mock(GetDatadisProductionAggregateRepository.class);

    private final GetPlantHourlyEnergySeriesService service =
            new GetPlantHourlyEnergySeriesServiceImpl(scopeResolver, aggregateRepository);

    @Test
    @DisplayName("PEM-003 AC7 every hour is emitted: a record as stored, zeros included, and a gap as null")
    void everyHourIsEmittedWithGapsAsNull() {
        when(scopeResolver.resolve(PLANT_ID, START, END, null, EnergyMetricsPeriodValidator.MAX_HOURLY_LENGTH))
                .thenReturn(new PlantEnergyScope(SUPPLY_CODE, ZONE, new EnergyMetricsPeriod(START, END)));
        // From 01:00+02:00 to 04:00+01:00 on the day the clocks go back: four hours, 02:00 twice.
        when(aggregateRepository.findHourlyProduction(SUPPLY_CODE, START.toInstant(), END.toInstant()))
                .thenReturn(Map.of(
                        Instant.parse("2026-10-24T23:00:00Z"), 0d,
                        Instant.parse("2026-10-25T01:00:00Z"), 2.5d,
                        Instant.parse("2026-10-25T02:00:00Z"), 0d));

        List<PlantEnergySeriesBucket> buckets = service.getHourlySeries(PLANT_ID, START, END);

        assertEquals(List.of(
                OffsetDateTime.parse("2026-10-25T01:00:00+02:00"),
                OffsetDateTime.parse("2026-10-25T02:00:00+02:00"),
                OffsetDateTime.parse("2026-10-25T02:00:00+01:00"),
                OffsetDateTime.parse("2026-10-25T03:00:00+01:00")),
                buckets.stream().map(PlantEnergySeriesBucket::getStartsAt).toList());
        assertEquals(0d, buckets.get(0).getProductionKWh());
        assertEquals(1, buckets.get(0).getHoursWithData());
        assertNull(buckets.get(1).getProductionKWh());
        assertEquals(0, buckets.get(1).getHoursWithData());
        assertEquals(2.5d, buckets.get(2).getProductionKWh());
        assertEquals(0d, buckets.get(3).getProductionKWh());
        buckets.forEach(bucket -> assertEquals(1, bucket.getExpectedHours()));
    }
}
