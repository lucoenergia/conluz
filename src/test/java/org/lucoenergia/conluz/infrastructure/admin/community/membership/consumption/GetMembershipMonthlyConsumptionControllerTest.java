package org.lucoenergia.conluz.infrastructure.admin.community.membership.consumption;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture.hourlyRecord;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end over a real PostgreSQL and InfluxDB. Each test writes hourly records; a supply that
 * must have data in a month is first given a published zero at every hour of it, so that its
 * self-consumption reaches the publication threshold and only the test's own records add anything.
 *
 * <p>The tariff in the test profile is the 0.15 EUR/kWh estimate with no VAT, so a month's savings
 * are always {@code self-consumed kWh * 0.15}, and every expected figure below is arithmetic on the
 * records each test writes. Every energy value is exactly representable as a float.
 */
@Transactional
class GetMembershipMonthlyConsumptionControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/communities/{communityId}/memberships/{userId}/consumption/monthly";

    /** January and February 2024, local, both bounds inclusive. */
    private static final String JANUARY_START = "2024-01-01T00:00:00+01:00";
    private static final String FEBRUARY_START = "2024-02-01T00:00:00+01:00";
    private static final String FEBRUARY_END = "2024-02-29T23:00:00+01:00";
    private static final String MARCH_START = "2024-03-01T00:00:00+01:00";
    private static final String METRICS_PATH =
            "/api/v1/communities/{communityId}/memberships/{userId}/energy-metrics";

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final YearMonth JANUARY = YearMonth.of(2024, 1);
    private static final YearMonth FEBRUARY = YearMonth.of(2024, 2);

    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private DatadisConsumptionInfluxFixture influxFixture;
    @Autowired
    private DatadisMonthlyAggregationRepository monthlyAggregationRepository;

    private final List<String> writtenCups = new ArrayList<>();

    @AfterEach
    void clearWrittenSeries() {
        writtenCups.forEach(cups -> {
            influxFixture.clear(cups);
            influxFixture.clearMonthlyAggregates(cups);
        });
    }

    // --- Aggregation ---

    /**
     * AC1. Two supplies of very different magnitudes in February: 100.5 + 7.25 kWh consumed,
     * 40 + 2.5 self-consumed, 20.25 + 1.5 exported. 42.5 kWh at 0.15 is 6.375, presented as 6.38.
     */
    @Test
    void eachMonthsTotalsAreTheSumOfThePerSupplyTotals() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity large = persistSupply(member, community);
        SupplyEntity small = persistSupply(member, community);
        publish(FEBRUARY, large, small);
        write(large, "2024/02/10", "13:00", 100.5f, 40f, 20.25f);
        write(small, "2024/02/10", "14:00", 7.25f, 2.5f, 1.5f);

        request(community, member, loginUser(member), FEBRUARY_START, FEBRUARY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value("2024/02/01"))
                .andExpect(jsonPath("$[0].time").value("00:00"))
                .andExpect(jsonPath("$[0].gridImportKWh").value(107.75))
                .andExpect(jsonPath("$[0].selfConsumptionKWh").value(42.5))
                .andExpect(jsonPath("$[0].surplusKWh").value(21.75))
                .andExpect(jsonPath("$[0].savingsEur").value(6.38))
                .andExpect(jsonPath("$[0].tariffSource").value("ESTIMATE"))
                .andExpect(jsonPath("$[0].supplyCount").value(2))
                .andExpect(jsonPath("$[0].suppliesWithData").value(2));
    }

    // --- The three states the chart must tell apart: nothing stored, partly reported, complete ---

    /**
     * AC4. The supply stored records in February only. January is still emitted, with zero totals,
     * null savings, a null tariff source and no supply with data -- and with neither of the two
     * fields this schema drops.
     */
    @Test
    void aMonthWithNothingStoredIsEmittedWithNullSavingsAndNoSupplyWithData() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity supply = persistSupply(member, community);
        publish(FEBRUARY, supply);
        write(supply, "2024/02/10", "13:00", 3f, 2f, 0f);

        request(community, member, loginUser(member), JANUARY_START, FEBRUARY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2024/01/01"))
                .andExpect(jsonPath("$[0].gridImportKWh").value(0))
                .andExpect(jsonPath("$[0].surplusKWh").value(0))
                .andExpect(jsonPath("$[0].assignedProductionKWh").value(0))
                .andExpect(jsonPath("$[0].selfConsumptionKWh").value(0))
                .andExpect(jsonPath("$[0].savingsEur").value(nullValue()))
                .andExpect(jsonPath("$[0].tariffSource").value(nullValue()))
                .andExpect(jsonPath("$[0].supplyCount").value(1))
                .andExpect(jsonPath("$[0].suppliesWithData").value(0))
                .andExpect(jsonPath("$[1].savingsEur").value(0.30))
                .andExpect(jsonPath("$[1].suppliesWithData").value(1))
                .andExpect(content().string(containsString(
                        "\"savingsEur\":null,\"tariffSource\":null,\"supplyCount\":1,\"suppliesWithData\":0")))
                .andExpect(content().string(not(containsString("obtainMethod"))))
                .andExpect(content().string(not(containsString("cups"))));
    }

    /**
     * AC5. Three supplies in February: one published for the whole month, one with records on a
     * single day only -- far below the publication threshold -- and one silent. Only the first has
     * data, so supplyCount (3) and suppliesWithData (1) differ, and the incomplete supply's records
     * add nothing until it is published. 29 kWh consumed and self-consumed; 29 kWh at 0.15 is 4.35.
     */
    @Test
    void aPartlyReportedMonthReportsTheSupplyCountAndTheSuppliesThatContributed() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity everyDay = persistSupply(member, community);
        SupplyEntity oneDay = persistSupply(member, community);
        persistSupply(member, community);
        publish(FEBRUARY, everyDay);
        for (int day = 1; day <= 29; day++) {
            write(everyDay, String.format("2024/02/%02d", day), "13:00", 1f, 1f, 0f);
        }
        write(oneDay, "2024/02/10", "13:00", 2f, 1f, 0f);

        request(community, member, loginUser(member), FEBRUARY_START, FEBRUARY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].supplyCount").value(3))
                .andExpect(jsonPath("$[0].suppliesWithData").value(1))
                .andExpect(jsonPath("$[0].gridImportKWh").value(29.0))
                .andExpect(jsonPath("$[0].selfConsumptionKWh").value(29.0))
                .andExpect(jsonPath("$[0].savingsEur").value(4.35));
    }

    @Test
    void aCompleteMonthHasEverySupplyWithData() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity first = persistSupply(member, community);
        SupplyEntity second = persistSupply(member, community);
        publish(FEBRUARY, first, second);
        write(first, "2024/02/10", "13:00", 1f, 1f, 0f);
        write(second, "2024/02/11", "13:00", 1f, 1f, 0f);

        request(community, member, loginUser(member), FEBRUARY_START, FEBRUARY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].supplyCount").value(2))
                .andExpect(jsonPath("$[0].suppliesWithData").value(2));
    }

    /**
     * A published month whose records carry zero energy is a measured zero: the supply has data, and
     * the savings are 0.00 with their source, not null.
     */
    @Test
    void aStoredMonthWithZeroEnergyIsAMeasuredZero() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity supply = persistSupply(member, community);
        publish(FEBRUARY, supply);
        write(supply, "2024/02/10", "13:00", 0f, 0f, 0f);

        request(community, member, loginUser(member), FEBRUARY_START, FEBRUARY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].suppliesWithData").value(1))
                .andExpect(jsonPath("$[0].tariffSource").value("ESTIMATE"))
                .andExpect(content().string(containsString("\"savingsEur\":0.00")));
    }

    /**
     * February was aggregated from preliminary data: its pre-aggregate holds no self-consumption.
     * The hourly records now carry the published figures, and no aggregation has run since. The
     * series sums the hourly records, so it reports them, and agrees with the energy metrics, which
     * read the same records.
     */
    @Test
    @DisplayName("MCS-004 a month is summed from the hourly records, not from a stale pre-aggregate, and agrees with the energy metrics")
    void aMonthIsSummedFromTheHourlyRecordsAndNotFromAStalePreAggregate() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity first = persistSupply(member, community);
        SupplyEntity second = persistSupply(member, community);
        publish(FEBRUARY, first, second);
        influxFixture.writeMonthlyAggregate(first.getCode(), FEBRUARY, ZONE, 90.0, 0.0, 3.0);
        influxFixture.writeMonthlyAggregate(second.getCode(), FEBRUARY, ZONE, 90.0, 0.0, 3.0);
        write(first, "2024/02/01", "01:00", 10.5f, 4.25f, 1f);
        write(first, "2024/02/29", "24:00", 1.25f, 0.5f, 0.25f);
        write(second, "2024/02/15", "14:00", 20f, 8f, 2.5f);
        String token = loginUser(member);

        JsonNode month = objectMapper.readTree(request(community, member, token, FEBRUARY_START, MARCH_START)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString()).get(0);
        JsonNode metrics = objectMapper.readTree(mockMvc.perform(get(METRICS_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .queryParam("startDate", FEBRUARY_START)
                        .queryParam("endDate", MARCH_START))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertEquals(12.75, month.path("selfConsumptionKWh").asDouble(), 1e-9);
        assertEquals(31.75, month.path("gridImportKWh").asDouble(), 1e-9);
        assertEquals(3.75, month.path("surplusKWh").asDouble(), 1e-9);
        assertEquals(metrics.path("energy").path("selfConsumptionKWh").asDouble(),
                month.path("selfConsumptionKWh").asDouble(), 1e-9, "self-consumption");
        assertEquals(metrics.path("energy").path("surplusKWh").asDouble(),
                month.path("surplusKWh").asDouble(), 1e-9, "surplus");
        assertEquals(metrics.path("energy").path("gridImportKWh").asDouble(),
                month.path("gridImportKWh").asDouble(), 1e-9, "grid import");
        assertEquals(metrics.path("savings").path("amountEur").asDouble(),
                month.path("savingsEur").asDouble(), 1e-9, "savings");
    }

    // --- Months ---

    /**
     * AC6. Bounds in the middle of January and of March select February and March, chronologically,
     * exactly the months the per-supply series selects for the same bounds -- and March is emitted
     * although nothing is stored for it, where the per-supply series omits it.
     */
    @Test
    void everyMonthInRangeIsEmittedWithTheSameSelectionAsThePerSupplySeries() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity supply = persistSupply(member, community);
        publish(JANUARY, supply);
        publish(FEBRUARY, supply);
        write(supply, "2024/01/20", "13:00", 1f, 1f, 0f);
        write(supply, "2024/02/10", "13:00", 2f, 1f, 0f);
        // The per-supply series compared with still reads the monthly pre-aggregates.
        aggregate(JANUARY, supply);
        aggregate(FEBRUARY, supply);
        String start = "2024-01-15T12:00:00+01:00";
        String end = "2024-03-10T00:00:00+01:00";
        String token = loginUser(member);

        request(community, member, token, start, end)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2024/02/01"))
                .andExpect(jsonPath("$[0].gridImportKWh").value(2.0))
                .andExpect(jsonPath("$[1].date").value("2024/03/01"))
                .andExpect(jsonPath("$[1].savingsEur").value(nullValue()));

        mockMvc.perform(get("/api/v1/supplies/{supplyId}/consumption/monthly", supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .queryParam("startDate", start)
                        .queryParam("endDate", end))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value("2024/02/01"));
    }

    /**
     * AC7. No supplies: every requested month, zero totals, null savings, a supply count of zero.
     */
    @Test
    void aMembershipWithoutSuppliesGetsEveryMonthEmpty() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        request(community, member, loginUser(member), JANUARY_START, FEBRUARY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2024/01/01"))
                .andExpect(jsonPath("$[1].date").value("2024/02/01"))
                .andExpect(jsonPath("$[0].gridImportKWh").value(0))
                .andExpect(jsonPath("$[0].savingsEur").value(nullValue()))
                .andExpect(jsonPath("$[0].tariffSource").value(nullValue()))
                .andExpect(jsonPath("$[0].supplyCount").value(0))
                .andExpect(jsonPath("$[0].suppliesWithData").value(0))
                .andExpect(jsonPath("$[1].gridImportKWh").value(0))
                .andExpect(jsonPath("$[1].savingsEur").value(nullValue()))
                .andExpect(jsonPath("$[1].tariffSource").value(nullValue()))
                .andExpect(jsonPath("$[1].supplyCount").value(0))
                .andExpect(jsonPath("$[1].suppliesWithData").value(0));
    }

    // --- Invalid periods (AC8) ---

    @Test
    void aStartDateWithoutAnEndDateIsABadRequest() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        mockMvc.perform(get(PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(member))
                        .queryParam("startDate", JANUARY_START))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anEndDateWithoutAStartDateIsABadRequest() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        mockMvc.perform(get(PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(member))
                        .queryParam("endDate", FEBRUARY_END))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aStartDateAfterTheEndDateIsABadRequest() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        request(community, member, loginUser(member), FEBRUARY_END, JANUARY_START)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_START_AFTER_END"));
    }

    // --- Authorization (AC9) ---

    @Test
    void theMemberThemselfCanRead() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        request(community, member, loginUser(member), JANUARY_START, FEBRUARY_END).andExpect(status().isOk());
    }

    @Test
    void aCommunityAdminOfThatCommunityCanRead() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        request(community, member, loginAsCommunityAdmin(community.getId()), JANUARY_START, FEBRUARY_END)
                .andExpect(status().isOk());
    }

    @Test
    void anotherMemberOfTheSameCommunityIsAnsweredNotFound() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        request(community, member, loginAsCommunityMember(community.getId()), JANUARY_START, FEBRUARY_END)
                .andExpect(status().isNotFound());
    }

    @Test
    void anAdminOfAnotherCommunityIsAnsweredNotFound() throws Exception {
        CommunityEntity community = persistCommunity();
        CommunityEntity otherCommunity = persistCommunity();
        User member = persistMember(community.getId());

        request(community, member, loginAsCommunityAdmin(otherCommunity.getId()), JANUARY_START, FEBRUARY_END)
                .andExpect(status().isNotFound());
    }

    @Test
    void aPlatformAdminWhoIsNeitherTheMemberNorAnAdminThereIsAnsweredNotFound() throws Exception {
        String platformAdminToken = loginAsDefaultPlatformAdmin();
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        request(community, member, platformAdminToken, JANUARY_START, FEBRUARY_END)
                .andExpect(status().isNotFound());
    }

    @Test
    void anAllowedCallerTargetingAMembershipThatDoesNotExistIsAnsweredNotFound() throws Exception {
        CommunityEntity community = persistCommunity();
        User outsider = persistUser();

        request(community, outsider, loginAsCommunityAdmin(community.getId()), JANUARY_START, FEBRUARY_END)
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        mockMvc.perform(get(PATH, community.getId(), member.getId())
                        .queryParam("startDate", JANUARY_START)
                        .queryParam("endDate", FEBRUARY_END))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aMalformedUserIdIsABadRequest() throws Exception {
        CommunityEntity community = persistCommunity();

        mockMvc.perform(get(PATH, community.getId(), "not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId()))
                        .queryParam("startDate", JANUARY_START)
                        .queryParam("endDate", FEBRUARY_END))
                .andExpect(status().isBadRequest());
    }

    // --- fixtures ---

    private ResultActions request(CommunityEntity community, User member, String token, String startDate,
                                  String endDate) throws Exception {
        return mockMvc.perform(get(PATH, community.getId(), member.getId())
                .header(HttpHeaders.AUTHORIZATION, token)
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate));
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
        createMembershipRepository.create(communityId, member.getId(), CommunityRole.COMMUNITY_MEMBER);
        return member;
    }

    private SupplyEntity persistSupply(User owner, CommunityEntity community) {
        return supplyRepository.save(SupplyEntityMother.random(
                userRepository.getReferenceById(owner.getId()), community));
    }

    /**
     * Writes one hourly record for the supply, at a local date and time.
     */
    private void write(SupplyEntity supply, String date, String time, Float gridImportKWh,
                       Float selfConsumptionKWh, Float surplusKWh) {
        DatadisConsumption record = hourlyRecord(supply.getCode(), date, time, gridImportKWh,
                selfConsumptionKWh, surplusKWh);
        influxFixture.write(List.of(record));
        if (!writtenCups.contains(supply.getCode())) {
            writtenCups.add(supply.getCode());
        }
    }

    /**
     * Builds the month's pre-aggregate of each supply through the production aggregation.
     */
    private void aggregate(YearMonth month, SupplyEntity... supplies) {
        for (SupplyEntity supply : supplies) {
            monthlyAggregationRepository.aggregateMonthlyConsumption(
                    SupplyMother.random().withId(supply.getId()).withCode(supply.getCode()).build(),
                    month.getMonth(), month.getYear());
        }
    }

    /**
     * Publishes the month for each supply: a record carrying zero at every hour of it, so the supply
     * has data in the month and only the records written afterwards add anything. Written before
     * them, since a record written later at the same instant replaces these fields.
     */
    private void publish(YearMonth month, SupplyEntity... supplies) {
        for (SupplyEntity supply : supplies) {
            influxFixture.writePublishedZeros(supply.getCode(), month, ZONE);
            if (!writtenCups.contains(supply.getCode())) {
                writtenCups.add(supply.getCode());
            }
        }
    }
}
