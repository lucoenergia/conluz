package org.lucoenergia.conluz.infrastructure.shared.error;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorDetail;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ErrorBuilderTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00.123Z");

    /**
     * Stands for anything a refused request may carry into an exception's message, such as a value it sent.
     */
    private static final String VALUE_FROM_THE_REQUEST = "12345678Z";

    @Test
    void theTimestampOfAnErrorIsTheInstantOfTheApplicationClock_inTheJvmDefaultZone() {
        MessageSource messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(eq("error.key"), any(), any())).thenReturn("An error");
        ErrorBuilder errorBuilder = new ErrorBuilder(messageSource, Clock.fixed(NOW, ZoneOffset.UTC));

        RestError error = errorBuilder.build("ignored", "error.key", null, HttpStatus.BAD_REQUEST).getBody();

        assertEquals(NOW, error.getTimestamp().toInstant());
        assertEquals(ZoneId.systemDefault().getRules().getOffset(NOW), error.getTimestamp().getOffset());
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                Arguments.of(HttpStatus.BAD_REQUEST, Level.INFO),
                Arguments.of(HttpStatus.NOT_FOUND, Level.INFO),
                Arguments.of(HttpStatus.CONFLICT, Level.INFO),
                Arguments.of(HttpStatus.PAYLOAD_TOO_LARGE, Level.INFO),
                Arguments.of(HttpStatus.UNAUTHORIZED, Level.WARN),
                Arguments.of(HttpStatus.FORBIDDEN, Level.WARN),
                Arguments.of(HttpStatus.TOO_MANY_REQUESTS, Level.WARN)
        );
    }

    @ParameterizedTest
    @MethodSource("refusals")
    void aRefusalIsOneLineWithoutStackTrace_atTheLevelOfItsStatus(HttpStatus status, Level level) {
        ErrorBuilder errorBuilder = errorBuilder();

        assertOneLine(level, () -> errorBuilder.build("A refusal", status));
        assertOneLine(level, () -> errorBuilder.build("A refusal", RestErrorCode.USER_ALREADY_EXISTS, null, status));
        assertOneLine(level, () -> errorBuilder.build("ignored", "error.key", null, status));
        assertOneLine(level, () -> errorBuilder.build("A refusal", List.of(new RestErrorDetail("A refusal", null, null)), status));
    }

    @ParameterizedTest
    @MethodSource("refusals")
    void aRefusedException_isLoggedByItsTypeOnly_withoutItsMessageOrStackTrace(HttpStatus status, Level level) {
        ErrorBuilder errorBuilder = errorBuilder();

        ILoggingEvent event = assertOneLine(level, () -> errorBuilder.build(
                new IllegalStateException(VALUE_FROM_THE_REQUEST), "error.key", null, status));

        assertThat(event.getFormattedMessage()).contains("IllegalStateException").doesNotContain(VALUE_FROM_THE_REQUEST);
    }

    @Test
    void aServerError_isLoggedAtErrorWithItsStackTrace() {
        ErrorBuilder errorBuilder = errorBuilder();

        try (LogCapture logs = LogCapture.of(ErrorBuilder.class)) {
            errorBuilder.build(new IllegalStateException("Broken"), "error.key", null, HttpStatus.INTERNAL_SERVER_ERROR);

            assertThat(logs.all()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getThrowableProxy()).isNotNull();
            });
        }
    }

    @Test
    void aBrokenInvariant_isLoggedAtErrorWithItsStackTrace_althoughItIsAnsweredWithA4xx() {
        ErrorBuilder errorBuilder = errorBuilder();

        try (LogCapture logs = LogCapture.of(ErrorBuilder.class)) {
            RestError error = errorBuilder.buildForBrokenInvariant(new IllegalArgumentException("Broken"),
                    "error.key", null, HttpStatus.BAD_REQUEST).getBody();

            assertEquals(400, error.getStatus());
            assertThat(logs.all()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getThrowableProxy()).isNotNull();
            });
        }
    }

    @Test
    void aRefusalAnsweredWithoutLogging_logsNothing() {
        ErrorBuilder errorBuilder = errorBuilder();

        try (LogCapture logs = LogCapture.of(ErrorBuilder.class)) {
            errorBuilder.buildWithoutLogging("error.key", null, HttpStatus.UNAUTHORIZED);
            errorBuilder.buildWithoutLogging("A refusal", RestErrorCode.USER_ALREADY_EXISTS, null, HttpStatus.CONFLICT);

            assertThat(logs.all()).isEmpty();
        }
    }

    private static ILoggingEvent assertOneLine(Level level, Runnable build) {
        try (LogCapture logs = LogCapture.of(ErrorBuilder.class)) {
            build.run();

            List<ILoggingEvent> events = logs.all();
            assertThat(events).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(level);
                assertThat(event.getThrowableProxy()).isNull();
            });
            return events.get(0);
        }
    }

    private static ErrorBuilder errorBuilder() {
        MessageSource messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(eq("error.key"), any(), any())).thenReturn("An error");
        return new ErrorBuilder(messageSource, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
