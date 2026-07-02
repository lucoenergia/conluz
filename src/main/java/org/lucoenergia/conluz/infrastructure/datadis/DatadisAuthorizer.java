package org.lucoenergia.conluz.infrastructure.datadis;

import okhttp3.*;
import org.lucoenergia.conluz.domain.datadis.DatadisException;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.get.GetDatadisConfigRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.Authorizer;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class DatadisAuthorizer implements Authorizer {

    private static final String AUTH_PATH = "/nikola-auth/tokens/login";
    private static final String BODY_PARAM_USERNAME = "username";
    private static final String BODY_PARAM_PASSWORD = "password";

    private final GetDatadisConfigRepository getDatadisConfigRepository;
    private final ConluzRestClientBuilder conluzRestClientBuilder;

    public DatadisAuthorizer(GetDatadisConfigRepository getDatadisConfigRepository, ConluzRestClientBuilder conluzRestClientBuilder) {
        this.getDatadisConfigRepository = getDatadisConfigRepository;
        this.conluzRestClientBuilder = conluzRestClientBuilder;
    }

    @Override
    public String getAuthToken() {
        DatadisConfig config = getDatadisConfigRepository.getDatadisConfig()
                .orElseThrow(() -> new DatadisException("Datadis configuration not found"));
        return getAuthToken(config);
    }

    public String getAuthToken(DatadisConfig config) {
        String username = config.getUsername();
        String password = config.getPassword();
        String url = config.getBaseUrl() + AUTH_PATH;

        OkHttpClient client = conluzRestClientBuilder.build();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(BODY_PARAM_USERNAME, username)
                .addFormDataPart(BODY_PARAM_PASSWORD, password)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header(HttpHeaders.ACCEPT, "*/*")
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return response.body().string();
            } else {
                throw new DatadisException(String.format(
                        "Unable to get auth token from datadis. Code: %s, message: %s",
                        response.code(), response.body() != null ? response.body().string() : response.message()
                ));
            }
        } catch (IOException e) {
            throw new DatadisException("Unable to make the request to datadis", e);
        }
    }

    private boolean isOwner(UserPersonalId id) {
        DatadisConfig config = getDatadisConfigRepository.getDatadisConfig()
                .orElseThrow(() -> new DatadisException("Datadis configuration not found"));
        return normalize(config.getUsername()).equals(normalize(id.getPersonalId()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    public boolean requiresAuthorizedNif(UserPersonalId id) {
        return !isOwner(id);
    }
}
