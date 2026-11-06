package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.admin.community.energy.CommunityEnergyTestFixture.EnergyCommunity;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end over a real PostgreSQL and InfluxDB. The clock is fixed at 2026-10-15T12:00 in Madrid.
 */
@Transactional
class GetCommunityDailyEnergySeriesControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/communities/{communityId}/energy-series/daily";
    private static final double TOLERANCE = 1e-9;
    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");
    /** Midnight of 10 May 2026 in Madrid. */
    private static final Instant MAY_10 = Instant.parse("2026-05-09T22:00:00Z");

    @Autowired
    private CommunityEnergyTestFixture fixture;

    @BeforeEach
    void givenAFixedClock() {
        travelTo(NOW);
    }

    @AfterEach
    void clearWrittenSeries() {
        fixture.clear();
    }

    /**
     * On 10 May two of three consumer supplies have every hour; on 11 May one has 22 of 24 hours, which
     * counts, and the other 21, which does not; on 12 May nobody has anything.
     */
    @Test
    @DisplayName("CEM-006 AC7 every day is emitted with the supplies with data, a partial day counted, a gap null")
    void everyDayIsEmittedWithTheSuppliesWithData() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeConsumption(community.supplies().get(0), hours(MAY_10, 24), 1f, 0.5f, 0.25f);
        fixture.writeConsumption(community.supplies().get(1), hours(MAY_10, 24), 1f, 1f, 0f);
        Instant may11 = MAY_10.plus(Duration.ofDays(1));
        fixture.writeConsumption(community.supplies().get(0), hours(may11, 22), 1f, 0.5f, 0.25f);
        fixture.writeConsumption(community.supplies().get(1), hours(may11, 21), 1f, 1f, 0f);
        fixture.writeConsumption(community.disabledSupply(), hours(may11, 24), 1f, 100f, 100f);

        request(community.id(), loginUser(community.memberA()), "2026-05-10T00:00:00+02:00",
                "2026-05-13T00:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].date").value("2026/05/10"))
                .andExpect(jsonPath("$[0].time").value("00:00"))
                .andExpect(jsonPath("$[0].selfConsumptionKWh").value(closeTo(36.0, TOLERANCE)))
                .andExpect(jsonPath("$[0].surplusKWh").value(closeTo(6.0, TOLERANCE)))
                .andExpect(jsonPath("$[0].supplyCount").value(5))
                .andExpect(jsonPath("$[0].suppliesWithData").value(2))
                .andExpect(jsonPath("$[1].date").value("2026/05/11"))
                .andExpect(jsonPath("$[1].selfConsumptionKWh").value(closeTo(11.0, TOLERANCE)))
                .andExpect(jsonPath("$[1].surplusKWh").value(closeTo(5.5, TOLERANCE)))
                .andExpect(jsonPath("$[1].suppliesWithData").value(1))
                .andExpect(jsonPath("$[2].date").value("2026/05/12"))
                .andExpect(jsonPath("$[2].selfConsumptionKWh").value(nullValue()))
                .andExpect(jsonPath("$[2].surplusKWh").value(nullValue()))
                .andExpect(jsonPath("$[2].suppliesWithData").value(0));
    }

    @Test
    @DisplayName("CEM-006 a day whose supplies with data carry no energy is a measured zero")
    void aMeasuredZeroIsZero() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeConsumption(community.supplies().get(0), hours(MAY_10, 24), 1f, 0f, 0f);

        request(community.id(), loginUser(community.memberA()), "2026-05-10T00:00:00+02:00",
                "2026-05-11T00:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].selfConsumptionKWh").value(0.0))
                .andExpect(jsonPath("$[0].surplusKWh").value(0.0))
                .andExpect(jsonPath("$[0].suppliesWithData").value(1));
    }

    @Test
    @DisplayName("EPR-001 AC9 the period is half-open: the day starting on the end bound is not emitted")
    void thePeriodIsHalfOpen() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeConsumption(community.supplies().get(0), hours(MAY_10.plus(Duration.ofDays(1)), 24), 1f, 1f, 0f);

        request(community.id(), loginUser(community.memberA()), "2026-05-10T00:00:00+02:00",
                "2026-05-11T00:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].selfConsumptionKWh").value(nullValue()));
    }

    /**
     * Today is clipped at 12:00, so its possible hours are 12, of which 11 reach 90 %.
     */
    @Test
    @DisplayName("EPR-002 AC9 today is emitted only up to the current hour and judged on those hours")
    void todayIsEmittedOnlyUpToTheCurrentHour() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        Instant today = Instant.parse("2026-10-14T22:00:00Z");
        fixture.writeConsumption(community.supplies().get(0), hours(today, 11), 1f, 1f, 0f);

        request(community.id(), loginUser(community.memberA()), "2026-10-14T00:00:00+02:00",
                "2026-10-20T00:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].date").value("2026/10/15"))
                .andExpect(jsonPath("$[1].suppliesWithData").value(1))
                .andExpect(jsonPath("$[1].selfConsumptionKWh").value(closeTo(11.0, TOLERANCE)));
    }

    @Test
    @DisplayName("EPR-003 AC9 a start at the start of the current hour is rejected")
    void aStartAtTheCurrentHourIsRejected() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        request(community.id(), loginUser(community.memberA()), "2026-10-15T12:00:00+02:00",
                "2026-10-16T00:00:00+02:00")
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
        String start = "2026-05-10T00:00:00+02:00";
        String end = "2026-05-11T00:00:00+02:00";

        request(community.id(), loginUser(community.memberA()), start, end).andExpect(status().isOk());
        request(community.id(), loginAsDefaultPlatformAdmin(), start, end).andExpect(status().isForbidden());
        request(community.id(), loginAsPartner(), start, end).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CEM-007 AC11 the series carries no supply id, CUPS or member reference")
    void theSeriesCarriesNoSupplyIdCupsOrMemberReference() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeConsumption(community.supplies().get(0), hours(MAY_10, 24), 1f, 1f, 1f);

        String body = request(community.id(), loginUser(community.memberA()), "2026-05-10T00:00:00+02:00",
                "2026-05-11T00:00:00+02:00")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        GetCommunityEnergyMetricsControllerTest.assertNoSupplyOrMember(body, community);
    }

    private static List<Instant> hours(Instant first, int count) {
        return Stream.iterate(first, time -> time.plus(Duration.ofHours(1))).limit(count).toList();
    }

    private ResultActions request(UUID communityId, String token, String startDate, String endDate)
            throws Exception {
        return mockMvc.perform(get(PATH, communityId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate));
    }
}
