package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import org.apache.commons.lang3.RandomStringUtils;
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
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.Month;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The period conventions every endpoint taking a requested period shares, checked endpoint by
 * endpoint against stored records: supply and membership energy metrics, the supply daily and
 * monthly series and the membership monthly series.
 */
class EnergyPeriodBoundsControllerTest extends BaseControllerTest {

    private static final String SUPPLY_PATH = "/api/v1/supplies/{supplyId}";
    private static final String MEMBERSHIP_PATH = "/api/v1/communities/{communityId}/memberships/{userId}";
    private static final double TOLERANCE = 1e-6;

    /**
     * Well after every period requested below, so that none of them reaches the current hour.
     */
    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private static final String DAY_START = "2026-08-10T00:00:00+02:00";
    private static final String DAY_END = "2026-08-11T00:00:00+02:00";
    private static final String AUGUST_START = "2026-08-01T00:00:00+02:00";
    private static final String AUGUST_END = "2026-09-01T00:00:00+02:00";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private DatadisMonthlyAggregationRepository monthlyAggregationRepository;
    @Autowired
    private DatadisConsumptionInfluxFixture influxFixture;

    private final List<String> writtenCups = new ArrayList<>();

    private Community community;
    private User member;
    private Supply supply;
    private String token;

    @BeforeEach
    void givenAMemberWithOneSupply() throws Exception {
        travelTo(NOW);
        community = createCommunityRepository.create(CommunityMother.random().build());
        member = UserMother.randomUser();
        member.enable();
        createUserRepository.create(member);
        createMembershipRepository.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        String code = "ES" + RandomStringUtils.random(20, false, true);
        writtenCups.add(code);
        supply = createSupplyRepository.create(SupplyMother.random(member).withCode(code).build(),
                UserId.of(member.getId()), community.getId());
        token = loginUser(member);
    }

    @AfterEach
    void clearWrittenSeries() {
        writtenCups.forEach(cups -> {
            influxFixture.clear(cups);
            influxFixture.clearMonthlyAggregates(cups);
        });
    }

    // --- AC1: a day is [00:00, 00:00 of the next day) ---

