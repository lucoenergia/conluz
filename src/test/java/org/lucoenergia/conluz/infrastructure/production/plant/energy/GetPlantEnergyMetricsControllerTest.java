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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.lucoenergia.conluz.infrastructure.production.plant.energy.PlantEnergyTestSupport.DAY_KWH;
import static org.lucoenergia.conluz.infrastructure.production.plant.energy.PlantEnergyTestSupport.assertNoSupplyOrMember;
import static org.lucoenergia.conluz.infrastructure.production.plant.energy.PlantEnergyTestSupport.hoursOf;
import static org.lucoenergia.conluz.infrastructure.production.plant.energy.PlantEnergyTestSupport.writeDay;
import static org.lucoenergia.conluz.infrastructure.production.plant.energy.PlantEnergyTestSupport.writeDayWithout;
import static org.lucoenergia.conluz.infrastructure.production.plant.energy.PlantEnergyTestSupport.writeFirstHours;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end over a real PostgreSQL and InfluxDB. The clock is fixed at 2026-11-10T10:30 in Madrid.
 */
@Transactional
class GetPlantEnergyMetricsControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/plants/{plantId}/energy-metrics";
    private static final double TOLERANCE = 1e-9;
    private static final Instant NOW = Instant.parse("2026-11-10T09:30:00Z");
    private static final String LATEST = "LATEST_PRODUCTION_DAY";

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
    @DisplayName("PEM-001 AC1 the latest production day is the latest complete day, a later partial day left out")
    void theLatestProductionDayIsTheLatestCompleteDay() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        writeDay(fixture, community.north(), LocalDate.of(2026, 11, 5));
        writeDay(fixture, community.north(), LocalDate.of(2026, 11, 7));
        writeFirstHours(fixture, community.north(), LocalDate.of(2026, 11, 8), 12);
        // The other plant's later complete day is not this plant's.
        writeDay(fixture, community.south(), LocalDate.of(2026, 11, 9));
        String admin = loginAsCommunityAdmin(community.id());

        latest(community.north().getId(), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2026-11-07T00:00:00+01:00"))
                .andExpect(jsonPath("$.period.endDate").value("2026-11-08T00:00:00+01:00"))
                .andExpect(jsonPath("$.energy.productionKWh").value(closeTo(DAY_KWH, TOLERANCE)))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(24))
                .andExpect(jsonPath("$.coverage.expectedHours").value(24));
    }

    @Test
    @DisplayName("PEM-001 AC2 without a production record in the last 24 months, 200 with null bounds and production")
    void withoutARecordInTheLastTwentyFourMonthsTheBoundsAreNull() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        // Complete, but before the search window.
        writeDay(fixture, community.north(), LocalDate.of(2024, 11, 9));
        writeDay(fixture, community.south(), LocalDate.of(2026, 11, 7));

        latest(community.north().getId(), loginAsCommunityAdmin(community.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value(nullValue()))
                .andExpect(jsonPath("$.period.endDate").value(nullValue()))
                .andExpect(jsonPath("$.energy.productionKWh").value(nullValue()))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(0))
                .andExpect(jsonPath("$.coverage.expectedHours").value(0));
    }

    @Test
    @DisplayName("PEM-001 the first day of the 24 months before today is still a candidate")
    void theFirstDayOfTheSearchWindowIsACandidate() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        writeDay(fixture, community.north(), LocalDate.of(2024, 11, 10));

        latest(community.north().getId(), loginAsCommunityAdmin(community.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2024-11-10T00:00:00+01:00"));
    }

    @Test
    @DisplayName("PEM-001 AC3 a day whose night hours are stored as 0 is complete, a day missing one night hour is not")
    void aDayWithNightZerosIsCompleteAndOneMissingANightHourIsNot() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        writeDay(fixture, community.north(), LocalDate.of(2026, 11, 8));
        // 03:00 missing: every other hour, the night zeros included, is stored.
        writeDayWithout(fixture, community.north(), LocalDate.of(2026, 11, 9), Set.of(3));

        latest(community.north().getId(), loginAsCommunityAdmin(community.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2026-11-08T00:00:00+01:00"))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(24))
                .andExpect(jsonPath("$.energy.productionKWh").value(closeTo(DAY_KWH, TOLERANCE)));
    }

    @Test
    @DisplayName("PEM-001 AC3b the current day is never the latest production day, even with every hour stored")
    void theCurrentDayIsNeverTheLatestProductionDay() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        writeDay(fixture, community.north(), LocalDate.of(2026, 11, 8));
        writeFirstHours(fixture, community.north(), LocalDate.of(2026, 11, 9), 20);
        writeDay(fixture, community.north(), LocalDate.of(2026, 11, 10));

        latest(community.north().getId(), loginAsCommunityAdmin(community.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2026-11-08T00:00:00+01:00"))
                .andExpect(jsonPath("$.period.endDate").value("2026-11-09T00:00:00+01:00"));
    }

    @Test
    @DisplayName("PEM-001 a complete day the clocks go back on has 25 records")
    void aCompleteDayTheClocksGoBackOnHasTwentyFiveRecords() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        writeDay(fixture, community.north(), LocalDate.of(2026, 10, 25));
        writeDayWithout(fixture, community.north(), LocalDate.of(2026, 10, 26), Set.of(23));

        latest(community.north().getId(), loginAsCommunityAdmin(community.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2026-10-25T00:00:00+02:00"))
                .andExpect(jsonPath("$.period.endDate").value("2026-10-26T00:00:00+01:00"))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(25))
                .andExpect(jsonPath("$.coverage.expectedHours").value(25));
    }

    @Test
    @DisplayName("PEM-002 AC4 EVL-001 the production is the plant's records in the period, rounded to 3 decimals")
    void theProductionIsThePlantsRecordsRounded() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        Instant ten = Instant.parse("2026-11-03T09:00:00Z");
        fixture.writeProduction(community.north(), ten, 0.1234f);
        fixture.writeProduction(community.north(), ten.plusSeconds(3600), 0.2345f);
        fixture.writeProduction(community.north(), ten.plusSeconds(7200), 0f);
        fixture.writeProduction(community.south(), ten, 1000f);
        EnergyCommunity other = fixture.createCommunity();
        fixture.writeProduction(other.north(), ten, 1000f);

        explicit(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2026-11-03T00:00:00+01:00", "2026-11-04T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.energy.productionKWh").value(0.358))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(3))
                .andExpect(jsonPath("$.coverage.expectedHours").value(24));
    }

    @Test
    @DisplayName("PEM-002 AC5 a period without records has null production, not 0")
    void aPeriodWithoutRecordsHasNullProduction() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeProduction(community.south(), Instant.parse("2026-11-03T09:00:00Z"), 5f);

        explicit(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2026-11-03T00:00:00+01:00", "2026-11-04T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2026-11-03T00:00:00+01:00"))
                .andExpect(jsonPath("$.energy.productionKWh").value(nullValue()))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(0));
    }

    @Test
    @DisplayName("PEM-002 a period whose records are all 0 has a production of 0")
    void aPeriodOfStoredZerosHasAProductionOfZero() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        hoursOf(LocalDate.of(2026, 11, 3)).subList(0, 6)
                .forEach(hour -> fixture.writeProduction(community.north(), hour, 0f));

        explicit(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2026-11-03T00:00:00+01:00", "2026-11-04T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.energy.productionKWh").value(0.0))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(6));
    }

    @Test
    @DisplayName("EPR-001 AC10 the period is half-open: a record on the start counts, one on the end does not")
    void thePeriodIsHalfOpen() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeProduction(community.north(), Instant.parse("2026-11-02T23:00:00Z"), 1f);
        fixture.writeProduction(community.north(), Instant.parse("2026-11-03T23:00:00Z"), 100f);

        explicit(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2026-11-03T00:00:00+01:00", "2026-11-04T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.energy.productionKWh").value(closeTo(1.0, TOLERANCE)))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(1));
    }

    @Test
    @DisplayName("EPR-002 AC10 an end after the current hour is clipped to it and reported clipped")
    void anEndAfterTheCurrentHourIsClipped() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        explicit(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2026-11-10T00:00:00+01:00", "2026-11-11T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.endDate").value("2026-11-10T10:00:00+01:00"))
                .andExpect(jsonPath("$.coverage.expectedHours").value(10));
    }

    @Test
    @DisplayName("EPR-003 AC10 a start at the start of the current hour is rejected")
    void aStartAtTheCurrentHourIsRejected() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        explicit(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2026-11-10T10:00:00+01:00", "2026-11-10T11:00:00+01:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_START_IN_FUTURE"));
    }

    @Test
    @DisplayName("EPR-004 AC11 a period longer than 24 months is answered with its months limit")
    void aPeriodLongerThanTwentyFourMonthsIsRejected() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        explicit(community.north().getId(), loginAsCommunityAdmin(community.id()),
                "2024-01-01T00:00:00+01:00", "2026-01-02T00:00:00+01:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_TOO_LONG"))
                .andExpect(jsonPath("$.errors[0].params.amount").value("24"))
                .andExpect(jsonPath("$.errors[0].params.unit").value("MONTHS"));
    }

    @Test
    @DisplayName("EPR-001 the latest production day together with a date is a bad request")
    void theLatestProductionDayTogetherWithADateIsABadRequest() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        request(get(PATH, community.north().getId())
                .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.id()))
                .queryParam("period", LATEST)
                .queryParam("startDate", "2026-11-03T00:00:00+01:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_CONFLICTING"));
    }

    @Test
    @DisplayName("EPR-001 without dates or period the period spans the plant's records, to the end of the last hour")
    void withoutDatesOrPeriodThePeriodSpansThePlantsRecords() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeProduction(community.north(), Instant.parse("2026-03-01T08:00:00Z"), 2f);
        fixture.writeProduction(community.north(), Instant.parse("2026-07-20T19:00:00Z"), 3f);
        fixture.writeProduction(community.south(), Instant.parse("2025-01-01T08:00:00Z"), 1000f);

        request(get(PATH, community.north().getId())
                .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2026-03-01T09:00:00+01:00"))
                .andExpect(jsonPath("$.period.endDate").value("2026-07-20T22:00:00+02:00"))
                .andExpect(jsonPath("$.energy.productionKWh").value(closeTo(5.0, TOLERANCE)))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(2));
    }

    @Test
    @DisplayName("PEM-004 AC12 a community admin reads it, a member gets 403, a non-member and a platform admin 404")
    void onlyACommunityAdminReadsIt() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        UUID plantId = community.north().getId();

        latest(plantId, loginAsCommunityAdmin(community.id())).andExpect(status().isOk());
        // Member A owns the plant's own supply, and is still not an admin.
        latest(plantId, loginUser(community.memberA())).andExpect(status().isForbidden());
        latest(plantId, loginAsPartner()).andExpect(status().isNotFound());
        latest(plantId, loginAsDefaultPlatformAdmin()).andExpect(status().isNotFound());
        latest(plantId, loginUser(community.disabledMember())).andExpect(status().isNotFound());
        latest(UUID.randomUUID(), loginAsCommunityAdmin(community.id())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PEM-004 AC13 the metrics carry no CUPS, supply id or member reference")
    void theMetricsCarryNoCupsSupplyIdOrMemberReference() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        writeDay(fixture, community.north(), LocalDate.of(2026, 11, 8));

        String body = latest(community.north().getId(), loginAsCommunityAdmin(community.id()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertNoSupplyOrMember(body, community);
    }

    private ResultActions latest(UUID plantId, String token) throws Exception {
        return request(get(PATH, plantId).header(HttpHeaders.AUTHORIZATION, token).queryParam("period", LATEST));
    }

    private ResultActions explicit(UUID plantId, String token, String startDate, String endDate) throws Exception {
        return request(get(PATH, plantId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate));
    }

    private ResultActions request(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request);
    }
}
