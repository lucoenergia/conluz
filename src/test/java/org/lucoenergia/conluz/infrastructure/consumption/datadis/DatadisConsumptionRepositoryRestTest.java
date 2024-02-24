package org.lucoenergia.conluz.infrastructure.consumption.datadis;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.consumption.datadis.Consumption;
import org.lucoenergia.conluz.infrastructure.shared.datadis.DatadisAuthorizer;
import org.lucoenergia.conluz.infrastructure.shared.datadis.DatadisException;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.RestClientBuilder;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Month;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class DatadisConsumptionRepositoryRestTest {

    private DatadisConsumptionRepositoryRest repository;
    private DatadisAuthorizer datadisAuthorizer;
    private RestClientBuilder restClientBuilder;

    @BeforeEach
    public void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        datadisAuthorizer = Mockito.mock(DatadisAuthorizer.class);
        restClientBuilder = Mockito.mock(RestClientBuilder.class);
        repository = new DatadisConsumptionRepositoryRest(objectMapper, datadisAuthorizer, restClientBuilder);
    }

    @Test
    void testGetMonthlyConsumptionWithEmptySuppliesList() {
        // Assemble
        final List<Supply> supplies = Collections.emptyList();
        final Month month = Month.APRIL;
        final int year = 2023;

        Mockito
                .when(datadisAuthorizer.getAuthTokenWithBearerFormat())
                .thenReturn("token");

        // Act
        Map<String, List<Consumption>> result = repository.getMonthlyConsumption(supplies, month, year);

        // Assert
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void testGetMonthlyConsumptionWithSupplyWithoutDistributorCode() {
        // Assemble
        final List<Supply> supplies = Arrays.asList(
                new Supply.Builder().build()
        );
        final Month month = Month.APRIL;
        final int year = 2023;

        Mockito
                .when(datadisAuthorizer.getAuthTokenWithBearerFormat())
                .thenReturn("token");

        // Act & assert
        DatadisSupplyConfigurationException exception = Assertions.assertThrows(DatadisSupplyConfigurationException.class,
                () -> repository.getMonthlyConsumption(supplies, month, year));

        Assertions.assertEquals("Distributor code is mandatory to get monthly consumption.",
                exception.getMessage());
    }

    @Test
    void testGetMonthlyConsumptionWithSupplyWithoutPointType() {
        // Assemble
        final List<Supply> supplies = Arrays.asList(
                new Supply.Builder().withDistributorCode("2").build()
        );
        final Month month = Month.APRIL;
        final int year = 2023;

        Mockito
                .when(datadisAuthorizer.getAuthTokenWithBearerFormat())
                .thenReturn("token");

        // Act & assert
        DatadisSupplyConfigurationException exception = Assertions.assertThrows(DatadisSupplyConfigurationException.class,
                () -> repository.getMonthlyConsumption(supplies, month, year));

        Assertions.assertEquals("Point type is mandatory to get monthly consumption.",
                exception.getMessage());
    }

    @Test
    void testGetMonthlyConsumptionWithUnsuccessfulResponseAndEmptyBody() throws IOException {
        // Assemble
        final User user = new User.Builder().personalId("authorizedNif").build();
        final List<Supply> supplies = Arrays.asList(
                new Supply.Builder()
                        .withId("cups")
                        .withUser(user)
                        .withDistributorCode("distributorCode")
                        .withPointType("pointType")
                        .build()
        );
        final Month month = Month.APRIL;
        final int year = 2023;

        Mockito
                .when(datadisAuthorizer.getAuthTokenWithBearerFormat())
                .thenReturn("token");

        OkHttpClient client = Mockito.mock(OkHttpClient.class);
        Call call = Mockito.mock(Call.class);
        Response response = Mockito.mock(Response.class);
        ResponseBody body = Mockito.mock(ResponseBody.class);
        Mockito
                .when(restClientBuilder.build())
                .thenReturn(client);
        Mockito.when(client.newCall(Mockito.any(Request.class))).thenReturn(call);
        Mockito.when(call.execute()).thenReturn(response);
        Mockito.when(response.isSuccessful()).thenReturn(false);
        Mockito.when(response.code()).thenReturn(429);
        Mockito.when(response.body()).thenReturn(body);
        Mockito.when(body.string()).thenReturn("Consulta ya realizada en las últimas 24 horas.");

        // Act & Assert
        DatadisException exception = Assertions.assertThrows(DatadisException.class,
                () -> repository.getMonthlyConsumption(supplies, month, year));

        Assertions.assertEquals(
                "Unable to get consumptions for supply with ID cups. Code 429, message: Consulta ya realizada en las últimas 24 horas.",
                exception.getMessage());
    }

    @Test
    void testGetMonthlyConsumptionWithSuccessfulResponse() throws IOException {
        // Assemble
        final User user = new User.Builder().personalId("authorizedNif").build();
        final List<Supply> supplies = Arrays.asList(
                new Supply.Builder()
                        .withId("ES0031300329693002BQ0F")
                        .withUser(user)
                        .withDistributorCode("distributorCode")
                        .withPointType("pointType")
                        .build()
        );
        final Month month = Month.APRIL;
        final int year = 2023;

        Mockito
                .when(datadisAuthorizer.getAuthTokenWithBearerFormat())
                .thenReturn("token");

        OkHttpClient client = Mockito.mock(OkHttpClient.class);
        Call call = Mockito.mock(Call.class);
        Response response = Mockito.mock(Response.class);
        ResponseBody body = Mockito.mock(ResponseBody.class);
        Mockito
                .when(restClientBuilder.build())
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
                  "consumptionKWh" : 0.0,
                  "obtainMethod" : "Real",
                  "surplusEnergyKWh" : 0.0
                }, {
                  "cups" : "ES0031300329693002BQ0F",
                  "date" : "2023/10/01",
                  "time" : "02:00",
                  "consumptionKWh" : 0.0,
                  "obtainMethod" : "Real",
                  "surplusEnergyKWh" : 0.0
                }, {
                  "cups" : "ES0031300329693002BQ0F",
                  "date" : "2023/10/01",
                  "time" : "03:00",
                  "consumptionKWh" : 4.2,
                  "obtainMethod" : "Real",
                  "surplusEnergyKWh" : 0.5
                }]
                """);

        // Act & Assert
        Map<String, List<Consumption>> result = repository.getMonthlyConsumption(supplies, month, year);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(3, result.get("ES0031300329693002BQ0F").size());

        Assertions.assertEquals("ES0031300329693002BQ0F", result.get("ES0031300329693002BQ0F").get(0).getCups());
        Assertions.assertEquals("2023/10/01", result.get("ES0031300329693002BQ0F").get(0).getDate());
        Assertions.assertEquals("01:00", result.get("ES0031300329693002BQ0F").get(0).getTime());
        Assertions.assertEquals(0.0f, result.get("ES0031300329693002BQ0F").get(0).getConsumptionKWh());
        Assertions.assertEquals("Real", result.get("ES0031300329693002BQ0F").get(0).getObtainMethod());
        Assertions.assertEquals(0.0f, result.get("ES0031300329693002BQ0F").get(0).getSurplusEnergyKWh());

        Assertions.assertEquals("ES0031300329693002BQ0F", result.get("ES0031300329693002BQ0F").get(2).getCups());
        Assertions.assertEquals("2023/10/01", result.get("ES0031300329693002BQ0F").get(2).getDate());
        Assertions.assertEquals("03:00", result.get("ES0031300329693002BQ0F").get(2).getTime());
        Assertions.assertEquals(4.2f, result.get("ES0031300329693002BQ0F").get(2).getConsumptionKWh());
        Assertions.assertEquals("Real", result.get("ES0031300329693002BQ0F").get(2).getObtainMethod());
        Assertions.assertEquals(0.5f, result.get("ES0031300329693002BQ0F").get(2).getSurplusEnergyKWh());
    }
}
