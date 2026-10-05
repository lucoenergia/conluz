package org.lucoenergia.conluz.infrastructure.shared.web.clientaddress;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.AuthenticationThrottleServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which client address a request is attributed to, on a real embedded Tomcat: {@code RemoteIpValve}, which
 * resolves the address from {@code X-Forwarded-For}, does not run under MockMvc.
 * <p>
 * The test client always connects from {@code 127.0.0.1}, an address Tomcat would trust as a proxy by default, and
 * claims to be forwarding for {@link #FORWARDED_CLIENT}. The address is observed in the warning logged for a failed
 * login.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class BaseClientAddressTest extends BaseIntegrationTest {

    static final String PEER = "127.0.0.1";
    static final String FORWARDED_CLIENT = "203.0.113.7";

    @LocalServerPort
    private int port;

    String addressOfAFailedLoginForwardedFor(String forwardedFor) throws Exception {
        String body = "{\"username\": \"" + UserMother.randomPersonalId() + "\", \"password\": \"wrong password\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + PEER + ":" + port + "/api/v1/login"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try (LogCapture logs = LogCapture.of(AuthenticationThrottleServiceImpl.class)) {
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, response.statusCode());

            List<ILoggingEvent> warnings = logs.warningsAndAbove();
            assertEquals(1, warnings.size());
            String message = warnings.get(0).getFormattedMessage();
            return message.substring(message.indexOf("ip=") + "ip=".length(), message.indexOf(", reason="));
        }
    }
}
