package org.lucoenergia.conluz.infrastructure.datadis.get;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.datadis.MeasurementType;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.DatadisTokenRejectedException;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetConsumptionFromDatadisRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.supply.DatadisSupplyConfigurationException;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisAuthorizer;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisDateTimeConverter;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisParams;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Duration;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;


@Repository
public class GetConsumptionFromDatadisRepositoryRest implements GetConsumptionFromDatadisRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetConsumptionFromDatadisRepositoryRest.class);

    private static final String API_PATH = "/api-private/api";
    private static final String GET_CONSUMPTION_DATA_PATH = "/get-consumption-data";

    private final ObjectMapper objectMapper;
    private final DatadisAuthorizer datadisAuthorizer;
    private final ConluzRestClientBuilder conluzRestClientBuilder;
    private final DatadisDateTimeConverter datadisDateTimeConverter;

    public GetConsumptionFromDatadisRepositoryRest(ObjectMapper objectMapper, DatadisAuthorizer datadisAuthorizer,
                                                   ConluzRestClientBuilder conluzRestClientBuilder,
                                                   DatadisDateTimeConverter datadisDateTimeConverter) {
        this.objectMapper = objectMapper;
        this.datadisAuthorizer = datadisAuthorizer;
        this.conluzRestClientBuilder = conluzRestClientBuilder;
        this.datadisDateTimeConverter = datadisDateTimeConverter;
    }

    @Override
    public List<DatadisConsumption> getHourlyConsumptionsByMonth(DatadisConfig config, String bearerToken,
                                                                 Supply supply, Month month, int year) {

        final List<DatadisConsumption> result = new ArrayList<>();

        final String monthDate = datadisDateTimeConverter.convertFromMonthAndYear(month, year);

        LOGGER.info("Processing supply {} to get consumptions.", supply.getId());

        validateSupply(supply);

        final OkHttpClient client = conluzRestClientBuilder.build(false, Duration.ofSeconds(60));

        final String baseUrl = config.getBaseUrl();

        // Create the complete URL with the query parameter
        UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromUriString(baseUrl + API_PATH + GET_CONSUMPTION_DATA_PATH)
                .queryParam(DatadisParams.CUPS, supply.getCode())
                .queryParam(DatadisParams.DISTRIBUTOR_CODE, supply.getDistributor().getCode())
                .queryParam(DatadisParams.START_DATE, monthDate)
                .queryParam(DatadisParams.END_DATE, monthDate)
                .queryParam(DatadisParams.MEASUREMENT_TYPE, MeasurementType.PER_HOUR)
                .queryParam(DatadisParams.POINT_TYPE, supply.getDistributor().getPointType());
        if (datadisAuthorizer.requiresAuthorizedNif(config, UserPersonalId.of(supply.getUser().getPersonalId()))) {
            urlBuilder = urlBuilder.queryParam(DatadisParams.AUTHORIZED_NIF, supply.getUser().getPersonalId());
        }
        final String url = urlBuilder.build().toUriString();

        final Request request = new Request.Builder()
                .url(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                .header(HttpHeaders.ACCEPT, "*/*")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.code() == HttpStatus.UNAUTHORIZED.value()) {
                // The caller decides whether to log in again: the token belongs to its run, not to this request
                throw new DatadisTokenRejectedException(String.format(
                        "Datadis rejected the token for supply with ID %s for month %s and year %s",
                        supply.getId(), month, year));
            }
            if (response.isSuccessful()) {

                String jsonData = response.body() != null ? response.body().string() : "";

                List<DatadisConsumption> consumptions = objectMapper.readValue(jsonData, new TypeReference<>() {});

                result.addAll(consumptions);
            } else {
                LOGGER.error("Unable to get consumptions for supply with ID {} for month {} and year {}. Code {}, message: {}",
                        supply.getId(), month, year, response.code(), response.body() != null ? response.body().string() : response.message());
            }
        } catch (IOException e) {
            LOGGER.error("Unable to get consumptions from datadis", e);
        }

        LOGGER.info("Supply processed.");
        LOGGER.debug("Results. {}", result);

        return result;
    }

    private void validateSupply(Supply supply) {
        if (supply.getDistributor() == null || supply.getDistributor().getCode() == null || supply.getDistributor().getCode().isEmpty()) {
            throw new DatadisSupplyConfigurationException("Distributor code is mandatory to get monthly consumption.");
        }
        if (supply.getDistributor() == null || supply.getDistributor().getPointType() == null) {
            throw new DatadisSupplyConfigurationException("Point type is mandatory to get monthly consumption.");
        }
    }
}
