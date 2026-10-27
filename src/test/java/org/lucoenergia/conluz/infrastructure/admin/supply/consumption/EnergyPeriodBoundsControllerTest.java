package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.time.YearMonth;
import java.time.ZoneId;
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

    private static final String SUPPLY_ENERGY_METRICS = "supply energy metrics";
    private static final String SUPPLY_DAILY = "supply daily series";
    private static final String SUPPLY_MONTHLY = "supply monthly series";
    private static final String MEMBERSHIP_ENERGY_METRICS = "membership energy metrics";
    private static final String MEMBERSHIP_MONTHLY = "membership monthly series";

    /**
     * 2026-10-26T10:30 in Madrid, the day after the clocks went back: the current hour started at
     * 10:00+01:00, after every period the AC1 and AC2 tests request.
     */
    private static final Instant NOW = Instant.parse("2026-10-26T09:30:00Z");

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

    // --- AC7: a period the server resolves is half-open too ---

    @Test
    @DisplayName("EPR-001 AC7 the latest published month resolved to August is [08-01, 09-01) on supply and membership energy metrics")
    void theLatestPublishedMonthIsHalfOpen() throws Exception {
        influxFixture.writePublishedZeros(supply.getCode(), YearMonth.of(2026, 8), ZoneId.of("Europe/Madrid"));

        for (String path : List.of(supplyPath("/energy-metrics"), membershipPath("/energy-metrics"))) {
            mockMvc.perform(get(path)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .queryParam("period", "LATEST_PUBLISHED_MONTH"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.period.startDate").value(AUGUST_START))
                    .andExpect(jsonPath("$.period.endDate").value(AUGUST_END))
                    .andExpect(jsonPath("$.coverage.expectedHours").value(744))
                    .andExpect(jsonPath("$.coverage.hoursWithData").value(744));
        }
    }

    // --- AC4: an end after the start of the current hour is clipped to it ---

    private static final String CLIPPED_END = "2026-10-26T10:00:00+01:00";
    private static final String FUTURE_END = "2026-11-01T00:00:00+01:00";

    @Test
    @DisplayName("EPR-002 AC4 supply energy metrics clip the end to the current hour, report it and count no hour from it")
    void supplyEnergyMetricsClipTheEndToTheCurrentHour() throws Exception {
        givenRecordsAroundTheCurrentHour();

        request(supplyPath("/energy-metrics"), "2026-10-26T00:00:00+01:00", FUTURE_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.endDate").value(CLIPPED_END))
                .andExpect(jsonPath("$.coverage.expectedHours").value(10))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(1))
                .andExpect(jsonPath("$.energy.gridImportKWh").value(closeTo(1.0, TOLERANCE)));
    }

    @Test
    @DisplayName("EPR-002 AC4 membership energy metrics clip the end to the current hour, report it and count no hour from it")
    void membershipEnergyMetricsClipTheEndToTheCurrentHour() throws Exception {
        givenRecordsAroundTheCurrentHour();

        request(membershipPath("/energy-metrics"), "2026-10-26T00:00:00+01:00", FUTURE_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.endDate").value(CLIPPED_END))
                .andExpect(jsonPath("$.coverage.expectedHours").value(10))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(1))
                .andExpect(jsonPath("$.energy.gridImportKWh").value(closeTo(1.0, TOLERANCE)));
    }

    @Test
    @DisplayName("EPR-002 AC4 the supply daily series emits no bucket after the day of the current hour")
    void theSupplyDailySeriesEmitsNoBucketAfterTheDayOfTheCurrentHour() throws Exception {
        givenRecordsAroundTheCurrentHour();

        request(supplyPath("/consumption/daily"), "2026-10-24T00:00:00+02:00", FUTURE_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[2].date").value("2026/10/26"))
                // The 09:00 record only: the one at 10:00 is on the clipped end.
                .andExpect(jsonPath("$[2].gridImportKWh").value(closeTo(1.0, TOLERANCE)));
    }

    @Test
    @DisplayName("EPR-002 AC4 the supply monthly series emits no month starting after the current hour")
    void theSupplyMonthlySeriesEmitsNoMonthStartingAfterTheCurrentHour() throws Exception {
        givenMonthlyAggregatesForSeptemberOctoberAndNovember();

        request(supplyPath("/consumption/monthly"), "2026-09-01T00:00:00+02:00", "2026-12-01T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].date").value("2026/09/01"))
                .andExpect(jsonPath("$[1].date").value("2026/10/01"));
    }

    @Test
    @DisplayName("EPR-002 AC4 the membership monthly series emits no month starting after the current hour")
    void theMembershipMonthlySeriesEmitsNoMonthStartingAfterTheCurrentHour() throws Exception {
        givenMonthlyAggregatesForSeptemberOctoberAndNovember();

        request(membershipPath("/consumption/monthly"), "2026-09-01T00:00:00+02:00", "2026-12-01T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].date").value("2026/09/01"))
                .andExpect(jsonPath("$[1].date").value("2026/10/01"));
    }

    // --- AC3: a start not strictly before the end ---

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {SUPPLY_ENERGY_METRICS, SUPPLY_DAILY, SUPPLY_MONTHLY, MEMBERSHIP_ENERGY_METRICS,
            MEMBERSHIP_MONTHLY})
    @DisplayName("EPR-001 AC3 equal bounds are answered 400 ENERGY_METRICS_PERIOD_START_AFTER_END")
    void equalBoundsAreRejected(String endpoint) throws Exception {
        assertRejected(endpoint, DAY_START, DAY_START, "ENERGY_METRICS_PERIOD_START_AFTER_END");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {SUPPLY_ENERGY_METRICS, SUPPLY_DAILY, SUPPLY_MONTHLY, MEMBERSHIP_ENERGY_METRICS,
            MEMBERSHIP_MONTHLY})
    @DisplayName("EPR-001 AC3 inverted bounds are answered 400 ENERGY_METRICS_PERIOD_START_AFTER_END")
    void invertedBoundsAreRejected(String endpoint) throws Exception {
        assertRejected(endpoint, DAY_END, DAY_START, "ENERGY_METRICS_PERIOD_START_AFTER_END");
    }

    // --- AC5: a start at or after the start of the current hour ---

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {SUPPLY_ENERGY_METRICS, SUPPLY_DAILY, SUPPLY_MONTHLY, MEMBERSHIP_ENERGY_METRICS,
            MEMBERSHIP_MONTHLY})
    @DisplayName("EPR-003 AC5 a start at the start of the current hour is answered 400 ENERGY_METRICS_PERIOD_START_IN_FUTURE")
    void aStartAtTheStartOfTheCurrentHourIsRejected(String endpoint) throws Exception {
        assertRejected(endpoint, "2026-10-26T10:00:00+01:00", "2026-11-01T00:00:00+01:00",
                "ENERGY_METRICS_PERIOD_START_IN_FUTURE");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {SUPPLY_ENERGY_METRICS, SUPPLY_DAILY, SUPPLY_MONTHLY, MEMBERSHIP_ENERGY_METRICS,
            MEMBERSHIP_MONTHLY})
    @DisplayName("EPR-003 AC5 a start tomorrow is answered 400 ENERGY_METRICS_PERIOD_START_IN_FUTURE")
    void aStartTomorrowIsRejected(String endpoint) throws Exception {
        assertRejected(endpoint, "2026-10-27T00:00:00+01:00", "2026-10-28T00:00:00+01:00",
                "ENERGY_METRICS_PERIOD_START_IN_FUTURE");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {SUPPLY_ENERGY_METRICS, SUPPLY_DAILY, SUPPLY_MONTHLY, MEMBERSHIP_ENERGY_METRICS,
            MEMBERSHIP_MONTHLY})
    @DisplayName("EPR-003 AC5 a start in the previous hour is accepted")
    void aStartInThePreviousHourIsAccepted(String endpoint) throws Exception {
        request(path(endpoint), "2026-10-26T09:00:00+01:00", "2026-10-26T10:00:00+01:00")
                .andExpect(status().isOk());
    }

    // --- AC6: at most 24 calendar months, on the requested bounds ---

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {SUPPLY_ENERGY_METRICS, SUPPLY_DAILY, SUPPLY_MONTHLY, MEMBERSHIP_ENERGY_METRICS,
            MEMBERSHIP_MONTHLY})
    @DisplayName("EPR-004 AC6 exactly 24 calendar months are accepted")
    void exactlyTwentyFourMonthsAreAccepted(String endpoint) throws Exception {
        request(path(endpoint), "2024-10-01T00:00:00+02:00", "2026-10-01T00:00:00+02:00")
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {SUPPLY_ENERGY_METRICS, SUPPLY_DAILY, SUPPLY_MONTHLY, MEMBERSHIP_ENERGY_METRICS,
            MEMBERSHIP_MONTHLY})
    @DisplayName("EPR-004 AC6 24 calendar months and a day are answered 400 ENERGY_METRICS_PERIOD_TOO_LONG")
    void twentyFourMonthsAndADayAreRejected(String endpoint) throws Exception {
        assertRejected(endpoint, "2024-10-01T00:00:00+02:00", "2026-10-02T00:00:00+02:00",
                "ENERGY_METRICS_PERIOD_TOO_LONG");
    }

    /**
     * Clipping the end to the current hour would bring this period under 24 months; the cap is
     * evaluated on the bounds as requested.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {SUPPLY_ENERGY_METRICS, SUPPLY_DAILY, SUPPLY_MONTHLY, MEMBERSHIP_ENERGY_METRICS,
            MEMBERSHIP_MONTHLY})
    @DisplayName("EPR-004 AC6 24 months and a day ending in the future are answered 400 ENERGY_METRICS_PERIOD_TOO_LONG")
    void twentyFourMonthsAndADayEndingInTheFutureAreRejected(String endpoint) throws Exception {
        assertRejected(endpoint, "2024-10-26T00:00:00+02:00", "2026-10-27T00:00:00+01:00",
                "ENERGY_METRICS_PERIOD_TOO_LONG");
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

    /**
     * A record on each of the two days before the current hour's, one at 09:00 on its day and one
     * at 10:00, exactly on the start of the current hour. A record stored for an hour that has not
     * fully happened is unusual, but it is what shows the clipped end is exclusive.
     */
    private void givenRecordsAroundTheCurrentHour() {
        writeAt(OffsetDateTime.parse("2026-10-24T12:00:00+02:00"), 4f);
        writeAt(OffsetDateTime.parse("2026-10-25T12:00:00+01:00"), 4f);
        writeAt(OffsetDateTime.parse("2026-10-26T09:00:00+01:00"), 1f);
        writeAt(OffsetDateTime.parse("2026-10-26T10:00:00+01:00"), 2f);
    }

    /**
     * Pre-aggregates for September, October and November: November's is stamped at local midnight
     * of 1 November, after the current hour.
     */
    private void givenMonthlyAggregatesForSeptemberOctoberAndNovember() {
        writeAt(OffsetDateTime.parse("2026-09-15T12:00:00+02:00"), 1f);
        writeAt(OffsetDateTime.parse("2026-10-15T12:00:00+02:00"), 2f);
        writeAt(OffsetDateTime.parse("2026-11-15T12:00:00+01:00"), 4f);
        for (Month month : List.of(Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER)) {
            monthlyAggregationRepository.aggregateMonthlyConsumption(supply, month, 2026);
        }
    }

    private void writeAt(OffsetDateTime time, float gridImportKWh) {
        influxFixture.writeAt(supply.getCode(), time.toInstant(), gridImportKWh, 0f, 0f);
    }

    private void assertRejected(String endpoint, String startDate, String endDate, String code) throws Exception {
        request(path(endpoint), startDate, endDate)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(code))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private String path(String endpoint) {
        return switch (endpoint) {
            case SUPPLY_ENERGY_METRICS -> supplyPath("/energy-metrics");
            case SUPPLY_DAILY -> supplyPath("/consumption/daily");
            case SUPPLY_MONTHLY -> supplyPath("/consumption/monthly");
            case MEMBERSHIP_ENERGY_METRICS -> membershipPath("/energy-metrics");
            case MEMBERSHIP_MONTHLY -> membershipPath("/consumption/monthly");
            default -> throw new IllegalArgumentException(endpoint);
        };
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
