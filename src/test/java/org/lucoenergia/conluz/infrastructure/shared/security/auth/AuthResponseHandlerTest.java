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
    private JwtConfiguration jwtConfiguration;

    @BeforeEach
    void setup() {
        jwtConfiguration = Mockito.mock(JwtConfiguration.class);
        authResponseHandler = new AuthResponseHandler(jwtConfiguration);
    }

    @Test
    void testSetAccessCookie() {
        // Given
        HttpServletResponse mockResponse = Mockito.mock(HttpServletResponse.class);
        Token token = Token.of("mockToken");
        
        int expirationTime = 30;
        Mockito.when(jwtConfiguration.getExpirationTime()).thenReturn(expirationTime);

        // When
        authResponseHandler.setAccessCookie(mockResponse, token);

        Cookie expectedCookie = new Cookie(AuthParameter.ACCESS_TOKEN.getCookieName(), "mockToken");
        expectedCookie.setHttpOnly(true);
        expectedCookie.setSecure(true);
        expectedCookie.setPath("/");
        expectedCookie.setAttribute("SameSite", "Lax");
        expectedCookie.setMaxAge(expirationTime);

        verify(mockResponse).addCookie(expectedCookie);
    }

    @Test
    void testUnsetAccessCookie() {
        // Given
        HttpServletResponse mockResponse = Mockito.mock(HttpServletResponse.class);

        // When
        authResponseHandler.unsetAccessCookie(mockResponse);

        // Then
        Cookie expectedCookie = new Cookie(AuthParameter.ACCESS_TOKEN.getCookieName(), "");
        expectedCookie.setHttpOnly(true);
        expectedCookie.setSecure(true);
        expectedCookie.setPath("/");
        expectedCookie.setAttribute("SameSite", "Lax");
        expectedCookie.setMaxAge(0);

        verify(mockResponse).addCookie(expectedCookie);
    }
}