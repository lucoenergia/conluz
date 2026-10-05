package org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle;

import org.lucoenergia.conluz.domain.admin.user.auth.throttle.LoginAttempt;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.LoginFailureReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class LoginAttemptImpl implements LoginAttempt {

    // The throttling logs under one category, whichever class writes the line
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationThrottleServiceImpl.class);

    private final ReservedSlot slot;
    private final String maskedAccount;
    private final String clientIp;

    LoginAttemptImpl(ReservedSlot slot, String maskedAccount, String clientIp) {
        this.slot = slot;
        this.maskedAccount = maskedAccount;
        this.clientIp = clientIp;
    }

    @Override
    public void failed(LoginFailureReason reason) {
        LOGGER.warn("Failed login: account={}, ip={}, reason={}", maskedAccount, clientIp, reason);
        slot.failed();
    }

    @Override
    public void succeeded() {
        slot.succeeded();
    }

    @Override
    public void close() {
        slot.release();
    }
}
