package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.lucoenergia.conluz.domain.admin.supply.DatadisSupply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepositoryDatadis;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.shared.datadis.DatadisAuthorizer;
import org.lucoenergia.conluz.infrastructure.shared.datadis.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.datadis.DatadisParams;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class GetSupplyRepositoryDatadisRest implements GetSupplyRepositoryDatadis {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetSupplyRepositoryDatadisRest.class);

    private static final String GET_SUPPLIES_PATH = "/get-supplies";

    private final ObjectMapper objectMapper;
    private final DatadisAuthorizer datadisAuthorizer;
    private final ConluzRestClientBuilder conluzRestClientBuilder;

    public GetSupplyRepositoryDatadisRest(ObjectMapper objectMapper, DatadisAuthorizer datadisAuthorizer,
                                          ConluzRestClientBuilder conluzRestClientBuilder) {
        this.objectMapper = objectMapper;
        this.datadisAuthorizer = datadisAuthorizer;
        this.conluzRestClientBuilder = conluzRestClientBuilder;
    }

    @Override
    public List<DatadisSupply> getSuppliesByUser(User user) {

        final List<DatadisSupply> result = new ArrayList<>();

        final String authToken = datadisAuthorizer.getAuthBearerToken();

        final OkHttpClient client = conluzRestClientBuilder.build();

        LOGGER.info("Getting all supplies from datadis.es of user {}", user.getId());

        // Create the complete URL with the query parameter
        final String url = UriComponentsBuilder.fromUriString(DatadisConfigEntity.BASE_URL + GET_SUPPLIES_PATH)
                .queryParam(DatadisParams.AUTHORIZED_NIF, user.getPersonalId())
                .build()
                .toUriString();

        final Request request = new Request.Builder()
                .url(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, authToken)
                .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                .header(HttpHeaders.ACCEPT, "*/*")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {

                String jsonData = response.body().string();

                List<DatadisSupply> datadisSupplies = objectMapper.readValue(jsonData, new TypeReference<List<DatadisSupply>>() {
                });

                result.addAll(datadisSupplies);
            } else {
                LOGGER.error("Unable to get supplies from user {}. Code {}, message: {}",
                        user.getId(), response.code(),
                        response.body() != null ? response.body().string() : response.message());

                return result;
            }
        } catch (IOException e) {
            LOGGER.error(String.format("Unable to get supplies from datadis.es for user %s", user.getId()), e);
            return result;
        }

        LOGGER.info("Supplies retrieved.");

        return result;
    }
}
