package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScope;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScopeResolver;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergySeriesBucket;
import org.lucoenergia.conluz.domain.admin.community.energy.GetCommunityHourlyEnergySeriesService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisHourlyRecordsRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.HourlyEnergyRecord;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetCommunityHourlyEnergySeriesServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    /** 10:00 to 13:00 on 10 May 2026 in Madrid: three hours. */
    private static final EnergyMetricsPeriod PERIOD = new EnergyMetricsPeriod(
            OffsetDateTime.parse("2026-05-10T10:00:00+02:00"), OffsetDateTime.parse("2026-05-10T13:00:00+02:00"));
    private static final Instant TEN = Instant.parse("2026-05-10T08:00:00Z");
    private static final Instant ELEVEN = Instant.parse("2026-05-10T09:00:00Z");

    private final CommunityEnergyScopeResolver scopeResolver = mock(CommunityEnergyScopeResolver.class);
    private final GetDatadisHourlyRecordsRepository recordsRepository = mock(GetDatadisHourlyRecordsRepository.class);
    private final GetCommunityHourlyEnergySeriesService service =
            new GetCommunityHourlyEnergySeriesServiceImpl(scopeResolver, recordsRepository);

    private final Supply first = SupplyMother.random().build();
    private final Supply second = SupplyMother.random().build();
    private final Supply third = SupplyMother.random().build();

    @Test
    @DisplayName("CEM-006 every hour is emitted with the supplies whose record carries self-consumption")
    void everyHourIsEmittedWithTheSuppliesWhoseRecordCarriesSelfConsumption() {
        when(scopeResolver.resolve(COMMUNITY_ID, PERIOD.getStartDate(), PERIOD.getEndDate(), null,
                EnergyMetricsPeriodValidator.MAX_HOURLY_LENGTH))
                .thenReturn(new CommunityEnergyScope(List.of(first, second, third), ZONE, PERIOD));
        givenRecords(Map.of(
                first, List.of(new HourlyEnergyRecord(TEN, 1d, 2d, 0.5), new HourlyEnergyRecord(ELEVEN, 1d, 0d, 0d)),
                second, List.of(new HourlyEnergyRecord(TEN, 1d, 3d, null)),
                // Surplus alone is not data.
                third, List.of(new HourlyEnergyRecord(TEN, 1d, null, 9d))));

        List<CommunityEnergySeriesBucket> series = service.getHourlySeries(COMMUNITY_ID, PERIOD.getStartDate(),
                PERIOD.getEndDate());

        assertEquals(3, series.size());
        assertEquals(OffsetDateTime.parse("2026-05-10T10:00:00+02:00"), series.get(0).getStartsAt());
        assertEquals(5d, series.get(0).getSelfConsumptionKWh(), 1e-12);
        assertEquals(0.5, series.get(0).getSurplusKWh(), 1e-12);
        assertEquals(2, series.get(0).getSuppliesWithData());
        assertEquals(3, series.get(0).getSupplyCount());
        assertEquals(0d, series.get(1).getSelfConsumptionKWh(), 0d);
        assertEquals(1, series.get(1).getSuppliesWithData());
        assertNull(series.get(2).getSelfConsumptionKWh());
        assertEquals(0, series.get(2).getSuppliesWithData());
    }

    private void givenRecords(Map<Supply, List<HourlyEnergyRecord>> records) {
        doAnswer(invocation -> {
            Supply supply = invocation.getArgument(0);
            Consumer<HourlyEnergyRecord> consumer = invocation.getArgument(3);
            records.getOrDefault(supply, List.of()).forEach(consumer);
            return null;
        }).when(recordsRepository).forEachRecord(any(), eq(PERIOD.getStartDate()), eq(PERIOD.getEndDate()), any());
    }
}
