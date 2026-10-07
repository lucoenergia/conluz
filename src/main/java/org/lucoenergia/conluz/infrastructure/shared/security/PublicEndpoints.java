package org.lucoenergia.conluz.infrastructure.shared.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * The endpoints that require no authentication. Declared once, because both the security configuration, which lets
 * them through, and the token filter, which ignores any token presented on them, must agree on them.
 *
 * <p>The paths in the first list are public on any HTTP method. The password recovery endpoints (#362) sit under
 * {@code /api/v1/users}, next to endpoints that require authentication, so each is public on its exact path and
 * method only.</p>
 */
public final class PublicEndpoints {

    public static final RequestMatcher MATCHER = new OrRequestMatcher(Stream.concat(
                    Arrays.stream(new String[]{
                                    "/api-docs/**",
                                    "/api/v1/login",
                                    "/api/v1/init",
                                    "/api/v1/info",
                                    "/actuator/**"
                            })
                            .map(path -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(path)),
                    Stream.of(
                            PathPatternRequestMatcher.withDefaults()
                                    .matcher(HttpMethod.POST, "/api/v1/users/password/recover"),
                            PathPatternRequestMatcher.withDefaults()
                                    .matcher(HttpMethod.POST, "/api/v1/users/password/reset")))
            .toList());

    private PublicEndpoints() {
    }
}
