package org.lucoenergia.conluz.infrastructure.admin.user.auth;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthRepository;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER_PREFIX = "Bearer ";

    private final AuthRepository authRepository;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(AuthRepository authRepository, UserDetailsService userDetailsService) {
        this.authRepository = authRepository;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        final Optional<String> token = getTokenFromRequest(request);
        final String username;

        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        username = authRepository.getUsernameFromToken(Token.of(token.get()));

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            User user = (User) userDetailsService.loadUserByUsername(username);

            if (authRepository.isTokenValid(Token.of(token.get()), user)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        user.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    private Optional<String> getTokenFromRequest(HttpServletRequest request) {
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(authHeader) && authHeader.startsWith(AUTHORIZATION_HEADER_PREFIX)) {
            return Optional.of(authHeader.substring(AUTHORIZATION_HEADER_PREFIX.length()));
        }
        return Optional.empty();
    }
}
