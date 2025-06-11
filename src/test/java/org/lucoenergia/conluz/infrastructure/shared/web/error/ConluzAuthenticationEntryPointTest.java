package org.lucoenergia.conluz.infrastructure.shared.web.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.ConluzAuthenticationEntryPoint;
import org.mockito.Mockito;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import java.io.IOException;
import java.io.PrintWriter;

class ConluzAuthenticationEntryPointTest {

    private ConluzAuthenticationEntryPoint entryPoint;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        objectMapper = Mockito.mock(ObjectMapper.class);
        entryPoint = new ConluzAuthenticationEntryPoint(objectMapper);
    }

    @Test
    void testCommenceWithInsufficientAuthenticationException() throws IOException {
        final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        final HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        final PrintWriter writer = Mockito.mock(PrintWriter.class);
        Mockito.when(response.getWriter()).thenReturn(writer);

        String errorBody = """
                {
                    "timestamp": "2024-01-06T17:44:36.962751794+01:00",
                    "status": 401,
                    "message": "Authentication Failed: Full authentication is required to access this resource",
                    "traceId": "a7845016-46af-4f33-8c11-a83fa8620bd6"
                }
                """;
        Mockito.when(objectMapper.writeValueAsString(Mockito.any())).thenReturn(errorBody);

        final InsufficientAuthenticationException exception =
                new InsufficientAuthenticationException("Full authentication is required to access this resource");

        entryPoint.commence(request, response, exception);

        Mockito.verify(response).setStatus(401);
        Mockito.verify(response).setContentType("application/json");
        Mockito.verify(writer).write(errorBody);
    }
}
