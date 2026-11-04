package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The supply hourly series against stored records: the period it reads, the hours it emits, and the
 * 31-day cap it is held to (#545).
 */
class SupplyHourlySeriesControllerTest extends BaseControllerTest {

    private static final double TOLERANCE = 1e-6;

    /**
     * 2026-11-04T10:30 in Madrid: the current hour started at 10:00+01:00, after every whole day the
     * tests request.
     */
    private static final Instant NOW = Instant.parse("2026-11-04T09:30:00Z");

    private static final String SEPTEMBER_FIRST = "2026-09-01T00:00:00+02:00";
    private static final String SEPTEMBER_SECOND = "2026-09-02T00:00:00+02:00";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private DatadisConsumptionInfluxFixture influxFixture;

    private Supply supply;
    private String token;

    @BeforeEach
    void givenAMemberWithOneSupply() throws Exception {
        travelTo(NOW);
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User member = UserMother.randomUser();
        member.enable();
        createUserRepository.create(member);
        createMembershipRepository.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        supply = createSupplyRepository.create(SupplyMother.random(member).build(),
                UserId.of(member.getId()), community.getId());
        token = loginUser(member);
    }

    @AfterEach
    void clearWrittenSeries() {
        influxFixture.clear(supply.getCode());
    }

    // --- AC1: a day is its 24 hours, without the record at the next midnight ---

    @Test
    @DisplayName("SCS-001 AC1 a whole day is 24 hours from 00:00, without the record at the next midnight")
    void aWholeDayIsTwentyFourHours() throws Exception {
        writeAt("2026-09-01T00:00:00+02:00", 1f, 0f);
        writeAt("2026-09-01T12:00:00+02:00", 2f, 0f);
        writeAt("2026-09-02T00:00:00+02:00", 4f, 0f);

        request(SEPTEMBER_FIRST, SEPTEMBER_SECOND)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(24)))
                .andExpect(jsonPath("$[*].date", everyItem(is("2026/09/01"))))
                .andExpect(jsonPath("$[*].time", contains(localHoursOfADay())))
                .andExpect(jsonPath("$[0].consumptionKWh").value(closeTo(1.0, TOLERANCE)))
                .andExpect(jsonPath("$[12].consumptionKWh").value(closeTo(2.0, TOLERANCE)))
                .andExpect(jsonPath("$[23].consumptionKWh").value(closeTo(0.0, TOLERANCE)));
    }

    // --- AC2: the days the clocks change ---

    @Test
    @DisplayName("SCS-001 AC2 the March daylight saving day is 23 hours, without the skipped 02:00")
    void theMarchDaylightSavingDayIsTwentyThreeHours() throws Exception {
        writeAt("2026-03-29T03:00:00+02:00", 1f, 0f);

        request("2026-03-29T00:00:00+01:00", "2026-03-30T00:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(23)))
                .andExpect(jsonPath("$[1].time").value("01:00"))
                .andExpect(jsonPath("$[2].time").value("03:00"))
                .andExpect(jsonPath("$[2].consumptionKWh").value(closeTo(1.0, TOLERANCE)))
                .andExpect(jsonPath("$[22].time").value("23:00"));
    }

    /**
     * Both records of the hour the clocks repeat are written at their own instant, with the fixture
     * that places either one on its own: this is a query test, not an ingestion one (#322).
     */
    @Test
    @DisplayName("SCS-001 AC2 the October daylight saving day is 25 hours, its repeated 02:00 twice")
    void theOctoberDaylightSavingDayIsTwentyFiveHours() throws Exception {
        writeAt("2026-10-25T02:00:00+02:00", 1f, 0f);
        writeAt("2026-10-25T02:00:00+01:00", 2f, 0f);

        request("2026-10-25T00:00:00+02:00", "2026-10-26T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(25)))
                .andExpect(jsonPath("$[2].time").value("02:00"))
                .andExpect(jsonPath("$[2].consumptionKWh").value(closeTo(1.0, TOLERANCE)))
                .andExpect(jsonPath("$[3].time").value("02:00"))
                .andExpect(jsonPath("$[3].consumptionKWh").value(closeTo(2.0, TOLERANCE)))
                .andExpect(jsonPath("$[24].time").value("23:00"));
    }

    // --- AC3: a measured zero and a gap ---

    @Test
    @DisplayName("SCS-001 AC3 an hour of stored zeros has its obtain method and an hour with no record has none")
    void aMeasuredZeroHasAnObtainMethodAndAGapHasNone() throws Exception {
        writeAt("2026-09-01T10:00:00+02:00", 0f, 0f);
        writeAt("2026-09-01T12:00:00+02:00", 1f, 0f);

        request(SEPTEMBER_FIRST, SEPTEMBER_SECOND)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[10].time").value("10:00"))
                .andExpect(jsonPath("$[10].obtainMethod").value("Real"))
                .andExpect(jsonPath("$[10].consumptionKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$[11].time").value("11:00"))
                .andExpect(jsonPath("$[11].obtainMethod").value(nullValue()))
                .andExpect(jsonPath("$[11].consumptionKWh").value(closeTo(0.0, TOLERANCE)));
    }

    /**
     * With no record anywhere in the period the store returns no row at all, so every hour here comes
     * from the series itself, not from the store.
     */
    @Test
    @DisplayName("SCS-001 AC3 a day without any record is still its 24 hours, each with zero energy and no obtain method")
    void aDayWithoutAnyRecordIsStillTwentyFourHours() throws Exception {
        request(SEPTEMBER_FIRST, SEPTEMBER_SECOND)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(24)))
                .andExpect(jsonPath("$[*].time", contains(localHoursOfADay())))
                .andExpect(jsonPath("$[*].cups", everyItem(is(supply.getCode()))))
                .andExpect(jsonPath("$[*].obtainMethod", everyItem(nullValue())))
                .andExpect(jsonPath("$[*].consumptionKWh", everyItem(is(0.0))))
                .andExpect(jsonPath("$[*].selfConsumptionEnergyKWh", everyItem(is(0.0))))
                .andExpect(jsonPath("$[*].surplusEnergyKWh", everyItem(is(0.0))));
    }

    // --- EPR-002: an end in the future is clipped to the current hour ---

    @Test
    @DisplayName("EPR-002 the hourly series emits no hour starting at or after the start of the current hour")
    void theHourlySeriesEmitsNoHourFromTheCurrentOne() throws Exception {
        writeAt("2026-11-04T09:00:00+01:00", 1f, 0f);
        writeAt("2026-11-04T10:00:00+01:00", 2f, 0f);

        request("2026-11-04T00:00:00+01:00", "2026-11-05T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[9].time").value("09:00"))
                .andExpect(jsonPath("$[9].consumptionKWh").value(closeTo(1.0, TOLERANCE)));
    }

    // --- EPR-005: at most 31 calendar days ---

    @Test
    @DisplayName("EPR-005 AC5 exactly 31 calendar days are accepted")
    void exactlyThirtyOneDaysAreAccepted() throws Exception {
        request(SEPTEMBER_FIRST, "2026-10-02T00:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(31 * 24)));
    }

    @Test
    @DisplayName("EPR-005 AC5 31 calendar days and one hour are answered 400 ENERGY_METRICS_PERIOD_TOO_LONG")
    void thirtyOneDaysAndAnHourAreRejected() throws Exception {
        request(SEPTEMBER_FIRST, "2026-10-02T01:00:00+02:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_TOO_LONG"))
                // The limit the message states is the hourly one, not the 24 months of the other operations.
                .andExpect(jsonPath("$.message", containsString(" 31 ")));
    }

    /**
     * October 2026 is 31 calendar days of 745 hours in Madrid: the clocks go back on the 25th, so the
     * cap is counted in local days, not in 24-hour steps.
     */
    @Test
    @DisplayName("EPR-005 the 31 calendar days are counted in the period's time zone")
    void theDaysAreCountedInThePeriodsZone() throws Exception {
        request("2026-10-01T00:00:00+02:00", "2026-11-01T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(745)));
        request("2026-10-01T00:00:00+02:00", "2026-11-01T01:00:00+01:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_TOO_LONG"));
    }

    /**
     * The end clipped to the current hour would be within 31 days of the start, but the cap is
     * evaluated on the bounds as requested.
     */
    @Test
    @DisplayName("EPR-005 the cap is evaluated on the requested end, even when it is in the future")
    void theCapIsEvaluatedOnTheRequestedEnd() throws Exception {
        request("2026-10-05T10:00:00+02:00", "2026-11-05T11:00:00+01:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_TOO_LONG"));
    }

    // --- fixtures ---

    private static String[] localHoursOfADay() {
        return IntStream.range(0, 24).mapToObj(hour -> String.format("%02d:00", hour)).toArray(String[]::new);
    }

    private void writeAt(String time, float gridImportKWh, float selfConsumptionKWh) {
        influxFixture.writeAt(supply.getCode(), OffsetDateTime.parse(time).toInstant(), gridImportKWh,
                selfConsumptionKWh, 0f);
    }

    private ResultActions request(String startDate, String endDate) throws Exception {
        return mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/consumption/hourly")
                .header(HttpHeaders.AUTHORIZATION, token)
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate));
    }
}
