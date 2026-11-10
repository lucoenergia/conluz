package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.admin.community.energy.CommunityEnergyTestFixture;
import org.lucoenergia.conluz.infrastructure.admin.community.energy.CommunityEnergyTestFixture.EnergyCommunity;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.lucoenergia.conluz.infrastructure.production.plant.energy.PlantEnergyTestSupport.DAY_KWH;
import static org.lucoenergia.conluz.infrastructure.production.plant.energy.PlantEnergyTestSupport.assertNoSupplyOrMember;
import static org.lucoenergia.conluz.infrastructure.production.plant.energy.PlantEnergyTestSupport.writeDay;
import static org.lucoenergia.conluz.infrastructure.production.plant.energy.PlantEnergyTestSupport.writeFirstHours;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end over a real PostgreSQL and InfluxDB. The clock is fixed at 2026-11-10T10:30 in Madrid.
 */
@Transactional
class GetPlantDailyEnergySeriesControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/plants/{plantId}/energy-series/daily";
    private static final double TOLERANCE = 1e-9;
    private static final Instant NOW = Instant.parse("2026-11-10T09:30:00Z");

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
    @DisplayName("PEM-003 AC6 every day is emitted: a day without records is null with 0 hours, a half day 12 of 24")
    void everyDayIsEmittedWithItsCoverage() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        writeDay(fixture, community.north(), LocalDate.of(2026, 11, 6));
        writeFirstHours(fixture, community.north(), LocalDate.of(2026, 11, 8), 12);
        writeDay(fixture, community.south(), LocalDate.of(2026, 11, 7));

        request(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2026-11-06T00:00:00+01:00", "2026-11-09T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].date").value("2026/11/06"))
                .andExpect(jsonPath("$[0].time").value("00:00"))
                .andExpect(jsonPath("$[0].productionKWh").value(closeTo(DAY_KWH, TOLERANCE)))
                .andExpect(jsonPath("$[0].hoursWithData").value(24))
                .andExpect(jsonPath("$[0].expectedHours").value(24))
                .andExpect(jsonPath("$[1].date").value("2026/11/07"))
                .andExpect(jsonPath("$[1].productionKWh").value(nullValue()))
                .andExpect(jsonPath("$[1].hoursWithData").value(0))
                .andExpect(jsonPath("$[1].expectedHours").value(24))
                .andExpect(jsonPath("$[2].productionKWh").value(closeTo(6.0, TOLERANCE)))
                .andExpect(jsonPath("$[2].hoursWithData").value(12))
                .andExpect(jsonPath("$[2].expectedHours").value(24));
    }

    @Test
    @DisplayName("PEM-003 a day whose records are all night zeros is a measured zero")
    void aDayOfNightZerosIsAMeasuredZero() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        writeFirstHours(fixture, community.north(), LocalDate.of(2026, 11, 6), 6);

        request(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2026-11-06T00:00:00+01:00", "2026-11-07T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productionKWh").value(0.0))
                .andExpect(jsonPath("$[0].hoursWithData").value(6));
    }

    @Test
    @DisplayName("PEM-003 AC9 the days the clocks change report 23 and 25 possible hours")
    void theDaysTheClocksChangeReportTwentyThreeAndTwentyFivePossibleHours() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        writeDay(fixture, community.north(), LocalDate.of(2026, 3, 29));
        writeDay(fixture, community.north(), LocalDate.of(2026, 10, 25));

        request(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2026-03-28T00:00:00+01:00", "2026-03-30T00:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].expectedHours").value(24))
                .andExpect(jsonPath("$[1].date").value("2026/03/29"))
                .andExpect(jsonPath("$[1].expectedHours").value(23))
                .andExpect(jsonPath("$[1].hoursWithData").value(23));
        request(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2026-10-25T00:00:00+02:00", "2026-10-27T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2026/10/25"))
                .andExpect(jsonPath("$[0].expectedHours").value(25))
                .andExpect(jsonPath("$[0].hoursWithData").value(25))
                .andExpect(jsonPath("$[1].expectedHours").value(24));
    }

    @Test
    @DisplayName("EPR-001 AC10 a day the period starts inside counts only its hours inside the period")
    void aDayThePeriodStartsInsideCountsOnlyItsHoursInside() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        writeDay(fixture, community.north(), LocalDate.of(2026, 11, 6));

        request(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2026-11-06T12:00:00+01:00", "2026-11-08T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].expectedHours").value(12))
                .andExpect(jsonPath("$[0].hoursWithData").value(12))
                .andExpect(jsonPath("$[0].productionKWh").value(closeTo(6 * 1.5, TOLERANCE)));
    }

    @Test
    @DisplayName("EPR-002 AC10 the current day is clipped to the start of the current hour")
    void theCurrentDayIsClipped() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        request(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2026-11-09T00:00:00+01:00", "2026-11-12T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].date").value("2026/11/10"))
                .andExpect(jsonPath("$[1].expectedHours").value(10));
    }

    @Test
    @DisplayName("EPR-003 AC10 a start at the start of the current hour is rejected")
    void aStartAtTheCurrentHourIsRejected() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        request(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2026-11-10T10:00:00+01:00", "2026-11-11T00:00:00+01:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_START_IN_FUTURE"));
    }

    @Test
    @DisplayName("EPR-004 AC11 a period longer than 24 months is answered with its months limit")
    void aPeriodLongerThanTwentyFourMonthsIsRejected() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        request(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2024-01-01T00:00:00+01:00", "2026-01-02T00:00:00+01:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_TOO_LONG"))
                .andExpect(jsonPath("$.errors[0].params.amount").value("24"))
                .andExpect(jsonPath("$.errors[0].params.unit").value("MONTHS"));
    }

    @Test
    @DisplayName("PEM-004 AC12 a community admin reads it, a member gets 403, a non-member and a platform admin 404")
    void onlyACommunityAdminReadsIt() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        UUID plantId = community.north().getId();

        request(plantId, loginAsCommunityAdmin(community.id()), "2026-11-01T00:00:00+01:00", "2026-11-09T00:00:00+01:00").andExpect(status().isOk());
        request(plantId, loginUser(community.memberA()), "2026-11-01T00:00:00+01:00", "2026-11-09T00:00:00+01:00").andExpect(status().isForbidden());
        request(plantId, loginAsPartner(), "2026-11-01T00:00:00+01:00", "2026-11-09T00:00:00+01:00").andExpect(status().isNotFound());
        request(plantId, loginAsDefaultPlatformAdmin(), "2026-11-01T00:00:00+01:00", "2026-11-09T00:00:00+01:00").andExpect(status().isNotFound());
        request(plantId, loginUser(community.disabledMember()), "2026-11-01T00:00:00+01:00", "2026-11-09T00:00:00+01:00").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PEM-004 AC13 the series carries no CUPS, supply id or member reference")
    void theSeriesCarriesNoCupsSupplyIdOrMemberReference() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        writeDay(fixture, community.north(), LocalDate.of(2026, 11, 8));

        String body = request(community.north().getId(), loginAsCommunityAdmin(community.id()), "2026-11-01T00:00:00+01:00", "2026-11-09T00:00:00+01:00")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertNoSupplyOrMember(body, community);
    }

    private ResultActions request(UUID plantId, String token, String startDate, String endDate) throws Exception {
        return mockMvc.perform(get(PATH, plantId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate));
    }
}
