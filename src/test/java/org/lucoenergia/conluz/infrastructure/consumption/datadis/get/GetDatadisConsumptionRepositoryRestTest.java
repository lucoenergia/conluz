package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisAuthorizer;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisDateTimeConverter;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.time.Month;
import java.util.List;

class GetDatadisConsumptionRepositoryRestTest {

    private DatadisAuthorizer datadisAuthorizer;
    private ConluzRestClientBuilder conluzRestClientBuilder;
    private GetDatadisConsumptionRepositoryRest repository;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        datadisAuthorizer = Mockito.mock(DatadisAuthorizer.class);
        conluzRestClientBuilder = Mockito.mock(ConluzRestClientBuilder.class);
        DatadisDateTimeConverter datadisDateTimeConverter = Mockito.mock(DatadisDateTimeConverter.class);
        repository = new GetDatadisConsumptionRepositoryRest(objectMapper, datadisAuthorizer, conluzRestClientBuilder,
                datadisDateTimeConverter);
    }

    @Test
    void testGetMonthlyConsumptionWithUnsuccessfulResponseAndEmptyBody() throws IOException {
        // Assemble
        final User user = new User.Builder().personalId("authorizedNif").build();
        final Supply supply = new Supply.Builder()
                .withCode("cups")
                .withUser(user)
                .withDistributorCode("distributorCode")
                .withPointType(5)
                .withThirdParty(true)
                .build();
        final Month month = Month.APRIL;
        final int year = 2023;

        Mockito
                .when(datadisAuthorizer.getAuthBearerToken())
                .thenReturn("token");

        OkHttpClient client = Mockito.mock(OkHttpClient.class);
        Call call = Mockito.mock(Call.class);
        Response response = Mockito.mock(Response.class);
        ResponseBody body = Mockito.mock(ResponseBody.class);
        Mockito
                .when(conluzRestClientBuilder.build(false, Duration.ofSeconds(60)))
                .thenReturn(client);
        Mockito.when(client.newCall(Mockito.any(Request.class))).thenReturn(call);
        Mockito.when(call.execute()).thenReturn(response);
        Mockito.when(response.isSuccessful()).thenReturn(false);
        Mockito.when(response.code()).thenReturn(429);
        Mockito.when(response.body()).thenReturn(body);
        Mockito.when(body.string()).thenReturn("Consulta ya realizada en las últimas 24 horas.");

        // Act & Assert
        Assertions.assertDoesNotThrow(() -> repository.getHourlyConsumptionsByMonth(supply, month, year));
    }

    @Test
    void testGetMonthlyConsumptionWithSuccessfulResponse() throws IOException {
        // Assemble
        final User user = new User.Builder().personalId("authorizedNif").build();
        final Supply supply = new Supply.Builder()
                .withCode("ES0031300329693002BQ0F")
                .withUser(user)
                .withDistributorCode("distributorCode")
                .withPointType(5)
                .withThirdParty(false)
                .build();
        final Month month = Month.APRIL;
        final int year = 2023;

        Mockito
                .when(datadisAuthorizer.getAuthBearerToken())
                .thenReturn("token");

        OkHttpClient client = Mockito.mock(OkHttpClient.class);
        Call call = Mockito.mock(Call.class);
        Response response = Mockito.mock(Response.class);
        ResponseBody body = Mockito.mock(ResponseBody.class);
        Mockito
                .when(conluzRestClientBuilder.build(false, Duration.ofSeconds(60)))
                .thenReturn(client);
        Mockito.when(client.newCall(Mockito.any(Request.class))).thenReturn(call);
        Mockito.when(call.execute()).thenReturn(response);
        Mockito.when(response.isSuccessful()).thenReturn(true);
        Mockito.when(response.code()).thenReturn(200);
        Mockito.when(response.body()).thenReturn(body);
        Mockito.when(body.string()).thenReturn("""
                [ {
                   "cups" : "ES0031300329693002BQ0F",
                   "date" : "2023/10/01",
                   "time" : "01:00",
                   "consumptionKWh" : 0.012,
                   "obtainMethod" : "Real",
                   "surplusEnergyKWh" : 0.10,
                   "generationEnergyKWh" : 0.20,
                   "selfConsumptionEnergyKWh" : 0.40
                 }, {
                   "cups" : "ES0031300329693002BQ0F",
                   "date" : "2023/10/01",
                   "time" : "02:00",
                   "consumptionKWh" : 0.03,
                   "obtainMethod" : "Real",
                   "surplusEnergyKWh" : 0.0,
                   "generationEnergyKWh" : 0.0,
                   "selfConsumptionEnergyKWh" : 0.0
                 }, {
                   "cups" : "ES0031300329693002BQ0F",
                   "date" : "2023/10/01",
                   "time" : "03:00",
                   "consumptionKWh" : 0.026,
                   "obtainMethod" : "Real",
                   "surplusEnergyKWh" : 0.0,
                   "generationEnergyKWh" : 0.0,
                   "selfConsumptionEnergyKWh" : 0.0
                 }]
                """);

        // Act & Assert
        List<DatadisConsumption> result = repository.getHourlyConsumptionsByMonth(supply, month, year);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(3, result.size());

        Assertions.assertEquals("ES0031300329693002BQ0F", result.get(0).getCups());
        Assertions.assertEquals("2023/10/01", result.get(0).getDate());
        Assertions.assertEquals("01:00", result.get(0).getTime());
        Assertions.assertEquals(0.012f, result.get(0).getConsumptionKWh());
        Assertions.assertEquals("Real", result.get(0).getObtainMethod());
        Assertions.assertEquals(0.10f, result.get(0).getSurplusEnergyKWh());
        Assertions.assertEquals(0.20f, result.get(0).getGenerationEnergyKWh());
        Assertions.assertEquals(0.40f, result.get(0).getSelfConsumptionEnergyKWh());

        Assertions.assertEquals("ES0031300329693002BQ0F", result.get(2).getCups());
        Assertions.assertEquals("2023/10/01", result.get(2).getDate());
        Assertions.assertEquals("03:00", result.get(2).getTime());
        Assertions.assertEquals(0.026f, result.get(2).getConsumptionKWh());
        Assertions.assertEquals("Real", result.get(2).getObtainMethod());
        Assertions.assertEquals(0.0f, result.get(2).getSurplusEnergyKWh());
        Assertions.assertEquals(0.0f, result.get(2).getGenerationEnergyKWh());
        Assertions.assertEquals(0.0f, result.get(2).getSelfConsumptionEnergyKWh());
    }


    @Test
    void testGetHourlyConsumptionWithUnsuccessfulResponse() throws IOException {
        // Assemble
        final User user = new User.Builder().personalId("authorizedNif").build();
        final Supply supply = new Supply.Builder()
                .withCode("ES0031300329693002BQ0F")
                .withUser(user)
                .withDistributorCode("distributorCode")
                .withPointType(5)
                .withThirdParty(false)
                .build();
        final Month month = Month.APRIL;
        final int year = 2023;

        Mockito
                .when(datadisAuthorizer.getAuthBearerToken())
                .thenReturn("token");

        OkHttpClient client = Mockito.mock(OkHttpClient.class);
        Call call = Mockito.mock(Call.class);
        Response response = Mockito.mock(Response.class);
        Mockito
                .when(conluzRestClientBuilder.build(false, Duration.ofSeconds(60)))
                .thenReturn(client);
        Mockito.when(client.newCall(Mockito.any(Request.class))).thenReturn(call);
        Mockito.when(call.execute()).thenReturn(response);
        Mockito.when(response.isSuccessful()).thenReturn(false);

        // Act
        List<DatadisConsumption> result = repository.getHourlyConsumptionsByMonth(supply, month, year);

        // Assert
        Assertions.assertTrue(result.isEmpty());
    }
}
