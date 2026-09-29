package org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics.hourlyprofile;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.time.ClockProvider;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionInfluxFixture.hourlyRecord;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end over a real PostgreSQL and InfluxDB, one test per acceptance criterion of #317. The
 * clock is fixed at 2026-09-29 unless a test moves it, so the latest month that can be published
 * is August 2026. The community's zone is Europe/Madrid: summer time is UTC+2, winter time UTC+1.
 *
 * <p>Records written with a local date and time go through the production write path; records
 * written at an instant are needed only where a local time is ambiguous or where the UTC hour is
 * the point of the test.
 */
@Transactional
class GetMembershipHourlyProfileControllerTest extends BaseControllerTest {

    private static final String PATH =
            "/api/v1/communities/{communityId}/memberships/{userId}/energy-metrics/hourly-profile";
    private static final String AGGREGATED_PATH =
            "/api/v1/communities/{communityId}/memberships/{userId}/energy-metrics";
    private static final double TOLERANCE = 1e-9;

    /** 2026-09-29T12:00 in Madrid. */
    private static final Instant NOW = Instant.parse("2026-09-29T10:00:00Z");

    @MockitoBean
    private ClockProvider clockProvider;

    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipService createMembershipService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private DatadisConsumptionInfluxFixture influxFixture;

    private final List<String> writtenCups = new ArrayList<>();

    @BeforeEach
    void givenAFixedClock() {
        when(clockProvider.now()).thenReturn(NOW);
    }

    @AfterEach
    void clearWrittenSeries() {
        writtenCups.forEach(influxFixture::clear);
    }

    // --- AC1: 24 ordered buckets and the resolved bounds ---

    @Test
    void ac1_returnsTwentyFourBucketsOrderedByHourIncludingEmptyOnesAndTheResolvedBounds() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity supply = persistSupply(member, community);
        write(supply, "2026/08/10", "12:00", 1f, 2f, 1f);
        write(supply, "2026/08/11", "13:00", 1f, 2f, 1f);

        ResultActions response = request(community, member, loginUser(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2026-08-01T00:00:00+02:00"))
                .andExpect(jsonPath("$.period.endDate").value("2026-08-31T23:00:00+02:00"))
                .andExpect(jsonPath("$.buckets.length()").value(24));
        for (int hour = 0; hour < 24; hour++) {
            response.andExpect(jsonPath("$.buckets[" + hour + "].hour").value(hour));
        }
    }

    // --- AC2: the same month as the aggregated energy metrics ---

    /**
     * August carries consumption only -- assigned production zero or not written -- so it is not
     * published; July is. Both endpoints resolve July, and August's large records never reach the
     * profile.
     */
    @Test
    void ac2_resolvesTheSameMonthAsTheAggregatedEnergyMetricsAndExcludesRecordsOutsideIt() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity supply = persistSupply(member, community);
        write(supply, "2026/07/15", "12:00", 3f, 4f, 1f);
        write(supply, "2026/08/10", "12:00", 90f, 0f, 0f);
        write(supply, "2026/08/11", "12:00", 90f, null, null);
        String token = loginUser(member);

        JsonNode profile = json(request(community, member, token).andExpect(status().isOk()));
        JsonNode aggregated = json(requestAggregatedLatest(community, member, token).andExpect(status().isOk()));

        assertEquals(aggregated.path("period"), profile.path("period"));
        assertEquals("2026-07-01T00:00:00+02:00", profile.path("period").path("startDate").asText());
        JsonNode noon = profile.path("buckets").path(12);
        assertEquals(7d, noon.path("averageConsumptionKWh").asDouble(), TOLERANCE);
        assertEquals(1L, noon.path("consumptionSampleCount").asLong());
    }

    // --- AC3: averaged over every record, not over per-supply averages ---

    /**
     * At local 10:00, supply A has 3 records of consumption 6 + 4 = 10 and assigned 4 + 2 = 6;
     * supply B has 1 record of consumption 1 + 1 = 2 and assigned 1 + 1 = 2. Over every record:
     * consumption 32 / 4 = 8.0 and assigned 20 / 4 = 5.0. Averaging the per-supply averages would
     * give (10 + 2) / 2 = 6.0 and (6 + 2) / 2 = 4.0.
     */
    @Test
    void ac3_averagesOverEveryRecordOfEverySupplyRatherThanOverThePerSupplyAverages() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity large = persistSupply(member, community);
        SupplyEntity small = persistSupply(member, community);
        write(large, "2026/08/03", "10:00", 6f, 4f, 2f);
        write(large, "2026/08/04", "10:00", 6f, 4f, 2f);
        write(large, "2026/08/05", "10:00", 6f, 4f, 2f);
        write(small, "2026/08/03", "10:00", 1f, 1f, 1f);

