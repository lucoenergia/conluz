package org.lucoenergia.conluz.infrastructure.shared.security.auth;

public enum AuthParameter {
    ACCESS_TOKEN("access_token", "access_token");

    private final String headerName;
    private final String cookieName;

    AuthParameter(String headerName, String cookieName) {
        this.headerName = headerName;
        this.cookieName = cookieName;
    }

    public String getHeaderName() {
        return headerName;
    }

    public String getCookieName() {
        return cookieName;
    }
}
