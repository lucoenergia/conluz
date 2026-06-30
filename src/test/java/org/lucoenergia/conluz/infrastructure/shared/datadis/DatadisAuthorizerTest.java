package org.lucoenergia.conluz.infrastructure.shared.datadis;

import okhttp3.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisException;
import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConfigRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisAuthorizer;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatadisAuthorizerTest {

    private DatadisAuthorizer datadisAuthorizer;
    private GetDatadisConfigRepository getDatadisConfigRepository;
    private ConluzRestClientBuilder conluzRestClientBuilder;

    @BeforeEach
    public void setUp() {
        conluzRestClientBuilder = Mockito.mock(ConluzRestClientBuilder.class);
        getDatadisConfigRepository = Mockito.mock(GetDatadisConfigRepository.class);
        datadisAuthorizer = new DatadisAuthorizer(getDatadisConfigRepository, conluzRestClientBuilder);
    }

    @Test
    void testGetAuthTokenWithBadCredentials() throws IOException {

        // Assemble
        final String username = "wrong_user";
        final String password = "wrong_password";

        final DatadisConfig config = new DatadisConfig.Builder()
                .setUsername(username)
                .setPassword(password)
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .build();
        Mockito.when(getDatadisConfigRepository.getDatadisConfig()).thenReturn(Optional.of(config));

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
                Unable to get auth token from datadis. Code: 500, message: {
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
        GetDatadisConfigRepository getDatadisConfigRepository = mock(GetDatadisConfigRepository.class);
        ConluzRestClientBuilder conluzRestClientBuilder = mock(ConluzRestClientBuilder.class);

        when(getDatadisConfigRepository.getDatadisConfig()).thenReturn(Optional.empty());

        // invocation
        DatadisAuthorizer datadisAuthorizer = new DatadisAuthorizer(getDatadisConfigRepository, conluzRestClientBuilder);

        // verification and assertion
        Assertions.assertThrows(DatadisException.class, datadisAuthorizer::getAuthToken);
    }

    @Test
    void testRequiresAuthorizedNifResolvesConfigFromRepository() {
        final DatadisConfig config = new DatadisConfig.Builder()
                .setUsername("accountNif")
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .build();
        when(getDatadisConfigRepository.getDatadisConfig()).thenReturn(Optional.of(config));

        // The owner NIF matches the account, so no authorizedNif is required.
        Assertions.assertFalse(datadisAuthorizer.requiresAuthorizedNif(UserPersonalId.of("accountNif")));
        // A different owner NIF requires the authorizedNif.
        Assertions.assertTrue(datadisAuthorizer.requiresAuthorizedNif(UserPersonalId.of("otherNif")));
    }

    @Test
    void testRequiresAuthorizedNifIgnoresCase() {
        givenConfigWithUsername("12345678Z");

        // Same NIF differing only by letter case is still the owner.
        Assertions.assertFalse(datadisAuthorizer.requiresAuthorizedNif(UserPersonalId.of("12345678z")));
    }

    @Test
    void testRequiresAuthorizedNifIgnoresSurroundingWhitespace() {
        givenConfigWithUsername("12345678Z");

        // Same NIF with leading/trailing whitespace is still the owner.
        Assertions.assertFalse(datadisAuthorizer.requiresAuthorizedNif(UserPersonalId.of(" 12345678Z ")));
    }

    @Test
    void testRequiresAuthorizedNifIgnoresCaseAndWhitespace() {
        givenConfigWithUsername("12345678Z");

        // Same NIF differing by both case and surrounding whitespace is still the owner.
        Assertions.assertFalse(datadisAuthorizer.requiresAuthorizedNif(UserPersonalId.of("  12345678z  ")));
    }

    @Test
    void testRequiresAuthorizedNifForDifferentNif() {
        givenConfigWithUsername("12345678Z");

        // A genuinely different NIF still requires the authorizedNif.
        Assertions.assertTrue(datadisAuthorizer.requiresAuthorizedNif(UserPersonalId.of("87654321X")));
    }

    private void givenConfigWithUsername(String username) {
        final DatadisConfig config = new DatadisConfig.Builder()
                .setUsername(username)
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .build();
        when(getDatadisConfigRepository.getDatadisConfig()).thenReturn(Optional.of(config));
    }
}
