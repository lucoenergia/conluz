package org.lucoenergia.conluz.infrastructure.shared.web.rest;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.stereotype.Component;

@Component
public class RestClientBuilder {

    public OkHttpClient build() {
        return build(false);
    }

    public OkHttpClient build(boolean enableLogging) {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
        if (enableLogging) {
            clientBuilder.addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY));
        }
        return clientBuilder.build();
    }
}
