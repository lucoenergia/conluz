package org.lucoenergia.conluz.infrastructure.production.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.huawei.HourlyProduction;
import org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiAuthorizer;
import org.lucoenergia.conluz.infrastructure.production.huawei.get.GetHuaweiHourlyProductionRepositoryRest;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class GetHuaweiHourlyProductionRepositoryRestTest {

    private final HuaweiAuthorizer huaweiAuthorizer = Mockito.mock(HuaweiAuthorizer.class);

    private final ConluzRestClientBuilder conluzRestClientBuilder = Mockito.mock(ConluzRestClientBuilder.class);

    private final ZoneId zoneId = ZoneId.of("Europe/Madrid");

    private GetHuaweiHourlyProductionRepositoryRest repositoryRest;

    private static final List<Plant> STATION_CODES = Arrays.asList(
            new Plant.Builder().withCode("BA4372D08E014822AB065017416F254C").build(),
            new Plant.Builder().withCode("5D02E8B40AD342159AC8D8A2BCD4FAB5").build()
    );

    private static final String RESPONSE_BODY_SUCCESSFULL = """
                {
                   "success":true,
                   "data":[
                      {
                          "dataItemMap":{
                             "radiation_intensity":null,
                             "theory_power":null,
                             "inverter_power":0,
                             "ongrid_power":null,
                             "power_profit":0
                          },
                          "stationCode":"5D02E8B40AD342159AC8D8A2BCD4FAB5",
                          "collectTime":1501862400000
                       },
                       {
                          "dataItemMap":{
                             "radiation_intensity":null,
                             "theory_power":null,
                             "inverter_power":10,
                             "ongrid_power":null,
                             "power_profit":5
                          },
                          "stationCode":"5D02E8B40AD342159AC8D8A2BCD4FAB5",
                          "collectTime":1501866000000
                       },
                       {
                          "dataItemMap":{
                             "radiation_intensity":500,
                             "theory_power":12.5,
                             "inverter_power":10,
                             "ongrid_power":20,
                             "power_profit":5
                          },
                          "stationCode":"BA4372D08E014822AB065017416F254C",
                          "collectTime":1501873200000
                       }
                   ],
                   "failCode":0,
                   "params":{
                      "stationCodes":"BA4372D08E014822AB065017416F254C,5D02E8B40AD342159AC8D8A2BCD4FAB5",
                      "currentTime":1750627686362
                   },
                   "message":null
                }
                """;

    @BeforeEach
    void setUp() {
        TimeConfiguration timeConfiguration = Mockito.mock(TimeConfiguration.class);
        when(timeConfiguration.getZoneId()).thenReturn(zoneId);
        when(timeConfiguration.now()).thenReturn(OffsetDateTime.now());
        DateConverter dateConverter = new DateConverter(timeConfiguration);

        repositoryRest = new GetHuaweiHourlyProductionRepositoryRest(new ObjectMapper(), huaweiAuthorizer,
                conluzRestClientBuilder, dateConverter);
    }
    
    
    
    @Test
    void getHourlyProduction_ByDateInterval_shouldCallServiceSevenTimesWhenDateRangeIsOneWeek() throws IOException {
        // Given
        when(huaweiAuthorizer.getAuthToken()).thenReturn("TOKEN");

        OkHttpClient client = Mockito.mock(OkHttpClient.class);
        when(conluzRestClientBuilder.build()).thenReturn(client);

        Call call = Mockito.mock(Call.class);
        when(client.newCall(any())).thenReturn(call);

        Response response = Mockito.mock(Response.class);
        when(call.execute()).thenReturn(response);

        ResponseBody responseBody = Mockito.mock(ResponseBody.class);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn(RESPONSE_BODY_SUCCESSFULL);

        // Calculate date interval to get data
        OffsetDateTime endDate = OffsetDateTime.now();
        OffsetDateTime startDate = endDate.minusWeeks(1);

        // When
        repositoryRest.getHourlyProductionByDateInterval(STATION_CODES, startDate, endDate);

        // Then
        Mockito.verify(client, Mockito.times(8)).newCall(any());
    }

    @Test
    void getHourlyProduction_ByDateInterval_shouldReturnEmptyListWhenStartDateIsAfterEndDate() throws IOException {
        // Given
        prepareSuccessfulResponse(RESPONSE_BODY_SUCCESSFULL);

        // Calculate date interval to get data
        OffsetDateTime today = OffsetDateTime.now();
        OffsetDateTime todayPlusOneWeek = today.plusWeeks(1);

        // When
        List<HourlyProduction> realTimeProductions = repositoryRest.getHourlyProductionByDateInterval(STATION_CODES,
                todayPlusOneWeek, today);

        // Then
        assertTrue(realTimeProductions.isEmpty());
    }

    @Test
    void getHourlyProduction_ByDateInterval_shouldReturnEmptyListWhenStationCodesIsEmpty() {
        // Given
        List<Plant> stationCodes = List.of();

        // Calculate date interval to get data
        OffsetDateTime today = OffsetDateTime.now();
        OffsetDateTime todayMinusOneWeek = today.minusWeeks(1);

        // When
        List<HourlyProduction> realTimeProductions = repositoryRest.getHourlyProductionByDateInterval(stationCodes,
                today, todayMinusOneWeek);

        // Then
        assertTrue(realTimeProductions.isEmpty());
    }

    @Test
    void getHourlyProduction_shouldReturnProductionByDateIntervalWhenStationCodesIsNotEmpty() throws IOException {
        // Given
        prepareSuccessfulResponse(RESPONSE_BODY_SUCCESSFULL);

        // Calculate date interval to get data
        OffsetDateTime today = OffsetDateTime.now();

        // When
        List<HourlyProduction> realTimeProductions = repositoryRest.getHourlyProductionByDateInterval(STATION_CODES,
                today, today);

        // Then
        assertEquals(3, realTimeProductions.size());

        assertEquals("5D02E8B40AD342159AC8D8A2BCD4FAB5", realTimeProductions.get(0).getStationCode());
        assertEquals(Instant.ofEpochMilli(1501862400000L), realTimeProductions.get(0).getTime());
        assertEquals(0, realTimeProductions.get(0).getInverterPower());
        assertEquals(0.0, realTimeProductions.get(0).getOngridPower());
        assertEquals(0, realTimeProductions.get(0).getPowerProfit());
        assertEquals(0.0, realTimeProductions.get(0).getRadiationIntensity());
        assertEquals(0.0, realTimeProductions.get(0).getTheoryPower());

        assertEquals("5D02E8B40AD342159AC8D8A2BCD4FAB5", realTimeProductions.get(1).getStationCode());
        assertEquals(Instant.ofEpochMilli(1501866000000L), realTimeProductions.get(1).getTime());
        assertEquals(10, realTimeProductions.get(1).getInverterPower());
        assertEquals(0.0, realTimeProductions.get(1).getOngridPower());
        assertEquals(5, realTimeProductions.get(1).getPowerProfit());
        assertEquals(0.0, realTimeProductions.get(1).getRadiationIntensity());
        assertEquals(0.0, realTimeProductions.get(1).getTheoryPower());

        assertEquals("BA4372D08E014822AB065017416F254C", realTimeProductions.get(2).getStationCode());
        assertEquals(Instant.ofEpochMilli(1501873200000L), realTimeProductions.get(2).getTime());
        assertEquals(10, realTimeProductions.get(2).getInverterPower());
        assertEquals(20, realTimeProductions.get(2).getOngridPower());
        assertEquals(5, realTimeProductions.get(2).getPowerProfit());
        assertEquals(500, realTimeProductions.get(2).getRadiationIntensity());
        assertEquals(12.5, realTimeProductions.get(2).getTheoryPower());
    }

    private void prepareSuccessfulResponse(String responseBodyString) throws IOException {

        when(huaweiAuthorizer.getAuthToken()).thenReturn("TOKEN");

        OkHttpClient client = Mockito.mock(OkHttpClient.class);
        when(conluzRestClientBuilder.build()).thenReturn(client);

        Call call = Mockito.mock(Call.class);
        when(client.newCall(any())).thenReturn(call);

        Response response = Mockito.mock(Response.class);
        when(call.execute()).thenReturn(response);

        ResponseBody responseBody = Mockito.mock(ResponseBody.class);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn(responseBodyString);
    }
}