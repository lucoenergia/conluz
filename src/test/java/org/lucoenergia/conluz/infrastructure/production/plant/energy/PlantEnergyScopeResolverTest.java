package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.InvalidEnergyMetricsPeriodException;
import org.lucoenergia.conluz.domain.production.datadis.get.DailyProductionAggregate;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
import org.lucoenergia.conluz.domain.production.datadis.get.RecordedProductionPeriod;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyMetricsReferencePeriod;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyScope;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyScopeResolver;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlantEnergyScopeResolverTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    /** 2026-11-10T10:30 in Madrid. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T09:30:00Z"), ZoneOffset.UTC);

    private final GetPlantRepository getPlantRepository = mock(GetPlantRepository.class);
    private final GetDatadisProductionAggregateRepository aggregateRepository =
            mock(GetDatadisProductionAggregateRepository.class);
    private final ZoneResolver zoneResolver = mock(ZoneResolver.class);
    private final TimeConfiguration timeConfiguration = mock(TimeConfiguration.class);

    private final PlantEnergyScopeResolver resolver = new PlantEnergyScopeResolverImpl(getPlantRepository,
            aggregateRepository, zoneResolver, new DateConverter(timeConfiguration), CLOCK);

    private final Community community = CommunityMother.random().build();
    private final Plant plant = PlantMother.random(SupplyMother.random().withCommunity(community).build()).build();

    @BeforeEach
    void givenThePlant() {
        when(getPlantRepository.findById(PlantId.of(plant.getId()))).thenReturn(Optional.of(plant));
        when(zoneResolver.resolveZoneIdForCommunity(community.getId())).thenReturn(ZONE);
        when(timeConfiguration.getZoneId()).thenReturn(ZONE);
    }

    @Test
    @DisplayName("EPR-002 an explicit period is clipped to the start of the current hour in the community's zone")
    void anExplicitPeriodIsClippedToTheCurrentHourInTheCommunityZone() {
        PlantEnergyScope scope = resolve(OffsetDateTime.parse("2026-11-09T00:00:00+01:00"),
                OffsetDateTime.parse("2026-11-11T00:00:00+01:00"), null);

        EnergyMetricsPeriod period = scope.getPeriod().orElseThrow();
        assertEquals(OffsetDateTime.parse("2026-11-09T00:00:00+01:00"), period.getStartDate());
        assertEquals(OffsetDateTime.parse("2026-11-10T10:00:00+01:00"), period.getEndDate());
        assertEquals(ZONE, scope.getZone());
        assertEquals(plant.getSupply().getCode(), scope.getSupplyCode());
        verify(zoneResolver, never()).resolveApplicationZoneId();
        verifyNoInteractions(aggregateRepository);
    }

    @Test
    @DisplayName("PEM-001 AC1 the latest production day is the latest day whose records cover all its hours")
    void theLatestProductionDayIsTheLatestCompleteDay() {
        when(aggregateRepository.aggregateByLocalDay(anyString(), any(), any(), any())).thenReturn(List.of(
                new DailyProductionAggregate(LocalDate.of(2026, 10, 25), 30d, 25),
                new DailyProductionAggregate(LocalDate.of(2026, 11, 7), 20d, 24),
                new DailyProductionAggregate(LocalDate.of(2026, 11, 8), 5d, 12),
                new DailyProductionAggregate(LocalDate.of(2026, 11, 5), 21d, 24)));

        EnergyMetricsPeriod period = resolve(null, null, PlantEnergyMetricsReferencePeriod.LATEST_PRODUCTION_DAY)
                .getPeriod().orElseThrow();

        assertEquals(OffsetDateTime.parse("2026-11-07T00:00:00+01:00"), period.getStartDate());
        assertEquals(OffsetDateTime.parse("2026-11-08T00:00:00+01:00"), period.getEndDate());
    }

    @Test
    @DisplayName("PEM-001 AC3b the search covers the 24 months before the current day and ends at its midnight")
    void theSearchCoversTheTwentyFourMonthsBeforeTheCurrentDay() {
        when(aggregateRepository.aggregateByLocalDay(anyString(), any(), any(), any())).thenReturn(List.of());

        resolve(null, null, PlantEnergyMetricsReferencePeriod.LATEST_PRODUCTION_DAY);

        verify(aggregateRepository).aggregateByLocalDay(plant.getSupply().getCode(),
                Instant.parse("2024-11-09T23:00:00Z"), Instant.parse("2026-11-09T23:00:00Z"), ZONE);
    }

    @Test
    @DisplayName("PEM-001 AC3 a day missing one hour is not complete, a daylight saving day needs 23 or 25")
    void aDayMissingOneHourIsNotComplete() {
        when(aggregateRepository.aggregateByLocalDay(anyString(), any(), any(), any())).thenReturn(List.of(
                new DailyProductionAggregate(LocalDate.of(2026, 3, 29), 10d, 23),
                new DailyProductionAggregate(LocalDate.of(2026, 10, 25), 10d, 24),
                new DailyProductionAggregate(LocalDate.of(2026, 11, 8), 10d, 23)));

        EnergyMetricsPeriod period = resolve(null, null, PlantEnergyMetricsReferencePeriod.LATEST_PRODUCTION_DAY)
                .getPeriod().orElseThrow();

        assertEquals(OffsetDateTime.parse("2026-03-29T00:00:00+01:00"), period.getStartDate());
        assertEquals(OffsetDateTime.parse("2026-03-30T00:00:00+02:00"), period.getEndDate());
    }

    @Test
    @DisplayName("PEM-001 AC2 without a complete day no period is resolved")
    void withoutACompleteDayNoPeriodIsResolved() {
        when(aggregateRepository.aggregateByLocalDay(anyString(), any(), any(), any())).thenReturn(List.of(
                new DailyProductionAggregate(LocalDate.of(2026, 11, 8), 5d, 12),
                new DailyProductionAggregate(LocalDate.of(2026, 11, 9), 6d, 20)));

        assertTrue(resolve(null, null, PlantEnergyMetricsReferencePeriod.LATEST_PRODUCTION_DAY)
                .getPeriod().isEmpty());
    }

    @Test
    @DisplayName("EPR-001 without dates or reference period the period spans the plant's stored records")
    void withoutDatesOrReferencePeriodThePeriodSpansTheStoredRecords() {
        when(aggregateRepository.findRecordedPeriod(plant.getSupply().getCode())).thenReturn(Optional.of(
                new RecordedProductionPeriod(Instant.parse("2026-03-01T08:00:00Z"),
                        Instant.parse("2026-07-20T19:00:00Z"))));

        EnergyMetricsPeriod period = resolve(null, null, null).getPeriod().orElseThrow();

        assertEquals(Instant.parse("2026-03-01T08:00:00Z"), period.getStartDate().toInstant());
        assertEquals(Instant.parse("2026-07-20T20:00:00Z"), period.getEndDate().toInstant());
    }

    @Test
    void withoutStoredRecordsNoPeriodIsResolved() {
        when(aggregateRepository.findRecordedPeriod(anyString())).thenReturn(Optional.empty());

        assertTrue(resolve(null, null, null).getPeriod().isEmpty());
    }

    @Test
    @DisplayName("EPR-001 a reference period together with a date is rejected before anything is read")
    void aReferencePeriodTogetherWithADateIsRejectedBeforeAnythingIsRead() {
        InvalidEnergyMetricsPeriodException exception = assertThrows(InvalidEnergyMetricsPeriodException.class,
                () -> resolve(OffsetDateTime.parse("2026-11-01T00:00:00+01:00"), null,
                        PlantEnergyMetricsReferencePeriod.LATEST_PRODUCTION_DAY));

        assertEquals(InvalidEnergyMetricsPeriodException.Reason.CONFLICTING_PERIOD, exception.getReason());
        verifyNoInteractions(aggregateRepository);
    }

    @Test
    void anUnknownPlantIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(getPlantRepository.findById(PlantId.of(unknown))).thenReturn(Optional.empty());

        assertThrows(PlantNotFoundException.class, () -> resolver.resolve(unknown, null, null, null,
                EnergyMetricsPeriodValidator.MAX_LENGTH));
    }

    private PlantEnergyScope resolve(OffsetDateTime startDate, OffsetDateTime endDate,
                                     PlantEnergyMetricsReferencePeriod referencePeriod) {
        return resolver.resolve(plant.getId(), startDate, endDate, referencePeriod,
                EnergyMetricsPeriodValidator.MAX_LENGTH);
    }
}
