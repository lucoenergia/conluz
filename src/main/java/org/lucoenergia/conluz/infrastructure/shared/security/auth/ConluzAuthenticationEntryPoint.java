package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * CustomAuthenticationEntryPoint is an implementation of Spring Security's
 * {@link org.springframework.security.web.AuthenticationEntryPoint} interface.
 * It provides custom handling for authentication failures in a pure REST API.
 * When an authentication failure occurs, this class sends a custom response with
 * a status code of 401 (Unauthorized) and a detailed error message to the client.
 *
 * <p>
 * Usage:
 * <pre>
 * {@code
 * @Bean
 * public AuthenticationEntryPoint authenticationEntryPoint() {
 *     return new CustomAuthenticationEntryPoint();
 * }
 * }
 * </pre>
 *
 * <p>
 * This class is typically configured in a Spring Security configuration class
 * to define the behavior when authentication fails for RESTful services.
 * </p>
 *
 * @see org.springframework.security.web.AuthenticationEntryPoint
 */
public class ConluzAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final ErrorBuilder errorBuilder;

    public ConluzAuthenticationEntryPoint(ObjectMapper objectMapper, ErrorBuilder errorBuilder) {
        this.objectMapper = objectMapper;
        this.errorBuilder = errorBuilder;
    }

    /**
     * Invoked when an authentication failure occurs. Sends a custom response
     * to the client with a status code of 401 (Unauthorized) and a detailed error message.
     *
     * @param request       the request being handled
     * @param response      the response to be populated
     * @param exception the exception that caused the authentication failure
     * @throws IOException      if an I/O error occurs
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {

        // Customize the response when authentication fails
        String errorMessage = "Authentication Failed: " + exception.getMessage();

        ResponseEntity<RestError> entity = errorBuilder.build(errorMessage, HttpStatus.UNAUTHORIZED);

        response.setStatus(entity.getStatusCode().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(entity.getBody()));
    }
}
