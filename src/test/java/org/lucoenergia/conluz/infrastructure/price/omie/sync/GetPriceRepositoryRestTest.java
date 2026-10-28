package org.lucoenergia.conluz.infrastructure.price.omie.sync;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.sync.OmieDayPrices;
import org.lucoenergia.conluz.infrastructure.price.get.GetPriceRepositoryRest;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class GetPriceRepositoryRestTest extends BaseIntegrationTest {

    public static final String OMIE_HOUR_PRICES = "src/test/resources/fixtures/price/omie_hour_prices.txt";
    public static final String OMIE_QUARTER_PRICES = "src/test/resources/fixtures/price/omie_quarter_prices.txt";
    public static final String OMIE_QUARTER_PRICES_DST = "src/test/resources/fixtures/price/omie_quarter_prices_DST.txt";
    public static final String OMIE_QUARTER_PRICES_DST_FALL = "src/test/resources/fixtures/price/omie_quarter_prices_DST_fall.txt";
    public static final String OMIE_HOUR_PRICES_DST_FALL = "src/test/resources/fixtures/price/omie_hour_prices_DST_fall.txt";
    public static final String OMIE_QUARTER_PRICES_WRONG_COUNT = "src/test/resources/fixtures/price/omie_quarter_prices_wrong_count.txt";
    public static final String OMIE_HOUR_PRICES_SPLIT_MARKET = "src/test/resources/fixtures/price/omie_hour_prices_split_market.txt";
    public static final String OMIE_QUARTER_PRICES_NON_NUMERIC_PRICE = "src/test/resources/fixtures/price/omie_quarter_prices_non_numeric_price.txt";
    public static final String OMIE_QUARTER_PRICES_MISSING_COLUMN = "src/test/resources/fixtures/price/omie_quarter_prices_missing_column.txt";

    private final ConluzRestClientBuilder conluzRestClientBuilder = Mockito.mock(ConluzRestClientBuilder.class);

    private final GetPriceRepositoryRest repository = new GetPriceRepositoryRest(conluzRestClientBuilder);

    private final OkHttpClient okHttpClient = Mockito.mock(OkHttpClient.class);
    private final Call call = Mockito.mock(Call.class);
    private final Response response = Mockito.mock(Response.class);
    private final ResponseBody responseBody = Mockito.mock(ResponseBody.class);

    @Test
    @DisplayName("PRC-001 a 24-hour day of hourly prices has 24 hours, each placed at its own instant")
    void syncDailyPrices_successfulResponseWithNumberOneAndHourlyPrices() throws IOException {

        // Assemble
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        OffsetDateTime startDate = LocalDate.parse("20230528", formatter).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endDate = LocalDate.parse("20230602", formatter).atStartOfDay().atOffset(ZoneOffset.UTC);

        String bodyString = new String(Files.readAllBytes(Paths.get(OMIE_HOUR_PRICES)));

        when(conluzRestClientBuilder.build(eq(false), ArgumentMatchers.any(Duration.class))).thenReturn(okHttpClient);
        when(okHttpClient.newCall(argThat(argument ->
                argument != null &&
                        argument.url() != null &&
                        argument.url().toString()
                                .startsWith("https://www.omie.es/es/file-download?parents[0]=marginalpdbc&filename=marginalpdbc_") &&
                        (
                                argument.url().toString().contains("20230528.1") ||
                                argument.url().toString().contains("20230529.1") ||
                                argument.url().toString().contains("20230530.1") ||
                                argument.url().toString().contains("20230531.1") ||
                                argument.url().toString().contains("20230601.1") ||
                                argument.url().toString().contains("20230602.1")
                        )
        ))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn(bodyString);

        // Act
        List<PriceByHour> prices = pricesOf(repository.getPricesByDays(startDate.toLocalDate(), endDate.toLocalDate().plusDays(1)));

        // Assert
        assertEquals(144, prices.size());

        assertEquals(0.105420, prices.get(0).getPrice());
        assertEquals(0.100000, prices.get(1).getPrice());
        assertEquals(0.099370, prices.get(2).getPrice());
        assertEquals(0.100000, prices.get(3).getPrice());
        assertEquals(0.100000, prices.get(4).getPrice());
        assertEquals(0.100000, prices.get(5).getPrice());
        assertEquals(0.097849, prices.get(6).getPrice());
        assertEquals(0.090420, prices.get(7).getPrice());
        assertEquals(0.083060, prices.get(8).getPrice());
        assertEquals(0.078870, prices.get(9).getPrice());
        assertEquals(0.078970, prices.get(10).getPrice());
        assertEquals(0.078290, prices.get(11).getPrice());
        assertEquals(0.070770, prices.get(12).getPrice());
        assertEquals(0.070470, prices.get(13).getPrice());
        assertEquals(0.069570, prices.get(14).getPrice());
        assertEquals(0.054170, prices.get(15).getPrice());
        assertEquals(0.050000, prices.get(16).getPrice());
        assertEquals(0.051000, prices.get(17).getPrice());
        assertEquals(0.055000, prices.get(18).getPrice());
        assertEquals(0.073280, prices.get(19).getPrice());
        assertEquals(0.100010, prices.get(20).getPrice());
        assertEquals(0.109080, prices.get(21).getPrice());
        assertEquals(0.115380, prices.get(22).getPrice());
        assertEquals(0.107640, prices.get(23).getPrice());

        assertEquals("2023:05:28T00:00", getDate(prices.get(0).getHour()));
        assertEquals("2023:05:28T01:00", getDate(prices.get(1).getHour()));
        assertEquals("2023:05:28T02:00", getDate(prices.get(2).getHour()));
        assertEquals("2023:05:28T03:00", getDate(prices.get(3).getHour()));
        assertEquals("2023:05:28T04:00", getDate(prices.get(4).getHour()));
        assertEquals("2023:05:28T05:00", getDate(prices.get(5).getHour()));
        assertEquals("2023:05:28T06:00", getDate(prices.get(6).getHour()));
        assertEquals("2023:05:28T07:00", getDate(prices.get(7).getHour()));
        assertEquals("2023:05:28T08:00", getDate(prices.get(8).getHour()));
        assertEquals("2023:05:28T09:00", getDate(prices.get(9).getHour()));
        assertEquals("2023:05:28T10:00", getDate(prices.get(10).getHour()));
        assertEquals("2023:05:28T11:00", getDate(prices.get(11).getHour()));
        assertEquals("2023:05:28T12:00", getDate(prices.get(12).getHour()));
        assertEquals("2023:05:28T13:00", getDate(prices.get(13).getHour()));
        assertEquals("2023:05:28T14:00", getDate(prices.get(14).getHour()));
        assertEquals("2023:05:28T15:00", getDate(prices.get(15).getHour()));
        assertEquals("2023:05:28T16:00", getDate(prices.get(16).getHour()));
        assertEquals("2023:05:28T17:00", getDate(prices.get(17).getHour()));
        assertEquals("2023:05:28T18:00", getDate(prices.get(18).getHour()));
        assertEquals("2023:05:28T19:00", getDate(prices.get(19).getHour()));
        assertEquals("2023:05:28T20:00", getDate(prices.get(20).getHour()));
        assertEquals("2023:05:28T21:00", getDate(prices.get(21).getHour()));
        assertEquals("2023:05:28T22:00", getDate(prices.get(22).getHour()));
        assertEquals("2023:05:28T23:00", getDate(prices.get(23).getHour()));
    }

    @Test
    @DisplayName("PRC-001 a 24-hour day has 96 quarters, each placed at its own instant")
    void syncDailyPrices_successfulResponseWithPricesEvery15Minutes() throws IOException {

        // Assemble
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        OffsetDateTime startDate = LocalDate.parse("20251212", formatter).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endDate = LocalDate.parse("20251212", formatter).atStartOfDay().atOffset(ZoneOffset.UTC);

        String bodyString = new String(Files.readAllBytes(Paths.get(OMIE_QUARTER_PRICES)));

        when(conluzRestClientBuilder.build(eq(false), ArgumentMatchers.any(Duration.class))).thenReturn(okHttpClient);
        when(okHttpClient.newCall(argThat(argument ->
                argument != null &&
                        argument.url() != null &&
                        argument.url().toString()
                                .startsWith("https://www.omie.es/es/file-download?parents[0]=marginalpdbc&filename=marginalpdbc_") &&
                        (
                                argument.url().toString().contains("20251212.1")
                        )
        ))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn(bodyString);

        // Act
        List<PriceByHour> prices = pricesOf(repository.getPricesByDays(startDate.toLocalDate(), endDate.toLocalDate().plusDays(1)));

        // Assert
        assertEquals(96, prices.size());

        assertEquals(0.09901, prices.get(0).getPrice());
        assertEquals(0.0775, prices.get(95).getPrice());

        assertEquals("2025:12:12T00:00", getDate(prices.get(0).getHour()));
        assertEquals("2025:12:12T00:15", getDate(prices.get(1).getHour()));
        assertEquals("2025:12:12T00:30", getDate(prices.get(2).getHour()));
        assertEquals("2025:12:12T00:45", getDate(prices.get(3).getHour()));

        assertEquals("2025:12:12T23:45", getDate(prices.get(95).getHour()));
    }

    @Test
    @DisplayName("PRC-001 a 23-hour day has 92 quarters, each placed at its own instant")
    void syncDailyPrices_successfulResponseWithPricesEvery15MinutesWhenClocksGoForward() throws IOException {

        // Assemble
        stubOmieFiles(Map.of("20260329.1", OMIE_QUARTER_PRICES_DST));

        // Act
        List<PriceByHour> prices = pricesOf(repository.getPricesByDays(day("20260329"), day("20260329").plusDays(1)));

        // Assert
        assertEquals(92, prices.size());
        assertEquals(92, distinctInstants(prices));

        assertEquals(0.00541, prices.get(0).getPrice());
        assertEquals(0.00600, prices.get(91).getPrice());

        assertEquals(Instant.parse("2026-03-28T23:00:00Z"), prices.get(0).getHour().toInstant());
        // Period 9 is the first quarter after the clocks go forward from 02:00 to 03:00
        assertEquals(Instant.parse("2026-03-29T01:00:00Z"), prices.get(8).getHour().toInstant());
        assertEquals("2026:03:29T03:00", getDate(prices.get(8).getHour()));
        assertEquals(Instant.parse("2026-03-29T02:00:00Z"), prices.get(12).getHour().toInstant());
        assertEquals(Instant.parse("2026-03-29T21:45:00Z"), prices.get(91).getHour().toInstant());
        assertEquals("2026:03:29T23:45", getDate(prices.get(91).getHour()));
    }

    @Test
    @DisplayName("PRC-001 a 25-hour day has 100 quarters, each placed at its own instant")
    void syncDailyPrices_successfulResponseWithPricesEvery15MinutesWhenClocksGoBack() throws IOException {

        // Assemble
        stubOmieFiles(Map.of("20261025.1", OMIE_QUARTER_PRICES_DST_FALL));

        // Act
        List<PriceByHour> prices = pricesOf(repository.getPricesByDays(day("20261025"), day("20261025").plusDays(1)));

        // Assert
        assertEquals(100, prices.size());
        assertEquals(100, distinctInstants(prices));

        assertEquals(0.2158, prices.get(0).getPrice());
        assertEquals(0.15494, prices.get(8).getPrice());
        assertEquals(0.14223, prices.get(12).getPrice());
        assertEquals(0.225, prices.get(99).getPrice());

        assertEquals(Instant.parse("2026-10-24T22:00:00Z"), prices.get(0).getHour().toInstant());
        // Periods 9 and 13 both start at 02:00 local time: before and after the clocks go back
        assertEquals(OffsetDateTime.parse("2026-10-25T02:00:00+02:00"), prices.get(8).getHour());
        assertEquals(OffsetDateTime.parse("2026-10-25T02:00:00+01:00"), prices.get(12).getHour());
        assertEquals(Instant.parse("2026-10-25T22:45:00Z"), prices.get(99).getHour().toInstant());
        assertEquals("2026:10:25T23:45", getDate(prices.get(99).getHour()));
    }

    @Test
    @DisplayName("PRC-001 a 25-hour day of hourly prices has 25 hours, each placed at its own instant")
    void syncDailyPrices_successfulResponseWithHourlyPricesWhenClocksGoBack() throws IOException {

        // Assemble
        stubOmieFiles(Map.of("20231029.1", OMIE_HOUR_PRICES_DST_FALL));

        // Act
        List<PriceByHour> prices = pricesOf(repository.getPricesByDays(day("20231029"), day("20231029").plusDays(1)));

        // Assert
        assertEquals(25, prices.size());
        assertEquals(25, distinctInstants(prices));

        assertEquals(0.101, prices.get(0).getPrice());
        assertEquals(0.125, prices.get(24).getPrice());

        assertEquals(Instant.parse("2023-10-28T22:00:00Z"), prices.get(0).getHour().toInstant());
        assertEquals(OffsetDateTime.parse("2023-10-29T02:00:00+02:00"), prices.get(2).getHour());
        assertEquals(OffsetDateTime.parse("2023-10-29T02:00:00+01:00"), prices.get(3).getHour());
        assertEquals(Instant.parse("2023-10-29T22:00:00Z"), prices.get(24).getHour().toInstant());
    }

    @Test
    @DisplayName("PRC-002 a file whose periods do not fill the day is rejected")
    void syncDailyPrices_rejectsFileWhosePeriodsDoNotFillTheDay() throws IOException {

        // Assemble
        stubOmieFiles(Map.of("20251213.1", OMIE_QUARTER_PRICES_WRONG_COUNT));

        // Act
        List<PriceByHour> prices = pricesOf(repository.getPricesByDays(day("20251213"), day("20251213").plusDays(1)));

        // Assert
        assertTrue(prices.isEmpty());
    }

    @Test
    @DisplayName("PRC-002 a file whose periods are not consecutive from 1 is rejected")
    void syncDailyPrices_rejectsFileWithPeriodsOutOfSequence() throws IOException {

        // Assemble
        String body = Files.readString(Paths.get(OMIE_QUARTER_PRICES))
                .replace("2025;12;12;2;", "2025;12;12;3;");
        stubOmieBodies(Map.of("20251212.1", body));

        // Act
        List<PriceByHour> prices = pricesOf(repository.getPricesByDays(day("20251212"), day("20251212").plusDays(1)));

        // Assert
        assertTrue(prices.isEmpty());
    }

    @Test
    @DisplayName("PRC-003 a day whose file is rejected does not prevent the other days from being returned")
    void syncDailyPrices_returnsOtherDaysWhenOneDayIsRejected() throws IOException {

        // Assemble
        String validNextDay = Files.readString(Paths.get(OMIE_QUARTER_PRICES)).replace("2025;12;12;", "2025;12;14;");
        stubOmieBodies(Map.of(
                "20251213.1", Files.readString(Paths.get(OMIE_QUARTER_PRICES_WRONG_COUNT)),
                "20251214.1", validNextDay));

        // Act
        List<PriceByHour> prices = pricesOf(repository.getPricesByDays(day("20251213"), day("20251214").plusDays(1)));

        // Assert
        assertEquals(96, prices.size());
        assertEquals(Instant.parse("2025-12-13T23:00:00Z"), prices.get(0).getHour().toInstant());
        assertEquals(Instant.parse("2025-12-14T22:45:00Z"), prices.get(95).getHour().toInstant());
    }

    @Test
    @DisplayName("PRC-004 the stored price is the Spanish marginal price, not the Portuguese one")
    void syncDailyPrices_storesSpanishPriceWhenIberianMarketSplits() throws IOException {

        // Assemble
        // Each row is year;month;day;period;Portuguese price;Spanish price, in EUR/MWh. On this day
        // periods 7, 8 and 9 have Portuguese prices 0, 0 and 0.04, and Spanish prices 0.4, 3.25 and 2.97.
        stubOmieFiles(Map.of("20240405.1", OMIE_HOUR_PRICES_SPLIT_MARKET));

        // Act
        List<PriceByHour> prices = pricesOf(repository.getPricesByDays(day("20240405"), day("20240405").plusDays(1)));

        // Assert
        assertEquals(24, prices.size());

        assertEquals(0.0004, prices.get(6).getPrice());
        assertEquals(0.00325, prices.get(7).getPrice());
        assertEquals(0.00297, prices.get(8).getPrice());

        assertNotEquals(0.0, prices.get(6).getPrice());
        assertNotEquals(0.0, prices.get(7).getPrice());
        assertNotEquals(0.00004, prices.get(8).getPrice());
    }

    @Test
    @DisplayName("PRC-003 each day of a range is reported, in date order, with why it was not served")
    void getPricesByDays_reportsEachDayInOrderWithItsStatus() throws IOException {

        // Assemble
        String servedDay = Files.readString(Paths.get(OMIE_QUARTER_PRICES)).replace("2025;12;12;", "2025;12;14;");
        stubOmieResponses(Map.of(
                "20251213.1", OmieStub.status(404),
                "20251213.2", OmieStub.status(404),
                "20251214.1", OmieStub.body(servedDay),
                "20251215.1", OmieStub.body(Files.readString(Paths.get(OMIE_QUARTER_PRICES_WRONG_COUNT))
                        .replace("2025;12;13;", "2025;12;15;")),
                "20251216.1", OmieStub.status(503),
                "20251216.2", OmieStub.status(404),
                "20251217.1", OmieStub.connectionFailure(),
                "20251217.2", OmieStub.connectionFailure()));

        // Act
        List<OmieDayPrices> days = repository.getPricesByDays(day("20251213"), day("20251218"));

        // Assert
        assertEquals(List.of(day("20251213"), day("20251214"), day("20251215"), day("20251216"), day("20251217")),
                days.stream().map(OmieDayPrices::day).toList());
        assertEquals(List.of(OmieDayPrices.Status.NOT_FOUND, OmieDayPrices.Status.SERVED,
                        OmieDayPrices.Status.REJECTED, OmieDayPrices.Status.FETCH_FAILED,
                        OmieDayPrices.Status.FETCH_FAILED),
                days.stream().map(OmieDayPrices::status).toList());
        assertEquals(List.of(0, 96, 0, 0, 0), days.stream().map(day -> day.prices().size()).toList());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {OMIE_QUARTER_PRICES_NON_NUMERIC_PRICE, OMIE_QUARTER_PRICES_MISSING_COLUMN})
    @DisplayName("PRC-003 a day whose file has a malformed row is rejected, and the other days are still served")
    void getPricesByDays_rejectsADayWithAMalformedRowAndServesTheOthers(String malformedFixture) throws IOException {

        // Assemble
        String validDay = Files.readString(Paths.get(OMIE_QUARTER_PRICES));
        stubOmieBodies(Map.of(
                "20251211.1", validDay.replace("2025;12;12;", "2025;12;11;"),
                "20251212.1", Files.readString(Paths.get(malformedFixture)),
                "20251213.1", validDay.replace("2025;12;12;", "2025;12;13;")));

        // Act
        List<OmieDayPrices> days = repository.getPricesByDays(day("20251211"), day("20251214"));

        // Assert
        assertEquals(List.of(OmieDayPrices.Status.SERVED, OmieDayPrices.Status.REJECTED,
                        OmieDayPrices.Status.SERVED),
                days.stream().map(OmieDayPrices::status).toList());
        assertEquals(List.of(96, 0, 96), days.stream().map(day -> day.prices().size()).toList());
    }

    @Test
    void getPricesByDays_triesTheSecondFileWhenTheFirstIsMissing() throws IOException {

        // Assemble
        stubOmieResponses(Map.of(
                "20251212.1", OmieStub.status(404),
                "20251212.2", OmieStub.body(Files.readString(Paths.get(OMIE_QUARTER_PRICES)))));

        // Act
        List<OmieDayPrices> days = repository.getPricesByDays(day("20251212"), day("20251213"));

        // Assert
        assertEquals(1, days.size());
        assertEquals(OmieDayPrices.Status.SERVED, days.get(0).status());
        assertEquals(96, days.get(0).prices().size());
    }

    @Test
    void getPricesByDays_reportsAnEmptyFileAsNotFound() {

        // Assemble
        stubOmieResponses(Map.of("20251212.1", OmieStub.body(""), "20251212.2", OmieStub.body("")));

        // Act
        List<OmieDayPrices> days = repository.getPricesByDays(day("20251212"), day("20251213"));

        // Assert
        assertEquals(OmieDayPrices.Status.NOT_FOUND, days.get(0).status());
        assertTrue(days.get(0).prices().isEmpty());
    }

    @Test
    void getPricesByDays_returnsNothingForAnEmptyRange() {

        // Act
        List<OmieDayPrices> days = repository.getPricesByDays(day("20251212"), day("20251212"));

        // Assert
        assertTrue(days.isEmpty());
    }

    /**
     * What the stubbed OMIE answers for one file: a status code, a successful body, or a connection failure.
     */
    private record OmieStub(int code, String body, boolean ioFailure) {

        static OmieStub status(int code) {
            return new OmieStub(code, "", false);
        }

        static OmieStub body(String body) {
            return new OmieStub(200, body, false);
        }

        static OmieStub connectionFailure() {
            return new OmieStub(0, "", true);
        }
    }

    /**
     * Answers each OMIE file name (e.g. "20261025.1") with its stub, and any other file with a 404.
     */
    private void stubOmieResponses(Map<String, OmieStub> stubsByFile) {
        when(conluzRestClientBuilder.build(eq(false), ArgumentMatchers.any(Duration.class))).thenReturn(okHttpClient);
        when(okHttpClient.newCall(ArgumentMatchers.any(Request.class))).thenAnswer(invocation -> {
            String url = invocation.getArgument(0, Request.class).url().toString();
            OmieStub stub = stubsByFile.entrySet().stream()
                    .filter(entry -> url.endsWith("marginalpdbc_" + entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(OmieStub.status(404));
            Call dayCall = Mockito.mock(Call.class);
            if (stub.ioFailure()) {
                when(dayCall.execute()).thenThrow(new IOException("connection reset"));
                return dayCall;
            }
            Response dayResponse = Mockito.mock(Response.class);
            ResponseBody dayResponseBody = Mockito.mock(ResponseBody.class);
            when(dayCall.execute()).thenReturn(dayResponse);
            when(dayResponse.isSuccessful()).thenReturn(stub.code() >= 200 && stub.code() < 300);
            when(dayResponse.code()).thenReturn(stub.code());
            when(dayResponse.body()).thenReturn(dayResponseBody);
            when(dayResponseBody.string()).thenReturn(stub.body());
            return dayCall;
        });
    }

    private void stubOmieFiles(Map<String, String> fixturesByFile) throws IOException {
        Map<String, String> bodiesByFile = new HashMap<>();
        for (Map.Entry<String, String> entry : fixturesByFile.entrySet()) {
            bodiesByFile.put(entry.getKey(), Files.readString(Paths.get(entry.getValue())));
        }
        stubOmieBodies(bodiesByFile);
    }

    /**
     * Answers each OMIE file name (e.g. "20261025.1") with its body, and any other file with an empty body.
     */
    private void stubOmieBodies(Map<String, String> bodiesByFile) {
        when(conluzRestClientBuilder.build(eq(false), ArgumentMatchers.any(Duration.class))).thenReturn(okHttpClient);
        when(okHttpClient.newCall(ArgumentMatchers.any(Request.class))).thenAnswer(invocation -> {
            String url = invocation.getArgument(0, Request.class).url().toString();
            String body = bodiesByFile.entrySet().stream()
                    .filter(entry -> url.endsWith("marginalpdbc_" + entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse("");
            Call dayCall = Mockito.mock(Call.class);
            Response dayResponse = Mockito.mock(Response.class);
            ResponseBody dayResponseBody = Mockito.mock(ResponseBody.class);
            when(dayCall.execute()).thenReturn(dayResponse);
            when(dayResponse.isSuccessful()).thenReturn(true);
            when(dayResponse.body()).thenReturn(dayResponseBody);
            when(dayResponseBody.string()).thenReturn(body);
            return dayCall;
        });
    }

    private static LocalDate day(String yyyyMMdd) {
        return LocalDate.parse(yyyyMMdd, DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private static List<PriceByHour> pricesOf(List<OmieDayPrices> days) {
        return days.stream().flatMap(day -> day.prices().stream()).toList();
    }

    private static long distinctInstants(List<PriceByHour> prices) {
        return prices.stream().map(price -> price.getHour().toInstant()).distinct().count();
    }

    private String getDate(OffsetDateTime date) {
        return date.format(DateTimeFormatter.ofPattern("yyyy:MM:dd'T'HH:mm"));
    }
}
