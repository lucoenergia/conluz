package org.lucoenergia.conluz.infrastructure.price.sync;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.get.GetPriceRepository;
import org.lucoenergia.conluz.domain.price.persist.PersistOmiePricesRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.price.OmieConfig;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The re-synchronisation of OMIE prices, end to end: the request goes through the controller, the service and the
 * real OMIE adapter, whose HTTP calls are answered by {@link OmieStub} from fixtures, and the prices are written to
 * and read back from InfluxDB.
 */
class SyncPricesControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/prices/sync";

    /**
     * 12:00 on 2026-04-01 in Madrid: tomorrow is 2026-04-02, so a range may end on 2026-04-03 at the latest.
     */
    private static final Instant NOW = Instant.parse("2026-04-01T10:00:00Z");

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures/price");
    private static final String MARCH_28 = "marginalpdbc_20260328.1";
    private static final String MARCH_29 = "marginalpdbc_20260329.1";
    /**
     * A 24-hour day of 96 quarters, 2025-12-12, which the tests re-date to the days they need.
     */
    private static final String QUARTER_PRICES = "omie_quarter_prices.txt";
    /**
     * A day, 2025-12-13, whose periods do not fill it, so it is rejected under PRC-002.
     */
    private static final String WRONG_COUNT = "omie_quarter_prices_wrong_count.txt";

    @MockitoBean
    private ConluzRestClientBuilder conluzRestClientBuilder;

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private ManagePlatformAdminRepository managePlatformAdminRepository;
    @Autowired
    private DisableUserRepository disableUserRepository;
    @Autowired
    private PersistOmiePricesRepository persistOmiePricesRepository;
    @Autowired
    private GetPriceRepository getPriceRepository;
    @Autowired
    private InfluxDbConnectionManager influxDbConnectionManager;

    private OmieStub omie;

    @BeforeEach
    void beforeEach() {
        travelTo(NOW);
        clearStoredPrices();
        omie = new OmieStub();
        when(conluzRestClientBuilder.build(anyBoolean(), any(Duration.class))).thenReturn(omie.client());
    }

    @AfterEach
    void afterEach() {
        clearStoredPrices();
    }

    // --- outcomes ---

    @Test
    @DisplayName("PRC-005 PRC-006 each day is stored with its count or skipped with its reason, in date order")
    void syncsEachDayAndReportsItInOrder() throws Exception {
        omie.serve("20260310.1", redated(QUARTER_PRICES, "2025;12;12;", "2026;03;10;"));
        omie.serve("20260312.1", redated(QUARTER_PRICES, "2025;12;12;", "2026;03;12;"));

        sync(platformAdmin(), "2026-03-10", "2026-03-13")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].date").value("2026-03-10"))
                .andExpect(jsonPath("$[0].outcome").value("STORED"))
                .andExpect(jsonPath("$[0].storedPrices").value(96))
                .andExpect(jsonPath("$[0].skipReason").doesNotExist())
                .andExpect(jsonPath("$[1].date").value("2026-03-11"))
                .andExpect(jsonPath("$[1].outcome").value("SKIPPED"))
                .andExpect(jsonPath("$[1].storedPrices").value(0))
                .andExpect(jsonPath("$[1].skipReason").value("FILE_NOT_FOUND"))
                .andExpect(jsonPath("$[2].date").value("2026-03-12"))
                .andExpect(jsonPath("$[2].outcome").value("STORED"))
                .andExpect(jsonPath("$[2].storedPrices").value(96))
                .andExpect(jsonPath("$[2].skipReason").doesNotExist());

        assertEquals(96, storedBetween("2026-03-09T23:00:00Z", "2026-03-10T22:45:00Z").size());
        assertTrue(storedBetween("2026-03-10T23:00:00Z", "2026-03-11T22:45:00Z").isEmpty());
        assertEquals(96, storedBetween("2026-03-11T23:00:00Z", "2026-03-12T22:45:00Z").size());
        // The missing day is looked for under both file numbers
        assertTrue(omie.requested().containsAll(List.of("20260311.1", "20260311.2")));
    }

    @Test
    @DisplayName("PRC-006 a day whose file is rejected is skipped with FILE_REJECTED, and the other days are stored")
    void skipsARejectedDayAndStoresTheOthers() throws Exception {
        omie.serve("20260313.1", redated(QUARTER_PRICES, "2025;12;12;", "2026;03;13;"));
        omie.serve("20260314.1", redated(WRONG_COUNT, "2025;12;13;", "2026;03;14;"));
        omie.serve("20260315.1", redated(QUARTER_PRICES, "2025;12;12;", "2026;03;15;"));

        sync(platformAdmin(), "2026-03-13", "2026-03-16")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outcome").value("STORED"))
                .andExpect(jsonPath("$[1].date").value("2026-03-14"))
                .andExpect(jsonPath("$[1].outcome").value("SKIPPED"))
                .andExpect(jsonPath("$[1].storedPrices").value(0))
                .andExpect(jsonPath("$[1].skipReason").value("FILE_REJECTED"))
                .andExpect(jsonPath("$[2].outcome").value("STORED"));

        assertEquals(96, storedBetween("2026-03-12T23:00:00Z", "2026-03-13T22:45:00Z").size());
        assertTrue(storedBetween("2026-03-13T23:00:00Z", "2026-03-14T22:45:00Z").isEmpty());
        assertEquals(96, storedBetween("2026-03-14T23:00:00Z", "2026-03-15T22:45:00Z").size());
    }

    @Test
    @DisplayName("PRC-006 a day that cannot be fetched is skipped with FETCH_FAILED, and the other days are stored")
    void skipsADayThatCannotBeFetchedAndStoresTheOthers() throws Exception {
        omie.failConnection("20260316.1");
        omie.failConnection("20260316.2");
        omie.answer("20260317.1", 503);
        omie.serve("20260318.1", redated(QUARTER_PRICES, "2025;12;12;", "2026;03;18;"));

        sync(platformAdmin(), "2026-03-16", "2026-03-19")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skipReason").value("FETCH_FAILED"))
                .andExpect(jsonPath("$[1].skipReason").value("FETCH_FAILED"))
                .andExpect(jsonPath("$[2].outcome").value("STORED"))
                .andExpect(jsonPath("$[2].storedPrices").value(96));
    }

    // --- the clock change ---

    /**
     * Before #516, the prices of 2026-03-29, the 23-hour day, were stored one hour early, so its first hour overwrote
     * the last hour of 2026-03-28. Re-synchronising both days puts every price back at its own instant.
     */
    @Test
    @DisplayName("PRC-005 re-synchronising both days repairs prices stored one hour early on the day the clocks go forward")
    void repairsTheShiftOfTheDayTheClocksGoForward() throws Exception {
        Map<Instant, Double> march28 = expectedPrices(MARCH_28);
        Map<Instant, Double> march29 = expectedPrices(MARCH_29);
        persistOmiePricesRepository.persistPrices(march29.entrySet().stream()
                .map(price -> new PriceByHour(price.getValue(),
                        price.getKey().minus(Duration.ofHours(1)).atOffset(ZoneOffset.UTC)))
                .toList());
        assertEquals(march29.get(Instant.parse("2026-03-28T23:00:00Z")),
                stored("2026-03-28T22:00:00Z", "2026-03-28T22:00:00Z").get(Instant.parse("2026-03-28T22:00:00Z")),
                "the last hour of 28 March starts out holding 29 March's first price");
        omie.serve("20260328.1", fixture(MARCH_28));
        omie.serve("20260329.1", fixture(MARCH_29));

        sync(platformAdmin(), "2026-03-28", "2026-03-30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].storedPrices").value(96))
                .andExpect(jsonPath("$[1].storedPrices").value(92));

        Map<Instant, Double> stored29 = stored("2026-03-28T23:00:00Z", "2026-03-29T21:45:00Z");
        assertEquals(92, stored29.size());
        assertEquals(Instant.parse("2026-03-28T23:00:00Z"), firstKey(stored29));
        assertEquals(Instant.parse("2026-03-29T21:45:00Z"), lastKey(stored29));
        assertEquals(march29, stored29);

        Map<Instant, Double> lastHourOf28 = stored("2026-03-28T22:00:00Z", "2026-03-28T22:45:00Z");
        assertEquals(4, lastHourOf28.size());
        lastHourOf28.forEach((instant, price) -> assertEquals(march28.get(instant), price, instant::toString));

        // No shifted value remains: every instant of both days holds its own day's price, and nothing else is stored
        Map<Instant, Double> expected = new TreeMap<>(march28);
        expected.putAll(march29);
        assertEquals(expected, stored("2026-03-27T23:00:00Z", "2026-03-29T21:45:00Z"));
    }

    // --- idempotency ---

    @Test
    @DisplayName("PRC-007 sending the same request twice leaves the prices one request stores")
    void repeatingTheSameRequestChangesNothing() throws Exception {
        omie.serve("20260328.1", fixture(MARCH_28));
        omie.serve("20260329.1", fixture(MARCH_29));
        String admin = platformAdmin();

        sync(admin, "2026-03-28", "2026-03-30").andExpect(status().isOk());
        Map<Instant, Double> afterOnce = stored("2026-03-27T23:00:00Z", "2026-03-29T21:45:00Z");
        sync(admin, "2026-03-28", "2026-03-30").andExpect(status().isOk());
        Map<Instant, Double> afterTwice = stored("2026-03-27T23:00:00Z", "2026-03-29T21:45:00Z");

        assertEquals(188, afterOnce.size());
        assertEquals(afterOnce, afterTwice);
    }

    @Test
    @DisplayName("PRC-007 retrying a request that left a day unsynchronised leaves the prices one successful run stores")
    void retryingAfterAPartialRunLeavesWhatOneSuccessfulRunStores() throws Exception {
        omie.serve("20260328.1", fixture(MARCH_28));
        omie.failConnection("20260329.1");
        omie.failConnection("20260329.2");
        String admin = platformAdmin();
        sync(admin, "2026-03-28", "2026-03-30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].skipReason").value("FETCH_FAILED"));

        omie.serve("20260329.1", fixture(MARCH_29));
        sync(admin, "2026-03-28", "2026-03-30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].outcome").value("STORED"));

        Map<Instant, Double> expected = new TreeMap<>(expectedPrices(MARCH_28));
        expected.putAll(expectedPrices(MARCH_29));
        assertEquals(expected, stored("2026-03-27T23:00:00Z", "2026-03-29T21:45:00Z"));
    }

    // --- range checks ---

    @ParameterizedTest(name = "[{0}, {1}) is accepted")
    @CsvSource({
            // exactly 31 days
            "2026-03-01, 2026-04-01, 31",
            // up to tomorrow: before OMIE publishes it, tomorrow is skipped, not refused
            "2026-04-02, 2026-04-03, 1"
    })
    @DisplayName("PRC-005 a range of 31 days, or one ending the day after tomorrow, is accepted")
    void acceptsTheLongestAndTheLatestRange(String startDate, String endDate, int days) throws Exception {
        sync(platformAdmin(), startDate, endDate)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(days))
                .andExpect(jsonPath("$[-1].skipReason").value("FILE_NOT_FOUND"));
    }

    @ParameterizedTest(name = "[{0}, {1}) is answered {2}")
    @CsvSource({
            "2026-03-28, 2026-03-28, PRICE_SYNC_PERIOD_START_AFTER_END",
            "2026-03-29, 2026-03-28, PRICE_SYNC_PERIOD_START_AFTER_END",
            // 32 days
            "2026-03-01, 2026-04-02, PRICE_SYNC_PERIOD_TOO_LONG",
            // one day beyond tomorrow
            "2026-04-02, 2026-04-04, PRICE_SYNC_PERIOD_END_BEYOND_TOMORROW"
    })
    @DisplayName("PRC-005 an empty, inverted, too long or beyond-tomorrow range is answered 400 and nothing is fetched")
    void rejectsARangeThatMayNotBeSynchronised(String startDate, String endDate, String code) throws Exception {
        sync(platformAdmin(), startDate, endDate)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(code))
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertTrue(omie.requested().isEmpty());
    }

    // --- authorization ---

    @Test
    @DisplayName("PRC-005 a request without a token is answered 401 and nothing is fetched")
    void anAnonymousCallerIsUnauthorized() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body("2026-03-28", "2026-03-30")))
                .andExpect(status().isUnauthorized());

        assertTrue(omie.requested().isEmpty());
    }

    @Test
    @DisplayName("PRC-005 a member and a community admin are answered 403 and nothing is fetched")
    void aMemberAndACommunityAdminAreForbidden() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());

        sync(loginAsCommunityMember(community.getId()), "2026-03-28", "2026-03-30")
                .andExpect(status().isForbidden());
        sync(loginAsCommunityAdmin(community.getId()), "2026-03-28", "2026-03-30")
                .andExpect(status().isForbidden());

        assertTrue(omie.requested().isEmpty());
    }

    /**
     * #520 expects 403 here, but a disabled user's token is rejected when the request is authenticated, before any
     * guard is asked, so a disabled platform admin is answered like any caller without a valid token.
     */
    @Test
    @DisplayName("PRC-005 a disabled platform admin is answered 401 and nothing is fetched")
    void aDisabledPlatformAdminIsUnauthorized() throws Exception {
        User user = persistedPlatformAdmin();
        String token = loginUser(user);
        disableUserRepository.disable(UserId.of(user.getId()), clock.instant());

        sync(token, "2026-03-28", "2026-03-30")
                .andExpect(status().isUnauthorized());

        assertTrue(omie.requested().isEmpty());
    }

    // --- helpers ---

    private ResultActions sync(String token, String startDate, String endDate) throws Exception {
        return mockMvc.perform(post(URL)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(body(startDate, endDate)));
    }

    private static String body(String startDate, String endDate) {
        return String.format("{\"startDate\": \"%s\", \"endDate\": \"%s\"}", startDate, endDate);
    }

    private String platformAdmin() throws Exception {
        return loginUser(persistedPlatformAdmin());
    }

    private User persistedPlatformAdmin() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        managePlatformAdminRepository.grant(UserId.of(user.getId()));
        return user;
    }

    private static String fixture(String name) throws IOException {
        return Files.readString(FIXTURES.resolve(name));
    }

    private static String redated(String name, String fromDayPrefix, String toDayPrefix) throws IOException {
        return fixture(name).replace(fromDayPrefix, toDayPrefix);
    }

    /**
     * The prices a day's file must be stored as, by PRC-001 and PRC-004: period n at the start of the market day in
     * Europe/Madrid plus n - 1 quarters, valued at the Spanish price (6th column) in EUR/kWh, truncated to 6 decimals.
     */
    private static Map<Instant, Double> expectedPrices(String name) throws IOException {
        Map<Instant, Double> prices = new TreeMap<>();
        for (String line : fixture(name).split("\r?\n")) {
            String[] fields = line.split(";");
            if (fields.length < 6) {
                continue;
            }
            LocalDate day = LocalDate.of(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
                    Integer.parseInt(fields[2]));
            Instant instant = day.atStartOfDay(OmieConfig.OMIE_ZONE).toInstant()
                    .plus(Duration.ofMinutes(15L * (Integer.parseInt(fields[3]) - 1)));
            prices.put(instant, Math.floor(Double.parseDouble(fields[5]) / 1000 * 1e6) / 1e6);
        }
        return prices;
    }

    /**
     * The stored prices from {@code from} to {@code to}, both included, by instant.
     */
    private Map<Instant, Double> stored(String from, String to) {
        return storedBetween(from, to).stream()
                .collect(Collectors.toMap(price -> price.getHour().toInstant(), PriceByHour::getPrice,
                        (first, second) -> {
                            throw new IllegalStateException("two prices stored at the same instant");
                        }, TreeMap::new));
    }

    private List<PriceByHour> storedBetween(String from, String to) {
        return getPriceRepository.getPricesByRangeOfDates(OffsetDateTime.parse(from), OffsetDateTime.parse(to));
    }

    private static Instant firstKey(Map<Instant, Double> prices) {
        return ((TreeMap<Instant, Double>) prices).firstKey();
    }

    private static Instant lastKey(Map<Instant, Double> prices) {
        return ((TreeMap<Instant, Double>) prices).lastKey();
    }

    /**
     * Every date these tests store prices for falls in March 2026, which no other test stores prices for.
     */
    private void clearStoredPrices() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            connection.query(new Query(String.format(
                    "DELETE FROM %s WHERE time >= '2026-03-01T00:00:00Z' AND time < '2026-04-10T00:00:00Z'",
                    OmieConfig.PRICES_KWH_MEASUREMENT)));
        }
    }

    /**
     * Stands in for omie.es: answers each file by name without any network, with a body, a status code or a
     * connection failure, answers 404 for any other file, and records every file asked for.
     */
    private static final class OmieStub {

        private final Map<String, Function<okhttp3.Request, Response>> answers = new HashMap<>();
        private final List<String> requested = Collections.synchronizedList(new ArrayList<>());

        void serve(String file, String body) {
            answers.put(file, request -> response(request, 200,
                    ResponseBody.create(body, MediaType.get("application/octet-stream"))));
        }

        void answer(String file, int code) {
            answers.put(file, request -> response(request, code,
                    ResponseBody.create("", MediaType.get("text/html"))));
        }

        void failConnection(String file) {
            answers.put(file, null);
        }

        List<String> requested() {
            return List.copyOf(requested);
        }

        OkHttpClient client() {
            return new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        okhttp3.Request request = chain.request();
                        String filename = request.url().queryParameter("filename");
                        String file = filename == null ? "" : filename.replace("marginalpdbc_", "");
                        requested.add(file);
                        if (answers.containsKey(file) && answers.get(file) == null) {
                            throw new IOException("connection reset");
                        }
                        return answers.getOrDefault(file, r -> response(r, 404,
                                ResponseBody.create("", MediaType.get("text/html")))).apply(request);
                    })
                    .build();
        }

        private static Response response(okhttp3.Request request, int code, ResponseBody body) {
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(code == 200 ? "OK" : "Error")
                    .body(body)
                    .build();
        }
    }
}
