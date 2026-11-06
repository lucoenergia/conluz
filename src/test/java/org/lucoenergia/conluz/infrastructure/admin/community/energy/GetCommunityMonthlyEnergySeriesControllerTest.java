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
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end over a real PostgreSQL and InfluxDB. The clock is fixed at 2026-10-15T12:00 in Madrid, so
 * October is clipped at 12:00 on the 15th: 348 hours. August spans 744 hours and September 720, so 90 %
 * is 670 and 648 hours.
 */
@Transactional
class GetCommunityMonthlyEnergySeriesControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/communities/{communityId}/energy-series/monthly";
    private static final String METRICS_PATH = "/api/v1/communities/{communityId}/energy-metrics";
    private static final double TOLERANCE = 1e-6;
    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");
    private static final YearMonth AUGUST = YearMonth.of(2026, 8);
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

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
     * July: no supply has records. August: two supplies published in full, the third with no record at
     * all. September: one supply at exactly 90 % of its hours, the other one hour below. October, up to
     * the current hour: one supply published with zeros.
     */
    @Test
    @DisplayName("CEM-006 CEM-004 every month sums the supplies with data under the 90 % threshold, a gap null")
    void everyMonthSumsTheSuppliesWithDataUnderTheThreshold() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        Supply first = community.supplies().get(0);
        Supply second = community.supplies().get(1);
        fixture.writeConsumption(first, hoursOf(AUGUST), 1f, 0.5f, 0.25f);
        fixture.writeConsumption(second, hoursOf(AUGUST), 1f, 1f, 0f);
        fixture.writeConsumption(first, hoursOf(SEPTEMBER).subList(0, 648), 1f, 0.5f, 0.25f);
        fixture.writeConsumption(second, hoursOf(SEPTEMBER).subList(0, 647), 1f, 1f, 0f);
        fixture.writeConsumption(first, hoursOf(OCTOBER).subList(0, 348), 1f, 0f, 0f);
        fixture.writeConsumption(community.disabledSupply(), hoursOf(AUGUST), 1f, 100f, 100f);

        request(community.id(), loginUser(community.memberA()), "2026-07-01T00:00:00+02:00",
                "2026-11-01T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
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
                .andExpect(jsonPath("$[2].selfConsumptionKWh").value(closeTo(648 * 0.5, TOLERANCE)))
                .andExpect(jsonPath("$[2].surplusKWh").value(closeTo(648 * 0.25, TOLERANCE)))
                .andExpect(jsonPath("$[2].suppliesWithData").value(1))
                .andExpect(jsonPath("$[3].date").value("2026/10/01"))
                .andExpect(jsonPath("$[3].selfConsumptionKWh").value(0.0))
                .andExpect(jsonPath("$[3].surplusKWh").value(0.0))
                .andExpect(jsonPath("$[3].suppliesWithData").value(1));
    }

    @Test
    @DisplayName("CEM-006 CEM-004 a supply with no records in a month is left out of its sums and its count")
    void aSupplyWithNoRecordsInAMonthIsLeftOut() throws Exception {
        UUID communityId = fixture.createEmptyCommunity().getId();
        List<Supply> supplies = fixture.createCommunityWithSupplies(communityId, 2);
        fixture.writeConsumption(supplies.get(0), hoursOf(AUGUST), 1f, 2f, 1f);
        fixture.writeConsumption(supplies.get(0), hoursOf(SEPTEMBER), 1f, 1f, 0f);
        fixture.writeConsumption(supplies.get(1), hoursOf(SEPTEMBER), 1f, 1f, 0f);

        request(communityId, loginUser(fixture.createMember(communityId)), "2026-08-01T00:00:00+02:00",
                "2026-10-01T00:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].selfConsumptionKWh").value(closeTo(744 * 2.0, TOLERANCE)))
                .andExpect(jsonPath("$[0].surplusKWh").value(closeTo(744 * 1.0, TOLERANCE)))
                .andExpect(jsonPath("$[0].supplyCount").value(2))
                .andExpect(jsonPath("$[0].suppliesWithData").value(1))
                .andExpect(jsonPath("$[1].selfConsumptionKWh").value(closeTo(720 * 2.0, TOLERANCE)))
                .andExpect(jsonPath("$[1].suppliesWithData").value(2));
    }

    /**
     * The pre-aggregate of a month whose records carry surplus but no self-consumption stores 0
     * self-consumption: the series must not read it as a measured zero.
     */
    @Test
    @DisplayName("CEM-006 an unpublished month is a gap even when its monthly pre-aggregate exists")
    void anUnpublishedMonthIsAGapEvenWhenItsPreAggregateExists() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        Supply first = community.supplies().get(0);
        Supply second = community.supplies().get(1);
        fixture.writeConsumption(first, hoursOf(SEPTEMBER), 1f, null, 2f);
        fixture.writeConsumption(second, hoursOf(SEPTEMBER), 1f, null, 3f);
        monthlyAggregationRepository.aggregateMonthlyConsumption(first, SEPTEMBER.getMonth(), SEPTEMBER.getYear());
        monthlyAggregationRepository.aggregateMonthlyConsumption(second, SEPTEMBER.getMonth(), SEPTEMBER.getYear());

        request(community.id(), loginUser(community.memberA()), "2026-09-01T00:00:00+02:00",
                "2026-10-01T00:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].selfConsumptionKWh").value(nullValue()))
                .andExpect(jsonPath("$[0].surplusKWh").value(nullValue()))
                .andExpect(jsonPath("$[0].suppliesWithData").value(0));
    }

    @Test
    @DisplayName("CEM-006 CEM-004 for the same month, suppliesWithData matches the energy metrics coverage")
    void suppliesWithDataMatchesTheMetricsCoverageForTheSameMonth() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeConsumption(community.supplies().get(0), hoursOf(AUGUST), 1f, 1f, 0f);
        fixture.writeConsumption(community.supplies().get(1), hoursOf(AUGUST).subList(0, 670), 1f, 1f, 0f);
        fixture.writeConsumption(community.supplies().get(2), hoursOf(AUGUST).subList(0, 669), 1f, 1f, 0f);
        fixture.writeConsumption(community.supplies().get(0), hoursOf(SEPTEMBER).subList(0, 647), 1f, 1f, 0f);
        fixture.writeConsumption(community.supplies().get(1), hoursOf(SEPTEMBER), 1f, 1f, 0f);
        String token = loginUser(community.memberA());

        String series = request(community.id(), token, "2026-08-01T00:00:00+02:00", "2026-10-01T00:00:00+02:00")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int[] expected = {2, 1};
        YearMonth[] months = {AUGUST, SEPTEMBER};
        for (int i = 0; i < months.length; i++) {
            String metrics = mockMvc.perform(get(METRICS_PATH, community.id())
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .queryParam("startDate", months[i].atDay(1).atStartOfDay(ZONE).toOffsetDateTime().toString())
                            .queryParam("endDate", months[i].plusMonths(1).atDay(1).atStartOfDay(ZONE).toOffsetDateTime()
                                    .toString()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            int coverage = objectMapper.readTree(metrics).at("/coverage/suppliesWithData").asInt();
            int bucket = objectMapper.readTree(series).get(i).get("suppliesWithData").asInt();

            assertEquals(expected[i], coverage, months[i].toString());
            assertEquals(coverage, bucket, months[i].toString());
        }
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
        fixture.writeConsumption(community.supplies().get(0), hoursOf(AUGUST), 1f, 1f, 1f);

        String body = request(community.id(), loginUser(community.memberA()), "2026-08-01T00:00:00+02:00",
                "2026-09-01T00:00:00+02:00")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        GetCommunityEnergyMetricsControllerTest.assertNoSupplyOrMember(body, community);
    }

    private static List<Instant> hoursOf(YearMonth month) {
        return GetCommunityEnergyMetricsControllerTest.hoursOf(month);
    }

    private ResultActions request(UUID communityId, String token, String startDate, String endDate)
            throws Exception {
        return mockMvc.perform(get(PATH, communityId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate));
    }
}
