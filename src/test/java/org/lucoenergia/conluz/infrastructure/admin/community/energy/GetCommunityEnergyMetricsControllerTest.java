package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.energy.CommunityEnergyMetrics;
import org.lucoenergia.conluz.domain.admin.community.energy.GetCommunityEnergyMetricsService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.price.get.GetPriceRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.energy.CommunityEnergyTestFixture.EnergyCommunity;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.web.KWhJson;
import org.lucoenergia.conluz.infrastructure.shared.web.KWhRounding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end over a real PostgreSQL and InfluxDB. The clock is fixed at 2026-10-15T12:00 in Madrid, so
 * the current month is October 2026 and the latest complete one September.
 */
@Transactional
class GetCommunityEnergyMetricsControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/communities/{communityId}/energy-metrics";
    private static final String LATEST = "LATEST_PUBLISHED_MONTH";
    private static final double TOLERANCE = 1e-9;
    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

    private static final String DAY_START = "2026-05-10T00:00:00+02:00";
    private static final String DAY_END = "2026-05-11T00:00:00+02:00";
    /** 10:00 on 10 May 2026 in Madrid. */
    private static final Instant TEN = Instant.parse("2026-05-10T08:00:00Z");

    /** The fixture's price window, from 10 September to 20 October 2025, as Madrid times. */
    private static final String INCOME_START = "2025-09-10T02:00:00+02:00";
    private static final String INCOME_END = "2025-10-20T02:00:00+02:00";
    /** 12:00 on 10 October 2025 in Madrid, priced by four quarter-hour prices. */
    private static final Instant QUARTERS_HOUR = Instant.parse("2025-10-10T10:00:00Z");
    /** 12:00 on 20 September 2025 in Madrid, priced by a single hourly price. */
    private static final Instant HOURLY_HOUR = Instant.parse("2025-09-20T10:00:00Z");
    /** 13:00 on 10 October 2025 in Madrid, without a price. */
    private static final Instant UNPRICED_HOUR = Instant.parse("2025-10-10T11:00:00Z");
    /** 14:00 on 10 October 2025 in Madrid, at a negative price. */
    private static final Instant NEGATIVE_HOUR = Instant.parse("2025-10-10T12:00:00Z");

    @Autowired
    private CommunityEnergyTestFixture fixture;
    @Autowired
    private GetCommunityEnergyMetricsService getCommunityEnergyMetricsService;
    @Autowired
    private GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;
    @Autowired
    private GetPriceRepository getPriceRepository;

    @BeforeEach
    void givenAFixedClock() {
        travelTo(NOW);
    }

    @AfterEach
    void clearWrittenSeries() {
        fixture.clear();
    }

    // --- Figures ---

    /**
     * Self-consumption 0.1234 + 0.2222 + 10 + 5 = 15.3456, which rounds to 15.346 where the rounded
     * figures would add up to 15.345. The disabled supply and the other community add nothing.
     */
    @Test
    @DisplayName("CEM-003 EVL-001 AC4 the totals are sums over the supplies in scope and the plants, rounded to 3 decimals")
    void theTotalsAreSumsOverTheSuppliesInScopeAndThePlants() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        writeAc4Day(community);
        EnergyCommunity other = fixture.createCommunity();
        writeAc4Day(other);
        fixture.writeConsumption(other.supplies().get(0), TEN, 1f, 1000f, 1000f);
        fixture.writeProduction(other.north(), TEN, 1000f);

        String body = requestPeriod(community.id(), loginUser(community.memberA()), DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2026-05-10T00:00:00+02:00"))
                .andExpect(jsonPath("$.period.endDate").value("2026-05-11T00:00:00+02:00"))
                .andExpect(jsonPath("$.installedPowerKWp").value(closeTo(150.5, TOLERANCE)))
                .andExpect(jsonPath("$.coverage.supplyCount").value(5))
                .andReturn().getResponse().getContentAsString();

        KWhJson.assertKWh(body, "/energy/selfConsumptionKWh", "15.346");
        KWhJson.assertKWh(body, "/energy/surplusKWh", "6");
        KWhJson.assertKWh(body, "/energy/productionKWh", "50");
        KWhJson.assertKWh(body, "/plants/0/productionKWh", "40.25");
        KWhJson.assertKWh(body, "/plants/1/productionKWh", "9.75");
    }

    @Test
    @DisplayName("CEM-002 AC3 a supply whose owner's membership is disabled is in scope; a disabled supply is not")
    void aSupplyOfADisabledMembershipIsInScopeAndADisabledSupplyIsNot() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        Supply ofDisabledMember = community.supplies().get(2);
        fixture.writeConsumption(ofDisabledMember, hoursOfDay(), 0f, 0.5f, 0.25f);
        fixture.writeConsumption(community.disabledSupply(), hoursOfDay(), 0f, 100f, 100f);

        requestPeriod(community.id(), loginUser(community.memberA()), DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(12.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.surplusKWh").value(closeTo(6.0, TOLERANCE)))
                .andExpect(jsonPath("$.coverage.supplyCount").value(5))
                .andExpect(jsonPath("$.coverage.suppliesWithData").value(1));
    }

    @Test
    @DisplayName("CEM-003 AC5 self-consumption 60 and surplus 40 give shares of 0.6 and 0.4")
    void selfConsumptionAndSurplusGiveTheShares() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeConsumption(community.supplies().get(0), TEN, 1f, 60f, 0f);
        fixture.writeConsumption(community.supplies().get(1), TEN, 1f, 0f, 40f);

        requestPeriod(community.id(), loginUser(community.memberA()), DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selfConsumptionRatio").value(closeTo(0.6, TOLERANCE)))
                .andExpect(jsonPath("$.surplusRatio").value(closeTo(0.4, TOLERANCE)));
    }

    @Test
    @DisplayName("CEM-003 AC5 both shares are null when self-consumption and surplus are both zero")
    void bothSharesAreNullWhenBothFiguresAreZero() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeConsumption(community.supplies().get(0), TEN, 1f, 0f, 0f);
        fixture.writeConsumption(community.supplies().get(1), TEN, 1f, 0f, 0f);

        requestPeriod(community.id(), loginUser(community.memberA()), DAY_START, DAY_END)
                .andExpect(status().isOk())
                // A measured zero stays 0.
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(0.0))
                .andExpect(jsonPath("$.energy.surplusKWh").value(0.0))
                .andExpect(jsonPath("$.selfConsumptionRatio").value(nullValue()))
                .andExpect(jsonPath("$.surplusRatio").value(nullValue()));
    }

    @Test
    @DisplayName("CEM-005 AC6 every plant is listed with its reference, power, production and share; a gap is null")
    void everyPlantIsListedAndAPlantWithoutRecordsHasNullProductionAndShare() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeProduction(community.north(), TEN, 30f);
        fixture.writeProduction(community.north(), TEN.plusSeconds(3600), 12f);

        requestPeriod(community.id(), loginUser(community.memberA()), DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plants.length()").value(2))
                .andExpect(jsonPath("$.plants[0].plant.id").value(community.north().getId().toString()))
                .andExpect(jsonPath("$.plants[0].plant.name").value("North"))
                .andExpect(jsonPath("$.plants[0].installedPowerKWp").value(closeTo(100.0, TOLERANCE)))
                .andExpect(jsonPath("$.plants[0].productionKWh").value(closeTo(42.0, TOLERANCE)))
                .andExpect(jsonPath("$.plants[0].productionShare").value(closeTo(1.0, TOLERANCE)))
                .andExpect(jsonPath("$.plants[1].plant.id").value(community.south().getId().toString()))
                .andExpect(jsonPath("$.plants[1].plant.name").value("South"))
                .andExpect(jsonPath("$.plants[1].installedPowerKWp").value(closeTo(50.5, TOLERANCE)))
                .andExpect(jsonPath("$.plants[1].productionKWh").value(nullValue()))
                .andExpect(jsonPath("$.plants[1].productionShare").value(nullValue()))
                .andExpect(jsonPath("$.energy.productionKWh").value(closeTo(42.0, TOLERANCE)))
                // No consumption record at all: a gap, not a measured zero.
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(nullValue()))
                .andExpect(jsonPath("$.energy.surplusKWh").value(nullValue()));
    }

    @Test
    @DisplayName("CEM-005 plant shares add up to the community's production")
    void plantSharesSplitTheCommunitysProduction() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeProduction(community.north(), TEN, 30f);
        fixture.writeProduction(community.south(), TEN, 10f);

        requestPeriod(community.id(), loginUser(community.memberA()), DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.energy.productionKWh").value(closeTo(40.0, TOLERANCE)))
                .andExpect(jsonPath("$.plants[0].productionShare").value(closeTo(0.75, TOLERANCE)))
                .andExpect(jsonPath("$.plants[1].productionShare").value(closeTo(0.25, TOLERANCE)));
    }

    // --- Anchor and coverage ---

    @Test
    @DisplayName("CEM-001 CEM-004 AC1 the latest month published for any supply resolves, with the supplies with data in it")
    void theLatestMonthPublishedForAnySupplyResolves() throws Exception {
        UUID communityId = fixture.createEmptyCommunity().getId();
        List<Supply> supplies = fixture.createCommunityWithSupplies(communityId, 2);
        Supply publishedInSeptember = supplies.get(0);
        Supply publishedInAugustOnly = supplies.get(1);
        writePublished(publishedInSeptember, YearMonth.of(2026, 8));
        writePublished(publishedInSeptember, YearMonth.of(2026, 9));
        writePublished(publishedInAugustOnly, YearMonth.of(2026, 8));
        // The current month is never a candidate, however much of it is published.
        writePublished(publishedInAugustOnly, YearMonth.of(2026, 10));

        requestLatest(communityId, loginUser(fixture.createMember(communityId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2026-09-01T00:00:00+02:00"))
                .andExpect(jsonPath("$.period.endDate").value("2026-10-01T00:00:00+02:00"))
                .andExpect(jsonPath("$.coverage.supplyCount").value(2))
                .andExpect(jsonPath("$.coverage.suppliesWithData").value(1));
    }

    @Test
    @DisplayName("CEM-001 AC2 with no published month the response is 200 with null period bounds and no figures")
    void withNoPublishedMonthTheResponseHasNullBoundsAndNoFigures() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        // Surplus alone is not publication.
        fixture.writeConsumption(community.supplies().get(0), hoursOf(YearMonth.of(2026, 9)), 1f, null, 2f);
        fixture.writeProduction(community.north(), Instant.parse("2026-09-10T10:00:00Z"), 5f);

        requestLatest(community.id(), loginUser(community.memberA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value(nullValue()))
                .andExpect(jsonPath("$.period.endDate").value(nullValue()))
                .andExpect(jsonPath("$.energy.productionKWh").value(nullValue()))
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(nullValue()))
                .andExpect(jsonPath("$.energy.surplusKWh").value(nullValue()))
                .andExpect(jsonPath("$.selfConsumptionRatio").value(nullValue()))
                .andExpect(jsonPath("$.surplusRatio").value(nullValue()))
                .andExpect(jsonPath("$.installedPowerKWp").value(closeTo(150.5, TOLERANCE)))
                .andExpect(jsonPath("$.coverage.supplyCount").value(5))
                .andExpect(jsonPath("$.coverage.suppliesWithData").value(0))
                .andExpect(jsonPath("$.plants.length()").value(2))
                .andExpect(jsonPath("$.plants[0].productionKWh").value(nullValue()))
                .andExpect(jsonPath("$.plants[0].productionShare").value(nullValue()));
    }

    /**
     * September 2026 spans 720 hours, none of them a daylight saving change: 648 is exactly 90 %.
     */
    @Test
    @DisplayName("CEM-004 AC12 a supply with self-consumption in 648 of 720 hours has data, one with 647 has not")
    void aSupplyHasDataFromNinetyPercentOfThePossibleHours() throws Exception {
        UUID communityId = fixture.createEmptyCommunity().getId();
        List<Supply> supplies = fixture.createCommunityWithSupplies(communityId, 3);
        List<Instant> september = hoursOf(YearMonth.of(2026, 9));
        fixture.writeConsumption(supplies.get(0), september.subList(0, 648), 1f, 0f, 0f);
        fixture.writeConsumption(supplies.get(1), september.subList(0, 647), 1f, 0f, 0f);
        // Consumption and surplus in every hour, but self-consumption in none.
        fixture.writeConsumption(supplies.get(2), september, 1f, null, 1f);

        requestPeriod(communityId, loginUser(fixture.createMember(communityId)), "2026-09-01T00:00:00+02:00",
                "2026-10-01T00:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverage.supplyCount").value(3))
                .andExpect(jsonPath("$.coverage.suppliesWithData").value(1));
    }

    // --- Period ---

    @Test
    @DisplayName("EPR-001 AC9 the period is half-open: a record on the end bound is not counted")
    void thePeriodIsHalfOpen() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        Instant start = Instant.parse("2026-05-09T22:00:00Z");
        Instant end = Instant.parse("2026-05-10T22:00:00Z");
        fixture.writeConsumption(community.supplies().get(0), start, 1f, 1f, 0f);
        fixture.writeConsumption(community.supplies().get(0), end, 1f, 100f, 0f);
        fixture.writeProduction(community.north(), start, 2f);
        fixture.writeProduction(community.north(), end, 200f);

        requestPeriod(community.id(), loginUser(community.memberA()), DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(1.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.productionKWh").value(closeTo(2.0, TOLERANCE)));
    }

    @Test
    @DisplayName("EPR-002 AC9 an end after the start of the current hour is clipped to it")
    void anEndAfterTheCurrentHourIsClipped() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        requestPeriod(community.id(), loginUser(community.memberA()), "2026-10-15T00:00:00+02:00",
                "2026-10-20T00:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.endDate").value("2026-10-15T12:00:00+02:00"));
    }

    @Test
    @DisplayName("EPR-003 AC9 a start at the start of the current hour is rejected")
    void aStartAtTheCurrentHourIsRejected() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        requestPeriod(community.id(), loginUser(community.memberA()), "2026-10-15T12:00:00+02:00",
                "2026-10-15T13:00:00+02:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_START_IN_FUTURE"));
    }

    @Test
    @DisplayName("EPR-001 a start that is not before the end is rejected")
    void aStartThatIsNotBeforeTheEndIsRejected() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        requestPeriod(community.id(), loginUser(community.memberA()), DAY_START, DAY_START)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_START_AFTER_END"));
    }

    @Test
    @DisplayName("EPR-004 AC8 a period longer than 24 months is answered with its months limit")
    void aPeriodLongerThanTwentyFourMonthsIsRejected() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        requestPeriod(community.id(), loginUser(community.memberA()), "2024-01-01T00:00:00+01:00",
                "2026-01-01T01:00:00+01:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_TOO_LONG"))
                .andExpect(jsonPath("$.errors[0].params.amount").value("24"))
                .andExpect(jsonPath("$.errors[0].params.unit").value("MONTHS"));
    }

    @Test
    void aReferencePeriodCombinedWithADateIsRejected() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        mockMvc.perform(get(PATH, community.id())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(community.memberA()))
                        .queryParam("period", LATEST)
                        .queryParam("startDate", DAY_START))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_CONFLICTING"));
    }

    // --- Access ---

    @Test
    @DisplayName("CEM-007 AC10 an active member of any role reads the metrics")
    void anActiveMemberOfAnyRoleReadsTheMetrics() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        requestPeriod(community.id(), loginUser(community.memberA()), DAY_START, DAY_END)
                .andExpect(status().isOk());
        requestPeriod(community.id(), loginAsCommunityAdmin(community.id()), DAY_START, DAY_END)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CEM-007 AC10 a platform admin without a membership is answered 403")
    void aPlatformAdminWithoutAMembershipIsForbidden() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        requestPeriod(community.id(), loginAsDefaultPlatformAdmin(), DAY_START, DAY_END)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CEM-007 AC10 a non-member, a member of another community and a disabled member are answered 404")
    void anyoneElseIsAnsweredNotFound() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        EnergyCommunity other = fixture.createCommunity();

        requestPeriod(community.id(), loginAsPartner(), DAY_START, DAY_END)
                .andExpect(status().isNotFound());
        requestPeriod(community.id(), loginUser(other.memberA()), DAY_START, DAY_END)
                .andExpect(status().isNotFound());
        requestPeriod(community.id(), loginUser(community.disabledMember()), DAY_START, DAY_END)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CEM-007 an anonymous caller is answered 401")
    void anAnonymousCallerIsUnauthorized() throws Exception {
        EnergyCommunity community = fixture.createCommunity();

        mockMvc.perform(get(PATH, community.id())
                        .queryParam("startDate", DAY_START)
                        .queryParam("endDate", DAY_END))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CEM-007 AC11 the response carries no supply id, CUPS or member reference")
    void theResponseCarriesNoSupplyIdCupsOrMemberReference() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        writeAc4Day(community);

        String body = requestPeriod(community.id(), loginUser(community.memberA()), DAY_START, DAY_END)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertNoSupplyOrMember(body, community);
    }

    // --- Surplus income ---

    @Test
    @DisplayName("CEM-008 CEM-009 AC1 AC2 AC3 AC4 AC7 each hour's surplus is priced at its mean OMIE price; an unpriced hour is reported apart")
    void eachHoursSurplusIsPricedAtItsMeanPrice() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        writeIncomeHours(community);
        EnergyCommunity other = fixture.createCommunity();
        fixture.writeConsumption(other.supplies().get(0), List.of(QUARTERS_HOUR, HOURLY_HOUR, UNPRICED_HOUR,
                NEGATIVE_HOUR), 1f, 0f, 1000f);
        writeIncomePrices();

        String body = requestPeriod(community.id(), loginUser(community.memberA()), INCOME_START, INCOME_END)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 5 × 0.07 + 2 × 0.05 + 4 × −0.01 = 0.35 + 0.10 − 0.04; the other community adds nothing.
        assertAmount(body, "0.41");
        KWhJson.assertKWh(body, "/surplusIncome/unpricedSurplusKWh", "1.5");
        KWhJson.assertKWh(body, "/energy/surplusKWh", "12.5");
    }

    @Test
    @DisplayName("CEM-008 CEM-010 AC8 the surplus of a supply whose owner's membership is disabled is priced")
    void theSurplusOfADisabledMembersSupplyIsPriced() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeConsumption(community.supplies().get(2), QUARTERS_HOUR, 1f, 0f, 1f);
        fixture.writeConsumption(community.disabledSupply(), QUARTERS_HOUR, 1f, 0f, 500f);
        writeIncomePrices();

        String body = requestPeriod(community.id(), loginUser(community.memberA()), INCOME_START, INCOME_END)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertAmount(body, "0.07");
        KWhJson.assertKWh(body, "/surplusIncome/unpricedSurplusKWh", "0");
    }

    @Test
    @DisplayName("CEM-008 CEM-009 AC5 a surplus of zero in every hour earns 0.00 with no unpriced surplus")
    void aSurplusOfZeroInEveryHourEarnsNothing() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeConsumption(community.supplies().get(0), List.of(QUARTERS_HOUR, UNPRICED_HOUR), 1f, 1f, 0f);
        fixture.writeConsumption(community.supplies().get(1), NEGATIVE_HOUR, 1f, 1f, 0f);
        writeIncomePrices();

        String body = requestPeriod(community.id(), loginUser(community.memberA()), INCOME_START, INCOME_END)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertAmount(body, "0.00");
        KWhJson.assertKWh(body, "/surplusIncome/unpricedSurplusKWh", "0");
    }

    @Test
    @DisplayName("CEM-010 AC6 both figures are null when no supply stored a record in the period")
    void bothFiguresAreNullWhenNoSupplyStoredARecord() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        fixture.writeProduction(community.north(), QUARTERS_HOUR, 5f);
        writeIncomePrices();

        requestPeriod(community.id(), loginUser(community.memberA()), INCOME_START, INCOME_END)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.energy.surplusKWh").value(nullValue()))
                .andExpect(jsonPath("$.surplusIncome.amountEur").value(nullValue()))
                .andExpect(jsonPath("$.surplusIncome.unpricedSurplusKWh").value(nullValue()));
    }

    @Test
    @DisplayName("CEM-010 AC6 both figures are null when no period resolves")
    void bothFiguresAreNullWhenNoPeriodResolves() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        writeIncomePrices();

        requestLatest(community.id(), loginUser(community.memberA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value(nullValue()))
                .andExpect(jsonPath("$.surplusIncome.amountEur").value(nullValue()))
                .andExpect(jsonPath("$.surplusIncome.unpricedSurplusKWh").value(nullValue()));
    }

    /**
     * The invariant behind CEM-010, checked before any rounding: the hours the income prices and the
     * hours it reports unpriced add up to the surplus of the same supplies over the same period.
     */
    @Test
    @DisplayName("CEM-010 the priced and the unpriced surplus add up to the surplus of the supplies in scope")
    void thePricedAndTheUnpricedSurplusAddUpToTheSurplus() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        writeIncomeHours(community);
        fixture.writeConsumption(community.disabledSupply(), QUARTERS_HOUR, 1f, 0f, 500f);
        writeIncomePrices();
        OffsetDateTime start = OffsetDateTime.parse(INCOME_START);
        OffsetDateTime end = OffsetDateTime.parse(INCOME_END);
        List<Supply> inScope = new ArrayList<>(community.supplies());
        inScope.add(community.north().getSupply());
        inScope.add(community.south().getSupply());

        Map<Instant, Double> surplusByHour = getDatadisConsumptionAggregateRepository.sumSurplusByHour(inScope,
                start.toInstant(), end.toInstant(), ZONE);
        Map<Instant, Double> priceByHour = getPriceRepository.meanPriceByHour(start.toInstant(), end.toInstant(),
                ZONE);
        double surplus = surplusByHour.values().stream().mapToDouble(Double::doubleValue).sum();
        double priced = surplusByHour.entrySet().stream().filter(hour -> priceByHour.containsKey(hour.getKey()))
                .mapToDouble(Map.Entry::getValue).sum();
        BigDecimal amount = surplusByHour.entrySet().stream().filter(hour -> priceByHour.containsKey(hour.getKey()))
                .map(hour -> BigDecimal.valueOf(hour.getValue())
                        .multiply(BigDecimal.valueOf(priceByHour.get(hour.getKey()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CommunityEnergyMetrics metrics = getCommunityEnergyMetricsService.getEnergyMetrics(community.id(), start, end,
                null);

        assertTrue(priced > 0 && metrics.getUnpricedSurplusKWh() > 0, "both parts must be exercised");
        assertEquals(surplus, priced + metrics.getUnpricedSurplusKWh(), TOLERANCE);
        assertEquals(0, amount.compareTo(metrics.getSurplusIncomeEur()),
                () -> "expected " + amount + ", was " + metrics.getSurplusIncomeEur());
        assertEquals(KWhRounding.round(metrics.getSurplusKWh()), KWhRounding.round(surplus));
    }

    @Test
    @DisplayName("CEM-007 AC10 the surplus income carries two figures and no supply, member or price")
    void theSurplusIncomeCarriesTwoFiguresAndNoSupplyMemberOrPrice() throws Exception {
        EnergyCommunity community = fixture.createCommunity();
        writeIncomeHours(community);
        writeIncomePrices();

        String body = requestPeriod(community.id(), loginUser(community.memberA()), INCOME_START, INCOME_END)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        List<String> incomeKeys = new ArrayList<>();
        json.path("surplusIncome").fieldNames().forEachRemaining(incomeKeys::add);
        assertEquals(List.of("amountEur", "unpricedSurplusKWh"), incomeKeys.stream().sorted().toList());
        List<String> keys = new ArrayList<>();
        collectKeys(json, keys);
        // "unpriced" names the surplus without a price, not a price.
        assertEquals(List.of("unpricedSurplusKWh"),
                keys.stream().filter(key -> key.toLowerCase(Locale.ROOT).contains("price")).toList(),
                () -> "the body carries a price: " + keys);
        assertNoSupplyOrMember(body, community);
    }

    // --- helpers ---

    /**
     * Every consumer supply, a plant's own supply and both plants write on 10 May, as does the disabled
     * supply, which is out of scope. Self-consumption 0.1234 + 0.2222 + 10 + 5 and surplus 1 + 2 + 3 + 0;
     * production 40.25 + 9.75.
     */
    private void writeAc4Day(EnergyCommunity community) {
        fixture.writeConsumption(community.supplies().get(0), TEN, 1f, 0.1234f, 1f);
        fixture.writeConsumption(community.supplies().get(1), TEN, 1f, 0.2222f, 2f);
        fixture.writeConsumption(community.supplies().get(2), TEN, 1f, 10f, 3f);
        fixture.writeConsumption(community.north().getSupply(), TEN, 1f, 5f, 0f);
        fixture.writeConsumption(community.disabledSupply(), TEN, 1f, 500f, 500f);
        fixture.writeProduction(community.north(), TEN, 40.25f);
        fixture.writeProduction(community.south(), TEN.plusSeconds(3600), 9.75f);
    }

    /**
     * Surplus of supply A in the four kinds of hour, and of supply B in two of them: 5 kWh in the
     * quarter-hour priced hour, 2 in the hourly priced one, 1.5 unpriced and 4 at a negative price.
     */
    private void writeIncomeHours(EnergyCommunity community) {
        fixture.writeConsumption(community.supplies().get(0), QUARTERS_HOUR, 1f, 0f, 2f);
        fixture.writeConsumption(community.supplies().get(0), HOURLY_HOUR, 1f, 0f, 2f);
        fixture.writeConsumption(community.supplies().get(0), UNPRICED_HOUR, 1f, 0f, 1.5f);
        fixture.writeConsumption(community.supplies().get(1), QUARTERS_HOUR, 1f, 0f, 3f);
        fixture.writeConsumption(community.supplies().get(1), NEGATIVE_HOUR, 1f, 0f, 4f);
    }

    /**
     * Four quarter-hour prices averaging 0.07 €/kWh, a single hourly price of 0.05 and a negative price
     * of −0.01; none in the unpriced hour. A price in the hour before the quarter-hour one, outside every
     * hour with surplus, must never be averaged in.
     */
    private void writeIncomePrices() {
        fixture.writePrice(QUARTERS_HOUR, 0.04);
        fixture.writePrice(QUARTERS_HOUR.plus(Duration.ofMinutes(15)), 0.06);
        fixture.writePrice(QUARTERS_HOUR.plus(Duration.ofMinutes(30)), 0.08);
        fixture.writePrice(QUARTERS_HOUR.plus(Duration.ofMinutes(45)), 0.10);
        fixture.writePrice(QUARTERS_HOUR.minus(Duration.ofMinutes(15)), 9.99);
        fixture.writePrice(HOURLY_HOUR, 0.05);
        fixture.writePrice(NEGATIVE_HOUR, -0.01);
    }

    /**
     * Asserts the amount as the response wrote it, in the body's own text: a JSON reader would turn
     * {@code 0.00} into {@code 0} and hide the 2 decimals.
     */
    private static void assertAmount(String body, String expected) throws Exception {
        assertEquals(0, new BigDecimal(expected).compareTo(KWhJson.kWhAt(body, "/surplusIncome/amountEur")),
                () -> "amountEur is not " + expected + " in " + body);
        assertTrue(body.contains("\"amountEur\":" + expected + ","),
                () -> "amountEur is not written as " + expected + " in " + body);
    }

    private static void collectKeys(JsonNode node, List<String> keys) {
        node.fieldNames().forEachRemaining(keys::add);
        node.forEach(child -> collectKeys(child, keys));
    }

    static void assertNoSupplyOrMember(String body, EnergyCommunity community) {
        for (Supply supply : community.allSupplies()) {
            assertFalse(body.contains(supply.getId().toString()), () -> "the body carries a supply id: " + body);
            assertFalse(body.contains(supply.getCode()), () -> "the body carries a CUPS: " + body);
        }
        for (User member : community.members()) {
            assertFalse(body.contains(member.getId().toString()), () -> "the body carries a user id: " + body);
            assertFalse(body.contains(member.getPersonalId()), () -> "the body carries a personal id: " + body);
        }
    }

    private void writePublished(Supply supply, YearMonth month) {
        fixture.writeConsumption(supply, hoursOf(month), 0f, 0f, 0f);
    }

    /** Every hour of 10 May 2026 in Madrid. */
    private static List<Instant> hoursOfDay() {
        Instant start = Instant.parse("2026-05-09T22:00:00Z");
        return Stream.iterate(start, time -> time.isBefore(start.plus(Duration.ofDays(1))),
                time -> time.plus(Duration.ofHours(1))).toList();
    }

    static List<Instant> hoursOf(YearMonth month) {
        Instant start = month.atDay(1).atStartOfDay(ZONE).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant();
        return Stream.iterate(start, time -> time.isBefore(end), time -> time.plus(Duration.ofHours(1))).toList();
    }

    private ResultActions requestLatest(UUID communityId, String token) throws Exception {
        return mockMvc.perform(get(PATH, communityId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .queryParam("period", LATEST));
    }

    private ResultActions requestPeriod(UUID communityId, String token, String startDate, String endDate)
            throws Exception {
        return mockMvc.perform(get(PATH, communityId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate));
    }
}
