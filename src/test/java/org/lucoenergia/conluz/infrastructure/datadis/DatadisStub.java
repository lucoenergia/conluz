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
 * active while it was made. A data request is answered only when it carries the token its account's latest login
 * returned, so a request made with another account's token or base URL, or with a token replaced by a later login,
 * gets a 401.
 */
public final class DatadisStub {

    public static final String LOGIN_PATH = "/nikola-auth/tokens/login";
    public static final String SUPPLIES_PATH = "/api-private/api/get-supplies";
    public static final String CONSUMPTION_PATH = "/api-private/api/get-consumption-data";

    /**
     * One request received, and the status it was answered with. {@code authorizedNif}, {@code cups} and
     * {@code startDate} are null when the request did not carry them.
     */
    public record Call(String host, String path, String authorization, String authorizedNif, String cups,
                       String startDate, boolean transactionActive, int status) {
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, List<String>> tokens = new ConcurrentHashMap<>();
    private final Map<String, Integer> logins = new ConcurrentHashMap<>();
    private final Map<String, String> issued = new ConcurrentHashMap<>();
    private final Map<String, Integer> expiries = new ConcurrentHashMap<>();
    private final Map<String, Integer> answeredConsumptions = new ConcurrentHashMap<>();
    private final Map<String, Boolean> rejectedConsumptions = new ConcurrentHashMap<>();
    private final Map<String, Integer> loginFailures = new ConcurrentHashMap<>();
    private final Map<String, List<DatadisSupply>> supplies = new ConcurrentHashMap<>();
    private final Map<String, Integer> suppliesFailures = new ConcurrentHashMap<>();
    private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());

    /**
     * An account at {@code baseUrl} whose first login returns {@code token}, and each later login the next of
     * {@code laterTokens}, the last one repeating.
     */
    public void account(String baseUrl, String token, String... laterTokens) {
        List<String> sequence = new ArrayList<>();
        sequence.add(token);
        sequence.addAll(List.of(laterTokens));
        tokens.put(host(baseUrl), List.copyOf(sequence));
    }

    /**
     * The token the first login at {@code baseUrl} returns is rejected once {@code consumptionRequests} consumption
     * requests have been answered with it, as a token that expires during a run.
     */
    public void expireFirstTokenAfter(String baseUrl, int consumptionRequests) {
        expiries.put(host(baseUrl), consumptionRequests);
    }

    /**
     * Every consumption request at {@code baseUrl} for {@code cups} and {@code month} ({@code yyyy/MM}, as Datadis
     * takes it) is answered 401, whatever token it carries.
     */
    public void rejectConsumption(String baseUrl, String cups, String month) {
        rejectedConsumptions.put(key(host(baseUrl), cups + "|" + month), true);
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
        boolean transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
        Response response = respond(request);
        calls.add(new Call(request.url().host(), request.url().encodedPath(),
                request.header(HttpHeaders.AUTHORIZATION), request.url().queryParameter("authorizedNif"),
                request.url().queryParameter("cups"), request.url().queryParameter("startDate"), transactionActive,
                response.code()));
        return response;
    }

    private synchronized Response respond(Request request) {
        String host = request.url().host();
        String path = request.url().encodedPath();
        String authorizedNif = request.url().queryParameter("authorizedNif");

        if (path.equals(LOGIN_PATH)) {
            Integer failure = loginFailures.get(host);
            if (failure != null) {
                return response(request, failure, "login refused");
            }
            List<String> sequence = tokens.get(host);
            if (sequence == null) {
                return response(request, 401, "unknown account");
            }
            int login = logins.merge(host, 1, Integer::sum);
            String token = sequence.get(Math.min(login, sequence.size()) - 1);
            issued.put(host, token);
            return response(request, 200, token);
        }
        String token = issued.get(host);
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
            if (rejectedConsumptions.containsKey(key(host, request.url().queryParameter("cups") + "|"
                    + request.url().queryParameter("startDate")))) {
                return response(request, 401, "invalid token");
            }
            Integer expiry = expiries.get(host);
            if (expiry != null && token.equals(tokens.get(host).get(0))
                    && answeredConsumptions.getOrDefault(host, 0) >= expiry) {
                return response(request, 401, "token expired");
            }
            answeredConsumptions.merge(host, 1, Integer::sum);
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
