package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Optional;

@Component
public class JwtAccessTokenHandler {

    public static final String AUTHORIZATION_HEADER_PREFIX = "Bearer ";

    public Optional<String> getTokenFromRequest(HttpServletRequest request) {
        Optional<String> result = getTokenFromHeader(request);
        return result
                .or(() -> getTokenFromCookie(request));
    }

    private Optional<String> getTokenFromHeader(HttpServletRequest request) {
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(authHeader) && authHeader.startsWith(AUTHORIZATION_HEADER_PREFIX)) {
            return Optional.of(authHeader.substring(AUTHORIZATION_HEADER_PREFIX.length()));
        }
        return Optional.empty();
    }

    private Optional<String> getTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }

        return Arrays.stream(cookies)
                .filter(cookie -> AuthParameter.ACCESS_TOKEN.getCookieName().equalsIgnoreCase(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
