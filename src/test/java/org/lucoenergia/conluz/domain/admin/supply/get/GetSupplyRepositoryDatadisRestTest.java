package org.lucoenergia.conluz.domain.admin.supply.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.sync.DatadisSupply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.infrastructure.shared.datadis.DatadisAuthorizer;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetSupplyRepositoryDatadisRestTest {

    private final GetUserRepository getUserRepository = Mockito.mock(GetUserRepository.class);
    private final ConluzRestClientBuilder conluzRestClientBuilder = Mockito.mock(ConluzRestClientBuilder.class);
    private final DatadisAuthorizer datadisAuthorizer = Mockito.mock(DatadisAuthorizer.class);

    GetSupplyRepositoryDatadisRest getSupplyRepositoryDatadisRest = new GetSupplyRepositoryDatadisRest(
            new ObjectMapper(), datadisAuthorizer, conluzRestClientBuilder, getUserRepository);

    @Test
    void testGetSupplies() {
        // Assemble
        User defaultAdminUser = new User.Builder()
                .personalId("personalId1")
                .build();
        Mockito.when(getUserRepository.getDefaultAdminUser()).thenReturn(Optional.of(defaultAdminUser));

        Mockito.when(datadisAuthorizer.getAuthTokenWithBearerFormat()).thenReturn("Bearer: auth-token");

        OkHttpClient client = Mockito.mock(OkHttpClient.class);
        Mockito.when(conluzRestClientBuilder.build()).thenReturn(client);

        Response successfulResponse = new Response.Builder()
                .code(200)
                .message("ok")
                .body(ResponseBody.create(MediaType.get("application/json"), "[]"))
                .request(new Request.Builder().url("http://localhost").build())
                .protocol(Protocol.HTTP_1_1)
                .build();

        try {
            Call call = Mockito.mock(Call.class);
            Mockito.when(client.newCall(Mockito.any(Request.class))).thenReturn(call);
            Mockito.when(call.execute()).thenReturn(successfulResponse);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Act and assert
        assertTrue(getSupplyRepositoryDatadisRest.getSupplies().isEmpty());

        DatadisSupply datadisSupply = new DatadisSupply.Builder()
                .withAddress("address1")
                .withCups("cups1")
                .withPostalCode("postalCode1")
                .withProvince("province1")
                .withMunicipality("municipality1")
                .withValidDateFrom("validDateFrom1")
                .withValidDateTo("validDateTo1")
                .withPointType(1)
                .withDistributor("distributor1")
                .withDistributorCode("distributorCode1")
                .build();

        String suppliesJson = "[{\"address\": \"address1\",\"cups\": \"cups1\",\"postalCode\": \"postalCode1\",\"province\": \"province1\","
                + "\"municipality\": \"municipality1\",\"validDateFrom\": \"validDateFrom1\",\"validDateTo\": \"validDateTo1\","
                + "\"pointType\": 1,\"distributor\": \"distributor1\",\"distributorCode\": \"distributorCode1\"}]";

        successfulResponse = new Response.Builder()
                .code(200)
                .message("ok")
                .body(ResponseBody.create(MediaType.get("application/json"), suppliesJson))
                .request(new Request.Builder().url("http://localhost").build())
                .protocol(Protocol.HTTP_1_1)
                .build();

        try {
            Call call = Mockito.mock(Call.class);
            Mockito.when(client.newCall(Mockito.any(Request.class))).thenReturn(call);
            Mockito.when(call.execute()).thenReturn(successfulResponse);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Act
        List<DatadisSupply> result = getSupplyRepositoryDatadisRest.getSupplies();

        // Assert
        assertEquals(1, result.size());
        assertEquals(datadisSupply.getCups(), result.get(0).getCups());
        assertEquals(datadisSupply.getAddress(), result.get(0).getAddress());
        assertEquals(datadisSupply.getPostalCode(), result.get(0).getPostalCode());
        assertEquals(datadisSupply.getProvince(), result.get(0).getProvince());
        assertEquals(datadisSupply.getMunicipality(), result.get(0).getMunicipality());
        assertEquals(datadisSupply.getValidDateFrom(), result.get(0).getValidDateFrom());
        assertEquals(datadisSupply.getValidDateTo(), result.get(0).getValidDateTo());
        assertEquals(datadisSupply.getPointType(), result.get(0).getPointType());
        assertEquals(datadisSupply.getDistributor(), result.get(0).getDistributor());
        assertEquals(datadisSupply.getDistributorCode(), result.get(0).getDistributorCode());
    }
}