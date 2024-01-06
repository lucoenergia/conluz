package org.lucoenergia.conluz.infrastructure.shared.web.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;
import java.io.PrintWriter;

class ConluzAccessDeniedHandlerTest {

    private ConluzAccessDeniedHandler handler;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        objectMapper = Mockito.mock(ObjectMapper.class);
        handler = new ConluzAccessDeniedHandler(objectMapper);
    }

    @Test
    void testHandleWithAccessDeniedException() throws IOException {
        final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        final HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        final PrintWriter writer = Mockito.mock(PrintWriter.class);
        Mockito.when(response.getWriter()).thenReturn(writer);

        String errorBody = """
                {
                    "timestamp": "2024-01-06T17:44:36.962751794+01:00",
                    "status": 401,
                    "message": "Access Denied: This operation is not allowed.",
                    "traceId": "a7845016-46af-4f33-8c11-a83fa8620bd6"
                }
                """;
        Mockito.when(objectMapper.writeValueAsString(Mockito.any())).thenReturn(errorBody);

        final AccessDeniedException exception =
                new AccessDeniedException("This operation is not allowed.");

        handler.handle(request, response, exception);

        Mockito.verify(response).setStatus(403);
        Mockito.verify(response).setContentType("application/json");
        Mockito.verify(writer).write(errorBody);
    }
}
