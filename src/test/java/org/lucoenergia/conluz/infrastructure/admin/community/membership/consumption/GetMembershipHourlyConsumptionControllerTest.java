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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.shared.web.KWhJson.assertKWh;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end over a real PostgreSQL and InfluxDB. Each test writes hourly records at the instant
 * their hour starts and reads the series the endpoint builds from them.
 *
 * <p>The tariff in the test profile is the 0.15 EUR/kWh estimate with no VAT, so an hour's savings
 * are always {@code self-consumed kWh * 0.15}, and every expected figure below is arithmetic on the
 * records each test writes. Every energy value is exactly representable as a float. The application
 * clock stands at 2026-01-01T00:00Z, 01:00 in Madrid.
 */
@Transactional
class GetMembershipHourlyConsumptionControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/communities/{communityId}/memberships/{userId}/consumption/hourly";
    private static final String METRICS_PATH = "/api/v1/communities/{communityId}/memberships/{userId}/energy-metrics";

    /** 10 January 2024, a whole local day in winter time. */
    private static final String JANUARY_10 = "2024-01-10T00:00:00+01:00";
    private static final String JANUARY_11 = "2024-01-11T00:00:00+01:00";

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
     * AC1. At 13:00 only the first supply stored a record: 6 kWh consumed, 2 self-consumed, 1
     * exported, 2 kWh at 0.15 saving 0.30. The second supply only stored at 14:00.
     */
    @Test
    @DisplayName("MCS-003 AC1 an hour in which one of two supplies stored a record counts and sums only that supply")
    void anHourInWhichOneOfTwoSuppliesStoredARecordSumsOnlyThatSupply() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);
        Supply first = persistSupply(member, community);
        Supply second = persistSupply(member, community);
        writeAt(first, "2024-01-10T13:00:00+01:00", 6f, 2f, 1f);
        writeAt(second, "2024-01-10T14:00:00+01:00", 3f, 1f, 0f);

        request(community, member, loginUser(member), JANUARY_10, JANUARY_11)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(24))
                .andExpect(jsonPath("$[13].startsAt").value("2024-01-10T13:00:00+01:00"))
                .andExpect(jsonPath("$[13].date").value("2024/01/10"))
                .andExpect(jsonPath("$[13].time").value("13:00"))
                .andExpect(jsonPath("$[13].supplyCount").value(2))
                .andExpect(jsonPath("$[13].suppliesWithData").value(1))
                .andExpect(jsonPath("$[13].gridImportKWh").value(6.0))
                .andExpect(jsonPath("$[13].selfConsumptionKWh").value(2.0))
                .andExpect(jsonPath("$[13].surplusKWh").value(1.0))
                .andExpect(jsonPath("$[13].assignedProductionKWh").value(3.0))
                .andExpect(jsonPath("$[13].savingsEur").value(0.30))
                .andExpect(jsonPath("$[13].tariffSource").value("ESTIMATE"))
                .andExpect(jsonPath("$[14].suppliesWithData").value(1))
                .andExpect(jsonPath("$[14].gridImportKWh").value(3.0));
    }

    /**
     * AC2. Nothing stored at 10:00, by either supply: the hour is still emitted, with null savings
     * and source. At 11:00 both supplies stored records carrying zeros: a measured zero.
     */
    @Test
    @DisplayName("MCS-003 AC2 an hour with nothing stored has null savings, an hour of stored zeros saves 0.00")
    void anHourWithNothingStoredIsNotAnHourOfStoredZeros() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);
        Supply first = persistSupply(member, community);
        Supply second = persistSupply(member, community);
        writeAt(first, "2024-01-10T11:00:00+01:00", 0f, 0f, 0f);
        writeAt(second, "2024-01-10T11:00:00+01:00", 0f, 0f, 0f);

        request(community, member, loginUser(member), "2024-01-10T10:00:00+01:00", "2024-01-10T12:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].startsAt").value("2024-01-10T10:00:00+01:00"))
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
     * The two records of {@code RoundingNoiseDataset}, one per supply, in the same hour. Rounded
     * supply by supply, grid import would add up to 0.123 + 0.222 = 0.345; summed unrounded and
     * rounded once it is 0.346.
     */
    @Test
    @DisplayName("MCS-003 EVL-001 EVL-002 an hour rounds the unrounded sum across supplies, once, to 3 decimals")
    void anHourRoundsTheUnroundedSumOnce() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);
        Supply first = persistSupply(member, community);
        Supply second = persistSupply(member, community);
        String hour = "2024-02-10T10:00:00+01:00";
        writeAt(first, hour, RoundingNoiseDataset.FIRST_GRID_IMPORT, RoundingNoiseDataset.FIRST_SELF_CONSUMPTION,
                RoundingNoiseDataset.FIRST_SURPLUS);
        writeAt(second, hour, RoundingNoiseDataset.SECOND_GRID_IMPORT, RoundingNoiseDataset.SECOND_SELF_CONSUMPTION,
                RoundingNoiseDataset.SECOND_SURPLUS);

        String body = request(community, member, loginUser(member), hour, "2024-02-10T11:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].suppliesWithData").value(2))
                .andReturn().getResponse().getContentAsString();

        assertKWh(body, "/0/gridImportKWh", RoundingNoiseDataset.GRID_IMPORT);
        assertKWh(body, "/0/selfConsumptionKWh", RoundingNoiseDataset.SELF_CONSUMPTION);
        assertKWh(body, "/0/surplusKWh", RoundingNoiseDataset.SURPLUS);
        assertKWh(body, "/0/assignedProductionKWh", RoundingNoiseDataset.ASSIGNED_PRODUCTION);
    }

    // --- Hours ---

    /**
     * AC3. 31 March 2024, when the clocks go forward in Madrid, has 23 hours: no 02:00, and every
     * {@code startsAt} distinct.
     */
    @Test
    @DisplayName("MCS-003 AC3 the March daylight saving day has 23 hours, each with its own startsAt")
    void theSpringForwardDayHasTwentyThreeHours() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);
        persistSupply(member, community);

        JsonNode hours = json(request(community, member, loginUser(member),
                "2024-03-31T00:00:00+01:00", "2024-04-01T00:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(23))
                .andExpect(jsonPath("$[1].startsAt").value("2024-03-31T01:00:00+01:00"))
                .andExpect(jsonPath("$[2].startsAt").value("2024-03-31T03:00:00+02:00"))
                .andExpect(jsonPath("$[2].time").value("03:00")));

        assertDistinctStarts(hours);
    }

    /**
     * AC3. 27 October 2024, when the clocks go back in Madrid, has 25 hours. Both records of the
     * repeated 02:00 are written at their own instant, with different figures, and each hour keeps its
     * own: matched by the instant, not by the local label they share.
     */
    @Test
    @DisplayName("MCS-003 AC3 the October daylight saving day has 25 hours, its repeated 02:00 twice with its own figures")
    void theFallBackDayHasTwentyFiveHours() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);
        Supply first = persistSupply(member, community);
        Supply second = persistSupply(member, community);
        writeAt(first, "2024-10-27T02:00:00+02:00", 1f, 1f, 0f);
        writeAt(first, "2024-10-27T02:00:00+01:00", 2f, 0f, 0f);
        writeAt(second, "2024-10-27T02:00:00+01:00", 4f, 0f, 0f);

        JsonNode hours = json(request(community, member, loginUser(member),
                "2024-10-27T00:00:00+02:00", "2024-10-28T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(25))
                .andExpect(jsonPath("$[2].startsAt").value("2024-10-27T02:00:00+02:00"))
                .andExpect(jsonPath("$[2].date").value("2024/10/27"))
                .andExpect(jsonPath("$[2].time").value("02:00"))
                .andExpect(jsonPath("$[2].gridImportKWh").value(1.0))
                .andExpect(jsonPath("$[2].suppliesWithData").value(1))
                .andExpect(jsonPath("$[2].savingsEur").value(0.15))
                .andExpect(jsonPath("$[3].startsAt").value("2024-10-27T02:00:00+01:00"))
                .andExpect(jsonPath("$[3].date").value("2024/10/27"))
                .andExpect(jsonPath("$[3].time").value("02:00"))
                .andExpect(jsonPath("$[3].gridImportKWh").value(6.0))
                .andExpect(jsonPath("$[3].suppliesWithData").value(2))
                .andExpect(jsonPath("$[3].savingsEur").value(0.0)));

        assertDistinctStarts(hours);
    }

    /**
     * AC4. One day of records, uneven across hours and supplies, read through the hourly series and
     * through the energy metrics over the same day: the hours add up to the day, field by field,
     * within half a unit of the last digit of each hour's rounding.
     */
    @Test
    @DisplayName("MCS-003 AC4 the hourly buckets of a day add up to the membership energy metrics of that day")
    void theHourlyBucketsAddUpToTheEnergyMetrics() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);
        Supply first = persistSupply(member, community);
        Supply second = persistSupply(member, community);
        writeAt(first, "2024-01-10T10:00:00+01:00", 1.125f, 0.375f, 0.0625f);
        writeAt(first, "2024-01-10T14:00:00+01:00", 2.25f, 1.5f, 0.75f);
        writeAt(second, "2024-01-10T14:00:00+01:00", 0.8125f, 0.3125f, 0.125f);
        writeAt(second, "2024-01-10T22:00:00+01:00", 3.5f, 0f, 0f);
        String token = loginUser(member);

        JsonNode hours = json(request(community, member, token, JANUARY_10, JANUARY_11)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(24)));
        JsonNode metrics = json(mockMvc.perform(get(METRICS_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .queryParam("startDate", JANUARY_10)
                        .queryParam("endDate", JANUARY_11))
                .andExpect(status().isOk()));

        BigDecimal tolerance = new BigDecimal("0.005").multiply(BigDecimal.valueOf(hours.size()));
        assertSumMatches(hours, "gridImportKWh", metrics.at("/energy/gridImportKWh"), tolerance);
        assertSumMatches(hours, "selfConsumptionKWh", metrics.at("/energy/selfConsumptionKWh"), tolerance);
        assertSumMatches(hours, "surplusKWh", metrics.at("/energy/surplusKWh"), tolerance);
        assertSumMatches(hours, "assignedProductionKWh", metrics.at("/energy/assignedProductionKWh"), tolerance);
        assertSumMatches(hours, "savingsEur", metrics.at("/savings/amountEur"), tolerance);
    }

    @Test
    @DisplayName("MCS-003 a membership without supplies gets every hour, empty")
    void aMembershipWithoutSuppliesGetsEveryHourEmpty() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);

        request(community, member, loginUser(member), JANUARY_10, JANUARY_11)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(24))
                .andExpect(jsonPath("$[0].startsAt").value("2024-01-10T00:00:00+01:00"))
                .andExpect(jsonPath("$[23].startsAt").value("2024-01-10T23:00:00+01:00"))
                .andExpect(jsonPath("$[23].gridImportKWh").value(0))
                .andExpect(jsonPath("$[23].savingsEur").value(nullValue()))
                .andExpect(jsonPath("$[23].tariffSource").value(nullValue()))
                .andExpect(jsonPath("$[23].supplyCount").value(0))
                .andExpect(jsonPath("$[23].suppliesWithData").value(0));
    }

    // --- Requested periods (AC6), on a membership without supplies so only the scope decides ---

    /**
     * 1 November to 2 December 2025: exactly 31 calendar days, all in winter time.
     */
    @Test
    @DisplayName("EPR-005 MCS-003 AC6 exactly 31 calendar days are accepted, every hour emitted")
    void exactlyThirtyOneDaysAreAccepted() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);

        request(community, member, loginUser(member), "2025-11-01T00:00:00+01:00", "2025-12-02T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(31 * 24));
    }

    @Test
    @DisplayName("EPR-005 MCS-003 AC6 31 calendar days and one hour are answered 400 ENERGY_METRICS_PERIOD_TOO_LONG")
    void thirtyOneDaysAndOneHourAreRefused() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);

        request(community, member, loginUser(member), "2025-11-01T00:00:00+01:00", "2025-12-02T01:00:00+01:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_TOO_LONG"))
                // The limit the message states is the hourly one, not the 24 months of the other operations.
                .andExpect(jsonPath("$.message", containsString(" 31 ")));
    }

    @Test
    @DisplayName("EPR-001 MCS-003 equal bounds are refused as a start that is not before the end")
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
    @DisplayName("EPR-003 MCS-003 a start in the current hour is refused")
    void aStartInTheCurrentHourIsRefused() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);

        request(community, member, loginUser(member), "2026-01-01T01:00:00+01:00", "2026-01-01T02:00:00+01:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_START_IN_FUTURE"));
    }

    /**
     * The current hour started at 01:00 on 1 January 2026 in Madrid: the series stops at the hour
     * before it, 25 hours after the start.
     */
    @Test
    @DisplayName("EPR-002 MCS-003 a future end emits no hour at or after the start of the current hour")
    void aFutureEndEmitsNoHourFromTheCurrentOne() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);

        request(community, member, loginUser(member), "2025-12-31T00:00:00+01:00", "2026-01-02T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(25))
                .andExpect(jsonPath("$[24].startsAt").value("2026-01-01T00:00:00+01:00"));
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
    @DisplayName("MCS-003 AC5 the member themself can read")
    void theMemberThemselfCanRead() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);

        request(community, member, loginUser(member), JANUARY_10, JANUARY_11).andExpect(status().isOk());
    }

    @Test
    @DisplayName("MCS-003 AC5 an enabled community admin of that community can read")
    void anEnabledCommunityAdminCanRead() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);

        request(community, member, loginAsCommunityAdmin(community.getId()), JANUARY_10, JANUARY_11)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("MEM-031 MCS-003 AC5 a platform admin who is neither the member nor an admin there is answered 404")
    void aPlatformAdminIsAnsweredNotFound() throws Exception {
        String platformAdminToken = loginAsDefaultPlatformAdmin();
        Community community = persistCommunity();
        User member = persistMember(community);

        request(community, member, platformAdminToken, JANUARY_10, JANUARY_11).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("MCS-003 AC5 another member of the same community is answered 404")
    void anotherMemberOfTheSameCommunityIsAnsweredNotFound() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);

        request(community, member, loginAsCommunityMember(community.getId()), JANUARY_10, JANUARY_11)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("MCS-003 AC5 a member of another community is answered 404")
    void aMemberOfAnotherCommunityIsAnsweredNotFound() throws Exception {
        Community community = persistCommunity();
        Community otherCommunity = persistCommunity();
        User member = persistMember(community);

        request(community, member, loginAsCommunityMember(otherCommunity.getId()), JANUARY_10, JANUARY_11)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("MCS-003 AC5 a disabled community admin of that community is answered 404")
    void aDisabledCommunityAdminIsAnsweredNotFound() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);
        User admin = persistUser();
        createMembershipRepository.create(community.getId(), admin.getId(), CommunityRole.COMMUNITY_ADMIN);
        MembershipStateTestSupport.markDisabled(communityMembershipJpaRepository, admin.getId(), community.getId());

        request(community, member, loginUser(admin), JANUARY_10, JANUARY_11).andExpect(status().isNotFound());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        Community community = persistCommunity();
        User member = persistMember(community);

        mockMvc.perform(get(PATH, community.getId(), member.getId())
                        .queryParam("startDate", JANUARY_10)
                        .queryParam("endDate", JANUARY_11))
                .andExpect(status().isUnauthorized());
    }

    // --- fixtures ---

    private static void assertDistinctStarts(JsonNode hours) {
        Set<Instant> starts = new HashSet<>();
        for (JsonNode hour : hours) {
            starts.add(OffsetDateTime.parse(hour.path("startsAt").asText()).toInstant());
        }
        assertEquals(hours.size(), starts.size(), () -> "startsAt repeats: " + hours);
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

    private static void assertSumMatches(JsonNode hours, String field, JsonNode expected, BigDecimal tolerance) {
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode hour : hours) {
            if (!hour.path(field).isNull()) {
                sum = sum.add(hour.path(field).decimalValue());
            }
        }
        BigDecimal difference = sum.subtract(expected.decimalValue()).abs();
        BigDecimal total = sum;
        assertTrue(difference.compareTo(tolerance) <= 0,
                () -> field + ": hours add up to " + total + ", the energy metrics report " + expected);
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
     * Writes one hourly record for the supply at the instant its hour starts, so both records of the
     * hour a fall-back repeats can be written.
     */
    private void writeAt(Supply supply, String startsAt, Float gridImportKWh, Float selfConsumptionKWh,
                         Float surplusKWh) {
        influxFixture.writeAt(supply.getCode(), OffsetDateTime.parse(startsAt).toInstant(), gridImportKWh,
                selfConsumptionKWh, surplusKWh);
        if (!writtenCups.contains(supply.getCode())) {
            writtenCups.add(supply.getCode());
        }
    }
}