    @Test
    @DisplayName("EPR-001 AC1 supply energy metrics over a day count the record at its midnight and not the one at the next")
    void supplyEnergyMetricsOverADay() throws Exception {
        givenRecordsAroundTheDay();

        request(supplyPath("/energy-metrics"), DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value(DAY_START))
                .andExpect(jsonPath("$.period.endDate").value(DAY_END))
                .andExpect(jsonPath("$.coverage.expectedHours").value(24))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(2))
                .andExpect(jsonPath("$.energy.gridImportKWh").value(closeTo(3.0, TOLERANCE)));
    }

    @Test
    @DisplayName("EPR-001 AC1 membership energy metrics over a day count the record at its midnight and not the one at the next")
    void membershipEnergyMetricsOverADay() throws Exception {
        givenRecordsAroundTheDay();

        request(membershipPath("/energy-metrics"), DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value(DAY_START))
                .andExpect(jsonPath("$.period.endDate").value(DAY_END))
                .andExpect(jsonPath("$.coverage.expectedHours").value(24))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(2))
                .andExpect(jsonPath("$.energy.gridImportKWh").value(closeTo(3.0, TOLERANCE)));
    }

    @Test
    @DisplayName("EPR-001 AC1 the supply daily series over a day is one bucket without the record at the next midnight")
    void supplyDailySeriesOverADay() throws Exception {
        givenRecordsAroundTheDay();

        request(supplyPath("/consumption/daily"), DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].date").value("2026/08/10"))
                .andExpect(jsonPath("$[0].gridImportKWh").value(closeTo(3.0, TOLERANCE)));
    }

    @Test
    @DisplayName("EPR-001 AC1 the October daylight saving day covers 25 hours on supply and membership energy metrics")
    void theOctoberDaylightSavingDayCoversTwentyFiveHours() throws Exception {
        String start = "2026-10-25T00:00:00+02:00";
        String end = "2026-10-26T00:00:00+01:00";

        request(supplyPath("/energy-metrics"), start, end)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverage.expectedHours").value(25));
        request(membershipPath("/energy-metrics"), start, end)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverage.expectedHours").value(25));
    }

    @Test
    @DisplayName("EPR-001 AC1 the March daylight saving day covers 23 hours on supply and membership energy metrics")
    void theMarchDaylightSavingDayCoversTwentyThreeHours() throws Exception {
        String start = "2026-03-29T00:00:00+01:00";
        String end = "2026-03-30T00:00:00+02:00";

        request(supplyPath("/energy-metrics"), start, end)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverage.expectedHours").value(23));
        request(membershipPath("/energy-metrics"), start, end)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverage.expectedHours").value(23));
    }

    // --- AC2: a month is [00:00 of day 1, 00:00 of day 1 of the next month) ---

    @Test
    @DisplayName("EPR-001 AC2 the supply monthly series over August returns August only")
    void supplyMonthlySeriesOverAugustReturnsAugustOnly() throws Exception {
        givenMonthlyAggregatesForJulyAugustAndSeptember();

        request(supplyPath("/consumption/monthly"), AUGUST_START, AUGUST_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].date").value("2026/08/01"))
                .andExpect(jsonPath("$[0].gridImportKWh").value(closeTo(2.0, TOLERANCE)));
    }

    @Test
    @DisplayName("EPR-001 AC2 the membership monthly series over August returns August only")
    void membershipMonthlySeriesOverAugustReturnsAugustOnly() throws Exception {
        givenMonthlyAggregatesForJulyAugustAndSeptember();

        request(membershipPath("/consumption/monthly"), AUGUST_START, AUGUST_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].date").value("2026/08/01"))
                .andExpect(jsonPath("$[0].gridImportKWh").value(closeTo(2.0, TOLERANCE)));
    }

    @Test
    @DisplayName("EPR-001 AC2 August covers 744 hours on supply and membership energy metrics")
    void augustCoversSevenHundredFortyFourHours() throws Exception {
        request(supplyPath("/energy-metrics"), AUGUST_START, AUGUST_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverage.expectedHours").value(744));
        request(membershipPath("/energy-metrics"), AUGUST_START, AUGUST_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverage.expectedHours").value(744));
    }

    // --- Out of scope: the supply hourly series keeps both bounds inclusive ---

    @Test
    @DisplayName("The supply hourly series still returns the record sitting exactly on endDate")
    void theSupplyHourlySeriesStillIncludesTheRecordOnTheEndBound() throws Exception {
        givenRecordsAroundTheDay();

        request(supplyPath("/consumption/hourly"), DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.date == '2026/08/11' && @.time == '00:00')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.date == '2026/08/10' && @.time == '00:00')]", hasSize(1)));
    }

    // --- fixtures ---

    /**
     * One record at local midnight of 10 August, one at noon and one at midnight of 11 August, each
     * with a distinct grid import, so a sum tells which of them were counted.
     */
    private void givenRecordsAroundTheDay() {
        writeAt(OffsetDateTime.parse("2026-08-10T00:00:00+02:00"), 1f);
        writeAt(OffsetDateTime.parse("2026-08-10T12:00:00+02:00"), 2f);
        writeAt(OffsetDateTime.parse("2026-08-11T00:00:00+02:00"), 4f);
    }

    /**
     * One record per month, aggregated by the production aggregation into a pre-aggregate stamped at
     * local midnight of day 1: 1 kWh in July, 2 in August, 4 in September.
     */
    private void givenMonthlyAggregatesForJulyAugustAndSeptember() {
        writeAt(OffsetDateTime.parse("2026-07-15T12:00:00+02:00"), 1f);
        writeAt(OffsetDateTime.parse("2026-08-15T12:00:00+02:00"), 2f);
        writeAt(OffsetDateTime.parse("2026-09-15T12:00:00+02:00"), 4f);
        for (Month month : List.of(Month.JULY, Month.AUGUST, Month.SEPTEMBER)) {
            monthlyAggregationRepository.aggregateMonthlyConsumption(supply, month, 2026);
        }
    }

    private void writeAt(OffsetDateTime time, float gridImportKWh) {
        influxFixture.writeAt(supply.getCode(), time.toInstant(), gridImportKWh, 0f, 0f);
    }

    private String supplyPath(String suffix) {
        return SUPPLY_PATH.replace("{supplyId}", supply.getId().toString()) + suffix;
    }

    private String membershipPath(String suffix) {
        return MEMBERSHIP_PATH.replace("{communityId}", community.getId().toString())
                .replace("{userId}", member.getId().toString()) + suffix;
    }

    private ResultActions request(String path, String startDate, String endDate) throws Exception {
        return mockMvc.perform(get(path)
                .header(HttpHeaders.AUTHORIZATION, token)
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate));
    }
}
