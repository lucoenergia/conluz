package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.lucoenergia.conluz.domain.admin.supply.delete.DeleteSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.disable.DisableSupplyService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture.hourlyRecord;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The per-supply Datadis CSV end to end over a real PostgreSQL and InfluxDB. The clock is at 2026-11-10 in Madrid,
 * the configured zone. Records are stored through the production Datadis persistence, by the fixture: a whole local
 * day of records is placed by position, a lone record one hour before its Datadis label.
 */
class GetSupplyDatadisConsumptionHourlyCsvControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/supplies/{supplyId}/consumption/datadis/hourly/csv";
    private static final String HOURLY_SERIES_PATH = "/api/v1/supplies/{supplyId}/consumption/hourly";
    private static final Instant NOW = Instant.parse("2026-11-10T09:30:00Z");
    private static final String BOM = "﻿";
    private static final String HEADER = "cups;date;time;consumptionKWh;obtainMethod;surplusEnergyKWh;"
            + "generationEnergyKWh;selfConsumptionEnergyKWh";
    private static final List<String> FALL_BACK_LABELS = labels("01:00", "02:00", "03:00", "03:00");
    private static final List<String> SPRING_FORWARD_LABELS = labels("01:00", "02:00");

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private DeleteSupplyRepository deleteSupplyRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private DisableSupplyService disableSupplyService;
    @Autowired
    private DatadisConsumptionInfluxFixture datadis;
    @Autowired
    private ManagePlatformAdminRepository managePlatformAdminRepository;

    private final List<UserId> platformAdmins = new ArrayList<>();

    private Community community;
    private User owner;
    private Supply supply;
    private String cups;

    @BeforeEach
    void setUp() {
        travelTo(NOW);
        // A community of its own: the test commits what it creates, and other tests count the default community's
        // supplies. The supply is deleted after each test, since other tests count every supply.
        community = createCommunityRepository.create(CommunityMother.random().build());
        owner = UserMother.randomUser();
        owner.enable();
        createUserRepository.create(owner);
        createMembershipRepository.create(community.getId(), owner.getId(), CommunityRole.COMMUNITY_MEMBER);
        supply = createSupplyRepository.create(SupplyMother.random(owner).build(), UserId.of(owner.getId()),
                community.getId());
        cups = supply.getCode();
    }

    @AfterEach
    void tearDown() {
        platformAdmins.forEach(managePlatformAdminRepository::revoke);
        datadis.clear(cups);
        deleteSupplyRepository.delete(SupplyId.of(supply.getId()));
    }

    @Test
    @DisplayName("DCX-001 DCX-002 AC1 the owner gets the month's records as a ; separated UTF-8 CSV with a BOM and CRLF line ends, in chronological order")
    void theOwnerGetsTheMonthAsACsv() throws Exception {
        DatadisConsumption withGeneration = hourlyRecord(cups, "2026/09/01", "13:00", 0.5f, 0.125f, 0.25f);
        withGeneration.setGenerationEnergyKWh(1.5f);
        datadis.write(List.of(
                hourlyRecord(cups, "2026/09/30", "24:00", 3f, 0f, 0f),
                withGeneration,
                hourlyRecord(cups, "2026/09/15", "08:00", 2f, 0.75f, 0f)));

        MockHttpServletResponse response = csv(loginUser(owner), "2026-09").andExpect(status().isOk())
                .andReturn().getResponse();

        assertEquals("text/csv;charset=UTF-8", response.getContentType());
        assertEquals(cups + "-2026-09.csv", filename(response));
        byte[] body = response.getContentAsByteArray();
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Arrays.copyOf(body, 3));
        assertEquals(BOM + HEADER + "\r\n"
                        + cups + ";2026/09/01;12:00;0,5;Real;0,25;1,5;0,125\r\n"
                        + cups + ";2026/09/15;07:00;2;Real;0;;0,75\r\n"
                        + cups + ";2026/09/30;23:00;3;Real;0;;0\r\n",
                new String(body, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("DCX-001 AC2 AC3 numbers are unrounded with a decimal comma and no exponent, and a null value is an empty field on a record still written")
    void numbersAreUnroundedAndNullsAreEmpty() throws Exception {
        datadis.write(List.of(
                hourlyRecord(cups, "2026/09/10", "11:00", 0.0005f, 12.345f, 1f),
                hourlyRecord(cups, "2026/09/10", "12:00", 1f, null, 0f)));

        assertEquals(List.of(
                        cups + ";2026/09/10;10:00;0,0005;Real;1;;12,345",
                        cups + ";2026/09/10;11:00;1;Real;0;;"),
                lines(csvBody(loginUser(owner), "2026-09")));
    }

    @Test
    @DisplayName("DCX-003 AC4 the month is half-open over its local midnights: the hours just before and just after it are not written")
    void theNeighbouringMonthsHoursAreNotWritten() throws Exception {
        datadis.writeAt(cups, List.of(
                Instant.parse("2026-08-31T21:00:00Z"),   // 2026-08-31 23:00 local
                Instant.parse("2026-08-31T22:00:00Z"),   // 2026-09-01 00:00 local
                Instant.parse("2026-09-30T21:00:00Z"),   // 2026-09-30 23:00 local
                Instant.parse("2026-09-30T22:00:00Z")),  // 2026-10-01 00:00 local
                1f, 0f, 0f);

        assertEquals(List.of(
                        cups + ";2026/09/01;00:00;1;Real;0;;0",
                        cups + ";2026/09/30;23:00;1;Real;0;;0"),
                lines(csvBody(loginUser(owner), "2026-09")));
    }

    @Test
    @DisplayName("DCX-004 AC5 a month without records is the header line only")
    void aMonthWithoutRecordsIsTheHeaderOnly() throws Exception {
        datadis.write(List.of(hourlyRecord(cups, "2026/08/31", "24:00", 1f, 0f, 0f),
                hourlyRecord(cups, "2026/10/01", "01:00", 1f, 0f, 0f)));

        assertEquals(BOM + HEADER + "\r\n", csvBody(loginUser(owner), "2026-09"));
    }

    @Test
    @DisplayName("DCX-003 AC6 the current month is written with the records stored so far")
    void theCurrentMonthIsAllowed() throws Exception {
        datadis.write(List.of(hourlyRecord(cups, "2026/11/02", "05:00", 1f, null, 0f),
                hourlyRecord(cups, "2026/11/09", "21:00", 2f, null, 0f)));

        assertEquals(List.of(cups + ";2026/11/02;04:00;1;Real;0;;", cups + ";2026/11/09;20:00;2;Real;0;;"),
                lines(csvBody(loginUser(owner), "2026-11")));
    }

    @Test
    @DisplayName("DCX-003 AC7 a month after the current one is CONSUMPTION_CSV_MONTH_IN_FUTURE, the current month taken in the application's time zone")
    void aFutureMonthIsRefused() throws Exception {
        String token = loginUser(owner);
        for (String month : new String[]{"2026-12", "2027-01", "2030-06"}) {
            csv(token, month)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].code").value("CONSUMPTION_CSV_MONTH_IN_FUTURE"));
        }

        // Still November in UTC, already December in Madrid.
        travelTo(Instant.parse("2026-11-30T23:30:00Z"));
        token = loginUser(owner);
        csv(token, "2026-12").andExpect(status().isOk());
        csv(token, "2027-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("CONSUMPTION_CSV_MONTH_IN_FUTURE"));
    }

    @Test
    @DisplayName("DCX-003 AC8 a missing or malformed month is a bad request with the standard code")
    void aMissingOrMalformedMonthIsABadRequest() throws Exception {
        String token = loginUser(owner);
        mockMvc.perform(get(PATH, supply.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(nullValue()));
        for (String month : new String[]{"2026-13", "2026-9", "2026/09", "2026-09-01", "september", ""}) {
            csv(token, month)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].code").value(nullValue()));
        }
    }

    @Test
    @DisplayName("DCX-002 AC9 the day the clocks go back has 25 lines, the repeated 02:00 twice in a row, earlier instant first, each line with the date and time getSupplyHourlyConsumption gives the same hour")
    void theDayTheClocksGoBack() throws Exception {
        datadis.write(fullDay("2026/10/25", FALL_BACK_LABELS));
        String token = loginUser(owner);

        List<String> day = lines(csvBody(token, "2026-10")).stream()
                .filter(line -> line.startsWith(cups + ";2026/10/25;")).toList();

        assertEquals(25, day.size());
        // The third and fourth records of the day: 02:00-03:00 in summer time, then again in winter time.
        assertEquals(cups + ";2026/10/25;02:00;3;Real;0;;0", day.get(2));
        assertEquals(cups + ";2026/10/25;02:00;4;Real;0;;0", day.get(3));
        assertEquals(1, day.stream().filter(line -> line.contains(";01:00;")).count());
        assertEquals(1, day.stream().filter(line -> line.contains(";03:00;")).count());
        assertEquals(hourlySeriesLabels(token, "2026-10-25T00:00:00+02:00", "2026-10-26T00:00:00+01:00"),
                dateAndTime(day));
    }

    @Test
    @DisplayName("DCX-002 AC10 the day the clocks go forward has 23 lines and none for the skipped 02:00, each line with the date and time getSupplyHourlyConsumption gives the same hour")
    void theDayTheClocksGoForward() throws Exception {
        datadis.write(fullDay("2026/03/29", SPRING_FORWARD_LABELS));
        String token = loginUser(owner);

        List<String> day = lines(csvBody(token, "2026-03")).stream()
                .filter(line -> line.startsWith(cups + ";2026/03/29;")).toList();

        assertEquals(23, day.size());
        assertFalse(day.stream().anyMatch(line -> line.contains(";02:00;")));
        assertEquals(cups + ";2026/03/29;01:00;2;Real;0;;0", day.get(1));
        assertEquals(cups + ";2026/03/29;03:00;3;Real;0;;0", day.get(2));
        assertEquals(hourlySeriesLabels(token, "2026-03-29T00:00:00+01:00", "2026-03-30T00:00:00+02:00"),
                dateAndTime(day));
    }

    @Test
    @DisplayName("DCX-005 AC11 AC12 every caller gets the status getSupplyHourlyConsumption gives them: owner and community admin 200, other members and a platform admin who is not a member 404, no token 401")
    void everyCallerIsAnsweredAsForTheHourlySeries() throws Exception {
        datadis.write(List.of(hourlyRecord(cups, "2026/09/10", "11:00", 1f, 0f, 0f)));
        Community otherCommunity = createCommunityRepository.create(CommunityMother.random().build());

        Map<String, String> tokens = new LinkedHashMap<>();
        tokens.put("owner", loginUser(owner));
        tokens.put("community admin", loginAsCommunityAdmin(community.getId()));
        tokens.put("another member of the community", loginAsCommunityMember(community.getId()));
        tokens.put("a member of another community", loginAsCommunityMember(otherCommunity.getId()));
        tokens.put("a platform admin who is not a member", loginAsPlatformAdminWithoutMembership());
        tokens.put("no token", null);
        Map<String, Integer> expected = Map.of("owner", 200, "community admin", 200,
                "another member of the community", 404, "a member of another community", 404,
                "a platform admin who is not a member", 404, "no token", 401);

        for (Map.Entry<String, String> caller : tokens.entrySet()) {
            int csvStatus = mockMvc.perform(withToken(get(PATH, supply.getId()).queryParam("month", "2026-09"),
                    caller.getValue())).andReturn().getResponse().getStatus();
            int seriesStatus = mockMvc.perform(withToken(get(HOURLY_SERIES_PATH, supply.getId())
                            .queryParam("startDate", "2026-09-10T00:00:00+02:00")
                            .queryParam("endDate", "2026-09-11T00:00:00+02:00"), caller.getValue()))
                    .andReturn().getResponse().getStatus();

            assertEquals(expected.get(caller.getKey()), csvStatus, caller.getKey());
            assertEquals(seriesStatus, csvStatus, caller.getKey());
        }
    }

    @Test
    @DisplayName("DCX-005 AC13 a disabled supply still has every stored record of the month written")
    void aDisabledSupplyIsNotClipped() throws Exception {
        datadis.write(List.of(hourlyRecord(cups, "2026/09/10", "11:00", 1f, 0f, 0f),
                hourlyRecord(cups, "2026/09/29", "11:00", 2f, 0f, 0f)));
        disableSupplyService.disable(SupplyId.of(supply.getId()));

        assertEquals(List.of(cups + ";2026/09/10;10:00;1;Real;0;;0", cups + ";2026/09/29;10:00;2;Real;0;;0"),
                lines(csvBody(loginUser(owner), "2026-09")));
    }

    @Test
    @DisplayName("DCX-003 AC14 the 200 and the 400 responses are the ones api-docs publishes for the operation")
    void theResponsesMatchTheContract() throws Exception {
        String token = loginUser(owner);
        JsonNode document = objectMapper.readTree(mockMvc.perform(get("/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode operation = document.path("paths")
                .path("/api/v1/supplies/{supplyId}/consumption/datadis/hourly/csv").path("get");
        assertEquals("getSupplyDatadisConsumptionHourlyCsv", operation.path("operationId").asText());

        MockHttpServletResponse ok = csv(token, "2026-09").andExpect(status().isOk()).andReturn().getResponse();
        JsonNode csvSchema = operation.path("responses").path("200").path("content").path("text/csv").path("schema");
        assertEquals("string", csvSchema.path("type").asText());
        assertEquals("binary", csvSchema.path("format").asText());
        assertTrue(ok.getContentType().startsWith("text/csv"));

        assertFalse(operation.path("responses").path("400").path("content").path("application/json").isMissingNode());
        JsonNode future = objectMapper.readTree(csv(token, "2027-01").andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString());
        assertMatchesErrorSchemas(document, future);
        assertEquals("CONSUMPTION_CSV_MONTH_IN_FUTURE", future.path("errors").get(0).path("code").asText());
        assertMatchesErrorSchemas(document, objectMapper.readTree(csv(token, "2026-13")
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString()));
    }

    /**
     * The operation's 400 publishes the shared error example without a schema, so the body is checked against the
     * published {@code RestError} and {@code RestErrorDetail} schemas: exactly their properties, and a code from the
     * published enum. {@code ResponseContractAssertions} does not apply: those schemas predate the convention that
     * every response property is declared required.
     */
    private static void assertMatchesErrorSchemas(JsonNode document, JsonNode body) {
        JsonNode schemas = document.path("components").path("schemas");
        assertEquals(propertyNames(schemas.path("RestError")), fieldNames(body));
        JsonNode detailSchema = schemas.path("RestErrorDetail");
        List<String> codes = new ArrayList<>();
        detailSchema.path("properties").path("code").path("enum").forEach(code -> codes.add(code.asText()));
        assertFalse(body.path("errors").isEmpty());
        for (JsonNode detail : body.path("errors")) {
            assertEquals(propertyNames(detailSchema), fieldNames(detail));
            assertTrue(detail.path("code").isNull() || codes.contains(detail.path("code").asText()),
                    () -> detail.path("code") + " is not a published error code");
        }
    }

    private static Set<String> propertyNames(JsonNode schema) {
        Set<String> names = new TreeSet<>();
        schema.path("properties").fieldNames().forEachRemaining(names::add);
        assertFalse(names.isEmpty());
        return names;
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private ResultActions csv(String token, String month) throws Exception {
        return mockMvc.perform(get(PATH, supply.getId()).header(HttpHeaders.AUTHORIZATION, token)
                .queryParam("month", month));
    }

    private String csvBody(String token, String month) throws Exception {
        return new String(csv(token, month).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, token);
    }

    /**
     * The record lines, without the BOM, the header and the final line end.
     */
    private static List<String> lines(String body) {
        assertTrue(body.startsWith(BOM + HEADER + "\r\n"));
        assertTrue(body.endsWith("\r\n"));
        assertFalse(body.replace("\r\n", "").contains("\n"));
        List<String> lines = new ArrayList<>(List.of(body.split("\r\n")));
        lines.remove(0);
        return lines;
    }

    private static List<String> dateAndTime(List<String> lines) {
        return lines.stream().map(line -> {
            String[] fields = line.split(";");
            return fields[1] + " " + fields[2];
        }).toList();
    }

    private List<String> hourlySeriesLabels(String token, String startDate, String endDate) throws Exception {
        JsonNode series = objectMapper.readTree(mockMvc.perform(get(HOURLY_SERIES_PATH, supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .queryParam("startDate", startDate)
                        .queryParam("endDate", endDate))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> labels = new ArrayList<>();
        series.forEach(hour -> labels.add(hour.path("date").asText() + " " + hour.path("time").asText()));
        return labels;
    }

    /**
     * One record per Datadis label of the day, the n-th carrying n kWh of consumption.
     */
    private List<DatadisConsumption> fullDay(String date, List<String> labels) {
        return IntStream.range(0, labels.size())
                .mapToObj(i -> hourlyRecord(cups, date, labels.get(i), (float) (i + 1), 0f, 0f))
                .toList();
    }

    /**
     * The Datadis labels of a day: the given first ones, then every hour-end from {@code 04:00} to {@code 24:00}.
     */
    private static List<String> labels(String... first) {
        List<String> labels = new ArrayList<>(List.of(first));
        IntStream.rangeClosed(4, 24).forEach(hour -> labels.add(String.format("%02d:00", hour)));
        return List.copyOf(labels);
    }

    private static String filename(MockHttpServletResponse response) {
        ContentDisposition disposition = ContentDisposition.parse(response.getHeader(HttpHeaders.CONTENT_DISPOSITION));
        assertTrue(disposition.isAttachment());
        return disposition.getFilename();
    }

    /**
     * A platform admin with a personal ID of their own, revoked after the test. This test commits what it creates:
     * the default admin would stay in the database, where other tests create a user with its personal ID, and a
     * platform admin left behind would be counted by the tests that count them.
     */
    private String loginAsPlatformAdminWithoutMembership() throws Exception {
        User platformAdmin = UserMother.randomUser();
        platformAdmin.enable();
        createUserRepository.create(platformAdmin);
        managePlatformAdminRepository.grant(UserId.of(platformAdmin.getId()));
        platformAdmins.add(UserId.of(platformAdmin.getId()));
        return loginUser(platformAdmin);
    }
}
