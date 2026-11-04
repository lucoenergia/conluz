package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.lucoenergia.conluz.infrastructure.admin.supply.tariff.EstimatedTariffProperties;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The supply hourly series against stored records: the period it reads, the hours it emits with
 * their start instants, what each hour saved, and the 31-day cap it is held to (#545).
 */
class SupplyHourlySeriesControllerTest extends BaseControllerTest {

    private static final double TOLERANCE = 1e-6;
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /**
     * 2026-11-04T10:30 in Madrid: the current hour started at 10:00+01:00, after every whole day the
     * tests request.
     */
    private static final Instant NOW = Instant.parse("2026-11-04T09:30:00Z");

    private static final String SEPTEMBER_FIRST = "2026-09-01T00:00:00+02:00";
    private static final String SEPTEMBER_SECOND = "2026-09-02T00:00:00+02:00";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private DatadisConsumptionInfluxFixture influxFixture;
    @Autowired
    private EstimatedTariffProperties estimatedTariff;

    private Supply supply;
    private String token;

    @BeforeEach
    void givenAMemberWithOneSupply() throws Exception {
        travelTo(NOW);
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User member = UserMother.randomUser();
        member.enable();
        createUserRepository.create(member);
        createMembershipRepository.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        supply = createSupplyRepository.create(SupplyMother.random(member).build(),
                UserId.of(member.getId()), community.getId());
        token = loginUser(member);
    }

    @AfterEach
    void clearWrittenSeries() {
        influxFixture.clear(supply.getCode());
    }

    // --- AC1: a day is its 24 hours, without the record at the next midnight ---

    @Test
    @DisplayName("SCS-001 AC1 a whole day is 24 hours from 00:00, without the record at the next midnight")
    void aWholeDayIsTwentyFourHours() throws Exception {
        writeAt("2026-09-01T00:00:00+02:00", 1f, 0f);
        writeAt("2026-09-01T12:00:00+02:00", 2f, 0f);
        writeAt("2026-09-02T00:00:00+02:00", 4f, 0f);

        request(SEPTEMBER_FIRST, SEPTEMBER_SECOND)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(24)))
                .andExpect(jsonPath("$[*].date", everyItem(is("2026/09/01"))))
                .andExpect(jsonPath("$[*].time", contains(localHoursOfADay())))
                .andExpect(jsonPath("$[*].startsAt", contains(hourlyInstants("2026-09-01T00:00:00+02:00", 24))))
                .andExpect(jsonPath("$[0].startsAt").value("2026-09-01T00:00:00+02:00"))
                .andExpect(jsonPath("$[23].startsAt").value("2026-09-01T23:00:00+02:00"))
                .andExpect(jsonPath("$[0].gridImportKWh").value(closeTo(1.0, TOLERANCE)))
                .andExpect(jsonPath("$[12].gridImportKWh").value(closeTo(2.0, TOLERANCE)))
                .andExpect(jsonPath("$[23].gridImportKWh").value(closeTo(0.0, TOLERANCE)));
    }

    // --- AC2: the days the clocks change ---

    @Test
    @DisplayName("SCS-001 AC2 the March daylight saving day is 23 hours, without the skipped 02:00")
    void theMarchDaylightSavingDayIsTwentyThreeHours() throws Exception {
        writeAt("2026-03-29T03:00:00+02:00", 1f, 0f);

        request("2026-03-29T00:00:00+01:00", "2026-03-30T00:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(23)))
                .andExpect(jsonPath("$[*].startsAt", contains(hourlyInstants("2026-03-29T00:00:00+01:00", 23))))
                .andExpect(jsonPath("$[1].time").value("01:00"))
                .andExpect(jsonPath("$[1].startsAt").value("2026-03-29T01:00:00+01:00"))
                .andExpect(jsonPath("$[2].time").value("03:00"))
                .andExpect(jsonPath("$[2].startsAt").value("2026-03-29T03:00:00+02:00"))
                .andExpect(jsonPath("$[2].gridImportKWh").value(closeTo(1.0, TOLERANCE)))
                .andExpect(jsonPath("$[22].time").value("23:00"));
    }

    /**
     * Both records of the hour the clocks repeat are written at their own instant, with the fixture
     * that places either one on its own: this is a query test, not an ingestion one (#322).
     */
    @Test
    @DisplayName("SCS-001 AC2 the October daylight saving day is 25 hours, its repeated 02:00 twice")
    void theOctoberDaylightSavingDayIsTwentyFiveHours() throws Exception {
        writeAt("2026-10-25T02:00:00+02:00", 1f, 0f);
        writeAt("2026-10-25T02:00:00+01:00", 2f, 0f);

        request("2026-10-25T00:00:00+02:00", "2026-10-26T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(25)))
                .andExpect(jsonPath("$[*].startsAt", contains(hourlyInstants("2026-10-25T00:00:00+02:00", 25))))
                .andExpect(jsonPath("$[2].time").value("02:00"))
                .andExpect(jsonPath("$[2].startsAt").value("2026-10-25T02:00:00+02:00"))
                .andExpect(jsonPath("$[2].gridImportKWh").value(closeTo(1.0, TOLERANCE)))
                .andExpect(jsonPath("$[3].time").value("02:00"))
                .andExpect(jsonPath("$[3].startsAt").value("2026-10-25T02:00:00+01:00"))
                .andExpect(jsonPath("$[3].gridImportKWh").value(closeTo(2.0, TOLERANCE)))
                .andExpect(jsonPath("$[24].time").value("23:00"));
    }

    // --- AC3: a measured zero and a gap ---

    @Test
    @DisplayName("SCS-001 AC3 an hour of stored zeros has its obtain method and an hour with no record has none")
    void aMeasuredZeroHasAnObtainMethodAndAGapHasNone() throws Exception {
        writeAt("2026-09-01T10:00:00+02:00", 0f, 0f);
        writeAt("2026-09-01T12:00:00+02:00", 1f, 0f);

        request(SEPTEMBER_FIRST, SEPTEMBER_SECOND)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[10].time").value("10:00"))
                .andExpect(jsonPath("$[10].obtainMethod").value("Real"))
                .andExpect(jsonPath("$[10].gridImportKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$[10].savingsEur").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$[11].time").value("11:00"))
                .andExpect(jsonPath("$[11].obtainMethod").value(nullValue()))
                .andExpect(jsonPath("$[11].gridImportKWh").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$[11].savingsEur").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$[11].tariffSource").value("ESTIMATE"))
                .andExpect(content().string(containsString("\"savingsEur\":0.00")));
    }

    /**
     * With no record anywhere in the period the store returns no row at all, so every hour here comes
     * from the series itself, not from the store.
     */
    @Test
    @DisplayName("SCS-001 AC3 a day without any record is still its 24 hours, each with zero energy and no obtain method")
    void aDayWithoutAnyRecordIsStillTwentyFourHours() throws Exception {
        request(SEPTEMBER_FIRST, SEPTEMBER_SECOND)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(24)))
                .andExpect(jsonPath("$[*].time", contains(localHoursOfADay())))
                .andExpect(jsonPath("$[*].cups", everyItem(is(supply.getCode()))))
                .andExpect(jsonPath("$[*].obtainMethod", everyItem(nullValue())))
                .andExpect(jsonPath("$[*].gridImportKWh", everyItem(is(0.0))))
                .andExpect(jsonPath("$[*].selfConsumptionKWh", everyItem(is(0.0))))
                .andExpect(jsonPath("$[*].surplusKWh", everyItem(is(0.0))))
                .andExpect(jsonPath("$[*].savingsEur", everyItem(is(0.0))))
                .andExpect(jsonPath("$[*].startsAt", contains(hourlyInstants(SEPTEMBER_FIRST, 24))));
    }

    // --- AC4: each hour is priced over itself ---

    /**
     * Two hours with different self-consumption, so that neither a price per day nor a copy of one
     * hour's amount can match both. 0.3 kWh at 0.15 €/kWh is 0.045 €, which rounds half-up to 0.05:
     * rounding half-even would give 0.04.
     */
    @Test
    @DisplayName("SCS-001 AC4 each hour's savings are its own self-consumption priced with the estimated tariff")
    void eachHourIsPricedFromItsOwnSelfConsumption() throws Exception {
        writeAt("2026-09-01T10:00:00+02:00", 0f, 0.3f);
        writeAt("2026-09-01T11:00:00+02:00", 0f, 2.5f);

        request(SEPTEMBER_FIRST, SEPTEMBER_SECOND)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[10].savingsEur").value(closeTo(priced("0.3"), TOLERANCE)))
                .andExpect(jsonPath("$[10].tariffSource").value("ESTIMATE"))
                .andExpect(jsonPath("$[11].savingsEur").value(closeTo(priced("2.5"), TOLERANCE)))
                .andExpect(content().string(containsString("\"savingsEur\":0.05")))
                .andExpect(content().string(containsString("\"savingsEur\":0.38")));
    }

    /**
     * An end in the middle of an hour leaves a partial last hour: it holds the record stamped at its
     * start, and is priced over the part of it the period covers.
     */
    @Test
    @DisplayName("SCS-001 an end in the middle of an hour leaves a partial last hour that keeps its record")
    void anEndInTheMiddleOfAnHourLeavesAPartialLastHour() throws Exception {
        writeAt("2026-09-01T10:00:00+02:00", 1f, 2.5f);

        request("2026-09-01T09:00:00+02:00", "2026-09-01T10:30:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].startsAt").value("2026-09-01T10:00:00+02:00"))
                .andExpect(jsonPath("$[1].gridImportKWh").value(closeTo(1.0, TOLERANCE)))
                .andExpect(jsonPath("$[1].savingsEur").value(closeTo(priced("2.5"), TOLERANCE)));
    }

    /**
     * A start in the middle of an hour: that hour started before the period, and the record stamped
     * at its start is not part of it, so the first hour emitted is the next whole one.
     */
    @Test
    @DisplayName("SCS-001 a start in the middle of an hour emits the next whole hour first")
    void aStartInTheMiddleOfAnHourEmitsTheNextWholeHourFirst() throws Exception {
        writeAt("2026-09-01T10:00:00+02:00", 1f, 0f);

        request("2026-09-01T10:30:00+02:00", "2026-09-01T12:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].startsAt").value("2026-09-01T11:00:00+02:00"))
                .andExpect(jsonPath("$[0].obtainMethod").value(nullValue()));
    }

    // --- EPR-002: an end in the future is clipped to the current hour ---

    @Test
    @DisplayName("EPR-002 the hourly series emits no hour starting at or after the start of the current hour")
    void theHourlySeriesEmitsNoHourFromTheCurrentOne() throws Exception {
        writeAt("2026-11-04T09:00:00+01:00", 1f, 0f);
        writeAt("2026-11-04T10:00:00+01:00", 2f, 0f);

        request("2026-11-04T00:00:00+01:00", "2026-11-05T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[9].time").value("09:00"))
                .andExpect(jsonPath("$[9].gridImportKWh").value(closeTo(1.0, TOLERANCE)));
    }

    // --- EPR-005: at most 31 calendar days ---

    @Test
    @DisplayName("EPR-005 AC5 exactly 31 calendar days are accepted")
    void exactlyThirtyOneDaysAreAccepted() throws Exception {
        request(SEPTEMBER_FIRST, "2026-10-02T00:00:00+02:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(31 * 24)));
    }

    @Test
    @DisplayName("EPR-005 AC5 31 calendar days and one hour are answered 400 ENERGY_METRICS_PERIOD_TOO_LONG")
    void thirtyOneDaysAndAnHourAreRejected() throws Exception {
        request(SEPTEMBER_FIRST, "2026-10-02T01:00:00+02:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_TOO_LONG"))
                // The limit the message states is the hourly one, not the 24 months of the other operations.
                .andExpect(jsonPath("$.message", containsString(" 31 ")));
    }

    /**
     * October 2026 is 31 calendar days of 745 hours in Madrid: the clocks go back on the 25th, so the
     * cap is counted in local days, not in 24-hour steps.
     */
    @Test
    @DisplayName("EPR-005 the 31 calendar days are counted in the period's time zone")
    void theDaysAreCountedInThePeriodsZone() throws Exception {
        request("2026-10-01T00:00:00+02:00", "2026-11-01T00:00:00+01:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(745)));
        request("2026-10-01T00:00:00+02:00", "2026-11-01T01:00:00+01:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_TOO_LONG"));
    }

    /**
     * The end clipped to the current hour would be within 31 days of the start, but the cap is
     * evaluated on the bounds as requested.
     */
    @Test
    @DisplayName("EPR-005 the cap is evaluated on the requested end, even when it is in the future")
    void theCapIsEvaluatedOnTheRequestedEnd() throws Exception {
        request("2026-10-05T10:00:00+02:00", "2026-11-05T11:00:00+01:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ENERGY_METRICS_PERIOD_TOO_LONG"));
    }

    // --- fixtures ---

    private static String[] localHoursOfADay() {
        return IntStream.range(0, 24).mapToObj(hour -> String.format("%02d:00", hour)).toArray(String[]::new);
    }

    /**
     * The start instants of {@code count} consecutive hours from {@code first}, each with the offset
     * in force in Madrid at that instant, as the series renders them.
     */
    private static String[] hourlyInstants(String first, int count) {
        Instant start = OffsetDateTime.parse(first).toInstant();
        return IntStream.range(0, count)
                .mapToObj(hour -> start.plus(hour, ChronoUnit.HOURS).atZone(MADRID).toOffsetDateTime()
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .toArray(String[]::new);
    }

    /**
     * What {@code selfConsumptionKWh} is worth at the configured estimated tariff, rounded to cents
     * half-up, as every series rounds an amount.
     */
    private double priced(String selfConsumptionKWh) {
        return new BigDecimal(selfConsumptionKWh)
                .multiply(estimatedTariff.getBaseEurPerKwh())
                .multiply(BigDecimal.ONE.add(estimatedTariff.getVatRate()))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private void writeAt(String time, float gridImportKWh, float selfConsumptionKWh) {
        influxFixture.writeAt(supply.getCode(), OffsetDateTime.parse(time).toInstant(), gridImportKWh,
                selfConsumptionKWh, 0f);
    }

    private ResultActions request(String startDate, String endDate) throws Exception {
        return mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/consumption/hourly")
                .header(HttpHeaders.AUTHORIZATION, token)
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate));
    }
}
