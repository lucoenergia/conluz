package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.contract.SupplyContract;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.datadis.RoundingNoiseDataset;

import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;
import static org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture.hourlyRecord;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.lucoenergia.conluz.infrastructure.shared.web.KWhJson.assertKWh;

/**
 * Tests written from the endpoint's acceptance criteria. Each test owns its dataset under a unique
 * CUPS, so nothing it writes to the shared InfluxDB container can reach another test.
 */
@Transactional
class GetSupplyEnergyMetricsControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/supplies";
    private static final String PATH = "/energy-metrics";
    private static final double TOLERANCE = 1e-6;
    private static final String LATEST = "LATEST_PUBLISHED_MONTH";
    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    /**
     * October 2026 is the current month: the search window runs from October 2024 to September
     * 2026, and October itself is never a candidate.
     */
    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    /**
     * Every record of these local hours is left out of a month, so 20 of its 24 hours a day carry a
     * record: 83 %, below the publication threshold.
     */
    private static final Integer[] FOUR_HOURS_A_DAY = {20, 21, 22, 23};

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private DatadisConsumptionInfluxFixture fixture;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CommunityMembershipJpaRepository communityMembershipJpaRepository;
    @Autowired
    @Qualifier("getDatadisConsumptionRepositoryInflux")
    private GetDatadisConsumptionRepository getDatadisConsumptionRepository;

    private final List<String> createdCupsCodes = new ArrayList<>();

    @AfterEach
    void afterEach() {
        createdCupsCodes.forEach(fixture::clear);
    }

    /**
     * Three records of materially different magnitude. The weighted ratios differ from the mean of
     * the per-record ratios, which is what makes this dataset able to tell the two apart: averaging
     * would give exactly 0.5 for self-sufficiency and 0.5300 for self-consumption.
     */
    @Test
    void testRatiosAreWeightedBySumsRatherThanAveragedPerRecord() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        writeRecords(
                hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 1.0f, 1.0f, 9.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "01:00", 100.0f, 50.0f, 0.5f),
                hourlyRecord(supply.getCode(), "2024/02/01", "02:00", 1.0f, 2.0f, 2.0f));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2024-02-01T00:00:00+01:00")
                        .queryParam("endDate", "2024-02-01T03:00:00+01:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supply.id").value(supply.getId().toString()))
                .andExpect(jsonPath("$.supply.code").value(supply.getCode()))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(3))
                .andExpect(jsonPath("$.coverage.expectedHours").value(3))
                // The sums the ratios are derived from.
                .andExpect(jsonPath("$.energy.totalConsumptionKWh").value(closeTo(155.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.gridImportKWh").value(closeTo(102.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(53.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.surplusKWh").value(closeTo(11.5, TOLERANCE)))
                .andExpect(jsonPath("$.energy.assignedProductionKWh").value(closeTo(64.5, TOLERANCE)))
                // Sum first, divide once...
                .andExpect(jsonPath("$.selfSufficiencyRatio").value(closeTo(53.0 / 155.0, TOLERANCE)))
                .andExpect(jsonPath("$.selfConsumptionRatio").value(closeTo(53.0 / 64.5, TOLERANCE)))
                // ...which is not the mean of the per-record ratios.
                .andExpect(jsonPath("$.selfSufficiencyRatio").value(not(closeTo(0.5, 1e-3))))
                .andExpect(jsonPath("$.selfConsumptionRatio").value(not(closeTo(0.530033, 1e-3))));
    }

    @Test
    void testZeroTotalConsumptionYieldsANullSelfSufficiencyRatio() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        writeRecords(hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 0.0f, 0.0f, 5.0f));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2024-02-01T00:00:00+01:00")
                        .queryParam("endDate", "2024-02-01T01:00:00+01:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.energy.totalConsumptionKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.selfSufficiencyRatio").value(nullValue()))
                .andExpect(jsonPath("$.selfConsumptionRatio").value(0.0));
    }

    @Test
    void testZeroAssignedProductionYieldsANullSelfConsumptionRatio() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        writeRecords(hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 10.0f, 0.0f, 0.0f));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2024-02-01T00:00:00+01:00")
                        .queryParam("endDate", "2024-02-01T01:00:00+01:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.energy.assignedProductionKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.selfConsumptionRatio").value(nullValue()))
                // Zero self-consumption over a non-zero consumption is a ratio of zero, not null.
                .andExpect(jsonPath("$.selfSufficiencyRatio").value(0.0));
    }

    @Test
    void testRecordsWithoutSelfConsumptionFieldsAreTreatedAsZero() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        // A supply without self-consumption: the distributor reports neither field, so neither is
        // stored and summing them returns null rather than zero.
        writeRecords(
                hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 5.0f, null, null),
                hourlyRecord(supply.getCode(), "2024/02/01", "01:00", 7.0f, null, null));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2024-02-01T00:00:00+01:00")
                        .queryParam("endDate", "2024-02-01T02:00:00+01:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverage.hoursWithData").value(2))
                .andExpect(jsonPath("$.energy.gridImportKWh").value(closeTo(12.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.totalConsumptionKWh").value(closeTo(12.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.surplusKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.assignedProductionKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.selfConsumptionRatio").value(nullValue()))
                .andExpect(jsonPath("$.selfSufficiencyRatio").value(0.0));
    }

    @Test
    @DisplayName("EPR-001 an unbounded request spans from the first stored record to the end of the hour of the last")
    void testUnboundedRequestSpansFromTheFirstStoredRecordToTheLast() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        writeRecords(
                hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 1.0f, 1.0f, 1.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "01:00", 2.0f, 2.0f, 2.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "02:00", 3.0f, 3.0f, 3.0f));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2024-02-01T00:00:00+01:00"))
                .andExpect(jsonPath("$.period.endDate").value("2024-02-01T03:00:00+01:00"))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(3))
                .andExpect(jsonPath("$.coverage.expectedHours").value(3))
                .andExpect(jsonPath("$.energy.gridImportKWh").value(closeTo(6.0, TOLERANCE)));
    }

    /**
     * The period of an unbounded request comes from the stored records, never from the supply's
     * contract date.
     */
    @Test
    void testUnboundedRequestIgnoresTheContractStartDate() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        User owner = createUserRepository.create(UserMother.randomUser());
        String code = randomCupsCode();
        Supply supply = createSupplyRepository.create(
                SupplyMother.random(owner)
                        .withCode(code)
                        .withContract(new SupplyContract.Builder()
                                .withValidDateFrom(LocalDate.of(2015, 6, 30))
                                .build())
                        .build(),
                UserId.of(owner.getId()), DEFAULT_COMMUNITY_ID);

        writeRecords(hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 1.0f, 1.0f, 1.0f));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2024-02-01T00:00:00+01:00"))
                .andExpect(jsonPath("$.period.endDate").value("2024-02-01T01:00:00+01:00"));
    }

    @Test
    void testPartiallyCoveredPeriodReportsTheGapAndSumsOnlyTheRecordsPresent() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        writeRecords(
                hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 1.0f, 1.0f, 1.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "01:00", 2.0f, 2.0f, 2.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "02:00", 3.0f, 3.0f, 3.0f));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2024-02-01T00:00:00+01:00")
                        .queryParam("endDate", "2024-02-02T00:00:00+01:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverage.expectedHours").value(24))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(3))
                // The 21 hours with no record contribute nothing; they are not zeros in the sums.
                .andExpect(jsonPath("$.energy.gridImportKWh").value(closeTo(6.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(6.0, TOLERANCE)))
                .andExpect(jsonPath("$.selfSufficiencyRatio").value(closeTo(0.5, TOLERANCE)));
    }

    @Test
    void testSupplyWithoutAnyRecordReturnsAnEmptyResultAndNotANotFound() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supply.id").value(supply.getId().toString()))
                .andExpect(jsonPath("$.period.startDate").value(nullValue()))
                .andExpect(jsonPath("$.period.endDate").value(nullValue()))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(0))
                .andExpect(jsonPath("$.coverage.expectedHours").value(0))
                .andExpect(jsonPath("$.energy.totalConsumptionKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.gridImportKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.surplusKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.assignedProductionKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.selfSufficiencyRatio").value(nullValue()))
                .andExpect(jsonPath("$.selfConsumptionRatio").value(nullValue()))
                // Pins the serialisation: the keys are present and explicitly null.
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"selfSufficiencyRatio\":null")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"selfConsumptionRatio\":null")));
    }

    @Test
    void testExplicitPeriodWithoutAnyRecordStillResolvesThePeriod() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        // The supply has records, just none inside the requested period.
        writeRecords(hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 1.0f, 1.0f, 1.0f));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2025-01-01T00:00:00+01:00")
                        .queryParam("endDate", "2025-01-02T00:00:00+01:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2025-01-01T00:00:00+01:00"))
                .andExpect(jsonPath("$.period.endDate").value("2025-01-02T00:00:00+01:00"))
                .andExpect(jsonPath("$.coverage.expectedHours").value(24))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(0))
                .andExpect(jsonPath("$.energy.totalConsumptionKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.gridImportKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.surplusKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.assignedProductionKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.selfSufficiencyRatio").value(nullValue()))
                .andExpect(jsonPath("$.selfConsumptionRatio").value(nullValue()));
    }

    /**
     * The period is half-open, so the record sitting exactly on startDate counts and the one sitting
     * exactly on endDate does not.
     */
    @Test
    @DisplayName("EPR-001 AC1 the record on startDate counts and the one on endDate does not")
    void testTheStartBoundIsIncludedAndTheEndBoundExcluded() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        writeRecords(
                hourlyRecord(supply.getCode(), "2024/02/01", "10:00", 1.0f, 0.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "11:00", 2.0f, 0.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "12:00", 4.0f, 0.0f, 0.0f));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2024-02-01T10:00:00+01:00")
                        .queryParam("endDate", "2024-02-01T12:00:00+01:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverage.hoursWithData").value(2))
                .andExpect(jsonPath("$.coverage.expectedHours").value(2))
                // The record on the start bound is in; the one on the end bound is out.
                .andExpect(jsonPath("$.energy.gridImportKWh").value(closeTo(3.0, TOLERANCE)));
    }

    /**
     * The local day the clocks go forward has 23 hours, so a fully covered one must not report 24.
     */
    @Test
    @DisplayName("EPR-001 AC1 the spring daylight saving day spans 23 hours")
    void testExpectedHoursFollowsTheSpringDaylightSavingTransition() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        // 2023-03-26 in Europe/Madrid: 02:00 does not exist, the day runs 00:00, 01:00, 03:00..23:00.
        List<DatadisConsumption> records = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            if (hour == 2) {
                continue;
            }
            records.add(hourlyRecord(supply.getCode(), "2023/03/26",
                    String.format("%02d:00", hour), 1.0f, 0.0f, 0.0f));
        }
        writeRecords(records.toArray(new DatadisConsumption[0]));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2023-03-26T00:00:00+01:00"))
                .andExpect(jsonPath("$.period.endDate").value("2023-03-27T00:00:00+02:00"))
                .andExpect(jsonPath("$.coverage.expectedHours").value(23))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(23));
    }

    /**
     * The local day the clocks go back has 25 hours. Writing each local hour label once covers only
     * 24 of them -- the second 02:00 is never written -- which is exactly the gap coverage reports.
     */
    @Test
    @DisplayName("EPR-001 AC1 the autumn daylight saving day spans 25 hours")
    void testExpectedHoursFollowsTheAutumnDaylightSavingTransition() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        // 2023-10-29 in Europe/Madrid: 02:00 happens twice, so the day runs 25 hours.
        List<DatadisConsumption> records = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            records.add(hourlyRecord(supply.getCode(), "2023/10/29",
                    String.format("%02d:00", hour), 1.0f, 0.0f, 0.0f));
        }
        writeRecords(records.toArray(new DatadisConsumption[0]));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2023-10-29T00:00:00+02:00"))
                .andExpect(jsonPath("$.period.endDate").value("2023-10-30T00:00:00+01:00"))
                .andExpect(jsonPath("$.coverage.expectedHours").value(25))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(24));
    }

    // --- Estimated savings ---

    /**
     * AC1. The configured estimate is 0.15 EUR/kWh at a VAT rate of 0, so a period whose
     * self-consumption is 1 + 50 + 2 = 53 kWh is worth 53 x 0.15 = 7.95 EUR. The three records
     * differ in magnitude, so an amount derived from a count of records rather than from the kWh
     * could not land on this figure.
     */
    @Test
    void testSavingsPriceTheSelfConsumedEnergyAtTheEstimatedTariff() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        writeRecords(
                hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 1.0f, 1.0f, 9.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "01:00", 100.0f, 50.0f, 0.5f),
                hourlyRecord(supply.getCode(), "2024/02/01", "02:00", 1.0f, 2.0f, 2.0f));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2024-02-01T00:00:00+01:00")
                        .queryParam("endDate", "2024-02-01T03:00:00+01:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savings.amountEur").value(closeTo(7.95, TOLERANCE)))
                .andExpect(jsonPath("$.savings.tariffSource").value("ESTIMATE"))
                // Two decimals exactly, not 7.949999... or 7.9.
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"amountEur\":7.95")));
    }

    /**
     * AC2. No stored record and no requested period: there is no period to price, so the amount
     * is absent. The object itself is still there, and so is its source.
     */
    @Test
    void testSupplyWithoutAnyRecordAndWithoutAPeriodReportsAnAbsentAmount() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value(nullValue()))
                .andExpect(jsonPath("$.savings").exists())
                .andExpect(jsonPath("$.savings.amountEur").value(nullValue()))
                .andExpect(jsonPath("$.savings.tariffSource").value("ESTIMATE"))
                // Pins the serialisation: the key is present and explicitly null.
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"amountEur\":null")));
    }

    // --- Estimated price (#315) ---

    /**
     * AC1 (#315). The default configuration prices the estimate at 0.15 EUR/kWh, and that is the
     * price the savings carry, serialised exactly as configured rather than rounded to cents.
     */
    @Test
    void testEstimatedSavingsCarryTheConfiguredEstimatedPrice() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        writeRecords(
                hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 1.0f, 1.0f, 9.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "01:00", 100.0f, 50.0f, 0.5f));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2024-02-01T00:00:00+01:00")
                        .queryParam("endDate", "2024-02-01T01:00:00+01:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savings.tariffSource").value("ESTIMATE"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"estimatedPrice\":{\"eurPerKWh\":0.15}")));
    }

    /**
     * AC3 (#315). No stored record and no requested period: nothing was priced, so there is no
     * price behind the absent amount. The key is present and explicitly null, never an object
     * holding a null price.
     */
    @Test
    void testSupplyWithoutAnyRecordAndWithoutAPeriodReportsNoEstimatedPrice() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savings.amountEur").value(nullValue()))
                .andExpect(jsonPath("$.savings.tariffSource").value("ESTIMATE"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"estimatedPrice\":null")));
    }

    /**
     * The "Estimate · price before taxes" label shown next to the amount is only truthful if the
     * amount is the self-consumed energy times that price, rounded once. With the default
     * configuration (VAT rate 0) it must be exactly that.
     *
     * <p>The self-consumption of 1.50 + 2.50 + 0.25 = 4.25 kWh is worth 4.25 x 0.15 = 0.6375, so
     * 0.64 rounded once; rounding each hour first would give 0.23 + 0.38 + 0.04 = 0.65. If this
     * fails, the amount is not the simple product the label implies and the label must be revisited
     * -- a tolerance here would hide exactly that. It also fails if a non-zero VAT rate is ever
     * configured, since the exposed price is the pre-VAT base.
     */
    @Test
    void amountEqualsSelfConsumedKWhTimesExposedPriceUnderDefaultVat() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        writeRecords(
                hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 1.0f, 1.5f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "01:00", 1.0f, 2.5f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "02:00", 1.0f, 0.25f, 0.0f));

        MvcResult result = mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2024-02-01T00:00:00+01:00")
                        .queryParam("endDate", "2024-02-01T03:00:00+01:00"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        BigDecimal selfConsumedKWh = BigDecimal.valueOf(body.path("energy").path("selfConsumptionKWh").asDouble());
        BigDecimal eurPerKWh = body.path("savings").path("estimatedPrice").path("eurPerKWh").decimalValue();
        BigDecimal amountEur = body.path("savings").path("amountEur").decimalValue();

        BigDecimal expected = selfConsumedKWh.multiply(eurPerKWh).setScale(2, RoundingMode.HALF_UP);
        assertEquals(0, new BigDecimal("0.64").compareTo(expected),
                () -> "the dataset no longer exercises single rounding: expected " + expected);
        assertEquals(0, expected.compareTo(amountEur),
                () -> "amountEur " + amountEur + " is not " + selfConsumedKWh + " kWh x " + eurPerKWh);
    }

    /**
     * An explicitly requested period with no record inside it is a different case from AC2: the
     * period resolved, so the answer is a real zero rather than an absent amount. The endpoint
     * cannot tell missing data from genuine zeros here, which is exactly what `coverage` is for --
     * and it reports 0 of 24 hours covered.
     */
    @Test
    void testExplicitPeriodWithoutAnyRecordIsWorthZeroRatherThanNull() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        // The supply has records, just none inside the requested period.
        writeRecords(hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 1.0f, 1.0f, 1.0f));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2025-01-01T00:00:00+01:00")
                        .queryParam("endDate", "2025-01-02T00:00:00+01:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savings.amountEur").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.savings.tariffSource").value("ESTIMATE"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"amountEur\":0.00")))
                // coverage is the field that tells this apart from a genuinely idle period.
                .andExpect(jsonPath("$.coverage.hoursWithData").value(0))
                .andExpect(jsonPath("$.coverage.expectedHours").value(24));
    }

    /**
     * AC3. Records exist and were consumed from the grid, but none of the energy was
     * self-consumed, so the saving is a real zero carried to two decimals.
     */
    @Test
    void testZeroSelfConsumptionIsWorthZero() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        writeRecords(
                hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 10.0f, 0.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "01:00", 20.0f, 0.0f, 0.0f));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2024-02-01T00:00:00+01:00")
                        .queryParam("endDate", "2024-02-01T01:00:00+01:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.savings.amountEur").value(closeTo(0.0, TOLERANCE)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"amountEur\":0.00")));
    }

    /**
     * AC4. With explicit bounds, the saving covers the same period the energy does. Both figures
     * are asserted against values computed by hand from the records inside the period, rather
     * than one against the other: the records outside the period are large enough that pricing
     * the supply's whole history would give 12.00 EUR instead of 1.20.
     */
    @Test
    void testSavingsCoverTheSameExplicitPeriodAsTheEnergyTotals() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        writeRecords(
                // Outside the period, before it.
                hourlyRecord(supply.getCode(), "2024/02/01", "09:00", 1.0f, 36.0f, 0.0f),
                // Inside: 2 + 3 + 3 = 8 kWh self-consumed.
                hourlyRecord(supply.getCode(), "2024/02/01", "10:00", 1.0f, 2.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "11:00", 1.0f, 3.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "12:00", 1.0f, 3.0f, 0.0f),
                // Outside the period, after it.
                hourlyRecord(supply.getCode(), "2024/02/01", "13:00", 1.0f, 36.0f, 0.0f));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2024-02-01T10:00:00+01:00")
                        .queryParam("endDate", "2024-02-01T13:00:00+01:00"))
                .andExpect(status().isOk())
                // Hand-computed from the three in-period records.
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(8.0, TOLERANCE)))
                // 8 x 0.15 = 1.20, not 80 x 0.15 = 12.00.
                .andExpect(jsonPath("$.savings.amountEur").value(closeTo(1.20, TOLERANCE)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"amountEur\":1.20")));
    }

    /**
     * The record on the exclusive end bound is not priced -- the saving honours the same half-open
     * bounds the energy totals do.
     */
    @Test
    @DisplayName("EPR-001 the savings leave out the record on the exclusive end bound")
    void testSavingsHonourTheExclusiveEndBound() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        writeRecords(
                hourlyRecord(supply.getCode(), "2024/02/01", "10:00", 0.0f, 10.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "11:00", 0.0f, 10.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "12:00", 0.0f, 1000.0f, 0.0f));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2024-02-01T10:00:00+01:00")
                        .queryParam("endDate", "2024-02-01T12:00:00+01:00"))
                .andExpect(status().isOk())
                // 20 x 0.15 = 3.00. The 12:00 record, on the end bound, would have taken it to 153.00.
                .andExpect(jsonPath("$.savings.amountEur").value(closeTo(3.00, TOLERANCE)))
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(20.0, TOLERANCE)));
    }

    /**
     * AC5. Savings are read through the same authorization as the rest of the response: the owner
     * sees their own supply's amount.
     */
    @Test
    void testOwnerSeesTheSavingsOfTheirOwnSupply() throws Exception {
        User owner = UserMother.randomUser();
        owner.enable();
        User createdOwner = createUserRepository.create(owner);
        createMembershipRepository.create(DEFAULT_COMMUNITY_ID, createdOwner.getId(), CommunityRole.COMMUNITY_MEMBER);
        Supply supply = createSupplyOwnedBy(createdOwner);

        writeRecords(hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 4.0f, 10.0f, 0.0f));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, loginUser(owner)))
                .andExpect(status().isOk())
                // 10 x 0.15 = 1.50.
                .andExpect(jsonPath("$.savings.amountEur").value(closeTo(1.50, TOLERANCE)))
                .andExpect(jsonPath("$.savings.tariffSource").value("ESTIMATE"));
    }

    @Test
    void testAsOwnerOfTheSupply() throws Exception {
        User owner = UserMother.randomUser();
        owner.enable();
        User createdOwner = createUserRepository.create(owner);
        createMembershipRepository.create(DEFAULT_COMMUNITY_ID, createdOwner.getId(), CommunityRole.COMMUNITY_MEMBER);
        Supply supply = createSupplyOwnedBy(createdOwner);

        writeRecords(hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 4.0f, 1.0f, 0.0f));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, loginUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supply.id").value(supply.getId().toString()))
                .andExpect(jsonPath("$.selfSufficiencyRatio").value(closeTo(0.2, TOLERANCE)));
    }

    @Test
    @DisplayName("SUP-003 the owner whose membership is disabled is answered not found")
    void theOwnerWhoseMembershipIsDisabledIsAnsweredNotFound() throws Exception {
        User owner = createMemberOfDefaultCommunity();
        Supply supply = createSupplyOwnedBy(owner);
        MembershipStateTestSupport.markDisabled(communityMembershipJpaRepository, owner.getId(), DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, loginUser(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testAsNeitherOwnerNorCommunityAdminReturnsNotFound() throws Exception {
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, loginAsPartner()))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithUnknownSupply() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();
        UUID supplyId = UUID.randomUUID();

        mockMvc.perform(get(URL + "/" + supplyId + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":404")))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithStartDateAfterEndDate() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2024-02-02T00:00:00+01:00")
                        .queryParam("endDate", "2024-02-01T00:00:00+01:00"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_START_AFTER_END"));
    }

    @Test
    void testWithOnlyStartDate() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2024-02-01T00:00:00+01:00"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_INCOMPLETE"));
    }

    @Test
    void testWithOnlyEndDate() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("endDate", "2024-02-01T00:00:00+01:00"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_INCOMPLETE"));
    }

    @Test
    void testWithMissingToken() throws Exception {
        mockMvc.perform(get(URL + "/" + UUID.randomUUID() + PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithWrongToken() throws Exception {
        mockMvc.perform(get(URL + "/" + UUID.randomUUID() + PATH)
                        .header(HttpHeaders.AUTHORIZATION,
                                JwtAuthenticationFilter.AUTHORIZATION_HEADER_PREFIX + "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithExpiredToken() throws Exception {
        final String expiredToken = JwtAuthenticationFilter.AUTHORIZATION_HEADER_PREFIX +
                "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQURNSU4iLCJzdWIiOiJiMTFlMTgxNS1mNzE0LTRmNGEtOGZjMS0yNjQxM2FmM2YzYmIiLCJpYXQiOjE3MDQyNzkzNzIsImV4cCI6MTcwNDI4MTE3Mn0.xvJF4LjS7oIcMUXjI7WbHkuxTnmuJn-3JVcwWm6qbok";

        mockMvc.perform(get(URL + "/" + UUID.randomUUID() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    // --- SEM-001: the latest published month of this supply alone ---

    /**
     * Two supplies of the same owner in the same community. A publishes July and August in full but
     * only 83 % of September; B publishes August and September in full. Both carry the whole current
     * month. A resolves August, which a membership-wide resolution would have answered with B's
     * September; B resolves September.
     */
    @Test
    @DisplayName("SEM-001 EPR-001 AC7 each supply of an owner resolves the latest month it published itself, half-open")
    void eachSupplyOfAnOwnerResolvesTheLatestMonthItPublishedItself() throws Exception {
        travelTo(NOW);
        User owner = createMemberOfDefaultCommunity();
        Supply supplyA = createSupplyOwnedBy(owner);
        Supply supplyB = createSupplyOwnedBy(owner);
        writePublishedZeros(supplyA, YearMonth.of(2026, 7));
        writePublishedZeros(supplyA, YearMonth.of(2026, 8));
        writePublishedZeros(supplyA, YearMonth.of(2026, 9), FOUR_HOURS_A_DAY);
        writePublishedZeros(supplyA, YearMonth.of(2026, 10));
        writePublishedZeros(supplyB, YearMonth.of(2026, 8));
        writePublishedZeros(supplyB, YearMonth.of(2026, 9));
        writePublishedZeros(supplyB, YearMonth.of(2026, 10));

        requestLatest(supplyA, loginUser(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supply.id").value(supplyA.getId().toString()))
                .andExpect(jsonPath("$.period.startDate").value("2026-08-01T00:00:00+02:00"))
                .andExpect(jsonPath("$.period.endDate").value("2026-09-01T00:00:00+02:00"))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(744))
                .andExpect(jsonPath("$.coverage.expectedHours").value(744));

        requestLatest(supplyB, loginUser(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supply.id").value(supplyB.getId().toString()))
                .andExpect(jsonPath("$.period.startDate").value("2026-09-01T00:00:00+02:00"))
                .andExpect(jsonPath("$.period.endDate").value("2026-10-01T00:00:00+02:00"))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(720))
                .andExpect(jsonPath("$.coverage.expectedHours").value(720));
    }

    /**
     * The resolved month is aggregated exactly as an explicit period over it: the self-consumed
     * energy of the one record carrying any is reported, and priced.
     */
    @Test
    @DisplayName("SEM-001 AC1 the resolved month is aggregated over this supply's records")
    void theResolvedMonthIsAggregatedOverThisSuppliesRecords() throws Exception {
        travelTo(NOW);
        User owner = createMemberOfDefaultCommunity();
        Supply supply = createSupplyOwnedBy(owner);
        writePublishedZeros(supply, YearMonth.of(2026, 9));
        writeRecords(hourlyRecord(supply.getCode(), "2026/09/10", "12:00", 3.0f, 4.0f, 1.0f));

        requestLatest(supply, loginUser(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2026-09-01T00:00:00+02:00"))
                .andExpect(jsonPath("$.energy.gridImportKWh").value(closeTo(3.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(4.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.surplusKWh").value(closeTo(1.0, TOLERANCE)))
                // 4 x 0.15 = 0.60.
                .andExpect(jsonPath("$.savings.amountEur").value(closeTo(0.60, TOLERANCE)))
                .andExpect(jsonPath("$.selfConsumptionRatio").value(closeTo(0.8, TOLERANCE)));
    }

    @Test
    @DisplayName("SEM-001 AC2 the reference period combined with a start date is a bad request")
    void theReferencePeriodCombinedWithAStartDateIsABadRequest() throws Exception {
        User owner = createMemberOfDefaultCommunity();
        Supply supply = createSupplyOwnedBy(owner);

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, loginUser(owner))
                        .queryParam("period", LATEST)
                        .queryParam("startDate", "2026-08-01T00:00:00+02:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_CONFLICTING"));
    }

    @Test
    @DisplayName("SEM-001 AC2 the reference period combined with an end date is a bad request")
    void theReferencePeriodCombinedWithAnEndDateIsABadRequest() throws Exception {
        User owner = createMemberOfDefaultCommunity();
        Supply supply = createSupplyOwnedBy(owner);

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, loginUser(owner))
                        .queryParam("period", LATEST)
                        .queryParam("endDate", "2026-08-31T23:00:00+02:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_CONFLICTING"));
    }

    @Test
    @DisplayName("SEM-001 AC2 the reference period combined with both dates is a bad request")
    void theReferencePeriodCombinedWithBothDatesIsABadRequest() throws Exception {
        User owner = createMemberOfDefaultCommunity();
        Supply supply = createSupplyOwnedBy(owner);

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, loginUser(owner))
                        .queryParam("period", LATEST)
                        .queryParam("startDate", "2026-08-01T00:00:00+02:00")
                        .queryParam("endDate", "2026-08-31T23:00:00+02:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_CONFLICTING"));
    }

    /**
     * August is below the threshold, September carries consumption and measured surplus but no
     * self-consumption yet, and the current month is published in full. No complete month before
     * the current one is published, so no period is resolved: the current month is never chosen
     * instead.
     */
    @Test
    @DisplayName("SEM-001 AC3 a supply without a published month before the current one resolves no period")
    void aSupplyWithoutAPublishedMonthBeforeTheCurrentOneResolvesNoPeriod() throws Exception {
        travelTo(NOW);
        User owner = createMemberOfDefaultCommunity();
        Supply supply = createSupplyOwnedBy(owner);
        writePublishedZeros(supply, YearMonth.of(2026, 8), FOUR_HOURS_A_DAY);
        fixture.writeAt(supply.getCode(), hourly(YearMonth.of(2026, 9)), 0.5f, null, 0.2f);
        writePublishedZeros(supply, YearMonth.of(2026, 10));

        assertNoPeriodResolved(requestLatest(supply, loginUser(owner)), supply);
    }

    @Test
    @DisplayName("SEM-001 AC3 a supply without any stored record resolves no period")
    void aSupplyWithoutAnyStoredRecordResolvesNoPeriod() throws Exception {
        travelTo(NOW);
        User owner = createMemberOfDefaultCommunity();
        Supply supply = createSupplyOwnedBy(owner);

        assertNoPeriodResolved(requestLatest(supply, loginUser(owner)), supply);
    }

    /**
     * Without {@code period}, the same records still span from the first stored record to the
     * last, the below-threshold and the current month included.
     */
    @Test
    @DisplayName("SEM-001 AC4 without a period nor dates the period still spans every stored record")
    void withoutAPeriodNorDatesThePeriodStillSpansEveryStoredRecord() throws Exception {
        travelTo(NOW);
        User owner = createMemberOfDefaultCommunity();
        Supply supply = createSupplyOwnedBy(owner);
        writePublishedZeros(supply, YearMonth.of(2026, 8));
        writePublishedZeros(supply, YearMonth.of(2026, 9), FOUR_HOURS_A_DAY);
        writePublishedZeros(supply, YearMonth.of(2026, 10));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, loginUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2026-08-01T00:00:00+02:00"))
                // The last September record is at 19:00; October is stored in full, its last record at
                // 23:00 on the 31st, so the period ends an hour later.
                .andExpect(jsonPath("$.period.endDate").value("2026-11-01T00:00:00+01:00"))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(744 + 600 + 745))
                .andExpect(jsonPath("$.coverage.expectedHours").value(744 + 720 + 745));
    }

    @Test
    @DisplayName("SEM-001 AC4 explicit dates over the resolved month answer exactly what the reference period answers")
    void explicitDatesOverTheResolvedMonthAnswerExactlyWhatTheReferencePeriodAnswers() throws Exception {
        travelTo(NOW);
        User owner = createMemberOfDefaultCommunity();
        Supply supply = createSupplyOwnedBy(owner);
        writePublishedZeros(supply, YearMonth.of(2026, 8));
        writeRecords(hourlyRecord(supply.getCode(), "2026/08/10", "12:00", 3.0f, 4.0f, 1.0f));
        String token = loginUser(owner);

        String explicit = mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .queryParam("startDate", "2026-08-01T00:00:00+02:00")
                        .queryParam("endDate", "2026-09-01T00:00:00+02:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2026-08-01T00:00:00+02:00"))
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(4.0, TOLERANCE)))
                .andReturn().getResponse().getContentAsString();
        String latest = requestLatest(supply, token)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(objectMapper.readTree(explicit), objectMapper.readTree(latest));
    }

    @Test
    @DisplayName("SEM-001 AC5 the owner can read the latest published month")
    void theOwnerCanReadTheLatestPublishedMonth() throws Exception {
        Community community = createCommunity();
        User owner = createMemberOf(community);
        Supply supply = createSupplyOwnedBy(owner, community);

        requestLatest(supply, loginUser(owner)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("SEM-001 AC5 an enabled community admin can read the latest published month")
    void anEnabledCommunityAdminCanReadTheLatestPublishedMonth() throws Exception {
        Community community = createCommunity();
        Supply supply = createSupplyOwnedBy(createMemberOf(community), community);

        requestLatest(supply, loginAsCommunityAdmin(community.getId())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("SEM-001 AC5 a platform admin is answered not found")
    void aPlatformAdminIsAnsweredNotFound() throws Exception {
        String platformAdminToken = loginAsDefaultPlatformAdmin();
        Community community = createCommunity();
        Supply supply = createSupplyOwnedBy(createMemberOf(community), community);

        requestLatest(supply, platformAdminToken).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SEM-001 AC5 a member of another community is answered not found")
    void aMemberOfAnotherCommunityIsAnsweredNotFound() throws Exception {
        Community community = createCommunity();
        Supply supply = createSupplyOwnedBy(createMemberOf(community), community);

        requestLatest(supply, loginAsCommunityMember(createCommunity().getId())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SEM-001 AC5 a disabled community admin is answered not found")
    void aDisabledCommunityAdminIsAnsweredNotFound() throws Exception {
        Community community = createCommunity();
        Supply supply = createSupplyOwnedBy(createMemberOf(community), community);
        User admin = createEnabledUser();
        createMembershipRepository.create(community.getId(), admin.getId(), CommunityRole.COMMUNITY_ADMIN);
        MembershipStateTestSupport.markDisabled(communityMembershipJpaRepository, admin.getId(), community.getId());

        requestLatest(supply, loginUser(admin)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SEM-001 AC5 a member of the same community who is neither the owner nor an admin is answered not found")
    void aMemberOfTheSameCommunityWhoIsNeitherTheOwnerNorAnAdminIsAnsweredNotFound() throws Exception {
        Community community = createCommunity();
        Supply supply = createSupplyOwnedBy(createMemberOf(community), community);

        requestLatest(supply, loginAsCommunityMember(community.getId())).andExpect(status().isNotFound());
    }

    private ResultActions requestLatest(Supply supply, String token) throws Exception {
        return mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                .header(HttpHeaders.AUTHORIZATION, token)
                .queryParam("period", LATEST));
    }

    /**
     * The same answer the membership energy metrics give when no period can be resolved: null
     * bounds, zero totals, no coverage, unpriced savings and null ratios.
     */
    private void assertNoPeriodResolved(ResultActions result, Supply supply) throws Exception {
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.supply.id").value(supply.getId().toString()))
                .andExpect(jsonPath("$.period.startDate").value(nullValue()))
                .andExpect(jsonPath("$.period.endDate").value(nullValue()))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(0))
                .andExpect(jsonPath("$.coverage.expectedHours").value(0))
                .andExpect(jsonPath("$.energy.totalConsumptionKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.gridImportKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.surplusKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.energy.assignedProductionKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.savings.amountEur").value(nullValue()))
                .andExpect(jsonPath("$.savings.tariffSource").value("ESTIMATE"))
                .andExpect(jsonPath("$.savings.estimatedPrice").value(nullValue()))
                .andExpect(jsonPath("$.selfSufficiencyRatio").value(nullValue()))
                .andExpect(jsonPath("$.selfConsumptionRatio").value(nullValue()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"startDate\":null")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"amountEur\":null")));
    }

    private void writePublishedZeros(Supply supply, YearMonth month, Integer... skippedLocalHours) {
        fixture.writePublishedZeros(supply.getCode(), month, ZONE, skippedLocalHours);
    }

    /**
     * Every hour of the month in the zone.
     */
    private static List<Instant> hourly(YearMonth month) {
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant();
        List<Instant> times = new ArrayList<>();
        for (Instant time = month.atDay(1).atStartOfDay(ZONE).toInstant(); time.isBefore(end);
             time = time.plusSeconds(3600)) {
            times.add(time);
        }
        return times;
    }

    private User createEnabledUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        return user;
    }

    private Community createCommunity() {
        return createCommunityRepository.create(CommunityMother.random().build());
    }

    private User createMemberOfDefaultCommunity() {
        User member = createEnabledUser();
        createMembershipRepository.create(DEFAULT_COMMUNITY_ID, member.getId(), CommunityRole.COMMUNITY_MEMBER);
        return member;
    }

    private User createMemberOf(Community community) {
        User member = createEnabledUser();
        createMembershipRepository.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        return member;
    }

    private Supply createSupplyOwnedBy(User owner, Community community) {
        return createSupplyRepository.create(
                SupplyMother.random(owner).withCode(randomCupsCode()).build(),
                UserId.of(owner.getId()), community.getId());
    }

    private Supply createSupplyOwnedBy(User owner) {
        return createSupplyRepository.create(
                SupplyMother.random(owner).withCode(randomCupsCode()).build(),
                UserId.of(owner.getId()), DEFAULT_COMMUNITY_ID);
    }

    private String randomCupsCode() {
        String code = "ES" + RandomStringUtils.random(20, false, true);
        createdCupsCodes.add(code);
        return code;
    }

    private void writeRecords(DatadisConsumption... records) {
        fixture.write(List.of(records));
    }

    @Test
    @DisplayName("EVL-001 AC1 AC2 AC5 the energy metrics round every energy figure to 3 decimals and store nothing rounded")
    void testEnergyMetricsRoundEveryEnergyFigure() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));
        writeRecords(RoundingNoiseDataset.firstRecord(supply.getCode()),
                RoundingNoiseDataset.secondRecord(supply.getCode()));

        String body = mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", RoundingNoiseDataset.DAY_START)
                        .queryParam("endDate", RoundingNoiseDataset.DAY_END))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertKWh(body, "/energy/gridImportKWh", RoundingNoiseDataset.GRID_IMPORT);
        assertKWh(body, "/energy/selfConsumptionKWh", RoundingNoiseDataset.SELF_CONSUMPTION);
        assertKWh(body, "/energy/surplusKWh", RoundingNoiseDataset.SURPLUS);
        assertKWh(body, "/energy/totalConsumptionKWh", RoundingNoiseDataset.TOTAL_CONSUMPTION);
        assertKWh(body, "/energy/assignedProductionKWh", RoundingNoiseDataset.ASSIGNED_PRODUCTION);
        RoundingNoiseDataset.assertHourlyRecordsUnchanged(getDatadisConsumptionRepository, supply);
    }

    /**
     * From the rounded figures the ratios would be 0.033 / 0.379 and 0.033 / 0.366, and the savings
     * 0.033 kWh x 0.15 = 0.00495, presented as 0.00. Constant price: the test profile prices every
     * hour at the 0.15 EUR/kWh estimate, so the expected amount is the unrounded self-consumption
     * times 0.15, rounded to cents.
     */
    @Test
    @DisplayName("EVL-002 the energy metrics derive the ratios and the savings from the unrounded totals")
    void testEnergyMetricsDeriveRatiosAndSavingsFromTheUnroundedTotals() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));
        writeRecords(RoundingNoiseDataset.firstRecord(supply.getCode()),
                RoundingNoiseDataset.secondRecord(supply.getCode()));
        double selfConsumption = RoundingNoiseDataset.selfConsumptionSum();

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", RoundingNoiseDataset.DAY_START)
                        .queryParam("endDate", RoundingNoiseDataset.DAY_END))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selfSufficiencyRatio").value(
                        closeTo(selfConsumption / (RoundingNoiseDataset.gridImportSum() + selfConsumption), 1e-12), Double.class))
                .andExpect(jsonPath("$.selfConsumptionRatio").value(
                        closeTo(selfConsumption / (selfConsumption + RoundingNoiseDataset.surplusSum()), 1e-12), Double.class))
                .andExpect(jsonPath("$.selfSufficiencyRatio").value(not(closeTo(0.033 / 0.379, 1e-6)), Double.class))
                .andExpect(jsonPath("$.selfConsumptionRatio").value(not(closeTo(0.033 / 0.366, 1e-6)), Double.class))
                .andExpect(jsonPath("$.savings.amountEur").value(RoundingNoiseDataset.SAVINGS_EUR));
    }
}
