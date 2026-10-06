package org.lucoenergia.conluz.infrastructure.shared.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PasswordChangeAllowedEndpointsTest {

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/users/current, true",
            "PUT, /api/v1/users/current/password, true",
            "POST, /api/v1/logout, true",
            // Another method on an allowed path
            "PUT, /api/v1/users/current, false",
            "GET, /api/v1/users/current/password, false",
            "GET, /api/v1/logout, false",
            // A path under or next to an allowed one
            "GET, /api/v1/users/current/extra, false",
            "GET, /api/v1/users/current/, false",
            "GET, /api/v1/users, false",
            "PUT, /api/v1/users/profile, false"
    })
    void matchesExactlyTheThreeAllowedRequests(String method, String path, boolean allowed) {
        assertEquals(allowed, PasswordChangeAllowedEndpoints.MATCHER.matches(new MockHttpServletRequest(method, path)));
    }
}
