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
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.lucoenergia.conluz.infrastructure.production.plant.energy.PlantEnergyTestSupport.assertNoSupplyOrMember;
import static org.lucoenergia.conluz.infrastructure.production.plant.energy.PlantEnergyTestSupport.writeDay;
import static org.lucoenergia.conluz.infrastructure.production.plant.energy.PlantEnergyTestSupport.writeDayWithout;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end over a real PostgreSQL and InfluxDB. The clock is fixed at 2026-11-10T10:30 in Madrid.
 */
@Transactional
class GetPlantHourlyEnergySeriesControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/plants/{plantId}/energy-series/hourly";
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
    @DisplayName("PEM-003 AC7 every hour is emitted: night zeros are 0, a missing hour is null, the other plant left out")
    void everyHourIsEmittedWithNightZerosAndGaps() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        // 03:00 missing; the other night hours stored as 0.
        writeDayWithout(fixture, community.north(), LocalDate.of(2026, 11, 8), Set.of(3));
        writeDay(fixture, community.south(), LocalDate.of(2026, 11, 8));
        fixture.writeProduction(community.south(), Instant.parse("2026-11-08T01:00:00Z"), 1000f);

        request(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2026-11-08T00:00:00+01:00", "2026-11-09T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(24))
                .andExpect(jsonPath("$[2].startsAt").value("2026-11-08T02:00:00+01:00"))
                .andExpect(jsonPath("$[2].date").value("2026/11/08"))
                .andExpect(jsonPath("$[2].time").value("02:00"))
                .andExpect(jsonPath("$[2].productionKWh").value(0.0))
                .andExpect(jsonPath("$[2].hoursWithData").value(1))
                .andExpect(jsonPath("$[2].expectedHours").value(1))
                .andExpect(jsonPath("$[3].productionKWh").value(nullValue()))
                .andExpect(jsonPath("$[3].hoursWithData").value(0))
                .andExpect(jsonPath("$[3].expectedHours").value(1))
                .andExpect(jsonPath("$[12].productionKWh").value(closeTo(1.5, TOLERANCE)))
                .andExpect(jsonPath("$[22].productionKWh").value(0.0));
    }

    @Test
    @DisplayName("PEM-003 the day the clocks go back has 25 hours, each with its own start instant")
    void theDayTheClocksGoBackHasTwentyFiveHours() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeProduction(community.north(), Instant.parse("2026-10-25T00:00:00Z"), 1f);
        fixture.writeProduction(community.north(), Instant.parse("2026-10-25T01:00:00Z"), 2f);

        request(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2026-10-25T00:00:00+02:00", "2026-10-26T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(25))
                .andExpect(jsonPath("$[2].startsAt").value("2026-10-25T02:00:00+02:00"))
                .andExpect(jsonPath("$[2].productionKWh").value(closeTo(1.0, TOLERANCE)))
                .andExpect(jsonPath("$[3].startsAt").value("2026-10-25T02:00:00+01:00"))
                .andExpect(jsonPath("$[3].productionKWh").value(closeTo(2.0, TOLERANCE)));
    }

    @Test
    @DisplayName("EPR-001 AC10 the period is half-open: the hour starting on the end bound is not emitted")
    void thePeriodIsHalfOpen() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeProduction(community.north(), Instant.parse("2026-11-08T11:00:00Z"), 7f);

        request(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2026-11-08T10:00:00+01:00", "2026-11-08T12:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].startsAt").value("2026-11-08T11:00:00+01:00"))
                .andExpect(jsonPath("$[1].productionKWh").value(nullValue()));
    }

    @Test
    @DisplayName("EPR-002 AC10 no hour starting at or after the start of the current hour is emitted")
    void noHourAfterTheCurrentHourIsEmitted() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        request(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2026-11-10T00:00:00+01:00", "2026-11-11T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[9].startsAt").value("2026-11-10T09:00:00+01:00"));
    }

    @Test
    @DisplayName("EPR-003 AC10 a start at the start of the current hour is rejected")
    void aStartAtTheCurrentHourIsRejected() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        request(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2026-11-10T10:00:00+01:00", "2026-11-10T11:00:00+01:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_START_IN_FUTURE"));
    }

    @Test
    @DisplayName("EPR-005 AC11 a period longer than 31 days is answered with its days limit")
    void aPeriodLongerThanThirtyOneDaysIsRejected() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        request(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2026-09-01T00:00:00+02:00", "2026-10-02T01:00:00+02:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_TOO_LONG"))
                .andExpect(jsonPath("$.errors[0].params.amount").value("31"))
                .andExpect(jsonPath("$.errors[0].params.unit").value("DAYS"));
    }

    @Test
    @DisplayName("EPR-005 a period of exactly 31 days is accepted")
    void aPeriodOfExactlyThirtyOneDaysIsAccepted() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        request(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2026-09-01T00:00:00+02:00", "2026-10-02T00:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(31 * 24));
    }

    @Test
    @DisplayName("PEM-004 AC12 a community admin reads it, a member gets 403, a non-member and a platform admin 404")
    void onlyACommunityAdminReadsIt() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        UUID plantId = community.north().getId();

        request(plantId, loginAsCommunityAdmin(community.id()), "2026-11-08T00:00:00+01:00", "2026-11-09T00:00:00+01:00").andExpect(status().isOk());
        request(plantId, loginUser(community.memberA()), "2026-11-08T00:00:00+01:00", "2026-11-09T00:00:00+01:00").andExpect(status().isForbidden());
        request(plantId, loginAsPartner(), "2026-11-08T00:00:00+01:00", "2026-11-09T00:00:00+01:00").andExpect(status().isNotFound());
        request(plantId, loginAsDefaultPlatformAdmin(), "2026-11-08T00:00:00+01:00", "2026-11-09T00:00:00+01:00").andExpect(status().isNotFound());
        request(plantId, loginUser(community.disabledMember()), "2026-11-08T00:00:00+01:00", "2026-11-09T00:00:00+01:00").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PEM-004 AC13 the series carries no CUPS, supply id or member reference")
    void theSeriesCarriesNoCupsSupplyIdOrMemberReference() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        writeDay(fixture, community.north(), LocalDate.of(2026, 11, 8));

        String body = request(community.north().getId(), loginAsCommunityAdmin(community.id()), "2026-11-08T00:00:00+01:00", "2026-11-09T00:00:00+01:00")
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
