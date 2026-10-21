package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture.hourlyRecord;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.ASSIGNED_PRODUCTION;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.DAY_END;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.DAY_START;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.FIRST_GENERATION;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.FIRST_GRID_IMPORT;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.FIRST_SELF_CONSUMPTION;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.FIRST_SURPLUS;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.GENERATION;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.GRID_IMPORT;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.MONTH_END;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.MONTH_START;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.SAVINGS_EUR;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.SECOND_GENERATION;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.SECOND_GRID_IMPORT;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.SECOND_SELF_CONSUMPTION;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.SECOND_SURPLUS;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.SELF_CONSUMPTION;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.SURPLUS;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.TOTAL_CONSUMPTION;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.assertMonthlyAggregateUnchanged;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.firstRecord;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.gridImportSum;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.secondRecord;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.selfConsumptionSum;
import static org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset.surplusSum;
import static org.lucoenergia.conluz.infrastructure.shared.web.KWhJson.assertKWh;
import static org.lucoenergia.conluz.infrastructure.shared.web.KWhJson.kWhAt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * How the two membership operations round their energy figures (#385): every figure is the
 * unrounded sum across the member's supplies, rounded once; the ratios and the savings come from the
 * unrounded totals; and the two operations report the same figures for the same month.
 *
 * <p>The member owns two supplies, one carrying each record of {@code RoundingNoiseDataset}, so
 * every expected figure here is the dataset's two-record sum. The tariff in the test profile is the
 * 0.15 EUR/kWh estimate, a constant price for every hour.
 */
@Transactional
class MembershipEnergyValuesControllerTest extends BaseControllerTest {

    private static final String MONTHLY_PATH =
            "/api/v1/communities/{communityId}/memberships/{userId}/consumption/monthly";
    private static final String METRICS_PATH =
            "/api/v1/communities/{communityId}/memberships/{userId}/energy-metrics";
    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final YearMonth FEBRUARY = YearMonth.of(2024, 2);

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipService createMembershipService;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private DatadisConsumptionInfluxFixture influxFixture;
    @Autowired
    private DatadisMonthlyAggregationRepository monthlyAggregationRepository;
    @Autowired
    @Qualifier("getDatadisConsumptionRepositoryInflux")
    private GetDatadisConsumptionRepository getDatadisConsumptionRepository;

    private final List<String> writtenCups = new ArrayList<>();

    @AfterEach
    void clearWrittenSeries() {
        writtenCups.forEach(cups -> {
            influxFixture.clear(cups);
            influxFixture.clearMonthlyAggregates(cups);
        });
    }

    @Test
    @DisplayName("EVL-001 AC1 AC2 AC5 the membership monthly series rounds every energy figure to 3 decimals and stores nothing rounded")
    void theMonthlySeriesRoundsEveryEnergyFigure() throws Exception {
        TwoSupplyMembership membership = twoSupplyMembership();
        write(membership.first(), firstRecord(membership.first().getCode()));
        write(membership.second(), secondRecord(membership.second().getCode()));
        aggregateFebruary(membership);

        String body = requestMonthly(membership)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        assertKWh(body, "/0/consumptionKWh", GRID_IMPORT);
        assertKWh(body, "/0/selfConsumptionEnergyKWh", SELF_CONSUMPTION);
        assertKWh(body, "/0/surplusEnergyKWh", SURPLUS);
        assertKWh(body, "/0/generationEnergyKWh", GENERATION);
        assertMonthlyAggregateUnchanged(getDatadisConsumptionRepository, membership.first(),
                FIRST_GRID_IMPORT, FIRST_SELF_CONSUMPTION, FIRST_SURPLUS, FIRST_GENERATION);
        assertMonthlyAggregateUnchanged(getDatadisConsumptionRepository, membership.second(),
                SECOND_GRID_IMPORT, SECOND_SELF_CONSUMPTION, SECOND_SURPLUS, SECOND_GENERATION);
    }

    /**
     * Rounded supply by supply, grid import would add up to 0.123 + 0.222 = 0.345 and generation to
     * 0.056 + 0.056 = 0.112; the savings would be 0.00 + 0.00. Constant price: the expected amount
     * is the unrounded self-consumption, 0.0334 kWh, times 0.15 EUR/kWh, rounded to cents.
     */
    @Test
    @DisplayName("EVL-002 AC3 the membership monthly series rounds the unrounded sum across supplies, once, and prices it unrounded")
    void theMonthlySeriesRoundsTheUnroundedSumAcrossSupplies() throws Exception {
        TwoSupplyMembership membership = twoSupplyMembership();
        write(membership.first(), firstRecord(membership.first().getCode()));
        write(membership.second(), secondRecord(membership.second().getCode()));
        aggregateFebruary(membership);

        String body = requestMonthly(membership)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].savingsEur").value(SAVINGS_EUR))
                .andReturn().getResponse().getContentAsString();

        assertKWh(body, "/0/consumptionKWh", GRID_IMPORT);
        assertKWh(body, "/0/generationEnergyKWh", GENERATION);
    }

    @Test
    @DisplayName("EVL-001 AC1 AC2 the membership energy metrics round every energy figure to 3 decimals")
    void theEnergyMetricsRoundEveryEnergyFigure() throws Exception {
        TwoSupplyMembership membership = twoSupplyMembership();
        write(membership.first(), firstRecord(membership.first().getCode()));
        write(membership.second(), secondRecord(membership.second().getCode()));

        String body = requestMetrics(membership, DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertKWh(body, "/energy/gridImportKWh", GRID_IMPORT);
        assertKWh(body, "/energy/selfConsumptionKWh", SELF_CONSUMPTION);
        assertKWh(body, "/energy/surplusKWh", SURPLUS);
        assertKWh(body, "/energy/totalConsumptionKWh", TOTAL_CONSUMPTION);
        assertKWh(body, "/energy/assignedProductionKWh", ASSIGNED_PRODUCTION);
    }

    /**
     * From the rounded figures the ratios would be 0.033 / 0.379 = 0.08707 and 0.033 / 0.366 =
     * 0.09016, and the savings 0.033 kWh x 0.15 = 0.00495, presented as 0.00. Constant price: the
     * expected amount is the unrounded self-consumption times 0.15 EUR/kWh, rounded to cents.
     */
    @Test
    @DisplayName("EVL-002 AC3 the membership energy metrics derive the ratios and the savings from the unrounded totals")
    void theEnergyMetricsDeriveRatiosAndSavingsFromTheUnroundedTotals() throws Exception {
        TwoSupplyMembership membership = twoSupplyMembership();
        write(membership.first(), firstRecord(membership.first().getCode()));
        write(membership.second(), secondRecord(membership.second().getCode()));
        double selfConsumption = selfConsumptionSum();

        requestMetrics(membership, DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selfSufficiencyRatio")
                        .value(closeTo(selfConsumption / (gridImportSum() + selfConsumption), 1e-12), Double.class))
                .andExpect(jsonPath("$.selfConsumptionRatio")
                        .value(closeTo(selfConsumption / (selfConsumption + surplusSum()), 1e-12), Double.class))
                .andExpect(jsonPath("$.selfSufficiencyRatio").value(not(closeTo(0.033 / 0.379, 1e-6)), Double.class))
                .andExpect(jsonPath("$.selfConsumptionRatio").value(not(closeTo(0.033 / 0.366, 1e-6)), Double.class))
                .andExpect(jsonPath("$.savings.amountEur").value(SAVINGS_EUR));
    }

    /**
     * February 2024, published at every hour for both supplies, with several hourly records each
     * carrying four decimals, aggregated after the last of them and read through both operations over the month's local bounds.
     */
    @Test
    @DisplayName("EVL-001 EVL-002 AC4 the membership monthly series and energy metrics report the same figures for a month")
    void theMonthlySeriesAndTheEnergyMetricsAgreeOnAMonth() throws Exception {
        TwoSupplyMembership membership = twoSupplyMembership();
        // Every hour of the month published as zero first, so the records below land in a complete
        // month; a record written later at the same instant replaces the zero.
        influxFixture.writePublishedZeros(membership.first().getCode(), FEBRUARY, ZONE);
        influxFixture.writePublishedZeros(membership.second().getCode(), FEBRUARY, ZONE);
        write(membership.first(), firstRecord(membership.first().getCode()), secondRecord(membership.first().getCode()),
                hourlyRecord(membership.first().getCode(), "2024/02/01", "00:00", 1.0005f, 0.3337f, 2.1119f),
                hourlyRecord(membership.first().getCode(), "2024/02/29", "23:00", 0.0007f, 0.0049f, 0.0001f));
        write(membership.second(), firstRecord(membership.second().getCode()),
                hourlyRecord(membership.second().getCode(), "2024/02/15", "07:00", 3.4567f, 1.2345f, 0.9876f),
                hourlyRecord(membership.second().getCode(), "2024/02/20", "19:00", 0.1111f, 0.2229f, 0.0004f));
        aggregateFebruary(membership);

        String monthly = requestMonthly(membership)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].suppliesWithData").value(2))
                .andReturn().getResponse().getContentAsString();
        String metrics = requestMetrics(membership, MONTH_START, MONTH_END)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(0, kWhAt(monthly, "/0/consumptionKWh").compareTo(kWhAt(metrics, "/energy/gridImportKWh")),
                () -> "grid import differs: " + monthly + " vs " + metrics);
        assertEquals(0, kWhAt(monthly, "/0/surplusEnergyKWh").compareTo(kWhAt(metrics, "/energy/surplusKWh")),
                () -> "surplus differs: " + monthly + " vs " + metrics);
        assertEquals(0, kWhAt(monthly, "/0/selfConsumptionEnergyKWh")
                        .compareTo(kWhAt(metrics, "/energy/selfConsumptionKWh")),
                () -> "self-consumption differs: " + monthly + " vs " + metrics);
    }

    // --- fixtures ---

    private record TwoSupplyMembership(Community community, User member, Supply first, Supply second) {
    }

    private TwoSupplyMembership twoSupplyMembership() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User member = UserMother.randomUser();
        member.enable();
        createUserRepository.create(member);
        createMembershipService.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        return new TwoSupplyMembership(community, member, createSupply(member, community), createSupply(member, community));
    }

    private Supply createSupply(User owner, Community community) {
        return createSupplyRepository.create(SupplyMother.random(owner).build(), UserId.of(owner.getId()),
                community.getId());
    }

    private void write(Supply supply, DatadisConsumption... records) {
        influxFixture.write(List.of(records));
        if (!writtenCups.contains(supply.getCode())) {
            writtenCups.add(supply.getCode());
        }
    }

    /**
     * Builds February's pre-aggregate of each supply through the production aggregation.
     */
    private void aggregateFebruary(TwoSupplyMembership membership) {
        monthlyAggregationRepository.aggregateMonthlyConsumption(membership.first(), Month.FEBRUARY, 2024);
        monthlyAggregationRepository.aggregateMonthlyConsumption(membership.second(), Month.FEBRUARY, 2024);
    }

    private ResultActions requestMonthly(TwoSupplyMembership membership)
            throws Exception {
        return mockMvc.perform(get(MONTHLY_PATH, membership.community().getId(), membership.member().getId())
                .header(HttpHeaders.AUTHORIZATION, loginUser(membership.member()))
                .queryParam("startDate", MONTH_START)
                .queryParam("endDate", MONTH_END));
    }

    private ResultActions requestMetrics(TwoSupplyMembership membership, String startDate, String endDate)
            throws Exception {
        return mockMvc.perform(get(METRICS_PATH, membership.community().getId(), membership.member().getId())
                .header(HttpHeaders.AUTHORIZATION, loginUser(membership.member()))
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate));
    }
}
