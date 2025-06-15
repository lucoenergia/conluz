package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthRepository;
import org.lucoenergia.conluz.domain.admin.user.auth.BlacklistedTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @InjectMocks
    private JwtAuthenticationFilter filter;
    @Mock
    private AuthRepository authRepository;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private JwtAccessTokenHandler jwtAccessTokenHandler;
    @Mock
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @Test
    void testTokenNotPresentInRequestOrEmpty() throws ServletException, IOException {

        Mockito.when(jwtAccessTokenHandler.getTokenFromRequest(request))
                .thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        Mockito.verify(filterChain).doFilter(request, response);
    }

    @Test
    void testTokenWithInvalidFormat() {

        final String invalidToken = "invalid-token";
        Mockito.when(jwtAccessTokenHandler.getTokenFromRequest(request))
                .thenReturn(Optional.of("Bearer " + invalidToken));

        tokenNotIncludedInBlacklist();

        Mockito.when(authRepository.getUserIdFromToken(Mockito.any(Token.class)))
                .thenThrow(InvalidTokenException.class);

        Assertions.assertThrows(InvalidTokenException.class,
                () -> filter.doFilterInternal(request, response, filterChain));
    }

    @Test
    void testTokenWithValidFormatButEmptyUserId() {

        final String validToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwMTIzNDU2N1oiLCJpYXQiOjE3MDMyODA2MTksImV4cCI6MTcwMzI4MjQxOX0.mNS-1EiY8tYDcVvrU_oR6Rlj9bpB3QNcSpqdP_7KH_o";
        Mockito.when(jwtAccessTokenHandler.getTokenFromRequest(request))
                .thenReturn(Optional.of("Bearer " + validToken));

        tokenNotIncludedInBlacklist();

        Mockito.when(authRepository.getUserIdFromToken(Mockito.any(Token.class)))
                .thenReturn(null);

        Assertions.assertThrows(InvalidTokenException.class,
                () -> filter.doFilterInternal(request, response, filterChain));
    }

    @Test
    void testTokenWithValidFormatButUserNotFound() {

        final String invalidToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwMTIzNDU2N1oiLCJpYXQiOjE3MDMyODA2MTksImV4cCI6MTcwMzI4MjQxOX0.mNS-1EiY8tYDcVvrU_oR6Rlj9bpB3QNcSpqdP_7KH_o";
        Mockito.when(jwtAccessTokenHandler.getTokenFromRequest(request))
                .thenReturn(Optional.of("Bearer " + invalidToken));

        tokenNotIncludedInBlacklist();

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
        Mockito.when(jwtAccessTokenHandler.getTokenFromRequest(request))
                .thenReturn(Optional.of("Bearer " + invalidToken));

        tokenNotIncludedInBlacklist();

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
        Mockito.when(jwtAccessTokenHandler.getTokenFromRequest(request))
                .thenReturn(Optional.of("Bearer " + validToken));

        tokenNotIncludedInBlacklist();

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

    @Test
    void testTokenIncludedInBlacklist() {
        final String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwMTIzNDU2N1oiLCJpYXQiOjE3MDMyODA2MTksImV4cCI6MTcwMzI4MjQxOX0.mNS-1EiY8tYDcVvrU_oR6Rlj9bpB3QNcSpqdP_7KH_o";
        Mockito.when(jwtAccessTokenHandler.getTokenFromRequest(request))
                .thenReturn(Optional.of("Bearer " + token));

        String jti = UUID.randomUUID().toString();
        Mockito.when(authRepository.getJtiFromToken(Mockito.any(Token.class)))
                .thenReturn(Optional.of(jti));
        Mockito.when(blacklistedTokenRepository.existsByJti(jti))
                .thenReturn(true);

        Assertions.assertThrows(InvalidTokenException.class,
                () -> filter.doFilterInternal(request, response, filterChain));
    }

    private void tokenNotIncludedInBlacklist() {
        String jti = UUID.randomUUID().toString();
        Mockito.when(authRepository.getJtiFromToken(Mockito.any(Token.class)))
                .thenReturn(Optional.of(jti));
        Mockito.when(blacklistedTokenRepository.existsByJti(jti))
                .thenReturn(false);
    }
}
