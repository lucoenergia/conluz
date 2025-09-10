package org.lucoenergia.conluz.infrastructure.shared.error;

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
import java.util.List;

@Component
public class GlobalExceptionFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionFilter.class);
    
    private final ObjectMapper objectMapper;
    private final ErrorBuilder errorBuilder;

    public GlobalExceptionFilter(ObjectMapper objectMapper, ErrorBuilder errorBuilder) {
        this.objectMapper = objectMapper;
        this.errorBuilder = errorBuilder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            // Skip handling if response is already committed
            if (response.isCommitted()) {
                LOGGER.warn("Response already committed, cannot handle exception: {}", e.getMessage());
                throw e;
            }
            
            handleGenericException(response, e);
        }
    }

    private void handleGenericException(HttpServletResponse response, Exception exception) throws IOException {

        final ResponseEntity<RestError> errorResponse = errorBuilder.build(exception, "error.unexpected",
                List.of().toArray(), HttpStatus.INTERNAL_SERVER_ERROR);

        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), errorResponse.getBody());
    }
}
