package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.distributor.SupplyDistributor;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConfigRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisAuthorizer;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisDateTimeConverter;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisParams;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.time.Month;
import java.util.List;
import java.util.Optional;

class GetDatadisConsumptionRepositoryRestTest {

    private static final String ACCOUNT_NIF = "accountNif";

    private DatadisAuthorizer datadisAuthorizer;
    private ConluzRestClientBuilder conluzRestClientBuilder;
    private GetDatadisConsumptionRepositoryRest repository;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        // Spy on a real authorizer so requiresAuthorizedNif uses the real NIF comparison,
        // while getAuthToken is stubbed to avoid any HTTP call.
        datadisAuthorizer = Mockito.spy(new DatadisAuthorizer(Mockito.mock(GetDatadisConfigRepository.class),
                Mockito.mock(ConluzRestClientBuilder.class)));
        conluzRestClientBuilder = Mockito.mock(ConluzRestClientBuilder.class);
        DatadisDateTimeConverter datadisDateTimeConverter = Mockito.mock(DatadisDateTimeConverter.class);
        GetDatadisConfigRepository getDatadisConfigRepository = Mockito.mock(GetDatadisConfigRepository.class);
        DatadisConfig config = new DatadisConfig.Builder()
                .setUsername(ACCOUNT_NIF)
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .build();
        Mockito.when(getDatadisConfigRepository.getDatadisConfig()).thenReturn(Optional.of(config));
        Mockito.doReturn("testToken").when(datadisAuthorizer).getAuthToken(Mockito.any(DatadisConfig.class));
        repository = new GetDatadisConsumptionRepositoryRest(objectMapper, datadisAuthorizer, conluzRestClientBuilder,
                datadisDateTimeConverter, getDatadisConfigRepository);
    }

    private Supply supplyOwnedBy(String personalId) {
        return new Supply.Builder()
                .withCode("ES0031300329693002BQ0F")
                .withUser(new User.Builder().personalId(personalId).build())
                .withDistributor(new SupplyDistributor.Builder()
                        .withCode("distributorCode")
                        .withPointType(5)
                        .build())
                .build();
    }

    /**
     * Captures the URL of the request issued to Datadis for the given supply. The response is
     * stubbed as an empty successful body so the flow reaches the HTTP client.
     */
    private String captureRequestUrl(Supply supply) throws IOException {
        OkHttpClient client = Mockito.mock(OkHttpClient.class);
        Call call = Mockito.mock(Call.class);
        Response response = Mockito.mock(Response.class);
        ResponseBody body = Mockito.mock(ResponseBody.class);
        Mockito.when(conluzRestClientBuilder.build(false, Duration.ofSeconds(60))).thenReturn(client);
        Mockito.when(client.newCall(Mockito.any(Request.class))).thenReturn(call);
        Mockito.when(call.execute()).thenReturn(response);
        Mockito.when(response.isSuccessful()).thenReturn(true);
        Mockito.when(response.code()).thenReturn(200);
        Mockito.when(response.body()).thenReturn(body);
        Mockito.when(body.string()).thenReturn("[]");

        repository.getHourlyConsumptionsByMonth(supply, Month.APRIL, 2023);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        Mockito.verify(client).newCall(requestCaptor.capture());
        return requestCaptor.getValue().url().toString();
    }

    @Test
    void testAuthorizedNifIsOmittedWhenSupplyOwnerMatchesDatadisAccount() throws IOException {
        // The supply owner NIF equals the Datadis account username, so no authorizedNif is sent.
        String url = captureRequestUrl(supplyOwnedBy(ACCOUNT_NIF));

        Assertions.assertFalse(url.contains(DatadisParams.AUTHORIZED_NIF));
    }

    @Test
    void testAuthorizedNifIsAddedWhenSupplyOwnerDiffersFromDatadisAccount() throws IOException {
        // The supply owner NIF differs from the Datadis account username, so authorizedNif is sent
        // and equals the owner NIF.
        String ownerNif = "differentOwnerNif";
        String url = captureRequestUrl(supplyOwnedBy(ownerNif));

        Assertions.assertTrue(url.contains(DatadisParams.AUTHORIZED_NIF + "=" + ownerNif));
    }

    @Test
    void testGetMonthlyConsumptionWithUnsuccessfulResponseAndEmptyBody() throws IOException {
        // Assemble
        final Supply supply = supplyOwnedBy("differentOwnerNif");
        final Month month = Month.APRIL;
        final int year = 2023;

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
        final Supply supply = supplyOwnedBy(ACCOUNT_NIF);
        final Month month = Month.APRIL;
        final int year = 2023;

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
        final Supply supply = supplyOwnedBy(ACCOUNT_NIF);
        final Month month = Month.APRIL;
        final int year = 2023;

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
