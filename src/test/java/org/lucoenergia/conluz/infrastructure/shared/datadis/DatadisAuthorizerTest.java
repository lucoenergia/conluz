package org.lucoenergia.conluz.infrastructure.shared.datadis;

import okhttp3.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.RestClientBuilder;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DatadisAuthorizerTest {

    private DatadisAuthorizer datadisAuthorizer;
    private DatadisConfigRepository datadisConfigRepository;
    private RestClientBuilder restClientBuilder;

    @BeforeEach
    public void setUp() {
        restClientBuilder = Mockito.mock(RestClientBuilder.class);
        datadisConfigRepository = Mockito.mock(DatadisConfigRepository.class);
        datadisAuthorizer = new DatadisAuthorizer(datadisConfigRepository, restClientBuilder);
    }

    @Test
    void testGetAuthTokenWithBadCredentials() throws IOException {

        // Assemble
        final String username = "wrong_user";
        final String password = "wrong_password";

        final DatadisConfig config = new DatadisConfig();
        config.setId(UUID.randomUUID());
        config.setUsername(username);
        config.setPassword(password);
        Mockito.when(datadisConfigRepository.findFirstByOrderByIdAsc()).thenReturn(config);

        OkHttpClient client = Mockito.mock(OkHttpClient.class);
        Call call = Mockito.mock(Call.class);
        Response response = Mockito.mock(Response.class);
        ResponseBody body = Mockito.mock(ResponseBody.class);
        Mockito
                .when(restClientBuilder.build())
                .thenReturn(client);
        Mockito.when(client.newCall(Mockito.any(Request.class))).thenReturn(call);
        Mockito.when(call.execute()).thenReturn(response);
        Mockito.when(response.isSuccessful()).thenReturn(false);
        Mockito.when(response.code()).thenReturn(500);
        Mockito.when(response.body()).thenReturn(body);
        Mockito.when(body.string()).thenReturn("""
                {
                    "timestamp": "2024-02-14T13:49:09.918Z",
                    "status": 500,
                    "error": "Internal Server Error",
                    "message": "Bad credentials"
                }
                """);

        // Act and Assert
        final Throwable exception = assertThrows(DatadisException.class, () -> datadisAuthorizer.getAuthToken());
        Assertions.assertTrue(exception.getMessage().startsWith("""
                Unable to get auth token from datadis.es. Code: 500, message: {
                    "timestamp": "2024-02-14T13:49:09.918Z",
                    "status": 500,
                    "error": "Internal Server Error",
                    "message": "Bad credentials"
                }
                """));
    }
}
