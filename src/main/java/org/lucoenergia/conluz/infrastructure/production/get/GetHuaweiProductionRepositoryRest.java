package org.lucoenergia.conluz.infrastructure.production.get;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiAuthorizer;
import org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.infrastructure.production.huawei.RealTimeProduction;
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
public class GetHuaweiProductionRepositoryRest {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetHuaweiProductionRepositoryRest.class);

    private static final String URL = HuaweiConfig.BASE_URL + "/getStationRealKpi";
    private static final String PARAM_STATION_CODES = "stationCodes";

    private final ObjectMapper objectMapper;
    private final HuaweiAuthorizer huaweiAuthorizer;
    private final ConluzRestClientBuilder conluzRestClientBuilder;

    public GetHuaweiProductionRepositoryRest(ObjectMapper objectMapper, HuaweiAuthorizer huaweiAuthorizer, ConluzRestClientBuilder conluzRestClientBuilder) {
        this.objectMapper = objectMapper;
        this.huaweiAuthorizer = huaweiAuthorizer;
        this.conluzRestClientBuilder = conluzRestClientBuilder;
    }

    public List<RealTimeProduction> getRealTimeProduction(List<String> stationCodes) {

        List<RealTimeProduction> result = new ArrayList<>();

        if (stationCodes == null || stationCodes.isEmpty()) {
            return result;
        }

        final String authToken = huaweiAuthorizer.getAuthBearerToken();

        final OkHttpClient client = conluzRestClientBuilder.build();

        LOGGER.info("Getting real time production from stations {} .", stationCodes);

        // Create the complete URL with the query parameter
        final String url = UriComponentsBuilder.fromUriString(URL)
                .queryParam(PARAM_STATION_CODES, String.join(",", stationCodes))
                .build()
                .toUriString();

        final Request request = new Request.Builder()
                .url(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, authToken)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {

                String jsonData = response.body().string();

                result.addAll(processBody(jsonData));
            } else {
                LOGGER.error("Unable to get real-time production from stations {}. Code {}, message: {}",
                        stationCodes, response.code(),
                        response.body() != null ? response.body().string() : response.message());
            }
        } catch (IOException e) {
            LOGGER.error(String.format("Unable to get real-time production from stations %s", stationCodes), e);
        }

        LOGGER.info("Stations processed.");

        return result;
    }

    private List<RealTimeProduction> processBody(String body) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode dataNode = root.get("data");

        List<RealTimeProduction> dataList = new ArrayList<>();

        if (dataNode.isArray()) {
            for (JsonNode node : dataNode) {

                JsonNode dataItemMap = node.get("dataItemMap");

                RealTimeProduction item = new RealTimeProduction();
                item.setStationCode(node.get("stationCode").asText());
                item.setDayIncome(dataItemMap.get("day_income").asDouble());
                item.setDayPower(dataItemMap.get("day_power").asDouble());
                item.setMonthPower(dataItemMap.get("month_power").asDouble());
                item.setTotalPower(dataItemMap.get("total_power").asDouble());
                item.setTotalIncome(dataItemMap.get("total_income").asDouble());
                item.setRealHealthState(dataItemMap.get("real_health_state").asInt());

                dataList.add(item);
            }
        }

        return dataList;
    }
}