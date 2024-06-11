package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.DatadisSupplyConfigurationException;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisAuthorizer;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisDateTimeConverter;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisParams;
import org.lucoenergia.conluz.domain.consumption.datadis.MeasurementType;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Duration;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;


@Repository
@Qualifier("getDatadisConsumptionRepositoryRest")
public class GetDatadisConsumptionRepositoryRest implements GetDatadisConsumptionRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetDatadisConsumptionRepositoryRest.class);

    private static final String GET_CONSUMPTION_DATA_PATH = "/get-consumption-data";

    private final ObjectMapper objectMapper;
    private final DatadisAuthorizer datadisAuthorizer;
    private final ConluzRestClientBuilder conluzRestClientBuilder;
    private final DatadisDateTimeConverter datadisDateTimeConverter;

    public GetDatadisConsumptionRepositoryRest(ObjectMapper objectMapper, DatadisAuthorizer datadisAuthorizer,
                                               ConluzRestClientBuilder conluzRestClientBuilder,
                                               DatadisDateTimeConverter datadisDateTimeConverter) {
        this.objectMapper = objectMapper;
        this.datadisAuthorizer = datadisAuthorizer;
        this.conluzRestClientBuilder = conluzRestClientBuilder;
        this.datadisDateTimeConverter = datadisDateTimeConverter;
    }

    @Override
    public List<DatadisConsumption> getHourlyConsumptionsByMonth(@NotNull Supply supply, @NotNull Month month, @NotNull int year) {

        final List<DatadisConsumption> result = new ArrayList<>();

        final String monthDate = datadisDateTimeConverter.convertFromMonthAndYear(month, year);

        final String authToken = datadisAuthorizer.getAuthBearerToken();

        final OkHttpClient client = conluzRestClientBuilder.build(false, Duration.ofSeconds(60));

        LOGGER.info("Processing supply {} to get consumptions.", supply.getId());

        validateSupply(supply);

        // Create the complete URL with the query parameter
        UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromUriString(DatadisConfigEntity.BASE_URL + GET_CONSUMPTION_DATA_PATH)
                .queryParam(DatadisParams.CUPS, supply.getCode())
                .queryParam(DatadisParams.DISTRIBUTOR_CODE, supply.getDatadisDistributorCode())
                .queryParam(DatadisParams.START_DATE, monthDate)
                .queryParam(DatadisParams.END_DATE, monthDate)
                .queryParam(DatadisParams.MEASUREMENT_TYPE, MeasurementType.PER_HOUR)
                .queryParam(DatadisParams.POINT_TYPE, supply.getDatadisPointType());
        if (supply.getDatadisIsThirdParty()) {
            urlBuilder = urlBuilder.queryParam(DatadisParams.AUTHORIZED_NIF, supply.getUser().getPersonalId());
        }
        final String url = urlBuilder.build().toUriString();

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

                List<DatadisConsumption> consumptions = objectMapper.readValue(jsonData, new TypeReference<List<DatadisConsumption>>() {
                });

                result.addAll(consumptions);
            } else {
                LOGGER.error("Unable to get consumptions for supply with ID {}. Code {}, message: {}",
                        supply.getCode(), response.code(), response.body() != null ? response.body().string() : response.message());
            }
        } catch (IOException e) {
            LOGGER.error("Unable to get consumptions from datadis.es", e);
        }

        LOGGER.info("Supply processed.");

        return result;
    }

    private void validateSupply(Supply supply) {
        if (supply.getDatadisDistributorCode() == null || supply.getDatadisDistributorCode().isEmpty()) {
            throw new DatadisSupplyConfigurationException("Distributor code is mandatory to get monthly consumption.");
        }
        if (supply.getDatadisPointType() == null) {
            throw new DatadisSupplyConfigurationException("Point type is mandatory to get monthly consumption.");
        }
    }
}
