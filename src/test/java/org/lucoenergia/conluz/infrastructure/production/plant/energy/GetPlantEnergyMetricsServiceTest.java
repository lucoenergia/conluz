package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
import org.lucoenergia.conluz.domain.production.datadis.get.ProductionAggregate;
import org.lucoenergia.conluz.domain.production.plant.energy.GetPlantEnergyMetricsService;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyMetrics;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyMetricsReferencePeriod;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyScope;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyScopeResolver;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GetPlantEnergyMetricsServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final String SUPPLY_CODE = "ES0031000000000001AA";
    private static final EnergyMetricsPeriod DAY = EnergyMetricsPeriod.ofDay(LocalDate.of(2026, 11, 7), ZONE);

    private final PlantEnergyScopeResolver scopeResolver = mock(PlantEnergyScopeResolver.class);
    private final GetDatadisProductionAggregateRepository aggregateRepository =
            mock(GetDatadisProductionAggregateRepository.class);

    private final GetPlantEnergyMetricsService service =
            new GetPlantEnergyMetricsServiceImpl(scopeResolver, aggregateRepository);

    @Test
    @DisplayName("PEM-002 the production is the total of the backing supply's records over the resolved period")
    void theProductionIsTheTotalOverTheResolvedPeriod() {
        givenTheScope(DAY);
        when(aggregateRepository.aggregate(SUPPLY_CODE, Instant.parse("2026-11-06T23:00:00Z"),
                Instant.parse("2026-11-07T23:00:00Z"))).thenReturn(Optional.of(new ProductionAggregate(41.5, 24)));

        PlantEnergyMetrics metrics = getMetrics();

        assertEquals(DAY, metrics.getPeriod().orElseThrow());
        assertEquals(41.5, metrics.getProductionKWh());
        assertEquals(24, metrics.getHoursWithData());
        assertEquals(24, metrics.getExpectedHours());
    }

    @Test
    @DisplayName("PEM-002 AC5 a period without records has null production, not 0")
    void aPeriodWithoutRecordsHasNullProduction() {
        givenTheScope(DAY);
        when(aggregateRepository.aggregate(SUPPLY_CODE, DAY.getStartDate().toInstant(),
                DAY.getEndDate().toInstant())).thenReturn(Optional.empty());

        PlantEnergyMetrics metrics = getMetrics();

        assertNull(metrics.getProductionKWh());
        assertEquals(0, metrics.getHoursWithData());
        assertEquals(24, metrics.getExpectedHours());
    }

    @Test
    @DisplayName("PEM-001 AC2 without a period nothing more is read and every figure is empty")
    void withoutAPeriodNothingMoreIsRead() {
        givenTheScope(null);

        PlantEnergyMetrics metrics = getMetrics();

        assertTrue(metrics.getPeriod().isEmpty());
        assertNull(metrics.getProductionKWh());
        assertEquals(0, metrics.getHoursWithData());
        assertEquals(0, metrics.getExpectedHours());
        verifyNoInteractions(aggregateRepository);
    }

    private void givenTheScope(EnergyMetricsPeriod period) {
        when(scopeResolver.resolve(PLANT_ID, null, null, PlantEnergyMetricsReferencePeriod.LATEST_PRODUCTION_DAY,
                EnergyMetricsPeriodValidator.MAX_LENGTH)).thenReturn(new PlantEnergyScope(SUPPLY_CODE, ZONE, period));
    }

    private PlantEnergyMetrics getMetrics() {
        return service.getEnergyMetrics(PLANT_ID, null, null, PlantEnergyMetricsReferencePeriod.LATEST_PRODUCTION_DAY);
    }
}
