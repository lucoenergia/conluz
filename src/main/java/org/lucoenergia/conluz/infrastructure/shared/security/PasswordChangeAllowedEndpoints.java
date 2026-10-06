package org.lucoenergia.conluz.infrastructure.shared.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * The only requests a user who must change their password may make (#342): reading the current user, changing the
 * password and logging out. Every other authenticated request is refused until the password is changed. Declared
 * once, because both the filter that refuses the rest and the API documentation of that refusal depend on it.
 */
public final class PasswordChangeAllowedEndpoints {

    public static final RequestMatcher MATCHER = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/users/current"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.PUT, "/api/v1/users/current/password"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/logout"));

    private PasswordChangeAllowedEndpoints() {
    }
}
