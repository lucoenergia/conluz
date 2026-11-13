package org.lucoenergia.conluz.infrastructure.production.get;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiAuthorizer;
import org.lucoenergia.conluz.infrastructure.production.huawei.get.GetHuaweiRealTimeProductionRepositoryRest;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Station codes identify a customer's installation: the realtime sync's INFO line states only how
 * many stations it reads, while an ERROR line keeps the codes of the stations that failed (#570).
 */
class GetHuaweiRealTimeProductionRepositoryRestLogTest {

    private static final String FIRST_STATION = "BA4372D08E014822AB065017416F254C";
    private static final String SECOND_STATION = "5D02E8B40AD342159AC8D8A2BCD4FAB5";

    private final HuaweiAuthorizer huaweiAuthorizer = Mockito.mock(HuaweiAuthorizer.class);
    private final ConluzRestClientBuilder conluzRestClientBuilder = Mockito.mock(ConluzRestClientBuilder.class);
    private final TimeConfiguration timeConfiguration = Mockito.mock(TimeConfiguration.class);
    private final Response response = Mockito.mock(Response.class);
    private final ResponseBody responseBody = Mockito.mock(ResponseBody.class);

    private final List<Plant> stations = List.of(
            new Plant.Builder().withProviderCode(FIRST_STATION).build(),
            new Plant.Builder().withProviderCode(SECOND_STATION).build());

    private GetHuaweiRealTimeProductionRepositoryRest repositoryRest;

    @BeforeEach
    void setUp() throws IOException {
        repositoryRest = new GetHuaweiRealTimeProductionRepositoryRest(new ObjectMapper(), huaweiAuthorizer,
                conluzRestClientBuilder, new DateConverter(timeConfiguration));
        when(timeConfiguration.getZoneId()).thenReturn(ZoneId.of("Europe/Madrid"));
        when(huaweiAuthorizer.getAuthToken()).thenReturn("TOKEN");

        OkHttpClient client = Mockito.mock(OkHttpClient.class);
        Call call = Mockito.mock(Call.class);
        when(conluzRestClientBuilder.build()).thenReturn(client);
        when(client.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.body()).thenReturn(responseBody);
    }

    @Test
    void theInfoLineStatesHowManyStationsAreReadAndNoStationCode() throws IOException {
        when(response.isSuccessful()).thenReturn(true);
        when(responseBody.string()).thenReturn("{\"success\":true,\"data\":[],\"failCode\":0,"
                + "\"params\":{\"currentTime\":1750627686362},\"message\":null}");

        try (LogCapture logs = LogCapture.of(GetHuaweiRealTimeProductionRepositoryRest.class)) {
            repositoryRest.getRealTimeProduction(stations, HuaweiConfig.DEFAULT_BASE_URL);

            List<String> lines = messages(logs, Level.INFO, "Getting real time production");
            assertEquals(List.of("Getting real time production from 2 stations"), lines);
            for (ILoggingEvent event : logs.all()) {
                if (event.getLevel() == Level.INFO) {
                    String message = event.getFormattedMessage();
                    assertFalse(message.contains(FIRST_STATION) || message.contains(SECOND_STATION),
                            () -> "An INFO line names a station: " + message);
                }
            }
        }
    }

    @Test
    void anErrorLineForAFailedReadNamesTheFailingStations() throws IOException {
        when(response.isSuccessful()).thenReturn(false);
        when(response.code()).thenReturn(500);
        when(responseBody.string()).thenReturn("Internal error");

        try (LogCapture logs = LogCapture.of(GetHuaweiRealTimeProductionRepositoryRest.class)) {
            repositoryRest.getRealTimeProduction(stations, HuaweiConfig.DEFAULT_BASE_URL);

            List<String> lines = messages(logs, Level.ERROR, "Unable to get real-time production");
            assertEquals(1, lines.size(), () -> "ERROR lines were: " + lines);
            assertTrue(lines.get(0).contains(FIRST_STATION) && lines.get(0).contains(SECOND_STATION),
                    () -> "The ERROR line does not name the failing stations: " + lines.get(0));
        }
    }

    private static List<String> messages(LogCapture logs, Level level, String prefix) {
        return logs.all().stream()
                .filter(event -> event.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith(prefix))
                .toList();
    }
}
