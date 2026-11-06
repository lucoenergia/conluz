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

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end over a real PostgreSQL and InfluxDB. The clock is fixed at 2026-10-15T12:00 in Madrid.
 */
@Transactional
class GetCommunityHourlyEnergySeriesControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/communities/{communityId}/energy-series/hourly";
    private static final double TOLERANCE = 1e-9;
    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

    private static final String DAY_START = "2026-05-10T00:00:00+02:00";
    private static final String DAY_END = "2026-05-11T00:00:00+02:00";
    /** 10:00 on 10 May 2026 in Madrid. */
    private static final Instant TEN = Instant.parse("2026-05-10T08:00:00Z");

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

    @Test
    @DisplayName("CEM-006 every hour is emitted, summing the supplies with data, with null for a gap")
    void everyHourIsEmittedSummingTheSuppliesWithDataWithNullForAGap() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeConsumption(community.supplies().get(0), TEN, 1f, 2f, 1f);
        fixture.writeConsumption(community.supplies().get(1), TEN, 1f, 3f, 0f);
        // Surplus alone is not data, and a disabled supply is out of scope.
        fixture.writeConsumption(community.supplies().get(2), TEN, 1f, null, 9f);
        fixture.writeConsumption(community.disabledSupply(), TEN, 1f, 100f, 100f);
        // A measured zero at 12:00.
        fixture.writeConsumption(community.supplies().get(0), TEN.plusSeconds(7200), 1f, 0f, 0f);
        EnergyCommunity other = fixture.createCommunity();
        fixture.writeConsumption(other.supplies().get(0), TEN, 1f, 1000f, 1000f);

        request(community.id(), loginUser(community.memberA()), DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(24))
                .andExpect(jsonPath("$[10].startsAt").value("2026-05-10T10:00:00+02:00"))
                .andExpect(jsonPath("$[10].date").value("2026/05/10"))
                .andExpect(jsonPath("$[10].time").value("10:00"))
                .andExpect(jsonPath("$[10].selfConsumptionKWh").value(closeTo(5.0, TOLERANCE)))
                .andExpect(jsonPath("$[10].surplusKWh").value(closeTo(1.0, TOLERANCE)))
                .andExpect(jsonPath("$[10].supplyCount").value(5))
                .andExpect(jsonPath("$[10].suppliesWithData").value(2))
                .andExpect(jsonPath("$[11].selfConsumptionKWh").value(nullValue()))
                .andExpect(jsonPath("$[11].surplusKWh").value(nullValue()))
                .andExpect(jsonPath("$[11].suppliesWithData").value(0))
                .andExpect(jsonPath("$[12].selfConsumptionKWh").value(0.0))
                .andExpect(jsonPath("$[12].surplusKWh").value(0.0))
                .andExpect(jsonPath("$[12].suppliesWithData").value(1));
    }

    @Test
    @DisplayName("CEM-006 the day the clocks go back has 25 hours, each with its own start instant")
    void theDayTheClocksGoBackHasTwentyFiveHours() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeConsumption(community.supplies().get(0), Instant.parse("2025-10-26T00:00:00Z"), 1f, 1f, 0f);
        fixture.writeConsumption(community.supplies().get(0), Instant.parse("2025-10-26T01:00:00Z"), 1f, 2f, 0f);

        request(community.id(), loginUser(community.memberA()), "2025-10-26T00:00:00+02:00",
                "2025-10-27T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(25))
                .andExpect(jsonPath("$[2].startsAt").value("2025-10-26T02:00:00+02:00"))
                .andExpect(jsonPath("$[2].selfConsumptionKWh").value(closeTo(1.0, TOLERANCE)))
                .andExpect(jsonPath("$[3].startsAt").value("2025-10-26T02:00:00+01:00"))
                .andExpect(jsonPath("$[3].selfConsumptionKWh").value(closeTo(2.0, TOLERANCE)));
    }

    @Test
    @DisplayName("EPR-001 AC9 the period is half-open: the hour starting on the end bound is not emitted")
    void thePeriodIsHalfOpen() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeConsumption(community.supplies().get(0), TEN.plusSeconds(7200), 1f, 7f, 0f);

        request(community.id(), loginUser(community.memberA()), "2026-05-10T10:00:00+02:00",
                "2026-05-10T12:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].startsAt").value("2026-05-10T11:00:00+02:00"))
                .andExpect(jsonPath("$[1].selfConsumptionKWh").value(nullValue()));
    }

    @Test
    @DisplayName("EPR-002 AC9 no hour starting at or after the start of the current hour is emitted")
    void noHourAfterTheCurrentHourIsEmitted() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        request(community.id(), loginUser(community.memberA()), "2026-10-15T00:00:00+02:00",
                "2026-10-16T00:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[11].startsAt").value("2026-10-15T11:00:00+02:00"));
    }

    @Test
    @DisplayName("EPR-003 AC9 a start at the start of the current hour is rejected")
    void aStartAtTheCurrentHourIsRejected() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        request(community.id(), loginUser(community.memberA()), "2026-10-15T12:00:00+02:00",
                "2026-10-15T13:00:00+02:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_START_IN_FUTURE"));
    }

    @Test
    @DisplayName("EPR-005 AC8 a period longer than 31 days is answered with its days limit")
    void aPeriodLongerThanThirtyOneDaysIsRejected() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        request(community.id(), loginUser(community.memberA()), "2026-09-01T00:00:00+02:00",
                "2026-10-02T01:00:00+02:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_TOO_LONG"))
                .andExpect(jsonPath("$.errors[0].params.amount").value("31"))
                .andExpect(jsonPath("$.errors[0].params.unit").value("DAYS"));
    }

    @Test
    @DisplayName("EPR-005 a period of exactly 31 days is accepted")
    void aPeriodOfExactlyThirtyOneDaysIsAccepted() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        request(community.id(), loginUser(community.memberA()), "2026-09-01T00:00:00+02:00",
                "2026-10-02T00:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(31 * 24));
    }

    @Test
    @DisplayName("CEM-007 AC10 a member reads the series, a platform admin without membership gets 403, anyone else 404")
    void accessFollowsTheMembership() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        request(community.id(), loginUser(community.memberA()), DAY_START, DAY_END).andExpect(status().isOk());
        request(community.id(), loginAsDefaultPlatformAdmin(), DAY_START, DAY_END).andExpect(status().isForbidden());
        request(community.id(), loginAsPartner(), DAY_START, DAY_END).andExpect(status().isNotFound());
        request(community.id(), loginUser(community.disabledMember()), DAY_START, DAY_END)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CEM-007 AC11 the series carries no supply id, CUPS or member reference")
    void theSeriesCarriesNoSupplyIdCupsOrMemberReference() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeConsumption(community.supplies().get(0), TEN, 1f, 2f, 1f);

        String body = request(community.id(), loginUser(community.memberA()), DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        GetCommunityEnergyMetricsControllerTest.assertNoSupplyOrMember(body, community);
    }

    private ResultActions request(UUID communityId, String token, String startDate, String endDate)
            throws Exception {
        return mockMvc.perform(get(PATH, communityId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate));
    }
}
