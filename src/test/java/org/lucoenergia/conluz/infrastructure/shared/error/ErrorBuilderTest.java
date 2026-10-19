package org.lucoenergia.conluz.infrastructure.shared.error;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ErrorBuilderTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00.123Z");

    @Test
    void theTimestampOfAnErrorIsTheInstantOfTheApplicationClock_inTheJvmDefaultZone() {
        MessageSource messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(eq("error.key"), any(), any())).thenReturn("An error");
        ErrorBuilder errorBuilder = new ErrorBuilder(messageSource, Clock.fixed(NOW, ZoneOffset.UTC));

        RestError error = errorBuilder.build("ignored", "error.key", null, HttpStatus.BAD_REQUEST).getBody();

        assertEquals(NOW, error.getTimestamp().toInstant());
        assertEquals(ZoneId.systemDefault().getRules().getOffset(NOW), error.getTimestamp().getOffset());
    }
}
