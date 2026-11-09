package org.lucoenergia.conluz.infrastructure.datadis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.lucoenergia.conluz.domain.admin.supply.DatadisSupply;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Answers the Datadis API in place of the network, for any number of accounts told apart by their base URL. Every
 * request is recorded with the token it carried, its {@code authorizedNif}, and whether a database transaction was
 * active while it was made. A data request is answered only when it carries the token its account's login returned,
 * so a request made with another account's token or base URL gets a 401.
 */
public final class DatadisStub {

    public static final String LOGIN_PATH = "/nikola-auth/tokens/login";
    public static final String SUPPLIES_PATH = "/api-private/api/get-supplies";
    public static final String CONSUMPTION_PATH = "/api-private/api/get-consumption-data";

    /**
     * One request received. {@code authorizedNif} is null when the request did not carry one.
     */
    public record Call(String host, String path, String authorization, String authorizedNif,
                       boolean transactionActive) {
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> tokens = new ConcurrentHashMap<>();
    private final Map<String, Integer> loginFailures = new ConcurrentHashMap<>();
    private final Map<String, List<DatadisSupply>> supplies = new ConcurrentHashMap<>();
    private final Map<String, Integer> suppliesFailures = new ConcurrentHashMap<>();
    private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());

    /**
     * An account at {@code baseUrl} whose login returns {@code token}.
     */
    public void account(String baseUrl, String token) {
        tokens.put(host(baseUrl), token);
    }

    /**
     * The login of the account at {@code baseUrl} is answered with {@code status}.
     */
    public void failLogin(String baseUrl, int status) {
        loginFailures.put(host(baseUrl), status);
    }

    /**
     * The supplies returned at {@code baseUrl} for {@code authorizedNif}, or for the account holder when it is null,
     * in this order.
     */
    public void supplies(String baseUrl, String authorizedNif, List<DatadisSupply> entries) {
        supplies.put(key(host(baseUrl), authorizedNif), List.copyOf(entries));
    }

    /**
     * The supplies request at {@code baseUrl} for {@code authorizedNif} is answered with {@code status}.
     */
    public void failSupplies(String baseUrl, String authorizedNif, int status) {
        suppliesFailures.put(key(host(baseUrl), authorizedNif), status);
    }

    public List<Call> calls() {
        synchronized (calls) {
            return List.copyOf(calls);
        }
    }

    public List<Call> calls(String baseUrl, String path) {
        String host = host(baseUrl);
        return calls().stream().filter(call -> call.host().equals(host) && call.path().equals(path)).toList();
    }

    public void clear() {
        calls.clear();
    }

    public OkHttpClient client() {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> answer(chain.request()))
                .build();
    }

    private Response answer(Request request) {
        String host = request.url().host();
        String path = request.url().encodedPath();
        String authorizedNif = request.url().queryParameter("authorizedNif");
        calls.add(new Call(host, path, request.header(HttpHeaders.AUTHORIZATION), authorizedNif,
                TransactionSynchronizationManager.isActualTransactionActive()));

        if (path.equals(LOGIN_PATH)) {
            Integer failure = loginFailures.get(host);
            if (failure != null) {
                return response(request, failure, "login refused");
            }
            String token = tokens.get(host);
            return token == null ? response(request, 401, "unknown account") : response(request, 200, token);
        }
        String token = tokens.get(host);
        if (token == null || !("Bearer " + token).equals(request.header(HttpHeaders.AUTHORIZATION))) {
            return response(request, 401, "invalid token");
        }
        if (path.equals(SUPPLIES_PATH)) {
            Integer failure = suppliesFailures.get(key(host, authorizedNif));
            if (failure != null) {
                return response(request, failure, "supplies refused");
            }
            return response(request, 200, json(supplies.getOrDefault(key(host, authorizedNif), List.of())));
        }
        if (path.equals(CONSUMPTION_PATH)) {
            return response(request, 200, "[]");
        }
        return response(request, 404, "not found");
    }

    private String json(List<DatadisSupply> entries) {
        try {
            return objectMapper.writeValueAsString(entries);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String key(String host, String authorizedNif) {
        return host + "|" + (authorizedNif == null ? "" : authorizedNif);
    }

    private static String host(String baseUrl) {
        return okhttp3.HttpUrl.get(baseUrl).host();
    }

    private static Response response(Request request, int code, String body) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(code == 200 ? "OK" : "Error")
                .body(ResponseBody.create(body, MediaType.get("application/json")))
                .build();
    }
}
