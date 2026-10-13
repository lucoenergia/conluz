package org.lucoenergia.conluz.domain.shared.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Which of the fields a provider connection needs are missing from a stored configuration (#414).
 */
public final class ConnectionConfigCompleteness {

    public static final String USERNAME = "username";
    public static final String BASE_URL = "baseUrl";
    public static final String PASSWORD = "password";

    private ConnectionConfigCompleteness() {
    }

    /**
     * The missing fields, always in the order {@code username}, {@code baseUrl}, {@code password}; empty when
     * the configuration is complete. A username is missing when blank, a base URL when {@code baseUrlValidity}
     * rejects it, and a password when empty.
     */
    public static List<String> missingFields(String username, String baseUrl, String password,
                                             BaseUrlValidity baseUrlValidity) {
        List<String> missing = new ArrayList<>();
        if (username == null || username.isBlank()) {
            missing.add(USERNAME);
        }
        if (!baseUrlValidity.isValid(baseUrl)) {
            missing.add(BASE_URL);
        }
        if (password == null || password.isEmpty()) {
            missing.add(PASSWORD);
        }
        return List.copyOf(missing);
    }
}
