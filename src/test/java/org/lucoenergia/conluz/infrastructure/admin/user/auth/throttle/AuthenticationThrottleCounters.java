package org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle;

import org.lucoenergia.conluz.domain.admin.user.auth.throttle.AuthenticationThrottleService;
import org.springframework.test.util.AopTestUtils;

/**
 * Lets a test outside this package forget every failed authentication attempt counted so far, which it must do
 * whenever it moves the application clock backwards: a window opened later in time would otherwise still be open.
 */
public final class AuthenticationThrottleCounters {

    private AuthenticationThrottleCounters() {
    }

    public static void clearAll(AuthenticationThrottleService service) {
        AuthenticationThrottleServiceImpl impl = AopTestUtils.getUltimateTargetObject(service);
        impl.clearAllCounters();
    }
}
