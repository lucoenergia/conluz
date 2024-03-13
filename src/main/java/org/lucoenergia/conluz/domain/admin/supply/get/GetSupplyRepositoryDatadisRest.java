package org.lucoenergia.conluz.domain.admin.supply.get;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.lucoenergia.conluz.domain.admin.datadis.DistributorCode;
import org.lucoenergia.conluz.domain.admin.supply.sync.DatadisSupply;
import org.lucoenergia.conluz.domain.admin.supply.sync.GetSupplyRepositoryDatadis;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.infrastructure.shared.datadis.*;
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
import java.util.Optional;

@Repository
public class GetSupplyRepositoryDatadisRest implements GetSupplyRepositoryDatadis {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetSupplyRepositoryDatadisRest.class);

    private static final String GET_SUPPLIES_PATH = "/get-supplies";

    private final ObjectMapper objectMapper;
    private final DatadisAuthorizer datadisAuthorizer;
    private final ConluzRestClientBuilder conluzRestClientBuilder;

    private final GetUserRepository getUserRepository;

    public GetSupplyRepositoryDatadisRest(ObjectMapper objectMapper, DatadisAuthorizer datadisAuthorizer,
                                          ConluzRestClientBuilder conluzRestClientBuilder,
                                          GetUserRepository getUserRepository) {
        this.objectMapper = objectMapper;
        this.datadisAuthorizer = datadisAuthorizer;
        this.conluzRestClientBuilder = conluzRestClientBuilder;
        this.getUserRepository = getUserRepository;
    }

    public List<DatadisSupply> getSupplies() {

        final List<DatadisSupply> result = new ArrayList<>();

        Optional<User> defaultAdminUser = getUserRepository.getDefaultAdminUser();
        if (defaultAdminUser.isEmpty()) {
            throw new UserNotFoundException("Default admin user not found.");
        }

        final String authToken = datadisAuthorizer.getAuthTokenWithBearerFormat();

        final OkHttpClient client = conluzRestClientBuilder.build();

        LOGGER.debug("Getting all supplies from datadis.es");

        // Create the complete URL with the query parameter
        final String url = UriComponentsBuilder.fromUriString(DatadisConfigEntity.BASE_URL + GET_SUPPLIES_PATH)
                .queryParam(DatadisParams.DISTRIBUTOR_CODE, DistributorCode.E_DISTRIBUCION)
                .queryParam(DatadisParams.AUTHORIZED_NIF, defaultAdminUser.get().getPersonalId())
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
                throw new DatadisException(String.format("Unable to get supplies. Code %s, message: %s",
                        response.code(), response.body() != null ? response.body().string() : response.message()));
            }
        } catch (IOException e) {
            throw new DatadisException("Unable to get supplies from datadis.es", e);
        }

        LOGGER.debug("Supplies retrieved.");

        return result;
    }
}
