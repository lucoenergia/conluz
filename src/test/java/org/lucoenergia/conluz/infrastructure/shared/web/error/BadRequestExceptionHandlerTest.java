package org.lucoenergia.conluz.infrastructure.shared.web.error;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BadRequestExceptionHandlerTest {

    @Test
    void anIllegalArgument_isAnswered400_butLoggedAtErrorWithItsStackTrace_asTheBrokenInvariantItIs() {
        MessageSource messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(eq("error.invalid.arguments"), any(), any())).thenReturn("Invalid arguments.");
        BadRequestExceptionHandler handler = new BadRequestExceptionHandler(messageSource,
                new ErrorBuilder(messageSource, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));

        try (LogCapture logs = LogCapture.ofRoot()) {
            ResponseEntity<RestError> response = handler.handleException(new IllegalArgumentException("Broken"));

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(logs.allOfThisThread()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getThrowableProxy()).isNotNull();
            });
        }
    }
}
