package org.lucoenergia.conluz.infrastructure.shared.error;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionFilterTest {

    @Test
    void anUnexpectedFailure_isAnswered500_andLoggedOnceAtErrorWithItsStackTrace() throws Exception {
        MessageSource messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(eq("error.unexpected"), any(), any())).thenReturn("Unexpected error");
        GlobalExceptionFilter filter = new GlobalExceptionFilter(new ObjectMapper().registerModule(new JavaTimeModule()),
                new ErrorBuilder(messageSource, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (LogCapture logs = LogCapture.ofRoot()) {
            filter.doFilter(new MockHttpServletRequest(), response, (request, ignored) -> {
                throw new IllegalStateException("Broken");
            });

            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(logs.allOfThisThread()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getThrowableProxy()).isNotNull();
                assertThat(event.getThrowableProxy().getMessage()).isEqualTo("Broken");
            });
        }
    }
}
