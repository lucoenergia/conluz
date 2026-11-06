package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScope;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyScopeResolver;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergySeriesBucket;
import org.lucoenergia.conluz.domain.admin.community.energy.GetCommunityMonthlyEnergySeriesService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetCommunityMonthlyEnergySeriesServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    /** From mid-January to April: February and March are selected. */
    private static final EnergyMetricsPeriod PERIOD = new EnergyMetricsPeriod(
            OffsetDateTime.parse("2026-01-15T00:00:00+01:00"), OffsetDateTime.parse("2026-04-01T00:00:00+02:00"));

    private final CommunityEnergyScopeResolver scopeResolver = mock(CommunityEnergyScopeResolver.class);
    private final GetDatadisConsumptionRepository consumptionRepository = mock(GetDatadisConsumptionRepository.class);
    private final GetCommunityMonthlyEnergySeriesService service =
            new GetCommunityMonthlyEnergySeriesServiceImpl(scopeResolver, consumptionRepository);

    private final Supply first = SupplyMother.random().build();
    private final Supply second = SupplyMother.random().build();

    @Test
    @DisplayName("CEM-006 every selected month is emitted with the supplies that stored a monthly record")
    void everySelectedMonthIsEmittedWithTheSuppliesThatStoredAMonthlyRecord() {
        when(scopeResolver.resolve(COMMUNITY_ID, PERIOD.getStartDate(), PERIOD.getEndDate(), null,
                EnergyMetricsPeriodValidator.MAX_LENGTH))
                .thenReturn(new CommunityEnergyScope(List.of(first, second), ZONE, PERIOD));
        when(consumptionRepository.getMonthlyConsumptionsByRangeOfDates(first, PERIOD.getStartDate(),
                PERIOD.getEndDate())).thenReturn(List.of(month(first, "2026/02/01", 10f, 1f)));
        when(consumptionRepository.getMonthlyConsumptionsByRangeOfDates(second, PERIOD.getStartDate(),
                PERIOD.getEndDate())).thenReturn(List.of(month(second, "2026/02/01", 5f, 2f)));

        List<CommunityEnergySeriesBucket> series = service.getMonthlySeries(COMMUNITY_ID, PERIOD.getStartDate(),
                PERIOD.getEndDate());

        assertEquals(2, series.size());
        assertEquals(OffsetDateTime.parse("2026-02-01T00:00:00+01:00"), series.get(0).getStartsAt());
        assertEquals(15d, series.get(0).getSelfConsumptionKWh(), 1e-6);
        assertEquals(3d, series.get(0).getSurplusKWh(), 1e-6);
        assertEquals(2, series.get(0).getSuppliesWithData());
        assertEquals(OffsetDateTime.parse("2026-03-01T00:00:00+01:00"), series.get(1).getStartsAt());
        assertNull(series.get(1).getSelfConsumptionKWh());
        assertEquals(0, series.get(1).getSuppliesWithData());
        assertEquals(2, series.get(1).getSupplyCount());
    }

    private static DatadisConsumption month(Supply supply, String date, Float selfConsumptionKWh, Float surplusKWh) {
        DatadisConsumption consumption = new DatadisConsumption();
        consumption.setCups(supply.getCode());
        consumption.setDate(date);
        consumption.setSelfConsumptionEnergyKWh(selfConsumptionKWh);
        consumption.setSurplusEnergyKWh(surplusKWh);
        return consumption;
    }
}
