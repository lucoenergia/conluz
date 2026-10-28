package org.lucoenergia.conluz.infrastructure.price.omie.sync;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.infrastructure.price.get.GetPriceRepositoryRest;
import org.lucoenergia.conluz.infrastructure.price.get.OmiePriceFileException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        List<PriceByHour> prices = repository.getPricesByRangeOfDates(startDate, endDate);

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
        List<PriceByHour> prices = repository.getPricesByRangeOfDates(startDate, endDate);

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
        List<PriceByHour> prices = repository.getPricesByRangeOfDates(day("20260329"), day("20260329"));

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
        List<PriceByHour> prices = repository.getPricesByRangeOfDates(day("20261025"), day("20261025"));

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
        List<PriceByHour> prices = repository.getPricesByRangeOfDates(day("20231029"), day("20231029"));

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

        // Act & assert
        assertThrows(OmiePriceFileException.class,
                () -> repository.getPricesByRangeOfDates(day("20251213"), day("20251213")));
    }

    @Test
    @DisplayName("PRC-002 a file whose periods are not consecutive from 1 is rejected")
    void syncDailyPrices_rejectsFileWithPeriodsOutOfSequence() throws IOException {

        // Assemble
        String body = Files.readString(Paths.get(OMIE_QUARTER_PRICES))
                .replace("2025;12;12;2;", "2025;12;12;3;");
        stubOmieBodies(Map.of("20251212.1", body));

        // Act & assert
        assertThrows(OmiePriceFileException.class,
                () -> repository.getPricesByRangeOfDates(day("20251212"), day("20251212")));
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

    private static OffsetDateTime day(String yyyyMMdd) {
        return LocalDate.parse(yyyyMMdd, DateTimeFormatter.ofPattern("yyyyMMdd")).atStartOfDay()
                .atOffset(ZoneOffset.UTC);
    }

    private static long distinctInstants(List<PriceByHour> prices) {
        return prices.stream().map(price -> price.getHour().toInstant()).distinct().count();
    }

    private String getDate(OffsetDateTime date) {
        return date.format(DateTimeFormatter.ofPattern("yyyy:MM:dd'T'HH:mm"));
    }
}
