package org.lucoenergia.conluz.infrastructure.shared.security.auth;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthRepository;
import org.lucoenergia.conluz.domain.admin.user.auth.BlacklistedTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.lucoenergia.conluz.domain.admin.user.auth.TokenRejectionReason;
import org.lucoenergia.conluz.domain.admin.user.auth.VerifiedToken;
import org.lucoenergia.conluz.infrastructure.shared.security.PublicEndpoints;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTHORIZATION_HEADER_PREFIX = "Bearer ";

    private final AuthRepository authRepository;
    private final UserDetailsService userDetailsService;
    private final JwtAccessTokenHandler jwtAccessTokenHandler;
    private final BlacklistedTokenRepository blacklistedTokenRepository;

    public JwtAuthenticationFilter(AuthRepository authRepository, UserDetailsService userDetailsService,
                                   JwtAccessTokenHandler jwtAccessTokenHandler,
                                   BlacklistedTokenRepository blacklistedTokenRepository) {
        this.authRepository = authRepository;
        this.userDetailsService = userDetailsService;
        this.jwtAccessTokenHandler = jwtAccessTokenHandler;
        this.blacklistedTokenRepository = blacklistedTokenRepository;
    }

    /**
     * Endpoints that require no authentication ignore any token presented on them: it is neither validated nor
     * rejected, and it does not authenticate the request. A stale token therefore never stands in the way of
     * logging in again.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PublicEndpoints.MATCHER.matches(request);
    }

    /**
     * Every rejection is thrown as an {@link InvalidTokenException} carrying its reason, and logged once by
     * {@link JwtAuthenticationExceptionFilter}.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final Optional<String> tokenString = jwtAccessTokenHandler.getTokenFromRequest(request);

        // If no token is provided, we should continue the filter chain
        // This is especially important for not authenticated endpoints
        if (tokenString.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        Token token = Token.of(tokenString.get());

        final VerifiedToken verifiedToken = authRepository.verify(token);
        final UUID userId = verifiedToken.userId();

        if (blacklistedTokenRepository.existsByJti(verifiedToken.jti())) {
            throw new InvalidTokenException(TokenRejectionReason.REVOKED, userId);
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            User user = loadUser(userId);

            Optional<TokenRejectionReason> rejection = authRepository.findRejectionReason(token, user);
            if (rejection.isPresent()) {
                throw new InvalidTokenException(rejection.get(), userId);
            }
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    user,
                    null,
                    user.getAuthorities()
            );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }
        // If authentication is already set by a prior filter invocation, proceed without re-processing.

        filterChain.doFilter(request, response);
    }

    private User loadUser(UUID userId) {
        try {
            return (User) userDetailsService.loadUserByUsername(userId.toString());
        } catch (UsernameNotFoundException e) {
            throw new InvalidTokenException(TokenRejectionReason.USER_NOT_FOUND, userId);
        }
    }
}
