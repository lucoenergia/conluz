package org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle;

import org.lucoenergia.conluz.domain.admin.user.auth.throttle.PasswordChangeAttempt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

final class PasswordChangeAttemptImpl implements PasswordChangeAttempt {

    // The throttling logs under one category, whichever class writes the line
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationThrottleServiceImpl.class);

    private final ReservedSlot slot;
    private final UUID userId;
    private final String clientIp;

    PasswordChangeAttemptImpl(ReservedSlot slot, UUID userId, String clientIp) {
        this.slot = slot;
        this.userId = userId;
        this.clientIp = clientIp;
    }

    @Override
    public void failed() {
        LOGGER.warn("Failed password change: user={}, ip={}, reason=WRONG_CURRENT_PASSWORD", userId, clientIp);
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
