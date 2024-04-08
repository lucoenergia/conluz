package org.lucoenergia.conluz.infrastructure.production.huawei;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.Authorizer;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class HuaweiAuthorizer implements Authorizer {

    private static final String URL = HuaweiConfig.BASE_URL + "/login";
    private static final String BODY_PARAM_USERNAME = "userName";
    private static final String BODY_PARAM_PASSWORD = "systemCode";
    public static final String TOKEN_HEADER = "xsrf-token";

    private final ObjectMapper objectMapper;
    private final ConluzRestClientBuilder conluzRestClientBuilder;
    private final HuaweiConfigRepository huaweiConfigRepository;

    public HuaweiAuthorizer(ObjectMapper objectMapper, ConluzRestClientBuilder conluzRestClientBuilder,
                            HuaweiConfigRepository huaweiConfigRepository) {
        this.objectMapper = objectMapper;
        this.conluzRestClientBuilder = conluzRestClientBuilder;
        this.huaweiConfigRepository = huaweiConfigRepository;
    }

    @Override
    public String getAuthToken() {
        Optional<HuaweiConfigEntity> optionalConfig = huaweiConfigRepository.findFirstByOrderByIdAsc();
        if (optionalConfig.isEmpty()) {
            throw new HuaweiException("Huawei configuration not found");
        }
        HuaweiConfigEntity config = optionalConfig.get();
        String username = config.getUsername();
        String password = config.getPassword();

        OkHttpClient client = conluzRestClientBuilder.build();

        Map<String, Object> map = new HashMap<>();
        map.put(BODY_PARAM_USERNAME, username);
        map.put(BODY_PARAM_PASSWORD, password);
        RequestBody requestBody = null;
        try {
            requestBody = RequestBody.create(
                    objectMapper.writeValueAsString(map),
                    okhttp3.MediaType.parse(String.join(";", List.of(MediaType.APPLICATION_JSON_VALUE, "charset=UTF-8")))
            );
        } catch (JsonProcessingException e) {
            throw new HuaweiException("Error generating body to get auth token", e);
        }

        Request request = new Request.Builder()
                .url(URL)
                .header(HttpHeaders.CONTENT_TYPE, String.join(";", List.of(MediaType.APPLICATION_JSON_VALUE, "charset=UTF-8")))
                .header(HttpHeaders.ACCEPT, "*/*")
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
