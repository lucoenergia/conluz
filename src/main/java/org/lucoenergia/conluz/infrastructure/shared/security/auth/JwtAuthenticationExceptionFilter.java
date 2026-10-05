package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class JwtAuthenticationExceptionFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationExceptionFilter.class);

    private final ObjectMapper objectMapper;
    private final AuthenticationExceptionHandler authenticationExceptionHandler;

    public JwtAuthenticationExceptionFilter(ObjectMapper objectMapper, AuthenticationExceptionHandler authenticationExceptionHandler) {
        this.objectMapper = objectMapper;
        this.authenticationExceptionHandler = authenticationExceptionHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (InvalidTokenException e) {
            handleInvalidTokenException(request, response, e);
        }
    }

    /**
     * The one place a rejected token is logged: a single warning, without a stack trace, carrying the trace id of
     * the response so that the two can be matched. The user is named only when the token's signature was verified.
     */
    private void handleInvalidTokenException(HttpServletRequest request, HttpServletResponse response,
                                             InvalidTokenException exception) throws IOException {
        ResponseEntity<RestError> errorResponse = authenticationExceptionHandler.handleRejectedToken();
        LOGGER.warn("Token rejected: reason={}, user={}, ip={}, traceId={}", exception.getReason(),
                exception.getUserId().map(UUID::toString).orElse("unknown"), request.getRemoteAddr(),
                errorResponse.getBody().getTraceId());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), errorResponse.getBody());
    }
}