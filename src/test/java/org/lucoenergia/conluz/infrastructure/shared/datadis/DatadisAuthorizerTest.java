package org.lucoenergia.conluz.infrastructure.shared.datadis;

import okhttp3.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisAuthorizer;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConfigRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisException;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatadisAuthorizerTest {

    private DatadisAuthorizer datadisAuthorizer;
    private DatadisConfigRepository datadisConfigRepository;
    private ConluzRestClientBuilder conluzRestClientBuilder;

    @BeforeEach
    public void setUp() {
        conluzRestClientBuilder = Mockito.mock(ConluzRestClientBuilder.class);
        datadisConfigRepository = Mockito.mock(DatadisConfigRepository.class);
        datadisAuthorizer = new DatadisAuthorizer(datadisConfigRepository, conluzRestClientBuilder);
    }

    @Test
    void testGetAuthTokenWithBadCredentials() throws IOException {

        // Assemble
        final String username = "wrong_user";
        final String password = "wrong_password";

        final DatadisConfigEntity config = new DatadisConfigEntity();
        config.setId(UUID.randomUUID());
        config.setUsername(username);
        config.setPassword(password);
        Mockito.when(datadisConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));

        OkHttpClient client = Mockito.mock(OkHttpClient.class);
        Call call = Mockito.mock(Call.class);
        Response response = Mockito.mock(Response.class);
        ResponseBody body = Mockito.mock(ResponseBody.class);
        Mockito
                .when(conluzRestClientBuilder.build())
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

    @Test
    void testGetAuthTokenNoConfig() {
        // setup
        DatadisConfigRepository datadisConfigRepository = mock(DatadisConfigRepository.class);
        ConluzRestClientBuilder conluzRestClientBuilder = mock(ConluzRestClientBuilder.class);

        when(datadisConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        // invocation
        DatadisAuthorizer datadisAuthorizer = new DatadisAuthorizer(datadisConfigRepository, conluzRestClientBuilder);

        // verification and assertion
        Assertions.assertThrows(DatadisException.class, datadisAuthorizer::getAuthToken);
    }
}
