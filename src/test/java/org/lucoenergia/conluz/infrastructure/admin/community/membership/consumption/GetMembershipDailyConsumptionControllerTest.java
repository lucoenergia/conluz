package org.lucoenergia.conluz.infrastructure.admin.community.membership.consumption;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
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
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture.hourlyRecord;
import static org.lucoenergia.conluz.infrastructure.shared.web.KWhJson.assertKWh;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end over a real PostgreSQL and InfluxDB. Each test writes hourly records through the
 * production persistence and reads the series the endpoint builds from them.
 *
 * <p>The tariff in the test profile is the 0.15 EUR/kWh estimate with no VAT, so a day's savings are
 * always {@code self-consumed kWh * 0.15}, and every expected figure below is arithmetic on the
 * records each test writes. Every energy value is exactly representable as a float. The application
 * clock stands at 2026-01-01T00:00Z unless a test moves it.
 */
@Transactional
class GetMembershipDailyConsumptionControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/communities/{communityId}/memberships/{userId}/consumption/daily";
    private static final String METRICS_PATH = "/api/v1/communities/{communityId}/memberships/{userId}/energy-metrics";
    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    /** 10 and 11 January 2024, local. */
    private static final String JANUARY_10 = "2024-01-10T00:00:00+01:00";
    private static final String JANUARY_12 = "2024-01-12T00:00:00+01:00";

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CommunityMembershipJpaRepository communityMembershipJpaRepository;
    @Autowired
    private DatadisConsumptionInfluxFixture influxFixture;

    private final List<String> writtenCups = new ArrayList<>();

    @AfterEach
    void clearWrittenSeries() {
        writtenCups.forEach(influxFixture::clear);
    }

    // --- Folding ---

    /**
     * AC1. On the 10th only the first supply stored records: 10 kWh consumed, 2 self-consumed, 1
     * exported, 2 kWh at 0.15 saving 0.30. The second supply only stored on the 11th.
     */
    @Test
    @DisplayName("MCS-002 AC1 a day on which one of two supplies stored records counts and sums only that supply")
    void aDayOnWhichOneOfTwoSuppliesStoredRecordsSumsOnlyThatSupply() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);
        Supply first = persistSupply(member, community);
        Supply second = persistSupply(member, community);
        write(first, "2024/01/10", "12:00", 6f, 1f, 0.5f);
        write(first, "2024/01/10", "13:00", 4f, 1f, 0.5f);
        write(second, "2024/01/11", "12:00", 3f, 1f, 0f);

        request(community, member, loginUser(member), JANUARY_10, JANUARY_12)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2024/01/10"))
                .andExpect(jsonPath("$[0].time").value("00:00"))
                .andExpect(jsonPath("$[0].supplyCount").value(2))
                .andExpect(jsonPath("$[0].suppliesWithData").value(1))
                .andExpect(jsonPath("$[0].gridImportKWh").value(10.0))
                .andExpect(jsonPath("$[0].selfConsumptionKWh").value(2.0))
                .andExpect(jsonPath("$[0].surplusKWh").value(1.0))
                .andExpect(jsonPath("$[0].assignedProductionKWh").value(3.0))
                .andExpect(jsonPath("$[0].savingsEur").value(0.30))
                .andExpect(jsonPath("$[0].tariffSource").value("ESTIMATE"))
                .andExpect(jsonPath("$[1].date").value("2024/01/11"))
                .andExpect(jsonPath("$[1].suppliesWithData").value(1))
                .andExpect(jsonPath("$[1].gridImportKWh").value(3.0));
    }

    /**
     * AC2. Nothing stored on the 10th, by either supply: the day is still emitted, with null savings
     * and source. On the 11th both supplies stored records carrying zeros: a measured zero.
     */
    @Test
    @DisplayName("MCS-001 AC2 a day with nothing stored has null savings, a day of stored zeros saves 0.00")
    void aDayWithNothingStoredIsNotADayOfStoredZeros() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);
        Supply first = persistSupply(member, community);
        Supply second = persistSupply(member, community);
        write(first, "2024/01/11", "12:00", 0f, 0f, 0f);
        write(second, "2024/01/11", "15:00", 0f, 0f, 0f);

        request(community, member, loginUser(member), JANUARY_10, JANUARY_12)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2024/01/10"))
                .andExpect(jsonPath("$[0].gridImportKWh").value(0))
                .andExpect(jsonPath("$[0].savingsEur").value(nullValue()))
                .andExpect(jsonPath("$[0].tariffSource").value(nullValue()))
                .andExpect(jsonPath("$[0].supplyCount").value(2))
                .andExpect(jsonPath("$[0].suppliesWithData").value(0))
                .andExpect(jsonPath("$[1].suppliesWithData").value(2))
                .andExpect(jsonPath("$[1].tariffSource").value("ESTIMATE"))
                .andExpect(content().string(containsString("\"savingsEur\":0.00")));
    }

    /**
     * The two records of {@code RoundingNoiseDataset}, one per supply, on one day. Rounded supply by
     * supply, grid import would add up to 0.123 + 0.222 = 0.345; summed unrounded and rounded once it
     * is 0.346, as the membership monthly series and energy metrics report it.
     */
    @Test
    @DisplayName("EVL-001 EVL-002 the membership daily series rounds the unrounded sum across supplies, once, to 3 decimals")
    void theDailySeriesRoundsTheUnroundedSumOnce() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);
        Supply first = persistSupply(member, community);
        Supply second = persistSupply(member, community);
        influxFixture.write(List.of(RoundingNoiseDataset.firstRecord(first.getCode())));
        influxFixture.write(List.of(RoundingNoiseDataset.secondRecord(second.getCode())));
        remember(first);
        remember(second);

        String body = request(community, member, loginUser(member), RoundingNoiseDataset.DAY_START,
                RoundingNoiseDataset.DAY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        assertKWh(body, "/0/gridImportKWh", RoundingNoiseDataset.GRID_IMPORT);
        assertKWh(body, "/0/selfConsumptionKWh", RoundingNoiseDataset.SELF_CONSUMPTION);
        assertKWh(body, "/0/surplusKWh", RoundingNoiseDataset.SURPLUS);
        assertKWh(body, "/0/assignedProductionKWh", RoundingNoiseDataset.ASSIGNED_PRODUCTION);
    }

    // --- Days ---

    /**
     * AC3. Records at every hour from 30 March to 1 April 2024: 1 kWh, 1 self-consumed, for the
     * first supply and 0.5 kWh for the second. 31 March, when the clocks go forward, has 23 local
     * hours, so 23 + 11.5 kWh and 23 kWh at 0.15 saving 3.45; its neighbours have 24.
     */
    @Test
    @DisplayName("MCS-001 AC3 the March daylight saving day sums exactly its 23 local hours")
    void theSpringForwardDaySumsItsTwentyThreeHours() throws Exception {
        assertDaylightSavingDay(LocalDate.of(2024, 3, 31), 23);
    }

    /**
     * AC3. 27 October 2024, when the clocks go back, has 25 local hours: 25 + 12.5 kWh, 25 kWh at
     * 0.15 saving 3.75. Both records of the repeated hour are written at their own instants.
     */
    @Test
    @DisplayName("MCS-001 AC3 the October daylight saving day sums exactly its 25 local hours")
    void theFallBackDaySumsItsTwentyFiveHours() throws Exception {
        assertDaylightSavingDay(LocalDate.of(2024, 10, 27), 25);
    }

    /**
     * AC4. Three days of records, uneven across hours and supplies, read through the daily series
     * and through the energy metrics over the same period: the days add up to the period, field by
     * field, within half a unit of the last digit of each day's rounding.
     */
    @Test
    @DisplayName("MCS-002 AC4 the daily buckets of a period add up to the membership energy metrics of that period")
    void theDailyBucketsAddUpToTheEnergyMetrics() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);
        Supply first = persistSupply(member, community);
        Supply second = persistSupply(member, community);
        for (String day : List.of("2024/01/10", "2024/01/11", "2024/01/12")) {
            write(first, day, "09:00", 1.125f, 0.375f, 0.0625f);
            write(first, day, "13:00", 2.25f, 1.5f, 0.75f);
            write(second, day, "13:00", 0.8125f, 0.3125f, 0.125f);
            write(second, day, "21:00", 3.5f, 0f, 0f);
        }
        String start = JANUARY_10;
        String end = "2024-01-13T00:00:00+01:00";
        String token = loginUser(member);

        JsonNode days = json(request(community, member, token, start, end)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3)));
        JsonNode metrics = json(mockMvc.perform(get(METRICS_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .queryParam("startDate", start)
                        .queryParam("endDate", end))
                .andExpect(status().isOk()));

        BigDecimal tolerance = new BigDecimal("0.005").multiply(BigDecimal.valueOf(days.size()));
        assertSumMatches(days, "gridImportKWh", metrics.at("/energy/gridImportKWh"), tolerance);
        assertSumMatches(days, "selfConsumptionKWh", metrics.at("/energy/selfConsumptionKWh"), tolerance);
        assertSumMatches(days, "surplusKWh", metrics.at("/energy/surplusKWh"), tolerance);
        assertSumMatches(days, "assignedProductionKWh", metrics.at("/energy/assignedProductionKWh"), tolerance);
        assertSumMatches(days, "savingsEur", metrics.at("/savings/amountEur"), tolerance);
    }

    /**
     * A period starting at 12:00 on the 10th: the 11:00 record is outside it, the 12:00 and 13:00
     * ones inside. 1 + 2 kWh consumed and self-consumed, 3 kWh at 0.15 saving 0.45.
     */
    @Test
    @DisplayName("MCS-001 EPR-001 a first day starting at 12:00 counts only that day's hours from 12:00, energy and savings")
    void aFirstDayStartingAtNoonCountsOnlyItsHoursFromNoon() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);
        Supply first = persistSupply(member, community);
        Supply second = persistSupply(member, community);
        write(first, "2024/01/10", "11:00", 5f, 5f, 0f);
        write(first, "2024/01/10", "12:00", 1f, 1f, 0f);
        write(second, "2024/01/10", "13:00", 2f, 2f, 0f);

        request(community, member, loginUser(member), "2024-01-10T12:00:00+01:00", "2024-01-11T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value("2024/01/10"))
                .andExpect(jsonPath("$[0].time").value("00:00"))
                .andExpect(jsonPath("$[0].gridImportKWh").value(3.0))
                .andExpect(jsonPath("$[0].selfConsumptionKWh").value(3.0))
                .andExpect(jsonPath("$[0].savingsEur").value(0.45))
                .andExpect(jsonPath("$[0].suppliesWithData").value(2));
    }

    /**
     * The clock stands at 11:30 on 11 January 2024 in Madrid and the requested end is days later.
     * The 11th is the last day, and counts the 09:00 and 10:00 records but not the one in the
     * current hour, 11:00.
     */
    @Test
    @DisplayName("EPR-002 an end in the future emits no day after the clipped end, and today only up to the current hour")
    void anEndInTheFutureIsClippedToTheCurrentHour() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);
        Supply first = persistSupply(member, community);
        Supply second = persistSupply(member, community);
        write(first, "2024/01/10", "12:00", 7f, 0f, 0f);
        write(first, "2024/01/11", "09:00", 1f, 0f, 0f);
        write(second, "2024/01/11", "10:00", 2f, 0f, 0f);
        write(second, "2024/01/11", "11:00", 4f, 0f, 0f);
        String token = loginUser(member);
        travelTo(OffsetDateTime.parse("2024-01-11T11:30:00+01:00").toInstant());

        request(community, member, token, JANUARY_10, "2024-01-15T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2024/01/10"))
                .andExpect(jsonPath("$[0].gridImportKWh").value(7.0))
                .andExpect(jsonPath("$[1].date").value("2024/01/11"))
                .andExpect(jsonPath("$[1].gridImportKWh").value(3.0))
                .andExpect(jsonPath("$[1].suppliesWithData").value(2));
    }

    @Test
    @DisplayName("MCS-001 a membership without supplies gets every day, empty")
    void aMembershipWithoutSuppliesGetsEveryDayEmpty() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);

        request(community, member, loginUser(member), JANUARY_10, JANUARY_12)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2024/01/10"))
                .andExpect(jsonPath("$[1].date").value("2024/01/11"))
                .andExpect(jsonPath("$[1].gridImportKWh").value(0))
                .andExpect(jsonPath("$[1].savingsEur").value(nullValue()))
                .andExpect(jsonPath("$[1].tariffSource").value(nullValue()))
                .andExpect(jsonPath("$[1].supplyCount").value(0))
                .andExpect(jsonPath("$[1].suppliesWithData").value(0));
    }

    // --- Requested periods (AC6, AC7), on a membership without supplies so only the scope decides ---

    @Test
    @DisplayName("EPR-001 AC7 equal bounds are refused as a start that is not before the end")
    void equalBoundsAreRefused() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);

        request(community, member, loginUser(member), JANUARY_10, JANUARY_10)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_START_AFTER_END"));
    }

    /**
     * The clock stands at 01:00 on 1 January 2026 in Madrid: a period starting then has not begun.
     */
    @Test
    @DisplayName("EPR-003 AC7 a start in the current hour is refused")
    void aStartInTheCurrentHourIsRefused() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);

        request(community, member, loginUser(member), "2026-01-01T01:00:00+01:00", "2026-01-01T02:00:00+01:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_START_IN_FUTURE"));
    }

    /**
     * 1 June 2023 to 1 June 2025 is exactly 24 months, 731 days with the leap day of 2024.
     */
    @Test
    @DisplayName("EPR-004 AC6 exactly 24 months is accepted, every day emitted")
    void exactlyTwentyFourMonthsIsAccepted() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);

        request(community, member, loginUser(member), "2023-06-01T00:00:00+02:00", "2025-06-01T00:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(731))
                .andExpect(jsonPath("$[0].date").value("2023/06/01"))
                .andExpect(jsonPath("$[730].date").value("2025/05/31"));
    }

    @Test
    @DisplayName("EPR-004 AC6 24 months and one day is refused as too long")
    void oneDayMoreThanTwentyFourMonthsIsRefused() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);

        request(community, member, loginUser(member), "2023-06-01T00:00:00+02:00", "2025-06-02T00:00:00+02:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_TOO_LONG"));
    }

    @Test
    void aMissingBoundIsABadRequest() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);

        mockMvc.perform(get(PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(member))
                        .queryParam("startDate", JANUARY_10))
                .andExpect(status().isBadRequest());
    }

    // --- Authorization (AC5) ---

    @Test
    @DisplayName("MCS-002 AC5 the member themself can read")
    void theMemberThemselfCanRead() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);

        request(community, member, loginUser(member), JANUARY_10, JANUARY_12).andExpect(status().isOk());
    }

    @Test
    @DisplayName("MCS-002 AC5 an enabled community admin of that community can read")
    void anEnabledCommunityAdminCanRead() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);

        request(community, member, loginAsCommunityAdmin(community.getId()), JANUARY_10, JANUARY_12)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("MEM-031 MCS-002 AC5 a platform admin who is neither the member nor an admin there is answered 404")
    void aPlatformAdminIsAnsweredNotFound() throws Exception {
        String platformAdminToken = loginAsDefaultPlatformAdmin();
        Community community = persistCommunity();
        User member = persistMember(community);

        request(community, member, platformAdminToken, JANUARY_10, JANUARY_12).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("MCS-002 AC5 another member of the same community is answered 404")
    void anotherMemberOfTheSameCommunityIsAnsweredNotFound() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);

        request(community, member, loginAsCommunityMember(community.getId()), JANUARY_10, JANUARY_12)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("MCS-002 AC5 a member of another community is answered 404")
    void aMemberOfAnotherCommunityIsAnsweredNotFound() throws Exception {
        Community community = persistCommunity();
        Community otherCommunity = persistCommunity();
        User member = persistMember(community);

        request(community, member, loginAsCommunityMember(otherCommunity.getId()), JANUARY_10, JANUARY_12)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("MCS-002 AC5 a disabled community admin of that community is answered 404")
    void aDisabledCommunityAdminIsAnsweredNotFound() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);
        User admin = persistUser();
        createMembershipRepository.create(community.getId(), admin.getId(), CommunityRole.COMMUNITY_ADMIN);
        MembershipStateTestSupport.markDisabled(communityMembershipJpaRepository, admin.getId(), community.getId());

        request(community, member, loginUser(admin), JANUARY_10, JANUARY_12).andExpect(status().isNotFound());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);

        mockMvc.perform(get(PATH, community.getId(), member.getId())
                        .queryParam("startDate", JANUARY_10)
                        .queryParam("endDate", JANUARY_12))
                .andExpect(status().isUnauthorized());
    }

    // --- fixtures ---

    /**
     * Writes a record at every hour from local midnight of the day before {@code day} to local
     * midnight of the day after it, for two supplies, and checks the three days: each sums exactly
     * the records of its own local day.
     */
    private void assertDaylightSavingDay(LocalDate day, int hours) throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);
        Supply first = persistSupply(member, community);
        Supply second = persistSupply(member, community);
        List<Instant> everyHour = everyHour(day.minusDays(1), day.plusDays(2));
        writeAt(first, everyHour, 1f, 1f);
        writeAt(second, everyHour, 0.5f, 0f);

        request(community, member, loginUser(member),
                day.minusDays(1).atStartOfDay(ZONE).toOffsetDateTime().toString(),
                day.plusDays(2).atStartOfDay(ZONE).toOffsetDateTime().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].gridImportKWh").value(36.0))
                .andExpect(jsonPath("$[1].date").value(day.toString().replace('-', '/')))
                .andExpect(jsonPath("$[1].gridImportKWh").value(hours * 1.5))
                .andExpect(jsonPath("$[1].selfConsumptionKWh").value((double) hours))
                .andExpect(jsonPath("$[1].savingsEur").value(new BigDecimal(hours).multiply(new BigDecimal("0.15")).doubleValue()))
                .andExpect(jsonPath("$[1].suppliesWithData").value(2))
                .andExpect(jsonPath("$[2].gridImportKWh").value(36.0));
    }

    private static List<Instant> everyHour(LocalDate from, LocalDate toExclusive) {
        Instant end = toExclusive.atStartOfDay(ZONE).toInstant();
        return Stream.iterate(from.atStartOfDay(ZONE).toInstant(), time -> time.isBefore(end),
                time -> time.plus(Duration.ofHours(1))).toList();
    }

    private ResultActions request(Community community, User member, String token, String startDate,
                                  String endDate) throws Exception {
        return mockMvc.perform(get(PATH, community.getId(), member.getId())
                .header(HttpHeaders.AUTHORIZATION, token)
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static void assertSumMatches(JsonNode days, String field, JsonNode expected, BigDecimal tolerance) {
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode day : days) {
            sum = sum.add(day.path(field).decimalValue());
        }
        BigDecimal difference = sum.subtract(expected.decimalValue()).abs();
        BigDecimal total = sum;
        assertTrue(difference.compareTo(tolerance) <= 0,
                () -> field + ": days add up to " + total + ", the energy metrics report " + expected);
    }

    private Community persistCommunity() {
        return createCommunityRepository.create(CommunityMother.random().build());
    }

    private User persistUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        return user;
    }

    private User persistMember(Community community) {
        User member = persistUser();
        createMembershipRepository.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        return member;
    }

    private Supply persistSupply(User owner, Community community) {
        return createSupplyRepository.create(SupplyMother.random(owner).build(), UserId.of(owner.getId()),
                community.getId());
    }

    /**
     * Writes one hourly record for the supply, at a local date and time.
     */
    private void write(Supply supply, String date, String time, Float gridImportKWh, Float selfConsumptionKWh,
                       Float surplusKWh) {
        influxFixture.write(List.of(hourlyRecord(supply.getCode(), date, time, gridImportKWh, selfConsumptionKWh,
                surplusKWh)));
        remember(supply);
    }

    /**
     * Writes one record per instant, so both records of the hour a fall-back repeats can be written.
     */
    private void writeAt(Supply supply, List<Instant> times, Float gridImportKWh, Float selfConsumptionKWh) {
        influxFixture.writeAt(supply.getCode(), times, gridImportKWh, selfConsumptionKWh, 0f);
        remember(supply);
    }

    private void remember(Supply supply) {
        if (!writtenCups.contains(supply.getCode())) {
            writtenCups.add(supply.getCode());
        }
    }
}
