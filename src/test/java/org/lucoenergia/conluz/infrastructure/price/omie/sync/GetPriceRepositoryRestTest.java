package org.lucoenergia.conluz.infrastructure.price.omie.sync;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.infrastructure.price.get.GetPriceRepositoryRest;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class GetPriceRepositoryRestTest extends BaseIntegrationTest {

    public static final String OMIE_PRICES = "src/test/resources/fixtures/price/omie_prices.txt";

    private GetPriceRepositoryRest repository;
    @Autowired
    private TimeConfiguration timeConfiguration;

    private final ConluzRestClientBuilder conluzRestClientBuilder = Mockito.mock(ConluzRestClientBuilder.class);
    private final OkHttpClient okHttpClient = Mockito.mock(OkHttpClient.class);
    private final Call call = Mockito.mock(Call.class);
    private final Response response = Mockito.mock(Response.class);
    private final ResponseBody responseBody = Mockito.mock(ResponseBody.class);

    @BeforeEach
    void setup() {
        repository = new GetPriceRepositoryRest(conluzRestClientBuilder, timeConfiguration);
    }

    @Test
    void syncDailyPrices_successfulResponseWithNumberOne() throws IOException {

        // Assemble
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate date = LocalDate.parse("20230528", formatter);
        OffsetDateTime dateTime = date.atStartOfDay().atOffset(ZoneOffset.UTC);

        String bodyString = new String(Files.readAllBytes(Paths.get(OMIE_PRICES)));

        when(conluzRestClientBuilder.build(eq(false), ArgumentMatchers.any(Duration.class))).thenReturn(okHttpClient);
        when(okHttpClient.newCall(argThat(argument ->
                argument != null &&
                argument.url() != null &&
                argument.url().toString()
                        .equals("https://www.omie.es/es/file-download?parents[0]=marginalpdbc&filename=marginalpdbc_20230528.1")))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn(bodyString);

        // Act
        List<PriceByHour> prices = repository.getPricesByDay(dateTime);

        // Assert
        assertEquals(24, prices.size());

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


    private String getDate(OffsetDateTime date) {
        return date.format(DateTimeFormatter.ofPattern("yyyy:MM:dd'T'HH:mm"));
    }

}
