package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.auth.TokenRejectionReason;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class JwtAuthenticationExceptionFilterTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AuthenticationExceptionHandler authenticationExceptionHandler;

    @InjectMocks
    private JwtAuthenticationExceptionFilter jwtAuthenticationExceptionFilter;

    public JwtAuthenticationExceptionFilterTest() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldHandleInvalidTokenException() throws ServletException, IOException {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        UUID userId = UUID.randomUUID();
        InvalidTokenException invalidTokenException = new InvalidTokenException(TokenRejectionReason.USER_DISABLED,
                userId);
        when(request.getRemoteAddr()).thenReturn("203.0.113.7");
        RestError restError = new RestError(HttpStatus.UNAUTHORIZED.value(), "Invalid token error",
                UUID.randomUUID().toString());
        ResponseEntity<RestError> errorResponse = new ResponseEntity<>(restError, HttpStatus.UNAUTHORIZED);

        when(authenticationExceptionHandler.handleRejectedToken()).thenReturn(errorResponse);
        when(response.getOutputStream()).thenReturn(mock(ServletOutputStream.class));

        doThrow(invalidTokenException).when(filterChain).doFilter(request, response);

        // Act
        List<ILoggingEvent> events;
        try (LogCapture logs = LogCapture.of(JwtAuthenticationExceptionFilter.class)) {
            jwtAuthenticationExceptionFilter.doFilterInternal(request, response, filterChain);
            events = logs.allOfThisThread();
        }

        // Assert
        verify(authenticationExceptionHandler).handleRejectedToken();
        verify(authenticationExceptionHandler, never()).handleInvalidTokenException(any());
        assertEquals(1, events.size());
        assertEquals(Level.WARN, events.get(0).getLevel());
        assertEquals("Token rejected: reason=USER_DISABLED, user=" + userId + ", ip=203.0.113.7, traceId="
                + restError.getTraceId(), events.get(0).getFormattedMessage());
        assertNull(events.get(0).getThrowableProxy());
        verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        verify(objectMapper).writeValue(any(ServletOutputStream.class), eq(restError));
    }

    @Test
    void shouldPassThroughOnNoException() throws ServletException, IOException {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        // Act
        jwtAuthenticationExceptionFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(authenticationExceptionHandler, objectMapper);
    }
}