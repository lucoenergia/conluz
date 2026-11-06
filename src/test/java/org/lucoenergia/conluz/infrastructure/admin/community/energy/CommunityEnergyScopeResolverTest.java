package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScope;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScopeResolver;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsReferencePeriod;
import org.lucoenergia.conluz.domain.consumption.InvalidEnergyMetricsPeriodException;
import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.domain.consumption.ReferenceMonthResolver;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CommunityEnergyScopeResolverTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    /** 2026-10-15T12:00 in Madrid. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC);
    private static final UUID COMMUNITY_ID = UUID.randomUUID();

    private final GetSupplyRepository getSupplyRepository = mock(GetSupplyRepository.class);
    private final GetDatadisConsumptionAggregateRepository aggregateRepository =
            mock(GetDatadisConsumptionAggregateRepository.class);
    private final ReferenceMonthResolver referenceMonthResolver = mock(ReferenceMonthResolver.class);
    private final ZoneResolver zoneResolver = mock(ZoneResolver.class);
    private final TimeConfiguration timeConfiguration = mock(TimeConfiguration.class);

    private final CommunityEnergyScopeResolver resolver = new CommunityEnergyScopeResolverImpl(getSupplyRepository,
            aggregateRepository, referenceMonthResolver, zoneResolver, new DateConverter(timeConfiguration), CLOCK);

    private final Supply enabledOfActiveMember = SupplyMother.random().withEnabled(true).build();
    private final Supply enabledOfDisabledMember = SupplyMother.random().withEnabled(true).build();
    private final Supply disabled = SupplyMother.random().withEnabled(false).build();

    @BeforeEach
    void givenTheCommunitySupplies() {
        when(zoneResolver.resolveZoneIdForCommunity(COMMUNITY_ID)).thenReturn(ZONE);
        when(timeConfiguration.getZoneId()).thenReturn(ZONE);
        when(getSupplyRepository.findAllByCommunityId(COMMUNITY_ID))
                .thenReturn(List.of(enabledOfActiveMember, disabled, enabledOfDisabledMember));
    }

    @Test
    @DisplayName("CEM-002 the supplies in scope are every enabled supply of the community")
    void theSuppliesInScopeAreEveryEnabledSupplyOfTheCommunity() {
        CommunityEnergyScope scope = resolve(OffsetDateTime.parse("2026-05-10T00:00:00+02:00"),
                OffsetDateTime.parse("2026-05-11T00:00:00+02:00"), null);

        assertEquals(List.of(enabledOfActiveMember, enabledOfDisabledMember), scope.getSupplies());
        assertEquals(ZONE, scope.getZone());
    }

    @Test
    @DisplayName("CEM-001 the latest published month is resolved over the supplies in scope")
    void theLatestPublishedMonthIsResolvedOverTheSuppliesInScope() {
        when(referenceMonthResolver.resolveLatestPublishedMonth(List.of(enabledOfActiveMember,
                enabledOfDisabledMember), ZONE)).thenReturn(Optional.of(YearMonth.of(2026, 9)));

        CommunityEnergyScope scope = resolve(null, null, EnergyMetricsReferencePeriod.LATEST_PUBLISHED_MONTH);

        EnergyMetricsPeriod period = scope.getPeriod().orElseThrow();
        assertEquals(OffsetDateTime.parse("2026-09-01T00:00:00+02:00"), period.getStartDate());
        assertEquals(OffsetDateTime.parse("2026-10-01T00:00:00+02:00"), period.getEndDate());
    }

    @Test
    @DisplayName("CEM-001 AC2 no published month resolves no period")
    void noPublishedMonthResolvesNoPeriod() {
        when(referenceMonthResolver.resolveLatestPublishedMonth(any(), any())).thenReturn(Optional.empty());

        CommunityEnergyScope scope = resolve(null, null, EnergyMetricsReferencePeriod.LATEST_PUBLISHED_MONTH);

        assertTrue(scope.getPeriod().isEmpty());
    }

    @Test
    @DisplayName("EPR-002 an end after the start of the current hour is clipped to it")
    void anEndAfterTheCurrentHourIsClippedToIt() {
        CommunityEnergyScope scope = resolve(OffsetDateTime.parse("2026-10-01T00:00:00+02:00"),
                OffsetDateTime.parse("2026-11-01T00:00:00+01:00"), null);

        assertEquals(OffsetDateTime.parse("2026-10-15T12:00:00+02:00"),
                scope.getPeriod().orElseThrow().getEndDate());
    }

    @Test
    @DisplayName("EPR-003 the period is validated before any supply is read")
    void thePeriodIsValidatedBeforeAnySupplyIsRead() {
        assertThrows(InvalidEnergyMetricsPeriodException.class, () -> resolve(
                OffsetDateTime.parse("2026-10-15T12:00:00+02:00"), OffsetDateTime.parse("2026-10-16T00:00:00+02:00"),
                null));

        verifyNoInteractions(getSupplyRepository);
    }

    @Test
    void withNeitherDatesNorAReferencePeriodThePeriodSpansTheStoredRecords() {
        when(aggregateRepository.findRecordedPeriod(enabledOfActiveMember)).thenReturn(Optional.of(
                new RecordedConsumptionPeriod(Instant.parse("2026-05-01T22:00:00Z"),
                        Instant.parse("2026-05-03T10:00:00Z"))));
        when(aggregateRepository.findRecordedPeriod(enabledOfDisabledMember)).thenReturn(Optional.of(
                new RecordedConsumptionPeriod(Instant.parse("2026-04-30T22:00:00Z"),
                        Instant.parse("2026-05-02T10:00:00Z"))));

        EnergyMetricsPeriod period = resolve(null, null, null).getPeriod().orElseThrow();

        assertEquals(Instant.parse("2026-04-30T22:00:00Z"), period.getStartDate().toInstant());
        assertEquals(Instant.parse("2026-05-03T11:00:00Z"), period.getEndDate().toInstant());
        verify(aggregateRepository, org.mockito.Mockito.never()).findRecordedPeriod(disabled);
    }

    private CommunityEnergyScope resolve(OffsetDateTime startDate, OffsetDateTime endDate,
                                         EnergyMetricsReferencePeriod referencePeriod) {
        return resolver.resolve(COMMUNITY_ID, startDate, endDate, referencePeriod,
                EnergyMetricsPeriodValidator.MAX_LENGTH);
    }
}
