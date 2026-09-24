package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.contract.SupplyContract;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionInfluxFixture.hourlyRecord;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests written from the endpoint's acceptance criteria. Each test owns its dataset under a unique
 * CUPS, so nothing it writes to the shared InfluxDB container can reach another test.
 */
@Transactional
class GetSupplyEnergyMetricsControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/supplies";
    private static final String PATH = "/energy-metrics";
    private static final double TOLERANCE = 1e-6;

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private DatadisConsumptionInfluxFixture fixture;

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
                        .queryParam("endDate", "2024-02-01T02:00:00+01:00"))
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
                        .queryParam("endDate", "2024-02-01T00:00:00+01:00"))
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
                        .queryParam("endDate", "2024-02-01T00:00:00+01:00"))
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
                        .queryParam("endDate", "2024-02-01T01:00:00+01:00"))
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
                .andExpect(jsonPath("$.period.endDate").value("2024-02-01T02:00:00+01:00"))
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
                UserId.of(owner.getId()));

        writeRecords(hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 1.0f, 1.0f, 1.0f));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2024-02-01T00:00:00+01:00"))
                .andExpect(jsonPath("$.period.endDate").value("2024-02-01T00:00:00+01:00"));
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
                        .queryParam("endDate", "2024-02-01T23:00:00+01:00"))
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
                        .queryParam("endDate", "2025-01-01T23:00:00+01:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.startDate").value("2025-01-01T00:00:00+01:00"))
                .andExpect(jsonPath("$.period.endDate").value("2025-01-01T23:00:00+01:00"))
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
     * Both bounds are inclusive, so the record sitting exactly on endDate counts and the next one
     * does not.
     */
    @Test
    void testBothPeriodBoundsAreInclusive() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        writeRecords(
                hourlyRecord(supply.getCode(), "2024/02/01", "10:00", 1.0f, 0.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "11:00", 2.0f, 0.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "12:00", 4.0f, 0.0f, 0.0f));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2024-02-01T10:00:00+01:00")
                        .queryParam("endDate", "2024-02-01T11:00:00+01:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverage.hoursWithData").value(2))
                .andExpect(jsonPath("$.coverage.expectedHours").value(2))
                // The record on the bound is in; the one an hour later is out.
                .andExpect(jsonPath("$.energy.gridImportKWh").value(closeTo(3.0, TOLERANCE)));
    }

    /**
     * The local day the clocks go forward has 23 hours, so a fully covered one must not report 24.
     */
    @Test
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
                .andExpect(jsonPath("$.period.endDate").value("2023-03-26T23:00:00+02:00"))
                .andExpect(jsonPath("$.coverage.expectedHours").value(23))
                .andExpect(jsonPath("$.coverage.hoursWithData").value(23));
    }

    /**
     * The local day the clocks go back has 25 hours. Writing each local hour label once covers only
     * 24 of them -- the second 02:00 is never written -- which is exactly the gap coverage reports.
     */
    @Test
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
                .andExpect(jsonPath("$.period.endDate").value("2023-10-29T23:00:00+01:00"))
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
                        .queryParam("endDate", "2024-02-01T02:00:00+01:00"))
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
                        .queryParam("endDate", "2025-01-01T23:00:00+01:00"))
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
                        .queryParam("endDate", "2024-02-01T12:00:00+01:00"))
                .andExpect(status().isOk())
                // Hand-computed from the three in-period records.
                .andExpect(jsonPath("$.energy.selfConsumptionKWh").value(closeTo(8.0, TOLERANCE)))
                // 8 x 0.15 = 1.20, not 80 x 0.15 = 12.00.
                .andExpect(jsonPath("$.savings.amountEur").value(closeTo(1.20, TOLERANCE)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"amountEur\":1.20")));
    }

    /**
     * The record on the inclusive end bound is priced, the one an hour later is not -- the saving
     * honours the same inclusive bounds the energy totals do.
     */
    @Test
    void testSavingsHonourTheInclusiveEndBound() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createSupplyOwnedBy(createUserRepository.create(UserMother.randomUser()));

        writeRecords(
                hourlyRecord(supply.getCode(), "2024/02/01", "10:00", 0.0f, 10.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "11:00", 0.0f, 10.0f, 0.0f),
                hourlyRecord(supply.getCode(), "2024/02/01", "12:00", 0.0f, 1000.0f, 0.0f));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", "2024-02-01T10:00:00+01:00")
                        .queryParam("endDate", "2024-02-01T11:00:00+01:00"))
                .andExpect(status().isOk())
                // 20 x 0.15 = 3.00. The 12:00 record would have taken it to 153.00.
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
        Supply supply = createSupplyOwnedBy(createdOwner);

        writeRecords(hourlyRecord(supply.getCode(), "2024/02/01", "00:00", 4.0f, 1.0f, 0.0f));

        mockMvc.perform(get(URL + "/" + supply.getId() + PATH)
                        .header(HttpHeaders.AUTHORIZATION, loginUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supply.id").value(supply.getId().toString()))
                .andExpect(jsonPath("$.selfSufficiencyRatio").value(closeTo(0.2, TOLERANCE)));
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
                .andExpect(jsonPath("$.traceId").isNotEmpty());
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
                .andExpect(jsonPath("$.message").isNotEmpty());
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
                .andExpect(jsonPath("$.message").isNotEmpty());
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

    private Supply createSupplyOwnedBy(User owner) {
        return createSupplyRepository.create(
                SupplyMother.random(owner).withCode(randomCupsCode()).build(),
                UserId.of(owner.getId()));
    }

    private String randomCupsCode() {
        String code = "ES" + RandomStringUtils.random(20, false, true);
        createdCupsCodes.add(code);
        return code;
    }

    private void writeRecords(DatadisConsumption... records) {
        fixture.write(List.of(records));
    }
}
