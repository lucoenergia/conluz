package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.auth.TokenRejectionReason;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A token or authentication refused once the request has reached a controller is one warning, without a stack trace
 * or any part of the token (#479).
 */
class AuthenticationExceptionHandlerTest {

    /**
     * Shaped like a token: three Base64URL segments, built here rather than written out.
     */
    private static final String TOKEN = String.join(".", base64Url("{\"alg\":\"HS256\"}"),
            base64Url("{\"sub\":\"someone\"}"), base64Url("signature"));

    private final AuthenticationExceptionHandler handler = handler();

    @Test
    void anInvalidToken_isOneWarningWithItsReason_andTheTraceIdOfTheResponse() {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");

        try (LogCapture logs = LogCapture.ofRoot()) {
            ResponseEntity<RestError> response = handler.handleInvalidTokenException(
                    new InvalidTokenException(TokenRejectionReason.REVOKED, userId), request);

            assertThat(response.getStatusCode().value()).isEqualTo(401);
            ILoggingEvent event = assertOneWarningWithoutTokenOrStackTrace(logs.allOfThisThread());
            assertThat(event.getFormattedMessage()).isEqualTo("Token rejected: reason=REVOKED, user=" + userId
                    + ", ip=203.0.113.7, traceId=" + response.getBody().getTraceId());
        }
    }

    @Test
    void aJwtException_isOneWarningWithoutItsMessage() {
        try (LogCapture logs = LogCapture.ofRoot()) {
            ResponseEntity<RestError> response = handler.handleJwtException(
                    new MalformedJwtException("Malformed token: " + TOKEN));

            assertThat(response.getStatusCode().value()).isEqualTo(401);
            assertOneWarningWithoutTokenOrStackTrace(logs.allOfThisThread());
        }
    }

    @Test
    void anAuthenticationException_isOneWarning() {
        try (LogCapture logs = LogCapture.ofRoot()) {
            ResponseEntity<RestError> response = handler.handleAuthenticationException(
                    new InsufficientAuthenticationException("Bearer " + TOKEN));

            assertThat(response.getStatusCode().value()).isEqualTo(401);
            assertOneWarningWithoutTokenOrStackTrace(logs.allOfThisThread());
        }
    }

    private static ILoggingEvent assertOneWarningWithoutTokenOrStackTrace(List<ILoggingEvent> events) {
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getThrowableProxy()).isNull();
            for (String segment : TOKEN.split("\\.")) {
                assertThat(event.getFormattedMessage()).doesNotContain(segment);
            }
        });
        return events.get(0);
    }

    private static String base64Url(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static AuthenticationExceptionHandler handler() {
        MessageSource messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(eq("error.unauthorized"), any(), any())).thenReturn("Unauthorized");
        return new AuthenticationExceptionHandler(new ErrorBuilder(messageSource, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)), messageSource);
    }
}
