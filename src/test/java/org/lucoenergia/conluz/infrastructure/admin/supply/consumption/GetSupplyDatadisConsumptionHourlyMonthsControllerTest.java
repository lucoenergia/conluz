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
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture.hourlyRecord;
import static org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ResponseContractAssertions.assertMatchesContract;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The months listing of a supply's hourly Datadis records end to end over a real PostgreSQL and InfluxDB. The clock
 * is at 2026-11-20 in Madrid, the configured zone. Records are stored through the production Datadis persistence, by
 * the fixture.
 */
class GetSupplyDatadisConsumptionHourlyMonthsControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/supplies/{supplyId}/consumption/datadis/hourly/months";
    private static final String CSV_PATH = "/api/v1/supplies/{supplyId}/consumption/datadis/hourly/csv";
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final Instant NOW = Instant.parse("2026-11-20T09:30:00Z");
    private static final String OTHER_CUPS = "ES0031406912345678MO0F";

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
        datadis.clear(cups);
        datadis.clear(OTHER_CUPS);
        deleteSupplyRepository.delete(SupplyId.of(supply.getId()));
    }

    @Test
    @DisplayName("DCX-006 DCX-007 AC1 one entry per month with records, newest first, with its records against the whole month, and none for the months between")
    void oneEntryPerMonthWithRecordsNewestFirst() throws Exception {
        datadis.writeAt(cups, hoursFrom("2026-11-01T00:00", 312), 1f, 0f, 0f);
        datadis.writeAt(cups, hoursFrom("2026-10-01T00:00", 745), 1f, 0f, 0f);
        datadis.writeAt(cups, hoursFrom("2025-06-10T00:00", 100), 1f, 0f, 0f);

        assertEquals(List.of("2026-11 312/720", "2026-10 745/745", "2025-06 100/720"),
                months(loginUser(owner)));
    }

    @Test
    @DisplayName("DCX-006 AC2 a supply without records is answered with an empty array")
    void noRecordsIsAnEmptyArray() throws Exception {
        String body = mockMvc.perform(get(PATH, supply.getId()).header(HttpHeaders.AUTHORIZATION, loginUser(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertEquals("[]", body);
    }

    @Test
    @DisplayName("DCX-007 AC3 the expected hours of a month in which the clocks go forward are 743, those of a leap February 696")
    void expectedHoursOfDaylightSavingAndLeapMonths() throws Exception {
        // The clock is moved to 2028 so that February 2028 is a past month.
        travelTo(Instant.parse("2028-03-10T09:30:00Z"));
        datadis.writeAt(cups, hoursFrom("2026-03-29T00:00", 3), 1f, 0f, 0f);
        datadis.writeAt(cups, hoursFrom("2028-02-29T10:00", 2), 1f, 0f, 0f);

        assertEquals(List.of("2028-02 2/696", "2026-03 3/743"), months(loginUser(owner)));
    }

    @Test
    @DisplayName("DCX-006 AC4 an hour starting at 23:00 on the last day counts for its month, one starting at 00:00 on the first day for the next")
    void hoursOnAMonthBoundary() throws Exception {
        datadis.writeAt(cups, hoursFrom("2026-09-30T23:00", 2), 1f, 0f, 0f);

        assertEquals(List.of("2026-10 1/745", "2026-09 1/720"), months(loginUser(owner)));
    }

    @Test
    @DisplayName("DCX-007 AC5 each month's hoursWithData is the number of lines after the header of its CSV export")
    void hoursWithDataMatchTheCsvExport() throws Exception {
        datadis.write(fullDay("2026/10/25", labels("01:00", "02:00", "03:00", "03:00")));
        datadis.write(List.of(hourlyRecord(cups, "2026/10/03", "08:00", 2f, null, 0f),
                hourlyRecord(cups, "2026/09/30", "24:00", 3f, 0f, 0f),
                hourlyRecord(cups, "2026/08/01", "01:00", 4f, 0f, 0f),
                hourlyRecord(cups, "2026/08/15", "12:00", 5f, 0f, 0f)));
        // A record carrying none of the energy fields: only its obtain method is stored.
        datadis.writeAt(cups, Instant.parse("2026-08-20T10:00:00Z"), null, null, null);
        String token = loginUser(owner);

        JsonNode months = objectMapper.readTree(monthsBody(token));

        assertEquals(List.of("2026-10", "2026-09", "2026-08"), monthNames(months));
        assertEquals(List.of(26L, 1L, 3L), hoursWithData(months));
        for (JsonNode month : months) {
            assertEquals(month.path("hoursWithData").asLong(), csvLines(token, month.path("month").asText()),
                    month.path("month").asText());
        }
    }

    @Test
    @DisplayName("DCX-006 AC6 months older than 24 months are listed")
    void oldMonthsAreListed() throws Exception {
        datadis.writeAt(cups, hoursFrom("2023-05-01T00:00", 2), 1f, 0f, 0f);
        datadis.writeAt(cups, hoursFrom("2001-01-15T00:00", 1), 1f, 0f, 0f);
        datadis.writeAt(cups, hoursFrom("2026-11-01T00:00", 1), 1f, 0f, 0f);

        assertEquals(List.of("2026-11 1/720", "2023-05 2/744", "2001-01 1/744"), months(loginUser(owner)));
    }

    @Test
    @DisplayName("DCX-007 another supply's records never count, even in the same months")
    void anotherSupplysRecordsNeverCount() throws Exception {
        datadis.writeAt(cups, hoursFrom("2026-10-05T00:00", 2), 1f, 0f, 0f);
        datadis.writeAt(cups, hoursFrom("2026-08-05T00:00", 1), 1f, 0f, 0f);
        datadis.writeAt(OTHER_CUPS, hoursFrom("2026-10-05T00:00", 10), 1f, 0f, 0f);
        datadis.writeAt(OTHER_CUPS, hoursFrom("2026-09-05T00:00", 10), 1f, 0f, 0f);

        assertEquals(List.of("2026-10 2/745", "2026-08 1/744"), months(loginUser(owner)));
    }

    @Test
    @DisplayName("DCX-008 AC7 every caller gets the status getSupplyDatadisConsumptionHourlyCsv gives them: owner and community admin 200, other members and a platform admin who is not a member 404, no token 401")
    void everyCallerIsAnsweredAsForTheCsvExport() throws Exception {
        datadis.writeAt(cups, hoursFrom("2026-09-10T10:00", 1), 1f, 0f, 0f);
        Community otherCommunity = createCommunityRepository.create(CommunityMother.random().build());

        Map<String, String> tokens = new LinkedHashMap<>();
        tokens.put("owner", loginUser(owner));
        tokens.put("community admin", loginAsCommunityAdmin(community.getId()));
        tokens.put("another member of the community", loginAsCommunityMember(community.getId()));
        tokens.put("a member of another community", loginAsCommunityMember(otherCommunity.getId()));
        tokens.put("a platform admin who is not a member", loginAsDefaultPlatformAdmin());
        tokens.put("no token", null);
        Map<String, Integer> expected = Map.of("owner", 200, "community admin", 200,
                "another member of the community", 404, "a member of another community", 404,
                "a platform admin who is not a member", 404, "no token", 401);

        for (Map.Entry<String, String> caller : tokens.entrySet()) {
            int monthsStatus = mockMvc.perform(withToken(get(PATH, supply.getId()), caller.getValue()))
                    .andReturn().getResponse().getStatus();
            int csvStatus = mockMvc.perform(withToken(get(CSV_PATH, supply.getId()).queryParam("month", "2026-09"),
                    caller.getValue())).andReturn().getResponse().getStatus();

            assertEquals(expected.get(caller.getKey()), monthsStatus, caller.getKey());
            assertEquals(csvStatus, monthsStatus, caller.getKey());
        }
    }

    @Test
    @DisplayName("DCX-006 AC8 a disabled supply still has its months listed")
    void aDisabledSupplyIsListed() throws Exception {
        datadis.writeAt(cups, hoursFrom("2026-09-10T10:00", 2), 1f, 0f, 0f);
        datadis.writeAt(cups, hoursFrom("2026-07-10T10:00", 1), 1f, 0f, 0f);
        disableSupplyService.disable(SupplyId.of(supply.getId()));

        assertEquals(List.of("2026-09 2/720", "2026-07 1/744"), months(loginUser(owner)));
    }

    @Test
    @DisplayName("DCX-006 AC9 the 200 response is the array api-docs publishes for the operation, every property declared required")
    void theResponseMatchesTheContract() throws Exception {
        datadis.writeAt(cups, hoursFrom("2026-11-01T00:00", 2), 1f, 0f, 0f);
        datadis.writeAt(cups, hoursFrom("2026-09-01T00:00", 1), 1f, 0f, 0f);
        JsonNode document = objectMapper.readTree(mockMvc.perform(get("/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode operation = document.path("paths").path(PATH).path("get");
        assertEquals("getSupplyDatadisConsumptionHourlyMonths", operation.path("operationId").asText());
        JsonNode okSchema = operation.path("responses").path("200").path("content").path("*/*").path("schema");
        assertEquals("array", okSchema.path("type").asText());
        assertEquals("#/components/schemas/SupplyDatadisConsumptionMonthResponse",
                okSchema.path("items").path("$ref").asText());
        JsonNode schema = document.path("components").path("schemas").path("SupplyDatadisConsumptionMonthResponse");
        assertEquals(Set.of("month", "hoursWithData", "expectedHours"), strings(schema.path("required")));
        String pattern = schema.path("properties").path("month").path("pattern").asText();
        assertFalse(pattern.isEmpty());

        JsonNode body = objectMapper.readTree(monthsBody(loginUser(owner)));

        assertEquals(2, body.size());
        for (JsonNode month : body) {
            assertMatchesContract(document, "SupplyDatadisConsumptionMonthResponse", month);
            assertTrue(month.path("month").asText().matches(pattern), month.path("month").asText());
        }
    }

    /**
     * {@code count} consecutive hours on the instant line, from the local date and time in Madrid.
     */
    private static List<Instant> hoursFrom(String localStart, int count) {
        Instant start = LocalDateTime.parse(localStart).atZone(MADRID).toInstant();
        return IntStream.range(0, count).mapToObj(hour -> start.plusSeconds(3600L * hour)).toList();
    }

    private String monthsBody(String token) throws Exception {
        return mockMvc.perform(get(PATH, supply.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /**
     * Each entry as {@code month hoursWithData/expectedHours}, in the order of the response.
     */
    private List<String> months(String token) throws Exception {
        List<String> months = new ArrayList<>();
        objectMapper.readTree(monthsBody(token)).forEach(month -> months.add(month.path("month").asText() + " "
                + month.path("hoursWithData").asLong() + "/" + month.path("expectedHours").asLong()));
        return months;
    }

    private static List<String> monthNames(JsonNode months) {
        List<String> names = new ArrayList<>();
        months.forEach(month -> names.add(month.path("month").asText()));
        return names;
    }

    private static List<Long> hoursWithData(JsonNode months) {
        List<Long> hours = new ArrayList<>();
        months.forEach(month -> hours.add(month.path("hoursWithData").asLong()));
        return hours;
    }

    /**
     * The number of lines after the header of the month's CSV export.
     */
    private long csvLines(String token, String month) throws Exception {
        String body = new String(mockMvc.perform(get(CSV_PATH, supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, token).queryParam("month", month))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(body.endsWith("\r\n"));
        return body.split("\r\n").length - 1L;
    }

    private static Set<String> strings(JsonNode array) {
        Set<String> values = new TreeSet<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, token);
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
}
