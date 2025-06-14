package org.lucoenergia.conluz.infrastructure.shared.security.auth;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthRepository;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTHORIZATION_HEADER_PREFIX = "Bearer ";

    private final AuthRepository authRepository;
    private final UserDetailsService userDetailsService;
    private final JwtAccessTokenHandler jwtAccessTokenHandler;

    public JwtAuthenticationFilter(AuthRepository authRepository, UserDetailsService userDetailsService,
                                   JwtAccessTokenHandler jwtAccessTokenHandler) {
        this.authRepository = authRepository;
        this.userDetailsService = userDetailsService;
        this.jwtAccessTokenHandler = jwtAccessTokenHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final Optional<String> tokenString = jwtAccessTokenHandler.getTokenFromRequest(request);
        final UUID userId;

        // If no token is provided, we should continue the filter chain
        // This is especially important for not authenticated endpoints
        if (tokenString.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        Token token = Token.of(tokenString.get());
        userId = authRepository.getUserIdFromToken(token);

        if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            User user = (User) userDetailsService.loadUserByUsername(userId.toString());

            if (authRepository.isTokenValid(token, user)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        user.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } else {
                throw new InvalidTokenException(tokenString.get());
            }
        } else {
            throw new InvalidTokenException(tokenString.get());
        }

        filterChain.doFilter(request, response);
    }
}
