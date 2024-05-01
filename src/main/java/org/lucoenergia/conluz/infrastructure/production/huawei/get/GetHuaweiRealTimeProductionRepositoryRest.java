package org.lucoenergia.conluz.infrastructure.production.huawei.get;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiAuthorizer;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiException;
import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class GetHuaweiRealTimeProductionRepositoryRest {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetHuaweiRealTimeProductionRepositoryRest.class);

    private static final String URL = HuaweiConfig.BASE_URL + "/getStationRealKpi";
    private static final String PARAM_STATION_CODES = "stationCodes";

    private final ObjectMapper objectMapper;
    private final HuaweiAuthorizer huaweiAuthorizer;
    private final ConluzRestClientBuilder conluzRestClientBuilder;
    private final DateConverter dateConverter;

    public GetHuaweiRealTimeProductionRepositoryRest(ObjectMapper objectMapper, HuaweiAuthorizer huaweiAuthorizer,
                                                     ConluzRestClientBuilder conluzRestClientBuilder,
                                                     DateConverter dateConverter) {
        this.objectMapper = objectMapper;
        this.huaweiAuthorizer = huaweiAuthorizer;
        this.conluzRestClientBuilder = conluzRestClientBuilder;
        this.dateConverter = dateConverter;
    }

    public List<RealTimeProduction> getRealTimeProduction(List<Plant> stations) {

        List<RealTimeProduction> result = new ArrayList<>();

        if (stations == null || stations.isEmpty()) {
            LOGGER.info("No Huawei stations provided");
            return result;
        }

        String stationCodes = stations.stream()
                .map(Plant::getCode)
                .collect(Collectors.joining(", "));

        final String authToken = huaweiAuthorizer.getAuthToken();

        final OkHttpClient client = conluzRestClientBuilder.build();

        LOGGER.info("Getting real time production from stations {} .", stationCodes);

        // Create the complete URL
        final String url = UriComponentsBuilder.fromUriString(URL)
                .build()
                .toUriString();

        Map<String, Object> map = new HashMap<>();
        map.put(PARAM_STATION_CODES, stationCodes);
        RequestBody requestBody;
        try {
            requestBody = RequestBody.create(
                    objectMapper.writeValueAsString(map),
                    okhttp3.MediaType.parse(String.join(";", List.of(MediaType.APPLICATION_JSON_VALUE, "charset=UTF-8")))
            );
        } catch (JsonProcessingException e) {
            throw new HuaweiException("Error generating body to get real-time production", e);
        }

        final Request request = new Request.Builder()
                .url(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HuaweiAuthorizer.TOKEN_HEADER, authToken)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.body() != null) {
                    String jsonData = response.body().string();
                    result.addAll(processBody(jsonData));
                } else {
                    LOGGER.error("Response body is empty");
                }
            } else {
                LOGGER.error("Unable to get real-time production from stations {}. Code {}, message: {}",
                        stations, response.code(),
                        response.body() != null ? response.body().string() : response.message());
            }
        } catch (IOException e) {
            LOGGER.error(String.format("Unable to get real-time production from stations %s", stations), e);
        }

        LOGGER.info("Stations processed.");

        return result;
    }

    private List<RealTimeProduction> processBody(String body) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode dataNode = root.get("data");
        long currentTime = root.path("params").path("currentTime").asLong();
        if (currentTime <= 0) {
            LOGGER.error("Current time is less than or equal to zero. Current time is {}", currentTime);
            return new ArrayList<>();
        }

        List<RealTimeProduction> dataList = new ArrayList<>();

        if (dataNode.isArray()) {
            for (JsonNode node : dataNode) {

                JsonNode dataItemMap = node.get("dataItemMap");

                RealTimeProduction item = new RealTimeProduction.Builder()
                        .setTime(dateConverter.convertMillisecondsToOffsetDateTime(currentTime))
                        .setStationCode(node.get("stationCode").asText())
                        .setDayIncome(dataItemMap.get("day_income").asDouble())
                        .setDayPower(dataItemMap.get("day_power").asDouble())
                        .setMonthPower(dataItemMap.get("month_power").asDouble())
                        .setTotalPower(dataItemMap.get("total_power").asDouble())
                        .setTotalIncome(dataItemMap.get("total_income").asDouble())
                        .setRealHealthState(dataItemMap.get("real_health_state").asInt())
                        .build();

                dataList.add(item);
            }
        }

        return dataList;
    }
}