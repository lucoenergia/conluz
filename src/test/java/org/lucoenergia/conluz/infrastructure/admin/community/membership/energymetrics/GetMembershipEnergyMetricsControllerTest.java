package org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
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
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionInfluxFixture.hourlyRecord;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end over a real PostgreSQL and InfluxDB. The clock is fixed at 2026-09-29, so the current
 * month is September 2026 and the previous calendar month is August 2026.
 *
 * <p>The tariff in the test profile is the 0.15 EUR/kWh estimate with no VAT, so an amount is
 * always {@code self-consumed kWh * 0.15}, and every expected figure below is arithmetic on the
 * records each test writes.
 */
@Transactional
class GetMembershipEnergyMetricsControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/communities/{communityId}/memberships/{userId}/energy-metrics";
    private static final String LATEST = "LATEST_PUBLISHED_MONTH";
    private static final double TOLERANCE = 1e-9;
    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    /** 2026-09-29T12:00 in Madrid. */
    private static final Instant NOW = Instant.parse("2026-09-29T10:00:00Z");

    /** A single day in summer time: 24 hourly slots, both bounds inclusive. */
    private static final String DAY_START = "2026-05-10T00:00:00+02:00";
    private static final String DAY_END = "2026-05-10T23:00:00+02:00";

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

    // --- Aggregation ---

    @Test
    void eachTotalIsTheSumOfThePerSupplyTotals() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity first = persistSupply(member, community);
        SupplyEntity second = persistSupply(member, community);
        write(first, "2026/05/10", "10:00", 60f, 40f, 10f);
        write(second, "2026/05/10", "11:00", 1f, 1f, 3f);

        requestPeriod(community, member, loginUser(member), DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.energy.gridImportKWh").value(closeTo(61.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(41.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.surplusKWh").value(closeTo(13.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.totalConsumptionKWh").value(closeTo(102.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.assignedProductionKWh").value(closeTo(54.0, TOLERANCE)))
                // 41 kWh * 0.15 = 6.15
                .andExpect(jsonPath("$.savings.amountEur").value(6.15));
    }

    /**
     * Supply A: 60 kWh imported, 40 self-consumed, 10 surplus -- ratios 0.40 and 0.80. Supply B:
     * 1, 1, 3 -- ratios 0.50 and 0.25. Averaging would report 0.45 and 0.525; summing first
     * reports 41/102 = 0.4020 and 41/54 = 0.7593.
     */
    @Test
    void ratiosAreDerivedFromTheSummedTotalsAndNeverByAveragingThePerSupplyRatios() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity first = persistSupply(member, community);
        SupplyEntity second = persistSupply(member, community);
        write(first, "2026/05/10", "10:00", 60f, 40f, 10f);
        write(second, "2026/05/10", "11:00", 1f, 1f, 3f);

        requestPeriod(community, member, loginUser(member), DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selfSufficiencyRatio").value(closeTo(0.4020, 1e-4)))
                .andExpect(jsonPath("$.selfConsumptionRatio").value(closeTo(0.7593, 1e-4)))
                .andExpect(jsonPath("$.selfSufficiencyRatio").value(not(closeTo(0.45, 1e-3))))
                .andExpect(jsonPath("$.selfConsumptionRatio").value(not(closeTo(0.525, 1e-3))));
    }

    @Test
    void aRatioWhoseSummedDenominatorIsZeroIsNullRatherThanZero() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity first = persistSupply(member, community);
        SupplyEntity second = persistSupply(member, community);
        // Consumption from the grid only: nothing assigned to either supply.
        write(first, "2026/05/10", "10:00", 5f, 0f, 0f);
        write(second, "2026/05/10", "11:00", 7f, 0f, 0f);

        requestPeriod(community, member, loginUser(member), DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selfConsumptionRatio").value(nullValue()))
                .andExpect(jsonPath("$.selfSufficiencyRatio").value(0.0));
    }

    @Test
    void suppliesInAnotherCommunityAreNotCounted() throws Exception {
        CommunityEntity community = persistCommunity();
        CommunityEntity otherCommunity = persistCommunity();
        User member = persistMember(community.getId());
        createMembershipService.create(otherCommunity.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        write(persistSupply(member, community), "2026/05/10", "10:00", 1f, 2f, 0f);
        // Larger, so counting it would be unmistakable.
        write(persistSupply(member, otherCommunity), "2026/05/10", "10:00", 1f, 1000f, 0f);

        requestPeriod(community, member, loginUser(member), DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(2.0, TOLERANCE)))
                .andExpect(jsonPath("$.coverage.supplyCount").value(1));
    }

    // --- Coverage ---

    /**
     * Three supplies over one 24-hour day: one complete, one with 10 records and one silent. The
     * expected hours span all three, so the silent supply lowers coverage, and the counters tell
     * it apart from the partial one: 3 supplies, 2 with data, only 1 of them complete. Hours
     * without a record contribute nothing to the sums.
     */
    @Test
    void coverageSpansEverySupplyAndCountsTheSuppliesThatContributedRecords() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity complete = persistSupply(member, community);
        SupplyEntity partial = persistSupply(member, community);
        persistSupply(member, community);
        for (int hour = 0; hour < 24; hour++) {
            write(complete, "2026/05/10", String.format("%02d:00", hour), 1f, 0.5f, 0f);
        }
        for (int hour = 8; hour < 18; hour++) {
            write(partial, "2026/05/10", String.format("%02d:00", hour), 2f, 1f, 0f);
        }

        requestPeriod(community, member, loginUser(member), DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverage.hoursWithData").value(34))
                .andExpect(jsonPath("$.coverage.expectedHours").value(72))
                .andExpect(jsonPath("$.coverage.supplyCount").value(3))
                .andExpect(jsonPath("$.coverage.suppliesWithData").value(2))
                // 24 * 1 + 10 * 2 = 44 imported; 24 * 0.5 + 10 * 1 = 22 self-consumed.
                .andExpect(jsonPath("$.energy.gridImportKWh").value(closeTo(44.0, 1e-6)))
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(22.0, 1e-6)));
    }

    // --- Reference period ---

    /**
     * August holds a single record carrying self-consumption, so it has not been published yet. The
     * reference month is July, published in full for the first supply, not an almost empty August.
     * Only the first supply has records in July, and the second supply's July hours still count
     * against coverage.
     */
    @Test
    void anUnpublishedPreviousMonthResolvesToTheLatestPublishedMonth() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity first = persistSupply(member, community);
        SupplyEntity second = persistSupply(member, community);
        writePublishedZeros(first, YearMonth.of(2026, 7));
        write(first, "2026/07/15", "12:00", 3f, 4f, 1f);
        write(first, "2026/08/10", "12:00", 9f, 0f, 0f);
        write(first, "2026/08/11", "12:00", 9f, null, null);
        write(second, "2026/06/10", "12:00", 1f, 1f, 1f);

        requestLatest(community, member, loginUser(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2026-07-01T00:00:00+02:00"))
                .andExpect(jsonPath("$.period.endDate").value("2026-07-31T23:00:00+02:00"))
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(4.0, TOLERANCE)))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(744))
                // 31 days * 24 hours * 2 supplies.
                .andExpect(jsonPath("$.coverage.expectedHours").value(1488))
                .andExpect(jsonPath("$.coverage.suppliesWithData").value(1));
    }

    /**
     * The shape that caused #382, with the figures observed for a real supply on 8 October 2026.
     * August is published: every hour carries consumption, self-consumption and surplus, except the
     * last one, which carries no self-consumption. September holds 287 hours of consumption,
     * 168.8 kWh, and the surplus the meter measured on 90 of them, 0.198 kWh in all, but no
     * self-consumption: Datadis has not published it. Resolving September would report 0.198 kWh
     * of assigned production against 168.8 kWh consumed; August is resolved instead.
     */
    @Test
    @DisplayName("ENM-001 AC8 a complete month followed by one holding only unpublished surplus resolves the complete month")
    void aCompleteMonthFollowedByOneHoldingOnlyUnpublishedSurplusResolvesTheCompleteMonth() throws Exception {
        when(clockProvider.now()).thenReturn(Instant.parse("2026-10-08T10:00:00Z"));
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity supply = persistSupply(member, community);
        // August 2026 in Madrid: 744 hours, the last one at 21:00Z on the 31st.
        writeAt(supply, hourly(Instant.parse("2026-07-31T22:00:00Z"), Instant.parse("2026-08-31T20:00:00Z")),
                0.2f, 0.3f, 0.5f);
        writeAt(supply, List.of(Instant.parse("2026-08-31T21:00:00Z")), 0.2f, null, 0.5f);
        // September 2026: its first 287 hours, 287 * 0.588 = 168.756 kWh consumed.
        writeAt(supply, hourly(Instant.parse("2026-08-31T22:00:00Z"), Instant.parse("2026-09-12T20:00:00Z")),
                0.588f, null, null);
        // 90 hours of measured surplus, 09:00 to 17:00 in Madrid on its first ten days: 90 * 0.0022.
        for (int day = 1; day <= 10; day++) {
            Instant nine = Instant.parse(String.format("2026-09-%02dT07:00:00Z", day));
            writeAt(supply, hourly(nine, nine.plus(Duration.ofHours(8))), 0.588f, null, 0.0022f);
        }

        requestLatest(community, member, loginUser(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2026-08-01T00:00:00+02:00"))
                .andExpect(jsonPath("$.period.endDate").value("2026-08-31T23:00:00+02:00"))
                // 743 * 0.3 self-consumed, 744 * 0.5 surplus.
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(222.9, 1e-3)))
                .andExpect(jsonPath("$.energy.surplusKWh").value(closeTo(372.0, 1e-3)))
                .andExpect(jsonPath("$.selfConsumptionRatio").value(closeTo(222.9 / 594.9, 1e-4)))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(744))
                .andExpect(jsonPath("$.coverage.expectedHours").value(744));
    }

    /**
     * August is published for both supplies but 62 of its hours -- local 22:00 and 23:00 of every
     * day -- have no record at all: 682 / 744 = 92 %, above the threshold. August is resolved, and
     * coverage still reports the missing hours.
     */
    @Test
    @DisplayName("ENM-005 AC6 coverage is reported for the resolved month and still reports its gaps")
    void coverageIsReportedForTheResolvedMonthAndStillReportsItsGaps() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity supply = persistSupply(member, community);
        writePublishedZeros(supply, YearMonth.of(2026, 8), 22, 23);
        write(supply, "2026/08/10", "12:00", 3f, 4f, 1f);

        requestLatest(community, member, loginUser(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2026-08-01T00:00:00+02:00"))
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(4.0, TOLERANCE)))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(682))
                .andExpect(jsonPath("$.coverage.expectedHours").value(744))
                .andExpect(jsonPath("$.coverage.supplyCount").value(1))
                .andExpect(jsonPath("$.coverage.suppliesWithData").value(1));
    }

    /**
     * The watched assumption behind resolving a month published for any supply. August is published
     * for one supply; the other holds consumption for every hour of it but no self-consumption yet.
     * August is resolved with the published supply's assigned production alone, and coverage, which
     * counts consumption records, reports the month complete: the missing publication shows nowhere
     * in the response. This is the cost the rule accepts because Datadis publishes a distributor's
     * month in one batch.
     */
    @Test
    @DisplayName("ENM-002 a month published for only one of the member's supplies is resolved")
    void aMonthPublishedForOnlyOneOfTheMembersSuppliesIsResolved() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity published = persistSupply(member, community);
        SupplyEntity lagging = persistSupply(member, community);
        writePublishedZeros(published, YearMonth.of(2026, 8));
        write(published, "2026/08/10", "12:00", 3f, 4f, 1f);
        writeAt(lagging, hourly(Instant.parse("2026-07-31T22:00:00Z"), Instant.parse("2026-08-31T21:00:00Z")),
                1f, null, null);

        requestLatest(community, member, loginUser(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2026-08-01T00:00:00+02:00"))
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(4.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.gridImportKWh").value(closeTo(747.0, TOLERANCE)))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(2 * 744))
                .andExpect(jsonPath("$.coverage.expectedHours").value(2 * 744))
                .andExpect(jsonPath("$.coverage.suppliesWithData").value(2));
    }

    @Test
    void theCurrentMonthIsNeverTheReferenceMonth() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        SupplyEntity supply = persistSupply(member, community);
        writePublishedZeros(supply, YearMonth.of(2026, 8));
        writePublishedZeros(supply, YearMonth.of(2026, 9));

        requestLatest(community, member, loginUser(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2026-08-01T00:00:00+02:00"));
    }

    // --- Savings: null only when no period could be resolved ---

    /**
     * The savings rule, first half: with no assigned production in the 24-month window there is no
     * period, so the amount is null, and so are the bounds, the ratios and the estimated price.
     * The record in August 2024 sits one month before the window.
     */
    @Test
    void savingsAreNullOnlyWhenNoPeriodCouldBeResolved() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        write(persistSupply(member, community), "2024/08/15", "12:00", 3f, 4f, 1f);

        requestLatest(community, member, loginUser(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value(nullValue()))
                .andExpect(jsonPath("$.period.endDate").value(nullValue()))
                .andExpect(jsonPath("$.energy.totalConsumptionKWh").value(0.0))
                .andExpect(jsonPath("$.energy.assignedProductionKWh").value(0.0))
                .andExpect(jsonPath("$.selfSufficiencyRatio").value(nullValue()))
                .andExpect(jsonPath("$.selfConsumptionRatio").value(nullValue()))
                .andExpect(jsonPath("$.savings.amountEur").value(nullValue()))
                .andExpect(content().string(containsString("\"estimatedPrice\":null")))
                .andExpect(jsonPath("$.coverage.expectedHours").value(0))
                .andExpect(jsonPath("$.coverage.supplyCount").value(1));
    }

    /**
     * The same rule for a membership without any supply: nothing to search, so no period, and the
     * same empty shape -- a successful response, not a 404.
     */
    @Test
    void savingsAreNullOnlyWhenNoPeriodCouldBeResolvedAlsoForAMembershipWithoutSupplies() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        requestLatest(community, member, loginUser(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value(nullValue()))
                .andExpect(jsonPath("$.period.endDate").value(nullValue()))
                .andExpect(jsonPath("$.energy.totalConsumptionKWh").value(0.0))
                .andExpect(jsonPath("$.selfSufficiencyRatio").value(nullValue()))
                .andExpect(jsonPath("$.selfConsumptionRatio").value(nullValue()))
                .andExpect(jsonPath("$.savings.amountEur").value(nullValue()))
                .andExpect(jsonPath("$.coverage.supplyCount").value(0));
    }

    /**
     * The savings rule, second half: an explicit period always resolves, so the amount is a figure
     * -- 0.00 when nothing was priced -- both for supplies without records in it and for a
     * membership without supplies.
     */
    @Test
    void savingsAreAFigureWheneverAPeriodExists() throws Exception {
        CommunityEntity community = persistCommunity();
        User withSupply = persistMember(community.getId());
        persistSupply(withSupply, community);
        User withoutSupplies = persistMember(community.getId());

        for (User member : List.of(withSupply, withoutSupplies)) {
            requestPeriod(community, member, loginUser(member), DAY_START, DAY_END)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.period.startDate").value(DAY_START))
                    .andExpect(jsonPath("$.period.endDate").value(DAY_END))
                    .andExpect(jsonPath("$.savings.amountEur").value(0.0))
                    .andExpect(jsonPath("$.selfSufficiencyRatio").value(nullValue()))
                    .andExpect(jsonPath("$.selfConsumptionRatio").value(nullValue()));
        }
    }

    /**
     * With the estimated tariff, the savings are an estimate and carry the configured price,
     * serialised exactly as configured, as the per-supply endpoint reports it.
     */
    @Test
    void estimatedSavingsCarryTheConfiguredEstimatedPrice() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        write(persistSupply(member, community), "2026/05/10", "10:00", 1f, 2f, 0f);
        write(persistSupply(member, community), "2026/05/10", "11:00", 1f, 3f, 0f);

        requestPeriod(community, member, loginUser(member), DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savings.tariffSource").value("ESTIMATE"))
                .andExpect(content().string(containsString("\"estimatedPrice\":{\"eurPerKWh\":0.15}")));
    }

    // --- Invalid periods ---

    @Test
    void aStartDateWithoutAnEndDateIsABadRequest() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        mockMvc.perform(get(PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(member))
                        .queryParam("startDate", DAY_START))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aStartDateAfterTheEndDateIsABadRequest() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        requestPeriod(community, member, loginUser(member), DAY_END, DAY_START)
                .andExpect(status().isBadRequest());
    }

    @Test
    void theReferencePeriodCombinedWithExplicitDatesIsABadRequest() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        mockMvc.perform(get(PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(member))
                        .queryParam("period", LATEST)
                        .queryParam("startDate", DAY_START)
                        .queryParam("endDate", DAY_END))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("period")));
    }

    @Test
    void anUnknownReferencePeriodIsABadRequest() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        mockMvc.perform(get(PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(member))
                        .queryParam("period", "LAST_YEAR"))
                .andExpect(status().isBadRequest());
    }

    // --- Authorization ---

    @Test
    void theMemberThemselfCanRead() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        requestLatest(community, member, loginUser(member)).andExpect(status().isOk());
    }

    @Test
    void aCommunityAdminOfThatCommunityCanRead() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        requestLatest(community, member, loginAsCommunityAdmin(community.getId())).andExpect(status().isOk());
    }

    @Test
    void anotherMemberOfTheSameCommunityIsAnsweredNotFound() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        requestLatest(community, member, loginAsCommunityMember(community.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAdminOfAnotherCommunityIsAnsweredNotFound() throws Exception {
        CommunityEntity community = persistCommunity();
        CommunityEntity otherCommunity = persistCommunity();
        User member = persistMember(community.getId());

        requestLatest(community, member, loginAsCommunityAdmin(otherCommunity.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aPlatformAdminWhoIsNeitherTheMemberNorAnAdminThereIsAnsweredNotFound() throws Exception {
        String platformAdminToken = loginAsDefaultPlatformAdmin();
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        requestLatest(community, member, platformAdminToken).andExpect(status().isNotFound());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());

        mockMvc.perform(get(PATH, community.getId(), member.getId()).queryParam("period", LATEST))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anAllowedCallerTargetingAMembershipThatDoesNotExistIsAnsweredNotFound() throws Exception {
        CommunityEntity community = persistCommunity();
        User outsider = persistUser();

        requestLatest(community, outsider, loginAsCommunityAdmin(community.getId()))
                .andExpect(status().isNotFound());
    }

    // --- fixtures ---

    private ResultActions requestLatest(CommunityEntity community, User member, String token) throws Exception {
        return mockMvc.perform(get(PATH, community.getId(), member.getId())
                .header(HttpHeaders.AUTHORIZATION, token)
                .queryParam("period", LATEST));
    }

    private ResultActions requestPeriod(CommunityEntity community, User member, String token, String startDate,
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
        createMembershipService.create(communityId, member.getId(), CommunityRole.COMMUNITY_MEMBER);
        return member;
    }

    private SupplyEntity persistSupply(User owner, CommunityEntity community) {
        return supplyRepository.save(SupplyEntityMother.random(
                userRepository.getReferenceById(owner.getId()), community));
    }

    /**
     * Writes a published month of zeros for the supply, leaving the given local hours without any
     * record; see {@link DatadisConsumptionInfluxFixture#writePublishedZeros}.
     */
    private void writePublishedZeros(SupplyEntity supply, YearMonth month, Integer... skippedLocalHours) {
        influxFixture.writePublishedZeros(supply.getCode(), month, ZONE, skippedLocalHours);
        track(supply);
    }

    /**
     * Writes one hourly record at each instant, all with the same values. A null energy value is
     * stored as an absent field.
     */
    private void writeAt(SupplyEntity supply, List<Instant> times, Float gridImportKWh, Float selfConsumptionKWh,
                         Float surplusKWh) {
        influxFixture.writeAt(supply.getCode(), times, gridImportKWh, selfConsumptionKWh, surplusKWh);
        track(supply);
    }

    /**
     * Every hour from {@code first} to {@code last}, both inclusive.
     */
    private static List<Instant> hourly(Instant first, Instant last) {
        return Stream.iterate(first, time -> !time.isAfter(last), time -> time.plus(Duration.ofHours(1))).toList();
    }

    /**
     * Writes one hourly record for the supply. A null energy value is stored as an absent field.
     */
    private void write(SupplyEntity supply, String date, String time, Float gridImportKWh,
                       Float selfConsumptionKWh, Float surplusKWh) {
        DatadisConsumption record = hourlyRecord(supply.getCode(), date, time, gridImportKWh,
                selfConsumptionKWh, surplusKWh);
        influxFixture.write(List.of(record));
        track(supply);
    }

    private void track(SupplyEntity supply) {
        if (!writtenCups.contains(supply.getCode())) {
            writtenCups.add(supply.getCode());
        }
    }
}
