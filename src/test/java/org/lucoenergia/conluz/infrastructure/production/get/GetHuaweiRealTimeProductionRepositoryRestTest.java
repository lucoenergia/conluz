package org.lucoenergia.conluz.infrastructure.production.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiAuthorizer;
import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;
import org.lucoenergia.conluz.infrastructure.production.huawei.get.GetHuaweiRealTimeProductionRepositoryRest;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class GetHuaweiRealTimeProductionRepositoryRestTest {

    private final HuaweiAuthorizer huaweiAuthorizer = Mockito.mock(HuaweiAuthorizer.class);

    private final ConluzRestClientBuilder conluzRestClientBuilder = Mockito.mock(ConluzRestClientBuilder.class);

    private final DateConverter dateConverter = Mockito.mock(DateConverter.class);

    private GetHuaweiRealTimeProductionRepositoryRest repositoryRest;

    @BeforeEach
    public void setUp() {
        repositoryRest = new GetHuaweiRealTimeProductionRepositoryRest(new ObjectMapper(), huaweiAuthorizer,
                conluzRestClientBuilder, dateConverter);
    }

    @Test
    void getRealTimeProduction_shouldReturnEmptyListWhenStationCodesIsEmpty() {
        // Given
        List<Plant> stationCodes = List.of();

        // When
        List<RealTimeProduction> realTimeProductions = repositoryRest.getRealTimeProduction(stationCodes);

        // Then
        assertTrue(realTimeProductions.isEmpty());
    }

    @Test
    void getRealTimeProduction_shouldReturnProductionWhenStationCodesIsNotEmpty() throws IOException {
        // Given
        List<Plant> stationCodes = Arrays.asList(
                new Plant.Builder().withCode("BA4372D08E014822AB065017416F254C").build(),
                new Plant.Builder().withCode("5D02E8B40AD342159AC8D8A2BCD4FAB5").build()
        );

        String responseBodyString = """
                {
                   "success":true,
                   "data":[
                      {
                         "dataItemMap":{
                            "real_health_state":"3",
                            "day_power":"10000",
                            "total_power":"900.000",
                            "day_income":"0.000",
                            "month_power":"900.000",
                            "total_income":"2088.000"
                         },
                         "stationCode":"BA4372D08E014822AB065017416F254C"
                      },
                      {
                         "dataItemMap":{
                            "real_health_state":"1",
                            "day_power":"16770.000",
                            "total_power":"35100.000",
                            "day_income":"26832.000",
                            "month_power":"35100.000",
                            "total_income":"61152.000"
                         },
                         "stationCode":"5D02E8B40AD342159AC8D8A2BCD4FAB5"
                      }
                   ],
                   "failCode":0,
                   "params":{
                      "stationCodes":"BA4372D08E014822AB065017416F254C,5D02E8B40AD342159AC8D8A2BCD4FAB5",
                      "currentTime":1503046597854
                   },
                   "message":null
                }
                """;


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

        // When
        List<RealTimeProduction> realTimeProductions = repositoryRest.getRealTimeProduction(stationCodes);

        // Then
        assertEquals(2, realTimeProductions.size());

        assertEquals("BA4372D08E014822AB065017416F254C", realTimeProductions.get(0).getStationCode());
        assertEquals(0.000d, realTimeProductions.get(0).getDayIncome());
        assertEquals(10000d, realTimeProductions.get(0).getDayPower());
        assertEquals(900.000d, realTimeProductions.get(0).getMonthPower());
        assertEquals(2088.000d, realTimeProductions.get(0).getTotalIncome());
        assertEquals(900.000d, realTimeProductions.get(0).getTotalPower());
        assertEquals(3, realTimeProductions.get(0).getRealHealthState());

        assertEquals("5D02E8B40AD342159AC8D8A2BCD4FAB5", realTimeProductions.get(1).getStationCode());
        assertEquals(26832.000d, realTimeProductions.get(1).getDayIncome());
        assertEquals(16770.000d, realTimeProductions.get(1).getDayPower());
        assertEquals(35100.000d, realTimeProductions.get(1).getMonthPower());
        assertEquals(61152.000d, realTimeProductions.get(1).getTotalIncome());
        assertEquals(35100.000d, realTimeProductions.get(1).getTotalPower());
        assertEquals(1, realTimeProductions.get(1).getRealHealthState());
    }
}