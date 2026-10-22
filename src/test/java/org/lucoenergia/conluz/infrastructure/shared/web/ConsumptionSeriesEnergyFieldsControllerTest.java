package org.lucoenergia.conluz.infrastructure.shared.web;

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
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture.hourlyRecord;
import static org.lucoenergia.conluz.infrastructure.shared.web.KWhJson.assertKWh;
import static org.lucoenergia.conluz.infrastructure.shared.web.KWhJson.kWhAt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The energy fields of the three consumption series (#460): they carry the names the energy metrics
 * give the same quantities, and the assigned production is the self-consumed plus the surplus energy,
 * derived from unrounded values and rounded once, never the distributor's generation figure.
 *
 * <p>The member owns two supplies with two hourly records each on 10 February 2024, every record
 * carrying a stored generation of 9.999 kWh that is nothing like its self-consumption plus surplus:
 *
 * <ul>
 *     <li>first: self-consumption 0.1000 + 0.0234 = 0.1234 and surplus 0.1111 + 0.1111 = 0.2222, so
 *     assigned production 0.3456, reported 0.346 -- not the 0.123 + 0.222 = 0.345 the rounded
 *     figures add up to;</li>
 *     <li>second: self-consumption 0.0300 + 0.0314 = 0.0614 and surplus 0.0500 + 0.0612 = 0.1112, so
 *     assigned production 0.1726, reported 0.173;</li>
 *     <li>the membership: self-consumption 0.1848 and surplus 0.3334, so assigned production 0.5182,
 *     reported 0.518 -- not the 0.346 + 0.173 = 0.519 the rounded per-supply figures add up to.</li>
 * </ul>
 */
@Transactional
class ConsumptionSeriesEnergyFieldsControllerTest extends BaseControllerTest {

    private static final String SUPPLY_DAILY_PATH = "/api/v1/supplies/{supplyId}/consumption/daily";
    private static final String SUPPLY_MONTHLY_PATH = "/api/v1/supplies/{supplyId}/consumption/monthly";
    private static final String MEMBERSHIP_MONTHLY_PATH =
            "/api/v1/communities/{communityId}/memberships/{userId}/consumption/monthly";

    private static final String DATE = "2024/02/10";
    private static final String DAY_START = "2024-02-10T00:00:00+01:00";
    private static final String DAY_END = "2024-02-10T23:00:00+01:00";
    private static final String MONTH_START = "2024-02-01T00:00:00+01:00";
    private static final String MONTH_END = "2024-02-29T23:00:00+01:00";
    private static final float GENERATION = 9.999f;

    private static final List<String> NEW_FIELDS =
            List.of("gridImportKWh", "selfConsumptionKWh", "surplusKWh", "assignedProductionKWh");
    private static final List<String> OLD_FIELDS =
            List.of("consumptionKWh", "selfConsumptionEnergyKWh", "surplusEnergyKWh", "generationEnergyKWh");

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
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

    @Test
    @DisplayName("EVL-003 AC1 the three consumption series name their energy fields as the energy metrics do")
    void theThreeSeriesNameTheirEnergyFieldsAsTheEnergyMetricsDo() throws Exception {
        Membership membership = seededMembership();

        for (String body : List.of(
                supplySeries(membership, SUPPLY_DAILY_PATH, DAY_START, DAY_END),
                supplySeries(membership, SUPPLY_MONTHLY_PATH, MONTH_START, MONTH_END),
                membershipMonthly(membership))) {
            JsonNode bucket = objectMapper.readTree(body).path(0);
            for (String field : NEW_FIELDS) {
                assertTrue(bucket.has(field), () -> "missing " + field + ": " + bucket);
            }
            for (String field : OLD_FIELDS) {
                assertFalse(bucket.has(field), () -> "still carries " + field + ": " + bucket);
            }
        }
    }

    @Test
    @DisplayName("EVL-004 AC3 the supply series derive assigned production from the unrounded self-consumption and surplus, rounded once")
    void theSupplySeriesDeriveAssignedProductionRoundedOnce() throws Exception {
        Membership membership = seededMembership();

        for (String body : List.of(
                supplySeries(membership, SUPPLY_DAILY_PATH, DAY_START, DAY_END),
                supplySeries(membership, SUPPLY_MONTHLY_PATH, MONTH_START, MONTH_END))) {
            assertKWh(body, "/0/selfConsumptionKWh", "0.123");
            assertKWh(body, "/0/surplusKWh", "0.222");
            assertKWh(body, "/0/assignedProductionKWh", "0.346");
            BigDecimal sumOfRounded = kWhAt(body, "/0/selfConsumptionKWh").add(kWhAt(body, "/0/surplusKWh"));
            assertNotEquals(0, sumOfRounded.compareTo(kWhAt(body, "/0/assignedProductionKWh")),
                    () -> "assigned production was summed from the rounded figures: " + body);
        }
    }

    @Test
    @DisplayName("EVL-004 AC3 the membership monthly series derives assigned production from the unrounded membership sums")
    void theMembershipMonthlySeriesDerivesAssignedProductionFromTheUnroundedSums() throws Exception {
        Membership membership = seededMembership();

        String body = membershipMonthly(membership);

        assertKWh(body, "/0/selfConsumptionKWh", "0.185");
        assertKWh(body, "/0/surplusKWh", "0.333");
        assertKWh(body, "/0/assignedProductionKWh", "0.518");
        String secondSupply = supplySeries(membership.second(), SUPPLY_MONTHLY_PATH, MONTH_START, MONTH_END,
                loginUser(membership.member()));
        assertKWh(secondSupply, "/0/assignedProductionKWh", "0.173");
    }

    @Test
    @DisplayName("EVL-004 AC5 the consumption series never report the distributor's generation as assigned production")
    void theSeriesNeverReportGenerationAsAssignedProduction() throws Exception {
        Membership membership = seededMembership();

        assertSelfConsumptionPlusSurplusWithoutGeneration(
                supplySeries(membership, SUPPLY_DAILY_PATH, DAY_START, DAY_END), "0.346");
        assertSelfConsumptionPlusSurplusWithoutGeneration(
                supplySeries(membership, SUPPLY_MONTHLY_PATH, MONTH_START, MONTH_END), "0.346");
        assertSelfConsumptionPlusSurplusWithoutGeneration(membershipMonthly(membership), "0.518");
    }

    private void assertSelfConsumptionPlusSurplusWithoutGeneration(String body, String expected) throws Exception {
        JsonNode bucket = objectMapper.readTree(body).path(0);
        assertFalse(bucket.has("generationEnergyKWh"), () -> "generation is exposed: " + bucket);
        assertKWh(body, "/0/assignedProductionKWh", expected);
    }

    // --- fixtures ---

    private record Membership(Community community, User member, Supply first, Supply second) {
    }

    private Membership seededMembership() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User member = UserMother.randomUser();
        member.enable();
        createUserRepository.create(member);
        createMembershipRepository.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        Supply first = createSupply(member, community);
        Supply second = createSupply(member, community);
        write(first, record(first, "10:00", 0.1000f, 0.1111f), record(first, "11:00", 0.0234f, 0.1111f));
        write(second, record(second, "10:00", 0.0300f, 0.0500f), record(second, "11:00", 0.0314f, 0.0612f));
        monthlyAggregationRepository.aggregateMonthlyConsumption(first, Month.FEBRUARY, 2024);
        monthlyAggregationRepository.aggregateMonthlyConsumption(second, Month.FEBRUARY, 2024);
        return new Membership(community, member, first, second);
    }

    private Supply createSupply(User owner, Community community) {
        return createSupplyRepository.create(SupplyMother.random(owner).build(), UserId.of(owner.getId()),
                community.getId());
    }

    private static DatadisConsumption record(Supply supply, String hour, float selfConsumptionKWh, float surplusKWh) {
        DatadisConsumption record = hourlyRecord(supply.getCode(), DATE, hour, 0.5f, selfConsumptionKWh, surplusKWh);
        record.setGenerationEnergyKWh(GENERATION);
        return record;
    }

    private void write(Supply supply, DatadisConsumption... records) {
        influxFixture.write(List.of(records));
        writtenCups.add(supply.getCode());
    }

    /** The series of the membership's first supply. */
    private String supplySeries(Membership membership, String path, String startDate, String endDate)
            throws Exception {
        return supplySeries(membership.first(), path, startDate, endDate, loginUser(membership.member()));
    }

    private String supplySeries(Supply supply, String path, String startDate, String endDate, String token)
            throws Exception {
        return mockMvc.perform(get(path, supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .queryParam("startDate", startDate)
                        .queryParam("endDate", endDate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
    }

    private String membershipMonthly(Membership membership) throws Exception {
        return mockMvc.perform(get(MEMBERSHIP_MONTHLY_PATH, membership.community().getId(), membership.member().getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(membership.member()))
                        .queryParam("startDate", MONTH_START)
                        .queryParam("endDate", MONTH_END))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].suppliesWithData").value(2))
                .andReturn().getResponse().getContentAsString();
    }
}
