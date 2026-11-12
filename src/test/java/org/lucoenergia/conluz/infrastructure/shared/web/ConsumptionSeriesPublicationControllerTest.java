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
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisEnergyValues;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture.hourlyRecord;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The six membership and supply consumption series count a supply as having data only when its
 * records carry published self-consumption (#569): over a month or a day, when the records
 * carrying self-consumption reach 90 % of the bucket's hours inside the period; over an hour, when
 * its record carries self-consumption. Datadis publishes self-consumption some days after the month
 * ends, and until then a supply's records carry consumption and surplus but no self-consumption.
 *
 * <p>Every dataset is written as Datadis records are stored: a published hour carries
 * self-consumption, zero included, and an unpublished one does not carry the field at all. The
 * tariff of the test profile is the 0.15 EUR/kWh estimate without VAT, a constant price.
 */
@Transactional
class ConsumptionSeriesPublicationControllerTest extends BaseControllerTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    /** 720 hours, so 90 % of them is exactly 648. */
    private static final YearMonth JUNE = YearMonth.of(2024, 6);
    private static final YearMonth MAY = YearMonth.of(2024, 5);
    private static final LocalDate JUNE_10 = LocalDate.of(2024, 6, 10);

    private static final String MEMBERSHIP_PATH = "/api/v1/communities/{communityId}/memberships/{userId}/consumption/";
    private static final String SUPPLY_PATH = "/api/v1/supplies/{supplyId}/consumption/";

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

    // --- A supply published, another not yet (AC1, AC2) ---

    /**
     * A is published for June; B stored every hour of June with consumption and surplus but no
     * self-consumption. A second member of the community, with a published supply of its own, never
     * reaches the first member's figures. June's month, a day of it and an hour of it each count one
     * of the two supplies, and sum A alone: 5 kWh consumed, 2 self-consumed, 1 exported, 2 kWh at
     * 0.15 saving 0.30.
     */
    @Test
    @DisplayName("MCS-004 MCS-002 MCS-003 AC1 a supply whose self-consumption is not published counts in no bucket of the month, day or hour")
    void aSupplyNotYetPublishedCountsInNoBucket() throws Exception {
        Membership membership = membershipWithTwoSupplies();
        publishMonth(membership.first(), JUNE);
        write(membership.first(), "2024/06/10", "13:00", 5f, 2f, 1f);
        writeUnpublished(membership.second(), hoursOf(JUNE), 3f, 0.5f);
        Membership other = membershipWithTwoSupplies(membership.community());
        publishMonth(other.first(), JUNE);
        write(other.first(), "2024/06/10", "13:00", 100f, 50f, 25f);

        JsonNode month = membershipSeries(membership, "monthly", JUNE.atDay(1), JUNE.plusMonths(1).atDay(1)).get(0);
        JsonNode day = membershipSeries(membership, "daily", JUNE_10, JUNE_10.plusDays(1)).get(0);
        JsonNode hour = membershipSeries(membership, "hourly", JUNE_10, JUNE_10.plusDays(1)).get(12);

        for (JsonNode bucket : List.of(month, day, hour)) {
            assertEquals(2, bucket.path("supplyCount").asInt(), bucket::toString);
            assertEquals(1, bucket.path("suppliesWithData").asInt(), bucket::toString);
            assertDecimal("5", bucket.path("gridImportKWh"));
            assertDecimal("2", bucket.path("selfConsumptionKWh"));
            assertDecimal("1", bucket.path("surplusKWh"));
            assertDecimal("0.30", bucket.path("savingsEur"));
        }
        assertEquals("12:00", hour.path("time").asText());
    }

    /**
     * Coverage uniform across June: A is published at every hour, B has no record at all. Every day
     * of the daily series counts the same supplies with data as the month.
     */
    @Test
    @DisplayName("MCS-004 MCS-002 AC2 the month counts the supplies with data that every day of it counts, under uniform coverage")
    void theMonthCountsTheSuppliesWithDataEveryDayCounts() throws Exception {
        Membership membership = membershipWithTwoSupplies();
        publishMonth(membership.first(), JUNE);
        write(membership.first(), "2024/06/10", "13:00", 5f, 2f, 1f);
        Membership second = membershipWithTwoSupplies();
        publishMonth(second.first(), JUNE);
        writeUnpublished(second.second(), hoursOf(JUNE), 3f, 0.5f);

        for (Membership each : List.of(membership, second)) {
            JsonNode month = membershipSeries(each, "monthly", JUNE.atDay(1), JUNE.plusMonths(1).atDay(1)).get(0);
            JsonNode days = membershipSeries(each, "daily", JUNE.atDay(1), JUNE.plusMonths(1).atDay(1));

            assertEquals(30, days.size());
            assertEquals(1, month.path("suppliesWithData").asInt());
            for (JsonNode day : days) {
                assertEquals(month.path("suppliesWithData").asInt(), day.path("suppliesWithData").asInt(),
                        day::toString);
            }
        }
    }

    // --- Gaps and measured zeros (AC3, AC4) ---

    /**
     * May has no record of either supply; June is published, with zeros only. May, a day of May and
     * an hour of it are gaps: zero energy, no savings and no tariff source. June is a measured zero:
     * zero self-consumption, savings of 0.00 with their source, and both supplies with data.
     */
    @Test
    @DisplayName("MCS-001 MCS-004 AC3 AC4 a bucket without any supply with data is a gap, a published zero is a measured zero")
    void aBucketWithoutDataIsAGapAndAPublishedZeroIsAMeasuredZero() throws Exception {
        Membership membership = membershipWithTwoSupplies();
        publishMonth(membership.first(), JUNE);
        publishMonth(membership.second(), JUNE);

        JsonNode months = membershipSeries(membership, "monthly", MAY.atDay(1), JUNE.plusMonths(1).atDay(1));
        JsonNode mayDay = membershipSeries(membership, "daily", MAY.atDay(10), MAY.atDay(11)).get(0);
        JsonNode mayHour = membershipSeries(membership, "hourly", MAY.atDay(10), MAY.atDay(11)).get(12);
        JsonNode juneDay = membershipSeries(membership, "daily", JUNE_10, JUNE_10.plusDays(1)).get(0);

        for (JsonNode gap : List.of(months.get(0), mayDay, mayHour)) {
            assertEquals(0, gap.path("suppliesWithData").asInt(), gap::toString);
            assertDecimal("0", gap.path("gridImportKWh"));
            assertDecimal("0", gap.path("selfConsumptionKWh"));
            assertTrue(gap.path("savingsEur").isNull(), gap::toString);
            assertTrue(gap.path("tariffSource").isNull(), gap::toString);
        }
        for (JsonNode measuredZero : List.of(months.get(1), juneDay)) {
            assertEquals(2, measuredZero.path("suppliesWithData").asInt(), measuredZero::toString);
            assertDecimal("0", measuredZero.path("selfConsumptionKWh"));
            assertTrue(measuredZero.path("savingsEur").isNumber(), measuredZero::toString);
            assertDecimal("0", measuredZero.path("savingsEur"));
            assertEquals("ESTIMATE", measuredZero.path("tariffSource").asText());
        }
    }

    // --- The threshold (AC5) ---

    /**
     * Of June's 720 hours, the first supply's records carry self-consumption at 648 -- exactly 90 %
     * -- and the second's at 647. The first has data in June, in both series; the second has none:
     * the membership counts one supply and the supply series omits the second's June.
     */
    @Test
    @DisplayName("MCS-004 SCS-002 ENM-001 AC5 AC6 a month at exactly 90 % of its hours has data and one hour less has none")
    void aMonthAtExactlyNinetyPercentHasDataAndOneHourLessHasNone() throws Exception {
        Membership membership = membershipWithTwoSupplies();
        writePublishedHours(membership.first(), hoursOf(JUNE), 648, 1f);
        writePublishedHours(membership.second(), hoursOf(JUNE), 647, 1f);
        String start = JUNE.atDay(1).atStartOfDay(ZONE).toOffsetDateTime().toString();
        String end = JUNE.plusMonths(1).atDay(1).atStartOfDay(ZONE).toOffsetDateTime().toString();

        JsonNode month = membershipSeries(membership, "monthly", JUNE.atDay(1), JUNE.plusMonths(1).atDay(1)).get(0);
        JsonNode first = supplySeries(membership, membership.first(), "monthly", start, end);
        JsonNode second = supplySeries(membership, membership.second(), "monthly", start, end);

        assertEquals(1, month.path("suppliesWithData").asInt());
        assertDecimal("720", month.path("gridImportKWh"));
        assertEquals(1, first.size());
        assertEquals("2024/06/01", first.get(0).path("date").asText());
        assertDecimal("720", first.get(0).path("gridImportKWh"));
        assertEquals(0, second.size(), second::toString);
    }

    /**
     * A period of ten hours of 10 June: the first supply's records carry self-consumption at 9 of
     * them -- exactly 90 % of the day's hours inside the period -- and the second's at 8. Both
     * stored all ten hours with consumption. The first has data that day; the second is a gap in its
     * own series, its known consumption included, and does not count in the membership's day.
     */
    @Test
    @DisplayName("MCS-002 SCS-003 ENM-001 AC5 AC4 a day at exactly 90 % of its hours inside the period has data and one hour less is a gap")
    void aDayAtExactlyNinetyPercentHasDataAndOneHourLessIsAGap() throws Exception {
        Membership membership = membershipWithTwoSupplies();
        List<Instant> tenHours = hoursBetween(JUNE_10.atStartOfDay(ZONE).toInstant(), 10);
        writePublishedHours(membership.first(), tenHours, 9, 1f);
        writePublishedHours(membership.second(), tenHours, 8, 1f);
        String start = JUNE_10.atStartOfDay(ZONE).toOffsetDateTime().toString();
        String end = JUNE_10.atStartOfDay(ZONE).plusHours(10).toOffsetDateTime().toString();

        JsonNode day = json(mockMvc.perform(get(MEMBERSHIP_PATH + "daily", membership.community().getId(),
                        membership.member().getId())
                .header(HttpHeaders.AUTHORIZATION, loginUser(membership.member()))
                .queryParam("startDate", start)
                .queryParam("endDate", end))).get(0);
        JsonNode first = supplySeries(membership, membership.first(), "daily", start, end).get(0);
        JsonNode second = supplySeries(membership, membership.second(), "daily", start, end).get(0);

        assertEquals(1, day.path("suppliesWithData").asInt());
        assertDecimal("10", day.path("gridImportKWh"));
        assertEquals("Real", first.path("obtainMethod").asText());
        assertDecimal("10", first.path("gridImportKWh"));
        assertSupplyGap(second);
    }

    // --- Supply gaps hide known consumption (SCS-001, SCS-003) ---

    /**
     * On 10 June the supply stored every hour with consumption, but only the hours up to 12:00 carry
     * self-consumption. From 12:00 on, each hour is a gap although its consumption is known; so is
     * the day, at 12 of 24 hours.
     */
    @Test
    @DisplayName("SCS-001 SCS-003 AC4 a bucket whose consumption is known but whose self-consumption is not published is a gap")
    void aBucketWithKnownConsumptionButNoPublishedSelfConsumptionIsAGap() throws Exception {
        Membership membership = membershipWithTwoSupplies();
        List<Instant> day = hoursBetween(JUNE_10.atStartOfDay(ZONE).toInstant(), 24);
        writePublishedHours(membership.first(), day, 12, 2f);
        String start = JUNE_10.atStartOfDay(ZONE).toOffsetDateTime().toString();
        String end = JUNE_10.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime().toString();

        JsonNode hours = supplySeries(membership, membership.first(), "hourly", start, end);
        JsonNode days = supplySeries(membership, membership.first(), "daily", start, end);

        assertEquals(24, hours.size());
        assertEquals("Real", hours.get(11).path("obtainMethod").asText());
        assertDecimal("2", hours.get(11).path("gridImportKWh"));
        assertSupplyGap(hours.get(12));
        assertSupplyGap(hours.get(23));
        assertSupplyGap(days.get(0));
    }

    // --- Fully published data does not change (AC7) ---

    /**
     * Both supplies published at every hour of March 2024, with records carrying four decimals. The
     * monthly pre-aggregate the series used to read is built by the production aggregation over the
     * same records, and each monthly bucket, of the supply and of the membership, reports exactly
     * what it would have reported from it: every energy figure, the savings and the obtain method.
     */
    @Test
    @DisplayName("MCS-004 SCS-002 AC7 a fully published month reports exactly what the monthly pre-aggregate gave")
    void aFullyPublishedMonthReportsWhatThePreAggregateGave() throws Exception {
        YearMonth march = YearMonth.of(2024, 3);
        Membership membership = membershipWithTwoSupplies();
        publishMonth(membership.first(), march);
        publishMonth(membership.second(), march);
        write(membership.first(), "2024/03/01", "01:00", 1.0005f, 0.3337f, 2.1119f);
        write(membership.first(), "2024/03/15", "13:00", 3.4567f, 1.2345f, 0.9876f);
        write(membership.first(), "2024/03/31", "24:00", 0.0007f, 0.0049f, 0.0001f);
        write(membership.second(), "2024/03/10", "11:00", 0.1234f, 0.0164f, 0.1111f);
        write(membership.second(), "2024/03/31", "03:00", 0.2222f, 0.0170f, 0.2222f);
        String start = march.atDay(1).atStartOfDay(ZONE).toOffsetDateTime().toString();
        String end = march.plusMonths(1).atDay(1).atStartOfDay(ZONE).toOffsetDateTime().toString();

        List<DatadisConsumption> aggregates = new ArrayList<>();
        for (Supply supply : List.of(membership.first(), membership.second())) {
            monthlyAggregationRepository.aggregateMonthlyConsumption(supply, march.getMonth(), march.getYear());
            List<DatadisConsumption> stored = influxFixture.readMonthlyAggregates(supply.getCode(),
                    OffsetDateTime.parse(start).toInstant(), OffsetDateTime.parse(end).toInstant());
            assertEquals(1, stored.size(), stored::toString);
            aggregates.add(stored.get(0));

            JsonNode bucket = supplySeries(membership, supply, "monthly", start, end).get(0);
            DatadisConsumption aggregate = stored.get(0);
            assertDecimal(rounded(aggregate.getConsumptionKWh()), bucket.path("gridImportKWh"));
            assertDecimal(rounded(aggregate.getSelfConsumptionEnergyKWh()), bucket.path("selfConsumptionKWh"));
            assertDecimal(rounded(aggregate.getSurplusEnergyKWh()), bucket.path("surplusKWh"));
            assertDecimal(KWh(reported(aggregate.getSelfConsumptionEnergyKWh()).doubleValue()
                            + reported(aggregate.getSurplusEnergyKWh()).doubleValue()),
                    bucket.path("assignedProductionKWh"));
            assertDecimal(savings(reported(aggregate.getSelfConsumptionEnergyKWh())), bucket.path("savingsEur"));
            assertEquals(aggregate.getObtainMethod(), bucket.path("obtainMethod").asText());
        }

        JsonNode month = membershipSeries(membership, "monthly", march.atDay(1), march.plusMonths(1).atDay(1)).get(0);
        BigDecimal gridImport = reported(aggregates.get(0).getConsumptionKWh())
                .add(reported(aggregates.get(1).getConsumptionKWh()));
        BigDecimal selfConsumption = reported(aggregates.get(0).getSelfConsumptionEnergyKWh())
                .add(reported(aggregates.get(1).getSelfConsumptionEnergyKWh()));
        BigDecimal surplus = reported(aggregates.get(0).getSurplusEnergyKWh())
                .add(reported(aggregates.get(1).getSurplusEnergyKWh()));
        assertDecimal(KWh(gridImport.doubleValue()), month.path("gridImportKWh"));
        assertDecimal(KWh(selfConsumption.doubleValue()), month.path("selfConsumptionKWh"));
        assertDecimal(KWh(surplus.doubleValue()), month.path("surplusKWh"));
        assertDecimal(KWh(selfConsumption.doubleValue() + surplus.doubleValue()),
                month.path("assignedProductionKWh"));
        // The supplies' savings are added unrounded and rounded once; at a constant price that is the
        // summed self-consumption priced once.
        assertDecimal(savings(selfConsumption), month.path("savingsEur"));
        assertEquals(2, month.path("suppliesWithData").asInt());
    }

    /**
     * A fully published day: its daily bucket, and each of its hourly buckets, report the sums and
     * figures of exactly the records written, as before.
     */
    @Test
    @DisplayName("MCS-002 MCS-003 SCS-001 SCS-003 AC7 a fully published day and its hours report exactly the records written")
    void aFullyPublishedDayAndItsHoursReportTheRecordsWritten() throws Exception {
        Membership membership = membershipWithTwoSupplies();
        publishMonth(membership.first(), JUNE);
        publishMonth(membership.second(), JUNE);
        write(membership.first(), "2024/06/10", "11:00", 1.0005f, 0.3337f, 2.1119f);
        write(membership.first(), "2024/06/10", "14:00", 3.4567f, 1.2345f, 0.9876f);
        write(membership.second(), "2024/06/10", "14:00", 0.1234f, 0.0164f, 0.1111f);
        String start = JUNE_10.atStartOfDay(ZONE).toOffsetDateTime().toString();
        String end = JUNE_10.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime().toString();

        JsonNode supplyDay = supplySeries(membership, membership.first(), "daily", start, end).get(0);
        JsonNode supplyHours = supplySeries(membership, membership.first(), "hourly", start, end);
        JsonNode day = membershipSeries(membership, "daily", JUNE_10, JUNE_10.plusDays(1)).get(0);
        JsonNode hours = membershipSeries(membership, "hourly", JUNE_10, JUNE_10.plusDays(1));

        // 1.0005 + 3.4567 = 4.4572; 0.3337 + 1.2345 = 1.5682 at 0.15 is 0.23523.
        assertDecimal("4.457", supplyDay.path("gridImportKWh"));
        assertDecimal("1.568", supplyDay.path("selfConsumptionKWh"));
        assertDecimal("0.24", supplyDay.path("savingsEur"));
        assertDecimal("3.457", supplyHours.get(13).path("gridImportKWh"));
        assertDecimal("1.235", supplyHours.get(13).path("selfConsumptionKWh"));
        assertDecimal("0.19", supplyHours.get(13).path("savingsEur"));
        // The membership adds the second supply: 4.5806 kWh, 1.5846 self-consumed, at 0.15 0.23769.
        assertDecimal("4.581", day.path("gridImportKWh"));
        assertDecimal("1.585", day.path("selfConsumptionKWh"));
        assertDecimal("0.24", day.path("savingsEur"));
        assertDecimal("3.580", hours.get(13).path("gridImportKWh"));
        assertDecimal("1.251", hours.get(13).path("selfConsumptionKWh"));
        assertEquals(2, hours.get(13).path("suppliesWithData").asInt());
    }

    // --- fixtures ---

    private record Membership(Community community, User member, Supply first, Supply second) {
    }

    private Membership membershipWithTwoSupplies() {
        return membershipWithTwoSupplies(createCommunityRepository.create(CommunityMother.random().build()));
    }

    private Membership membershipWithTwoSupplies(Community community) {
        User member = UserMother.randomUser();
        member.enable();
        createUserRepository.create(member);
        createMembershipRepository.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        return new Membership(community, member, createSupply(member, community), createSupply(member, community));
    }

    private Supply createSupply(User owner, Community community) {
        Supply supply = createSupplyRepository.create(SupplyMother.random(owner).build(), UserId.of(owner.getId()),
                community.getId());
        writtenCups.add(supply.getCode());
        return supply;
    }

    /** A record carrying zero at every hour of the month, written before the records it holds. */
    private void publishMonth(Supply supply, YearMonth month) {
        influxFixture.writePublishedZeros(supply.getCode(), month, ZONE);
    }

    private void write(Supply supply, String date, String label, float gridImport, float selfConsumption,
                       float surplus) {
        influxFixture.write(List.of(hourlyRecord(supply.getCode(), date, label, gridImport, selfConsumption,
                surplus)));
    }

    /** Records with consumption and surplus but without self-consumption, as before publication. */
    private void writeUnpublished(Supply supply, List<Instant> hours, float gridImport, float surplus) {
        influxFixture.writeAt(supply.getCode(), hours, gridImport, null, surplus);
    }

    /**
     * A record at every one of the hours, each with {@code gridImport}; the first {@code published}
     * of them carry zero self-consumption, the rest none.
     */
    private void writePublishedHours(Supply supply, List<Instant> hours, int published, float gridImport) {
        influxFixture.writeAt(supply.getCode(), hours.subList(0, published), gridImport, 0f, 0f);
        writeUnpublished(supply, hours.subList(published, hours.size()), gridImport, 0f);
    }

    private static List<Instant> hoursOf(YearMonth month) {
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant();
        return Stream.iterate(month.atDay(1).atStartOfDay(ZONE).toInstant(), time -> time.isBefore(end),
                time -> time.plus(Duration.ofHours(1))).toList();
    }

    private static List<Instant> hoursBetween(Instant start, int hours) {
        return Stream.iterate(start, time -> time.plus(Duration.ofHours(1))).limit(hours).toList();
    }

    private JsonNode membershipSeries(Membership membership, String series, LocalDate from, LocalDate to)
            throws Exception {
        return json(mockMvc.perform(get(MEMBERSHIP_PATH + series, membership.community().getId(),
                        membership.member().getId())
                .header(HttpHeaders.AUTHORIZATION, loginUser(membership.member()))
                .queryParam("startDate", from.atStartOfDay(ZONE).toOffsetDateTime().toString())
                .queryParam("endDate", to.atStartOfDay(ZONE).toOffsetDateTime().toString())));
    }

    private JsonNode supplySeries(Membership membership, Supply supply, String series, String start, String end)
            throws Exception {
        return json(mockMvc.perform(get(SUPPLY_PATH + series, supply.getId())
                .header(HttpHeaders.AUTHORIZATION, loginUser(membership.member()))
                .queryParam("startDate", start)
                .queryParam("endDate", end)));
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return objectMapper.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /** A supply bucket without data: zero energy, no obtain method, nothing saved. */
    private static void assertSupplyGap(JsonNode bucket) {
        assertTrue(bucket.path("obtainMethod").isNull(), bucket::toString);
        assertDecimal("0", bucket.path("gridImportKWh"));
        assertDecimal("0", bucket.path("selfConsumptionKWh"));
        assertDecimal("0", bucket.path("surplusKWh"));
        assertTrue(bucket.path("savingsEur").isNumber(), bucket::toString);
        assertDecimal("0", bucket.path("savingsEur"));
    }

    private static BigDecimal reported(Float kWh) {
        return DatadisEnergyValues.exactlyAsReported(kWh);
    }

    /** A figure as reported, rounded to 3 decimals as every series presents it. */
    private static BigDecimal rounded(Float kWh) {
        return KWh(reported(kWh).doubleValue());
    }

    private static BigDecimal KWh(double kWh) {
        return BigDecimal.valueOf(KWhRounding.round(kWh));
    }

    /** The estimate of the test profile, 0.15 EUR/kWh without VAT, rounded to cents. */
    private static BigDecimal savings(BigDecimal selfConsumptionKWh) {
        return selfConsumptionKWh.multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP);
    }

    private static void assertDecimal(String expected, JsonNode actual) {
        assertDecimal(new BigDecimal(expected), actual);
    }

    private static void assertDecimal(BigDecimal expected, JsonNode actual) {
        assertEquals(0, expected.compareTo(actual.decimalValue()),
                () -> "expected " + expected + " but was " + actual);
    }
}