        request(community, member, loginUser(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets[10].averageConsumptionKWh").value(closeTo(8.0, TOLERANCE)))
                .andExpect(jsonPath("$.buckets[10].averageConsumptionKWh").value(not(closeTo(6.0, 1e-3))))
                .andExpect(jsonPath("$.buckets[10].consumptionSampleCount").value(4))
                .andExpect(jsonPath("$.buckets[10].averageAssignedProductionKWh").value(closeTo(5.0, TOLERANCE)))
                .andExpect(jsonPath("$.buckets[10].averageAssignedProductionKWh").value(not(closeTo(4.0, 1e-3))))
                .andExpect(jsonPath("$.buckets[10].assignedProductionSampleCount").value(4));
    }

    // --- AC4 and AC5: null without records, zero for measured zeros ---

    /**
     * Local 03:00 has no record on any day: null averages, present in the JSON, and zero samples.
     */
    @Test
    void ac4_anHourWithoutAnyRecordHasNullAveragesAndZeroSampleCounts() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity supply = persistSupply(member, community);
        write(supply, "2026/08/10", "02:00", 0.3f, 0f, 0f);
        write(supply, "2026/08/10", "12:00", 1f, 2f, 1f);

        request(community, member, loginUser(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets[3]").value(hasEntry(is("averageConsumptionKWh"), nullValue())))
                .andExpect(jsonPath("$.buckets[3]").value(hasEntry(is("averageAssignedProductionKWh"), nullValue())))
                .andExpect(jsonPath("$.buckets[3].consumptionSampleCount").value(0))
                .andExpect(jsonPath("$.buckets[3].assignedProductionSampleCount").value(0));
    }

    /**
     * Local 02:00, a night hour, carries stored zeros of self-consumption and surplus on two days of
     * the published month: its assigned production averages 0, not null, over 2 samples. Local
     * 03:00, beside it, has no record and stays null.
     */
    @Test
    void ac5_anHourWhoseRecordsCarryZeroAssignedProductionAveragesZeroNotNull() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity supply = persistSupply(member, community);
        write(supply, "2026/08/10", "02:00", 0.3f, 0f, 0f);
        write(supply, "2026/08/11", "02:00", 0.5f, 0f, 0f);
        write(supply, "2026/08/10", "12:00", 1f, 2f, 1f);

        request(community, member, loginUser(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets[2].averageAssignedProductionKWh").value(0.0))
                .andExpect(jsonPath("$.buckets[2].assignedProductionSampleCount").value(2))
                .andExpect(jsonPath("$.buckets[2].averageConsumptionKWh").value(closeTo(0.4, 1e-6)))
                .andExpect(jsonPath("$.buckets[3]").value(hasEntry(is("averageAssignedProductionKWh"), nullValue())))
                .andExpect(jsonPath("$.buckets[3].assignedProductionSampleCount").value(0));
    }

    // --- AC6: independent sample counts ---

    @Test
    void ac6_recordsWithoutAnyAssignedProductionFieldCountOnlyAsConsumptionSamples() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity supply = persistSupply(member, community);
        write(supply, "2026/08/10", "20:00", 2f, null, null);
        write(supply, "2026/08/11", "20:00", 4f, null, null);
        write(supply, "2026/08/10", "12:00", 1f, 2f, 1f);

        request(community, member, loginUser(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets[20].averageConsumptionKWh").value(closeTo(3.0, 1e-6)))
                .andExpect(jsonPath("$.buckets[20].consumptionSampleCount").value(2))
                .andExpect(jsonPath("$.buckets[20]").value(hasEntry(is("averageAssignedProductionKWh"), nullValue())))
                .andExpect(jsonPath("$.buckets[20].assignedProductionSampleCount").value(0));
    }

    // --- AC7: local hour, not UTC ---

    /**
     * 10:00 UTC on 2026-08-15 is 12:00 in Madrid summer time: the record belongs to bucket 12, and
     * bucket 10 stays empty.
     */
    @Test
    void ac7_aRecordFallsInTheBucketOfItsLocalHourInTheCommunityZone() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity supply = persistSupply(member, community);
        writeAt(supply, List.of(Instant.parse("2026-08-15T10:00:00Z")), 5f, 1f, 1f);

        request(community, member, loginUser(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets[12].averageConsumptionKWh").value(closeTo(6.0, 1e-6)))
                .andExpect(jsonPath("$.buckets[12].consumptionSampleCount").value(1))
                .andExpect(jsonPath("$.buckets[10].consumptionSampleCount").value(0));
    }

    // --- AC8: daylight saving transitions ---

    /**
     * Every hour of October 2026 carries a record: 745 of them, since local 02:00 happens twice on
     * the 25th, at 00:00Z and at 01:00Z. Local 02:00 therefore holds 31 + 1 = 32 samples and every
     * other hour 31. Each record consumes 1 kWh except the second 02:00 of the 25th, which consumes
     * 32, so bucket 2 averages (31 + 32) / 32 = 1.96875: both records of that day reached it.
     *
     * <p>Both records are written at their exact instants, bypassing the Datadis write path. This
     * pins what the profile does with two stored records of the repeated hour; it is not evidence
     * that ingestion stores both. The write path resolves a local date and time to the earlier
     * offset, so two records labelled with the same local 02:00 would collide on one instant (#322).
     */
    @Test
    void ac8_theHourRepeatedByTheOctoberTransitionReceivesBothRecordsOfThatDay() throws Exception {
        when(clockProvider.now()).thenReturn(Instant.parse("2026-11-15T10:00:00Z"));
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity supply = persistSupply(member, community);
        Instant secondTwoOClock = Instant.parse("2026-10-25T01:00:00Z");
        List<Instant> month = hourly(Instant.parse("2026-09-30T22:00:00Z"), Instant.parse("2026-10-31T22:00:00Z"));
        // Self-consumption zero and surplus one: consumption is the grid import alone, and the
        // month carries assigned production, so it is published.
        writeAt(supply, month.stream().filter(time -> !time.equals(secondTwoOClock)).toList(), 1f, 0f, 1f);
        writeAt(supply, List.of(secondTwoOClock), 32f, 0f, 1f);

        ResultActions response = request(community, member, loginUser(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2026-10-01T00:00:00+02:00"))
                .andExpect(jsonPath("$.period.endDate").value("2026-10-31T23:00:00+01:00"))
                .andExpect(jsonPath("$.coverage.expectedHours").value(745))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(745))
                .andExpect(jsonPath("$.buckets[2].consumptionSampleCount").value(32))
                .andExpect(jsonPath("$.buckets[2].averageConsumptionKWh").value(closeTo(63.0 / 32, 1e-6)));
        for (int hour : List.of(0, 1, 3, 4, 12, 23)) {
            response.andExpect(jsonPath("$.buckets[" + hour + "].consumptionSampleCount").value(31))
                    .andExpect(jsonPath("$.buckets[" + hour + "].averageConsumptionKWh").value(closeTo(1.0, 1e-6)));
        }
    }

    /**
     * Every hour of March 2026 carries a record: 743 of them, since local 02:00 never happens on the
     * 29th. Local 02:00 therefore holds 31 - 1 = 30 samples and every other hour 31. 01:00Z on the
     * 29th is local 03:00; it consumes 32 kWh and every other record 1, so bucket 3 averages
     * (30 + 32) / 31 = 2.0 while bucket 2 stays at 1.0: nothing shifted into the missing hour.
     */
    @Test
    void ac8_theHourSkippedByTheMarchTransitionReceivesNoRecordOfThatDayAndNoBucketShifts() throws Exception {
        when(clockProvider.now()).thenReturn(Instant.parse("2026-04-15T10:00:00Z"));
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity supply = persistSupply(member, community);
        Instant localThreeOClock = Instant.parse("2026-03-29T01:00:00Z");
        List<Instant> month = hourly(Instant.parse("2026-02-28T23:00:00Z"), Instant.parse("2026-03-31T21:00:00Z"));
        // Grid import zero and self-consumption one: consumption is 1 kWh, and the month carries
        // assigned production, so it is published.
        writeAt(supply, month.stream().filter(time -> !time.equals(localThreeOClock)).toList(), 0f, 1f, 0f);
        writeAt(supply, List.of(localThreeOClock), 31f, 1f, 0f);

        ResultActions response = request(community, member, loginUser(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2026-03-01T00:00:00+01:00"))
                .andExpect(jsonPath("$.period.endDate").value("2026-03-31T23:00:00+02:00"))
                .andExpect(jsonPath("$.coverage.expectedHours").value(743))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(743))
                .andExpect(jsonPath("$.buckets.length()").value(24))
                .andExpect(jsonPath("$.buckets[2].consumptionSampleCount").value(30))
                .andExpect(jsonPath("$.buckets[2].averageConsumptionKWh").value(closeTo(1.0, 1e-6)))
                .andExpect(jsonPath("$.buckets[3].consumptionSampleCount").value(31))
                .andExpect(jsonPath("$.buckets[3].averageConsumptionKWh").value(closeTo(2.0, 1e-6)));
        for (int hour : List.of(0, 1, 4, 12, 23)) {
            response.andExpect(jsonPath("$.buckets[" + hour + "].consumptionSampleCount").value(31));
        }
    }

    // --- AC9: no resolved month ---

    @Test
    void ac9_aMembershipWithoutSuppliesGetsNullBoundsAndTwentyFourEmptyBuckets() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        assertEmptyProfile(request(community, member, loginUser(member)), 0);
    }

    /**
     * The only assigned production sits in August 2024, one month before the 24-month search window.
     */
    @Test
    void ac9_noAssignedProductionInTheSearchWindowGetsNullBoundsAndTwentyFourEmptyBuckets() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity supply = persistSupply(member, community);
        write(supply, "2024/08/15", "12:00", 3f, 4f, 1f);
        write(supply, "2026/08/15", "12:00", 3f, 0f, 0f);

        assertEmptyProfile(request(community, member, loginUser(member)), 1);
    }

    // --- AC10: coverage consistent with the aggregated energy metrics ---

    /**
     * Two supplies, one silent: 2 supplies, 1 with data, and August's 744 hours expected for each.
     * The coverage object is identical to the one the aggregated endpoint reports.
     */
    @Test
    void ac10_reportsTheSameCoverageAsTheAggregatedEnergyMetrics() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity active = persistSupply(member, community);
        persistSupply(member, community);
        write(active, "2026/08/10", "12:00", 1f, 2f, 1f);
        write(active, "2026/08/11", "12:00", 1f, 2f, 1f);
        write(active, "2026/08/11", "13:00", 1f, null, null);
        String token = loginUser(member);

        JsonNode profile = json(request(community, member, token).andExpect(status().isOk()));
        JsonNode aggregated = json(requestAggregatedLatest(community, member, token).andExpect(status().isOk()));

        assertEquals(aggregated.path("coverage"), profile.path("coverage"));
        JsonNode coverage = profile.path("coverage");
        assertEquals(2, coverage.path("supplyCount").asInt());
        assertEquals(1, coverage.path("suppliesWithData").asInt());
        assertEquals(3L, coverage.path("hoursWithData").asLong());
        assertEquals(2 * 744L, coverage.path("expectedHours").asLong());
    }

    // --- AC11: authorization ---

    @Test
    void ac11_theMemberThemselfCanRead() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        request(community, member, loginUser(member)).andExpect(status().isOk());
    }

    @Test
    void ac11_aCommunityAdminOfThatCommunityCanRead() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        request(community, member, loginAsCommunityAdmin(community.getId())).andExpect(status().isOk());
    }

    @Test
    void ac11_anotherMemberOfTheSameCommunityIsAnsweredNotFound() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        request(community, member, loginAsCommunityMember(community.getId())).andExpect(status().isNotFound());
    }

    @Test
    void ac11_anAdminOfAnotherCommunityIsAnsweredNotFound() throws Exception {
        CommunityEntity community = persistCommunity();
        CommunityEntity otherCommunity = persistCommunity();
        User member = persistMember(community.getId());

        request(community, member, loginAsCommunityAdmin(otherCommunity.getId())).andExpect(status().isNotFound());
    }

    @Test
    void ac11_aPlatformAdminWhoIsNeitherTheMemberNorAnAdminThereIsAnsweredNotFound() throws Exception {
        String platformAdminToken = loginAsDefaultPlatformAdmin();
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        request(community, member, platformAdminToken).andExpect(status().isNotFound());
    }

    @Test
    void ac11_anAllowedCallerTargetingAMembershipThatDoesNotExistIsAnsweredNotFound() throws Exception {
        CommunityEntity community = persistCommunity();
        User outsider = persistUser();

        request(community, outsider, loginAsCommunityAdmin(community.getId())).andExpect(status().isNotFound());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        mockMvc.perform(get(PATH, community.getId(), member.getId())).andExpect(status().isUnauthorized());
    }

    /**
     * The endpoint takes no query parameter, but a path variable that is not a UUID is still a bad
     * request, as the 400 response in its contract states.
     */
    @Test
    void aMalformedUserIdIsABadRequest() throws Exception {
        CommunityEntity community = persistCommunity();

        mockMvc.perform(get(PATH, community.getId(), "not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId())))
                .andExpect(status().isBadRequest());
    }

    // --- fixtures ---

    private void assertEmptyProfile(ResultActions response, int supplyCount) throws Exception {
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value(hasEntry(is("startDate"), nullValue())))
                .andExpect(jsonPath("$.period").value(hasEntry(is("endDate"), nullValue())))
                .andExpect(jsonPath("$.coverage.supplyCount").value(supplyCount))
                .andExpect(jsonPath("$.coverage.expectedHours").value(0))
                .andExpect(jsonPath("$.buckets.length()").value(24));
        for (int hour = 0; hour < 24; hour++) {
            String bucket = "$.buckets[" + hour + "]";
            response.andExpect(jsonPath(bucket + ".hour").value(hour))
                    .andExpect(jsonPath(bucket).value(hasEntry(is("averageConsumptionKWh"), nullValue())))
                    .andExpect(jsonPath(bucket).value(hasEntry(is("averageAssignedProductionKWh"), nullValue())))
                    .andExpect(jsonPath(bucket + ".consumptionSampleCount").value(0))
                    .andExpect(jsonPath(bucket + ".assignedProductionSampleCount").value(0));
        }
    }

    private ResultActions request(CommunityEntity community, User member, String token) throws Exception {
        return mockMvc.perform(get(PATH, community.getId(), member.getId())
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions requestAggregatedLatest(CommunityEntity community, User member, String token)
            throws Exception {
        return mockMvc.perform(get(AGGREGATED_PATH, community.getId(), member.getId())
                .header(HttpHeaders.AUTHORIZATION, token)
                .queryParam("period", "LATEST_PUBLISHED_MONTH"));
    }

    private JsonNode json(ResultActions response) throws Exception {
        JsonNode node = objectMapper.readTree(response.andReturn().getResponse().getContentAsString());
        assertFalse(node.isMissingNode());
        return node;
    }

    /**
     * Every hour from {@code first} to {@code last}, both inclusive.
     */
    private static List<Instant> hourly(Instant first, Instant last) {
        return Stream.iterate(first, time -> !time.isAfter(last), time -> time.plus(Duration.ofHours(1))).toList();
    }

    private CommunityEntity persistCommunity() {
        return communityJpaRepository.save(CommunityMother.randomEntity().build());
    }

    private User persistUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        return user;
    }

    private User persistMember(UUID communityId) {
        User member = persistUser();
        createMembershipService.create(communityId, member.getId(), CommunityRole.COMMUNITY_MEMBER);
        return member;
    }

    private SupplyEntity persistSupply(User owner, CommunityEntity community) {
        return supplyRepository.save(SupplyEntityMother.random(
                userRepository.getReferenceById(owner.getId()), community));
    }

    /**
     * Writes one hourly record through the production write path. A null energy value is stored as
     * an absent field.
     */
    private void write(SupplyEntity supply, String date, String time, Float gridImportKWh,
                       Float selfConsumptionKWh, Float surplusKWh) {
        influxFixture.write(List.of(hourlyRecord(supply.getCode(), date, time, gridImportKWh,
                selfConsumptionKWh, surplusKWh)));
        track(supply);
    }

    /**
     * Writes one hourly record at each instant, all with the same values.
     */
    private void writeAt(SupplyEntity supply, List<Instant> times, Float gridImportKWh, Float selfConsumptionKWh,
                         Float surplusKWh) {
        influxFixture.writeAt(supply.getCode(), times, gridImportKWh, selfConsumptionKWh, surplusKWh);
        track(supply);
    }

    private void track(SupplyEntity supply) {
        if (!writtenCups.contains(supply.getCode())) {
            writtenCups.add(supply.getCode());
        }
    }
}
