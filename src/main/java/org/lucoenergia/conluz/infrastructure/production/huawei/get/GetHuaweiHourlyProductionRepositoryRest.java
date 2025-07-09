package org.lucoenergia.conluz.infrastructure.production.huawei.get;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.lucoenergia.conluz.domain.production.huawei.HourlyProduction;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.infrastructure.production.ProductionPoint;
import org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiAuthorizer;
import org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiException;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class GetHuaweiHourlyProductionRepositoryRest {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetHuaweiHourlyProductionRepositoryRest.class);

    private static final String PATH = HuaweiConfig.BASE_URL + "/getKpiStationHour";
    private static final String URL = UriComponentsBuilder.fromUriString(PATH).build().toUriString();
    private static final String PARAM_STATION_CODES = "stationCodes";
    private static final String PARAM_COLLECT_TIME = "collectTime";

    private final ObjectMapper objectMapper;
    private final HuaweiAuthorizer huaweiAuthorizer;
    private final ConluzRestClientBuilder conluzRestClientBuilder;
    private final DateConverter dateConverter;

    public GetHuaweiHourlyProductionRepositoryRest(ObjectMapper objectMapper, HuaweiAuthorizer huaweiAuthorizer,
                                                   ConluzRestClientBuilder conluzRestClientBuilder,
                                                   DateConverter dateConverter) {
        this.objectMapper = objectMapper;
        this.huaweiAuthorizer = huaweiAuthorizer;
        this.conluzRestClientBuilder = conluzRestClientBuilder;
        this.dateConverter = dateConverter;
    }

    public List<HourlyProduction> getHourlyProductionByDateInterval(List<Plant> stations, OffsetDateTime startDate,
                                                                    OffsetDateTime endDate) {

        List<HourlyProduction> result = new ArrayList<>();

        if (stations == null || stations.isEmpty()) {
            LOGGER.debug("No Huawei stations provided.");
            return result;
        }

        String stationCodes = stations.stream()
                .map(Plant::getCode)
                .collect(Collectors.joining(", "));

        final String authToken = huaweiAuthorizer.getAuthToken();

        final OkHttpClient client = conluzRestClientBuilder.build();

        LOGGER.info("Getting hourly production from stations {} .", stations);

        OffsetDateTime currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            LOGGER.info("Processing day {} .", currentDate);
            result.addAll(getHourlyProductionByDay(stationCodes, currentDate, authToken, client));
            currentDate = currentDate.plusDays(1);
        }

        LOGGER.info("All stations and days processed.");

        return result;
    }

    private List<HourlyProduction> getHourlyProductionByDay(final String stationCodes, final OffsetDateTime day,
                                                            final String authToken, final OkHttpClient client) {

        List<HourlyProduction> result = new ArrayList<>();

        Map<String, Object> map = new HashMap<>();
        map.put(PARAM_STATION_CODES, stationCodes);
        map.put(PARAM_COLLECT_TIME, day.toInstant().toEpochMilli());
        RequestBody requestBody;
        try {
            requestBody = RequestBody.create(
                    objectMapper.writeValueAsString(map),
                    okhttp3.MediaType.parse(String.join(";", List.of(MediaType.APPLICATION_JSON_VALUE, "charset=UTF-8")))
            );
        } catch (JsonProcessingException e) {
            throw new HuaweiException("Error generating body to get hourly production", e);
        }

        final Request request = new Request.Builder()
                .url(URL)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HuaweiAuthorizer.TOKEN_HEADER, authToken)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.body() != null) {
                    String jsonData = response.body().string();
                    result.addAll(processBody(jsonData));
                }
            } else {
                LOGGER.error("Unable to get hourly production from stations {}. Code {}, message: {}",
                        stationCodes, response.code(),
                        response.body() != null ? response.body().string() : response.message());
            }
        } catch (IOException e) {
            LOGGER.error("Unable to get hourly production from stations {}", stationCodes, e);
        }

        return result;
    }

    private List<HourlyProduction> processBody(String body) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode dataNode = root.get("data");

        List<HourlyProduction> dataList = new ArrayList<>();

        JsonNode failCode = root.get("failCode");
        JsonNode success = root.get("success");
        if (failCode != null && failCode.asInt() == 407) {
            throw new HuaweiException("Request frequency is too high");
        } else if (!success.asBoolean()) {
            throw new HuaweiException("Calling Huawei API failed. Fail code: " + failCode + ", message: " + dataNode.asText());
        }

        if (!dataNode.isArray()) {
            return dataList;
        }

        for (JsonNode node : dataNode) {

            JsonNode dataItemMap = node.get("dataItemMap");

            long currentTime = node.get(PARAM_COLLECT_TIME).asLong();

            HourlyProduction item = new HourlyProduction.Builder()
                    .withTime(dateConverter.convertMillisecondsToInstant(currentTime))
                    .withStationCode(node.get("stationCode").asText())
                    .withInverterPower(dataItemMap.get(ProductionPoint.INVERTER_POWER) != null ? dataItemMap.get(ProductionPoint.INVERTER_POWER).asDouble() : null)
                    .withOngridPower(dataItemMap.get("ongrid_power") != null ? dataItemMap.get("ongrid_power").asDouble() : null)
                    .withPowerProfit(dataItemMap.get("power_profit") != null ? dataItemMap.get("power_profit").asDouble() : null)
                    .withTheoryPower(dataItemMap.get("theory_power") != null ? dataItemMap.get("theory_power").asDouble() : null)
                    .withRadiationIntensity(dataItemMap.get("radiation_intensity") != null ? dataItemMap.get("radiation_intensity").asDouble() : null)
                    .build();

            dataList.add(item);
        }

        return dataList;
    }
}
