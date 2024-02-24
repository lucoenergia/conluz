package org.lucoenergia.conluz.infrastructure.consumption.datadis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.datadis.Consumption;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.shared.datadis.*;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Month;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DatadisConsumptionRepositoryRest implements DatadisConsumptionRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatadisConsumptionRepositoryRest.class);

    private static final String GET_CONSUMPTION_DATA_PATH = "/get-consumption-data";

    private final ObjectMapper objectMapper;
    private final DatadisAuthorizer datadisAuthorizer;
    private final RestClientBuilder restClientBuilder;

    public DatadisConsumptionRepositoryRest(ObjectMapper objectMapper, DatadisAuthorizer datadisAuthorizer,
                                            RestClientBuilder restClientBuilder) {
        this.objectMapper = objectMapper;
        this.datadisAuthorizer = datadisAuthorizer;
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public Map<String, List<Consumption>> getMonthlyConsumption(@NotNull List<Supply> supplies, Month month, int year) {

        final Map<String, List<Consumption>> result = new HashMap<>();

        final String monthDate = getDateAsString(month, year);

        final String authToken = datadisAuthorizer.getAuthTokenWithBearerFormat();

        final OkHttpClient client = restClientBuilder.build();

        for (Supply supply : supplies) {

            LOGGER.info("Processing supply {}", supply.getId());

            validateSupply(supply);

            // Create the complete URL with the query parameter
            final String url = UriComponentsBuilder.fromUriString(DatadisConfig.BASE_URL + GET_CONSUMPTION_DATA_PATH)
                    .queryParam(DatadisParams.CUPS, supply.getId())
                    .queryParam(DatadisParams.DISTRIBUTOR_CODE, supply.getDistributorCode())
                    .queryParam(DatadisParams.AUTHORIZED_NIF, supply.getUser().getPersonalId())
                    .queryParam(DatadisParams.START_DATE, monthDate)
                    .queryParam(DatadisParams.END_DATE, monthDate)
                    .queryParam(DatadisParams.MEASUREMENT_TYPE, MeasurementType.PER_HOUR)
                    .queryParam(DatadisParams.POINT_TYPE, supply.getPointType())
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

                    List<Consumption> consumptions = objectMapper.readValue(jsonData, new TypeReference<List<Consumption>>() {});

                    result.put(supply.getId(), consumptions);
                } else {
                    throw new DatadisException(String.format("Unable to get consumptions for supply with ID %s. Code %s, message: %s",
                            supply.getId(), response.code(), response.body() != null ? response.body().string() : response.message()));
                }
            } catch (IOException e) {
                throw new DatadisException("Unable to make the request to datadis.es", e);
            }
        }

        LOGGER.info("All supplies processed.");

        return result;
    }

    private String getDateAsString(Month month, int year) {
        return String.format("%s/%s", year, month.getValue());
    }

    private void validateSupply(Supply supply) {
        if (supply.getDistributorCode() == null || supply.getDistributorCode().isEmpty()) {
            throw new DatadisSupplyConfigurationException("Distributor code is mandatory to get monthly consumption.");
        }
        if (supply.getPointType() == null || supply.getPointType().isEmpty()) {
            throw new DatadisSupplyConfigurationException("Point type is mandatory to get monthly consumption.");
        }
    }
}
