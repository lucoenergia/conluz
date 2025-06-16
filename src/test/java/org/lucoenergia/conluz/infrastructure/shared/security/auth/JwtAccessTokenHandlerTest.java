package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class JwtAccessTokenHandlerTest {

    @Test
    void testGetTokenFromRequest_HeaderContainsValidToken() {
        // Arrange
        JwtAccessTokenHandler jwtAccessTokenHandler = new JwtAccessTokenHandler();
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        String token = "sampleToken";

        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);

        // Act
        Optional<String> result = jwtAccessTokenHandler.getTokenFromRequest(mockRequest);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(token, result.get());
        verify(mockRequest).getHeader(HttpHeaders.AUTHORIZATION);
    }

    @Test
    void testGetTokenFromRequest_HeaderDoesNotContainToken_CheckCookies() {
        // Arrange
        JwtAccessTokenHandler jwtAccessTokenHandler = new JwtAccessTokenHandler();
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);

        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        Cookie[] cookies = new Cookie[]{
                new Cookie("otherCookie", "otherValue"),
                new Cookie(AuthParameter.ACCESS_TOKEN.getCookieName(), "cookieToken")
        };
        when(mockRequest.getCookies()).thenReturn(cookies);

        // Act
        Optional<String> result = jwtAccessTokenHandler.getTokenFromRequest(mockRequest);

        // Assert
        assertTrue(result.isPresent());
        assertEquals("cookieToken", result.get());
        verify(mockRequest).getHeader(HttpHeaders.AUTHORIZATION);
        verify(mockRequest).getCookies();
    }

    @Test
    void testGetTokenFromRequest_NoHeaderAndNoCookies() {
        // Arrange
        JwtAccessTokenHandler jwtAccessTokenHandler = new JwtAccessTokenHandler();
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);

        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);
        when(mockRequest.getCookies()).thenReturn(null);

        // Act
        Optional<String> result = jwtAccessTokenHandler.getTokenFromRequest(mockRequest);

        // Assert
        assertTrue(result.isEmpty());
        verify(mockRequest).getHeader(HttpHeaders.AUTHORIZATION);
        verify(mockRequest).getCookies();
    }

    @Test
    void testGetTokenFromRequest_HeaderPresentButInvalidPrefix() {
        // Arrange
        JwtAccessTokenHandler jwtAccessTokenHandler = new JwtAccessTokenHandler();
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);

        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Invalid " + "sampleToken");

        // Act
        Optional<String> result = jwtAccessTokenHandler.getTokenFromRequest(mockRequest);

        // Assert
        assertTrue(result.isEmpty());
        verify(mockRequest).getHeader(HttpHeaders.AUTHORIZATION);
    }

    @Test
    void testGetTokenFromRequest_EmptyHeaderAndCookieArrayWithoutToken() {
        // Arrange
        JwtAccessTokenHandler jwtAccessTokenHandler = new JwtAccessTokenHandler();
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);

        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);
        Cookie[] cookies = new Cookie[]{
                new Cookie("otherCookie", "otherValue")
        };
        when(mockRequest.getCookies()).thenReturn(cookies);

        // Act
        Optional<String> result = jwtAccessTokenHandler.getTokenFromRequest(mockRequest);

        // Assert
        assertTrue(result.isEmpty());
        verify(mockRequest).getHeader(HttpHeaders.AUTHORIZATION);
        verify(mockRequest).getCookies();
    }
}