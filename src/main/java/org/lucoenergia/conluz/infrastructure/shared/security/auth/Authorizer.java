package org.lucoenergia.conluz.infrastructure.shared.security.auth;

public interface Authorizer {

    String getAuthToken();

    default String getAuthBearerToken() {
        return "Bearer " + getAuthToken();
    }
}
