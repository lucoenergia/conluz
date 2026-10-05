package org.lucoenergia.conluz.infrastructure.shared.security;

import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Arrays;

/**
 * The endpoints that require no authentication, on any HTTP method. Declared once, because both the security
 * configuration, which lets them through, and the token filter, which ignores any token presented on them, must
 * agree on them.
 */
public final class PublicEndpoints {

    public static final RequestMatcher MATCHER = new OrRequestMatcher(Arrays.stream(new String[]{
                    "/api-docs/**",
                    "/api/v1/login",
                    "/api/v1/init",
                    "/api/v1/info",
                    "/actuator/**"
            })
            .map(path -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(path))
            .toList());

    private PublicEndpoints() {
    }
}
