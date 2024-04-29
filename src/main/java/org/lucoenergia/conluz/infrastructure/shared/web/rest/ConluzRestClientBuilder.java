package org.lucoenergia.conluz.infrastructure.shared.web.rest;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ConluzRestClientBuilder {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    public OkHttpClient build() {
        return build(false, DEFAULT_TIMEOUT);
    }

    /**
     * Builds an OkHttpClient instance with optional logging interceptor.
     *
     * Sets default timeouts:
     *  connectTimeout: This sets the maximum time to establish the initial TCP connection to the server.
     *      It covers the period of time from when you start the call with a call.enqueue() or call.execute() up until
     *      the point that a successful TCP connection to the server has been established.
     *  readTimeout: This sets the maximum time to wait for a read action. Once your request has been sent and the TCP
     *      connection has been established, this timeout covers the ongoing completion of receiving a response from the
     *      server. If the server doesn't deliver enough bytes within this time limit, OkHttp will throw
     *      a SocketTimeoutException.
     *  writeTimeout: This sets the maximum time to wait for a write action. This timeout applies for the period of time
     *      when OkHttp is writing data to a server. If OkHttp is unable to send a byte within this time limit,
     *      a SocketTimeoutException is thrown.
     *
     * @param enableLogging true if logging interceptor should be added, false otherwise
     * @return the built OkHttpClient instance
     */
    public OkHttpClient build(boolean enableLogging, Duration timeout) {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
        if (enableLogging) {
            clientBuilder.addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY));
        }
        if (timeout == null) {
            timeout = DEFAULT_TIMEOUT;
        }
        clientBuilder.connectTimeout(timeout);
        clientBuilder.writeTimeout(timeout);
        clientBuilder.readTimeout(timeout);

        return clientBuilder.build();
    }
}
