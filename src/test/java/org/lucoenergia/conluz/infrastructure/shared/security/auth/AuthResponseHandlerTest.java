package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.mockito.Mockito;

import static org.mockito.Mockito.verify;

class AuthResponseHandlerTest {

    private AuthResponseHandler authResponseHandler;

    @BeforeEach
    void setup() {
        authResponseHandler = new AuthResponseHandler();
    }

    @Test
    void testSetAccessCookie() {
        // Given
        HttpServletResponse mockResponse = Mockito.mock(HttpServletResponse.class);
        Token token = Token.of("mockToken");

        // When
        authResponseHandler.setAccessCookie(mockResponse, token);

        Cookie expectedCookie = new Cookie(AuthParameter.ACCESS_TOKEN.getCookieName(), "mockToken");
        expectedCookie.setHttpOnly(true);
        expectedCookie.setSecure(true);
        expectedCookie.setPath("/");
        expectedCookie.setAttribute("SameSite", "Lax");

        verify(mockResponse).addCookie(expectedCookie);
    }
}