package org.lucoenergia.conluz.domain.admin.user.auth;

public class Token {

    private final String token;

    private Token(String token) {
        this.token = token;
    }

    public static Token of(String token) {
        return new Token(token);
    }

    public String getToken() {
        return token;
    }
}
