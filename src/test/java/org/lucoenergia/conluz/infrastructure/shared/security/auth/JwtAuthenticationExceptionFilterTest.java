package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

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
        InvalidTokenException invalidTokenException = new InvalidTokenException("Invalid token");
        RestError restError = new RestError(HttpStatus.UNAUTHORIZED.value(), "Invalid token error");
        ResponseEntity<RestError> errorResponse = new ResponseEntity<>(restError, HttpStatus.UNAUTHORIZED);

        when(authenticationExceptionHandler.handleInvalidTokenException(invalidTokenException)).thenReturn(errorResponse);
        when(response.getOutputStream()).thenReturn(mock(ServletOutputStream.class));

        doThrow(invalidTokenException).when(filterChain).doFilter(request, response);

        // Act
        jwtAuthenticationExceptionFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(authenticationExceptionHandler).handleInvalidTokenException(invalidTokenException);
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