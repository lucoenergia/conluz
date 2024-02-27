package org.lucoenergia.conluz.infrastructure.shared.web.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Custom implementation of {@link org.springframework.security.web.access.AccessDeniedHandler}
 * to handle access denied situations and return a custom message.
 *
 * <p>
 * This class extends the default Spring Security AccessDeniedHandler to provide a customized
 * response when access is denied for a particular resource or operation. It allows specifying
 * a custom message to be included in the response, providing more meaningful information to the user
 * about the reason for access denial.
 * </p>
 *
 * @see org.springframework.security.web.access.AccessDeniedHandler
 */
public class ConluzAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ConluzAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Handles access denied situation by returning a custom message in the response.
     *
     * @param request  the HttpServletRequest representing the denied request.
     * @param response the HttpServletResponse to be modified to include the custom message.
     * @param accessDeniedException the exception that caused the access denial.
     * @throws IOException in case of an I/O error while writing the response.
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        // Customize the response when authentication fails
        String errorMessage = "Access denied: " + accessDeniedException.getMessage();

        ResponseEntity<RestError> entity = new ResponseEntity<>(new RestError(HttpStatus.FORBIDDEN.value(),
                errorMessage), HttpStatus.FORBIDDEN);

        response.setStatus(entity.getStatusCode().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(entity.getBody()));
    }
}
