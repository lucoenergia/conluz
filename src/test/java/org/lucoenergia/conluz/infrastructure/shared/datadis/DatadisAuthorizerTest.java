package org.lucoenergia.conluz.infrastructure.shared.datadis;

import okhttp3.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.datadis.DatadisException;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.get.GetDatadisConfigRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisAuthorizer;
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
    void testRequiresAuthorizedNifComparesWithTheGivenConfiguration() {
        final DatadisConfig config = configWithUsername("accountNif");

        // The owner NIF matches the account, so no authorizedNif is required.
        Assertions.assertFalse(datadisAuthorizer.requiresAuthorizedNif(config, UserPersonalId.of("accountNif")));
        // A different owner NIF requires the authorizedNif.
        Assertions.assertTrue(datadisAuthorizer.requiresAuthorizedNif(config, UserPersonalId.of("otherNif")));
    }

    @Test
    void testRequiresAuthorizedNifIgnoresCase() {
        // Same NIF differing only by letter case is still the owner.
        Assertions.assertFalse(datadisAuthorizer.requiresAuthorizedNif(configWithUsername("12345678Z"),
                UserPersonalId.of("12345678z")));
    }

    @Test
    void testRequiresAuthorizedNifIgnoresSurroundingWhitespace() {
        // Same NIF with leading/trailing whitespace is still the owner.
        Assertions.assertFalse(datadisAuthorizer.requiresAuthorizedNif(configWithUsername("12345678Z"),
                UserPersonalId.of(" 12345678Z ")));
    }

    @Test
    void testRequiresAuthorizedNifIgnoresCaseAndWhitespace() {
        // Same NIF differing by both case and surrounding whitespace is still the owner.
        Assertions.assertFalse(datadisAuthorizer.requiresAuthorizedNif(configWithUsername("12345678Z"),
                UserPersonalId.of("  12345678z  ")));
    }

    @Test
    void testRequiresAuthorizedNifForDifferentNif() {
        // A genuinely different NIF still requires the authorizedNif.
        Assertions.assertTrue(datadisAuthorizer.requiresAuthorizedNif(configWithUsername("12345678Z"),
                UserPersonalId.of("87654321X")));
    }

    @Test
    @DisplayName("DSS-004 the owner of one community's Datadis account is not the owner for another community's account")
    void testRequiresAuthorizedNifDependsOnTheConfiguration() {
        UserPersonalId owner = UserPersonalId.of("12345678Z");

        Assertions.assertFalse(datadisAuthorizer.requiresAuthorizedNif(configWithUsername("12345678Z"), owner));
        Assertions.assertTrue(datadisAuthorizer.requiresAuthorizedNif(configWithUsername("87654321X"), owner));
        // The decision never looks the configuration up
        Mockito.verifyNoInteractions(getDatadisConfigRepository);
    }

    private DatadisConfig configWithUsername(String username) {
        return new DatadisConfig.Builder()
                .setUsername(username)
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .build();
    }
}
