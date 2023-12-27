package org.lucoenergia.conluz.infrastructure.shared.security.auth;

public class InvalidTokenException extends RuntimeException {

    private final String token;

    public InvalidTokenException(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }
}
