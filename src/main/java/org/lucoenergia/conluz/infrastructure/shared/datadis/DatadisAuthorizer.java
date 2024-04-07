package org.lucoenergia.conluz.infrastructure.shared.datadis;

import okhttp3.*;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
public class DatadisAuthorizer {

    private static final String URL = "https://datadis.es/nikola-auth/tokens/login";
    private static final String BODY_PARAM_USERNAME = "username";
    private static final String BODY_PARAM_PASSWORD = "password";

    private final DatadisConfigRepository datadisConfigRepository;
    private final ConluzRestClientBuilder conluzRestClientBuilder;

    public DatadisAuthorizer(DatadisConfigRepository datadisConfigRepository, ConluzRestClientBuilder conluzRestClientBuilder) {
        this.datadisConfigRepository = datadisConfigRepository;
        this.conluzRestClientBuilder = conluzRestClientBuilder;
    }

    public String getAuthToken() {
        Optional<DatadisConfigEntity> optionalConfig = datadisConfigRepository.findFirstByOrderByIdAsc();
        if (optionalConfig.isEmpty()) {
            throw new DatadisException("Datadis configuration not found");
        }
        DatadisConfigEntity config = optionalConfig.get();
        String username = config.getUsername();
        String password = config.getPassword();

        OkHttpClient client = conluzRestClientBuilder.build();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(BODY_PARAM_USERNAME, username)
                .addFormDataPart(BODY_PARAM_PASSWORD, password)
                .build();

        Request request = new Request.Builder()
                .url(URL)
                .header(HttpHeaders.ACCEPT, "*/*")
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return response.body().string();
            } else {
                throw new DatadisException(String.format(
                        "Unable to get auth token from datadis.es. Code: %s, message: %s",
                        response.code(), response.body() != null ? response.body().string() : response.message()
                ));
            }
        } catch (IOException e) {
            throw new DatadisException("Unable to make the request to datadis.es", e);
        }
    }

    public String getAuthTokenWithBearerFormat() {
        return "Bearer " + getAuthToken();
    }
}
