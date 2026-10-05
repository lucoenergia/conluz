package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthRepository;
import org.lucoenergia.conluz.domain.admin.user.auth.BlacklistedTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.lucoenergia.conluz.domain.admin.user.auth.TokenRejectionReason;
import org.lucoenergia.conluz.domain.admin.user.auth.VerifiedToken;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
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
    void aTokenThatFailsVerification_isRejectedWithTheReasonFromTheRepository() {
        givenAToken();
        Mockito.when(authRepository.verify(Mockito.any(Token.class)))
                .thenThrow(new InvalidTokenException(TokenRejectionReason.INVALID_SIGNATURE));

        InvalidTokenException exception = Assertions.assertThrows(InvalidTokenException.class,
                () -> filter.doFilterInternal(request, response, filterChain));

        Assertions.assertEquals(TokenRejectionReason.INVALID_SIGNATURE, exception.getReason());
        Assertions.assertTrue(exception.getUserId().isEmpty());
        Mockito.verifyNoInteractions(blacklistedTokenRepository, userDetailsService);
    }

    @Test
    void aRevokedToken_isRejectedBeforeItsUserIsLoaded() {
        givenAToken();
        UUID userId = UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        Mockito.when(authRepository.verify(Mockito.any(Token.class))).thenReturn(new VerifiedToken(userId, jti));
        Mockito.when(blacklistedTokenRepository.existsByJti(jti)).thenReturn(true);

        InvalidTokenException exception = Assertions.assertThrows(InvalidTokenException.class,
                () -> filter.doFilterInternal(request, response, filterChain));

        Assertions.assertEquals(TokenRejectionReason.REVOKED, exception.getReason());
        Assertions.assertEquals(Optional.of(userId), exception.getUserId());
        Mockito.verifyNoInteractions(userDetailsService);
    }

    @Test
    void aTokenWhoseUserNoLongerExists_isRejectedAsUserNotFound() {
        givenAToken();
        UUID userId = givenAVerifiedTokenNotRevoked();
        Mockito.when(userDetailsService.loadUserByUsername(userId.toString()))
                .thenThrow(UsernameNotFoundException.class);

        InvalidTokenException exception = Assertions.assertThrows(InvalidTokenException.class,
                () -> filter.doFilterInternal(request, response, filterChain));

        Assertions.assertEquals(TokenRejectionReason.USER_NOT_FOUND, exception.getReason());
        Assertions.assertEquals(Optional.of(userId), exception.getUserId());
    }

    @Test
    void aTokenRejectedForItsUser_isRejectedWithTheRuleThatRejectedIt() {
        givenAToken();
        UUID userId = givenAVerifiedTokenNotRevoked();
        User user = UserMother.randomUserWithId(userId);
        Mockito.when(userDetailsService.loadUserByUsername(userId.toString())).thenReturn(user);
        Mockito.when(authRepository.findRejectionReason(Mockito.any(Token.class), Mockito.eq(user)))
                .thenReturn(Optional.of(TokenRejectionReason.ISSUED_BEFORE_DISABLE));

        InvalidTokenException exception = Assertions.assertThrows(InvalidTokenException.class,
                () -> filter.doFilterInternal(request, response, filterChain));

        Assertions.assertEquals(TokenRejectionReason.ISSUED_BEFORE_DISABLE, exception.getReason());
        Assertions.assertEquals(Optional.of(userId), exception.getUserId());
    }

    @Test
    void aValidToken_authenticatesTheRequest() throws ServletException, IOException {
        givenAToken();
        UUID userId = givenAVerifiedTokenNotRevoked();
        User user = UserMother.randomUserWithId(userId);
        Mockito.when(userDetailsService.loadUserByUsername(userId.toString())).thenReturn(user);
        Mockito.when(authRepository.findRejectionReason(Mockito.any(Token.class), Mockito.eq(user)))
                .thenReturn(Optional.empty());

        try {
            Assertions.assertDoesNotThrow(() -> filter.doFilterInternal(request, response, filterChain));

            Mockito.verify(filterChain).doFilter(request, response);
            Assertions.assertSame(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /api/v1/login, true",
            "POST, /api/v1/init, true",
            "GET, /api/v1/info, true",
            "GET, /api-docs, true",
            "GET, /api-docs/swagger-config, true",
            "GET, /actuator/health, true",
            "POST, /api/v1/logout, false",
            "PUT, /api/v1/users/current/password, false",
            "GET, /api/v1/users/current, false"
    })
    void onlyPublicEndpoints_skipTheFilter(String method, String path, boolean skipped) {
        MockHttpServletRequest publicOrNot = new MockHttpServletRequest(method, path);

        Assertions.assertEquals(skipped, filter.shouldNotFilter(publicOrNot));
    }

    private void givenAToken() {
        Mockito.when(jwtAccessTokenHandler.getTokenFromRequest(request))
                .thenReturn(Optional.of("a token"));
    }

    private UUID givenAVerifiedTokenNotRevoked() {
        UUID userId = UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        Mockito.when(authRepository.verify(Mockito.any(Token.class))).thenReturn(new VerifiedToken(userId, jti));
        Mockito.when(blacklistedTokenRepository.existsByJti(jti)).thenReturn(false);
        return userId;
    }
}
