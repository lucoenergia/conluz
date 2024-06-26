package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthRepository;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.IOException;
import java.util.UUID;
import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @InjectMocks
    private JwtAuthenticationFilter filter;
    @Mock
    private AuthRepository authRepository;
    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "empty"})
    void testTokenNotPresentInRequestOrEmpty(String emptyToken) throws ServletException, IOException {

        Mockito.when(request.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn(emptyToken);

        filter.doFilterInternal(request, response, filterChain);

        Mockito.verify(filterChain).doFilter(request, response);
    }

    @Test
    void testTokenNotPresentAsHeaderButPresentAsCookie() throws ServletException, IOException {

        Mockito.when(request.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn(null);

        Mockito.when(request.getCookies())
                .thenReturn(Stream.generate(() -> new Cookie(AuthParameter.ACCESS_TOKEN.getCookieName(), "foo"))
                        .limit(1)
                        .toArray(Cookie[]::new));

        Mockito.when(authRepository.getUserIdFromToken(Mockito.any(Token.class)))
                .thenThrow(InvalidTokenException.class);

        Assertions.assertThrows(InvalidTokenException.class,
                () -> filter.doFilterInternal(request, response, filterChain));
    }

    @Test
    void testTokenWithInvalidFormat() {

        final String invalidToken = "invalid-token";
        Mockito.when(request.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn("Bearer " + invalidToken);

        Mockito.when(authRepository.getUserIdFromToken(Mockito.any(Token.class)))
                .thenThrow(InvalidTokenException.class);

        Assertions.assertThrows(InvalidTokenException.class,
                () -> filter.doFilterInternal(request, response, filterChain));
    }

    @Test
    void testTokenWithValidFormatButEmptyUserId() {

        final String validToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwMTIzNDU2N1oiLCJpYXQiOjE3MDMyODA2MTksImV4cCI6MTcwMzI4MjQxOX0.mNS-1EiY8tYDcVvrU_oR6Rlj9bpB3QNcSpqdP_7KH_o";
        Mockito.when(request.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn("Bearer " + validToken);

        Mockito.when(authRepository.getUserIdFromToken(Mockito.any(Token.class)))
                .thenReturn(null);

        Assertions.assertThrows(InvalidTokenException.class,
                () -> filter.doFilterInternal(request, response, filterChain));
    }

    @Test
    void testTokenWithValidFormatButUserNotFound() {

        final String invalidToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwMTIzNDU2N1oiLCJpYXQiOjE3MDMyODA2MTksImV4cCI6MTcwMzI4MjQxOX0.mNS-1EiY8tYDcVvrU_oR6Rlj9bpB3QNcSpqdP_7KH_o";
        Mockito.when(request.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn("Bearer " + invalidToken);

        UUID userId = UUID.randomUUID();
        Mockito.when(authRepository.getUserIdFromToken(Mockito.any(Token.class)))
                .thenReturn(userId);

        Mockito.when(userDetailsService.loadUserByUsername(userId.toString()))
                .thenThrow(UsernameNotFoundException.class);

        Assertions.assertThrows(UsernameNotFoundException.class,
                () -> filter.doFilterInternal(request, response, filterChain));
    }

    @Test
    void testTokenWithValidFormatButInvalidContent() {

        final String invalidToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwMTIzNDU2N1oiLCJpYXQiOjE3MDMyODA2MTksImV4cCI6MTcwMzI4MjQxOX0.mNS-1EiY8tYDcVvrU_oR6Rlj9bpB3QNcSpqdP_7KH_o";
        Mockito.when(request.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn("Bearer " + invalidToken);

        UUID userId = UUID.randomUUID();
        Mockito.when(authRepository.getUserIdFromToken(Mockito.any(Token.class)))
                .thenReturn(userId);

        User user = UserMother.randomUserWithId(userId);
        Mockito.when(userDetailsService.loadUserByUsername(userId.toString()))
                .thenReturn(user);

        Mockito.when(authRepository.isTokenValid(Mockito.any(Token.class), Mockito.eq(user)))
                .thenReturn(false);

        Assertions.assertThrows(InvalidTokenException.class,
                () -> filter.doFilterInternal(request, response, filterChain));
    }

    @Test
    void testTokenWithValidFormatAndValidContent() throws ServletException, IOException {

        final String validToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwMTIzNDU2N1oiLCJpYXQiOjE3MDMyODA2MTksImV4cCI6MTcwMzI4MjQxOX0.mNS-1EiY8tYDcVvrU_oR6Rlj9bpB3QNcSpqdP_7KH_o";
        Mockito.when(request.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn("Bearer " + validToken);

        UUID userId = UUID.randomUUID();
        Mockito.when(authRepository.getUserIdFromToken(Mockito.any(Token.class)))
                .thenReturn(userId);

        User user = UserMother.randomUserWithId(userId);
        Mockito.when(userDetailsService.loadUserByUsername(userId.toString()))
                .thenReturn(user);

        Mockito.when(authRepository.isTokenValid(Mockito.any(Token.class), Mockito.eq(user)))
                .thenReturn(true);

        Assertions.assertDoesNotThrow(() -> filter.doFilterInternal(request, response, filterChain));

        Mockito.verify(filterChain).doFilter(request, response);
    }
}
