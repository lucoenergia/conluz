package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationExceptionFilter extends OncePerRequestFilter {

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
            handleInvalidTokenException(response, e);
        }
    }

    private void handleInvalidTokenException(HttpServletResponse response, InvalidTokenException exception) throws IOException {
        ResponseEntity<RestError> errorResponse = authenticationExceptionHandler.handleInvalidTokenException(exception);

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), errorResponse.getBody());
    }
}