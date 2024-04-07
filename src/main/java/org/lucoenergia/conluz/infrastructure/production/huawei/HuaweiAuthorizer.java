package org.lucoenergia.conluz.infrastructure.production.huawei;

import okhttp3.*;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;

import java.io.IOException;
import java.util.Optional;

public class HuaweiAuthorizer {

    private static final String URL = HuaweiConfig.BASE_URL + "/login";
    private static final String BODY_PARAM_USERNAME = "userName";
    private static final String BODY_PARAM_PASSWORD = "systemCode";
    private static final String TOKEN_HEADER = "xsrf-token";

    private final ConluzRestClientBuilder conluzRestClientBuilder;
    private final HuaweiConfigRepository huaweiConfigRepository;

    public HuaweiAuthorizer(ConluzRestClientBuilder conluzRestClientBuilder, HuaweiConfigRepository huaweiConfigRepository) {
        this.conluzRestClientBuilder = conluzRestClientBuilder;
        this.huaweiConfigRepository = huaweiConfigRepository;
    }

    public String getAuthToken() {
        Optional<HuaweiConfigEntity> optionalConfig = huaweiConfigRepository.findFirstByOrderByIdAsc();
        if (optionalConfig.isEmpty()) {
            throw new HuaweiException("Huawei configuration not found");
        }
        HuaweiConfigEntity config = optionalConfig.get();
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
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return response.header(TOKEN_HEADER);
            } else {
                throw new HuaweiException(String.format(
                        "Unable to get auth token from %s. Code: %s, message: %s",
                        URL, response.code(), response.body() != null ? response.body().string() : response.message()
                ));
            }
        } catch (IOException e) {
            throw new HuaweiException(String.format("Unable to make the request to %s", URL), e);
        }
    }
}
