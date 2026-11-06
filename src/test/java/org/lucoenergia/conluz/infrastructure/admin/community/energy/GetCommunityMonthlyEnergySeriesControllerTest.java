package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.energy.CommunityEnergyTestFixture.EnergyCommunity;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end over a real PostgreSQL and InfluxDB. The monthly pre-aggregates are built by the
 * production aggregation from the hourly records each test writes. The clock is fixed at
 * 2026-10-15T12:00 in Madrid.
 */
@Transactional
class GetCommunityMonthlyEnergySeriesControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/communities/{communityId}/energy-series/monthly";
    private static final double TOLERANCE = 1e-6;
    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

    @Autowired
    private CommunityEnergyTestFixture fixture;
    @Autowired
    private DatadisMonthlyAggregationRepository monthlyAggregationRepository;

    @BeforeEach
    void givenAFixedClock() {
        travelTo(NOW);
    }

    @AfterEach
    void clearWrittenSeries() {
        fixture.clear();
    }

    /**
     * August: two supplies with a monthly record. September: one. July: none.
     */
    @Test
    @DisplayName("CEM-006 every selected month is emitted with the supplies that stored a monthly record, a gap null")
    void everyMonthIsEmittedWithTheSuppliesThatStoredAMonthlyRecord() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        Supply first = community.supplies().get(0);
        Supply second = community.supplies().get(1);
        writeAndAggregate(first, YearMonth.of(2026, 8), 0.5f, 0.25f);
        writeAndAggregate(second, YearMonth.of(2026, 8), 1f, 0f);
        writeAndAggregate(first, YearMonth.of(2026, 9), 0.5f, 0.25f);
        writeAndAggregate(community.disabledSupply(), YearMonth.of(2026, 9), 100f, 100f);

        request(community.id(), loginUser(community.memberA()), "2026-07-01T00:00:00+02:00",
                "2026-10-01T00:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].date").value("2026/07/01"))
                .andExpect(jsonPath("$[0].selfConsumptionKWh").value(nullValue()))
                .andExpect(jsonPath("$[0].surplusKWh").value(nullValue()))
                .andExpect(jsonPath("$[0].suppliesWithData").value(0))
                .andExpect(jsonPath("$[1].date").value("2026/08/01"))
                .andExpect(jsonPath("$[1].time").value("00:00"))
                .andExpect(jsonPath("$[1].selfConsumptionKWh").value(closeTo(744 * 1.5, TOLERANCE)))
                .andExpect(jsonPath("$[1].surplusKWh").value(closeTo(744 * 0.25, TOLERANCE)))
                .andExpect(jsonPath("$[1].suppliesWithData").value(2))
                .andExpect(jsonPath("$[1].supplyCount").value(5))
                .andExpect(jsonPath("$[2].date").value("2026/09/01"))
                .andExpect(jsonPath("$[2].selfConsumptionKWh").value(closeTo(720 * 0.5, TOLERANCE)))
                .andExpect(jsonPath("$[2].suppliesWithData").value(1));
    }

    @Test
    @DisplayName("EPR-001 AC9 a month is selected only when its first local midnight falls inside the period")
    void aMonthIsSelectedOnlyWhenItsFirstMidnightFallsInsideThePeriod() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        request(community.id(), loginUser(community.memberA()), "2026-07-15T00:00:00+02:00",
                "2026-09-01T00:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value("2026/08/01"));
    }

    @Test
    @DisplayName("EPR-002 AC9 no month starting at or after the start of the current hour is emitted")
    void noMonthAfterTheCurrentHourIsEmitted() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        request(community.id(), loginUser(community.memberA()), "2026-10-01T00:00:00+02:00",
                "2026-12-01T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value("2026/10/01"));
    }

    @Test
    @DisplayName("EPR-003 AC9 a start at the start of the current hour is rejected")
    void aStartAtTheCurrentHourIsRejected() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        request(community.id(), loginUser(community.memberA()), "2026-10-15T12:00:00+02:00",
                "2026-11-01T00:00:00+01:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_START_IN_FUTURE"));
    }

    @Test
    @DisplayName("EPR-004 a period longer than 24 months is answered with its months limit")
    void aPeriodLongerThanTwentyFourMonthsIsRejected() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        request(community.id(), loginUser(community.memberA()), "2024-01-01T00:00:00+01:00",
                "2026-01-02T00:00:00+01:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_TOO_LONG"))
                .andExpect(jsonPath("$.errors[0].params.amount").value("24"))
                .andExpect(jsonPath("$.errors[0].params.unit").value("MONTHS"));
    }

    @Test
    @DisplayName("CEM-007 AC10 a member reads the series, a platform admin without membership gets 403, anyone else 404")
    void accessFollowsTheMembership() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        String start = "2026-08-01T00:00:00+02:00";
        String end = "2026-09-01T00:00:00+02:00";

        request(community.id(), loginUser(community.memberA()), start, end).andExpect(status().isOk());
        request(community.id(), loginAsDefaultPlatformAdmin(), start, end).andExpect(status().isForbidden());
        request(community.id(), loginAsPartner(), start, end).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CEM-007 AC11 the series carries no supply id, CUPS or member reference")
    void theSeriesCarriesNoSupplyIdCupsOrMemberReference() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        writeAndAggregate(community.supplies().get(0), YearMonth.of(2026, 8), 1f, 1f);

        String body = request(community.id(), loginUser(community.memberA()), "2026-08-01T00:00:00+02:00",
                "2026-09-01T00:00:00+02:00")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        GetCommunityEnergyMetricsControllerTest.assertNoSupplyOrMember(body, community);
    }

    /**
     * Writes a record at every hour of the month and builds its monthly pre-aggregate.
     */
    private void writeAndAggregate(Supply supply, YearMonth month, float selfConsumptionKWh, float surplusKWh) {
        fixture.writeConsumption(supply, GetCommunityEnergyMetricsControllerTest.hoursOf(month), 1f,
                selfConsumptionKWh, surplusKWh);
        monthlyAggregationRepository.aggregateMonthlyConsumption(supply, month.getMonth(), month.getYear());
    }

    private ResultActions request(UUID communityId, String token, String startDate, String endDate)
            throws Exception {
        return mockMvc.perform(get(PATH, communityId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate));
    }
}
